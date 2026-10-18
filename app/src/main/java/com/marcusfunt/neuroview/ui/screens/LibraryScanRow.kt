package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ScanCatalog
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.ui.theme.NeuroChrome

/** The same preview-rich row is used in grouped and flat library views. */
@Composable
internal fun LibraryScanRow(
    scan: ScanRecord,
    catalog: ScanCatalog,
    busy: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    var actionsOpen by remember(scan.id) { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, NeuroChrome.stroke),
        colors = CardDefaults.cardColors(containerColor = NeuroChrome.panel),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScanThumbnail(scan, catalog)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    scan.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    scan.metadata.dimensions.joinToString(" × ") + " voxels",
                    color = NeuroChrome.muted, style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    scan.metadata.spacingMm.joinToString(" × ") {
                        String.format(java.util.Locale.US, "%.2f", it).trimEnd('0').trimEnd('.')
                    } + " mm",
                    style = MaterialTheme.typography.labelSmall, color = NeuroChrome.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    scan.metadata.orientation,
                    color = if (scan.metadata.orientation == "unknown")
                        MaterialTheme.colorScheme.error else NeuroChrome.muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { actionsOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${scan.label}")
                }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    DropdownMenuItem(text = { Text("Open volume") }, onClick = {
                        actionsOpen = false; onOpen()
                    })
                    DropdownMenuItem(text = { Text("Rename") }, enabled = !busy, onClick = {
                        actionsOpen = false; onRename()
                    })
                    DropdownMenuItem(text = { Text("Move to collection") }, enabled = !busy, onClick = {
                        actionsOpen = false; onMove()
                    })
                    DropdownMenuItem(text = { Text("Export derived DICOM ZIP") }, enabled = !busy, onClick = {
                        actionsOpen = false; onExport()
                    })
                    DropdownMenuItem(text = { Text("Remove from library") }, enabled = !busy, onClick = {
                        actionsOpen = false; onRemove()
                    })
                }
            }
        }
    }
}
