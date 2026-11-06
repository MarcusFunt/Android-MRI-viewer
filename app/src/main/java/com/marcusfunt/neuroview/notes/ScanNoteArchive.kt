package com.marcusfunt.neuroview.notes

import com.marcusfunt.neuroview.catalog.ScanRecord
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class ScanNoteArchive(
    val exportedAtMillis: Long,
    val notes: List<ArchivedScanNote>,
)

data class ArchivedScanNote(
    val id: String,
    val sourceCatalogId: String,
    val sourceFingerprint: String?,
    val sourceDisplayName: String,
    val collectionLabel: String,
    val seriesLabel: String,
    val planeName: String,
    val sliceIndex: Int,
    val sliceCount: Int,
    val createdAtMillis: Long,
    val body: String,
    val spatialTransformPresent: Boolean,
    val nativeStackAxis: Int?,
)

data class NoteArchiveMergeResult(
    val notes: List<ScanNote>,
    val imported: Int,
    val duplicatesSkipped: Int,
    val conflictsPreserved: Int,
    val awaitingRelink: Int,
)

/** Versioned JSON backup of note text and source metadata; never includes a provider URI or voxels. */
object ScanNoteArchiveCodec {
    const val MAX_ARCHIVE_BYTES = 8 * 1024 * 1024
    const val MAX_ARCHIVE_CHARS = 8 * 1024 * 1024
    private const val MAX_NOTES = 20_000
    private const val MAX_BODY_CHARS = 65_536
    private const val FORMAT = "neuroview.scan-notes"
    private const val FORMAT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun encode(
        notes: List<ScanNote>,
        currentScans: List<ScanRecord>,
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String {
        require(notes.size <= MAX_NOTES) { "The notes backup contains too many entries." }
        require(exportedAtMillis >= 0L) { "The export time is invalid." }
        val scansById = currentScans.associateBy { it.id }
        val entries = notes.map { note ->
            val currentScan = scansById[note.scanId]
            ArchivedScanNote(
                id = note.id,
                sourceCatalogId = note.archiveSourceScanId ?: note.scanId,
                sourceFingerprint = note.sourceFingerprint,
                sourceDisplayName = note.sourceDisplayName
                    ?: currentScan?.displayName
                    ?: note.seriesLabel,
                collectionLabel = note.collectionLabel,
                seriesLabel = note.seriesLabel,
                planeName = note.planeName,
                sliceIndex = note.sliceIndex,
                sliceCount = note.sliceCount,
                createdAtMillis = note.createdAtMillis,
                body = note.body,
                spatialTransformPresent = note.spatialTransformPresent,
                nativeStackAxis = note.nativeStackAxis,
            )
        }
        entries.forEach(::validate)
        val encoded = json.encodeToString(
            ArchiveWire(
                format = FORMAT,
                formatVersion = FORMAT_VERSION,
                exportedAtMillis = exportedAtMillis,
                notes = entries.map { it.toWire() },
            ),
        )
        requireWithinLimits(encoded, "The notes backup is too large to create safely.")
        return encoded
    }

    fun decode(input: String): ScanNoteArchive {
        requireWithinLimits(input, "The notes backup exceeds the size limit.")
        val wire = try {
            json.decodeFromString<ArchiveWire>(input)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("This file is not a valid NeuroView notes backup.", error)
        }
        require(wire.format == FORMAT) { "This file is not a NeuroView notes backup." }
        require(wire.formatVersion == FORMAT_VERSION) {
            "This notes backup uses an unsupported version (${wire.formatVersion})."
        }
        require(wire.exportedAtMillis >= 0L) { "The notes backup has an invalid export time." }
        require(wire.notes.size <= MAX_NOTES) { "The notes backup contains too many entries." }
        val entries = wire.notes.map { it.toModel() }
        entries.forEach(::validate)
        return ScanNoteArchive(wire.exportedAtMillis, entries)
    }

    private fun requireWithinLimits(text: String, message: String) {
        require(text.length <= MAX_ARCHIVE_CHARS &&
            text.toByteArray(StandardCharsets.UTF_8).size <= MAX_ARCHIVE_BYTES) { message }
    }

    /** Reads a SAF document without accepting an unbounded or malformed UTF-8 payload. */
    fun decode(input: InputStream): ScanNoteArchive {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        input.use { stream ->
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                require(output.size() + count <= MAX_ARCHIVE_BYTES) {
                    "The notes backup exceeds the 8 MiB import limit."
                }
                output.write(buffer, 0, count)
            }
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("The notes backup is not valid UTF-8 text.", error)
        }
        return decode(text)
    }

    /** Header-only fingerprint; it is a relinking hint, not proof that voxel bytes are identical. */
    fun fingerprint(scan: ScanRecord): String? {
        val metadata = scan.metadata
        val transformFingerprint = metadata.transformHeaderFingerprint ?: return null
        val canonical = buildString {
            appendLine("neuroview-scan-header-v1")
            appendLine(scan.displayName)
            appendLine(metadata.dimensions.joinToString(","))
            appendLine(metadata.spacingHeaderUnits.joinToString(",") { java.lang.Double.toHexString(it) })
            appendLine(metadata.spatialUnits.name)
            appendLine(metadata.timepoints)
            appendLine(metadata.dataType)
            appendLine(metadata.orientation)
            appendLine(metadata.qformCode)
            appendLine(metadata.sformCode)
            appendLine(transformFingerprint)
            appendLine(metadata.description)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun merge(
        existing: List<ScanNote>,
        archive: ScanNoteArchive,
        currentScans: List<ScanRecord>,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): NoteArchiveMergeResult {
        val localScansById = currentScans.associateBy { it.id }
        val localFingerprints = currentScans.associate { it.id to fingerprint(it) }
        val existingIds = (existing.mapTo(mutableSetOf()) { it.id })
        val merged = existing.toMutableList()
        var imported = 0
        var skipped = 0
        var conflicts = 0
        var awaitingRelink = 0

        archive.notes.forEachIndexed { index, entry ->
            val matchingLocalScan = localScansById[entry.sourceCatalogId]?.takeIf { scan ->
                entry.sourceFingerprint != null && localFingerprints[scan.id] == entry.sourceFingerprint
            }
            val localScanId = matchingLocalScan?.id ?: orphanScanId(entry, index, localScansById.keys)
            var restored = entry.toScanNote(
                scanId = localScanId,
                archiveSourceScanId = entry.sourceCatalogId.takeIf { matchingLocalScan == null },
            )
            val sameId = merged.firstOrNull { it.id == restored.id }
            if (sameId != null) {
                if (sameNoteContent(sameId, restored)) {
                    skipped++
                    return@forEachIndexed
                }
                conflicts++
                val newId = idFactory()
                require(newId.isNotBlank() && newId !in existingIds) {
                    "Could not create a unique ID for an imported note conflict."
                }
                existingIds += newId
                restored = restored.copy(id = newId)
            } else {
                existingIds += restored.id
            }
            merged += restored
            imported++
            if (matchingLocalScan == null) awaitingRelink++
        }
        return NoteArchiveMergeResult(
            notes = merged.sortedByDescending { it.createdAtMillis },
            imported = imported,
            duplicatesSkipped = skipped,
            conflictsPreserved = conflicts,
            awaitingRelink = awaitingRelink,
        )
    }

    private fun sameNoteContent(first: ScanNote, second: ScanNote): Boolean {
        val sameContent = first.copy(
            scanId = "",
            sourceFingerprint = null,
            sourceDisplayName = null,
            archiveSourceScanId = null,
        ) == second.copy(
            scanId = "",
            sourceFingerprint = null,
            sourceDisplayName = null,
            archiveSourceScanId = null,
        )
        val sameSource = first.scanId == second.scanId ||
            (first.sourceFingerprint != null && first.sourceFingerprint == second.sourceFingerprint) ||
            (second.archiveSourceScanId == first.scanId &&
                (first.sourceFingerprint == null || first.sourceFingerprint == second.sourceFingerprint))
        return sameContent && sameSource
    }

    private fun orphanScanId(entry: ArchivedScanNote, index: Int, localIds: Set<String>): String {
        var orphan = "archive-unlinked:${entry.sourceCatalogId}:$index"
        while (orphan in localIds) orphan += ":restored"
        return orphan
    }

    private fun validate(note: ArchivedScanNote) {
        require(note.id.isNotBlank() && note.id.length <= 160) { "A note has an invalid ID." }
        require(note.sourceCatalogId.isNotBlank() && note.sourceCatalogId.length <= 240) {
            "A note has an invalid original scan ID."
        }
        require(note.sourceDisplayName.length <= 240) { "A source filename is too long." }
        require(note.collectionLabel.length <= 240 && note.seriesLabel.length <= 240) {
            "A scan label in the notes backup is too long."
        }
        require(note.planeName.isNotBlank() && note.planeName.length <= 40) {
            "A note has an invalid image plane."
        }
        require(note.sliceCount > 0 && note.sliceIndex in 0 until note.sliceCount) {
            "A note has an invalid saved slice location."
        }
        require(note.createdAtMillis >= 0L) { "A note has an invalid creation time." }
        require(note.body.isNotBlank() && note.body.length <= MAX_BODY_CHARS) {
            "A note is empty or exceeds the 64 KiB text limit."
        }
        require(note.nativeStackAxis == null || note.nativeStackAxis in 0..2) {
            "A note has an invalid native stack axis."
        }
        require(note.sourceFingerprint == null || FINGERPRINT.matches(note.sourceFingerprint)) {
            "A note has an invalid scan metadata fingerprint."
        }
    }

    private fun ArchivedScanNote.toWire() = ArchiveNoteWire(
        id, sourceCatalogId, sourceFingerprint, sourceDisplayName, collectionLabel, seriesLabel,
        planeName, sliceIndex, sliceCount, createdAtMillis, body, spatialTransformPresent,
        nativeStackAxis,
    )

    private fun ArchiveNoteWire.toModel() = ArchivedScanNote(
        id, sourceCatalogId, sourceFingerprint, sourceDisplayName, collectionLabel, seriesLabel,
        planeName, sliceIndex, sliceCount, createdAtMillis, body, spatialTransformPresent,
        nativeStackAxis,
    )

    private fun ArchivedScanNote.toScanNote(scanId: String, archiveSourceScanId: String?) = ScanNote(
        id = id,
        scanId = scanId,
        collectionLabel = collectionLabel,
        seriesLabel = seriesLabel,
        planeName = planeName,
        sliceIndex = sliceIndex,
        sliceCount = sliceCount,
        createdAtMillis = createdAtMillis,
        body = body,
        spatialTransformPresent = spatialTransformPresent,
        nativeStackAxis = nativeStackAxis,
        sourceFingerprint = sourceFingerprint,
        sourceDisplayName = sourceDisplayName,
        archiveSourceScanId = archiveSourceScanId,
    )

    @Serializable
    private data class ArchiveWire(
        val format: String,
        val formatVersion: Int,
        val exportedAtMillis: Long,
        val notes: List<ArchiveNoteWire>,
    )

    @Serializable
    private data class ArchiveNoteWire(
        val id: String,
        val sourceCatalogId: String,
        val sourceFingerprint: String? = null,
        val sourceDisplayName: String,
        val collectionLabel: String,
        val seriesLabel: String,
        val planeName: String,
        val sliceIndex: Int,
        val sliceCount: Int,
        val createdAtMillis: Long,
        val body: String,
        val spatialTransformPresent: Boolean,
        val nativeStackAxis: Int? = null,
    )

    private val FINGERPRINT = Regex("sha256:[0-9a-f]{64}")
}
