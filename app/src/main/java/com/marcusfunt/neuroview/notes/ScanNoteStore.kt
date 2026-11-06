package com.marcusfunt.neuroview.notes

import android.content.Context
import android.content.SharedPreferences
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
    val orientationVerified: Boolean = false,
    val nativeStackAxis: Int? = null,
)

/** Versioned line codec that safely handles delimiters, Unicode and multiline notes. */
object ScanNoteCodec {
    private const val VERSION = "v3"
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
            note.orientationVerified.toString(),
            note.nativeStackAxis?.toString() ?: "-",
            encodeText(note.body),
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
            VERSION -> {
                require(fields.size == 12)
                true
            }
            else -> error("Unsupported note record")
        }
        val sliceIndex = fields[6].toInt()
        val sliceCount = fields[7].toInt()
        require(sliceIndex >= 0 && sliceCount > 0 && sliceIndex < sliceCount)
        ScanNote(
            id = decodeText(fields[1]),
            scanId = decodeText(fields[2]),
            collectionLabel = decodeText(fields[3]),
            seriesLabel = decodeText(fields[4]),
            planeName = decodeText(fields[5]),
            sliceIndex = sliceIndex,
            sliceCount = sliceCount,
            createdAtMillis = fields[8].toLong(),
            body = decodeText(fields[if (version == VERSION) 11 else if (hasOrientationState) 10 else 9]),
            orientationVerified = hasOrientationState && fields[9].toBooleanStrict(),
            nativeStackAxis = if (version == VERSION) fields[10].takeIf { it != "-" }?.toInt() else null,
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
    fun delete(id: String) {
        preferences.edit().putString(KEY_NOTES, ScanNoteCodec.encode(load().filterNot { it.id == id })).apply()
    }

    private companion object {
        const val KEY_NOTES = "notes"
    }
}
