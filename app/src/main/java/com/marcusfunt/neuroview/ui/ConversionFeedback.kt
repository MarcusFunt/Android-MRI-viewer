package com.marcusfunt.neuroview.ui

internal fun savedNiftiFeedback(
    sliceSummary: String,
    fileName: String?,
    folderPath: String?,
): String {
    val savedFile = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: "converted NIfTI file"
    val savedLocation = folderPath?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }
        ?.let { "Location: $it." }
        ?: "Search Files for $savedFile."
    return "$sliceSummary Saved file: $savedFile. $savedLocation Use Import NIfTI to add it to your library."
}

internal fun suggestedNiftiFileName(uniqueSuffix: String): String =
    "NeuroView_DICOM_derived_$uniqueSuffix.nii.gz"

internal fun externalStorageFolderPath(documentId: String): String? {
    val separator = documentId.indexOf(':')
    if (separator <= 0) return null

    val storageId = documentId.substring(0, separator)
    val relativePath = documentId.substring(separator + 1).replace('\\', '/').trim('/')
    val folder = relativePath.substringBeforeLast('/', missingDelimiterValue = "").trim('/')
    return when {
        folder.isNotEmpty() && storageId == "primary" -> folder
        folder.isNotEmpty() -> "Storage $storageId/$folder"
        storageId == "primary" -> "Internal storage"
        else -> "Storage $storageId"
    }
}
