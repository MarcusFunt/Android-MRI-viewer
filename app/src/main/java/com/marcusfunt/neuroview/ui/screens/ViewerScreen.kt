package com.marcusfunt.neuroview.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.ui.VolumeLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    volumeState: VolumeLoadState,
    onImport: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    var plane by rememberSaveable { mutableStateOf(MriPlane.AXIAL) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var sliceIndex by rememberSaveable { mutableIntStateOf(0) }
    var windowCenter by rememberSaveable { mutableFloatStateOf(0.5f) }
    var windowWidth by rememberSaveable { mutableFloatStateOf(1.0f) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val ready = volumeState as? VolumeLoadState.Ready
    val volume = ready?.volume
    val sliceCount = if (volume != null) MprRenderer.sliceCount(volume, plane) else 1
    val selectedSlice = sliceIndex.coerceIn(0, sliceCount - 1)

    LaunchedEffect(volume, plane) {
        sliceIndex = if (volume != null) MprRenderer.sliceCount(volume, plane) / 2 else 0
    }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        volume, plane, selectedSlice, windowCenter, windowWidth,
    ) {
        value = if (volume == null) null else withContext(Dispatchers.Default) {
            val image = MprRenderer.render(
                volume, plane, selectedSlice, windowCenter, windowWidth,
            )
            Bitmap.createBitmap(
                image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888,
            )
        }
    }

    fun fromDrawer(destination: Destination) {
        scope.launch {
            drawerState.close()
            onNavigate(destination)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    "NEUROVIEW",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = false,
                    onClick = { fromDrawer(Destination.Home) },
                )
                NavigationDrawerItem(
                    label = { Text("Series selector") },
                    selected = false,
                    onClick = { fromDrawer(Destination.Series) },
                )
                NavigationDrawerItem(
                    label = { Text("Library") },
                    selected = false,
                    onClick = { fromDrawer(Destination.Library) },
                )
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            ready?.displayName ?: "MRI viewer",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    actions = {
                        TextButton(onClick = { panelOpen = true }) {
                            Text(plane.label)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (volumeState) {
                    VolumeLoadState.Empty -> ViewerMessage(
                        title = "No MRI opened",
                        detail = "Select a .nii or .nii.gz NIfTI volume to begin.",
                        onImport = onImport,
                    )
                    VolumeLoadState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Reading NIfTI…", modifier = Modifier.padding(20.dp))
                        }
                    }
                    is VolumeLoadState.Failed -> ViewerMessage(
                        title = "Could not open MRI",
                        detail = volumeState.explanation,
                        onImport = onImport,
                    )
                    is VolumeLoadState.Ready -> MriContent(
                        modifier = Modifier.fillMaxSize(),
                        volume = volumeState.volume,
                        plane = plane,
                        bitmap = bitmap,
                        selectedSlice = selectedSlice,
                        totalSlices = sliceCount,
                        onSliceChange = { sliceIndex = it.coerceIn(0, sliceCount - 1) },
                        onOpenPlanePanel = { panelOpen = true },
                        windowCenter = windowCenter,
                        onWindowCenter = { windowCenter = it },
                        windowWidth = windowWidth,
                        onWindowWidth = { windowWidth = it },
                    )
                }

                if (panelOpen) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { panelOpen = false },
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.75f),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Viewing plane",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { panelOpen = false }) { Text("Close") }
                            }
                            Text(
                                "MPR reconstructed from one NIfTI volume.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MriPlane.entries.forEach { option ->
                                NavigationDrawerItem(
                                    label = { Text(option.label) },
                                    selected = plane == option,
                                    onClick = {
                                        plane = option
                                        panelOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerMessage(title: String, detail: String, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            detail,
            modifier = Modifier.padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onImport) { Text("OPEN NIFTI") }
    }
}

@Composable
private fun MriContent(
    modifier: Modifier,
    volume: NiftiVolume,
    plane: MriPlane,
    bitmap: Bitmap?,
    selectedSlice: Int,
    totalSlices: Int,
    onSliceChange: (Int) -> Unit,
    onOpenPlanePanel: () -> Unit,
    windowCenter: Float,
    onWindowCenter: (Float) -> Unit,
    windowWidth: Float,
    onWindowWidth: (Float) -> Unit,
) {
    // Gesture coroutines persist across recompositions; always read the latest slice.
    val currentSlice by rememberUpdatedState(selectedSlice)
    val currentOnSliceChange by rememberUpdatedState(onSliceChange)
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                plane.label.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Slice ${selectedSlice + 1}/$totalSlices · " +
                    "%.1f mm".format(
                        java.util.Locale.US,
                        MprRenderer.slicePosition(volume, plane, selectedSlice),
                    ),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        var horizontalDrag = 0f
        var verticalDrag = 0f
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .background(Color.Black)
                .pointerInput(volume, plane, totalSlices) {
                    detectDragGestures(
                        onDragStart = {
                            horizontalDrag = 0f
                            verticalDrag = 0f
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            horizontalDrag += drag.x
                            verticalDrag += drag.y
                            if (abs(verticalDrag) > abs(horizontalDrag) &&
                                abs(verticalDrag) >= 16f
                            ) {
                                val steps = (verticalDrag / 16f).toInt()
                                currentOnSliceChange(currentSlice - steps)
                                verticalDrag -= steps * 16f
                            }
                        },
                        onDragEnd = {
                            if (horizontalDrag < -90f) onOpenPlanePanel()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Real ${plane.label} MRI slice",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator()
            }
        }
        Text(
            if (volume.transformSource.startsWith("NO ORIENTATION"))
                "WARNING: Orientation unknown — anatomical labels are assumptions"
            else MprRenderer.displayOrientation(plane) + "  ·  " + volume.transformSource,
            color = if (volume.transformSource.startsWith("NO ORIENTATION"))
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onSliceChange(selectedSlice - 1) },
                enabled = selectedSlice > 0,
            ) { Text("− Slice") }
            Text(
                "Drag vertically to scroll · swipe left for planes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onSliceChange(selectedSlice + 1) },
                enabled = selectedSlice < totalSlices - 1,
            ) { Text("+ Slice") }
        }
        Slider(
            value = selectedSlice.toFloat(),
            onValueChange = { onSliceChange(it.roundToInt()) },
            valueRange = 0f..(totalSlices - 1).coerceAtLeast(1).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Brightness", style = MaterialTheme.typography.labelMedium)
            Text("Window width", style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Slider(
                value = windowCenter,
                onValueChange = onWindowCenter,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
            Slider(
                value = windowWidth,
                onValueChange = onWindowWidth,
                valueRange = 0.05f..1.8f,
                modifier = Modifier.weight(1f),
            )
        }
        if (volume.timepoints > 1) {
            Text(
                "4D dataset: displaying first timepoint only",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
