package com.marcusfunt.neuroview.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.marcusfunt.neuroview.mri.RenderQualityMode
import com.marcusfunt.neuroview.mri.RenderQualityPolicy
import com.marcusfunt.neuroview.mri.RenderQualitySettings
import com.marcusfunt.neuroview.mri.RenderPerformanceSnapshot
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MotionPreference
import com.marcusfunt.neuroview.ui.theme.MOTION_PREFERENCES
import com.marcusfunt.neuroview.ui.theme.writeMotionPreference
import kotlin.math.roundToInt

internal const val VIEWER_DISPLAY_PREFERENCES = "neuroview_display_v1"
internal const val SHOW_ORIENTATION_MARKERS_KEY = "show_orientation_markers"
internal const val PROBE_CROSSHAIR_ENABLED_KEY = "probe_crosshair_enabled"
internal const val CROSSHAIR_THICKNESS_DP_KEY = "crosshair_thickness_dp"
internal const val DEFAULT_CROSSHAIR_THICKNESS_DP = 3f
internal const val MIN_CROSSHAIR_THICKNESS_DP = 1f
internal const val MAX_CROSSHAIR_THICKNESS_DP = 4f
internal const val RENDER_QUALITY_MODE_KEY = "render_quality_mode"
internal const val ADAPTIVE_RENDERING_KEY = "adaptive_rendering"
internal const val PREVIEW_MAX_SIDE_KEY = "render_preview_max_side"
internal const val STILL_MAX_SIDE_KEY = "render_still_max_side"
internal const val RENDER_REFINE_DELAY_KEY = "render_refine_delay_ms"
internal const val SHOW_SLICE_BUTTONS_KEY = "show_slice_buttons"
internal const val INERTIAL_SCROLLING_KEY = "inertial_slice_scrolling"
internal const val IMMERSIVE_FOCUS_KEY = "immersive_focus"

internal fun SharedPreferences.readRenderQualitySettings(): RenderQualitySettings {
    val mode = runCatching {
        RenderQualityMode.valueOf(getString(RENDER_QUALITY_MODE_KEY, null) ?: "AUTO")
    }.getOrDefault(RenderQualityMode.AUTO)
    return RenderQualitySettings(
        mode = mode,
        adaptive = getBoolean(ADAPTIVE_RENDERING_KEY, true),
        previewMaxSide = getInt(PREVIEW_MAX_SIDE_KEY, 160),
        stillMaxSide = getInt(STILL_MAX_SIDE_KEY, 384),
        refineDelayMs = getInt(RENDER_REFINE_DELAY_KEY, 72).toLong(),
    ).validated()
}

internal fun SharedPreferences.writeRenderQualitySettings(settings: RenderQualitySettings) {
    val validated = settings.validated()
    edit()
        .putString(RENDER_QUALITY_MODE_KEY, validated.mode.name)
        .putBoolean(ADAPTIVE_RENDERING_KEY, validated.adaptive)
        .putInt(PREVIEW_MAX_SIDE_KEY, validated.previewMaxSide)
        .putInt(STILL_MAX_SIDE_KEY, validated.stillMaxSide)
        .putInt(RENDER_REFINE_DELAY_KEY, validated.refineDelayMs.toInt())
        .apply()
}

private val SettingsForeground = androidx.compose.ui.graphics.Color(0xFFF0F5FA)
private val SettingsSecondary = androidx.compose.ui.graphics.Color(0xFF9EADC2)
private val SettingsCyan = androidx.compose.ui.graphics.Color(0xFF79D6E6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeuroViewSettingsSheet(
    autoRotate: Boolean,
    onAutoRotateChange: (Boolean) -> Unit,
    glowEnabled: Boolean,
    onGlowEnabledChange: (Boolean) -> Unit,
    onResetBrainOrientation: (() -> Unit)?,
    onClearCache: suspend () -> Int,
    scrollToViewerSettings: Boolean,
    onDismiss: () -> Unit,
    renderPerformance: RenderPerformanceSnapshot? = null,
    memorySafeMaximumSidePx: Int = RenderQualityPolicy.memorySafeMaximumSide(Runtime.getRuntime().maxMemory()),
) {
    val context = LocalContext.current
    val showRenderDiagnostics = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val motionPreferences = remember(context.applicationContext) {
        context.getSharedPreferences(MOTION_PREFERENCES, Context.MODE_PRIVATE)
    }
    val motionPreference = LocalNeuroViewMotion.current
    val viewerPreferences = remember(context.applicationContext) {
        context.getSharedPreferences(VIEWER_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
    }
    var showOrientationMarkers by remember(viewerPreferences) {
        mutableStateOf(viewerPreferences.getBoolean(SHOW_ORIENTATION_MARKERS_KEY, false))
    }
    var probeCrosshairEnabled by remember(viewerPreferences) {
        mutableStateOf(viewerPreferences.getBoolean(PROBE_CROSSHAIR_ENABLED_KEY, false))
    }
    var crosshairThicknessDp by remember(viewerPreferences) {
        mutableStateOf(
            viewerPreferences.getFloat(
                CROSSHAIR_THICKNESS_DP_KEY,
                DEFAULT_CROSSHAIR_THICKNESS_DP,
            ).coerceIn(MIN_CROSSHAIR_THICKNESS_DP, MAX_CROSSHAIR_THICKNESS_DP),
        )
    }
    var renderQualitySettings by remember(viewerPreferences) {
        mutableStateOf(viewerPreferences.readRenderQualitySettings())
    }
    var showSliceButtons by remember(viewerPreferences) { mutableStateOf(viewerPreferences.getBoolean(SHOW_SLICE_BUTTONS_KEY, false)) }
    var inertialScrolling by remember(viewerPreferences) { mutableStateOf(viewerPreferences.getBoolean(INERTIAL_SCROLLING_KEY, true)) }
    var immersiveFocus by remember(viewerPreferences) { mutableStateOf(viewerPreferences.getBoolean(IMMERSIVE_FOCUS_KEY, false)) }
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val viewerSectionRequester = remember { BringIntoViewRequester() }
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.86f).dp

    DisposableEffect(viewerPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            when (key) {
                SHOW_SLICE_BUTTONS_KEY -> showSliceButtons = preferences.getBoolean(SHOW_SLICE_BUTTONS_KEY, false)
                INERTIAL_SCROLLING_KEY -> inertialScrolling = preferences.getBoolean(INERTIAL_SCROLLING_KEY, true)
                IMMERSIVE_FOCUS_KEY -> immersiveFocus = preferences.getBoolean(IMMERSIVE_FOCUS_KEY, false)
                SHOW_ORIENTATION_MARKERS_KEY -> {
                    showOrientationMarkers = preferences.getBoolean(SHOW_ORIENTATION_MARKERS_KEY, false)
                }
                PROBE_CROSSHAIR_ENABLED_KEY -> {
                    probeCrosshairEnabled = preferences.getBoolean(PROBE_CROSSHAIR_ENABLED_KEY, false)
                }
                CROSSHAIR_THICKNESS_DP_KEY -> {
                    crosshairThicknessDp = preferences.getFloat(
                        CROSSHAIR_THICKNESS_DP_KEY,
                        DEFAULT_CROSSHAIR_THICKNESS_DP,
                    ).coerceIn(MIN_CROSSHAIR_THICKNESS_DP, MAX_CROSSHAIR_THICKNESS_DP)
                }
                RENDER_QUALITY_MODE_KEY,
                ADAPTIVE_RENDERING_KEY,
                PREVIEW_MAX_SIDE_KEY,
                STILL_MAX_SIDE_KEY,
                RENDER_REFINE_DELAY_KEY -> {
                    renderQualitySettings = preferences.readRenderQualitySettings()
                }
            }
        }
        viewerPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { viewerPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(scrollToViewerSettings) {
        if (scrollToViewerSettings) {
            // Let the modal sheet finish its first layout before requesting the section.
            delay(180)
            viewerSectionRequester.bringIntoView()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF101F30),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = SettingsForeground)
            Text(
                "Appearance, viewer behavior, privacy and local storage. These settings do not modify your MRI files.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsSecondary,
            )

            Text("Home appearance", style = MaterialTheme.typography.titleMedium, color = SettingsForeground)
            SettingsSwitchRow(
                title = "Automatically rotate brain",
                checked = autoRotate,
                onCheckedChange = onAutoRotateChange,
            )
            SettingsSwitchRow(
                title = "Ambient background glow",
                checked = glowEnabled,
                onCheckedChange = onGlowEnabledChange,
            )
            onResetBrainOrientation?.let { reset ->
                TextButton(onClick = reset) {
                    Text("Reset brain orientation", color = SettingsCyan)
                }
            }

            HorizontalDivider(color = SettingsSecondary.copy(alpha = 0.28f))
            Text("Motion and accessibility", style = MaterialTheme.typography.titleMedium, color = SettingsForeground)
            SettingsChoiceRow(
                title = "Motion",
                selected = motionPreference.label,
                options = MotionPreference.entries.map { it.label to it },
                onSelected = motionPreferences::writeMotionPreference,
            )
            Text(
                motionPreference.description,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsSecondary,
            )

            HorizontalDivider(color = SettingsSecondary.copy(alpha = 0.28f))
            Text(
                "MRI viewer",
                modifier = Modifier.bringIntoViewRequester(viewerSectionRequester),
                style = MaterialTheme.typography.titleMedium,
                color = SettingsForeground,
            )
            Text("Navigation", style = MaterialTheme.typography.titleSmall, color = SettingsForeground)
            SettingsSwitchRow(
                title = "Show previous/next slice buttons",
                checked = showSliceButtons,
                onCheckedChange = { enabled ->
                    showSliceButtons = enabled
                    viewerPreferences.edit().putBoolean(SHOW_SLICE_BUTTONS_KEY, enabled).apply()
                },
            )
            SettingsSwitchRow(
                title = "Inertial slice scrolling",
                detail = "Continue a fast image swipe after release. Independent of Motion; disabled when Android animations are off. Drag and slider distance always stay the same.",
                checked = inertialScrolling,
                onCheckedChange = { enabled ->
                    inertialScrolling = enabled
                    viewerPreferences.edit().putBoolean(INERTIAL_SCROLLING_KEY, enabled).apply()
                },
            )
            Text("Hold the slice thumb and move upward for finer horizontal adjustment.",
                style = MaterialTheme.typography.bodySmall, color = SettingsSecondary)

            HorizontalDivider(color = SettingsSecondary.copy(alpha = 0.28f))
            Text("Quality", style = MaterialTheme.typography.titleSmall, color = SettingsForeground)
            SettingsChoiceRow(
                title = "Rendering priority",
                selected = renderQualitySettings.mode.label,
                options = RenderQualityMode.entries.map { it.label to it },
                onSelected = { mode ->
                    viewerPreferences.writeRenderQualitySettings(renderQualitySettings.copy(mode = mode))
                },
            )
            SettingsSwitchRow(
                title = "Adaptive rendering",
                detail = "Adjust quality using render timing, memory, battery saver and thermal state.",
                checked = renderQualitySettings.adaptive,
                onCheckedChange = { adaptive ->
                    viewerPreferences.writeRenderQualitySettings(
                        renderQualitySettings.copy(adaptive = adaptive),
                    )
                },
            )
            if (!renderQualitySettings.adaptive) {
                Text(
                    "With adaptive rendering off, large scans may stutter on slower phones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsSecondary,
                )
            }
            if (renderQualitySettings.mode == RenderQualityMode.MANUAL) {
                ManualRenderSettings(
                    settings = renderQualitySettings,
                    onSettingsPreview = { renderQualitySettings = it },
                    onSettingsCommitted = viewerPreferences::writeRenderQualitySettings,
                )
            }
            Text(
                "Selected sizes are maximum image sides. The current memory guard allows still frames up to $memorySafeMaximumSidePx px.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsSecondary,
            )
            HorizontalDivider(color = SettingsSecondary.copy(alpha = 0.28f))
            Text("Display", style = MaterialTheme.typography.titleSmall, color = SettingsForeground)
            SettingsSwitchRow(
                title = "Immersive Focus",
                detail = "Hide system bars in Focus. Swipe from an edge to reveal them; Exit focus and Back remain available.",
                checked = immersiveFocus,
                onCheckedChange = { enabled ->
                    immersiveFocus = enabled
                    viewerPreferences.edit().putBoolean(IMMERSIVE_FOCUS_KEY, enabled).apply()
                },
            )
            SettingsSwitchRow(
                title = "Orientation markers",
                detail = "Show R/L and A/P labels around MRI slices.",
                checked = showOrientationMarkers,
                onCheckedChange = { enabled ->
                    showOrientationMarkers = enabled
                    viewerPreferences.edit().putBoolean(SHOW_ORIENTATION_MARKERS_KEY, enabled).apply()
                },
            )
            SettingsSwitchRow(
                title = "Probe Crosshair",
                detail = "Tap a slice to show voxel coordinates and intensity at that point.",
                checked = probeCrosshairEnabled,
                onCheckedChange = { enabled ->
                    probeCrosshairEnabled = enabled
                    viewerPreferences.edit().putBoolean(PROBE_CROSSHAIR_ENABLED_KEY, enabled).apply()
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Crosshair thickness", color = SettingsForeground)
                    Text("${formatThickness(crosshairThicknessDp)} dp", color = SettingsSecondary, fontSize = 13.sp)
                }
                Text(
                    "Adjust the probe crosshair line weight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsSecondary,
                )
                Slider(
                    value = crosshairThicknessDp,
                    onValueChange = { value ->
                        crosshairThicknessDp = value
                        viewerPreferences.edit().putFloat(CROSSHAIR_THICKNESS_DP_KEY, value).apply()
                    },
                    valueRange = MIN_CROSSHAIR_THICKNESS_DP..MAX_CROSSHAIR_THICKNESS_DP,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Crosshair thickness"
                        stateDescription = "${formatThickness(crosshairThicknessDp)} dp"
                    },
                )
            }
            if (showRenderDiagnostics && renderPerformance != null && renderPerformance.sampleCount > 0) {
                Text("Diagnostics", style = MaterialTheme.typography.titleSmall, color = SettingsForeground)
                Text(
                    "Local render diagnostics · ${renderPerformance.displayedFrameCount} displayed frames · " +
                        "gesture→Compose p50/p95/p99 " +
                        "${renderPerformance.gestureToCommitP50Ms ?: 0}/" +
                        "${renderPerformance.gestureToCommitP95Ms ?: 0}/" +
                        "${renderPerformance.gestureToCommitP99Ms ?: 0} ms · " +
                        "worker p50/p95/p99 ${renderPerformance.p50Ms ?: 0}/" +
                        "${renderPerformance.p95Ms ?: 0}/${renderPerformance.p99Ms ?: 0} ms · " +
                        "${renderPerformance.supersededPercent}% superseded · " +
                        "peak heap ${renderPerformance.peakHeapUsedBytes / (1024L * 1024L)} MiB · " +
                        "max observed heap increase " +
                        "${renderPerformance.maxObservedHeapIncreaseBytes / (1024L * 1024L)} MiB",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsSecondary,
                )
            }

            HorizontalDivider(color = SettingsSecondary.copy(alpha = 0.28f))
            Text("Privacy & storage", style = MaterialTheme.typography.titleMedium, color = SettingsForeground)
            Text(
                "Scanning, viewing and format conversion run locally. Optional BrainSuite processing only uploads after your explicit consent.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsSecondary,
            )
            TextButton(onClick = { scope.launch {
                cacheStatus = try {
                    context.resources.catalogQuantityText(CatalogQuantity.PREVIEWS_CLEARED, onClearCache()) +
                        ". Original files unchanged."
                } catch (error: Exception) {
                    "Could not clear previews: ${error.message ?: "Unknown error"}"
                }
            } }) {
                Text("Clear cached MRI previews", color = SettingsCyan)
            }
            cacheStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SettingsSecondary) }
            Text(
                "NeuroView is for exploration and research, not medical diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsSecondary,
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Done")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    selected: String,
    options: List<Pair<String, T>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = SettingsForeground)
        Box {
            TextButton(onClick = { expanded = true }) { Text(selected, color = SettingsCyan) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                containerColor = NeuroChrome.topBar,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
            ) {
                options.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .background(if (label == selected) NeuroChrome.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                            .semantics { this.selected = label == selected },
                        colors = MenuDefaults.itemColors(
                            textColor = if (label == selected) SettingsCyan else SettingsForeground,
                        ),
                        onClick = {
                            expanded = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualRenderSettings(
    settings: RenderQualitySettings,
    onSettingsPreview: (RenderQualitySettings) -> Unit,
    onSettingsCommitted: (RenderQualitySettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Scrolling preview · ${settings.previewMaxSide} px", color = SettingsForeground)
        Slider(
            value = settings.previewMaxSide.toFloat(),
            onValueChange = { value ->
                onSettingsPreview(settings.copy(previewMaxSide = value.roundToInt()).validated())
            },
            onValueChangeFinished = { onSettingsCommitted(settings) },
            valueRange = RenderQualitySettings.MIN_PREVIEW_SIDE.toFloat()..
                RenderQualitySettings.MAX_PREVIEW_SIDE.toFloat(),
            steps = 6,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Scrolling preview maximum side"
                stateDescription = "${settings.previewMaxSide} pixels"
            },
        )
        Text("Still image · ${settings.stillMaxSide} px", color = SettingsForeground)
        Slider(
            value = settings.stillMaxSide.toFloat(),
            onValueChange = { value ->
                onSettingsPreview(settings.copy(stillMaxSide = value.roundToInt()).validated())
            },
            onValueChangeFinished = { onSettingsCommitted(settings) },
            valueRange = RenderQualitySettings.MIN_STILL_SIDE.toFloat()..
                RenderQualitySettings.MAX_STILL_SIDE.toFloat(),
            steps = 11,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Still image maximum side"
                stateDescription = "${settings.stillMaxSide} pixels"
            },
        )
        Text("Refine after scrolling · ${settings.refineDelayMs} ms", color = SettingsForeground)
        Slider(
            value = settings.refineDelayMs.toFloat(),
            onValueChange = { value ->
                onSettingsPreview(settings.copy(refineDelayMs = value.roundToInt().toLong()).validated())
            },
            onValueChangeFinished = { onSettingsCommitted(settings) },
            valueRange = RenderQualitySettings.MIN_REFINE_DELAY_MS.toFloat()..
                RenderQualitySettings.MAX_REFINE_DELAY_MS.toFloat(),
            steps = 7,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Still image refine delay"
                stateDescription = "${settings.refineDelayMs} milliseconds"
            },
        )
        Text(
            "Manual values save when you finish adjusting. Adaptive rendering may still lower resolution when required to keep memory use safe.",
            style = MaterialTheme.typography.bodySmall,
            color = SettingsSecondary,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SettingsForeground)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = SettingsSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun formatThickness(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
