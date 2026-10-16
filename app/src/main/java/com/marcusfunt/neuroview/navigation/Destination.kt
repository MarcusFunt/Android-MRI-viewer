package com.marcusfunt.neuroview.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Type-safe, saved navigation keys; future MRI IDs replace demo series IDs. */
@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Home : Destination

    @Serializable
    data object Series : Destination

    @Serializable
    data object Library : Destination

    @Serializable
    data class Viewer(val seriesId: String) : Destination
}
