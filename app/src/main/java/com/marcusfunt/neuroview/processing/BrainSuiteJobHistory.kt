package com.marcusfunt.neuroview.processing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Job references only: no MRI bytes, filenames, patient labels or access tokens. */
data class BrainSuiteJobRef(
    val id: String,
    val endpoint: String,
    val stages: String,
    val submittedAt: Long,
)

class BrainSuiteJobHistory(context: Context) {
    private val prefs = context.getSharedPreferences("brainsuite_job_refs_v1", Context.MODE_PRIVATE)

    fun list(): List<BrainSuiteJobRef> = runCatching {
        val array = JSONArray(prefs.getString("recent", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                BrainSuiteJobRef(
                    id = item.getString("id"),
                    endpoint = item.getString("endpoint"),
                    stages = item.getString("stages"),
                    submittedAt = item.getLong("submittedAt"),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun add(id: String, endpoint: String, stages: String, now: Long = System.currentTimeMillis()) {
        require(Regex("[0-9a-f-]{36}").matches(id))
        val existing = list().filterNot { it.id == id }
        store((listOf(BrainSuiteJobRef(id, endpoint, stages, now)) + existing).take(12))
    }

    fun forget(id: String) {
        store(list().filterNot { it.id == id })
    }

    private fun store(items: List<BrainSuiteJobRef>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("endpoint", it.endpoint)
                    .put("stages", it.stages)
                    .put("submittedAt", it.submittedAt),
            )
        }
        check(prefs.edit().putString("recent", array.toString()).commit()) {
            "Cannot save BrainSuite job history"
        }
    }
}
