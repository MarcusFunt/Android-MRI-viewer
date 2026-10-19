package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ScanCatalog
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import java.text.DateFormat
import java.util.Date

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
    importedAt: Long? = null,
) {
    var actionsOpen by remember(scan.id) { mutableStateOf(false) }
    var detailsOpen by remember(scan.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanThumbnail(scan, catalog)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    scan.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    scanFormatLabel(scan) + " · ${if (scan.metadata.timepoints > 1) "4D" else "3D"} · " +
                        scan.metadata.dimensions.joinToString(" × "),
                    color = NeuroChrome.muted, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                importedAt?.let {
                    Text("Imported ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}",
                        color = NeuroChrome.muted, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { actionsOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${scan.label}")
                }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    DropdownMenuItem(text = { Text("Open volume") }, onClick = {
                        actionsOpen = false; onOpen()
                    })
                    DropdownMenuItem(text = { Text("MRI details / source filename") }, onClick = {
                        actionsOpen = false; detailsOpen = true
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
        HorizontalDivider(color = NeuroChrome.stroke.copy(alpha = 0.5f))
    }
    if (detailsOpen) ScanDetailsDialog(scan, importedAt, onDismiss = { detailsOpen = false })
}
