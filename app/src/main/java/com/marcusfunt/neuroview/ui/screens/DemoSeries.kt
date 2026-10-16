package com.marcusfunt.neuroview.ui.screens

/** Illustrative UI options, not actual patient MRI studies. */
data class DemoSeries(
    val id: String,
    val title: String,
    val detail: String,
    val defaultPlane: ViewPlane,
)

enum class ViewPlane(val label: String) {
    AXIAL("Axial"),
    CORONAL("Coronal"),
    SAGITTAL("Sagittal"),
}

val demoSeries = listOf(
    DemoSeries("demo-t1", "T1-weighted", "Axial • Demo entry", ViewPlane.AXIAL),
    DemoSeries("demo-t2", "T2-weighted", "Coronal • Demo entry", ViewPlane.CORONAL),
    DemoSeries("demo-flair", "FLAIR", "Sagittal • Demo entry", ViewPlane.SAGITTAL),
)

fun seriesById(id: String): DemoSeries? = demoSeries.firstOrNull { it.id == id }
