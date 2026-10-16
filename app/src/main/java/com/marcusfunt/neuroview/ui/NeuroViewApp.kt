package com.marcusfunt.neuroview.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.marcusfunt.neuroview.catalog.ScanCatalog
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.mri.NiftiReader
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.navigation.NavigationActions
import com.marcusfunt.neuroview.ui.screens.HomeScreen
import com.marcusfunt.neuroview.ui.screens.LibraryScreen
import com.marcusfunt.neuroview.ui.screens.SeriesScreen
import com.marcusfunt.neuroview.ui.screens.ViewerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VolumeLoadState {
    data object Empty : VolumeLoadState
    data object Loading : VolumeLoadState
    data class Ready(val displayName: String, val volume: NiftiVolume) : VolumeLoadState
    data class Failed(val explanation: String) : VolumeLoadState
}

/** Phase 4 separates a persisted read-only series catalog from one active in-memory volume. */
@Composable
fun NeuroViewApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversions = rememberConversionControls()
    val catalog = remember(context.applicationContext) { ScanCatalog(context.applicationContext) }
    var snapshot by remember { mutableStateOf(catalog.load()) }
    var importBusy by remember { mutableStateOf(false) }
    var editBusy by remember { mutableStateOf(false) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedScanId by rememberSaveable { mutableStateOf<String?>(null) }

    fun updateCatalog(description: String, action: suspend () -> ScanCatalogSnapshot) {
        if (importBusy || editBusy || conversions.busy) return
        editBusy = true
        libraryMessage = null
        scope.launch {
            try {
                snapshot = action()
                libraryMessage = description
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                libraryMessage = "Library operation failed: ${error.message ?: "Unknown error"}"
            } finally {
                editBusy = false
            }
        }
    }


    val backStack = rememberNavBackStack(Destination.Home)
    val navigate: (Destination) -> Unit = { NavigationActions.navigate(backStack, it) }

    val volumeState by produceState<VolumeLoadState>(VolumeLoadState.Empty, selectedUri) {
        val source = selectedUri
        if (source == null) {
            value = VolumeLoadState.Empty
        } else {
            value = VolumeLoadState.Loading
            value = try {
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(source)
                    val name = runCatching {
                        context.contentResolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: "Opened NIfTI volume"
                    val volume = context.contentResolver.openInputStream(uri)?.use {
                        NiftiReader.read(it)
                    } ?: throw IllegalArgumentException("Unable to open selected volume")
                    VolumeLoadState.Ready(name, volume)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                VolumeLoadState.Failed(
                    exception.message ?: "Could not access or decode the NIfTI volume",
                )
            } catch (_: OutOfMemoryError) {
                VolumeLoadState.Failed(
                    "Device is low on memory. Try a smaller NIfTI volume.",
                )
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { documents ->
        if (documents.isNotEmpty()) {
            importBusy = true
            importMessage = null
            scope.launch {
                try {
                    val result = catalog.importDocuments(documents)
                    snapshot = catalog.load()
                    val summary = "Added ${result.added} NIfTI series; " +
                        "skipped ${result.skipped} duplicate or unsupported files."
                    importMessage = if (result.errors.isEmpty()) {
                        summary
                    } else {
                        summary + "\n" + result.errors.joinToString("\n")
                    }
                    navigate(Destination.Series)
                } catch (exception: Exception) {
                    importMessage = exception.message ?: "Import failed"
                    navigate(Destination.Series)
                } finally {
                    importBusy = false
                }
            }
        }
    }
    val chooseDocuments: () -> Unit = {
        if (!importBusy && !conversions.busy) picker.launch(arrayOf("*/*"))
    }

    val openScan: (ScanRecord) -> Unit = { scan ->
        selectedUri = scan.documentUri
        selectedScanId = scan.id
        navigate(Destination.Viewer(scan.id))
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { NavigationActions.back(backStack) },
            entryProvider = entryProvider {
                entry<Destination.Home> {
                    HomeScreen(
                        onView = { navigate(Destination.Series) },
                        onLibrary = { navigate(Destination.Library) },
                    )
                }
                entry<Destination.Series> {
                    SeriesScreen(
                        snapshot = snapshot,
                        catalog = catalog,
                        isImporting = importBusy,
                        importMessage = importMessage,
                        onImport = chooseDocuments,
                        onBack = { NavigationActions.back(backStack) },
                        onSelectSeries = openScan,
                    )
                }
                entry<Destination.Library> {
                    LibraryScreen(
                        snapshot = snapshot,
                        isBusy = importBusy || editBusy || conversions.busy,
                        feedback = conversions.message ?: libraryMessage ?: importMessage,
                        onConvertDicom = conversions.selectDicomSeries,
                        onExportDicom = conversions.exportNiftiAsDicom,
                        onImport = chooseDocuments,
                        onBrowse = { navigate(Destination.Series) },
                        onOpenScan = openScan,
                        onCreateCollection = { label ->
                            updateCatalog("Collection created") { catalog.createCollection(label) }
                        },
                        onRenameCollection = { id, name ->
                            updateCatalog("Collection renamed") { catalog.renameCollection(id, name) }
                        },
                        onRemoveCollection = { id ->
                            val removed = snapshot.inCollection(id)
                            updateCatalog("Collection and references removed; original files untouched") {
                                val updated = catalog.removeCollection(id)
                                if (removed.any { it.id == selectedScanId }) {
                                    selectedScanId = null
                                    selectedUri = null
                                }
                                updated
                            }
                        },
                        onRenameScan = { id, name ->
                            updateCatalog("MRI label updated") { catalog.renameSeries(id, name) }
                        },
                        onResetScanName = { id ->
                            updateCatalog("Original filename label restored") {
                                catalog.resetSeriesName(id)
                            }
                        },
                        onMoveScan = { id, collectionId ->
                            updateCatalog("MRI moved to another collection") {
                                catalog.moveSeries(id, collectionId)
                            }
                        },
                        onRemoveScan = { id ->
                            updateCatalog("MRI reference removed; source file untouched") {
                                val updated = catalog.removeSeries(id)
                                if (selectedScanId == id) {
                                    selectedScanId = null
                                    selectedUri = null
                                }
                                updated
                            }
                        },
                        onClearThumbnails = {
                            if (!importBusy && !editBusy) {
                                editBusy = true
                                scope.launch {
                                    try {
                                        val count = catalog.clearThumbnailCache()
                                        libraryMessage = "Cleared $count cached thumbnails"
                                    } catch (error: Exception) {
                                        libraryMessage = error.message ?: "Cache cleanup failed"
                                    } finally {
                                        editBusy = false
                                    }
                                }
                            }
                        },
                        onBack = { NavigationActions.back(backStack) },
                    )
                }
                entry<Destination.Viewer> { destination ->
                    ViewerScreen(
                        volumeState = volumeState,
                        displayNameOverride = snapshot.scans.firstOrNull {
                            it.id == destination.seriesId
                        }?.label,
                        onImport = chooseDocuments,
                        onNavigate = navigate,
                    )
                }
            },
        )
    }
}
