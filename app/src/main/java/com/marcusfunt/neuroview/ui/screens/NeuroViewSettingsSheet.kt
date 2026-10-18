package com.marcusfunt.neuroview.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
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

internal const val VIEWER_DISPLAY_PREFERENCES = "neuroview_display_v1"
internal const val SHOW_ORIENTATION_MARKERS_KEY = "show_orientation_markers"
internal const val PROBE_CROSSHAIR_ENABLED_KEY = "probe_crosshair_enabled"
internal const val CROSSHAIR_THICKNESS_DP_KEY = "crosshair_thickness_dp"
internal const val DEFAULT_CROSSHAIR_THICKNESS_DP = 3f
internal const val MIN_CROSSHAIR_THICKNESS_DP = 1f
internal const val MAX_CROSSHAIR_THICKNESS_DP = 4f

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
) {
    val context = LocalContext.current
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
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val viewerSectionRequester = remember { BringIntoViewRequester() }
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.86f).dp

    DisposableEffect(viewerPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            when (key) {
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
            Text(
                "MRI viewer",
                modifier = Modifier.bringIntoViewRequester(viewerSectionRequester),
                style = MaterialTheme.typography.titleMedium,
                color = SettingsForeground,
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

            HorizontalDivider(color = SettingsSecondary.copy(alpha = 0.28f))
            Text("Privacy & storage", style = MaterialTheme.typography.titleMedium, color = SettingsForeground)
            Text(
                "Scanning, viewing and format conversion run locally. Optional BrainSuite processing only uploads after your explicit consent.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsSecondary,
            )
            TextButton(onClick = { scope.launch {
                cacheStatus = try {
                    "Cleared ${onClearCache()} generated MRI previews. Original files unchanged."
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
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SettingsForeground)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = SettingsSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatThickness(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
