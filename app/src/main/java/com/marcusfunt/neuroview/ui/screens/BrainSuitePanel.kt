package com.marcusfunt.neuroview.ui.screens

import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.processing.BrainSuiteClient
import com.marcusfunt.neuroview.processing.BrainSuiteJobHistory
import com.marcusfunt.neuroview.processing.BrainSuiteServiceException
import com.marcusfunt.neuroview.processing.brainSuiteFailureMessage
import com.marcusfunt.neuroview.ui.components.OperationProgressIndicator
import com.marcusfunt.neuroview.ui.operations.OperationCoordinator
import com.marcusfunt.neuroview.ui.operations.OperationFailure
import com.marcusfunt.neuroview.ui.operations.OperationFailureKind
import com.marcusfunt.neuroview.ui.operations.OperationId
import com.marcusfunt.neuroview.ui.operations.OperationKind
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStage
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.operations.SystemMonotonicClock
import java.io.IOException
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deliberately a separate, opt-in workflow from display-only adjustments.
 * BrainSuite's Linux/Docker code is not bundled into the Android APK.
 */
@Composable
fun BrainSuitePanel(
    documentUri: String?,
    documentName: String?,
    accessToken: String,
    onAccessTokenChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val coordinator = remember { OperationCoordinator() }
    val operationSnapshots by coordinator.states.collectAsState()
    val history = remember(context) { BrainSuiteJobHistory(context.applicationContext) }
    var previousJobs by remember { mutableStateOf(history.list()) }
    var address by rememberSaveable { mutableStateOf("") }
    // Don't retain the access secret through Android saved-instance-state.
    var stage by rememberSaveable { mutableStateOf("CSE") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var jobId by rememberSaveable { mutableStateOf("") }
    var jobStatus by remember { mutableStateOf<BrainSuiteClient.Status?>(null) }
    var operationId by remember { mutableStateOf<OperationId?>(null) }
    var activeSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var lastSuccessfulStatusAt by remember { mutableStateOf<Long?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showToken by remember { mutableStateOf(false) }
    var showConnection by rememberSaveable { mutableStateOf(true) }
    var showJobs by rememberSaveable { mutableStateOf(false) }
    var showLimitations by rememberSaveable { mutableStateOf(false) }
    val canConnect = remember(address, accessToken) {
        runCatching { BrainSuiteClient(address, accessToken) }.isSuccess
    }

    fun client() = BrainSuiteClient(address, accessToken)

    fun applyRemoteStatus(
        status: BrainSuiteClient.Status,
        id: OperationId?,
        isStatusCheck: Boolean = true,
    ) {
        jobStatus = status
        if (isStatusCheck) lastSuccessfulStatusAt = System.currentTimeMillis()
        statusError = null
        val current = id?.let(coordinator::state) ?: return
        if (current.status.isTerminal || current.status == OperationStatus.CANCELING) return
        when (status.state) {
            "succeeded" -> coordinator.succeed(current.id, current.id.value.toString())
            "failed" -> coordinator.fail(
                current.id,
                OperationFailure(
                    kind = OperationFailureKind.REMOTE_ERROR,
                    safeMessage = status.message?.takeIf { it.isNotBlank() }
                        ?: "The BrainSuite processing job failed on the configured computer.",
                    retryable = false,
                ),
            )
            else -> {
                coordinator.reportProgress(current.id, OperationStage.WAITING_FOR_REMOTE, null)
                coordinator.heartbeat(current.id)
            }
        }
    }

    fun transferFailure(error: Throwable): OperationFailure {
        val serviceError = error as? BrainSuiteServiceException
        val retryable = when {
            serviceError != null -> serviceError.responseCode == 429 ||
                serviceError.responseCode == 503 || serviceError.responseCode in 500..599
            error is IOException -> true
            else -> false
        }
        return OperationFailure(
            kind = if (error is IOException && serviceError == null) {
                OperationFailureKind.NETWORK_ERROR
            } else {
                OperationFailureKind.REMOTE_ERROR
            },
            safeMessage = brainSuiteFailureMessage(error),
            retryable = retryable,
        )
    }

    fun cancelTransfer() {
        val id = operationId ?: return
        val stage = coordinator.state(id)?.stage
        if (!busy || stage !in setOf(OperationStage.UPLOADING, OperationStage.DOWNLOADING) ||
            !coordinator.requestCancel(id)
        ) return
        activeSignal?.cancel()
        activeJob?.cancel(CancellationException("BrainSuite transfer canceled by user"))
    }

    LaunchedEffect(documentUri, address, stage, accessToken) {
        consent = false
    }

    LaunchedEffect(jobId, address, accessToken, canConnect, jobStatus?.state, busy) {
        while (jobId.isNotBlank() && canConnect &&
            jobStatus?.state in listOf("queued", "running")) {
            delay(5_000)
            if (busy) continue
            try {
                val result = withContext(Dispatchers.IO) { client().status(jobId) }
                applyRemoteStatus(result, operationId)
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                statusError = brainSuiteFailureMessage(error)
            }
        }
    }


    fun perform(
        initialStage: OperationStage,
        operation: suspend (OperationId, CancellationSignal) -> Unit,
    ) {
        if (busy) return
        val id = coordinator.begin(OperationKind.BRAIN_SUITE, initialStage)
        val signal = CancellationSignal()
        operationId = id
        activeSignal = signal
        busy = true
        message = null
        statusError = null
        activeJob = scope.launch {
            try {
                operation(id, signal)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    coordinator.state(id)?.let { current ->
                        if (!current.status.isTerminal) {
                            if (current.status != OperationStatus.CANCELING) coordinator.requestCancel(id)
                            coordinator.confirmCanceled(id)
                        }
                    }
                    message = "Transfer stopped on this phone. A submitted remote job may continue; refresh its status to check."
                }
                throw cancelled
            } catch (error: Exception) {
                val failure = transferFailure(error)
                coordinator.fail(id, failure)
                message = failure.safeMessage
            } finally {
                busy = false
                activeSignal = null
                activeJob = null
            }
        }
    }

    val saveResults = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination != null && jobId.isNotBlank()) {
            val selected = jobId
            perform(OperationStage.DOWNLOADING) { id, signal ->
                val workerContext = currentCoroutineContext()
                var completed = false
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "wt")?.use { stream ->
                            client().download(
                                selected,
                                stream,
                                onProgress = { complete, total ->
                                    coordinator.reportProgress(
                                        id,
                                        OperationStage.DOWNLOADING,
                                        OperationProgress(complete, total, OperationUnit.BYTES),
                                    )
                                },
                                cancellationCheck = {
                                    workerContext.ensureActive()
                                    if (signal.isCanceled) throw CancellationException("Download canceled")
                                },
                            )
                        } ?: error("Cannot write the ZIP destination.")
                    }
                    completed = true
                    coordinator.succeed(id, id.value.toString())
                    message = "BrainSuite results saved. Extract the ZIP to inspect derivatives."
                } finally {
                    if (!completed) {
                        withContext(NonCancellable + Dispatchers.IO) {
                            runCatching {
                                DocumentsContract.deleteDocument(context.contentResolver, destination)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(modifier = Modifier.weight(1f),
                selected = !showJobs, onClick = { showJobs = false },
                label = { Text("New processing") })
            FilterChip(modifier = Modifier.weight(1f),
                selected = showJobs, onClick = { showJobs = true },
                label = { Text("Previous jobs and results") })
        }
        if (!showJobs) {
            Text("1 · Select processing", style = MaterialTheme.typography.titleSmall)
            Text(
                "Process a T1-weighted NIfTI on your own HTTPS computer. BrainSuite runs there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = stage == "CSE",
                    onClick = { stage = "CSE" },
                    label = { Text("Cortical surfaces") },
                    enabled = !busy,
                )
                FilterChip(
                    selected = stage == "CSE+SVREG",
                    onClick = { stage = "CSE+SVREG" },
                    label = { Text("Surfaces + atlas") },
                    enabled = !busy,
                )
            }

            Text(
                if (stage == "CSE") "CSE: brain/surface extraction and cortical thickness."
                else "CSE + SVReg: also register the anatomy to BrainSuite's atlas.",
                style = MaterialTheme.typography.bodySmall,
            )

        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (showJobs) "Processing computer" else "2 · Processing computer",
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showConnection = !showConnection }) {
                        Text(if (showConnection) "Hide details" else "Edit connection")
                    }
                }
                Text(
                    if (canConnect) "Connection configured · HTTPS"
                    else "Enter the address and token for your own computer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (showConnection) {
                    previousJobs.distinctBy { it.endpoint }.take(3).forEach { saved ->
                        TextButton(
                            onClick = {
                                address = saved.endpoint
                                jobStatus = null; operationId = null
                                lastSuccessfulStatusAt = null; statusError = null
                            },
                            enabled = !busy,
                        ) { Text("Use ${saved.endpoint}") }
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it.trim(); jobStatus = null; operationId = null
                            lastSuccessfulStatusAt = null; statusError = null
                        },
                        label = { Text("Secure HTTPS address") },
                        placeholder = { Text("https://computer.tailnet.ts.net") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        isError = address.isNotBlank() && !address.startsWith("https://"),
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = {
                            onAccessTokenChange(it); jobStatus = null; operationId = null
                            lastSuccessfulStatusAt = null; statusError = null
                        },
                        label = { Text("Access token (not saved)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) "Hide" else "Show")
                            }
                        },
                        enabled = !busy,
                    )
                    TextButton(
                        onClick = { showConnection = false },
                        enabled = canConnect && !busy,
                    ) { Text("Use this computer") }
                }
            }
        }
        if (!showJobs) {
            Text("3 · Confirm upload", style = MaterialTheme.typography.titleSmall)
            Text("Selected NIfTI: ${documentName ?: "Open a T1w NIfTI first"}",
                style = MaterialTheme.typography.bodyMedium)
            if (canConnect) {
                Text("Destination: $address", style = MaterialTheme.typography.bodySmall)
            }
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
                    perform(OperationStage.UPLOADING) { id, signal ->
                        val source = requireNotNull(documentUri) { "Open a NIfTI series first." }
                        val label = requireNotNull(documentName)
                        require(consent) { "Confirm the privacy notice before sending MRI data." }
                        val workerContext = currentCoroutineContext()
                        val result = withContext(Dispatchers.IO) {
                            client().submit(
                                context.contentResolver,
                                Uri.parse(source),
                                label,
                                stage,
                                onProgress = { complete, total ->
                                    coordinator.reportProgress(
                                        id,
                                        OperationStage.UPLOADING,
                                        OperationProgress(complete, total, OperationUnit.BYTES),
                                    )
                                },
                                cancellationCheck = {
                                    workerContext.ensureActive()
                                    if (signal.isCanceled) throw CancellationException("Upload canceled")
                                },
                            )
                        }
                        jobId = result.id
                        showJobs = true
                        showConnection = false
                        consent = false
                        history.add(result.id, address, stage)
                        previousJobs = history.list()
                        applyRemoteStatus(result, id, isStatusCheck = false)
                        message = "BrainSuite job submitted. Use Refresh status to check its progress."
                    }
                },
                enabled = !busy && canConnect && consent && documentUri != null && !documentName.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send T1w scan for processing")
            }
            val missingInput = when {
                busy -> "A transfer or status check is in progress."
                documentUri == null || documentName.isNullOrBlank() -> "Open a T1w NIfTI to enable sending."
                address.isBlank() -> "Enter your processing computer's HTTPS address."
                accessToken.length < 24 -> "Enter the access token supplied by your processing computer."
                !canConnect -> "Use a valid HTTPS server address without a path, query, or username."
                !consent -> "Confirm the upload consent above to enable sending."
                else -> null
            }
            missingInput?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { showLimitations = !showLimitations }) {
                Text(if (showLimitations) "Hide requirements and limitations" else "Requirements and limitations")
            }
            if (showLimitations) {
                Text(
                    "Use a T1w scan with the official BrainSuite BIDS-App on a Docker workstation. " +
                        "DWI/fMRI need complete BIDS datasets and are unavailable here. The source is never " +
                        "overwritten. Results remain on the workstation until downloaded. " +
                        "For research and exploration, not clinical diagnosis.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (showJobs) {

            if (previousJobs.isNotEmpty()) {
                Text("Recent jobs", style = MaterialTheme.typography.titleSmall)
                previousJobs.take(5).forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = !busy, onClick = {
                            address = entry.endpoint
                            jobId = entry.id
                            jobStatus = null
                            operationId = null
                            lastSuccessfulStatusAt = null
                            statusError = null
                            consent = false
                        }, modifier = Modifier.weight(1f)) {
                            Text("${entry.stages} · ${entry.id.take(8)}…")
                        }
                        TextButton(onClick = {
                            history.forget(entry.id)
                            previousJobs = history.list()
                        }) { Text("Forget") }
                    }
                }
            }
            Text("Monitor or retrieve a job", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = jobId,
                onValueChange = {
                    jobId = it.trim(); jobStatus = null; operationId = null
                    lastSuccessfulStatusAt = null; statusError = null
                },
                label = { Text("Job identifier (paste to resume)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (jobId.isNotBlank()) {
                    { TextButton(onClick = { clipboard.setText(AnnotatedString(jobId)) }) { Text("Copy") } }
                } else null,
                singleLine = true,
                enabled = !busy,
            )
            jobStatus?.let { job ->
                val displayState = when (job.state) {
                    "queued" -> "Queued"
                    "running" -> "Processing"
                    "succeeded" -> "Completed"
                    "failed" -> "Failed"
                    else -> "Unknown status"
                }
                Text("${displayState} · ${job.stages}",
                    style = MaterialTheme.typography.bodyMedium)
                job.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (job.state == "queued" || job.state == "running") {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text("Status checks automatically while this panel is open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            lastSuccessfulStatusAt?.let { timestamp ->
                val time = remember(timestamp) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                }
                Text("Last successful status check · $time", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            statusError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        perform(OperationStage.WAITING_FOR_REMOTE) { id, _ ->
                            val result = withContext(Dispatchers.IO) { client().status(jobId) }
                            applyRemoteStatus(result, id)
                        }
                    },
                    enabled = !busy && canConnect && jobId.isNotBlank(),
                ) { Text("Refresh status") }
                Button(
                    onClick = { saveResults.launch("NeuroView_BrainSuite_results.zip") },
                    enabled = !busy && jobStatus?.state == "succeeded",
                ) { Text("Save results ZIP") }
            }
        }
        operationId?.let { id ->
            operationSnapshots.lastOrNull { it.id == id }?.let { operation ->
                BrainSuiteOperationCard(
                    operation = operation,
                    canCancelTransfer = busy && operation.stage in setOf(
                        OperationStage.UPLOADING, OperationStage.DOWNLOADING,
                    ),
                    onCancel = ::cancelTransfer,
                )
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun BrainSuiteOperationCard(
    operation: OperationSnapshot,
    canCancelTransfer: Boolean,
    onCancel: () -> Unit,
) {
    var elapsedNow by remember(operation.id) {
        androidx.compose.runtime.mutableLongStateOf(SystemMonotonicClock.nowMs())
    }
    LaunchedEffect(operation.id) {
        while (true) {
            elapsedNow = SystemMonotonicClock.nowMs()
            delay(1_000L)
        }
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (operation.stage in setOf(OperationStage.UPLOADING, OperationStage.DOWNLOADING)) {
                        "Phone transfer"
                    } else "Remote job status",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (canCancelTransfer && operation.status != OperationStatus.CANCELING) {
                    TextButton(onClick = onCancel) { Text("CANCEL") }
                } else if (operation.status == OperationStatus.CANCELING) {
                    Text("Stopping…", style = MaterialTheme.typography.labelLarge)
                }
            }
            OperationProgressIndicator(
                snapshot = operation,
                elapsedMs = elapsedNow - operation.startedAtElapsedMs,
            )
        }
    }
}
