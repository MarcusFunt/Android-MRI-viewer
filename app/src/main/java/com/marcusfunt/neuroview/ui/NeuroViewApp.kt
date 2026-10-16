package com.marcusfunt.neuroview.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
import kotlinx.coroutines.withContext

sealed interface VolumeLoadState {
    data object Empty : VolumeLoadState
    data object Loading : VolumeLoadState
    data class Ready(val displayName: String, val volume: NiftiVolume) : VolumeLoadState
    data class Failed(val explanation: String) : VolumeLoadState
}

/**
 * Phase 3 holds one active NIfTI document. The URI is saved across Activity recreation,
 * and reloaded through the Android Storage Access Framework without broad storage access.
 * A persistent multi-document library is Phase 5.
 */
@Composable
fun NeuroViewApp() {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(Destination.Home)
    val navigate: (Destination) -> Unit = { NavigationActions.navigate(backStack, it) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }

    val volumeState by produceState<VolumeLoadState>(VolumeLoadState.Empty, selectedUri) {
        val source = selectedUri
        if (source == null) {
            value = VolumeLoadState.Empty
        } else {
            value = VolumeLoadState.Loading
            value = try {
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(source)
                    val displayName = runCatching {
                        context.contentResolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: "Opened NIfTI volume"
                    val volume = context.contentResolver.openInputStream(uri)?.use {
                        NiftiReader.read(it)
                    } ?: throw IllegalArgumentException("Unable to open selected file")
                    VolumeLoadState.Ready(displayName, volume)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                VolumeLoadState.Failed(error.message ?: "Unable to read NIfTI volume")
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            selectedUri = uri.toString()
            navigate(Destination.Viewer("active"))
        }
    }
    val pickNifti: () -> Unit = { launcher.launch(arrayOf("*/*")) }

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
                        volumeState = volumeState,
                        onImport = pickNifti,
                        onBack = { NavigationActions.back(backStack) },
                        onSelectActive = { navigate(Destination.Viewer("active")) },
                    )
                }
                entry<Destination.Library> {
                    LibraryScreen(
                        volumeState = volumeState,
                        onImport = pickNifti,
                        onBack = { NavigationActions.back(backStack) },
                        onOpenActive = { navigate(Destination.Viewer("active")) },
                    )
                }
                entry<Destination.Viewer> {
                    ViewerScreen(
                        volumeState = volumeState,
                        onImport = pickNifti,
                        onNavigate = navigate,
                    )
                }
            },
        )
    }
}
