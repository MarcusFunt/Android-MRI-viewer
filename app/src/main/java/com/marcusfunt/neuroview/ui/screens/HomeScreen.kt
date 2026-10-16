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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcusfunt.neuroview.ui.BrainPreview

private val Navy = Color(0xFF060F1A)
private val Cyan = Color(0xFF54D9EE)
private val Foreground = Color(0xFFF0F5FA)
private val Secondary = Color(0xFF9EADC2)
private val Edge = Color(0xFF385069)

/**
 * Brain-led welcome screen, intentionally independent from Series, Library
 * and the MRI viewer. Model, lighting and branding are decorative only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onView: () -> Unit, onLibrary: () -> Unit) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var autoRotate by rememberSaveable { mutableStateOf(true) }
    var glowEnabled by rememberSaveable { mutableStateOf(true) }
    var resetRequest by rememberSaveable { mutableIntStateOf(0) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Navy, Color(0xFF0A1B2C), Color(0xFF081423), Navy),
            ),
        ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding)
                    .padding(horizontal = 21.dp, vertical = 12.dp),
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
                                "EXPLORE  ·  VISUALIZE  ·  UNDERSTAND",
                                fontSize = 9.sp,
                                letterSpacing = 1.1.sp,
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
                                contentDescription = "Landing page settings",
                                tint = Color(0xFFC8E4F2),
                            )
                        }
                    }
                }

                // The translucent GLES viewport is intentionally unframed;
                // no card, border, or explanatory labels sit around the brain.
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.49f
                        if (glowEnabled) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        Color(0xFF276E9D).copy(alpha = 0.38f),
                                        Color(0xFF13405E).copy(alpha = 0.23f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(cx, cy),
                                    radius = size.width * 0.73f,
                                ),
                                radius = size.width * 0.73f,
                                center = Offset(cx, cy),
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFF6CC6EA).copy(alpha = 0.13f), Color.Transparent),
                                    center = Offset(cx, size.height * 0.89f),
                                    radius = size.width * 0.55f,
                                ),
                                radius = size.width * 0.55f,
                                center = Offset(cx, size.height * 0.89f),
                            )
                        }
                        // Quiet orbital guides; low enough opacity to stay background.
                        for (ratio in listOf(0.78f, 0.92f)) {
                            drawCircle(
                                color = Color(0xFF82B7CF).copy(alpha = 0.075f),
                                radius = size.width * ratio * 0.5f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                    }
                    BrainPreview(
                        modifier = Modifier.fillMaxSize(),
                        autoRotation = autoRotate,
                        resetRequest = resetRequest,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DetailPill("3D visualization", Modifier.weight(1.14f))
                    DetailPill("NIfTI / DICOM", Modifier.weight(1.04f))
                    DetailPill("On-device", Modifier.weight(0.91f))
                }
                Spacer(Modifier.height(6.dp))
                val primaryShape = RoundedCornerShape(19.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                        .clip(primaryShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0BBCE2), Color(0xFF1870E8)),
                            ),
                        )
                        .clickable(role = Role.Button, onClick = onView)
                        .padding(horizontal = 21.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Open viewer",
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
                        .clickable(role = Role.Button, onClick = onLibrary)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Import & Library",
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
                    "ON-DEVICE  ·  FOR EXPLORATION, NOT DIAGNOSIS",
                    modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 7.dp),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text(
                    "Landing settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Foreground,
                )
                Text(
                    "Customize the home preview. Your MRI scans are unaffected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Secondary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Automatically rotate brain", modifier = Modifier.weight(1f), color = Foreground)
                    Switch(checked = autoRotate, onCheckedChange = { autoRotate = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ambient background glow", modifier = Modifier.weight(1f), color = Foreground)
                    Switch(checked = glowEnabled, onCheckedChange = { glowEnabled = it })
                }
                TextButton(onClick = { resetRequest++ }) {
                    Text("Reset brain orientation", color = Cyan)
                }
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
