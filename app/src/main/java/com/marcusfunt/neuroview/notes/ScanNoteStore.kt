package com.marcusfunt.neuroview.notes

import android.content.Context
import android.content.SharedPreferences
import com.marcusfunt.neuroview.catalog.ScanRecord
import java.nio.charset.StandardCharsets
import java.util.Base64

/** A private, local annotation tied to one library entry and slice location. */
data class ScanNote(
    val id: String,
    val scanId: String,
    val collectionLabel: String,
    val seriesLabel: String,
    val planeName: String,
    val sliceIndex: Int,
    val sliceCount: Int,
    val createdAtMillis: Long,
    val body: String,
    /** A NIfTI spatial transform was present; this does not validate clinical orientation. */
    val spatialTransformPresent: Boolean = false,
    val nativeStackAxis: Int? = null,
    /** A hash of source filename and NIfTI header geometry; never contains the source URI. */
    val sourceFingerprint: String? = null,
    val sourceDisplayName: String? = null,
    /** Original catalog ID retained when a restored note needs manual relinking. */
    val archiveSourceScanId: String? = null,
)

/** Versioned line codec that safely handles delimiters, Unicode and multiline notes. */
object ScanNoteCodec {
    private const val VERSION = "v4"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(notes: List<ScanNote>): String = notes.joinToString("\n") { note ->
        listOf(
            VERSION,
            encodeText(note.id),
            encodeText(note.scanId),
            encodeText(note.collectionLabel),
            encodeText(note.seriesLabel),
            encodeText(note.planeName),
            note.sliceIndex.toString(),
            note.sliceCount.toString(),
            note.createdAtMillis.toString(),
            note.spatialTransformPresent.toString(),
            note.nativeStackAxis?.toString() ?: "-",
            encodeText(note.body),
            note.sourceFingerprint?.let(::encodeText) ?: "-",
            note.sourceDisplayName?.let(::encodeText) ?: "-",
            note.archiveSourceScanId?.let(::encodeText) ?: "-",
        ).joinToString("|")
    }

    fun decode(encoded: String?): List<ScanNote> = encoded
        ?.lineSequence()
        ?.mapNotNull(::decodeLine)
        ?.toList()
        .orEmpty()

    private fun decodeLine(line: String): ScanNote? = runCatching {
        val fields = line.split('|')
        val version = fields.firstOrNull()
        val hasOrientationState = when (version) {
            "v1" -> {
                require(fields.size == 10)
                false
            }
            "v2" -> {
                require(fields.size == 11)
                true
            }
            "v3" -> {
                require(fields.size == 12)
                true
            }
            VERSION -> {
                require(fields.size == 15)
                true
            }
            else -> error("Unsupported note record")
        }
        val sliceIndex = fields[6].toInt()
        val sliceCount = fields[7].toInt()
        require(sliceIndex >= 0 && sliceCount > 0 && sliceIndex < sliceCount)
        val bodyFieldIndex = when (version) {
            "v1" -> 9
            "v2" -> 10
            "v3", VERSION -> 11
            else -> error("Unsupported note record")
        }
        ScanNote(
            id = decodeText(fields[1]),
            scanId = decodeText(fields[2]),
            collectionLabel = decodeText(fields[3]),
            seriesLabel = decodeText(fields[4]),
            planeName = decodeText(fields[5]),
            sliceIndex = sliceIndex,
            sliceCount = sliceCount,
            createdAtMillis = fields[8].toLong(),
            body = decodeText(fields[bodyFieldIndex]),
            spatialTransformPresent = hasOrientationState && fields[9].toBooleanStrict(),
            nativeStackAxis = if (version == "v3" || version == VERSION) {
                fields[10].takeIf { it != "-" }?.toInt()
            } else null,
            sourceFingerprint = if (version == VERSION) fields[12].takeIf { it != "-" }?.let(::decodeText) else null,
            sourceDisplayName = if (version == VERSION) fields[13].takeIf { it != "-" }?.let(::decodeText) else null,
            archiveSourceScanId = if (version == VERSION) fields[14].takeIf { it != "-" }?.let(::decodeText) else null,
        ).also {
            require(it.id.isNotBlank() && it.scanId.isNotBlank() && it.body.isNotBlank())
            require(it.nativeStackAxis == null || it.nativeStackAxis in 0..2)
        }
    }.getOrNull()

    private fun encodeText(value: String): String = encoder.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
    )

    private fun decodeText(value: String): String = String(
        decoder.decode(value), StandardCharsets.UTF_8,
    )
}

/** Stores note text and labels only in app-private preferences; never stores a source URI. */
class ScanNoteStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "scan_notes_v1", Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): List<ScanNote> = ScanNoteCodec.decode(preferences.getString(KEY_NOTES, null))
        .sortedByDescending { it.createdAtMillis }

    @Synchronized
    fun save(note: ScanNote) {
        val updated = load().filterNot { it.id == note.id } + note
        preferences.edit().putString(KEY_NOTES, ScanNoteCodec.encode(updated)).apply()
    }

    @Synchronized
    fun mergeArchive(archive: ScanNoteArchive, currentScans: List<ScanRecord>): NoteArchiveMergeResult {
        val result = ScanNoteArchiveCodec.merge(load(), archive, currentScans)
        persist(result.notes)
        return result
    }

    @Synchronized
    fun relink(noteId: String, scan: ScanRecord): Boolean {
        val current = load()
        val existing = current.firstOrNull { it.id == noteId } ?: return false
        val updated = existing.copy(
            scanId = scan.id,
            sourceFingerprint = ScanNoteArchiveCodec.fingerprint(scan),
            sourceDisplayName = scan.displayName,
            archiveSourceScanId = null,
        )
        persist(current.map { if (it.id == noteId) updated else it })
        return true
    }

    @Synchronized
    fun delete(id: String) {
        preferences.edit().putString(KEY_NOTES, ScanNoteCodec.encode(load().filterNot { it.id == id })).apply()
    }

    private fun persist(notes: List<ScanNote>) {
        val encoded = ScanNoteCodec.encode(notes)
        require(encoded.length <= ScanNoteArchiveCodec.MAX_ARCHIVE_CHARS) {
            "The local notes are too large to store safely. Export a backup and remove older notes before importing."
        }
        val oldValue = preferences.getString(KEY_NOTES, null)
        if (preferences.edit().putString(KEY_NOTES, encoded).commit()) return

        // SharedPreferences updates its in-memory map before its disk write. Restore the old value
        // in memory too when the synchronous write reports failure; its atomic backup retains the
        // previous on-disk value if storage remains unavailable.
        val rollback = preferences.edit()
        if (oldValue == null) rollback.remove(KEY_NOTES) else rollback.putString(KEY_NOTES, oldValue)
        rollback.commit()
        error(
            "Could not save the notes archive. Existing notes were left unchanged."
        )
    }

    private companion object {
        const val KEY_NOTES = "notes"
    }
}
