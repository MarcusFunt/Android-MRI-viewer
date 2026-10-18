package com.marcusfunt.neuroview.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.marcusfunt.neuroview.ui.BrainPreview

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
    val preferences = remember(context.applicationContext) {
        context.getSharedPreferences("neuroview_home_appearance_v1", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var autoRotate by rememberSaveable {
        mutableStateOf(preferences.getBoolean("rotate", true))
    }
    var glowEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean("glow", true))
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
                    .verticalScroll(rememberScrollState())
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
                                buildAnnotatedString {
                                    append("Neuro")
                                    withStyle(SpanStyle(color = Cyan)) { append("View") }
                                },
                                fontSize = 29.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Foreground,
                                letterSpacing = (-0.5).sp,
                            )
                            Text(
                                "MRI exploration workspace",
                                fontSize = 12.sp,
                                letterSpacing = 0.1.sp,
                                color = Secondary,
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
                                contentDescription = "Home appearance settings",
                                tint = Color(0xFFC8E4F2),
                            )
                        }
                    }
                }

                // The translucent GLES viewport is intentionally unframed;
                // no card, border, or explanatory labels sit around the brain.
                Box(
                    modifier = Modifier.fillMaxWidth().height(
                        (LocalConfiguration.current.screenHeightDp.dp * 0.42f).coerceIn(220.dp, 390.dp),
                    ),
                ) {
                    BrainPreview(
                        modifier = Modifier.fillMaxSize(),
                        autoRotation = autoRotate,
                        resetRequest = resetRequest,
                    )
                }

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

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            containerColor = Color(0xFF101F30),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Foreground,
                )
                Text(
                    "Appearance, privacy and local storage. These settings do not modify your MRI files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Secondary,
                )
                Text("Home appearance", style = MaterialTheme.typography.titleMedium, color = Foreground)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Automatically rotate brain", modifier = Modifier.weight(1f), color = Foreground)
                    Switch(checked = autoRotate, onCheckedChange = { autoRotate = it; preferences.edit().putBoolean("rotate", it).apply() })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ambient background glow", modifier = Modifier.weight(1f), color = Foreground)
                    Switch(checked = glowEnabled, onCheckedChange = { glowEnabled = it; preferences.edit().putBoolean("glow", it).apply() })
                }
                TextButton(onClick = { resetRequest++ }) {
                    Text("Reset brain orientation", color = Cyan)
                }
                Text("Privacy & storage", style = MaterialTheme.typography.titleMedium, color = Foreground)
                Text(
                    "Scanning, viewing and format conversion run locally. " +
                        "Optional BrainSuite processing only uploads after your explicit consent.",
                    style = MaterialTheme.typography.bodySmall, color = Secondary,
                )
                TextButton(onClick = {
                    scope.launch {
                        cacheStatus = try {
                            "Cleared ${onClearCache()} generated MRI previews. Original files unchanged."
                        } catch (error: Exception) {
                            "Could not clear previews: ${error.message ?: "Unknown error"}"
                        }
                    }
                }) {
                    Text("Clear cached MRI previews", color = Cyan)
                }
                cacheStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Secondary)
                }
                Text(
                    "NeuroView is for exploration and research, not medical diagnosis.",
                    style = MaterialTheme.typography.bodySmall, color = Secondary,
                )
                Button(
                    onClick = { settingsOpen = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Done") }
                Spacer(Modifier.height(10.dp))
            }
        }
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

/** Small vector-style mark, drawn at device resolution. No external image asset. */
@Composable
private fun NeuroMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val left = Path().apply {
            moveTo(w * .50f, h * .17f)
            cubicTo(w * .29f, h * .03f, w * .20f, h * .21f, w * .23f, h * .31f)
            cubicTo(w * .06f, h * .31f, w * .05f, h * .53f, w * .19f, h * .60f)
            cubicTo(w * .07f, h * .78f, w * .28f, h * .93f, w * .43f, h * .83f)
            cubicTo(w * .49f, h * .90f, w * .53f, h * .82f, w * .50f, h * .70f)
        }
        val right = Path().apply {
            moveTo(w * .50f, h * .17f)
            cubicTo(w * .72f, h * .05f, w * .81f, h * .23f, w * .77f, h * .33f)
            cubicTo(w * .95f, h * .36f, w * .95f, h * .57f, w * .81f, h * .63f)
            cubicTo(w * .93f, h * .80f, w * .70f, h * .94f, w * .56f, h * .83f)
            cubicTo(w * .51f, h * .91f, w * .48f, h * .79f, w * .50f, h * .70f)
        }
        val sw = 2.2.dp.toPx()
        drawPath(left, Cyan, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(right, Cyan, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(Cyan, Offset(w * .50f, h * .19f), Offset(w * .50f, h * .72f), sw)
        val fold = Path().apply {
            moveTo(w * .31f, h * .28f)
            cubicTo(w * .17f, h * .42f, w * .48f, h * .36f, w * .35f, h * .53f)
            moveTo(w * .69f, h * .27f)
            cubicTo(w * .85f, h * .44f, w * .54f, h * .40f, w * .67f, h * .55f)
            moveTo(w * .25f, h * .67f)
            cubicTo(w * .38f, h * .59f, w * .43f, h * .67f, w * .44f, h * .76f)
            moveTo(w * .75f, h * .67f)
            cubicTo(w * .63f, h * .58f, w * .56f, h * .67f, w * .56f, h * .77f)
        }
        drawPath(fold, Cyan, style = Stroke(sw * .82f, cap = StrokeCap.Round))
    }
}
