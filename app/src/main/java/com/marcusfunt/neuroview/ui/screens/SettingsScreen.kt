package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.ViewerRenderPolicy
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    responsiveScrollEnabled: Boolean,
    onResponsiveScrollChange: (Boolean) -> Unit,
    scrollPreviewQuality: Float,
    onScrollPreviewQualityChange: (Float) -> Unit,
    autoRotate: Boolean,
    onAutoRotateChange: (Boolean) -> Unit,
    glowEnabled: Boolean,
    onGlowEnabledChange: (Boolean) -> Unit,
    onResetOrientation: () -> Unit,
    onClearCache: suspend () -> Int,
    onExportNotesBackup: () -> Unit,
    onImportNotesBackup: () -> Unit,
    noteArchiveBusy: Boolean,
    noteArchiveStatus: String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var cacheStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var cacheBusy by remember { mutableStateOf(false) }
    var noteArchiveAction by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = NeuroChrome.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Viewing",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = NeuroChrome.ink,
            )
            SettingSwitchRow(
                title = "Responsive scrolling",
                detail = "Use a smaller preview while moving through slices.",
                checked = responsiveScrollEnabled,
                onCheckedChange = onResponsiveScrollChange,
            )
            Text(
                "Full image quality returns when you stop moving.",
                style = MaterialTheme.typography.bodySmall,
                color = NeuroChrome.muted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Scrolling image quality", modifier = Modifier.weight(1f))
                    Text(
                        "${(scrollPreviewQuality * 100).toInt()}%",
                        color = NeuroChrome.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    "Preview resolution relative to the stable image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeuroChrome.muted,
                )
                Slider(
                    value = scrollPreviewQuality.coerceIn(
                        ViewerRenderPolicy.MIN_PREVIEW_QUALITY,
                        ViewerRenderPolicy.MAX_PREVIEW_QUALITY,
                    ),
                    onValueChange = onScrollPreviewQualityChange,
                    valueRange = ViewerRenderPolicy.MIN_PREVIEW_QUALITY..
                        ViewerRenderPolicy.MAX_PREVIEW_QUALITY,
                    enabled = responsiveScrollEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeuroChrome.accent,
                        activeTrackColor = NeuroChrome.accent,
                        inactiveTrackColor = NeuroChrome.stroke,
                        disabledThumbColor = NeuroChrome.muted,
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Faster", style = MaterialTheme.typography.labelSmall, color = NeuroChrome.muted)
                    Text("More detail", style = MaterialTheme.typography.labelSmall, color = NeuroChrome.muted)
                }
            }

            HorizontalDivider(color = NeuroChrome.stroke.copy(alpha = 0.7f))
            Text(
                "Home appearance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = NeuroChrome.ink,
            )
            SettingSwitchRow(
                title = "Automatically rotate brain",
                checked = autoRotate,
                onCheckedChange = onAutoRotateChange,
            )
            SettingSwitchRow(
                title = "Ambient background glow",
                checked = glowEnabled,
                onCheckedChange = onGlowEnabledChange,
            )
            TextButton(onClick = onResetOrientation) {
                Text("Reset brain orientation", color = NeuroChrome.accent)
            }

            HorizontalDivider(color = NeuroChrome.stroke.copy(alpha = 0.7f))
            Text(
                "Privacy & storage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = NeuroChrome.ink,
            )
            Text(
                "Scanning, viewing, notes and format conversion stay on this device. " +
                    "Optional BrainSuite processing uploads only after your explicit consent.",
                style = MaterialTheme.typography.bodySmall,
                color = NeuroChrome.muted,
            )
            Text(
                "Notes backups contain observation text, scan labels, slice locations, and a one-way " +
                    "hash of NIfTI header metadata. They do not contain MRI images or document-provider " +
                    "links. Note text and labels may still contain sensitive personal or medical information.",
                style = MaterialTheme.typography.bodySmall,
                color = NeuroChrome.muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { noteArchiveAction = "export" },
                    enabled = !noteArchiveBusy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Export notes") }
                TextButton(
                    onClick = { noteArchiveAction = "import" },
                    enabled = !noteArchiveBusy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Import notes backup") }
            }
            if (noteArchiveBusy) Text("Working with notes backup…",
                style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted)
            noteArchiveStatus?.let {
                Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted)
            }
            TextButton(
                enabled = !cacheBusy,
                onClick = {
                    cacheBusy = true
                    cacheStatus = null
                    scope.launch {
                        cacheStatus = try {
                            "Cleared ${onClearCache()} generated MRI previews. Original files unchanged."
                        } catch (error: Exception) {
                            "Could not clear previews: ${error.message ?: "Unknown error"}"
                        } finally {
                            cacheBusy = false
                        }
                    }
                },
            ) { Text(if (cacheBusy) "Clearing previews…" else "Clear cached MRI previews") }
            cacheStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted) }
            Text(
                "NeuroView is for exploration and research, not medical diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = NeuroChrome.muted,
            )
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Done")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    noteArchiveAction?.let { action ->
        val exporting = action == "export"
        AlertDialog(
            onDismissRequest = { noteArchiveAction = null },
            title = { Text(if (exporting) "Export notes backup" else "Import notes backup") },
            text = {
                Text(
                    if (exporting) {
                        "Note backups may contain sensitive personal or medical information. " +
                            "Save only to a location you trust. The archive contains note text and scan " +
                            "metadata, not MRI files or file-provider access."
                    } else {
                        "Import merges notes without deleting existing entries. Identical notes are " +
                            "skipped, conflicting notes are kept separately, and notes without their " +
                            "original scan require manual relinking. MRI files and provider permissions " +
                            "are not imported. Choose a backup you trust."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteArchiveAction = null
                    if (exporting) onExportNotesBackup() else onImportNotesBackup()
                }) { Text(if (exporting) "Choose backup location" else "Choose backup file") }
            },
            dismissButton = {
                TextButton(onClick = { noteArchiveAction = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    detail: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
