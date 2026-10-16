package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.VolumeLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    volumeState: VolumeLoadState,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onSelectActive: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("MRI series") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "PHASE 3 · SINGLE NIFTI VOLUME",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Open an MRI file to view real axial, coronal, and sagittal slices. " +
                    "DICOM series browsing will arrive in Phase 4.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN NIFTI (.NII / .NII.GZ)")
            }
            when (val state = volumeState) {
                is VolumeLoadState.Ready -> Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSelectActive),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                state.displayName,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${state.volume.nx} × ${state.volume.ny} × ${state.volume.nz} voxels",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Tap to view", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("›", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                VolumeLoadState.Loading -> Text("Reading NIfTI volume…")
                is VolumeLoadState.Failed -> Text("Import error: ${state.explanation}", color = MaterialTheme.colorScheme.error)
                VolumeLoadState.Empty -> Text("No file opened yet.")
            }
        }
    }
}
