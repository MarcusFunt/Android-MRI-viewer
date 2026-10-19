package com.marcusfunt.neuroview.ui.screens

import android.graphics.Bitmap
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.Trace
import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MotionToken
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.marcusfunt.neuroview.mri.AdaptiveQualityLevel
import com.marcusfunt.neuroview.mri.AdaptiveRenderQualityController
import com.marcusfunt.neuroview.mri.RenderEnvironment
import com.marcusfunt.neuroview.mri.RenderPerformanceMonitor
import com.marcusfunt.neuroview.mri.RenderPerformanceSnapshot
import com.marcusfunt.neuroview.mri.RenderQualityPolicy
import com.marcusfunt.neuroview.mri.RenderRequestGate
import com.marcusfunt.neuroview.mri.ThermalStatus
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.components.OperationProgressIndicator
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DEFAULT_MRI_IMAGE_SCALE = 1.35f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    volumeState: VolumeLoadState,
    displayNameOverride: String? = null,
    selectedDocumentUri: String? = null,
    onFirstSliceBitmapReady: (OperationId) -> Unit,
    onFirstSliceDisplayed: (OperationId) -> Unit,
    onFirstSliceRenderStarted: (OperationId, NiftiVolume, Job) -> Unit,
    onFirstSliceRenderFinished: (OperationId, NiftiVolume, Job) -> Unit,
    onCancelLoad: () -> Unit,
    onRetryLoad: () -> Unit,
    onReturnToLibrary: () -> Unit,
    onImport: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onClearCache: suspend () -> Int = { 0 },
) {
    var plane by rememberSaveable { mutableStateOf(MriPlane.AXIAL) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var sliceIndex by rememberSaveable { mutableIntStateOf(0) }
    var windowCenter by rememberSaveable { mutableFloatStateOf(0.5f) }
    var windowWidth by rememberSaveable { mutableFloatStateOf(1.0f) }
    val context = LocalContext.current
    val motionPreference = LocalNeuroViewMotion.current
    val motionDuration = MotionToken.QUICK.durationMs(motionPreference)
    val displayPrefs = remember(context.applicationContext) {
        context.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
    }
    var showOrientationMarkers by remember(displayPrefs) {
        mutableStateOf(displayPrefs.getBoolean(SHOW_ORIENTATION_MARKERS_KEY, false))
    }
    var probeCrosshairEnabled by remember(displayPrefs) {
        mutableStateOf(displayPrefs.getBoolean(PROBE_CROSSHAIR_ENABLED_KEY, false))
    }
    var crosshairThicknessDp by remember(displayPrefs) {
        mutableFloatStateOf(
            displayPrefs.getFloat(
                CROSSHAIR_THICKNESS_DP_KEY,
                DEFAULT_CROSSHAIR_THICKNESS_DP,
            ).coerceIn(MIN_CROSSHAIR_THICKNESS_DP, MAX_CROSSHAIR_THICKNESS_DP),
        )
    }
    var renderQualitySettings by remember(displayPrefs) {
        mutableStateOf(displayPrefs.readRenderQualitySettings())
    }
    var showSliceButtons by remember(displayPrefs) { mutableStateOf(displayPrefs.getBoolean(SHOW_SLICE_BUTTONS_KEY, false)) }
    var inertialScrolling by remember(displayPrefs) { mutableStateOf(displayPrefs.getBoolean(INERTIAL_SCROLLING_KEY, true)) }
    var immersiveFocus by remember(displayPrefs) { mutableStateOf(displayPrefs.getBoolean(IMMERSIVE_FOCUS_KEY, false)) }
    FocusSystemBars(focusMode && immersiveFocus)
    BackHandler(enabled = focusMode) { focusMode = false }
    DisposableEffect(displayPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                SHOW_SLICE_BUTTONS_KEY -> showSliceButtons = displayPrefs.getBoolean(SHOW_SLICE_BUTTONS_KEY, false)
                INERTIAL_SCROLLING_KEY -> inertialScrolling = displayPrefs.getBoolean(INERTIAL_SCROLLING_KEY, true)
                IMMERSIVE_FOCUS_KEY -> immersiveFocus = displayPrefs.getBoolean(IMMERSIVE_FOCUS_KEY, false)
                SHOW_ORIENTATION_MARKERS_KEY -> {
                    showOrientationMarkers = displayPrefs.getBoolean(SHOW_ORIENTATION_MARKERS_KEY, false)
                }
                PROBE_CROSSHAIR_ENABLED_KEY -> {
                    probeCrosshairEnabled = displayPrefs.getBoolean(PROBE_CROSSHAIR_ENABLED_KEY, false)
                }
                CROSSHAIR_THICKNESS_DP_KEY -> {
                    crosshairThicknessDp = displayPrefs.getFloat(
                        CROSSHAIR_THICKNESS_DP_KEY,
                        DEFAULT_CROSSHAIR_THICKNESS_DP,
                    ).coerceIn(MIN_CROSSHAIR_THICKNESS_DP, MAX_CROSSHAIR_THICKNESS_DP)
                }
                RENDER_QUALITY_MODE_KEY,
                ADAPTIVE_RENDERING_KEY,
                PREVIEW_MAX_SIDE_KEY,
                STILL_MAX_SIDE_KEY,
                RENDER_REFINE_DELAY_KEY -> {
                    renderQualitySettings = displayPrefs.readRenderQualitySettings()
                }
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
    var lastViewerInputAtNs by remember {
        mutableLongStateOf(SystemClock.elapsedRealtimeNanos())
    }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val homeAppearancePrefs = remember(context.applicationContext) {
        context.getSharedPreferences("neuroview_home_appearance_v1", Context.MODE_PRIVATE)
    }
    var autoRotate by remember(homeAppearancePrefs) {
        mutableStateOf(homeAppearancePrefs.getBoolean("rotate", true))
    }
    var glowEnabled by remember(homeAppearancePrefs) {
        mutableStateOf(homeAppearancePrefs.getBoolean("glow", true))
    }
    DisposableEffect(homeAppearancePrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            when (key) {
                "rotate" -> autoRotate = preferences.getBoolean("rotate", true)
                "glow" -> glowEnabled = preferences.getBoolean("glow", true)
            }
        }
        homeAppearancePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { homeAppearancePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val ready = volumeState as? VolumeLoadState.Ready
    val firstSlice = volumeState as? VolumeLoadState.FirstSliceReady
    val loading = volumeState as? VolumeLoadState.Loading
    val volume = ready?.volume ?: firstSlice?.volume ?: loading?.decodedVolume
    val contentDisplayName = ready?.displayName ?: firstSlice?.displayName
    val powerManager = remember(context.applicationContext) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    var environmentRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context.applicationContext, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                environmentRevision++
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        val thermalListener = if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
            PowerManager.OnThermalStatusChangedListener { environmentRevision++ }
                .also { powerManager.addThermalStatusListener(context.mainExecutor, it) }
        } else null
        onDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= 29 && thermalListener != null) {
                powerManager?.removeThermalStatusListener(thermalListener)
            }
        }
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val deviceViewportMaxSidePx = with(density) {
        maxOf(
            configuration.screenWidthDp.dp.toPx(),
            configuration.screenHeightDp.dp.toPx(),
        ).roundToInt()
    }
    var imageViewportSize by remember(volume) { mutableStateOf(IntSize.Zero) }
    val viewportMaxSidePx = if (imageViewportSize == IntSize.Zero) {
        deviceViewportMaxSidePx
    } else {
        maxOf(imageViewportSize.width, imageViewportSize.height)
    }
    val runtimeMaxHeap = Runtime.getRuntime().maxMemory()
    val runtime = Runtime.getRuntime()
    val runtimeUsedHeap = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    val runtimeAvailableHeap = (runtimeMaxHeap - runtimeUsedHeap).coerceAtLeast(0L)
    val powerSave = powerManager?.isPowerSaveMode == true
    val thermalStatus = if (Build.VERSION.SDK_INT >= 29) {
        when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_MODERATE,
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.MODERATE
            else -> ThermalStatus.NOMINAL
        }
    } else ThermalStatus.NOMINAL
    val renderEnvironment = remember(
        runtimeMaxHeap, volume, viewportMaxSidePx, powerSave, thermalStatus, environmentRevision,
        renderQualitySettings,
    ) {
        RenderEnvironment(
            maxHeapBytes = runtimeMaxHeap,
            availableHeapBytes = runtimeAvailableHeap,
            viewportMaxSidePx = viewportMaxSidePx,
            powerSave = powerSave,
            thermalStatus = thermalStatus,
        )
    }
    var adaptiveQualityLevel by remember(volume) {
        mutableStateOf(AdaptiveQualityLevel.STANDARD)
    }
    val adaptiveQualityController = remember(volume) { AdaptiveRenderQualityController() }
    val renderQualityProfile = remember(renderQualitySettings, renderEnvironment, adaptiveQualityLevel) {
        RenderQualityPolicy.resolve(renderQualitySettings, renderEnvironment, adaptiveQualityLevel)
    }
    val renderPerformanceMonitor = remember(volume) { RenderPerformanceMonitor() }
    var renderPerformance by remember(volume) { mutableStateOf(RenderPerformanceSnapshot()) }
    fun noteViewerInput() {
        lastViewerInputAtNs = SystemClock.elapsedRealtimeNanos()
    }
    var referenceWorld by remember(volume) { mutableStateOf<DoubleArray?>(null) }
    val sliceCount = if (volume != null) MprRenderer.sliceCount(volume, plane) else 1
    val selectedSlice = sliceIndex.coerceIn(0, sliceCount - 1)

    LaunchedEffect(volume) {
        // A newly loaded scan starts a fresh latency sample window.
        noteViewerInput()
    }

    LaunchedEffect(volume, plane) {
        sliceIndex = when {
            volume == null -> 0
            referenceWorld != null -> MprProbe.sliceForWorld(volume, plane, requireNotNull(referenceWorld))
            else -> MprRenderer.sliceCount(volume, plane) / 2
        }
    }

    fun latestSliceInput() = SliceRenderInput(
        sliceIndex.coerceIn(0, sliceCount - 1), windowCenter, windowWidth, isScrubbing,
    )
    // Preserve the previous frame until its replacement is ready. Heavy resampling
    // runs off the UI thread; collectLatest cancels stale requests so continuous
    // gestures can finish and display preview frames without an obsolete queue.
    // During interaction render a smaller preview, then refine at full resolution
    // once the gesture/slider settles. Cached scalar slices avoid costly rereads
    // when changing window levels or moving backwards through the stack.
    var bitmap by remember(volume, plane) { mutableStateOf<Bitmap?>(null) }
    var committedRenderGeneration by remember(volume, plane) { mutableLongStateOf(0L) }
    var committedInputStartedAtNs by remember(volume, plane) { mutableLongStateOf(0L) }
    var lastMeasuredDisplayGeneration by remember(volume, plane) { mutableLongStateOf(0L) }
    var renderStatusVisible by remember(volume, plane) { mutableStateOf(false) }
    LaunchedEffect(bitmap, volume, plane) {
        if (bitmap == null) {
            delay(160L)
            renderStatusVisible = true
        } else {
            renderStatusVisible = false
        }
    }
    var firstSliceReported by remember(loading?.operation?.id) { mutableStateOf(false) }
    LaunchedEffect(bitmap, loading?.operation?.id) {
        val operationId = loading?.operation?.id ?: return@LaunchedEffect
        if (loading.decodedVolume != null && bitmap != null && !firstSliceReported) {
            firstSliceReported = true
            onFirstSliceBitmapReady(operationId)
        }
    }
    var firstSliceDisplayedReported by remember(firstSlice?.operation?.id) { mutableStateOf(false) }
    SideEffect {
        if (committedRenderGeneration > lastMeasuredDisplayGeneration) {
            lastMeasuredDisplayGeneration = committedRenderGeneration
            renderPerformance = renderPerformanceMonitor.recordComposeCommit(
                generation = committedRenderGeneration,
                latencyMs = (SystemClock.elapsedRealtimeNanos() - committedInputStartedAtNs)
                    .coerceAtLeast(0L) / 1_000_000L,
            )
        }
        val operationId = firstSlice?.operation?.id
        if (operationId != null && !firstSliceDisplayedReported) {
            firstSliceDisplayedReported = true
            onFirstSliceDisplayed(operationId)
        }
    }
    val renderRequestGate = remember(volume, plane) { RenderRequestGate() }
    LaunchedEffect(volume, plane, renderQualityProfile) {
        if (volume == null) return@LaunchedEffect
        val operationId = loading?.operation?.id
        val renderJob = currentCoroutineContext()[Job]
        if (operationId != null && renderJob != null) {
            onFirstSliceRenderStarted(operationId, volume, renderJob)
        }
        try {
            val preview = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                MprRenderSession(
                    volume, plane, renderQualityProfile.previewMaxSide,
                    cancellationCheck = { context.ensureActive() },
                )
            }
            val full = if (renderQualityProfile.stillMaxSide == renderQualityProfile.previewMaxSide) {
                preview
            } else {
                withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    MprRenderSession(
                        volume, plane, renderQualityProfile.stillMaxSide,
                        cancellationCheck = { context.ensureActive() },
                    )
                }
            }
            // Observe the mutable sliceIndex directly. The composable's selectedSlice
            // is a recomposition-local value and would be captured at initial launch,
            // leaving the image frozen even when the slice counter changes.
            snapshotFlow {
                SliceRenderInput(
                    sliceIndex.coerceIn(0, sliceCount - 1), windowCenter, windowWidth, isScrubbing,
                )
            }.distinctUntilChanged().collectLatest { input ->
                val generation = renderRequestGate.begin()
                val request = SliceRenderRequest(generation, input, lastViewerInputAtNs)
                var committed = false
                var workerRenderDurationMs = 0L
                var settledRenderDurationMs: Long? = null
                var settledFrameCommitted = false
                try {
                    val previewStartedAt = SystemClock.elapsedRealtimeNanos()
                    val quick = try {
                        withContext(Dispatchers.Default) {
                            val context = currentCoroutineContext()
                            traceViewerRender("NeuroView.slice.preview") {
                                createViewerBitmap(
                                    preview.render(input.slice, input.center, input.width) {
                                        context.ensureActive()
                                    },
                                )
                            }
                        }
                    } finally {
                        workerRenderDurationMs += (SystemClock.elapsedRealtimeNanos() - previewStartedAt)
                            .coerceAtLeast(0L) / 1_000_000L
                    }
                    if (renderRequestGate.isCurrent(request.generation) && latestSliceInput() == input) {
                        bitmap = quick
                        traceViewerCommit(quick, request.generation, input.slice, "preview")
                        committedRenderGeneration = request.generation
                        committedInputStartedAtNs = request.inputStartedAtNs
                        committed = true
                    }
                    if (!input.scrubbing && renderRequestGate.isCurrent(request.generation) &&
                        latestSliceInput() == input
                    ) {
                        delay(renderQualityProfile.refineDelayMs)
                        if (!renderRequestGate.isCurrent(request.generation) || latestSliceInput() != input) {
                            return@collectLatest
                        }
                        val stillStartedAt = SystemClock.elapsedRealtimeNanos()
                        val refined = try {
                            withContext(Dispatchers.Default) {
                                val context = currentCoroutineContext()
                                traceViewerRender("NeuroView.slice.still") {
                                    createViewerBitmap(
                                        full.render(input.slice, input.center, input.width) {
                                            context.ensureActive()
                                        },
                                    )
                                }
                            }
                        } finally {
                            settledRenderDurationMs = (SystemClock.elapsedRealtimeNanos() - stillStartedAt)
                                .coerceAtLeast(0L) / 1_000_000L
                            workerRenderDurationMs += requireNotNull(settledRenderDurationMs)
                        }
                        if (renderRequestGate.isCurrent(request.generation) && latestSliceInput() == input) {
                            bitmap = refined
                            traceViewerCommit(refined, request.generation, input.slice, "still")
                            committedRenderGeneration = request.generation
                            committedInputStartedAtNs = request.inputStartedAtNs
                            committed = true
                            settledFrameCommitted = true
                        }
                    }
                } finally {
                    val runtime = Runtime.getRuntime()
                    renderPerformance = renderPerformanceMonitor.recordRender(
                        durationMs = workerRenderDurationMs,
                        committed = committed,
                        heapUsedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L),
                    )
                    if (settledFrameCommitted) {
                        adaptiveQualityLevel = adaptiveQualityController.recordRenderDuration(
                            settledRenderDurationMs ?: workerRenderDurationMs,
                        )
                    }
                }
            }
        } finally {
            if (operationId != null && renderJob != null) {
                onFirstSliceRenderFinished(operationId, volume, renderJob)
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
                    AnimatedVisibility(
                        visible = !focusMode,
                        enter = fadeIn(tween(motionDuration)) + slideInVertically(
                            animationSpec = tween(motionDuration),
                            initialOffsetY = { -it / 5 },
                        ),
                        exit = fadeOut(tween(motionDuration)) + slideOutVertically(
                            animationSpec = tween(motionDuration),
                            targetOffsetY = { -it / 5 },
                        ),
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    displayNameOverride ?: ready?.displayName ?: firstSlice?.displayName
                                        ?: loading?.displayName ?: "MRI viewer",
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
                                IconButton(onClick = { settingsOpen = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Viewer settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (volumeState) {
                        VolumeLoadState.Empty -> ViewerMessage(
                            title = "No MRI opened",
                            detail = "Select a .nii or .nii.gz NIfTI volume to begin.",
                            onImport = onImport,
                        )
                        is VolumeLoadState.Loading -> ViewerLoadingScreen(
                            operation = volumeState.operation,
                            displayName = volumeState.displayName,
                            cancelRequestedAtElapsedMs = volumeState.cancelRequestedAtElapsedMs,
                            onCancel = onCancelLoad,
                            onReturnToLibrary = onReturnToLibrary,
                        )
                        is VolumeLoadState.Failed -> ViewerMessage(
                            title = "Could not open MRI",
                            detail = volumeState.explanation,
                            onImport = onImport,
                            onRetry = onRetryLoad,
                        )
                        is VolumeLoadState.Canceled -> ViewerMessage(
                            title = "MRI load canceled",
                            detail = "The selected volume was not changed. You can retry or choose another file.",
                            onImport = onImport,
                            onRetry = onRetryLoad,
                        )
                        is VolumeLoadState.FirstSliceReady, is VolumeLoadState.Ready -> MriContent(
                            modifier = Modifier.fillMaxSize(),
                            focusMode = focusMode,
                            showSliceButtons = showSliceButtons,
                            inertialScrolling = inertialScrolling,
                            onFocusToggle = { focusMode = !focusMode },
                            showOrientationMarkers = showOrientationMarkers,
                            probeCrosshairEnabled = probeCrosshairEnabled,
                            onProbeCrosshairChange = { enabled ->
                                probeCrosshairEnabled = enabled
                                displayPrefs.edit().putBoolean(PROBE_CROSSHAIR_ENABLED_KEY, enabled).apply()
                            },
                            crosshairThicknessDp = crosshairThicknessDp,
                            volume = requireNotNull(volume),
                            plane = plane,
                            bitmap = bitmap,
                            renderStatusVisible = renderStatusVisible,
                            onImageViewportSizeChanged = { imageViewportSize = it },
                            selectedSlice = selectedSlice,
                            totalSlices = sliceCount,
                            onSliceChange = {
                                noteViewerInput()
                                sliceIndex = it.coerceIn(0, sliceCount - 1)
                            },
                            onScrubbingChanged = {
                                noteViewerInput()
                                isScrubbing = it
                            },
                            onPlaneSelect = { selectedPlane ->
                                noteViewerInput()
                                sliceIndex = when {
                                    referenceWorld != null -> MprProbe.sliceForWorld(
                                        requireNotNull(volume),
                                        selectedPlane,
                                        requireNotNull(referenceWorld),
                                    )
                                    else -> MprRenderer.sliceCount(requireNotNull(volume), selectedPlane) / 2
                                }
                                plane = selectedPlane
                            },
                            selectedDocumentUri = selectedDocumentUri,
                            originalDocumentName = contentDisplayName,
                            referenceWorld = referenceWorld,
                            onReferenceWorldChange = { referenceWorld = it },
                            windowCenter = windowCenter,
                            onWindowCenter = {
                                noteViewerInput()
                                setWindowCenter(it)
                            },
                            windowWidth = windowWidth,
                            onWindowWidth = {
                                noteViewerInput()
                                setWindowWidth(it)
                            },
                        )
                    }

                }
            }
        }
    }

    if (settingsOpen) {
        NeuroViewSettingsSheet(
            autoRotate = autoRotate,
            onAutoRotateChange = { enabled ->
                autoRotate = enabled
                homeAppearancePrefs.edit().putBoolean("rotate", enabled).apply()
            },
            glowEnabled = glowEnabled,
            onGlowEnabledChange = { enabled ->
                glowEnabled = enabled
                homeAppearancePrefs.edit().putBoolean("glow", enabled).apply()
            },
            onResetBrainOrientation = null,
            onClearCache = onClearCache,
            scrollToViewerSettings = true,
            renderPerformance = renderPerformance,
            memorySafeMaximumSidePx = RenderQualityPolicy.memorySafeMaximumSide(
                renderEnvironment.maxHeapBytes,
                renderEnvironment.availableHeapBytes,
            ),
            onDismiss = { settingsOpen = false },
        )
    }
}

internal fun createViewerBitmap(image: MprImage): Bitmap =
    Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)

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
private fun ViewerLoadingScreen(
    operation: OperationSnapshot,
    displayName: String?,
    cancelRequestedAtElapsedMs: Long?,
    onCancel: () -> Unit,
    onReturnToLibrary: () -> Unit,
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(com.marcusfunt.neuroview.R.drawable.neuroview_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
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
            if (displayName != null) {
                Text(
                    displayName,
                    modifier = Modifier.fillMaxWidth(),
                    color = NeuroChrome.muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(24.dp))
            }
            OperationProgressIndicator(
                snapshot = operation,
                elapsedMs = (elapsedNow - operation.startedAtElapsedMs).coerceAtLeast(0L),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            if (operation.status == OperationStatus.CANCELING) {
                Text("Stopping…", color = NeuroChrome.muted, style = MaterialTheme.typography.labelLarge)
                if (cancelRequestedAtElapsedMs != null &&
                    elapsedNow - cancelRequestedAtElapsedMs >= 8_000L
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The selected file provider is still responding slowly. You can return to the Library.",
                        color = NeuroChrome.muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onReturnToLibrary) { Text("RETURN TO LIBRARY") }
                }
            } else {
                Button(onClick = onCancel) { Text("CANCEL") }
            }
        }
    }
}

private inline fun <T> traceViewerRender(section: String, block: () -> T): T {
    val traceEnabled = Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()
    if (traceEnabled) Trace.beginSection(section)
    return try {
        block()
    } finally {
        if (traceEnabled) Trace.endSection()
    }
}

// Opt-in system tracing only: counts, index and event timing; no scan identity or voxel data.
private fun traceViewerGesture(event: String, slice: Int) {
    if (Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()) {
        Trace.beginSection("NeuroView.$event")
        Trace.setCounter("NeuroView.slice.index", slice.toLong())
        Trace.endSection()
    }
}

private fun traceViewerCommit(bitmap: Bitmap, generation: Long, slice: Int, quality: String) {
    if (Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()) {
        Trace.beginSection("NeuroView.commit.$quality")
        Trace.setCounter("NeuroView.render.generation", generation)
        Trace.setCounter("NeuroView.render.width", bitmap.width.toLong())
        Trace.setCounter("NeuroView.render.height", bitmap.height.toLong())
        Trace.setCounter("NeuroView.slice.index", slice.toLong())
        Trace.endSection()
    }
}

private data class SliceRenderInput(
    val slice: Int,
    val center: Float,
    val width: Float,
    val scrubbing: Boolean,
)

private data class SliceRenderRequest(
    val generation: Long,
    val input: SliceRenderInput,
    val inputStartedAtNs: Long,
)

@Composable
private fun ViewerMessage(
    title: String,
    detail: String,
    onImport: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
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
        if (onRetry != null) {
            Button(onClick = onRetry) { Text("RETRY") }
            TextButton(onClick = onImport) { Text("OPEN NIFTI") }
        } else {
            Button(onClick = onImport) { Text("OPEN NIFTI") }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MriContent(
    modifier: Modifier,
    focusMode: Boolean,
    showSliceButtons: Boolean,
    inertialScrolling: Boolean,
    onFocusToggle: () -> Unit,
    showOrientationMarkers: Boolean,
    probeCrosshairEnabled: Boolean,
    onProbeCrosshairChange: (Boolean) -> Unit,
    crosshairThicknessDp: Float,
    volume: NiftiVolume,
    plane: MriPlane,
    bitmap: Bitmap?,
    renderStatusVisible: Boolean,
    onImageViewportSizeChanged: (IntSize) -> Unit,
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
    val motionDuration = MotionToken.QUICK.durationMs(LocalNeuroViewMotion.current)
    // Gesture coroutines persist across recompositions; always read the latest slice.
    val currentSlice by rememberUpdatedState(selectedSlice)
    val currentOnSliceChange by rememberUpdatedState(onSliceChange)
    val currentBitmap by rememberUpdatedState(bitmap)
    val currentInertialScrolling by rememberUpdatedState(inertialScrolling)
    val currentReferenceWorldChange by rememberUpdatedState(onReferenceWorldChange)
    var showAdjustments by rememberSaveable { mutableStateOf(false) }
    var showProcessing by rememberSaveable { mutableStateOf(false) }
    var showMoreActions by rememberSaveable { mutableStateOf(false) }
    val editStateHolder = rememberSaveableStateHolder()
    var bridgeToken by remember { mutableStateOf("") }
    var zoomMode by rememberSaveable(volume) { mutableStateOf(false) }
    var probeReading by remember(volume) { mutableStateOf<String?>(null) }
    var scale by rememberSaveable(volume, plane) { mutableFloatStateOf(DEFAULT_MRI_IMAGE_SCALE) }
    var panOffset by rememberSaveable(volume, plane, stateSaver = androidx.compose.runtime.saveable.Saver<Offset, List<Float>>(
        save = { listOf(it.x, it.y) }, restore = { Offset(it[0], it[1]) },
    )) { mutableStateOf(Offset.Zero) }
    var imageViewport by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(probeCrosshairEnabled, volume) {
        probeReading = null
        currentReferenceWorldChange(null)
        if (probeCrosshairEnabled) {
            zoomMode = false
            scale = DEFAULT_MRI_IMAGE_SCALE
            panOffset = Offset.Zero
        }
    }
    val orientationUnknown = volume.transformSource.startsWith("NO ORIENTATION")
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Plane choices stay one tap away without a second drawer or stacked cards.
        AnimatedVisibility(
            visible = !focusMode,
            enter = fadeIn(tween(motionDuration)) + slideInVertically(
                animationSpec = tween(motionDuration),
                initialOffsetY = { it / 6 },
            ),
            exit = fadeOut(tween(motionDuration)) + slideOutVertically(
                animationSpec = tween(motionDuration),
                targetOffsetY = { it / 6 },
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    .offset(y = (-4).dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MriPlane.entries.forEach { option ->
                    val selected = plane == option
                    val tabColor by animateColorAsState(
                        if (selected) NeuroChrome.accent else NeuroChrome.muted,
                        animationSpec = tween(motionDuration), label = "Plane selection",
                    )
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
                            color = tabColor,
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
        DisposableEffect(volume, plane, zoomMode, probeCrosshairEnabled) {
            onDispose {
                flingJob?.cancel()
                currentScrubbingChanged(false)
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                .onSizeChanged {
                    imageViewport = it
                    onImageViewportSizeChanged(it)
                }
                .pointerInput(volume, plane, zoomMode) {
                    if (zoomMode) detectTapGestures(onDoubleTap = {
                        scale = DEFAULT_MRI_IMAGE_SCALE
                        panOffset = Offset.Zero
                    })
                }
                .pointerInput(
                    volume, plane, totalSlices, zoomMode, probeCrosshairEnabled,
                ) {
                    traceViewerGesture("pointer.start", currentSlice)
                    try {
                    if (probeCrosshairEnabled) {
                        detectTapGestures(onTap = { tap ->
                            val image = currentBitmap
                            if (image != null && imageViewport.width > 0 && imageViewport.height > 0) {
                                val fit = minOf(
                                    imageViewport.width.toFloat() / image.width,
                                    imageViewport.height.toFloat() / image.height,
                                )
                                val imageWidth = image.width * fit
                                val imageHeight = image.height * fit
                                val centerX = imageViewport.width / 2f
                                val centerY = imageViewport.height / 2f
                                val imageX =
                                    (tap.x - centerX - panOffset.x) / scale + centerX
                                val imageY =
                                    (tap.y - centerY - panOffset.y) / scale + centerY
                                val fractionX = (imageX - (imageViewport.width - imageWidth) / 2f) /
                                    imageWidth
                                val fractionY = (imageY - (imageViewport.height - imageHeight) / 2f) /
                                    imageHeight
                                val sampled = MprProbe.sample(
                                    volume, plane, currentSlice,
                                    fractionX.toDouble(), fractionY.toDouble(),
                                )
                                if (sampled != null) {
                                    currentReferenceWorldChange(sampled.worldRasMm)
                                    probeReading = "Voxel ${sampled.voxelIJK.joinToString(", ") {
                                        it.roundToInt().toString()
                                    }} · intensity ${String.format(java.util.Locale.US, "%.2f", sampled.intensity)}"
                                } else {
                                    currentReferenceWorldChange(null)
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
                    var dragAccumulator = SliceDragAccumulator(currentSlice.toFloat(), pixelsPerSlice, totalSlices - 1)
                    var scrollLocked = false
                    var tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            traceViewerGesture("drag.start", currentSlice)
                            flingJob?.cancel()
                            flingJob = null
                            dragAccumulator = SliceDragAccumulator(currentSlice.toFloat(), pixelsPerSlice, totalSlices - 1)
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
                            val previouslyLocked = scrollLocked
                            if (!scrollLocked && abs(verticalDrag) + abs(horizontalDrag) > 12.dp.toPx()) {
                                scrollLocked = abs(verticalDrag) > abs(horizontalDrag)
                            }
                            if (scrollLocked) {
                                // The gesture owns a fractional coordinate, independently of
                                // frame commits and the timing of selected-slice recomposition.
                                val nextSlice = dragAccumulator.move(if (previouslyLocked) drag.y else verticalDrag)
                                traceViewerGesture("drag.move", nextSlice)
                                currentOnSliceChange(nextSlice)
                            }
                        },
                        onDragEnd = {
                            traceViewerGesture("drag.end", currentSlice)
                            val pixelsPerSecond = tracker.calculateVelocity().y
                            if (allowsSliceInertia(currentInertialScrolling, !ValueAnimator.areAnimatorsEnabled()) &&
                                scrollLocked && abs(pixelsPerSecond) > 380f
                            ) {
                                val initialSlice = dragAccumulator.position
                                flingJob = flingScope.launch {
                                    try {
                                        Animatable(initialSlice).animateDecay(
                                            initialVelocity = (-pixelsPerSecond / pixelsPerSlice)
                                                .coerceIn(-90f, 90f),
                                            animationSpec = exponentialDecay(frictionMultiplier = 3.0f),
                                        ) {
                                            if (allowsSliceInertia(currentInertialScrolling, !ValueAnimator.areAnimatorsEnabled())) {
                                                currentOnSliceChange(value.roundToInt().coerceIn(0, totalSlices - 1))
                                            } else {
                                                currentScrubbingChanged(false)
                                                throw kotlinx.coroutines.CancellationException("Slice inertia disabled")
                                            }
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
                        onDragCancel = {
                            traceViewerGesture("drag.cancel", currentSlice)
                            currentScrubbingChanged(false)
                        },
                    )
                    }
                    } finally { traceViewerGesture("pointer.stop", currentSlice) }
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
            } else if (renderStatusVisible) {
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
                        "Rendering ${plane.label.lowercase(java.util.Locale.ROOT)} view",
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
            if (probeCrosshairEnabled && reference != null && bitmap != null && imageViewport.width > 0) {
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
                            drawLine(
                                lineColor,
                                Offset(x, 0f),
                                Offset(x, size.height),
                                crosshairThicknessDp.dp.toPx(),
                            )
                            drawLine(
                                lineColor,
                                Offset(0f, y),
                                Offset(size.width, y),
                                crosshairThicknessDp.dp.toPx(),
                            )
                        }
                    }
                }
            }
            if (focusMode) {
                TextButton(
                    onClick = onFocusToggle,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) { Text("Exit focus", color = NeuroChrome.accent) }
            }
            if (probeCrosshairEnabled) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .fillMaxWidth(0.68f),
                    color = NeuroChrome.topBar.copy(alpha = 0.94f),
                    contentColor = NeuroChrome.ink,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeuroChrome.accent.copy(alpha = 0.4f)),
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "Probe Crosshair",
                            color = NeuroChrome.accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            probeReading ?: "Tap a point on the image to inspect it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
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
                    if (showSliceButtons) {
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
                    }
                    PrecisionSliceSlider(
                        selectedSlice = selectedSlice,
                        totalSlices = totalSlices,
                        positionLabel = positionLabel,
                        onSliceChange = currentOnSliceChange,
                        onScrubbingChanged = {
                            flingJob?.cancel()
                            onScrubbingChanged(it)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (showSliceButtons) {
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
            }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .offset(y = (-4).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = zoomMode,
                    onClick = {
                        zoomMode = !zoomMode
                        if (probeCrosshairEnabled) onProbeCrosshairChange(false)
                        onReferenceWorldChange(null)
                        scale = DEFAULT_MRI_IMAGE_SCALE
                        panOffset = Offset.Zero
                    },
                    label = { Text(if (zoomMode) "Zoom" else "Scroll") },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Gesture mode"
                        stateDescription = if (zoomMode) "Zoom and pan" else "Scroll through slices"
                    },
                )
                TextButton(
                    onClick = { showAdjustments = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Display adjustments"
                    },
                ) {
                    Text("Adjust", style = MaterialTheme.typography.labelLarge)
                }
            }
            Box {
                IconButton(
                    onClick = { showMoreActions = true },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "More viewer actions"
                    },
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = NeuroChrome.ink)
                }
                DropdownMenu(
                    expanded = showMoreActions,
                    onDismissRequest = { showMoreActions = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Focus mode") },
                        onClick = {
                            showMoreActions = false
                            onFocusToggle()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Process with BrainSuite") },
                        onClick = {
                            showMoreActions = false
                            showProcessing = true
                        },
                    )
                }
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
                HorizontalDivider(color = NeuroChrome.stroke.copy(alpha = 0.65f))
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
