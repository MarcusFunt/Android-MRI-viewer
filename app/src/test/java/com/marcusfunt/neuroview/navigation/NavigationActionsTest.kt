package com.marcusfunt.neuroview.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationActionsTest {
    @Test
    fun navigatePushesNextScreen() {
        val stack = mutableListOf<NavKey>(Destination.Home)
        NavigationActions.navigate(stack, Destination.Series)
        NavigationActions.navigate(stack, Destination.Viewer("demo-t1"))
        assertEquals(
            listOf(Destination.Home, Destination.Series, Destination.Viewer("demo-t1")),
            stack,
        )
    }

    @Test
    fun choosingExistingDestinationPopsRatherThanDuplicating() {
        val stack = mutableListOf<NavKey>(Destination.Home, Destination.Series, Destination.Viewer("demo-t1"))
        NavigationActions.navigate(stack, Destination.Series)
        assertEquals(listOf(Destination.Home, Destination.Series), stack)
    }

    @Test
    fun homeAlwaysResetsHistory() {
        val stack = mutableListOf<NavKey>(Destination.Home, Destination.Library)
        NavigationActions.navigate(stack, Destination.Home)
        assertEquals(listOf(Destination.Home), stack)
    }

    @Test
    fun backNeverRemovesRoot() {
        val stack = mutableListOf<NavKey>(Destination.Home)
        NavigationActions.back(stack)
        assertEquals(listOf(Destination.Home), stack)
        NavigationActions.navigate(stack, Destination.Library)
        NavigationActions.back(stack)
        assertEquals(listOf(Destination.Home), stack)
    }

    @Test
    fun differentSeriesProduceDistinctViewerDestinations() {
        val stack = mutableListOf<NavKey>(Destination.Home, Destination.Series)
        NavigationActions.navigate(stack, Destination.Viewer("demo-t1"))
        NavigationActions.navigate(stack, Destination.Viewer("demo-t2"))
        assertEquals(Destination.Viewer("demo-t2"), stack.last())
    }
}
