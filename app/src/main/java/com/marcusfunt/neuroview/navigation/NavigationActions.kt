package com.marcusfunt.neuroview.navigation

import androidx.navigation3.runtime.NavKey

/** Keeps a single back stack for top-level navigation, with sensible pop-to-existing behavior. */
object NavigationActions {
    /** The most recent viewer remains active while a drawer or library screen is on top. */
    fun activeViewer(backStack: List<NavKey>): Destination.Viewer? =
        backStack.asReversed().firstNotNullOfOrNull { it as? Destination.Viewer }

    fun navigate(backStack: MutableList<NavKey>, destination: Destination) {
        if (destination == Destination.Home) {
            backStack.clear()
            backStack.add(Destination.Home)
            return
        }

        val existing = backStack.indexOfLast { it == destination }
        if (existing >= 0) {
            while (backStack.lastIndex > existing) {
                backStack.removeAt(backStack.lastIndex)
            }
        } else {
            backStack.add(destination)
        }
    }

    fun back(backStack: MutableList<NavKey>) {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
