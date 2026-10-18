package com.marcusfunt.neuroview.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.neuroWorkspaceBackground
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.mri.MprRenderSession
import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.ui.VolumeLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    volumeState: VolumeLoadState,
    displayNameOverride: String? = null,
    selectedDocumentUri: String? = null,
    onImport: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    var plane by rememberSaveable { mutableStateOf(MriPlane.AXIAL) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var sliceIndex by rememberSaveable { mutableIntStateOf(0) }
    var windowCenter by rememberSaveable { mutableFloatStateOf(0.5f) }
    var windowWidth by rememberSaveable { mutableFloatStateOf(1.0f) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isScrubbing by remember { mutableStateOf(false) }

    val ready = volumeState as? VolumeLoadState.Ready
    val volume = ready?.volume
    val sliceCount = if (volume != null) MprRenderer.sliceCount(volume, plane) else 1
    val selectedSlice = sliceIndex.coerceIn(0, sliceCount - 1)

    LaunchedEffect(volume, plane) {
        sliceIndex = if (volume != null) MprRenderer.sliceCount(volume, plane) / 2 else 0
    }

    // Preserve the previous frame until its replacement is ready. Heavy resampling
    // runs off the UI thread; a conflated queue skips intermediate requests while
    // ensuring continuous gestures can still finish and display preview frames.
    // During interaction render a smaller preview, then refine at full resolution
    // once the gesture/slider settles. Cached scalar slices avoid costly rereads
    // when changing window levels or moving backwards through the stack.
    var bitmap by remember(volume, plane) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(volume, plane) {
        if (volume == null) return@LaunchedEffect
        val preview = withContext(Dispatchers.Default) { MprRenderSession(volume, plane, 160) }
        val full = withContext(Dispatchers.Default) { MprRenderSession(volume, plane, 384) }
        // Observe the mutable sliceIndex directly. The composable's selectedSlice
        // is a recomposition-local value and would be captured at initial launch,
        // leaving the image frozen even when the slice counter changes.
        snapshotFlow {
            SliceRenderRequest(sliceIndex.coerceIn(0, sliceCount - 1), windowCenter, windowWidth, isScrubbing)
        }.distinctUntilChanged().conflate().collect { request ->
            val quick = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                val image = preview.render(
                    request.slice, request.center, request.width,
                ) { context.ensureActive() }
                Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            }
            // Present completed previews during continuous drag. For still frames,
            // do not display a request superseded by another slider/plane setting.
            fun latest() = SliceRenderRequest(sliceIndex.coerceIn(0, sliceCount - 1), windowCenter, windowWidth, isScrubbing)
            if (request.scrubbing || latest() == request) bitmap = quick
            if (!request.scrubbing && latest() == request) {
                delay(72)
                if (latest() != request) return@collect
                val refined = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val image = full.render(
                        request.slice, request.center, request.width,
                    ) { context.ensureActive() }
                    Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
                }
                if (latest() == request) bitmap = refined
            }
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
            ModalDrawerSheet(drawerContainerColor = NeuroChrome.topBar) {
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
                    label = { Text("Browse scans") },
                    selected = false,
                    onClick = { fromDrawer(Destination.Library) },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = NeuroChrome.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!focusMode) TopAppBar(
                    title = {
                        Text(
                            displayNameOverride ?: ready?.displayName ?: "MRI viewer",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    actions = {
                        TextButton(onClick = { focusMode = !focusMode }) {
                            Text(if (focusMode) "Controls" else "Focus", color = NeuroChrome.accent)
                        }
                        TextButton(onClick = { panelOpen = true }) {
                            Text(plane.label, color = NeuroChrome.ink)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NeuroChrome.topBar,
                    ),
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).neuroWorkspaceBackground()) {
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
                        focusMode = focusMode,
                        volume = volumeState.volume,
                        plane = plane,
                        bitmap = bitmap,
                        selectedSlice = selectedSlice,
                        totalSlices = sliceCount,
                        onSliceChange = { sliceIndex = it.coerceIn(0, sliceCount - 1) },
                        onScrubbingChanged = { isScrubbing = it },
                        onOpenPlanePanel = { panelOpen = true },
                        onPlaneSelect = { plane = it },
                        selectedDocumentUri = selectedDocumentUri,
                        originalDocumentName = volumeState.displayName,
                        windowCenter = windowCenter,
                        onWindowCenter = { windowCenter = it },
                        windowWidth = windowWidth,
                        onWindowWidth = { windowWidth = it },
                    )
                }

                if (focusMode) {
                    TextButton(
                        onClick = { focusMode = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) { Text("Exit focus", color = NeuroChrome.accent) }
                }
                if (panelOpen) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { panelOpen = false },
                    )
                }
                AnimatedVisibility(
                    visible = panelOpen,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.75f),
                        color = NeuroChrome.panel,
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

private data class SliceRenderRequest(
    val slice: Int,
    val center: Float,
    val width: Float,
    val scrubbing: Boolean,
)

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
@OptIn(ExperimentalMaterial3Api::class)
private fun MriContent(
    modifier: Modifier,
    focusMode: Boolean,
    volume: NiftiVolume,
    plane: MriPlane,
    bitmap: Bitmap?,
    selectedSlice: Int,
    totalSlices: Int,
    onSliceChange: (Int) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onOpenPlanePanel: () -> Unit,
    onPlaneSelect: (MriPlane) -> Unit,
    selectedDocumentUri: String?,
    originalDocumentName: String?,
    windowCenter: Float,
    onWindowCenter: (Float) -> Unit,
    windowWidth: Float,
    onWindowWidth: (Float) -> Unit,
) {
    // Gesture coroutines persist across recompositions; always read the latest slice.
    val currentSlice by rememberUpdatedState(selectedSlice)
    val currentOnSliceChange by rememberUpdatedState(onSliceChange)
    var showAdjustments by rememberSaveable { mutableStateOf(false) }
    var showProcessing by rememberSaveable { mutableStateOf(false) }
    val editStateHolder = rememberSaveableStateHolder()
    var bridgeToken by remember { mutableStateOf("") }
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Compact orientation tabs, with the medical image remaining grayscale.
        if (!focusMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MriPlane.entries.forEach { option ->
                    val selected = plane == option
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier.weight(1f).height(42.dp).clip(shape)
                            .background(if (selected) NeuroChrome.raised else NeuroChrome.panel)
                            .border(
                                BorderStroke(1.dp, if (selected) NeuroChrome.accent else NeuroChrome.stroke),
                                shape,
                            )
                            .clickable(onClick = { onPlaneSelect(option) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) NeuroChrome.accent else NeuroChrome.muted,
                        )
                    }
                }
            }
        }
        val flingScope = rememberCoroutineScope()
        var flingJob by remember(volume, plane) { mutableStateOf<Job?>(null) }
        val currentScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
        val currentOpenPlanePanel by rememberUpdatedState(onOpenPlanePanel)
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, NeuroChrome.stroke.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .pointerInput(volume, plane, totalSlices) {
                    val pixelsPerSlice = 18.dp.toPx()
                    var horizontalDrag = 0f
                    var verticalDrag = 0f
                    var anchorSlice = 0
                    var scrollLocked = false
                    var tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            flingJob?.cancel()
                            flingJob = null
                            anchorSlice = currentSlice
                            horizontalDrag = 0f
                            verticalDrag = 0f
                            scrollLocked = false
                            tracker = VelocityTracker()
                            currentScrubbingChanged(true)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            horizontalDrag += drag.x
                            verticalDrag += drag.y
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!scrollLocked && abs(verticalDrag) + abs(horizontalDrag) > 12.dp.toPx()) {
                                scrollLocked = abs(verticalDrag) > abs(horizontalDrag)
                            }
                            if (scrollLocked) {
                                // Anchor to the start of the gesture; don't lose steps when
                                // several pointer events arrive before recomposition.
                                currentOnSliceChange(
                                    (anchorSlice - (verticalDrag / pixelsPerSlice).toInt())
                                        .coerceIn(0, totalSlices - 1),
                                )
                            }
                        },
                        onDragEnd = {
                            if (!scrollLocked && horizontalDrag < -90.dp.toPx()) {
                                currentOpenPlanePanel()
                            }
                            val pixelsPerSecond = tracker.calculateVelocity().y
                            if (scrollLocked && abs(pixelsPerSecond) > 380f) {
                                val initialSlice = (anchorSlice - (verticalDrag / pixelsPerSlice).toInt())
                                    .coerceIn(0, totalSlices - 1).toFloat()
                                flingJob = flingScope.launch {
                                    try {
                                        Animatable(initialSlice).animateDecay(
                                            initialVelocity = (-pixelsPerSecond / pixelsPerSlice)
                                                .coerceIn(-90f, 90f),
                                            animationSpec = exponentialDecay(frictionMultiplier = 3.0f),
                                        ) {
                                            currentOnSliceChange(
                                                value.roundToInt().coerceIn(0, totalSlices - 1),
                                            )
                                            // Clamp out-of-bounds values; decay naturally stops.
                                        }
                                    } finally {
                                        if (currentCoroutineContext().isActive) currentScrubbingChanged(false)
                                    }
                                }
                            } else {
                                currentScrubbingChanged(false)
                            }
                        },
                        onDragCancel = { currentScrubbingChanged(false) },
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
            // The anatomical image stays neutral grayscale; contextual metadata
            // is placed in the surrounding chrome, never drawn over the pixels.
        }

        if (!focusMode) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NeuroChrome.panel,
            border = BorderStroke(1.dp, NeuroChrome.stroke),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "SLICE POSITION",
                        color = NeuroChrome.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${selectedSlice + 1} / $totalSlices",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = selectedSlice.toFloat(),
                    onValueChange = {
                        flingJob?.cancel()
                        onScrubbingChanged(true)
                        onSliceChange(it.roundToInt())
                    },
                    onValueChangeFinished = { onScrubbingChanged(false) },
                    valueRange = 0f..(totalSlices - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeuroChrome.accent,
                        activeTrackColor = NeuroChrome.accent,
                        inactiveTrackColor = NeuroChrome.stroke,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onSliceChange(selectedSlice - 1) },
                        enabled = selectedSlice > 0,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Previous")
                    }
                    Text(
                        "%.1f mm".format(
                            java.util.Locale.US,
                            MprRenderer.slicePosition(volume, plane, selectedSlice),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onSliceChange(selectedSlice + 1) },
                        enabled = selectedSlice < totalSlices - 1,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (volume.transformSource.startsWith("NO ORIENTATION"))
                    "Orientation unverified" else MprRenderer.displayOrientation(plane),
                color = if (volume.transformSource.startsWith("NO ORIENTATION"))
                    MaterialTheme.colorScheme.error else NeuroChrome.muted,
                style = MaterialTheme.typography.labelSmall,
            )
            TextButton(onClick = { showAdjustments = true }) { Text("Adjust") }
            TextButton(onClick = { showProcessing = true }) { Text("Process") }
        }
        if (volume.transformSource.startsWith("NO ORIENTATION")) {
            Text(
                "Orientation unknown: anatomical labels may not match the image.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (volume.timepoints > 1) {
            Text(
                "4D dataset: first timepoint shown",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        } // Hide position and adjustment controls in Focus mode
    }

    if (showAdjustments) {
        ModalBottomSheet(
            onDismissRequest = { showAdjustments = false },
            containerColor = NeuroChrome.topBar,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Display adjustments", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showAdjustments = false }) { Text("Close") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Image adjustments", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        onWindowCenter(0.5f)
                        onWindowWidth(1.0f)
                    }) { Text("Reset display") }
                }
                Text(
                    "Changes only affect display contrast; the original voxel data is unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Brightness", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(BrightnessSliderMapping.sliderFromWindowCenter(windowCenter) * 100).roundToInt()}%",
                        color = NeuroChrome.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Slider(
                    // Higher window center darkens a fixed voxel intensity.
                    // Invert the control so right = brighter, left = darker.
                    value = BrightnessSliderMapping.sliderFromWindowCenter(windowCenter),
                    onValueChange = {
                        onWindowCenter(BrightnessSliderMapping.windowCenterFromSlider(it))
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeuroChrome.accent,
                        activeTrackColor = NeuroChrome.accent,
                        inactiveTrackColor = NeuroChrome.stroke,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Darker",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Brighter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Contrast / window width", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(windowWidth * 100).roundToInt()}%",
                        color = NeuroChrome.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Slider(
                    value = windowWidth,
                    onValueChange = onWindowWidth,
                    valueRange = 0.05f..1.8f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeuroChrome.accent,
                        activeTrackColor = NeuroChrome.accent,
                        inactiveTrackColor = NeuroChrome.stroke,
                    ),
                )
                Button(
                    onClick = { showAdjustments = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Done") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (showProcessing) {
        ModalBottomSheet(
            onDismissRequest = { showProcessing = false },
            containerColor = NeuroChrome.topBar,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("MRI processing", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showProcessing = false }) { Text("Close") }
                }
                editStateHolder.SaveableStateProvider("brainsuite") {
                    BrainSuitePanel(
                        documentUri = selectedDocumentUri,
                        documentName = originalDocumentName,
                        accessToken = bridgeToken,
                        onAccessTokenChange = { bridgeToken = it },
                    )
                }
            }
        }
    }
}
