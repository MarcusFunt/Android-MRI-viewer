package com.marcusfunt.neuroview.processing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.OutputStream
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Client for the OPTIONAL self-hosted BrainSuite BIDS bridge.
 *
 * No scan bytes are sent without an explicit user action. The host must provide
 * normal trusted HTTPS (e.g. via Tailscale Serve); redirects are forbidden so
 * bearer tokens and MRI bytes cannot be forwarded to another destination.
 */
class BrainSuiteClient(
    private val endpoint: String,
    private val token: String,
) {
    data class Status(
        val id: String,
        val stages: String,
        val state: String,
        val message: String?,
    )

    private val base: URI = URI(endpoint.trim()).also {
        require(it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank()) {
            "Enter a trusted HTTPS address for your own BrainSuite computer."
        }
        require(it.userInfo == null && it.query == null && it.fragment == null &&
            (it.path.isNullOrEmpty() || it.path == "/")) {
            "Use only a server address, without a path, username, query or fragment."
        }
        require(it.port in -1..65535 && it.port != 0) { "Invalid HTTPS port" }
    }

    init {
        require(token.length >= 24) { "Enter the BrainSuite bridge access token." }
    }

    private fun connect(path: String, method: String): HttpsURLConnection {
        val uri = URI("https", null, base.host, base.port, path, null, null)
        return (URL(uri.toString()).openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun parse(connection: HttpsURLConnection, expected: Int): Status {
        if (connection.responseCode != expected) {
            throw IllegalStateException("BrainSuite service returned HTTP ${connection.responseCode}.")
        }
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val item = JSONObject(json)
        return Status(
            id = item.getString("id"),
            stages = item.getString("stages"),
            state = item.getString("status"),
            message = item.optString("message").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    /** Returns null when the provider cannot report a safe fixed upload size. */
    private fun sizeOf(resolver: ContentResolver, uri: Uri): Long? {
        val descriptorSize = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()
        if (descriptorSize != null && descriptorSize > 0) return descriptorSize
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
        }.getOrNull()
    }

    fun submit(
        resolver: ContentResolver,
        document: Uri,
        originalName: String,
        stages: String,
    ): Status {
        require(stages == "CSE" || stages == "CSE+SVREG") { "Unknown BrainSuite stage" }
        val format = when {
            originalName.endsWith(".nii.gz", ignoreCase = true) -> "nii.gz"
            originalName.endsWith(".nii", ignoreCase = true) -> "nii"
            else -> throw IllegalArgumentException("BrainSuite needs a T1-weighted .nii or .nii.gz scan")
        }
        val length = sizeOf(resolver, document)
        require(length != null && length in 1..(2L * 1024 * 1024 * 1024)) {
            "MRI must have a known size smaller than 2 GiB."
        }
        val connection = connect("/v1/jobs", "POST")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-NeuroView-Format", format)
            connection.setRequestProperty("X-BrainSuite-Stages", stages)
            connection.setFixedLengthStreamingMode(length)
            resolver.openInputStream(document)?.use { input ->
                connection.outputStream.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var transferred = 0L
                    while (transferred < length) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), length - transferred).toInt())
                        require(count > 0) { "Selected MRI ended before upload completed." }
                        output.write(buffer, 0, count)
                        transferred += count
                    }
                    require(input.read() == -1) { "MRI size changed during upload." }
                }
            } ?: throw IllegalArgumentException("Selected MRI is no longer readable")
            return parse(connection, 202)
        } finally {
            connection.disconnect()
        }
    }

    fun status(id: String): Status {
        require(Regex("[0-9a-f-]{36}").matches(id)) { "Invalid BrainSuite job ID" }
        val connection = connect("/v1/jobs/$id", "GET")
        return try {
            parse(connection, 200)
        } finally {
            connection.disconnect()
        }
    }

    fun download(id: String, output: OutputStream) {
        require(Regex("[0-9a-f-]{36}").matches(id)) { "Invalid BrainSuite job ID" }
        val connection = connect("/v1/jobs/$id/results", "GET")
        connection.readTimeout = 300_000
        try {
            if (connection.responseCode != 200) {
                throw IllegalStateException("Results unavailable (HTTP ${connection.responseCode}).")
            }
            require(connection.contentType?.startsWith("application/zip") == true) {
                "Expected a ZIP of BrainSuite derivatives"
            }
            connection.inputStream.use { it.copyTo(output, bufferSize = 64 * 1024) }
            output.flush()
        } finally {
            connection.disconnect()
        }
    }
}
