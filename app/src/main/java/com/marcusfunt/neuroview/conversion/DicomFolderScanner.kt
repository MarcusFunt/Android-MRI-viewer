package com.marcusfunt.neuroview.conversion

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

data class DicomFolderSelection(
    val documents: List<Uri>,
    val ignoredFiles: Int,
)

/**
 * Enumerates a user-selected SAF document tree, including nested directories.
 * Detects Part 10 DICOM by its header, not by file extension (many scanners use
 * numbered or extensionless filenames). Does not copy source data or require
 * broad storage permissions.
 */
object DicomFolderScanner {
    private const val MAX_SLICES = 1024
    private const val MAX_ENTRIES = 8192
    private const val MAX_DEPTH = 16

    fun scan(resolver: ContentResolver, treeUri: Uri): DicomFolderSelection {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()
        val documents = mutableListOf<Uri>()
        var inspected = 0
        var ignored = 0
        pending.add(rootId to 0)

        while (pending.isNotEmpty()) {
            val (parentId, depth) = pending.removeFirst()
            require(depth <= MAX_DEPTH) { "DICOM folder nesting is too deep" }
            if (!visited.add(parentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val cursor = resolver.query(childrenUri, projection, null, null, null)
                ?: throw IllegalArgumentException("Cannot list the selected folder")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val typeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    inspected++
                    require(inspected <= MAX_ENTRIES) {
                        "Folder contains too many entries (limit $MAX_ENTRIES). Select one series folder."
                    }
                    val childId = it.getString(idColumn) ?: continue
                    val filename = it.getString(nameColumn).orEmpty()
                    val mimeType = it.getString(typeColumn).orEmpty()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(childId to depth + 1)
                        continue
                    }
                    // DICOMDIR is an index, not an image slice.
                    if (filename.equals("DICOMDIR", ignoreCase = true)) {
                        ignored++
                        continue
                    }
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val isDicom = resolver.openInputStream(documentUri)?.use {
                        DicomPart10Signature.matches(it)
                    } ?: throw IllegalArgumentException("Cannot read an entry in this folder")
                    if (isDicom) {
                        require(documents.size < MAX_SLICES) {
                            "More than $MAX_SLICES DICOM files found. Select one series folder."
                        }
                        documents.add(documentUri)
                    } else if (DicomPart10Signature.hasDicomExtension(filename)) {
                        throw IllegalArgumentException(
                            "A .dcm/.dicom/.ima file has no DICOM Part 10 header. " +
                                "This converter requires Part 10 files.",
                        )
                    } else {
                        ignored++ // e.g. README, thumbnails, XML, scanner sidecars
                    }
                }
            }
        }
        require(documents.isNotEmpty()) {
            "No supported DICOM Part 10 files found in this folder."
        }
        return DicomFolderSelection(documents, ignored)
    }
}
