package com.marcusfunt.neuroview.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.navigation.Destination
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(seriesId: String, onNavigate: (Destination) -> Unit) {
    val demo = remember(seriesId) { seriesById(seriesId) }
    var plane by rememberSaveable(seriesId) {
        mutableStateOf(demo?.defaultPlane ?: ViewPlane.AXIAL)
    }
    var planePanelOpen by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun navigateFromMenu(destination: Destination) {
        scope.launch {
            drawerState.close()
            onNavigate(destination)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Text("NEUROVIEW", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = false,
                    onClick = { navigateFromMenu(Destination.Home) },
                )
                NavigationDrawerItem(
                    label = { Text("Series selector") },
                    selected = false,
                    onClick = { navigateFromMenu(Destination.Series) },
                )
                NavigationDrawerItem(
                    label = { Text("Library") },
                    selected = false,
                    onClick = { navigateFromMenu(Destination.Library) },
                )
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(demo?.title ?: "MRI viewer") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    actions = {
                        TextButton(onClick = { planePanelOpen = true }) { Text(plane.label) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                var dragDx = 0f
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF03080B))
                        .pointerInput(seriesId) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragDx = 0f },
                                onHorizontalDrag = { _, amount -> dragDx += amount },
                                onDragEnd = {
                                    if (dragDx < -80f) planePanelOpen = true
                                },
                            )
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(plane.label.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "MRI image not loaded",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Real image rendering and slice controls arrive in Phase 3.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = { planePanelOpen = true }) {
                        Text("Choose viewing plane  →")
                    }
                    Text(
                        "Swipe left to open plane selection",
                        modifier = Modifier.padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                if (planePanelOpen) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                            .clickable { planePanelOpen = false },
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.72f),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Viewing plane", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { planePanelOpen = false }) { Text("Close") }
                            }
                            Text(
                                "Phase 1: Plane labels only; no volume reformatting.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ViewPlane.entries.forEach { option ->
                                NavigationDrawerItem(
                                    label = { Text(option.label) },
                                    selected = plane == option,
                                    onClick = {
                                        plane = option
                                        planePanelOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
