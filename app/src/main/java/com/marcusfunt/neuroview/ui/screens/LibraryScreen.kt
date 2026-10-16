package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ImportCollection
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord

private sealed interface LibraryDialog {
    data object CreateCollection : LibraryDialog
    data class RenameCollection(val collection: ImportCollection) : LibraryDialog
    data class RemoveCollection(val collection: ImportCollection, val numberOfScans: Int) : LibraryDialog
    data class RenameScan(val scan: ScanRecord) : LibraryDialog
    data class MoveScan(val scan: ScanRecord) : LibraryDialog
    data class RemoveScan(val scan: ScanRecord) : LibraryDialog
}

/** Local library management. No action here deletes the underlying MRI document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    snapshot: ScanCatalogSnapshot,
    isBusy: Boolean,
    feedback: String?,
    onImport: () -> Unit,
    onConvertDicom: () -> Unit,
    onConvertDicomFiles: () -> Unit,
    onExportDicom: (ScanRecord) -> Unit,
    onBrowse: () -> Unit,
    onOpenScan: (ScanRecord) -> Unit,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (String, String) -> Unit,
    onRemoveCollection: (String) -> Unit,
    onRenameScan: (String, String) -> Unit,
    onResetScanName: (String) -> Unit,
    onMoveScan: (String, String) -> Unit,
    onRemoveScan: (String) -> Unit,
    onClearThumbnails: () -> Unit,
    onBack: () -> Unit,
) {
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    var search by rememberSaveable { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("MRI Library") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "ON-DEVICE WORKSPACE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text("Files & collections", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${snapshot.scans.size} volumes · ${snapshot.collections.size} collections",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Rename, organize and remove library references. " +
                            "Original MRI files and filenames are never changed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onImport,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Import NIfTI volumes") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("DICOM conversion", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Convert a DICOM series into a .nii.gz volume.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onConvertDicom,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Choose DICOM folder") }
                            TextButton(onClick = onConvertDicomFiles, enabled = !isBusy) {
                                Text("Select individual files instead")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { dialog = LibraryDialog.CreateCollection },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text("New collection") }
                        OutlinedButton(
                            onClick = onBrowse,
                            modifier = Modifier.weight(1f),
                        ) { Text("Browse series") }
                    }
                    if (isBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Saving library changes…")
                        }
                    }
                    if (!feedback.isNullOrBlank()) {
                        Text(
                            feedback,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search library") },
                        placeholder = { Text("Series or collection name") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            snapshot.collections.forEach { collection ->
                val all = snapshot.inCollection(collection.id)
                val query = search.trim()
                val groupMatches = collection.label.contains(query, ignoreCase = true)
                val visible = if (query.isBlank() || groupMatches) all else all.filter { scan ->
                    scan.label.contains(query, ignoreCase = true) ||
                        scan.displayName.contains(query, ignoreCase = true) ||
                        scan.metadata.description.contains(query, ignoreCase = true)
                }
                if (query.isBlank() || groupMatches || visible.isNotEmpty()) {
                    item(key = "group-${collection.id}") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    collection.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${all.size} linked MRI files · Collection names are local labels",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { dialog = LibraryDialog.RenameCollection(collection) },
                                        enabled = !isBusy,
                                    ) { Text("Rename") }
                                    TextButton(
                                        onClick = {
                                            dialog = LibraryDialog.RemoveCollection(collection, all.size)
                                        },
                                        enabled = !isBusy,
                                    ) { Text("Remove") }
                                }
                            }
                        }
                    }
                    items(visible, key = { "scan-${it.id}" }) { scan ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    scan.label,
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenScan(scan) },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (scan.customName != null) {
                                    Text(
                                        "Original: ${scan.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    scan.metadata.dimensions.joinToString(" × ") + " voxels · " +
                                        scan.metadata.orientation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = { onOpenScan(scan) }) {
                                        Text("Open volume")
                                    }
                                    Box {
                                        var actionsOpen by remember(scan.id) { mutableStateOf(false) }
                                        IconButton(onClick = { actionsOpen = true }) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "More actions for ${scan.label}",
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = actionsOpen,
                                            onDismissRequest = { actionsOpen = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    actionsOpen = false
                                                    dialog = LibraryDialog.RenameScan(scan)
                                                },
                                                enabled = !isBusy,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move to collection") },
                                                onClick = {
                                                    actionsOpen = false
                                                    dialog = LibraryDialog.MoveScan(scan)
                                                },
                                                enabled = !isBusy,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Export derived DICOM ZIP") },
                                                onClick = {
                                                    actionsOpen = false
                                                    onExportDicom(scan)
                                                },
                                                enabled = !isBusy,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove from library") },
                                                onClick = {
                                                    actionsOpen = false
                                                    dialog = LibraryDialog.RemoveScan(scan)
                                                },
                                                enabled = !isBusy,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (snapshot.scans.isEmpty()) {
                        Text(
                            "No MRI entries yet. Import NIfTI files or create a collection.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onClearThumbnails, enabled = !isBusy) {
                        Text("Clear cached MRI thumbnails")
                    }
                    Text(
                        "Removing entries here removes only in-app references, not files " +
                            "from device storage. DICOM exports are anonymized, derived " +
                            "Secondary Capture images, not reconstructed scanner originals.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (val current = dialog) {
        LibraryDialog.CreateCollection -> NameEditDialog(
            title = "New collection", initial = "", actionLabel = "Create",
            onDismiss = { dialog = null },
            onConfirm = { value ->
                onCreateCollection(value)
                dialog = null
            },
        )
        is LibraryDialog.RenameCollection -> NameEditDialog(
            title = "Rename collection", initial = current.collection.label,
            actionLabel = "Save",
            onDismiss = { dialog = null },
            onConfirm = { value ->
                onRenameCollection(current.collection.id, value)
                dialog = null
            },
        )
        is LibraryDialog.RenameScan -> NameEditDialog(
            title = "Rename MRI entry",
            initial = current.scan.customName ?: current.scan.displayName,
            actionLabel = "Save label",
            extraAction = if (current.scan.customName != null) "Restore original name" else null,
            onExtra = {
                onResetScanName(current.scan.id)
                dialog = null
            },
            onDismiss = { dialog = null },
            onConfirm = { value ->
                onRenameScan(current.scan.id, value)
                dialog = null
            },
        )
        is LibraryDialog.MoveScan -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Move MRI entry") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Choose a collection for ${current.scan.label}. " +
                            "The original document stays where it is stored.",
                    )
                    snapshot.collections
                        .filter { it.id != current.scan.collectionId }
                        .forEach { group ->
                            TextButton(
                                onClick = {
                                    onMoveScan(current.scan.id, group.id)
                                    dialog = null
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(group.label) }
                        }
                    if (snapshot.collections.size <= 1) {
                        Text("Create another collection first to move this MRI.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("Close") }
            },
        )
        is LibraryDialog.RemoveScan -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Remove MRI from library?") },
            text = {
                Text(
                    "Remove ${current.scan.label} from NeuroView? " +
                        "The original MRI file will NOT be deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveScan(current.scan.id)
                        dialog = null
                    },
                    enabled = !isBusy,
                ) { Text("Remove reference") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is LibraryDialog.RemoveCollection -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Remove collection?") },
            text = {
                Text(
                    "Remove ${current.collection.label} and its ${current.numberOfScans} " +
                        "MRI library entries? This does NOT delete the actual MRI files. " +
                        "To keep the entries, move them into another collection first.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveCollection(current.collection.id)
                        dialog = null
                    },
                    enabled = !isBusy,
                ) { Text("Remove collection & entries") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun NameEditDialog(
    title: String,
    initial: String,
    actionLabel: String,
    extraAction: String? = null,
    onExtra: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial, title) { mutableStateOf(initial) }
    val valid = text.trim().isNotEmpty() && text.trim().length <= 100 &&
        text.none { Character.isISOControl(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Local label") },
                    supportingText = { Text("1–100 characters. Original filenames remain unchanged.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (extraAction != null) {
                    TextButton(onClick = onExtra) { Text(extraAction) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = valid) {
                Text(actionLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
