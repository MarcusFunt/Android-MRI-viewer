package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Context
import androidx.compose.ui.unit.sp
import com.marcusfunt.neuroview.ui.BrainPreview
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion

private val Navy = Color(0xFF060F1A)
private val Cyan = Color(0xFF79D6E6)
private val Foreground = Color(0xFFF0F5FA)
private val Secondary = Color(0xFF9EADC2)
private val Edge = Color(0xFF385069)

/**
 * Brain-led welcome screen, intentionally independent from Series, Library
 * and the MRI viewer. Model, lighting and branding are decorative only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBrowse: () -> Unit,
    onImport: () -> Unit,
    lastScanName: String? = null,
    onResumeScan: () -> Unit = {},
    onClearCache: suspend () -> Int = { 0 },
) {
    val context = LocalContext.current
    val motionPreference = LocalNeuroViewMotion.current
    val preferences = remember(context.applicationContext) {
        context.getSharedPreferences("neuroview_home_appearance_v1", Context.MODE_PRIVATE)
    }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var autoRotate by rememberSaveable {
        mutableStateOf(preferences.getBoolean("rotate", true))
    }
    var glowEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean("glow", true))
    }
    androidx.compose.runtime.DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "rotate" -> autoRotate = prefs.getBoolean("rotate", true)
                "glow" -> glowEnabled = prefs.getBoolean("glow", true)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var resetRequest by rememberSaveable { mutableIntStateOf(0) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF05101B),
                    Color(0xFF0A1D2F),
                    Color(0xFF0B1D30),
                    Color(0xFF050E18),
                ),
            ),
        ),
    ) {
        if (glowEnabled) {
            // Broad cinematic softboxes, drawn behind the whole page, not
            // clipped to the OpenGL viewport. No orbit lines or oval borders.
            Canvas(Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFF458AB3).copy(alpha = 0.20f),
                        0.40f to Color(0xFF2B5E83).copy(alpha = 0.11f),
                        0.77f to Color(0xFF1E4569).copy(alpha = 0.03f),
                        1.0f to Color.Transparent,
                        center = Offset(width * 0.56f, height * 0.37f),
                        radius = maxOf(width * 0.89f, height * 0.42f),
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFF66A7CA).copy(alpha = 0.10f),
                        0.55f to Color(0xFF295276).copy(alpha = 0.035f),
                        1.0f to Color.Transparent,
                        center = Offset(width * 0.16f, height * 0.27f),
                        radius = maxOf(width * 0.58f, height * 0.33f),
                    ),
                )
                // A fully feathered hint of reflection, not an ellipse:
                // the light extends naturally into the navy surroundings.
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFF6EBAE3).copy(alpha = 0.10f),
                        0.49f to Color(0xFF3276A4).copy(alpha = 0.045f),
                        1.0f to Color.Transparent,
                        center = Offset(width * 0.5f, height * 0.64f),
                        radius = maxOf(width * 0.76f, height * 0.25f),
                    ),
                )
            }
        }
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(65.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeuroMark(Modifier.size(45.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "NeuroView",
                                fontSize = 29.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF1F1F1),
                                letterSpacing = (-0.5).sp,
                            )
                            Text(
                                "MRI exploration workspace",
                                fontSize = 12.sp,
                                letterSpacing = 0.1.sp,
                                color = Color(0xFF9E9E9E),
                                maxLines = 1,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(0xFF13253A))
                            .border(1.dp, Edge.copy(alpha = 0.55f), RoundedCornerShape(19.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFFC8E4F2),
                            )
                        }
                    }
                }

                // The translucent GLES viewport is intentionally unframed;
                // no card, border, or explanatory labels sit around the brain.
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 180.dp),
                ) {
                    BrainPreview(
                        modifier = Modifier.fillMaxSize(),
                        autoRotation = autoRotate && motionPreference.allowsDecorativeMotion,
                        resetRequest = resetRequest,
                    )
                }

                // Keep GLSurfaceView in the fixed, non-scrolling composition.
                // Only action controls scroll on compact displays.
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                if (!lastScanName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF11283A))
                            .border(1.dp, Edge.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button, onClick = onResumeScan)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continue viewing", color = Secondary, fontSize = 12.sp)
                            Text(lastScanName, color = Foreground, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Resume most recently opened scan", tint = Cyan)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val primaryShape = RoundedCornerShape(19.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                        .clip(primaryShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF12547C), Color(0xFF1B5F9B)),
                            ),
                        )
                        .clickable(role = Role.Button, onClick = onBrowse)
                        .padding(horizontal = 21.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(24.dp))
                    Text(
                        "Browse scans",
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.84f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(57.dp)
                        .clip(primaryShape)
                        .background(Color(0xFF111F2E))
                        .border(1.dp, Color(0xFF7796B2).copy(alpha = 0.73f), primaryShape)
                        .clickable(role = Role.Button, onClick = onImport)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(24.dp))
                    Text(
                        "Import MRI",
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Foreground,
                        textAlign = TextAlign.Center,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Secondary,
                    )
                }
                Text(
                    "Local viewing · For exploration, not diagnosis",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 7.dp),
                    fontSize = 12.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = Secondary.copy(alpha = 0.8f),
                )
                }
            }
        }
    }

    if (settingsOpen) {
        NeuroViewSettingsSheet(
            autoRotate = autoRotate,
            onAutoRotateChange = { enabled ->
                autoRotate = enabled
                preferences.edit().putBoolean("rotate", enabled).apply()
            },
            glowEnabled = glowEnabled,
            onGlowEnabledChange = { enabled ->
                glowEnabled = enabled
                preferences.edit().putBoolean("glow", enabled).apply()
            },
            onResetBrainOrientation = { resetRequest++ },
            onClearCache = onClearCache,
            scrollToViewerSettings = false,
            onDismiss = { settingsOpen = false },
        )
    }
}

@Composable
private fun DetailPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(37.dp).clip(RoundedCornerShape(50))
            .background(Color(0xFF142539).copy(alpha = 0.7f))
            .border(1.dp, Edge.copy(alpha = 0.52f), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = Color(0xFFBED5E6),
            maxLines = 1,
        )
    }
}

/** Reuses the app's recognizable brain mark in the home-screen wordmark. */
@Composable
private fun NeuroMark(modifier: Modifier) {
    Image(
        painter = painterResource(com.marcusfunt.neuroview.R.drawable.neuroview_logo),
        contentDescription = "NeuroView brain logo",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
