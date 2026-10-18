package com.marcusfunt.neuroview.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.processing.BrainSuiteClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deliberately a separate, opt-in workflow from display-only adjustments.
 * BrainSuite's Linux/Docker code is not bundled into the Android APK.
 */
@Composable
fun BrainSuitePanel(documentUri: String?, documentName: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    // Don't retain the access secret through Android saved-instance-state.
    var token by remember { mutableStateOf("") }
    var stage by rememberSaveable { mutableStateOf("CSE") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var jobId by rememberSaveable { mutableStateOf("") }
    var jobStatus by remember { mutableStateOf<BrainSuiteClient.Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun client() = BrainSuiteClient(address, token)

    fun perform(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "BrainSuite operation failed."
            } finally {
                busy = false
            }
        }
    }

    val saveResults = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination != null && jobId.isNotBlank()) {
            val selected = jobId
            perform {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "wt")?.use { stream ->
                            client().download(selected, stream)
                        } ?: error("Cannot write the ZIP destination.")
                    }
                    message = "BrainSuite results saved. Extract the ZIP to inspect derivatives."
                } catch (error: Exception) {
                    withContext(Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
                    }
                    throw error
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("BrainSuite anatomical processing", style = MaterialTheme.typography.titleMedium)
        Text(
            "Run the official BrainSuite BIDS-App on your own Docker workstation. " +
                "A selected T1-weighted NIfTI is sent over HTTPS; processing does not run on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = stage == "CSE",
                onClick = { stage = "CSE" },
                label = { Text("Surface extraction") },
                enabled = !busy,
            )
            FilterChip(
                selected = stage == "CSE+SVREG",
                onClick = { stage = "CSE+SVREG" },
                label = { Text("Extract + atlas") },
                enabled = !busy,
            )
        }

        Text(
            if (stage == "CSE") "CSE: brain/surface extraction and cortical thickness."
            else "CSE + SVReg: also register the anatomy to BrainSuite's atlas.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim(); jobStatus = null },
            label = { Text("Your BrainSuite HTTPS address") },
            placeholder = { Text("https://computer.tailnet.ts.net") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; jobStatus = null },
            label = { Text("Access token (not saved)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !busy,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = consent,
                onCheckedChange = { consent = it },
                enabled = !busy,
            )
            Text(
                "I understand this transfers the selected MRI to my configured computer. " +
                    "MRI images may contain identifiable anatomy.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(top = 7.dp),
            )
        }
        Button(
            onClick = {
                perform {
                    val source = requireNotNull(documentUri) { "Open a NIfTI series first." }
                    val label = requireNotNull(documentName)
                    require(consent) { "Confirm the privacy notice before sending MRI data." }
                    val result = withContext(Dispatchers.IO) {
                        client().submit(context.contentResolver, Uri.parse(source), label, stage)
                    }
                    jobId = result.id
                    jobStatus = result
                    message = "BrainSuite job submitted. Use Refresh status to check its progress."
                }
            },
            enabled = !busy && consent && documentUri != null && !documentName.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send T1w scan for processing")
        }
        Text(
            "Only a T1w scan is supported here. DWI/fMRI pipelines require full BIDS datasets " +
                "with sequence metadata; they are not enabled in this workflow.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = jobId,
            onValueChange = { jobId = it.trim(); jobStatus = null },
            label = { Text("Job ID (paste here to resume)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    perform {
                        jobStatus = withContext(Dispatchers.IO) { client().status(jobId) }
                    }
                },
                enabled = !busy && jobId.isNotBlank(),
            ) { Text("Refresh status") }
            Button(
                onClick = { saveResults.launch("NeuroView_BrainSuite_results.zip") },
                enabled = !busy && jobStatus?.state == "succeeded",
            ) { Text("Save results ZIP") }
        }
        jobStatus?.let {
            Text(
                "Job: ${it.state} · ${it.stages}",
                style = MaterialTheme.typography.bodyMedium,
            )
            it.message?.let { explanation ->
                Text(explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp))
                Text("Communicating with your processing computer…")
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Text(
            "The source image is never overwritten. Results remain on the workstation until " +
                "you download them. For research and exploration, not clinical diagnosis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
