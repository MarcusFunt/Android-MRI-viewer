package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.BrainPreview
import com.marcusfunt.neuroview.ui.theme.NeuroGradients
import com.marcusfunt.neuroview.ui.theme.neuroGradientBackdrop

@Composable
fun HomeScreen(onView: () -> Unit, onLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().neuroGradientBackdrop()) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "NEUROVIEW",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    modifier = Modifier.background(
                        brush = NeuroGradients.secondary, shape = RoundedCornerShape(50),
                    ),
                ) {
                    Text(
                        "ON-DEVICE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Explore in every plane.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "A focused workspace for your MRI volumes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).background(
                    brush = NeuroGradients.hero,
                    shape = RoundedCornerShape(28.dp),
                ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.5.dp, NeuroGradients.trim),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                BrainPreview(modifier = Modifier.fillMaxSize())
            }
            Card(
                onClick = onView,
                modifier = Modifier.fillMaxWidth().background(
                    brush = NeuroGradients.primary,
                    shape = RoundedCornerShape(20.dp),
                ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("View MRI series", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text("Browse scans and open the viewer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Card(
                onClick = onLibrary,
                modifier = Modifier.fillMaxWidth().background(
                    brush = NeuroGradients.secondary,
                    shape = RoundedCornerShape(20.dp),
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, NeuroGradients.trim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Library & conversion", style = MaterialTheme.typography.titleMedium)
                        Text("Import, organize and convert files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "Your files stay on your device • For exploration, not diagnosis",
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    }
}
