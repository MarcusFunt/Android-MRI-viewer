package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ScanRecord
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Full source metadata is selectable and wraps, including very long source filenames. */
@Composable
internal fun ScanDetailsDialog(scan: ScanRecord, importedAt: Long?, onDismiss: () -> Unit) {
    val resources = LocalContext.current.resources
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MRI details") },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(scan.label, style = MaterialTheme.typography.titleMedium)
                    Text("Source filename\n${scan.displayName}")
                    Text("${scanFormatLabel(scan)} · ${scan.metadata.dataType}")
                    Text("Dimensions\n${scan.metadata.dimensions.joinToString(" × ")} voxels · " +
                        resources.catalogQuantityText(CatalogQuantity.FRAMES, scan.metadata.timepoints))
                    Text("Voxel spacing\n" + scan.metadata.spacingMm.joinToString(" × ") {
                        String.format(Locale.getDefault(), "%.2f", it)
                    } + " mm")
                    Text("Orientation\n${scan.metadata.orientation}")
                    if (scan.metadata.description.isNotBlank()) Text("Description\n${scan.metadata.description}")
                    importedAt?.let { Text("Imported\n${DateFormat.getDateTimeInstance().format(Date(it))}") }
                    Text("Aliases only change the Library label. The original MRI file stays where it is stored.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
