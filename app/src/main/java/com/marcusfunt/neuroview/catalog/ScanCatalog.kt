package com.marcusfunt.neuroview.catalog

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiSeriesInspector
import com.marcusfunt.neuroview.mri.NiftiSeriesMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.UUID

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
)

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
)

/**
 * Lightweight, app-private URI catalog. Never copies the original MRI data.
 * No deletion/metadata edits or actual DICOM study organization (Phase 5/6).
 */
class ScanCatalog(private val context: Context) {
    private val preferences = context.getSharedPreferences("scan_catalog_v1", Context.MODE_PRIVATE)
    private val thumbnailsDir = File(context.cacheDir, "scan_thumbnails")
    // Multiple visible list cells may start image work together. Serialize big-volume decoding
    // so simultaneous full-memory reads do not exhaust a modest Android phone.
    private val thumbnailMutex = Mutex()

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

    suspend fun importDocuments(documents: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var snapshot = load()
        val records = mutableListOf<ScanRecord>()
        val errors = mutableListOf<String>()
        var skipped = 0
        // Avoid huge file-picker batches unexpectedly attempting many full scans.
        val capped = documents.take(64).distinctBy { it.toString() }
        if (documents.size > 64) errors += "Only the first 64 documents were considered."
        for (uri in capped) {
            val name = displayName(uri)
            if (!(name.endsWith(".nii", ignoreCase = true) ||
                    name.endsWith(".nii.gz", ignoreCase = true))) {
                skipped++
                errors += "$name: select a .nii or .nii.gz file"
                continue
            }
            if (snapshot.scans.any { it.documentUri == uri.toString() } ||
                records.any { it.documentUri == uri.toString() }) {
                skipped++
                continue
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                val metadata = context.contentResolver.openInputStream(uri)?.use {
                    NiftiSeriesInspector.inspect(it)
                } ?: throw IllegalArgumentException("Cannot read this file")
                records += ScanRecord(
                    id = UUID.randomUUID().toString(),
                    collectionId = "", // assigned once the batch has been read
                    documentUri = uri.toString(),
                    displayName = name,
                    metadata = metadata,
                )
            } catch (exception: Exception) {
                // Message is user-visible only. Never send patient filenames to app logs.
                errors += "$name: ${exception.message ?: "Unable to inspect header"}"
            }
        }
        if (records.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val groupId = UUID.randomUUID().toString()
            val label = "Import · " + DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT,
            ).format(Date(now))
            snapshot = snapshot.copy(
                collections = listOf(ImportCollection(groupId, label, now)) + snapshot.collections,
                scans = records.map { it.copy(collectionId = groupId) } + snapshot.scans,
            )
            store(snapshot)
        }
        ImportResult(records.size, skipped, errors.take(5))
    }

    suspend fun loadThumbnail(scan: ScanRecord): Bitmap? = withContext(Dispatchers.IO) {
        thumbnailMutex.withLock {
            val thumb = File(thumbnailsDir, "${scan.id}.png")
            if (thumb.isFile) {
                BitmapFactory.decodeFile(thumb.absolutePath)?.let { return@withLock it }
            }
            try {
                val volume = context.contentResolver.openInputStream(Uri.parse(scan.documentUri))
                    ?.use { NiftiReader.read(it) } ?: return@withLock null
                val plane = when {
                    // Choose a reformat with the fewest out-of-plane slices for thick 2D volumes.
                    volume.affine.axisSpacing(2) >=
                        maxOf(volume.affine.axisSpacing(0), volume.affine.axisSpacing(1)) * 2 ->
                        MriPlane.AXIAL
                    else -> MriPlane.AXIAL
                }
                val slice = MprRenderer.sliceCount(volume, plane) / 2
                val rendered = MprRenderer.render(volume, plane, slice, maxSide = 144)
                val bitmap = Bitmap.createBitmap(
                    rendered.pixels, rendered.width, rendered.height, Bitmap.Config.ARGB_8888,
                )
                if (!thumbnailsDir.exists()) thumbnailsDir.mkdirs()
                // Cache only derived preview pixels in app-private, evictable cache space.
                FileOutputStream(thumb).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.take(180) ?: uri.lastPathSegment?.takeLast(80) ?: "NIfTI file"

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
