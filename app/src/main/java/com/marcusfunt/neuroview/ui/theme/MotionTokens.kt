package com.marcusfunt.neuroview.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class MotionPreference(val label: String, val description: String) {
    SYSTEM("System default", "Use Android's animation setting."),
    REDUCED("Reduced", "Use shorter transitions for NeuroView interface changes."),
    OFF("Off", "Turn off nonessential NeuroView transitions.");

    val allowsDecorativeMotion: Boolean
        get() = this == SYSTEM

    val allowsInertialMotion: Boolean
        get() = this == SYSTEM
}

enum class MotionToken(private val systemDuration: Int) {
    INSTANT(60),
    QUICK(130),
    STANDARD(180),
    NAVIGATION(250),
    CELEBRATION(320);

    fun durationMs(preference: MotionPreference): Int = when (preference) {
        MotionPreference.SYSTEM -> systemDuration
        MotionPreference.REDUCED -> systemDuration / 2
        MotionPreference.OFF -> 0
    }
}

internal const val MOTION_PREFERENCES = "neuroview_motion_v1"
internal const val MOTION_PREFERENCE_KEY = "motion_preference"

val LocalNeuroViewMotion = compositionLocalOf { MotionPreference.SYSTEM }

fun SharedPreferences.readMotionPreference(): MotionPreference = runCatching {
    MotionPreference.valueOf(getString(MOTION_PREFERENCE_KEY, MotionPreference.SYSTEM.name)
        ?: MotionPreference.SYSTEM.name)
}.getOrDefault(MotionPreference.SYSTEM)

fun SharedPreferences.writeMotionPreference(preference: MotionPreference) {
    edit().putString(MOTION_PREFERENCE_KEY, preference.name).apply()
}

@Composable
fun rememberMotionPreference(): MotionPreference {
    val preferences = rememberMotionPreferences()
    var preference by remember(preferences) {
        mutableStateOf(preferences.readMotionPreference())
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == MOTION_PREFERENCE_KEY) preference = prefs.readMotionPreference()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return preference
}

@Composable
private fun rememberMotionPreferences(): SharedPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        context.getSharedPreferences(MOTION_PREFERENCES, Context.MODE_PRIVATE)
    }
}
