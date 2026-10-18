package com.marcusfunt.neuroview.catalog

/** Pure per-file catalog transition; each result can be persisted before checking the next URI. */
internal data class ImportCatalogCommit(
    val snapshot: ScanCatalogSnapshot,
    val collection: ImportCollection,
    val record: ScanRecord,
)

internal fun commitImportedRecord(
    snapshot: ScanCatalogSnapshot,
    batchCollection: ImportCollection?,
    proposedCollection: ImportCollection,
    record: ScanRecord,
): ImportCatalogCommit {
    val collection = batchCollection ?: proposedCollection
    val committedRecord = record.copy(collectionId = collection.id)
    return ImportCatalogCommit(
        snapshot = snapshot.copy(
            collections = if (batchCollection == null) {
                listOf(collection) + snapshot.collections
            } else snapshot.collections,
            scans = listOf(committedRecord) + snapshot.scans,
        ),
        collection = collection,
        record = committedRecord,
    )
}
