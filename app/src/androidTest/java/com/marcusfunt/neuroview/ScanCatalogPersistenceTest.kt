package com.marcusfunt.neuroview

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.marcusfunt.neuroview.catalog.ScanCatalog
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanCatalogPersistenceTest {
    @Test
    fun renamedCatalogLabelSurvivesReload() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("persistence_test_$name", mode)
        }
        val preferences = context.getSharedPreferences("scan_catalog_v1", Context.MODE_PRIVATE)
        val metadata = JSONObject()
            .put("dimensions", JSONArray(listOf(256, 256, 256)))
            .put("spacingMm", JSONArray(listOf(1.0, 1.0, 1.0)))
            .put("frames", 1)
            .put("dataType", "uint8")
            .put("orientation", "sform")
            .put("description", "")
        val series = JSONObject()
            .put("id", "demo-scan")
            .put("collectionId", "demo-collection")
            .put("uri", "content://demo/brainchop_demo_original_unsegmented.nii.gz")
            .put("name", "brainchop_demo_original_unsegmented.nii.gz")
            .put("metadata", metadata)
        val catalogJson = JSONObject()
            .put("collections", JSONArray())
            .put("series", JSONArray().put(series))
        val expectedLabel = "BrainChop demo sample, not a personal scan"

        try {
            check(preferences.edit().putString("catalog", catalogJson.toString()).commit())
            runBlocking {
                ScanCatalog(context).renameSeries("demo-scan", expectedLabel)
            }

            val reloaded = ScanCatalog(context).load().scans.single()
            assertEquals(expectedLabel, reloaded.label)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
