package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.notes.ScanNote
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: ScanNote,
    available: Boolean,
    onBack: () -> Unit,
    onOpenSavedSlice: () -> Unit,
) {
    Scaffold(
        containerColor = NeuroChrome.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeuroChrome.topBar),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).neuroWorkspaceBackground()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(note.seriesLabel, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, color = NeuroChrome.ink)
            Text(
                listOfNotNull(
                    note.collectionLabel.takeIf(String::isNotBlank),
                    "${note.planeName.lowercase().replaceFirstChar { it.uppercase() }} image plane · slice ${note.sliceIndex + 1}/${note.sliceCount}",
                    if (note.orientationVerified) "orientation verified" else "orientation unverified",
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(note.createdAtMillis)),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = NeuroChrome.accent,
            )
            if (available) {
                Button(onClick = onOpenSavedSlice, modifier = Modifier.fillMaxWidth()) {
                    Text("Open saved slice")
                }
            } else {
                Text(
                    "Unavailable · scan removed from library. This note is still saved on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuroChrome.muted,
                )
            }
            Text("Observation", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = NeuroChrome.ink)
            Text(note.body, style = MaterialTheme.typography.bodyLarge, color = NeuroChrome.ink)
        }
    }
}
