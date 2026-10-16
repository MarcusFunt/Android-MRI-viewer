package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.VolumeLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    volumeState: VolumeLoadState,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onOpenActive: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "OPEN FROM DEVICE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN .NII OR .NII.GZ")
            }
            when (val state = volumeState) {
                is VolumeLoadState.Ready -> {
                    Text("Current MRI", style = MaterialTheme.typography.titleMedium)
                    Text(state.displayName)
                    Text(
                        "${state.volume.nx} × ${state.volume.ny} × ${state.volume.nz} voxels · " +
                            state.volume.transformSource,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenActive, modifier = Modifier.fillMaxWidth()) {
                        Text("VIEW OPENED MRI")
                    }
                }
                VolumeLoadState.Loading -> Text("Importing file…")
                is VolumeLoadState.Failed -> Text(
                    "Could not open: ${state.explanation}",
                    color = MaterialTheme.colorScheme.error,
                )
                VolumeLoadState.Empty -> Text("No NIfTI opened yet.")
            }
            Text(
                "Phase 3 stores only the currently selected document permission. " +
                    "Multi-scan management, deletion, and DICOM conversion are later phases.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
