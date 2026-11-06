package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.notes.ScanNote
import com.marcusfunt.neuroview.notes.ScanNoteArchiveCodec
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: ScanNote,
    available: Boolean,
    availableScans: List<ScanRecord>,
    relinkStatus: String? = null,
    onRelink: (ScanRecord) -> Unit,
    onBack: () -> Unit,
    onOpenSavedSlice: () -> Unit,
) {
    var relinkPickerOpen by rememberSaveable(note.id) { mutableStateOf(false) }
    var pendingRelinkScan by remember(note.id) { mutableStateOf<ScanRecord?>(null) }
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
                    if (note.nativeStackAxis != null) {
                        "Native grid · ${note.planeName.lowercase().replaceFirstChar { it.uppercase() }} · slice ${note.sliceIndex + 1}/${note.sliceCount}"
                    } else {
                        "${note.planeName.lowercase().replaceFirstChar { it.uppercase() }} image plane · slice ${note.sliceIndex + 1}/${note.sliceCount}"
                    },
                    if (note.spatialTransformPresent) "spatial transform available" else "spatial transform unknown",
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
                Text(
                    "Original source: ${note.sourceDisplayName ?: note.seriesLabel}. Restored notes are never attached by catalog ID alone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeuroChrome.muted,
                )
                TextButton(
                    onClick = { relinkPickerOpen = true },
                    enabled = availableScans.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Relink to scan") }
                if (availableScans.isEmpty()) {
                    Text("Import a scan into the Library before relinking this note.",
                        style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted)
                }
            }
            Text("Observation", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = NeuroChrome.ink)
            Text(note.body, style = MaterialTheme.typography.bodyLarge, color = NeuroChrome.ink)
            relinkStatus?.let {
                Text(
                    it,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuroChrome.muted,
                )
            }
        }
    }

    if (relinkPickerOpen) {
        AlertDialog(
            onDismissRequest = { relinkPickerOpen = false },
            title = { Text("Choose a scan") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "A header fingerprint helps compare the backup, but does not prove that voxel data is identical. Confirm the correct scan manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeuroChrome.muted,
                    )
                    availableScans.forEach { scan ->
                        val metadataMatches = note.sourceFingerprint != null &&
                            ScanNoteArchiveCodec.fingerprint(scan) == note.sourceFingerprint
                        TextButton(
                            onClick = {
                                relinkPickerOpen = false
                                pendingRelinkScan = scan
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${scan.label} · ${scan.metadata.dimensions.joinToString(" × ")}",
                                    color = NeuroChrome.ink,
                                )
                                Text(
                                    when {
                                        note.sourceFingerprint == null -> "No saved metadata fingerprint"
                                        metadataMatches -> "Fingerprint matches backup"
                                        else -> "Metadata differs"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (metadataMatches) NeuroChrome.accent else NeuroChrome.muted,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { relinkPickerOpen = false }) { Text("Cancel") } },
        )
    }

    pendingRelinkScan?.let { scan ->
        val metadataMatches = note.sourceFingerprint != null &&
            ScanNoteArchiveCodec.fingerprint(scan) == note.sourceFingerprint
        AlertDialog(
            onDismissRequest = { pendingRelinkScan = null },
            title = { Text("Confirm relink") },
            text = {
                Text(
                    if (metadataMatches) {
                        "The selected scan metadata matches the backup. Recheck the saved slice after opening; the source file itself is not verified."
                    } else {
                        "Metadata differs from the saved fingerprint or is unavailable. Attach only if you verified this is the correct scan. The saved slice may not correspond to the same anatomy."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRelink(scan)
                    pendingRelinkScan = null
                }) { Text("Relink note") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRelinkScan = null }) { Text("Cancel") }
            },
        )
    }
}
