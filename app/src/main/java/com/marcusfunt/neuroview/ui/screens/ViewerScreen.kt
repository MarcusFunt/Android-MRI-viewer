package com.marcusfunt.neuroview.ui.screens

import android.graphics.Bitmap
import android.app.ActivityManager
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Debug
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MotionToken
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcusfunt.neuroview.BuildConfig
import com.marcusfunt.neuroview.ui.DrawOrderDiagnostics
import com.marcusfunt.neuroview.ui.traceComposeFirstDraw
import com.marcusfunt.neuroview.mri.MprRenderSession
import com.marcusfunt.neuroview.mri.MprRenderer
import com.marcusfunt.neuroview.mri.MprProbe
import com.marcusfunt.neuroview.mri.MprImage
import com.marcusfunt.neuroview.mri.MriPlane
import com.marcusfunt.neuroview.mri.ViewPlaneDescriptor
import com.marcusfunt.neuroview.mri.NativeGridOrientation
import com.marcusfunt.neuroview.mri.PlaneGeometry
import com.marcusfunt.neuroview.mri.PlaneFractions
import com.marcusfunt.neuroview.mri.NiftiVolume
import com.marcusfunt.neuroview.mri.AdaptiveQualityLevel
import com.marcusfunt.neuroview.mri.AdaptiveRenderQualityController
import com.marcusfunt.neuroview.mri.RenderEnvironment
import com.marcusfunt.neuroview.mri.RenderEnvironmentSnapshot
import com.marcusfunt.neuroview.mri.RenderPerformanceMonitor
import com.marcusfunt.neuroview.mri.RenderPerformanceSnapshot
import com.marcusfunt.neuroview.mri.RenderQualityPolicy
import com.marcusfunt.neuroview.mri.RenderQualitySettings
import com.marcusfunt.neuroview.mri.RenderRequestGate
import com.marcusfunt.neuroview.mri.RenderResourceQualityController
import com.marcusfunt.neuroview.mri.ThermalStatus
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.ui.VolumeLoadState
import com.marcusfunt.neuroview.ui.ViewerRenderPolicy
import com.marcusfunt.neuroview.ui.components.OperationProgressIndicator
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import com.marcusfunt.neuroview.ui.viewer.ViewerGestureController
import com.marcusfunt.neuroview.ui.viewer.ViewerGestureState
import com.marcusfunt.neuroview.ui.viewer.ViewportTransform
import com.marcusfunt.neuroview.ui.viewer.MeasurementPoint
import com.marcusfunt.neuroview.ui.viewer.MeasurementState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

private const val DEFAULT_MRI_IMAGE_SCALE = 1f
private const val RENDER_ENVIRONMENT_SAMPLE_INTERVAL_MS = 3_000L

private data class PendingViewerTap(val position: Offset, val upTimeMillis: Long)

private fun imageFractionsFromTap(
    tap: Offset,
    image: Bitmap?,
    viewport: IntSize,
    scale: Float,
    panOffset: Offset,
): Pair<Double, Double>? {
    if (image == null) return null
    val fractions = ViewportTransform(
        viewport.width, viewport.height, image.width, image.height, scale, panOffset,
    ).imageFractionForScreenPoint(tap) ?: return null
    return fractions.x.toDouble() to fractions.y.toDouble()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    volumeState: VolumeLoadState,
    displayNameOverride: String? = null,
    selectedDocumentUri: String? = null,
    responsiveScrollEnabled: Boolean = true,
    scrollPreviewQuality: Float = ViewerRenderPolicy.DEFAULT_PREVIEW_QUALITY,
    initialNoteId: String? = null,
    initialNotePlane: String? = null,
    initialNoteSlice: Int? = null,
    initialNoteNativeStackAxis: Int? = null,
    onSaveNote: (MriPlane, Int, Int, Int?, Boolean, String) -> Unit = { _, _, _, _, _, _ -> },
    pendingProviderCleanup: Boolean = false,
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
    var plane by rememberSaveable(selectedDocumentUri, initialNoteId) {
        mutableStateOf(
            initialNotePlane?.let { saved -> runCatching { MriPlane.valueOf(saved) }.getOrNull() }
                ?: MriPlane.AXIAL,
        )
    }
    var nativeGridSelected by rememberSaveable(selectedDocumentUri, initialNoteId) {
        mutableStateOf(initialNoteNativeStackAxis != null)
    }
    var nativeStackAxis by rememberSaveable(selectedDocumentUri, initialNoteId) {
        mutableStateOf(initialNoteNativeStackAxis)
    }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var sliceIndex by rememberSaveable(selectedDocumentUri, initialNoteId, initialNotePlane) {
        mutableIntStateOf(initialNoteSlice ?: 0)
    }
    var restoredInitialNoteId by rememberSaveable(selectedDocumentUri) { mutableStateOf<String?>(null) }
    var windowCenter by rememberSaveable { mutableFloatStateOf(0.5f) }
    var windowWidth by rememberSaveable { mutableFloatStateOf(1.0f) }
    val context = LocalContext.current
    val viewerLifecycleOwner = LocalLifecycleOwner.current
    var viewerResumed by remember(viewerLifecycleOwner) {
        mutableStateOf(viewerLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
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
                OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY -> Unit // Applied when a scan is opened.
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
    val activeDescriptor = remember(volume, plane, nativeGridSelected, nativeStackAxis) {
        if (volume != null && nativeGridSelected) {
            val assessment = NativeGridOrientation.analyze(volume)
            ViewPlaneDescriptor.nativeGrid(volume, nativeStackAxis ?: assessment.preferredStackAxis)
        } else {
            ViewPlaneDescriptor.worldAligned(plane)
        }
    }
    var bitmap by remember(volume, activeDescriptor) { mutableStateOf<Bitmap?>(null) }
    var displayedBitmapBytes by remember(volume, activeDescriptor) { mutableLongStateOf(0L) }
    var renderSessionCacheBytes by remember(volume) { mutableLongStateOf(0L) }
    var sessionInvalidationRevision by remember(volume) { mutableIntStateOf(0) }
    var lowMemorySignal by remember(volume) { mutableStateOf(false) }
    var trimMemoryLevel by remember(volume) { mutableIntStateOf(0) }
    fun releaseDisplayedFrame() {
        bitmap = null
        displayedBitmapBytes = 0L
    }
    val viewKey = if (activeDescriptor.isNativeGrid) "NATIVE_${activeDescriptor.stackAxis}" else plane.name
    LaunchedEffect(volume, scanKey, initialNoteId, initialNotePlane, initialNoteNativeStackAxis) {
        val loadedVolume = volume ?: return@LaunchedEffect
        val savedStartup = scanKey?.let { displayPrefs.getString("${it}_startup_view", null) }
        val shouldUseNative = when {
            initialNoteId != null -> initialNoteNativeStackAxis != null
            savedStartup == "NATIVE" -> true
            savedStartup != null -> false
            else -> displayPrefs.getBoolean(OPEN_NATIVE_GRID_WHEN_APPROPRIATE_KEY, true)
        }
        val startupPlane = when {
            initialNoteId != null -> initialNotePlane?.let { runCatching { MriPlane.valueOf(it) }.getOrNull() }
                ?: MriPlane.AXIAL
            savedStartup != null && savedStartup != "NATIVE" ->
                runCatching { MriPlane.valueOf(savedStartup) }.getOrDefault(MriPlane.AXIAL)
            savedStartup == "NATIVE" -> ViewPlaneDescriptor.nativeGrid(
                loadedVolume, NativeGridOrientation.analyze(loadedVolume).preferredStackAxis,
            ).nearestAnatomicalPlane ?: MriPlane.AXIAL
            else -> NativeGridOrientation.initialDescriptor(loadedVolume, shouldUseNative)
                .nearestAnatomicalPlane ?: MriPlane.AXIAL
        }
        if (shouldUseNative) {
            nativeStackAxis = initialNoteNativeStackAxis
                ?: NativeGridOrientation.analyze(loadedVolume).preferredStackAxis
        }
        plane = startupPlane
        nativeGridSelected = if (initialNoteId != null) {
            initialNoteNativeStackAxis != null
        } else {
            shouldUseNative &&
                (savedStartup == "NATIVE" || NativeGridOrientation.analyze(loadedVolume).useNativeForStartup)
        }
    }
    val powerManager = remember(context.applicationContext) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    var environmentRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(viewerLifecycleOwner, volume, activeDescriptor) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewerResumed = true
                    environmentRevision++
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewerResumed = false
                    sessionInvalidationRevision++
                }
                Lifecycle.Event.ON_STOP -> {
                    viewerResumed = false
                    sessionInvalidationRevision++
                    releaseDisplayedFrame()
                }
                else -> Unit
            }
        }
        viewerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { viewerLifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
    DisposableEffect(context.applicationContext, volume, activeDescriptor) {
        val appContext = context.applicationContext
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
            @Suppress("DEPRECATION")
            override fun onLowMemory() {
                lowMemorySignal = true
                trimMemoryLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                sessionInvalidationRevision++
                environmentRevision++
                releaseDisplayedFrame()
            }

            override fun onTrimMemory(level: Int) {
                trimMemoryLevel = level
                val memoryPressure = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                if (memoryPressure) lowMemorySignal = true
                if (memoryPressure || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                    // Cancel the active renderer before dropping its displayed frame so
                    // a repeated warning always schedules a replacement frame.
                    sessionInvalidationRevision++
                    releaseDisplayedFrame()
                }
                environmentRevision++
            }
        }
        appContext.registerComponentCallbacks(callbacks)
        onDispose { appContext.unregisterComponentCallbacks(callbacks) }
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
    var renderZoomScale by rememberSaveable(selectedDocumentUri) { mutableFloatStateOf(1f) }
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
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SEVERE
            else -> ThermalStatus.NOMINAL
        }
    } else ThermalStatus.NOMINAL
    val resourceQualityController = remember(volume) { RenderResourceQualityController() }
    var renderEnvironmentSnapshot by remember(volume) {
        mutableStateOf<RenderEnvironmentSnapshot?>(null)
    }
    val refreshRenderEnvironment by rememberUpdatedState(newValue = {
        val runtimeNow = Runtime.getRuntime()
        val maxHeap = runtimeNow.maxMemory()
        val usedHeap = (runtimeNow.totalMemory() - runtimeNow.freeMemory()).coerceAtLeast(0L)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val systemInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(systemInfo)
        val debugMemoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugMemoryInfo)
        val raw = RenderEnvironmentSnapshot(
            maxHeapBytes = maxHeap,
            availableHeapBytes = (maxHeap - usedHeap).coerceAtLeast(0L),
            usedHeapBytes = usedHeap,
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
            processPssBytes = debugMemoryInfo.totalPss.coerceAtLeast(0).toLong() * 1024L,
            bitmapBytes = displayedBitmapBytes,
            cachedRenderBytes = renderSessionCacheBytes,
            systemAvailableBytes = if (activityManager != null) systemInfo.availMem.coerceAtLeast(0L)
                else Long.MAX_VALUE,
            lowMemory = lowMemorySignal || systemInfo.lowMemory,
            trimMemoryLevel = trimMemoryLevel,
            viewportMaxSidePx = viewportMaxSidePx,
            powerSave = powerManager?.isPowerSaveMode == true,
            thermalStatus = thermalStatus,
            zoomScale = renderZoomScale,
            sourceMaxSide = volume?.let { maxOf(it.nx, it.ny, it.nz) }
                ?: RenderQualitySettings.MAX_STILL_SIDE,
        )
        val decision = resourceQualityController.update(raw, SystemClock.elapsedRealtime())
        renderEnvironmentSnapshot = raw.copy(memorySafetyCapOverride = decision.maximumSide)
        lowMemorySignal = false
    })
    LaunchedEffect(
        viewerLifecycleOwner,
        volume,
        environmentRevision,
        viewerResumed,
        viewportMaxSidePx,
        renderZoomScale,
        renderQualitySettings,
        powerSave,
        thermalStatus,
    ) {
        if (volume == null || !viewerResumed) return@LaunchedEffect
        refreshRenderEnvironment()
        while (true) {
            delay(RENDER_ENVIRONMENT_SAMPLE_INTERVAL_MS)
            refreshRenderEnvironment()
        }
    }
    val renderEnvironment = renderEnvironmentSnapshot ?: RenderEnvironment(
        maxHeapBytes = runtimeMaxHeap,
        availableHeapBytes = runtimeAvailableHeap,
        viewportMaxSidePx = viewportMaxSidePx,
        powerSave = powerSave,
        thermalStatus = thermalStatus,
        zoomScale = renderZoomScale,
        sourceMaxSide = volume?.let { maxOf(it.nx, it.ny, it.nz) }
            ?: RenderQualitySettings.MAX_STILL_SIDE,
    )
    var adaptiveQualityLevel by remember(volume) {
        mutableStateOf(AdaptiveQualityLevel.STANDARD)
    }
    val adaptiveQualityController = remember(volume) { AdaptiveRenderQualityController() }
    val baseRenderQualityProfile = remember(renderQualitySettings, renderEnvironment, adaptiveQualityLevel) {
        RenderQualityPolicy.resolve(renderQualitySettings, renderEnvironment, adaptiveQualityLevel)
    }
    val renderQualityProfile = remember(
        baseRenderQualityProfile,
        responsiveScrollEnabled,
        scrollPreviewQuality,
    ) {
        baseRenderQualityProfile.copy(
            previewMaxSide = if (responsiveScrollEnabled &&
                baseRenderQualityProfile.stillMaxSide > ViewerRenderPolicy.MIN_PREVIEW_RESOLUTION
            ) {
                (baseRenderQualityProfile.previewMaxSide *
                    (scrollPreviewQuality / ViewerRenderPolicy.DEFAULT_PREVIEW_QUALITY))
                    .roundToInt()
                    .coerceIn(
                        minOf(
                            ViewerRenderPolicy.MIN_PREVIEW_RESOLUTION,
                            baseRenderQualityProfile.stillMaxSide,
                        ),
                        baseRenderQualityProfile.stillMaxSide,
                    )
            } else {
                baseRenderQualityProfile.stillMaxSide
            },
        )
    }
    val renderPerformanceMonitor = remember(volume) { RenderPerformanceMonitor() }
    var renderPerformance by remember(volume) { mutableStateOf(RenderPerformanceSnapshot()) }
    fun noteViewerInput() {
        lastViewerInputAtNs = SystemClock.elapsedRealtimeNanos()
    }
    var referenceWorld by remember(volume) { mutableStateOf<DoubleArray?>(null) }
    val sliceCount = if (volume != null) MprRenderer.sliceCount(volume, activeDescriptor) else 1
    val selectedSlice = sliceIndex.coerceIn(0, sliceCount - 1)

    LaunchedEffect(volume, plane, nativeGridSelected, nativeStackAxis, initialNoteId) {
        if (volume == null) {
            sliceIndex = 0
            return@LaunchedEffect
        }
        val count = MprRenderer.sliceCount(volume, activeDescriptor)
        if (initialNoteId != null && restoredInitialNoteId != initialNoteId) {
            if (initialNotePlane == plane.name && initialNoteSlice != null) {
                sliceIndex = initialNoteSlice.coerceIn(0, count - 1)
            }
            restoredInitialNoteId = initialNoteId
        } else {
            sliceIndex = when {
                referenceWorld != null -> MprProbe.sliceForWorld(volume, activeDescriptor, requireNotNull(referenceWorld))
                scanKey != null -> displayPrefs.getInt("${scanKey}_${viewKey}_slice", count / 2)
                    .coerceIn(0, count - 1)
                else -> count / 2
            }
        }
    }
    LaunchedEffect(scanKey, viewKey, sliceIndex, sliceCount, volume) {
        if (scanKey == null || volume == null) return@LaunchedEffect
        delay(180L)
        displayPrefs.edit().putInt("${scanKey}_${viewKey}_slice", selectedSlice).apply()
    }

    LaunchedEffect(volume) {
        // A newly loaded scan starts a fresh latency sample window.
        noteViewerInput()
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
    var committedRenderGeneration by remember(volume, activeDescriptor) { mutableLongStateOf(0L) }
    var committedInputStartedAtNs by remember(volume, activeDescriptor) { mutableLongStateOf(0L) }
    var lastMeasuredDisplayGeneration by remember(volume, activeDescriptor) { mutableLongStateOf(0L) }
    var renderStatusVisible by remember(volume, activeDescriptor) { mutableStateOf(false) }
    LaunchedEffect(bitmap, volume, activeDescriptor) {
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
    val renderRequestGate = remember(volume, activeDescriptor) { RenderRequestGate() }
    val currentRefineDelayMs by rememberUpdatedState(renderQualityProfile.refineDelayMs)
    LaunchedEffect(
        volume,
        activeDescriptor,
        renderQualityProfile.previewMaxSide,
        renderQualityProfile.stillMaxSide,
        sessionInvalidationRevision,
        viewerResumed,
    ) {
        if (volume == null || !viewerResumed) return@LaunchedEffect
        val operationId = loading?.operation?.id
        val renderJob = currentCoroutineContext()[Job]
        var previewSession: MprRenderSession? = null
        var stillSession: MprRenderSession? = null
        if (operationId != null && renderJob != null) {
            onFirstSliceRenderStarted(operationId, volume, renderJob)
        }
        try {
            val preview = withContext(Dispatchers.Default) {
                MprRenderSession(
                    volume, activeDescriptor, renderQualityProfile.previewMaxSide,
                    cacheBudgetBytes = MprRenderSession.PREVIEW_CACHE_BUDGET_BYTES,
                )
            }
            previewSession = preview
            val full = if (renderQualityProfile.stillMaxSide == renderQualityProfile.previewMaxSide) {
                preview
            } else {
                withContext(Dispatchers.Default) {
                    MprRenderSession(
                        volume, activeDescriptor, renderQualityProfile.stillMaxSide,
                        cacheBudgetBytes = MprRenderSession.STILL_CACHE_BUDGET_BYTES,
                    )
                }
            }
            stillSession = full
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
                    } catch (_: OutOfMemoryError) {
                        lowMemorySignal = true
                        sessionInvalidationRevision++
                        environmentRevision++
                        refreshRenderEnvironment()
                        return@collectLatest
                    } finally {
                        workerRenderDurationMs += (SystemClock.elapsedRealtimeNanos() - previewStartedAt)
                            .coerceAtLeast(0L) / 1_000_000L
                    }
                    if (renderRequestGate.isCurrent(request.generation) && latestSliceInput() == input) {
                        bitmap = quick
                        displayedBitmapBytes = quick.allocationByteCount.toLong()
                        traceViewerCommit(quick, request.generation, input.slice, "preview")
                        committedRenderGeneration = request.generation
                        committedInputStartedAtNs = request.inputStartedAtNs
                        committed = true
                        renderSessionCacheBytes = preview.cachedBytes +
                            if (full === preview) 0L else full.cachedBytes
                        refreshRenderEnvironment()
                    }
                    if (!input.scrubbing && renderRequestGate.isCurrent(request.generation) &&
                        latestSliceInput() == input
                    ) {
                        delay(currentRefineDelayMs)
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
                        } catch (_: OutOfMemoryError) {
                            // Keep the already rendered preview visible; the pressure snapshot
                            // cancels this session and retries at the controller's safe cap.
                            lowMemorySignal = true
                            sessionInvalidationRevision++
                            environmentRevision++
                            bitmap = quick
                            displayedBitmapBytes = quick.allocationByteCount.toLong()
                            refreshRenderEnvironment()
                            null
                        } finally {
                            settledRenderDurationMs = (SystemClock.elapsedRealtimeNanos() - stillStartedAt)
                                .coerceAtLeast(0L) / 1_000_000L
                            workerRenderDurationMs += requireNotNull(settledRenderDurationMs)
                        }
                        if (refined != null && renderRequestGate.isCurrent(request.generation) && latestSliceInput() == input) {
                            bitmap = refined
                            displayedBitmapBytes = refined.allocationByteCount.toLong()
                            traceViewerCommit(refined, request.generation, input.slice, "still")
                            committedRenderGeneration = request.generation
                            committedInputStartedAtNs = request.inputStartedAtNs
                            committed = true
                            settledFrameCommitted = true
                            renderSessionCacheBytes = preview.cachedBytes +
                                if (full === preview) 0L else full.cachedBytes
                            refreshRenderEnvironment()
                        }
                    }
                } finally {
                    val runtime = Runtime.getRuntime()
                    renderPerformance = renderPerformanceMonitor.recordRender(
                        durationMs = workerRenderDurationMs,
                        committed = committed,
                        heapUsedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L),
                        nativeHeapBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
                        processPssBytes = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
                            .coerceAtLeast(0).toLong() * 1024L,
                        bitmapBytes = displayedBitmapBytes,
                        canceled = !currentCoroutineContext().isActive,
                    )
                    if (settledFrameCommitted) {
                        adaptiveQualityLevel = adaptiveQualityController.recordRenderDuration(
                            settledRenderDurationMs ?: workerRenderDurationMs,
                        )
                    }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                previewSession?.clearCache()
                if (stillSession !== previewSession) stillSession?.clearCache()
            }
            renderSessionCacheBytes = 0L
            refreshRenderEnvironment()
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
                            recoveryMessage = volumeState.recoveryMessage,
                            pendingProviderCleanup = pendingProviderCleanup,
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
                            onZoomScaleChanged = { renderZoomScale = it },
                            volume = requireNotNull(volume),
                            plane = plane,
                            descriptor = activeDescriptor,
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
                                nativeGridSelected = false
                                sliceIndex = when {
                                    referenceWorld != null -> MprProbe.sliceForWorld(
                                        requireNotNull(volume),
                                        ViewPlaneDescriptor.worldAligned(selectedPlane),
                                        requireNotNull(referenceWorld),
                                    )
                                    else -> MprRenderer.sliceCount(requireNotNull(volume), selectedPlane) / 2
                                }
                                plane = selectedPlane
                            },
                            onNativeGridSelect = {
                                noteViewerInput()
                                val native = ViewPlaneDescriptor.nativeGrid(
                                    requireNotNull(volume),
                                    NativeGridOrientation.analyze(requireNotNull(volume)).preferredStackAxis,
                                )
                                nativeStackAxis = native.stackAxis
                                plane = native.nearestAnatomicalPlane ?: MriPlane.AXIAL
                                nativeGridSelected = true
                                sliceIndex = if (referenceWorld != null) {
                                    MprProbe.sliceForWorld(requireNotNull(volume), native, requireNotNull(referenceWorld))
                                } else native.let { MprRenderer.sliceCount(requireNotNull(volume), it) / 2 }
                            },
                            onStartupViewChange = { view ->
                                if (scanKey != null) {
                                    displayPrefs.edit().putString("${scanKey}_startup_view", view).apply()
                                }
                                if (view == "NATIVE") {
                                    val native = ViewPlaneDescriptor.nativeGrid(
                                        requireNotNull(volume),
                                        NativeGridOrientation.analyze(requireNotNull(volume)).preferredStackAxis,
                                    )
                                    nativeStackAxis = native.stackAxis
                                    plane = native.nearestAnatomicalPlane ?: MriPlane.AXIAL
                                    nativeGridSelected = true
                                    sliceIndex = MprRenderer.sliceCount(requireNotNull(volume), native) / 2
                                } else {
                                    val selectedPlane = runCatching { MriPlane.valueOf(view) }
                                        .getOrDefault(MriPlane.AXIAL)
                                    plane = selectedPlane
                                    nativeGridSelected = false
                                    sliceIndex = MprRenderer.sliceCount(requireNotNull(volume), selectedPlane) / 2
                                }
                            },
                            onResetStartupView = {
                                if (scanKey != null) displayPrefs.edit().remove("${scanKey}_startup_view").apply()
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
                            onSaveNote = onSaveNote,
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
            memorySafeMaximumSidePx = renderEnvironment.memorySafetyCapOverride
                ?: RenderQualityPolicy.memorySafeMaximumSide(
                    renderEnvironment.maxHeapBytes,
                    renderEnvironment.effectiveAvailableHeapBytes,
                ),
            renderQualityProfile = renderQualityProfile,
            renderEnvironmentSnapshot = renderEnvironmentSnapshot,
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
    recoveryMessage: String?,
    pendingProviderCleanup: Boolean,
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
            if (pendingProviderCleanup) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "A canceled file-provider read is still pending cleanup. Its resources remain owned until it returns.",
                    color = NeuroChrome.muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (operation.status == OperationStatus.CANCELING) {
                Text("Stopping…", color = NeuroChrome.muted, style = MaterialTheme.typography.labelLarge)
                if (recoveryMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        recoveryMessage,
                        color = NeuroChrome.muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onReturnToLibrary) { Text("RETURN TO LIBRARY") }
                } else if (cancelRequestedAtElapsedMs != null &&
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
    onZoomScaleChanged: (Float) -> Unit,
    volume: NiftiVolume,
    plane: MriPlane,
    descriptor: ViewPlaneDescriptor,
    bitmap: Bitmap?,
    renderStatusVisible: Boolean,
    onImageViewportSizeChanged: (IntSize) -> Unit,
    selectedSlice: Int,
    totalSlices: Int,
    onSliceChange: (Int) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onPlaneSelect: (MriPlane) -> Unit,
    onNativeGridSelect: () -> Unit,
    onStartupViewChange: (String) -> Unit,
    onResetStartupView: () -> Unit,
    selectedDocumentUri: String?,
    originalDocumentName: String?,
    referenceWorld: DoubleArray?,
    onReferenceWorldChange: (DoubleArray?) -> Unit,
    windowCenter: Float,
    onWindowCenter: (Float) -> Unit,
    windowWidth: Float,
    onWindowWidth: (Float) -> Unit,
    onSaveNote: (MriPlane, Int, Int, Int?, Boolean, String) -> Unit = { _, _, _, _, _, _ -> },
) {
    val motionDuration = MotionToken.QUICK.durationMs(LocalNeuroViewMotion.current)
    val density = LocalDensity.current
    // Gesture coroutines persist across recompositions; always read the latest slice.
    val currentSlice by rememberUpdatedState(selectedSlice)
    val currentOnSliceChange by rememberUpdatedState(onSliceChange)
    val currentBitmap by rememberUpdatedState(bitmap)
    val currentInertialScrolling by rememberUpdatedState(inertialScrolling)
    val currentReferenceWorldChange by rememberUpdatedState(onReferenceWorldChange)
    val currentScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
    var showAdjustments by rememberSaveable { mutableStateOf(false) }
    var showProcessing by rememberSaveable { mutableStateOf(false) }
    var showMoreActions by rememberSaveable { mutableStateOf(false) }
    var showInspectMenu by rememberSaveable { mutableStateOf(false) }
    var noteEditorOpen by rememberSaveable { mutableStateOf(false) }
    var noteText by rememberSaveable { mutableStateOf("") }
    var noteSaved by rememberSaveable { mutableStateOf(false) }
    var orientationMenuExpanded by rememberSaveable(volume) { mutableStateOf(false) }
    var orientationDetailsOpen by rememberSaveable(volume) { mutableStateOf(false) }
    val editStateHolder = rememberSaveableStateHolder()
    var bridgeToken by remember { mutableStateOf("") }
    var measurementMode by rememberSaveable(volume, descriptor) { mutableStateOf(false) }
    var endpointSnapEnabled by rememberSaveable(volume) { mutableStateOf(false) }
    var showUnknownSpatialUnitsDialog by remember(volume) { mutableStateOf(false) }
    var measurementState by remember(volume, descriptor, selectedSlice) { mutableStateOf(MeasurementState()) }
    val measurementSamplingGeometry = remember(volume, descriptor) {
        PlaneGeometry.create(volume, descriptor, MprRenderer.MAX_IMAGE_SIDE)
    }
    val currentMeasurementState by rememberUpdatedState(measurementState)
    val currentMeasurementMode by rememberUpdatedState(measurementMode)
    val currentProbeMode by rememberUpdatedState(probeCrosshairEnabled)
    var probeReading by remember(volume) { mutableStateOf<String?>(null) }
    var scale by rememberSaveable(volume) { mutableFloatStateOf(DEFAULT_MRI_IMAGE_SCALE) }
    var panOffset by rememberSaveable(volume, stateSaver = androidx.compose.runtime.saveable.Saver<Offset, List<Float>>(
        save = { listOf(it.x, it.y) }, restore = { Offset(it[0], it[1]) },
    )) { mutableStateOf(Offset.Zero) }
    var imageViewport by remember { mutableStateOf(IntSize.Zero) }
    var pendingTap by remember(volume, descriptor) { mutableStateOf<PendingViewerTap?>(null) }
    var pendingTapJob by remember(volume, descriptor) { mutableStateOf<Job?>(null) }
    val flingScope = rememberCoroutineScope()
    var flingJob by remember(volume, descriptor) { mutableStateOf<Job?>(null) }

    fun setMeasurementMode(enabled: Boolean) {
        if (enabled && !volume.hasKnownSpatialUnits) {
            showUnknownSpatialUnitsDialog = true
            return
        }
        if (enabled) {
            measurementState = MeasurementState()
            measurementMode = true
            if (probeCrosshairEnabled) onProbeCrosshairChange(false)
            onReferenceWorldChange(null)
        } else {
            measurementMode = false
            measurementState = MeasurementState()
        }
    }

    fun setProbeMode(enabled: Boolean) {
        if (enabled && measurementMode) setMeasurementMode(false)
        if (enabled != probeCrosshairEnabled) onProbeCrosshairChange(enabled)
        probeReading = null
        onReferenceWorldChange(null)
    }

    fun viewportTransform(image: Bitmap? = currentBitmap): ViewportTransform? {
        if (image == null || imageViewport.width <= 0 || imageViewport.height <= 0) return null
        return ViewportTransform(
            imageViewport.width, imageViewport.height, image.width, image.height, scale, panOffset,
        )
    }

    fun measurementPointAt(horizontal: Double, vertical: Double): MeasurementPoint? {
        val fractions = if (endpointSnapEnabled) {
            measurementSamplingGeometry.snapFractionsToOutputGrid(horizontal, vertical)
        } else {
            PlaneFractions(horizontal, vertical)
        }
        val sample = measurementSamplingGeometry.sample(
            volume, currentSlice, fractions.horizontal, fractions.vertical,
        ) ?: return null
        return MeasurementPoint(fractions.horizontal, fractions.vertical, sample.worldRasMm)
    }

    fun handleToolTap(tap: Offset) {
        val fractions = imageFractionsFromTap(tap, currentBitmap, imageViewport, scale, panOffset)
            ?: return
        if (currentMeasurementMode) {
            measurementState = currentMeasurementState.add(
                measurementPointAt(fractions.first, fractions.second),
            )
        } else if (currentProbeMode) {
            val sampled = MprProbe.sample(
                volume, descriptor, currentSlice, fractions.first, fractions.second,
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
    }

    fun measurementHandleAt(tap: Offset): Int? {
        if (!currentMeasurementMode || currentMeasurementState.points.isEmpty()) return null
        val transform = viewportTransform() ?: return null
        val hitRadius = with(density) { 24.dp.toPx() }
        return currentMeasurementState.points.indices
            .mapNotNull { index ->
                val point = currentMeasurementState.points[index]
                val screen = transform.screenPointForImageFraction(
                    Offset(point.fractionX.toFloat(), point.fractionY.toFloat()),
                ) ?: return@mapNotNull null
                val dx = screen.x - tap.x
                val dy = screen.y - tap.y
                val distance = sqrt(dx * dx + dy * dy)
                index to distance
            }
            .filter { it.second <= hitRadius }
            .minByOrNull { it.second }
            ?.first
    }

    fun moveMeasurementHandle(index: Int, position: Offset) {
        val image = currentBitmap ?: return
        val fraction = viewportTransform(image)?.imageFractionForScreenPoint(position) ?: return
        val point = measurementPointAt(fraction.x.toDouble(), fraction.y.toDouble()) ?: return
        measurementState = currentMeasurementState.move(
            index,
            point,
        )
    }

    fun nudgeMeasurementPoint(index: Int, horizontalDelta: Double, verticalDelta: Double) {
        val point = currentMeasurementState.points.getOrNull(index) ?: return
        val nextX = (point.fractionX + horizontalDelta).coerceIn(0.0, 1.0)
        val nextY = (point.fractionY + verticalDelta).coerceIn(0.0, 1.0)
        measurementState = currentMeasurementState.move(index, measurementPointAt(nextX, nextY))
    }

    fun finishMeasurement() {
        val finished = currentMeasurementState.finish()
        if (finished.isFinished) {
            measurementState = finished
            measurementMode = false
        }
    }

    fun applyDoubleTap(tap: Offset) {
        val transform = viewportTransform() ?: return
        val next = if (scale > 1.01f) transform.fit() else transform.zoomAt(tap, 2f)
        scale = next.scale
        onZoomScaleChanged(next.scale)
        panOffset = next.panOffset
    }

    fun adjustZoom(factor: Float) {
        val transform = viewportTransform() ?: return
        val next = transform.zoomAt(
            Offset(imageViewport.width / 2f, imageViewport.height / 2f),
            factor,
        )
        scale = next.scale
        onZoomScaleChanged(next.scale)
        panOffset = next.panOffset
    }

    fun fitImage() {
        val fit = viewportTransform()?.fit() ?: return
        scale = fit.scale
        onZoomScaleChanged(fit.scale)
        panOffset = fit.panOffset
    }

    fun panImage(horizontalDirection: Int, verticalDirection: Int) {
        val transform = viewportTransform() ?: return
        val moved = transform.copy(
            panOffset = transform.panOffset + Offset(
                imageViewport.width * horizontalDirection * -0.25f,
                imageViewport.height * verticalDirection * -0.25f,
            ),
        ).clamped()
        panOffset = moved.panOffset
    }

    fun commitPendingTap() {
        val tap = pendingTap
        pendingTapJob?.cancel()
        pendingTapJob = null
        pendingTap = null
        if (tap != null) handleToolTap(tap.position)
    }

    LaunchedEffect(imageViewport.width, imageViewport.height, bitmap?.width, bitmap?.height) {
        val transform = viewportTransform(bitmap) ?: return@LaunchedEffect
        val bounded = transform.clamped()
        scale = bounded.scale
        onZoomScaleChanged(bounded.scale)
        panOffset = bounded.panOffset
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, volume, descriptor) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                flingJob?.cancel()
                flingJob = null
                pendingTapJob?.cancel()
                pendingTapJob = null
                pendingTap = null
                currentScrubbingChanged(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(probeCrosshairEnabled, volume, descriptor) {
        probeReading = null
        currentReferenceWorldChange(null)
        if (probeCrosshairEnabled) {
            setMeasurementMode(false)
        }
    }
    val orientationUnknown = volume.transformSource.startsWith("NO ORIENTATION")
    val nativeAssessment = remember(volume) { NativeGridOrientation.analyze(volume) }
    val planeGeometry = remember(volume, descriptor, bitmap?.width) {
        PlaneGeometry.create(volume, descriptor, maxOf(8, bitmap?.width ?: MprRenderer.MAX_IMAGE_SIDE))
    }
    val orientationDetails = remember(volume, descriptor, bitmap?.width, bitmap?.height) {
        val directions = (0..2).joinToString("\n") { axis ->
            val name = listOf("i", "j", "k")[axis]
            val direction = volume.affine.voxelAxisDirectionRas(axis)
                .joinToString(", ") { "%.3f".format(java.util.Locale.US, it) }
            "$name axis → RAS+ ($direction)"
        }
        val pixdim = volume.sourcePixdimMm.joinToString(" × ") {
            "%.3f".format(java.util.Locale.US, it)
        }
        val unitLabel = if (volume.hasKnownSpatialUnits) "mm" else "header values; units unset"
        val inPlaneNumbers = "%.3f × %.3f".format(
            java.util.Locale.US,
            planeGeometry.sourcePixelSpacingHorizontalMm,
            planeGeometry.sourcePixelSpacingVerticalMm,
        )
        val inPlane = if (volume.hasKnownSpatialUnits) "$inPlaneNumbers mm"
            else "$inPlaneNumbers (units unknown; no mm calibration)"
        val renderedPixels = if (volume.hasKnownSpatialUnits) {
            "%.3f × %.3f mm".format(
                java.util.Locale.US,
                planeGeometry.renderedPixelSpacingHorizontalMm,
                planeGeometry.renderedPixelSpacingVerticalMm,
            )
        } else "unavailable (units unknown)"
        val renderedSpacing = planeGeometry.renderedSliceSpacingMm()?.let {
            "%.3f mm".format(java.util.Locale.US, it)
        } ?: "unavailable (units unset or one slice)"
        val sourceKind = when {
            nativeAssessment.nearIsotropic -> "near-isotropic heuristic; acquisition type unknown"
            nativeAssessment.likely2DLike -> "anisotropic / 2D-like heuristic; not confirmed"
            else -> "acquisition type unknown"
        }
        val obliquity = "%.2f°".format(java.util.Locale.US, descriptor.obliquityDegrees)
        val effectiveSize = bitmap?.let { "${it.width} × ${it.height} px" } ?: "render pending"
        buildString {
            appendLine("Stored grid: ${volume.nx} × ${volume.ny} × ${volume.nz} voxels")
            appendLine("Header pixdim: $pixdim $unitLabel · qfac ${volume.sourceQfac?.toString() ?: "not available"}")
            appendLine("Affine: ${volume.transformSource}${if (volume.affine.hasShear()) " · shear present" else ""}")
            appendLine(directions)
            appendLine("Grid classification: $sourceKind")
            appendLine("Current view: ${descriptor.label} · nearest plane ${descriptor.nearestAnatomicalPlane?.label ?: "unknown"} · obliquity $obliquity")
            appendLine("View orientation: RAS+ affine; DICOM convention is LPS. Patient labels depend on a valid transform.")
            appendLine("Image: $effectiveSize · source in-plane spacing $inPlane · rendered pixel spacing $renderedPixels · rendered slice spacing $renderedSpacing")
            append("“Native grid” means the orientation stored in this NIfTI file; it does not prove original scanner acquisition orientation.")
        }
    }
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
            Column(modifier = Modifier.fillMaxWidth().traceComposeFirstDraw("viewer.plane-tabs")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .offset(y = (-4).dp).horizontalScroll(rememberScrollState()).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MriPlane.entries.forEach { option ->
                        val selected = plane == option
                        val tabColor by animateColorAsState(
                            if (selected) NeuroChrome.accent else NeuroChrome.muted,
                            animationSpec = tween(motionDuration), label = "Plane selection",
                        )
                        Box(
                            modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 48.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onPlaneSelect(option) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    color = tabColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (selected && descriptor.isNativeGrid && !descriptor.isObliqueNative) {
                                    Text(if (nativeAssessment.orientationKnown) "Native grid" else "Native*",
                                        color = NeuroChrome.accent.copy(alpha = 0.82f),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        maxLines = 1,
                                    )
                                }
                            }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (descriptor.isNativeGrid) {
                            Text(
                                descriptor.label,
                                modifier = Modifier.weight(1f),
                                color = NeuroChrome.accent,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = if (descriptor.isNativeGrid) {
                                { onPlaneSelect(descriptor.nearestAnatomicalPlane ?: MriPlane.AXIAL) }
                            } else onNativeGridSelect,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                if (descriptor.isNativeGrid) "Return to ${descriptor.nearestAnatomicalPlane?.label ?: "Axial"}"
                                else if (!nativeAssessment.orientationKnown) "Native grid · assumed"
                                else if (nativeAssessment.obliquityDegrees > NativeGridOrientation.ALIGNED_TOLERANCE_DEGREES) "Native · oblique"
                                else "Native grid",
                                color = NeuroChrome.accent,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box {
                        TextButton(
                            onClick = { orientationMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = "Startup view settings"
                            },
                        ) { Text("Startup", color = NeuroChrome.muted, style = MaterialTheme.typography.labelSmall) }
                        DropdownMenu(
                            expanded = orientationMenuExpanded,
                            onDismissRequest = { orientationMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Native grid on startup for this scan") },
                                onClick = { orientationMenuExpanded = false; onStartupViewChange("NATIVE") },
                            )
                            MriPlane.entries.forEach { startupPlane ->
                                DropdownMenuItem(
                                    text = { Text("Open ${startupPlane.label} on startup for this scan") },
                                    onClick = {
                                        orientationMenuExpanded = false
                                        onStartupViewChange(startupPlane.name)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Follow global startup preference") },
                                onClick = { orientationMenuExpanded = false; onResetStartupView() },
                            )
                            DropdownMenuItem(
                                text = { Text("Orientation details") },
                                onClick = { orientationMenuExpanded = false; orientationDetailsOpen = true },
                            )
                        }
                    }
                }
                if (DrawOrderDiagnostics.isEnabled()) {
                    Box(
                        modifier = Modifier.align(Alignment.CenterHorizontally).offset(x = 140.dp).size(12.dp)
                            .zIndex(10f).background(Color.Magenta)
                            .testTag("viewer-tabs-overlay-diagnostic"),
                    )
                }
            }
        }
        DisposableEffect(volume, descriptor, probeCrosshairEnabled, measurementMode) {
            onDispose {
                flingJob?.cancel()
                pendingTapJob?.cancel()
                pendingTap = null
                currentScrubbingChanged(false)
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .traceComposeFirstDraw("viewer.image-viewport")
                .clipToBounds()
                .background(Color.Black)
                .onSizeChanged {
                    imageViewport = it
                    onImageViewportSizeChanged(it)
                }
                .pointerInput(volume, descriptor, totalSlices) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        flingJob?.cancel()
                        flingJob = null
                        if (firstDown.isConsumed) return@awaitEachGesture
                        traceViewerGesture("pointer.start", currentSlice)
                        currentScrubbingChanged(false)

                        val doubleTapSlop = viewConfiguration.touchSlop * 2.5f
                        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                        val previousTap = pendingTap
                        val canBeDoubleTap = previousTap != null &&
                            firstDown.uptimeMillis - previousTap.upTimeMillis in 0..doubleTapTimeout &&
                            (firstDown.position - previousTap.position).getDistance() <= doubleTapSlop
                        if (previousTap != null && !canBeDoubleTap) commitPendingTap()

                        val controller = ViewerGestureController()
                        val handleAtDown = measurementHandleAt(firstDown.position)
                        val pixelsPerSlice = 18.dp.toPx()
                        val dragAccumulator = SliceDragAccumulator(
                            currentSlice.toFloat(), pixelsPerSlice, totalSlices - 1,
                        )
                        val tracker = VelocityTracker()
                        var horizontalDrag = 0f
                        var verticalDrag = 0f
                        var previousPosition = firstDown.position
                        var previousPointerCount = 1
                        var previousCentroid = firstDown.position
                        var previousSpan = 0f
                        var scrollLocked = false
                        var maximumTravel = 0f
                        var completedNormally = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (controller.state == ViewerGestureState.TAP_CANDIDATE &&
                                    event.changes.any { it.isConsumed }
                                ) {
                                    completedNormally = true
                                    break
                                }
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size > 1) {
                                    controller.onPointerCount(pressed.size)
                                    if (previousTap != null && canBeDoubleTap) commitPendingTap()
                                    var centerX = 0f
                                    var centerY = 0f
                                    pressed.forEach {
                                        centerX += it.position.x / pressed.size
                                        centerY += it.position.y / pressed.size
                                    }
                                    val centroid = Offset(centerX, centerY)
                                    var radiusSum = 0f
                                    pressed.forEach {
                                        val dx = it.position.x - centerX
                                        val dy = it.position.y - centerY
                                        radiusSum += sqrt(dx * dx + dy * dy)
                                    }
                                    val span = radiusSum * 2f / pressed.size
                                    if (previousPointerCount != pressed.size || previousSpan <= 0f) {
                                        previousCentroid = centroid
                                        previousSpan = span
                                        previousPointerCount = pressed.size
                                    } else {
                                        val transform = viewportTransform()
                                        if (transform != null && span > 0f) {
                                            val updated = transform.transformed(
                                                centroid = centroid,
                                                panDelta = centroid - previousCentroid,
                                                zoomFactor = span / previousSpan,
                                            )
                                            scale = updated.scale
                                            onZoomScaleChanged(updated.scale)
                                            panOffset = updated.panOffset
                                        }
                                        previousCentroid = centroid
                                        previousSpan = span
                                    }
                                    previousPointerCount = pressed.size
                                    currentScrubbingChanged(false)
                                    if (controller.state != ViewerGestureState.TAP_CANDIDATE) {
                                        event.changes.forEach { it.consume() }
                                    }
                                    continue
                                }

                                if (pressed.size == 1) {
                                    val change = pressed.single()
                                    val delta = change.position - previousPosition
                                    val fromStart = change.position - firstDown.position
                                    maximumTravel = maxOf(maximumTravel, fromStart.getDistance())
                                    if (controller.state == ViewerGestureState.TAP_CANDIDATE &&
                                        maximumTravel > viewConfiguration.touchSlop
                                    ) {
                                        if (previousTap != null && canBeDoubleTap) commitPendingTap()
                                        controller.onDragStarted(
                                            isToolMode = currentMeasurementMode || currentProbeMode,
                                            measurementHandleIndex = handleAtDown,
                                        )
                                        if (controller.state == ViewerGestureState.SLICE_DRAG) {
                                            traceViewerGesture("drag.start", currentSlice)
                                        }
                                    }

                                    when (controller.state) {
                                        ViewerGestureState.SLICE_DRAG -> {
                                            horizontalDrag += delta.x
                                            verticalDrag += delta.y
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            val wasLocked = scrollLocked
                                            if (!scrollLocked &&
                                                abs(verticalDrag) + abs(horizontalDrag) > 12.dp.toPx()
                                            ) {
                                                scrollLocked = abs(verticalDrag) > abs(horizontalDrag)
                                            }
                                            if (scrollLocked) {
                                                if (!wasLocked) currentScrubbingChanged(true)
                                                val nextSlice = dragAccumulator.move(
                                                    if (wasLocked) delta.y else verticalDrag,
                                                )
                                                traceViewerGesture("drag.move", nextSlice)
                                                currentOnSliceChange(nextSlice)
                                            }
                                        }
                                        ViewerGestureState.HANDLE_DRAG -> {
                                            val handle = requireNotNull(handleAtDown)
                                            moveMeasurementHandle(handle, change.position)
                                        }
                                        ViewerGestureState.TAP_CANDIDATE,
                                        ViewerGestureState.PINCH_PAN,
                                        ViewerGestureState.TOOL_DRAG -> Unit
                                    }
                                    previousPosition = change.position
                                    previousPointerCount = 1
                                    if (controller.state != ViewerGestureState.TAP_CANDIDATE) {
                                        event.changes.forEach { it.consume() }
                                    }
                                    continue
                                }

                                val released = event.changes.firstOrNull { it.changedToUp() }
                                val upTime = released?.uptimeMillis ?: firstDown.uptimeMillis
                                if (controller.state == ViewerGestureState.TAP_CANDIDATE &&
                                    maximumTravel <= viewConfiguration.touchSlop
                                ) {
                                    val tapPosition = released?.position ?: firstDown.position
                                    if (currentMeasurementMode || currentProbeMode) {
                                        if (handleAtDown == null) handleToolTap(tapPosition)
                                    } else {
                                        val prior = pendingTap
                                        val tapGap = prior?.let { upTime - it.upTimeMillis } ?: Long.MAX_VALUE
                                        val isDoubleTap = prior != null &&
                                            tapGap in viewConfiguration.doubleTapMinTimeMillis..doubleTapTimeout &&
                                            (tapPosition - prior.position).getDistance() <= doubleTapSlop
                                        if (isDoubleTap) {
                                            pendingTapJob?.cancel()
                                            pendingTapJob = null
                                            pendingTap = null
                                            applyDoubleTap(tapPosition)
                                        } else {
                                            if (prior != null) commitPendingTap()
                                            val candidate = PendingViewerTap(tapPosition, upTime)
                                            pendingTap = candidate
                                            pendingTapJob = flingScope.launch {
                                                delay(doubleTapTimeout)
                                                if (pendingTap == candidate) {
                                                    pendingTap = null
                                                    pendingTapJob = null
                                                    handleToolTap(candidate.position)
                                                }
                                            }
                                        }
                                    }
                                } else if (controller.state == ViewerGestureState.SLICE_DRAG) {
                                    traceViewerGesture("drag.end", currentSlice)
                                    val velocityY = tracker.calculateVelocity().y
                                    if (scrollLocked &&
                                        allowsSliceInertia(
                                            currentInertialScrolling,
                                            !ValueAnimator.areAnimatorsEnabled(),
                                        ) && abs(velocityY) > 380f
                                    ) {
                                        val startSlice = dragAccumulator.position
                                        currentScrubbingChanged(true)
                                        flingJob = flingScope.launch {
                                            try {
                                                Animatable(startSlice).animateDecay(
                                                    initialVelocity = (-velocityY / pixelsPerSlice)
                                                        .coerceIn(-90f, 90f),
                                                    animationSpec = exponentialDecay(frictionMultiplier = 3.0f),
                                                ) {
                                                    if (allowsSliceInertia(
                                                            currentInertialScrolling,
                                                            !ValueAnimator.areAnimatorsEnabled(),
                                                        )
                                                    ) {
                                                        currentOnSliceChange(
                                                            value.roundToInt().coerceIn(0, totalSlices - 1),
                                                        )
                                                    } else {
                                                        throw kotlinx.coroutines.CancellationException(
                                                            "Slice inertia disabled",
                                                        )
                                                    }
                                                }
                                            } finally {
                                                currentScrubbingChanged(false)
                                            }
                                        }
                                    } else {
                                        currentScrubbingChanged(false)
                                    }
                                } else {
                                    currentScrubbingChanged(false)
                                }
                                event.changes.forEach { it.consume() }
                                completedNormally = true
                                break
                            }
                        } finally {
                            if (!completedNormally) currentScrubbingChanged(false)
                            traceViewerGesture("pointer.stop", currentSlice)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = if (descriptor.isNativeGrid) {
                        "${descriptor.label} MRI slice"
                    } else "Real ${plane.label} MRI slice",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }.semantics {
                        stateDescription = if (scale > 1.01f) {
                            "Zoomed to ${(scale * 100).roundToInt()} percent. Use zoom controls or pinch to change zoom."
                        } else "Image fitted to view. Use Zoom in or pinch to zoom."
                        customActions = buildList {
                            add(CustomAccessibilityAction("Zoom in") { adjustZoom(1.25f); true })
                            if (scale > 1.01f) {
                                add(CustomAccessibilityAction("Zoom out") { adjustZoom(0.8f); true })
                                add(CustomAccessibilityAction("Fit image") { fitImage(); true })
                                add(CustomAccessibilityAction("Move image left") { panImage(-1, 0); true })
                                add(CustomAccessibilityAction("Move image right") { panImage(1, 0); true })
                                add(CustomAccessibilityAction("Move image up") { panImage(0, -1); true })
                                add(CustomAccessibilityAction("Move image down") { panImage(0, 1); true })
                            }
                        }
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
                        "Rendering ${descriptor.label.lowercase(java.util.Locale.ROOT)} view",
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
            if (scale > 1.01f) {
                TextButton(
                    onClick = ::fitImage,
                    modifier = Modifier.align(Alignment.TopStart).heightIn(min = 48.dp).semantics {
                        contentDescription = "Fit image"
                    },
                ) { Text("Fit", color = NeuroChrome.accent) }
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = { adjustZoom(1.25f) },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom in" },
                ) { Text("+", color = NeuroChrome.accent, fontSize = 24.sp) }
                IconButton(
                    onClick = { adjustZoom(0.8f) },
                    enabled = scale > 1.01f,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom out" },
                ) { Text("−", color = NeuroChrome.accent, fontSize = 24.sp) }
            }
            Text(
                if (scale > 1.01f) "${(scale * 100).roundToInt()}% · pinch to zoom" else "Fit · pinch to zoom",
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).semantics {
                    contentDescription = if (scale > 1.01f) {
                        "Zoom ${(scale * 100).roundToInt()} percent. Pinch to change zoom."
                    } else "Image fitted. Pinch to zoom."
                },
                style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.muted,
            )
            val reference = referenceWorld
            if (probeCrosshairEnabled && reference != null && bitmap != null && imageViewport.width > 0) {
                if (MprProbe.sliceForWorld(volume, descriptor, reference) == currentSlice) {
                    val (fx, fy) = MprProbe.fractionsForWorld(volume, descriptor, reference)
                    val position = viewportTransform(bitmap)?.screenPointForImageFraction(
                        Offset(fx.toFloat(), fy.toFloat()),
                    )
                    if (position != null && position.x in 0f..imageViewport.width.toFloat() &&
                        position.y in 0f..imageViewport.height.toFloat()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val lineColor = NeuroChrome.accent.copy(alpha = 0.65f)
                            drawLine(
                                lineColor,
                                Offset(position.x, 0f),
                                Offset(position.x, size.height),
                                crosshairThicknessDp.dp.toPx(),
                            )
                            drawLine(
                                lineColor,
                                Offset(0f, position.y),
                                Offset(size.width, position.y),
                                crosshairThicknessDp.dp.toPx(),
                            )
                        }
                    }
                }
            }
            MeasurementOverlay(measurementState, bitmap, imageViewport, scale, panOffset)
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
                            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            MeasurementStatusOverlay(
                state = measurementState,
                active = measurementMode,
                snapEnabled = endpointSnapEnabled,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).fillMaxWidth(0.98f),
                onClear = { measurementState = measurementState.clear() },
                onCancel = { setMeasurementMode(false) },
                onFinish = ::finishMeasurement,
                onNudgePoint = ::nudgeMeasurementPoint,
            )
        }

        if (!focusMode) {
            val slicePosition = planeGeometry.slicePositionMm(selectedSlice)
            val positionAxis = descriptor.nearestAnatomicalPlane?.let { nearest -> when (nearest) {
                MriPlane.AXIAL -> "S"
                MriPlane.CORONAL -> "A"
                MriPlane.SAGITTAL -> "R"
            } }
            val positionLabel = when {
                slicePosition == null -> "Units unknown"
                descriptor.isObliqueNative && positionAxis != null ->
                    "≈$positionAxis  %.1f mm".format(java.util.Locale.US, slicePosition)
                positionAxis == null || orientationUnknown -> "%.1f mm".format(java.util.Locale.US, slicePosition)
                else -> "$positionAxis  %.1f mm".format(java.util.Locale.US, slicePosition)
            }
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
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { showInspectMenu = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            contentDescription = "Inspect tools"
                        },
    ) { Text("Inspect", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                    DropdownMenu(
                        expanded = showInspectMenu,
                        onDismissRequest = { showInspectMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (measurementMode) "Stop measuring" else "Measure distance") },
                            onClick = {
                                showInspectMenu = false
                                setMeasurementMode(!measurementMode)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (probeCrosshairEnabled) "Stop probe" else "Probe") },
                            onClick = {
                                showInspectMenu = false
                                setProbeMode(!probeCrosshairEnabled)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Endpoint snapping: ${if (endpointSnapEnabled) "on" else "off"}")
                            },
                            enabled = measurementState.points.isEmpty(),
                            onClick = {
                                endpointSnapEnabled = !endpointSnapEnabled
                                showInspectMenu = false
                            },
                        )
                    }
                }
                TextButton(
                    onClick = { showAdjustments = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        contentDescription = "Display adjustments"
                    },
                ) { Text("Adjust", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                TextButton(
                    onClick = onFocusToggle,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        contentDescription = "Focus mode"
                    },
                ) { Text("Focus", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
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
                        text = { Text("Add note") },
                        onClick = {
                            showMoreActions = false
                            noteText = ""
                            noteSaved = false
                            noteEditorOpen = true
                        },
                    )
                    if (BuildConfig.BRAIN_SUITE_ENABLED) {
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

    if (showUnknownSpatialUnitsDialog) {
        AlertDialog(
            onDismissRequest = { showUnknownSpatialUnitsDialog = false },
            title = { Text("Distance measurement unavailable") },
            text = {
                Text(
                    "This NIfTI file does not specify spatial units, so NeuroView cannot " +
                        "verify a calibrated distance in millimetres.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnknownSpatialUnitsDialog = false }) { Text("OK") }
            },
        )
    }

    if (orientationDetailsOpen) {
        AlertDialog(
            onDismissRequest = { orientationDetailsOpen = false },
            title = { Text("Orientation and sampling") },
            text = {
                Text(
                    orientationDetails,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { orientationDetailsOpen = false }) { Text("Done") }
            },
        )
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
    if (BuildConfig.BRAIN_SUITE_ENABLED && showProcessing) {
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
    if (noteEditorOpen) {
        ModalBottomSheet(
            onDismissRequest = { noteEditorOpen = false },
            containerColor = NeuroChrome.topBar,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add note", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Saved on this device with this series and slice location. The source file is not changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeuroChrome.muted,
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Observation") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                )
                if (noteSaved) Text("Note saved on this device.", color = NeuroChrome.accent)
                Button(
                    onClick = {
                        onSaveNote(
                            plane, selectedSlice, totalSlices,
                            descriptor.stackAxis.takeIf { descriptor.isNativeGrid },
                            !orientationUnknown, noteText,
                        )
                        noteSaved = true
                    },
                    enabled = noteText.isNotBlank() && !noteSaved,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(if (noteSaved) "Saved" else "Save note") }
                TextButton(
                    onClick = { noteEditorOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (noteSaved) "Done" else "Cancel") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
