package com.marcusfunt.neuroview.ui.screens

import android.graphics.Bitmap
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcusfunt.neuroview.mri.MprRenderSession
import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MprProbe
import com.marcusfunt.neuroview.mri.MprImage
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
import kotlin.math.sqrt

internal const val VIEWER_DISPLAY_PREFERENCES = "neuroview_display_v1"
internal const val SHOW_ORIENTATION_MARKERS_KEY = "show_orientation_markers"
private const val DEFAULT_MRI_IMAGE_SCALE = 1.35f

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
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var sliceIndex by rememberSaveable { mutableIntStateOf(0) }
    var windowCenter by rememberSaveable { mutableFloatStateOf(0.5f) }
    var windowWidth by rememberSaveable { mutableFloatStateOf(1.0f) }
    val context = LocalContext.current
    val displayPrefs = remember(context.applicationContext) {
        context.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
    }
    var showOrientationMarkers by remember(displayPrefs) {
        mutableStateOf(displayPrefs.getBoolean(SHOW_ORIENTATION_MARKERS_KEY, false))
    }
    DisposableEffect(displayPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SHOW_ORIENTATION_MARKERS_KEY) {
                showOrientationMarkers = displayPrefs.getBoolean(SHOW_ORIENTATION_MARKERS_KEY, false)
            }
        }
        displayPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { displayPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val scanKey = remember(selectedDocumentUri) {
        selectedDocumentUri?.let { uri ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(uri.toByteArray(Charsets.UTF_8))
            digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
    LaunchedEffect(scanKey) {
        if (scanKey != null) {
            windowCenter = displayPrefs.getFloat("${scanKey}_center", 0.5f)
            windowWidth = displayPrefs.getFloat("${scanKey}_width", 1.0f)
        } else {
            windowCenter = 0.5f
            windowWidth = 1.0f
        }
    }
    fun setWindowCenter(value: Float) {
        windowCenter = value
        if (scanKey != null) displayPrefs.edit().putFloat("${scanKey}_center", value).apply()
    }
    fun setWindowWidth(value: Float) {
        windowWidth = value
        if (scanKey != null) displayPrefs.edit().putFloat("${scanKey}_width", value).apply()
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isScrubbing by remember { mutableStateOf(false) }

    val ready = volumeState as? VolumeLoadState.Ready
    val volume = ready?.volume
    var referenceWorld by remember(volume) { mutableStateOf<DoubleArray?>(null) }
    val sliceCount = if (volume != null) MprRenderer.sliceCount(volume, plane) else 1
    val selectedSlice = sliceIndex.coerceIn(0, sliceCount - 1)

    LaunchedEffect(volume, plane) {
        sliceIndex = when {
            volume == null -> 0
            referenceWorld != null -> MprProbe.sliceForWorld(volume, plane, requireNotNull(referenceWorld))
            else -> MprRenderer.sliceCount(volume, plane) / 2
        }
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
                createViewerBitmap(image)
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
                    createViewerBitmap(image)
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
        modifier = Modifier.fillMaxSize().background(NeuroChrome.background),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = NeuroChrome.background) {
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
        Box(modifier = Modifier.fillMaxSize()) {
            ViewerBackdrop(modifier = Modifier.matchParentSize())
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
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
                        VolumeLoadState.Loading -> ViewerLoadingScreen()
                        is VolumeLoadState.Failed -> ViewerMessage(
                            title = "Could not open MRI",
                            detail = volumeState.explanation,
                            onImport = onImport,
                        )
                        is VolumeLoadState.Ready -> MriContent(
                            modifier = Modifier.fillMaxSize(),
                            focusMode = focusMode,
                            showOrientationMarkers = showOrientationMarkers,
                            volume = volumeState.volume,
                            plane = plane,
                            bitmap = bitmap,
                            selectedSlice = selectedSlice,
                            totalSlices = sliceCount,
                            onSliceChange = { sliceIndex = it.coerceIn(0, sliceCount - 1) },
                            onScrubbingChanged = { isScrubbing = it },
                            onPlaneSelect = { plane = it },
                            selectedDocumentUri = selectedDocumentUri,
                            originalDocumentName = volumeState.displayName,
                            referenceWorld = referenceWorld,
                            onReferenceWorldChange = { referenceWorld = it },
                            windowCenter = windowCenter,
                            onWindowCenter = ::setWindowCenter,
                            windowWidth = windowWidth,
                            onWindowWidth = ::setWindowWidth,
                        )
                    }

                    if (focusMode) {
                        TextButton(
                            onClick = { focusMode = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        ) { Text("Exit focus", color = NeuroChrome.accent) }
                    }
                }
            }
        }
    }
}

private fun createViewerBitmap(image: MprImage): Bitmap {
    // Let only the darkest display values blend with the canvas. This removes
    // the opaque rectangular slice background while leaving source voxels alone.
    val pixels = image.pixels.copyOf()
    val lowEndRange = 24
    for (index in pixels.indices) {
        val intensity = pixels[index] and 0xff
        if (intensity < lowEndRange) {
            val alpha = (intensity * 255f / lowEndRange).roundToInt().coerceIn(0, 255)
            pixels[index] = (alpha shl 24) or (intensity shl 16) or
                (intensity shl 8) or intensity
        }
    }
    return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
}

@Composable
private fun ViewerBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(NeuroChrome.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = sqrt(size.width * size.width + size.height * size.height) / 2f
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.48f to Color.Transparent,
                        0.82f to Color(0xFF115177).copy(alpha = 0.055f),
                        1f to NeuroChrome.accent.copy(alpha = 0.085f),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius,
                ),
            )
        }
    }
}

@Composable
private fun ViewerLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = NeuroChrome.accent,
                    trackColor = NeuroChrome.stroke.copy(alpha = 0.45f),
                    strokeWidth = 2.dp,
                )
                Image(
                    painter = painterResource(com.marcusfunt.neuroview.R.drawable.neuroview_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "NEUROVIEW",
                color = NeuroChrome.accent,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Preparing your MRI",
                color = NeuroChrome.ink,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reading the volume and building the first slice…",
                color = NeuroChrome.muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
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
    showOrientationMarkers: Boolean,
    volume: NiftiVolume,
    plane: MriPlane,
    bitmap: Bitmap?,
    selectedSlice: Int,
    totalSlices: Int,
    onSliceChange: (Int) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onPlaneSelect: (MriPlane) -> Unit,
    selectedDocumentUri: String?,
    originalDocumentName: String?,
    referenceWorld: DoubleArray?,
    onReferenceWorldChange: (DoubleArray?) -> Unit,
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
    var zoomMode by remember(volume) { mutableStateOf(false) }
    var probeMode by remember(volume) { mutableStateOf(false) }
    var probeReading by remember(volume, plane) { mutableStateOf<String?>(null) }
    var scale by remember(volume, plane) { mutableFloatStateOf(DEFAULT_MRI_IMAGE_SCALE) }
    var panOffset by remember(volume, plane) { mutableStateOf(Offset.Zero) }
    var imageViewport by remember { mutableStateOf(IntSize.Zero) }
    val orientationUnknown = volume.transformSource.startsWith("NO ORIENTATION")
    val sliceSliderInteractionSource = remember { MutableInteractionSource() }
    val sliceSliderColors = SliderDefaults.colors(
        thumbColor = NeuroChrome.accent,
        activeTrackColor = NeuroChrome.accent,
        inactiveTrackColor = NeuroChrome.stroke,
    )
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Plane choices stay one tap away without a second drawer or stacked cards.
        if (!focusMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    .offset(y = (-4).dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MriPlane.entries.forEach { option ->
                    val selected = plane == option
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onPlaneSelect(option) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) NeuroChrome.accent else NeuroChrome.muted,
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .padding(horizontal = 28.dp)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(NeuroChrome.accent, RoundedCornerShape(1.dp)),
                            )
                        }
                    }
                }
            }
        }
        val flingScope = rememberCoroutineScope()
        var flingJob by remember(volume, plane) { mutableStateOf<Job?>(null) }
        val currentScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { imageViewport = it }
                .pointerInput(zoomMode) {
                    if (zoomMode) detectTapGestures(onDoubleTap = {
                        scale = DEFAULT_MRI_IMAGE_SCALE
                        panOffset = Offset.Zero
                    })
                }
                .pointerInput(volume, plane, totalSlices, zoomMode, probeMode, bitmap) {
                    if (probeMode) {
                        detectTapGestures(onTap = { tap ->
                            val image = bitmap
                            if (image != null && imageViewport.width > 0 && imageViewport.height > 0) {
                                val fit = minOf(
                                    imageViewport.width.toFloat() / image.width,
                                    imageViewport.height.toFloat() / image.height,
                                )
                                val imageWidth = image.width * fit
                                val imageHeight = image.height * fit
                                val fractionX = (tap.x - (imageViewport.width - imageWidth) / 2f) / imageWidth
                                val fractionY = (tap.y - (imageViewport.height - imageHeight) / 2f) / imageHeight
                                val sampled = MprProbe.sample(
                                    volume, plane, currentSlice,
                                    fractionX.toDouble(), fractionY.toDouble(),
                                )
                                if (sampled != null) {
                                    onReferenceWorldChange(sampled.worldRasMm)
                                    probeReading = "Voxel ${sampled.voxelIJK.joinToString(", ") {
                                        it.roundToInt().toString()
                                    }} · intensity ${String.format(java.util.Locale.US, "%.2f", sampled.intensity)}"
                                } else {
                                    probeReading = "No measured voxel at that position."
                                }
                            }
                        })
                    } else if (zoomMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = newScale
                            if (newScale <= 1f) {
                                panOffset = Offset.Zero
                            } else {
                                val limitX = imageViewport.width * (newScale - 1f) / 2f
                                val limitY = imageViewport.height * (newScale - 1f) / 2f
                                panOffset = Offset(
                                    (panOffset.x + pan.x).coerceIn(-limitX, limitX),
                                    (panOffset.y + pan.y).coerceIn(-limitY, limitY),
                                )
                            }
                        }
                    } else {
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
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Real ${plane.label} MRI slice",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    },
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = NeuroChrome.accent,
                        trackColor = NeuroChrome.stroke.copy(alpha = 0.45f),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "Rendering slice",
                        color = NeuroChrome.muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (showOrientationMarkers && !volume.transformSource.startsWith("NO ORIENTATION")) {
                Text(
                    if (plane == MriPlane.SAGITTAL) "A" else "R",
                    color = NeuroChrome.muted,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    if (plane == MriPlane.SAGITTAL) "P" else "L",
                    color = NeuroChrome.muted,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (zoomMode && scale > 1f) {
                TextButton(
                    onClick = { scale = 1f; panOffset = Offset.Zero },
                    modifier = Modifier.align(Alignment.TopStart),
                ) { Text("Reset zoom", color = NeuroChrome.accent) }
            }
            if (zoomMode) {
                Text(
                    "${(scale * 100).roundToInt()}% · drag to pan · pinch to zoom",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeuroChrome.muted,
                )
            }
            val reference = referenceWorld
            if (probeMode && reference != null && bitmap != null && imageViewport.width > 0) {
                val position = MprRenderer.slicePosition(volume, plane, currentSlice)
                val count = MprRenderer.sliceCount(volume, plane)
                val bounds = volume.affine.bounds(volume.nx, volume.ny, volume.nz)
                val spacing = (bounds[1][plane.firstAxis] - bounds[0][plane.firstAxis]) /
                    (count - 1).coerceAtLeast(1)
                if (abs(reference[plane.firstAxis] - position) <= spacing * 0.55) {
                    val (fx, fy) = MprProbe.fractionsForWorld(volume, plane, reference)
                    val fit = minOf(imageViewport.width.toFloat() / bitmap.width,
                        imageViewport.height.toFloat() / bitmap.height)
                    val imageWidth = bitmap.width * fit
                    val imageHeight = bitmap.height * fit
                    val centerX = imageViewport.width / 2f
                    val centerY = imageViewport.height / 2f
                    val x = ((imageViewport.width - imageWidth) / 2f + fx.toFloat() * imageWidth -
                        centerX) * scale + centerX + panOffset.x
                    val y = ((imageViewport.height - imageHeight) / 2f + fy.toFloat() * imageHeight -
                        centerY) * scale + centerY + panOffset.y
                    if (x in 0f..imageViewport.width.toFloat() &&
                        y in 0f..imageViewport.height.toFloat()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val lineColor = NeuroChrome.accent.copy(alpha = 0.65f)
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    }
                }
            }
            if (!focusMode) {
                TextButton(
                    onClick = {
                        probeMode = !probeMode
                        if (!probeMode) onReferenceWorldChange(null)
                        zoomMode = false
                        scale = DEFAULT_MRI_IMAGE_SCALE
                        panOffset = Offset.Zero
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) { Text(if (probeMode) "End probe" else "Probe",
                    color = NeuroChrome.accent) }
            }
            if (probeMode) {
                Text(
                    probeReading ?: "Tap a voxel to inspect intensity and set a cross-plane reference.",
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeuroChrome.ink,
                )
            }
        }

        if (!focusMode) {
            val slicePosition = MprRenderer.slicePosition(volume, plane, selectedSlice)
            val positionAxis = if (orientationUnknown) null else when (plane) {
                MriPlane.AXIAL -> "S"
                MriPlane.CORONAL -> "A"
                MriPlane.SAGITTAL -> "R"
            }
            val formattedPosition = "%.1f mm".format(java.util.Locale.US, slicePosition)
            val positionLabel = if (positionAxis == null) formattedPosition
                else "$positionAxis  $formattedPosition"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "SLICE",
                        color = NeuroChrome.muted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${selectedSlice + 1} / $totalSlices",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeuroChrome.ink,
                    )
                    Text(
                        positionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            flingJob?.cancel()
                            onScrubbingChanged(false)
                            onSliceChange((selectedSlice - 1).coerceAtLeast(0))
                        },
                        enabled = selectedSlice > 0,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Previous slice"
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Prev", style = MaterialTheme.typography.labelMedium)
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
                        enabled = totalSlices > 1,
                        interactionSource = sliceSliderInteractionSource,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = "Slice position"
                            stateDescription = "Slice ${selectedSlice + 1} of $totalSlices, $positionLabel"
                        },
                        colors = sliceSliderColors,
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = sliceSliderInteractionSource,
                                colors = sliceSliderColors,
                                enabled = totalSlices > 1,
                                thumbSize = DpSize(18.dp, 18.dp),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(4.dp),
                                colors = sliceSliderColors,
                                enabled = totalSlices > 1,
                                thumbTrackGapSize = 0.dp,
                            )
                        },
                    )
                    TextButton(
                        onClick = {
                            flingJob?.cancel()
                            onScrubbingChanged(false)
                            onSliceChange((selectedSlice + 1).coerceAtMost(totalSlices - 1))
                        },
                        enabled = selectedSlice < totalSlices - 1,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Next slice"
                        },
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .offset(y = (-4).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            TextButton(onClick = {
                zoomMode = !zoomMode
                probeMode = false
                onReferenceWorldChange(null)
                scale = DEFAULT_MRI_IMAGE_SCALE
                panOffset = Offset.Zero
            }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    if (zoomMode) "Scroll" else "Zoom",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                )
            }
            TextButton(onClick = { showAdjustments = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Adjust", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
            }
            TextButton(onClick = { showProcessing = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Process", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
            }
        }
        if (orientationUnknown) {
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
                Text("Display presets", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = windowWidth == 1f && windowCenter == 0.5f,
                        onClick = { onWindowCenter(0.5f); onWindowWidth(1f) },
                        label = { Text("Balanced") },
                    )
                    FilterChip(
                        selected = windowWidth == 0.55f && windowCenter == 0.5f,
                        onClick = { onWindowCenter(0.5f); onWindowWidth(0.55f) },
                        label = { Text("Detail") },
                    )
                    FilterChip(
                        selected = windowWidth == 1.5f && windowCenter == 0.5f,
                        onClick = { onWindowCenter(0.5f); onWindowWidth(1.5f) },
                        label = { Text("Wide") },
                    )
                }

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
