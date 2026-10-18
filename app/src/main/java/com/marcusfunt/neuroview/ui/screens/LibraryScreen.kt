package com.marcusfunt.neuroview.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MotionToken
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground
import com.marcusfunt.neuroview.catalog.ImportCollection
import com.marcusfunt.neuroview.catalog.ImportBatchProgress
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanCatalog
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import kotlinx.coroutines.delay

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
    catalog: ScanCatalog,
    isBusy: Boolean,
    isImporting: Boolean,
    importProgress: ImportBatchProgress?,
    importOperation: OperationSnapshot?,
    importCancelRequestedAtElapsedMs: Long?,
    onCancelImport: () -> Unit,
    activityLabel: String? = null,
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
    val motionPreference = LocalNeuroViewMotion.current
    val motionDuration = MotionToken.STANDARD.durationMs(motionPreference)
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    var search by rememberSaveable { mutableStateOf("") }


    var mode by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Recent") }
    var frameFilter by rememberSaveable { mutableStateOf("All") }
    var conversionOpen by rememberSaveable { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(snapshot.scans.isEmpty()) {
        if (snapshot.scans.isEmpty()) {
            search = ""
            mode = "All"
            sort = "Recent"
            frameFilter = "All"
        }
    }
    val query = search.trim()
    val matching = snapshot.scans.filter { scan ->
        val textMatches = query.isBlank() || scan.label.contains(query, true) ||
            scan.displayName.contains(query, true) ||
            scan.metadata.description.contains(query, true) ||
            snapshot.collections.any { it.id == scan.collectionId && it.label.contains(query, true) }
        val frameMatches = when (frameFilter) {
            "3D" -> scan.metadata.timepoints == 1
            "4D" -> scan.metadata.timepoints > 1
            else -> true
        }
        textMatches && frameMatches
    }
    val dates = remember(snapshot.collections) { snapshot.collections.associate { it.id to it.importedAt } }
    val ordered = if (sort == "Name") matching.sortedBy { it.label.lowercase() }
        else matching.sortedWith(compareByDescending<ScanRecord> { dates[it.collectionId] ?: 0L }
            .thenBy { it.label.lowercase() })

    Scaffold(
        containerColor = NeuroChrome.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("MRI Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeuroChrome.topBar),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().neuroWorkspaceBackground(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = if (importProgress != null && importOperation != null) 150.dp else 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "library-controls") {
                Column(
                    modifier = Modifier.animateContentSize(animationSpec = tween(motionDuration)),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${snapshot.scans.size} scans · ${snapshot.collections.size} collections",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeuroChrome.muted,
                        )
                        TextButton(onClick = { conversionOpen = !conversionOpen }) {
                            Text(if (conversionOpen) "Hide tools" else "Conversion tools")
                        }
                    }
                    Button(
                        onClick = onImport,
                        enabled = !isBusy && !isImporting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Import MRI volumes") }
                    if (conversionOpen) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = NeuroChrome.panel,
                            border = BorderStroke(1.dp, NeuroChrome.stroke),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("DICOM → NIfTI", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Select a compatible DICOM series and export it to NIfTI.",
                                    style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted,
                                )
                                OutlinedButton(
                                    onClick = onConvertDicom, enabled = !isBusy && !isImporting,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Select DICOM folder") }
                                TextButton(onClick = onConvertDicomFiles, enabled = !isBusy && !isImporting) {
                                    Text("Select DICOM files instead")
                                }
                            }
                        }
                    }
                    if (snapshot.scans.isNotEmpty()) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("Search scans and collections") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (search.isNotEmpty()) {
                                { IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                } }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == "All", onClick = { mode = "All" },
                            label = { Text("All scans") })
                        FilterChip(selected = mode == "Collections", onClick = { mode = "Collections" },
                            label = { Text("Collections") })
                        if (snapshot.scans.isNotEmpty()) {
                            FilterChip(selected = sort == "Name", onClick = {
                                sort = if (sort == "Name") "Recent" else "Name"
                            }, label = { Text(if (sort == "Name") "A–Z" else "Newest imports") })
                        }
                    }
                    if (snapshot.scans.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Frames", color = NeuroChrome.muted,
                                style = MaterialTheme.typography.labelSmall)
                            listOf("All", "3D", "4D").forEach { selection ->
                                FilterChip(selected = frameFilter == selection,
                                    onClick = { frameFilter = selection },
                                    label = { Text(selection) })
                            }
                        }
                    }
                    if (isBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp), strokeWidth = 2.dp,
                            )
                            Text(activityLabel ?: "Working on MRI operation…", color = NeuroChrome.muted)
                        }
                    }
                    if (!feedback.isNullOrBlank()) {
                        Surface(
                            color = NeuroChrome.panel, shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(feedback, modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (mode == "Collections") {
                        TextButton(
                            onClick = { dialog = LibraryDialog.CreateCollection },
                            enabled = !isBusy && !isImporting,
                        ) {
                            Text("+ New collection")
                        }
                    }
                }
            }
            if (matching.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        color = NeuroChrome.panel,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, NeuroChrome.stroke),
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (snapshot.scans.isEmpty()) "Your library is empty"
                                else "No matching scans",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (snapshot.scans.isEmpty()) "Import a NIfTI volume or convert a DICOM series to get started."
                                else "Try another name or clear the search filter.",
                                color = NeuroChrome.muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (search.isNotBlank()) {
                                TextButton(onClick = { search = "" }) { Text("Clear search") }
                            }
                        }
                    }
                }
            } else if (mode == "All") {
                items(ordered, key = { "all-${it.id}" }) { scan ->
                    LibraryScanRow(
                    scan = scan, catalog = catalog, busy = isBusy || isImporting,
                        onOpen = { onOpenScan(scan) },
                        onRename = { dialog = LibraryDialog.RenameScan(scan) },
                        onMove = { dialog = LibraryDialog.MoveScan(scan) },
                        onExport = { onExportDicom(scan) },
                        onRemove = { dialog = LibraryDialog.RemoveScan(scan) },
                    )
                }
            } else {
                snapshot.collections.sortedByDescending { it.importedAt }.forEach { collection ->
                    val records = ordered.filter { it.collectionId == collection.id }
                    if (records.isNotEmpty() || query.isBlank()) {
                        item(key = "collection-${collection.id}") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = NeuroChrome.raised,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { expanded[collection.id] = expanded[collection.id] == false }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (expanded[collection.id] == false)
                                            Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Expand or collapse collection",
                                        tint = NeuroChrome.accent,
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(collection.label, style = MaterialTheme.typography.titleSmall)
                                        Text("${records.size} matching scans", style = MaterialTheme.typography.labelSmall,
                                            color = NeuroChrome.muted)
                                    }
                                    var menu by remember(collection.id) { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Collection actions")
                                        }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Rename collection") },
                                                enabled = !isBusy && !isImporting,
                                                onClick = { menu = false; dialog = LibraryDialog.RenameCollection(collection) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove collection") },
                                                enabled = !isBusy && !isImporting,
                                                onClick = { menu = false; dialog = LibraryDialog.RemoveCollection(collection, snapshot.inCollection(collection.id).size) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (expanded[collection.id] != false) {
                            items(records, key = { "collection-scan-${it.id}" }) { scan ->
                                LibraryScanRow(
                                    scan = scan, catalog = catalog, busy = isBusy || isImporting,
                                    onOpen = { onOpenScan(scan) },
                                    onRename = { dialog = LibraryDialog.RenameScan(scan) },
                                    onMove = { dialog = LibraryDialog.MoveScan(scan) },
                                    onExport = { onExportDicom(scan) },
                                    onRemove = { dialog = LibraryDialog.RemoveScan(scan) },
                                )
                            }
                        }
                    }
                }
            }
            if (snapshot.scans.isNotEmpty()) {
                item(key = "library-footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onClearThumbnails, enabled = !isBusy && !isImporting) {
                            Text("Clear preview cache")
                        }
                        Text(
                            "Collections are personal labels, not verified clinical studies. " +
                                "Removing an entry never deletes its original MRI document.",
                            style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = importProgress != null && importOperation != null,
            enter = fadeIn(tween(motionDuration)) + slideInVertically(
                animationSpec = tween(motionDuration),
                initialOffsetY = { it / 4 },
            ),
            exit = fadeOut(tween(motionDuration)) + slideOutVertically(
                animationSpec = tween(motionDuration),
                targetOffsetY = { it / 4 },
            ),
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        ) {
            if (importProgress != null && importOperation != null) {
                ImportProgressStrip(
                    progress = importProgress,
                    operation = importOperation,
                    cancelRequestedAtElapsedMs = importCancelRequestedAtElapsedMs,
                    onCancel = onCancelImport,
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
                                enabled = !isBusy && !isImporting,
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
                    enabled = !isBusy && !isImporting,
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
                    enabled = !isBusy && !isImporting,
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

@Composable
internal fun ImportProgressStrip(
    progress: ImportBatchProgress,
    operation: OperationSnapshot,
    cancelRequestedAtElapsedMs: Long?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedNow by remember(operation.id) {
        mutableLongStateOf(SystemMonotonicClock.nowMs())
    }
    LaunchedEffect(operation.id) {
        while (true) {
            elapsedNow = SystemMonotonicClock.nowMs()
            delay(1_000L)
        }
    }
    val fraction = if (progress.totalFiles == 0) 0f else {
        progress.filesProcessed.toFloat() / progress.totalFiles.toFloat()
    }.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(MotionToken.QUICK.durationMs(LocalNeuroViewMotion.current)),
        label = "Import progress",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NeuroChrome.raised,
        border = BorderStroke(1.dp, NeuroChrome.stroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Importing MRI files",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (operation.status == OperationStatus.CANCELING) {
                    Text("Stopping…", color = NeuroChrome.muted, style = MaterialTheme.typography.labelLarge)
                } else {
                    TextButton(onClick = onCancel) { Text("CANCEL") }
                }
            }
            Text(
                progress.currentFileName?.let {
                    "Checking $it · file ${progress.filesProcessed + 1} of ${progress.totalFiles}"
                } ?: "Checked ${progress.filesProcessed} of ${progress.totalFiles} files",
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = NeuroChrome.ink,
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth(),
                color = NeuroChrome.accent,
                trackColor = NeuroChrome.stroke.copy(alpha = 0.55f),
            )
            Text(
                "${progress.added} added · ${progress.skipped} skipped · ${progress.failed} failed",
                style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.muted,
            )
            val statusMessage = when (operation.status) {
                OperationStatus.WAITING_FOR_PROVIDER -> "Waiting for the selected file provider…"
                OperationStatus.SLOW -> "Taking longer than usual"
                OperationStatus.SUSPECTED_STALL -> "This file check may be stuck"
                else -> null
            }
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = NeuroChrome.muted)
            }
            if (operation.status == OperationStatus.CANCELING &&
                cancelRequestedAtElapsedMs != null &&
                elapsedNow - cancelRequestedAtElapsedMs >= 8_000L
            ) {
                Text(
                    "The file provider has not confirmed cancellation. The Library remains available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeuroChrome.muted,
                )
            }
            val elapsedSeconds = ((elapsedNow - operation.startedAtElapsedMs).coerceAtLeast(0L) / 1_000L)
            Text(
                "Elapsed ${String.format(java.util.Locale.ROOT, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)}",
                style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.muted,
            )
        }
    }
}
