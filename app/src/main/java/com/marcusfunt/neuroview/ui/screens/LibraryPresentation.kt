package com.marcusfunt.neuroview.ui.screens

import android.content.res.Resources
import com.marcusfunt.neuroview.R
import com.marcusfunt.neuroview.catalog.ImportResult
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord
import java.util.Locale

internal enum class CatalogQuantity(val resourceName: String, val resourceId: Int) {
    SCANS("library_scans", R.plurals.library_scans),
    COLLECTIONS("library_collections", R.plurals.library_collections),
    MATCHING_SCANS("library_matching_scans", R.plurals.library_matching_scans),
    MRIS_ADDED("import_mris_added", R.plurals.import_mris_added),
    MRIS_KEPT("import_mris_kept", R.plurals.import_mris_kept),
    SKIPPED("import_skipped", R.plurals.import_skipped),
    FAILED("import_failed", R.plurals.import_failed),
    MRI_ENTRIES("library_mri_entries", R.plurals.library_mri_entries),
    FILES_CHECKED("import_files_checked", R.plurals.import_files_checked),
    FRAMES("scan_frames", R.plurals.scan_frames),
}

internal fun Resources.catalogQuantityText(quantity: CatalogQuantity, count: Int): String =
    getQuantityString(quantity.resourceId, count, count)

internal fun librarySummary(scans: Int, collections: Int, quantity: (CatalogQuantity, Int) -> String): String =
    "${quantity(CatalogQuantity.SCANS, scans)} · ${quantity(CatalogQuantity.COLLECTIONS, collections)}"

/** Keep skipped/failed counts distinct and retain each actionable inspection error. */
internal fun importResultSummary(result: ImportResult, quantity: (CatalogQuantity, Int) -> String): String {
    val summary = buildList {
        add(quantity(CatalogQuantity.MRIS_ADDED, result.added))
        if (result.skipped > 0) add(quantity(CatalogQuantity.SKIPPED, result.skipped))
        if (result.failed > 0) add(quantity(CatalogQuantity.FAILED, result.failed))
    }.joinToString(" · ")
    return (listOf(summary) + result.errors).joinToString("\n")
}

internal fun importCanceledSummary(result: ImportResult, processed: Int, total: Int,
    quantity: (CatalogQuantity, Int) -> String): String {
    val outcome = buildList {
        add(quantity(CatalogQuantity.MRIS_KEPT, result.added))
        if (result.skipped > 0) add(quantity(CatalogQuantity.SKIPPED, result.skipped))
        if (result.failed > 0) add(quantity(CatalogQuantity.FAILED, result.failed))
    }.joinToString(" · ")
    return (listOf("Import canceled · file checks: $processed / $total", outcome) + result.errors).joinToString("\n")
}

/** Presentation only: aliases, source names and collection labels remain searchable. */
internal fun libraryScans(snapshot: ScanCatalogSnapshot, query: String, sort: String, frames: String): List<ScanRecord> {
    val search = query.trim()
    val collections = snapshot.collections.associateBy { it.id }
    val matching = snapshot.scans.filter { scan ->
        val textMatches = search.isBlank() || scan.label.contains(search, true) ||
            scan.displayName.contains(search, true) || scan.metadata.description.contains(search, true) ||
            collections[scan.collectionId]?.label?.contains(search, true) == true
        val frameMatches = when (frames) {
            "3D" -> scan.metadata.timepoints == 1
            "4D" -> scan.metadata.timepoints > 1
            else -> true
        }
        textMatches && frameMatches
    }
    val nameOrder: (ScanRecord) -> String = { it.label.lowercase(Locale.ROOT) }
    return if (sort == "Name") matching.sortedBy(nameOrder)
    else matching.sortedWith(compareByDescending<ScanRecord> { collections[it.collectionId]?.importedAt ?: 0L }
        .thenBy(nameOrder))
}

internal fun shouldShowHomePreview(visible: Boolean, resumed: Boolean): Boolean = visible && resumed

internal fun scanFormatLabel(scan: ScanRecord): String =
    if (scan.displayName.endsWith(".nii.gz", ignoreCase = true)) "NIfTI · gzip" else "NIfTI"
