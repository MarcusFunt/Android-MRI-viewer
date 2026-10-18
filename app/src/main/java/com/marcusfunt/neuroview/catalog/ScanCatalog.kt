package com.marcusfunt.neuroview.catalog

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.OpenableColumns
import com.marcusfunt.neuroview.mri.MriMemoryBudget
import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiReadOptions
import com.marcusfunt.neuroview.mri.NiftiSeriesInspector
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Import collections reflect a file-picker batch, NOT an actual DICOM study UID. */
data class ImportCollection(
    val id: String,
    val label: String,
    val importedAt: Long,
)

data class ScanRecord(
    val id: String,
    val collectionId: String,
    val documentUri: String,
    val displayName: String,
    val metadata: NiftiSeriesMetadata,
    /** Optional in-app alias; the actual filename and external NIfTI are unchanged. */
    val customName: String? = null,
) {
    val label: String get() = customName?.takeIf { it.isNotBlank() } ?: displayName
}

data class ScanCatalogSnapshot(
    val collections: List<ImportCollection> = emptyList(),
    val scans: List<ScanRecord> = emptyList(),
) {
    fun inCollection(collectionId: String): List<ScanRecord> =
        scans.filter { it.collectionId == collectionId }
}

data class ImportResult(
    val added: Int,
    val skipped: Int,
    val errors: List<String>,
    val failed: Int = 0,
    val canceled: Boolean = false,
)

data class ImportBatchProgress(
    val filesProcessed: Int,
    val totalFiles: Int,
    val currentFileName: String?,
    val added: Int,
    val skipped: Int,
    val failed: Int,
    val waitingForProvider: Boolean = false,
) {
    init {
        require(totalFiles >= 0 && filesProcessed in 0..totalFiles)
        require(added >= 0 && skipped >= 0 && failed >= 0)
    }
}

/**
 * Lightweight, app-private URI catalog. Never copies the original MRI data.
 * Allows editing only in-app references and labels. Original MRI files stay untouched.
 */
class ScanCatalog(private val context: Context) {
    private val preferences = context.getSharedPreferences("scan_catalog_v1", Context.MODE_PRIVATE)
    private val thumbnailsDir = File(context.cacheDir, "scan_thumbnails")
    private val thumbnailCacheBytesLimit = 24L * 1024 * 1024
    private val thumbnailCacheEntriesLimit = 256
    // Multiple visible list cells may start image work together. Serialize big-volume decoding
    // so simultaneous full-memory reads do not exhaust a modest Android phone.
    private val thumbnailMutex = Mutex()
    private val catalogMutex = Mutex()
    private val viewerHasPriority = AtomicBoolean(false)
    private val thumbnailJobs = ConcurrentHashMap<String, Job>()

    init {
        thumbnailsDir.listFiles()?.filter { file ->
            file.isFile && (file.extension == "tmp" || !file.name.startsWith("preview-v2-"))
        }?.forEach { it.delete() }
    }

    /** Viewer work cancels queued and active low-priority thumbnail decodes. */
    fun setViewerPriority(active: Boolean) {
        viewerHasPriority.set(active)
        if (active) {
            thumbnailJobs.values.forEach { job ->
                job.cancel(CancellationException("Viewer load has priority over thumbnails"))
            }
        }
    }

    fun load(): ScanCatalogSnapshot {
        return try {
            val data = JSONObject(preferences.getString("catalog", "{}") ?: "{}")
            val groups = data.optJSONArray("collections") ?: JSONArray()
            val series = data.optJSONArray("series") ?: JSONArray()
            val collections = (0 until groups.length()).mapNotNull { idx ->
                runCatching {
                    val entry = groups.getJSONObject(idx)
                    ImportCollection(
                        id = entry.getString("id"),
                        label = entry.getString("label"),
                        importedAt = entry.getLong("importedAt"),
                    )
                }.getOrNull()
            }
            val scans = (0 until series.length()).mapNotNull { idx ->
                runCatching {
                    val item = series.getJSONObject(idx)
                    val m = item.getJSONObject("metadata")
                    val dimensions = readInts(m.getJSONArray("dimensions"), 3)
                    val spacings = readDoubles(m.getJSONArray("spacingMm"), 3)
                    ScanRecord(
                        id = item.getString("id"),
                        collectionId = item.getString("collectionId"),
                        documentUri = item.getString("uri"),
                        displayName = item.getString("name"),
                        customName = item.optString("customName").takeIf { it.isNotBlank() },
                        metadata = NiftiSeriesMetadata(
                            dimensions = dimensions,
                            spacingMm = spacings,
                            timepoints = m.getInt("frames"),
                            dataType = m.getString("dataType"),
                            orientation = m.getString("orientation"),
                            description = m.optString("description"),
                        ),
                    )
                }.getOrNull()
            }
            ScanCatalogSnapshot(collections, scans)
        } catch (_: Exception) {
            ScanCatalogSnapshot()
        }
    }

    suspend fun importDocuments(
        documents: List<Uri>,
        cancellationSignal: CancellationSignal = CancellationSignal(),
        onProgress: suspend (ImportBatchProgress) -> Unit = {},
        onCatalogChanged: suspend (ScanCatalogSnapshot) -> Unit = {},
    ): ImportResult = withContext(Dispatchers.IO) {
        catalogMutex.withLock {
            var snapshot = load()
            var collection: ImportCollection? = null
            val errors = mutableListOf<String>()
            val unique = documents.distinctBy { it.toString() }
            var skipped = documents.size - unique.size
            val capped = unique.take(64)
            if (unique.size > capped.size) {
                skipped += unique.size - capped.size
                errors += "Only the first 64 unique documents were considered."
            }
            var processed = 0
            var added = 0
            var failed = 0
            var canceled = false

            suspend fun publishProgress(
                currentFileName: String? = null,
                waitingForProvider: Boolean = false,
            ) {
                onProgress(
                    ImportBatchProgress(
                        filesProcessed = processed,
                        totalFiles = capped.size,
                        currentFileName = currentFileName,
                        added = added,
                        skipped = skipped,
                        failed = failed,
                        waitingForProvider = waitingForProvider,
                    ),
                )
            }

            publishProgress()
            for (uri in capped) {
                publishProgress(waitingForProvider = true)
                val name = try {
                    displayName(uri, cancellationSignal)
                } catch (_: OperationCanceledException) {
                    canceled = true
                    break
                }
                publishProgress(name, waitingForProvider = true)

                var permissionTaken = false
                var committed = false
                var finished = false
                var committedSnapshot: ScanCatalogSnapshot? = null
                try {
                    cancellationSignal.throwIfCanceled()
                    val supported = name.endsWith(".nii", ignoreCase = true) ||
                        name.endsWith(".nii.gz", ignoreCase = true)
                    if (!supported) {
                        skipped++
                        errors += "$name: select a .nii or .nii.gz file"
                        finished = true
                    } else if (snapshot.scans.any { it.documentUri == uri.toString() }) {
                        skipped++
                        finished = true
                    } else {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                        permissionTaken = true
                        val descriptor = context.contentResolver.openAssetFileDescriptor(
                            uri, "r", cancellationSignal,
                        ) ?: throw FileNotFoundException("Selected MRI is unavailable")
                        val metadata = descriptor.use { asset ->
                            NiftiSeriesInspector.inspect(asset.createInputStream()) {
                                cancellationSignal.throwIfCanceled()
                            }
                        }
                        cancellationSignal.throwIfCanceled()

                        val now = System.currentTimeMillis()
                        val batchCollection = collection ?: ImportCollection(
                            id = UUID.randomUUID().toString(),
                            label = "Import · " + DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.SHORT,
                            ).format(Date(now)),
                            importedAt = now,
                        )
                        val record = ScanRecord(
                            id = UUID.randomUUID().toString(),
                            collectionId = "",
                            documentUri = uri.toString(),
                            displayName = name,
                            metadata = metadata,
                        )
                        val commit = commitImportedRecord(
                            snapshot = snapshot,
                            batchCollection = collection,
                            proposedCollection = batchCollection,
                            record = record,
                        )
                        store(commit.snapshot)
                        snapshot = commit.snapshot
                        collection = commit.collection
                        added++
                        committed = true
                        committedSnapshot = commit.snapshot
                        finished = true
                    }
                } catch (providerCanceled: OperationCanceledException) {
                    if (cancellationSignal.isCanceled) {
                        canceled = true
                    } else {
                        failed++
                        errors += "$name: ${safeImportFailure(providerCanceled)}"
                        finished = true
                    }
                    if (permissionTaken && !committed) releaseReadPermission(uri.toString())
                } catch (coroutineCanceled: CancellationException) {
                    if (permissionTaken && !committed) releaseReadPermission(uri.toString())
                    throw coroutineCanceled
                } catch (exception: Exception) {
                    if (permissionTaken && !committed) releaseReadPermission(uri.toString())
                    failed++
                    errors += "$name: ${safeImportFailure(exception)}"
                    finished = true
                }
                if (finished) processed++
                publishProgress(waitingForProvider = false)
                committedSnapshot?.let { onCatalogChanged(it) }
                if (canceled) break
            }
            ImportResult(
                added = added,
                skipped = skipped,
                errors = errors.take(5),
                failed = failed,
                canceled = canceled,
            )
        }
    }

    /** Atomic and persistent catalog-only edits. The URI and source bytes never change. */
    suspend fun createCollection(label: String): ScanCatalogSnapshot =
        edit { original ->
            CatalogEdits.createCollection(
                original, UUID.randomUUID().toString(), label, System.currentTimeMillis(),
            )
        }

    suspend fun renameCollection(id: String, label: String): ScanCatalogSnapshot =
        edit { CatalogEdits.renameCollection(it, id, label) }

    suspend fun renameSeries(id: String, alias: String): ScanCatalogSnapshot =
        edit { CatalogEdits.renameSeries(it, id, alias) }

    suspend fun resetSeriesName(id: String): ScanCatalogSnapshot =
        edit { CatalogEdits.resetSeriesName(it, id) }

    suspend fun moveSeries(id: String, collectionId: String): ScanCatalogSnapshot =
        edit { CatalogEdits.moveSeries(it, id, collectionId) }

    suspend fun removeSeries(id: String): ScanCatalogSnapshot =
        edit { CatalogEdits.removeSeries(it, id) }

    /** Removes this collection AND all its in-app references; never deletes source files. */
    suspend fun removeCollection(id: String): ScanCatalogSnapshot =
        edit { CatalogEdits.removeCollection(it, id) }

    suspend fun clearThumbnailCache(): Int = withContext(Dispatchers.IO) {
        thumbnailMutex.withLock {
            if (!thumbnailsDir.isDirectory) 0 else {
                thumbnailsDir.listFiles()?.count { it.isFile && it.delete() } ?: 0
            }
        }
    }

    private suspend fun edit(
        operation: (ScanCatalogSnapshot) -> ScanCatalogSnapshot,
    ): ScanCatalogSnapshot = withContext(Dispatchers.IO) {
        catalogMutex.withLock {
            val before = load()
            val after = operation(before)
            store(after) // commit first; do not discard grants/files if persistence fails.
            val remainingIds = after.scans.mapTo(mutableSetOf()) { it.id }
            val remainingUris = after.scans.mapTo(mutableSetOf()) { it.documentUri }
            before.scans.filterNot { it.id in remainingIds }.forEach { removed ->
                thumbnailJobs[removed.id]?.cancel(
                    CancellationException("MRI entry was removed from the Library"),
                )
                thumbnailFile(removed.id).delete()
                if (removed.documentUri !in remainingUris) {
                    releaseReadPermission(removed.documentUri)
                }
            }
            after
        }
    }

    private fun releaseReadPermission(documentUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(documentUri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun safeImportFailure(error: Exception): String = when (error) {
        is SecurityException -> "File access was denied"
        is FileNotFoundException -> "The selected file is no longer available"
        is IllegalArgumentException -> if (
            error.message?.contains("Unsupported", ignoreCase = true) == true
        ) "Unsupported NIfTI format" else "Invalid or incomplete NIfTI header"
        else -> "Could not inspect the selected MRI"
    }

    private fun thumbnailFile(scanId: String) = File(thumbnailsDir, "preview-v2-$scanId.png")

    private fun pruneThumbnailCache(protected: File) {
        val files = thumbnailsDir.listFiles()?.filter { it.isFile }
            ?.sortedBy { it.lastModified() } ?: return
        var totalBytes = files.sumOf { it.length() }
        var fileCount = files.size
        for (file in files) {
            if (file == protected) continue
            if (totalBytes <= thumbnailCacheBytesLimit &&
                fileCount <= thumbnailCacheEntriesLimit
            ) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
                fileCount--
            }
        }
    }

    suspend fun loadThumbnail(scan: ScanRecord): Bitmap? = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job] ?: return@withContext null
        if (viewerHasPriority.get() || thumbnailJobs.putIfAbsent(scan.id, job) != null) {
            return@withContext null
        }
        val signal = CancellationSignal()
        val cancellationBridge = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                signal.cancel()
            }
        }
        try {
            thumbnailMutex.withLock {
                job.ensureActive()
                if (viewerHasPriority.get()) return@withLock null
                val thumb = thumbnailFile(scan.id)
                if (thumb.isFile) {
                    BitmapFactory.decodeFile(thumb.absolutePath)?.let {
                        thumb.setLastModified(System.currentTimeMillis())
                        return@withLock it
                    }
                    thumb.delete()
                }
                try {
                    // Large series remain importable. Avoid decompressing an entire huge
                    // volume just to show its thumbnail in a scrolling list.
                    val count = scan.metadata.dimensions.fold(1L) { acc, dim -> acc * dim }
                    if (count > MriMemoryBudget.maxVoxels(Runtime.getRuntime().maxMemory())) {
                        return@withLock null
                    }
                    val descriptor = context.contentResolver.openAssetFileDescriptor(
                        Uri.parse(scan.documentUri), "r", signal,
                    ) ?: return@withLock null
                    val volume = descriptor.use { asset ->
                        NiftiReader.read(
                            asset.createInputStream(),
                            NiftiReadOptions(
                                maxVoxels = MriMemoryBudget.maxVoxels(Runtime.getRuntime().maxMemory()),
                                cancellationCheck = {
                                    job.ensureActive()
                                    if (viewerHasPriority.get()) {
                                        throw CancellationException("Viewer work has priority")
                                    }
                                    signal.throwIfCanceled()
                                },
                            ),
                        )
                    }
                    try {
                        job.ensureActive()
                        if (viewerHasPriority.get()) {
                            throw CancellationException("Viewer work has priority")
                        }
                        val plane = MriPlane.AXIAL
                        val slice = MprRenderer.sliceCount(volume, plane) / 2
                        val rendered = MprRenderer.render(
                            volume, plane, slice, maxSide = 144,
                            cancellationCheck = {
                                job.ensureActive()
                                if (viewerHasPriority.get()) {
                                    throw CancellationException("Viewer work has priority")
                                }
                            },
                        )
                        job.ensureActive()
                        if (!thumbnailsDir.exists()) thumbnailsDir.mkdirs()
                        val temporary = File.createTempFile("preview-v2-", ".tmp", thumbnailsDir)
                        try {
                            val bitmap = Bitmap.createBitmap(
                                rendered.pixels, rendered.width, rendered.height, Bitmap.Config.ARGB_8888,
                            )
                            val saved = FileOutputStream(temporary).use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            job.ensureActive()
                            if (saved && temporary.renameTo(thumb)) {
                                thumb.setLastModified(System.currentTimeMillis())
                                pruneThumbnailCache(thumb)
                            }
                            temporary.delete()
                            bitmap
                        } finally {
                            temporary.delete()
                        }
                    } finally {
                        volume.close()
                    }
                } catch (canceled: CancellationException) {
                    throw canceled
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
            }
        } finally {
            cancellationBridge.cancel()
            thumbnailJobs.remove(scan.id, job)
        }
    }

    private fun displayName(uri: Uri, cancellationSignal: CancellationSignal? = null): String {
        val name = try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, cancellationSignal,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (canceled: OperationCanceledException) {
            throw canceled
        } catch (_: Exception) {
            null
        }
        return name?.take(180) ?: uri.lastPathSegment?.takeLast(80) ?: "NIfTI file"
    }

    private fun store(snapshot: ScanCatalogSnapshot) {
        val groups = JSONArray()
        snapshot.collections.forEach {
            groups.put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("importedAt", it.importedAt),
            )
        }
        val scans = JSONArray()
        snapshot.scans.forEach { scan ->
            val m = scan.metadata
            val metadata = JSONObject()
                .put("dimensions", JSONArray(m.dimensions))
                .put("spacingMm", JSONArray(m.spacingMm))
                .put("frames", m.timepoints)
                .put("dataType", m.dataType)
                .put("orientation", m.orientation)
                .put("description", m.description)
            scans.put(
                JSONObject()
                    .put("id", scan.id)
                    .put("collectionId", scan.collectionId)
                    .put("uri", scan.documentUri)
                    .put("name", scan.displayName)
                    .put("customName", scan.customName)
                    .put("metadata", metadata),
            )
        }
        check(
            preferences.edit()
                .putString("catalog", JSONObject().put("collections", groups).put("series", scans).toString())
                .commit(),
        ) { "Could not save the local MRI catalog" }
    }

    private fun readInts(array: JSONArray, length: Int) =
        List(length) { index -> array.getInt(index) }

    private fun readDoubles(array: JSONArray, length: Int) =
        List(length) { index -> array.getDouble(index) }
}
