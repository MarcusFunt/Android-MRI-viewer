package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.catalog.ImportCollection
import com.marcusfunt.neuroview.catalog.ScanCatalog
import com.marcusfunt.neuroview.catalog.ScanCatalogSnapshot
import com.marcusfunt.neuroview.catalog.ScanRecord
import java.util.Locale
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    snapshot: ScanCatalogSnapshot,
    catalog: ScanCatalog,
    isImporting: Boolean,
    importMessage: String?,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onSelectSeries: (ScanRecord) -> Unit,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val matchedCount = snapshot.scans.count { scan ->
        search.isBlank() || scan.label.contains(search, true) ||
            scan.displayName.contains(search, true) ||
            scan.metadata.description.contains(search, true) ||
            snapshot.collections.any { it.id == scan.collectionId && it.label.contains(search, true) }
    }

    Scaffold(
        containerColor = NeuroChrome.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Series browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeuroChrome.topBar,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).neuroWorkspaceBackground(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "SERIES BROWSER",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeuroChrome.accent,
                    )
                    Text(
                        "Explore scans",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Your local MRI volumes, organized for quick viewing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onImport,
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                            .background(NeuroChrome.primaryGradient, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isImporting) "Importing…" else "Import NIfTI volumes")
                    }
                    if (isImporting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Inspecting MRI headers…")
                        }
                    }
                    if (!importMessage.isNullOrBlank()) {
                        Surface(
                            color = NeuroChrome.panel,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                importMessage,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (snapshot.scans.isNotEmpty()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Search MRI volumes") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (search.isBlank()) "All series" else "Search results",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "$matchedCount of ${snapshot.scans.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (snapshot.scans.isEmpty() && !isImporting) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeuroChrome.panel,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, NeuroChrome.stroke),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No scans yet", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Import one or more .nii or .nii.gz volumes to build your local library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (matchedCount == 0 && !isImporting) {
                item {
                    Text(
                        "No volumes match your search.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            snapshot.collections.forEach { group ->
                val all = snapshot.inCollection(group.id)
                val records = if (search.isBlank() || group.label.contains(search, true)) all
                    else all.filter { scan ->
                        scan.label.contains(search, true) ||
                            scan.displayName.contains(search, true) ||
                            scan.metadata.description.contains(search, true)
                    }
                if (records.isNotEmpty()) {
                    val isExpanded = search.isNotBlank() || expanded[group.id] != false
                    item(key = "group-${group.id}") {
                        CollectionHeader(
                            collection = group,
                            count = records.size,
                            isExpanded = isExpanded,
                            onToggle = { expanded[group.id] = expanded[group.id] == false },
                        )
                    }
                    if (isExpanded) {
                        items(records, key = { "series-${it.id}" }) { scan ->
                            ScanSeriesCard(
                                scan = scan,
                                catalog = catalog,
                                onClick = { onSelectSeries(scan) },
                            )
                        }
                    }
                }
            }
            if (snapshot.scans.isNotEmpty()) {
                item {
                    Text(
                        "Collections are local groups, not verified clinical studies.",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    collection: ImportCollection,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NeuroChrome.raised)
            .clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(collection.label, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text(
                "$count volume${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse collection" else "Expand collection",
            tint = NeuroChrome.accent,
        )
    }
}

@Composable
private fun ScanSeriesCard(scan: ScanRecord, catalog: ScanCatalog, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NeuroChrome.stroke),
        colors = CardDefaults.cardColors(containerColor = NeuroChrome.panel),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScanThumbnail(scan, catalog)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    scan.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    scan.metadata.dimensions.joinToString(" × ") + " voxels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    scan.metadata.spacingMm.joinToString(" × ") {
                        String.format(Locale.US, "%.2f", it)
                    } + " mm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    scan.metadata.dataType + "  •  " + scan.metadata.orientation +
                        if (scan.metadata.timepoints > 1) "  •  ${scan.metadata.timepoints} frames" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (scan.metadata.orientation == "unknown")
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open volume",
                tint = NeuroChrome.accent,
            )
        }
    }
}

@Composable
private fun ScanThumbnail(scan: ScanRecord, catalog: ScanCatalog) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, scan.id) {
        value = catalog.loadThumbnail(scan)
    }
    Box(
        modifier = Modifier.size(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF03080D)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text("MRI", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall)
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "MRI preview of ${scan.label}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
