package com.marcusfunt.neuroview.catalog

/**
 * Immutable changes to the on-device MRI catalog. No operation changes the source NIfTI
 * document or its filename. Each operation validates targets before returning a snapshot.
 */
object CatalogEdits {
    fun checkedLabel(text: String): String {
        val label = text.trim()
        require(label.isNotEmpty()) { "Name cannot be empty" }
        require(label.length <= 100) { "Name must be 100 characters or fewer" }
        require(label.none { Character.isISOControl(it) }) { "Name cannot contain control characters" }
        return label
    }

    fun createCollection(
        original: ScanCatalogSnapshot,
        id: String,
        label: String,
        importedAt: Long,
    ): ScanCatalogSnapshot {
        require(id.isNotBlank() && original.collections.none { it.id == id }) {
            "Collection ID already exists"
        }
        return original.copy(
            collections = listOf(ImportCollection(id, checkedLabel(label), importedAt)) +
                original.collections,
        )
    }

    fun renameCollection(
        original: ScanCatalogSnapshot, id: String, name: String,
    ): ScanCatalogSnapshot {
        require(original.collections.any { it.id == id }) { "Collection no longer exists" }
        val label = checkedLabel(name)
        return original.copy(
            collections = original.collections.map {
                if (it.id == id) it.copy(label = label) else it
            },
        )
    }

    fun renameSeries(
        original: ScanCatalogSnapshot, id: String, alias: String,
    ): ScanCatalogSnapshot {
        require(original.scans.any { it.id == id }) { "MRI series no longer exists" }
        val name = checkedLabel(alias)
        return original.copy(
            scans = original.scans.map {
                if (it.id == id) it.copy(customName = name) else it
            },
        )
    }

    fun resetSeriesName(original: ScanCatalogSnapshot, id: String): ScanCatalogSnapshot {
        require(original.scans.any { it.id == id }) { "MRI series no longer exists" }
        return original.copy(
            scans = original.scans.map {
                if (it.id == id) it.copy(customName = null) else it
            },
        )
    }

    fun moveSeries(
        original: ScanCatalogSnapshot, seriesId: String, collectionId: String,
    ): ScanCatalogSnapshot {
        require(original.collections.any { it.id == collectionId }) {
            "Destination collection does not exist"
        }
        require(original.scans.any { it.id == seriesId }) { "MRI series no longer exists" }
        return original.copy(
            scans = original.scans.map {
                if (it.id == seriesId) it.copy(collectionId = collectionId) else it
            },
        )
    }

    fun removeSeries(original: ScanCatalogSnapshot, seriesId: String): ScanCatalogSnapshot {
        require(original.scans.any { it.id == seriesId }) { "MRI series no longer exists" }
        return original.copy(scans = original.scans.filterNot { it.id == seriesId })
    }

    /** Remove a collection plus its catalog entries — never external medical image files. */
    fun removeCollection(
        original: ScanCatalogSnapshot, collectionId: String,
    ): ScanCatalogSnapshot {
        require(original.collections.any { it.id == collectionId }) {
            "Collection no longer exists"
        }
        return original.copy(
            collections = original.collections.filterNot { it.id == collectionId },
            scans = original.scans.filterNot { it.collectionId == collectionId },
        )
    }
}
