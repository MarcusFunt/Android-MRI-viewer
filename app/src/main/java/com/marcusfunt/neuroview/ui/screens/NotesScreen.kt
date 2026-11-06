package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.notes.ScanNote
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<ScanNote>,
    availableScanIds: Set<String>,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
    onOpenNote: (ScanNote) -> Unit,
) {
    Scaffold(
        containerColor = NeuroChrome.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (notes.isEmpty()) {
                Text(
                    "No notes yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NeuroChrome.ink,
                )
                Text(
                    "Add a note while viewing a scan. It will be saved on this device with its series and slice location.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuroChrome.muted,
                )
                TextButton(onClick = onBrowse, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Browse scans")
                }
            } else {
                Text(
                    "Saved on this device",
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeuroChrome.muted,
                )
                notes.forEachIndexed { index, note ->
                    NoteRow(
                        note = note,
                        available = note.scanId in availableScanIds,
                        onClick = { onOpenNote(note) },
                    )
                    if (index < notes.lastIndex) HorizontalDivider(color = NeuroChrome.stroke.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: ScanNote, available: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(note.seriesLabel, style = MaterialTheme.typography.titleMedium, color = NeuroChrome.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(
                    note.collectionLabel,
                    if (note.nativeStackAxis != null) {
                        "Native grid · ${note.planeName.lowercase().replaceFirstChar { it.uppercase() }} · slice ${note.sliceIndex + 1}/${note.sliceCount}"
                    } else {
                        "${note.planeName.lowercase().replaceFirstChar { it.uppercase() }} image plane · slice ${note.sliceIndex + 1}/${note.sliceCount}"
                    },
                )
                    .filter(String::isNotBlank).joinToString("  ·  "),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(note.createdAtMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.muted,
            )
        }
        if (!note.spatialTransformPresent) {
            Text(
                "Spatial transform unknown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!available) {
            Text("Unavailable · scan removed from library", style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.muted)
        }
        Text(
            note.body,
            style = MaterialTheme.typography.bodyMedium,
            color = NeuroChrome.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
