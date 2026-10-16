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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    snapshot: ScanCatalogSnapshot,
    isImporting: Boolean,
    onImport: () -> Unit,
    onBrowse: () -> Unit,
    onBack: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "LOCAL MRI REFERENCES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${snapshot.scans.size} NIfTI files in ${snapshot.collections.size} import collections",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "The app remembers document links you select. Original MRI files remain " +
                    "in their original location on your device or storage provider.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onImport,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isImporting) "INDEXING SERIES…" else "IMPORT NIFTI FILES")
            }
            OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("BROWSE SCANS & SERIES")
            }
            Text(
                "Phase 4: read-only file indexing and series previews. " +
                    "Renaming, deleting, and managing library storage are Phase 5. " +
                    "DICOM import and conversion are planned separately.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
