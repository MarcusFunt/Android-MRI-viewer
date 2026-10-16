package com.marcusfunt.neuroview.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.marcusfunt.neuroview.navigation.Destination
import com.marcusfunt.neuroview.navigation.NavigationActions
import com.marcusfunt.neuroview.ui.screens.HomeScreen
import com.marcusfunt.neuroview.ui.screens.LibraryScreen
import com.marcusfunt.neuroview.ui.screens.SeriesScreen
import com.marcusfunt.neuroview.ui.screens.ViewerScreen

@Composable
fun NeuroViewApp() {
    val backStack = rememberNavBackStack(Destination.Home)
    val navigate: (Destination) -> Unit = { NavigationActions.navigate(backStack, it) }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { NavigationActions.back(backStack) },
            entryProvider = entryProvider {
                entry<Destination.Home> {
                    HomeScreen(
                        onView = { navigate(Destination.Series) },
                        onLibrary = { navigate(Destination.Library) },
                    )
                }
                entry<Destination.Series> {
                    SeriesScreen(
                        onBack = { NavigationActions.back(backStack) },
                        onSelectSeries = { id -> navigate(Destination.Viewer(id)) },
                    )
                }
                entry<Destination.Library> {
                    LibraryScreen(onBack = { NavigationActions.back(backStack) })
                }
                entry<Destination.Viewer> { destination ->
                    ViewerScreen(
                        seriesId = destination.seriesId,
                        onNavigate = navigate,
                    )
                }
            },
        )
    }
}
