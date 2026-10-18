package com.marcusfunt.neuroview.ui

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.marcusfunt.neuroview.catalog.ScanRecord
import com.marcusfunt.neuroview.conversion.DerivedDicomWriter
import com.marcusfunt.neuroview.conversion.DicomFolderScanner
import com.marcusfunt.neuroview.conversion.DicomReader
import com.marcusfunt.neuroview.conversion.DicomSeriesAssembler
import com.marcusfunt.neuroview.conversion.NiftiSeriesWriter
import com.marcusfunt.neuroview.mri.MriMemoryBudget
import com.marcusfunt.neuroview.mri.NiftiReadOptions
import com.marcusfunt.neuroview.mri.NiftiReadProgress
import com.marcusfunt.neuroview.mri.NiftiReadStage
import com.marcusfunt.neuroview.mri.NiftiReadUnit
import com.marcusfunt.neuroview.mri.NiftiReader
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
import com.marcusfunt.neuroview.ui.operations.RecoveryAction
import java.io.FileNotFoundException
import java.io.IOException
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversionControls(
    val busy: Boolean,
    val message: String?,
    val operation: OperationSnapshot?,
    val selectDicomSeries: () -> Unit,
    val selectDicomFolder: () -> Unit,
    val exportNiftiAsDicom: (ScanRecord) -> Unit,
    val cancel: () -> Unit,
)

@Composable
fun rememberConversionControls(): ConversionControls {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val coordinator = remember { OperationCoordinator() }
    val snapshots by coordinator.states.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var activeOperationId by remember { mutableStateOf<OperationId?>(null) }
    var activeSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var dicomSource by remember { mutableStateOf(emptyList<Uri>()) }
    var ignoredFolderFiles by remember { mutableStateOf(0) }
    var niftiSource by remember { mutableStateOf<ScanRecord?>(null) }

    fun reportProgress(
        id: OperationId,
        stage: OperationStage,
        completed: Long? = null,
        total: Long? = null,
        unit: OperationUnit? = null,
    ) {
        val progress = if (completed == null && total == null) null
        else OperationProgress(completed, total, requireNotNull(unit))
        coordinator.reportProgress(id, stage, progress)
    }

    fun failure(error: Throwable): OperationFailure {
        val detail = error.message.orEmpty()
        return when {
            error is OutOfMemoryError -> OperationFailure(
                OperationFailureKind.OUT_OF_MEMORY,
                "This device is low on memory. Try a smaller DICOM series or MRI volume.",
                retryable = true,
                actions = setOf(RecoveryAction.RETRY, RecoveryAction.CLEAR_DERIVED_CACHE),
            )
            detail.contains("Insufficient storage", ignoreCase = true) -> OperationFailure(
                OperationFailureKind.INSUFFICIENT_STORAGE,
                "There is not enough storage to finish this conversion.",
                retryable = true,
                actions = setOf(RecoveryAction.CLEAR_DERIVED_CACHE, RecoveryAction.RETRY),
            )
            error is FileNotFoundException || error is SecurityException -> OperationFailure(
                OperationFailureKind.SOURCE_UNAVAILABLE,
                "A selected MRI document is no longer accessible. Select it again.",
                retryable = true,
                actions = setOf(RecoveryAction.RECONNECT_SOURCE, RecoveryAction.RETRY),
            )
            error is IllegalArgumentException -> OperationFailure(
                OperationFailureKind.INVALID_DATA,
                detail.ifBlank { "The selected MRI data could not be converted." },
                retryable = false,
                actions = setOf(RecoveryAction.RECONNECT_SOURCE),
            )
            error is IOException -> OperationFailure(
                OperationFailureKind.PROVIDER_ERROR,
                "The selected file provider could not supply or save the MRI data.",
                retryable = true,
                actions = setOf(RecoveryAction.RECONNECT_SOURCE, RecoveryAction.RETRY),
            )
            else -> OperationFailure(
                OperationFailureKind.INTERNAL_ERROR,
                "NeuroView could not finish this conversion. Check source and destination access.",
                retryable = true,
                actions = setOf(RecoveryAction.RETRY, RecoveryAction.RECONNECT_SOURCE),
            )
        }
    }

    fun beginWork(
        initialStage: OperationStage,
        outputToCleanOnFailure: Uri? = null,
        work: suspend (OperationId, CancellationSignal) -> String,
    ) {
        if (busy) return
        val id = coordinator.begin(OperationKind.DICOM_CONVERSION, initialStage)
        val signal = CancellationSignal()
        activeOperationId = id
        activeSignal = signal
        busy = true
        message = null
        activeJob = scope.launch {
            try {
                message = work(id, signal)
                coordinator.succeed(id)
            } catch (cancelled: CancellationException) {
                val deleted = outputToCleanOnFailure?.let { output ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }
                            .getOrDefault(false)
                    }
                }
                withContext(NonCancellable) {
                    coordinator.confirmCanceled(id)
                    message = if (outputToCleanOnFailure == null) {
                        "Conversion canceled."
                    } else if (deleted == true) {
                        "Conversion canceled. Incomplete output removed."
                    } else {
                        "Conversion canceled. Check the destination for an incomplete output."
                    }
                }
            } catch (error: Exception) {
                val deleted = outputToCleanOnFailure?.let { output ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }
                            .getOrDefault(false)
                    }
                }
                val typed = failure(error)
                coordinator.fail(id, typed)
                message = typed.safeMessage + when {
                    outputToCleanOnFailure == null -> ""
                    deleted == true -> " (Incomplete output removed.)"
                    else -> " (Check the destination for an incomplete output.)"
                }
            } catch (error: OutOfMemoryError) {
                val deleted = outputToCleanOnFailure?.let { output ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }
                            .getOrDefault(false)
                    }
                }
                val typed = failure(error)
                coordinator.fail(id, typed)
                message = typed.safeMessage + when {
                    outputToCleanOnFailure == null -> ""
                    deleted == true -> " (Incomplete output removed.)"
                    else -> " (Check the destination for an incomplete output.)"
                }
            } finally {
                busy = false
                activeSignal = null
                activeJob = null
            }
        }
    }

    val createNifti = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { output ->
        if (output == null) {
            dicomSource = emptyList()
            ignoredFolderFiles = 0
        }
        if (output != null && !busy) {
            val source = dicomSource.toList()
            val skippedFolderFiles = ignoredFolderFiles
            beginWork(OperationStage.READING_DICOM_HEADERS, output) { id, signal ->
                val workerContext = currentCoroutineContext()
                val count = withContext(Dispatchers.IO) {
                    val checkCanceled = {
                        workerContext.ensureActive()
                        if (signal.isCanceled) throw CancellationException("Conversion canceled")
                    }
                    require(source.isNotEmpty() && source.size <= 1024) {
                        "Select 1–1024 DICOM files"
                    }
                    // Read one header at a time and keep only metadata until writing each slice.
                    val sources = IdentityHashMap<com.marcusfunt.neuroview.conversion.DicomSlice, Uri>()
                    val slices = source.mapIndexed { index, uri ->
                        checkCanceled()
                        val slice = context.contentResolver.openInputStream(uri)?.use {
                            DicomReader.read(it, includePixels = false)
                        } ?: throw IllegalArgumentException("Cannot open DICOM file ${index + 1}")
                        sources[slice] = uri
                        reportProgress(id, OperationStage.READING_DICOM_HEADERS,
                            (index + 1).toLong(), source.size.toLong(), OperationUnit.FILES)
                        slice
                    }
                    val series = DicomSeriesAssembler.assemble(
                        slices,
                        onProgress = { complete, total ->
                            reportProgress(id, OperationStage.ASSEMBLING_SERIES,
                                complete.toLong(), total.toLong(), OperationUnit.FILES)
                        },
                        cancellationCheck = checkCanceled,
                    )
                    reportProgress(id, OperationStage.WRITING_NIFTI, 0L,
                        series.nz.toLong(), OperationUnit.SLICES)
                    context.contentResolver.openOutputStream(output, "wt")?.use { stream ->
                        NiftiSeriesWriter.writeGzWithProgress(
                            series,
                            stream,
                            readPixels = { metadata ->
                                checkCanceled()
                                val uri = requireNotNull(sources[metadata])
                                val actual = context.contentResolver.openInputStream(uri)?.use {
                                    DicomReader.read(it)
                                } ?: throw IllegalArgumentException("DICOM source is unavailable")
                                require(
                                    actual.studyUid == metadata.studyUid &&
                                        actual.seriesUid == metadata.seriesUid &&
                                        actual.positionLps.contentEquals(metadata.positionLps)
                                ) { "DICOM source changed during conversion" }
                                actual.pixels
                            },
                            onProgress = { complete, total ->
                                reportProgress(id, OperationStage.WRITING_NIFTI,
                                    complete.toLong(), total.toLong(), OperationUnit.SLICES)
                            },
                            cancellationCheck = checkCanceled,
                        )
                    } ?: throw IllegalArgumentException("Cannot write NIfTI destination")
                    series.nz
                }
                "Saved $count DICOM slices as .nii.gz." +
                    (if (skippedFolderFiles > 0) " Skipped $skippedFolderFiles non-DICOM files." else "") +
                    " Use Import NIfTI to add the result to your library."
            }
            // The work captured these values and now owns the conversion lifetime.
            dicomSource = emptyList()
            ignoredFolderFiles = 0
        }
    }

    val pickDicom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { chosen ->
        if (chosen.isNotEmpty() && !busy) {
            if (chosen.size > 1024) {
                message = "Select at most 1024 single-frame DICOM files."
            } else {
                dicomSource = chosen
                ignoredFolderFiles = 0
                createNifti.launch("NeuroView_DICOM_derived.nii.gz")
            }
        }
    }

    val pickDicomFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null && !busy) {
            beginWork(OperationStage.SCANNING_DICOM) { id, signal ->
                val workerContext = currentCoroutineContext()
                val selection = withContext(Dispatchers.IO) {
                    try {
                        DicomFolderScanner.scan(
                            context.contentResolver,
                            treeUri,
                            cancellationSignal = signal,
                            cancellationCheck = {
                                workerContext.ensureActive()
                                if (signal.isCanceled) throw CancellationException("Folder scan canceled")
                            },
                            onProgress = { inspected ->
                                reportProgress(id, OperationStage.SCANNING_DICOM,
                                    inspected.toLong(), null, OperationUnit.ITEMS)
                            },
                        )
                    } catch (canceled: android.os.OperationCanceledException) {
                        if (signal.isCanceled) throw CancellationException("Folder scan canceled", canceled)
                        throw canceled
                    }
                }
                dicomSource = selection.documents
                ignoredFolderFiles = selection.ignoredFiles
                message = "Found ${selection.documents.size} DICOM slices. Choose where to save the NIfTI."
                createNifti.launch("NeuroView_DICOM_derived.nii.gz")
                "Found ${selection.documents.size} DICOM slices. Choose where to save the NIfTI."
            }
        }
    }

    val createDicomZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { output ->
        val record = niftiSource
        if (output != null && record != null && !busy) {
            beginWork(OperationStage.DECODING_VOLUME, output) { id, signal ->
                val workerContext = currentCoroutineContext()
                val checkCanceled = {
                    workerContext.ensureActive()
                    if (signal.isCanceled) throw CancellationException("Export canceled")
                }
                val count = withContext(Dispatchers.IO) {
                    val source = context.contentResolver.openInputStream(
                        Uri.parse(record.documentUri),
                    ) ?: throw IllegalArgumentException("Source NIfTI no longer accessible")
                    val volume = source.use {
                        NiftiReader.read(
                            it,
                            NiftiReadOptions(
                                maxVoxels = MriMemoryBudget.maxVoxels(Runtime.getRuntime().maxMemory()),
                                cancellationCheck = checkCanceled,
                                progressSink = { progress ->
                                    reportProgress(id, progress.toOperationStage(),
                                        progress.toOperationProgress()?.completedUnits,
                                        progress.toOperationProgress()?.totalUnits,
                                        progress.toOperationProgress()?.unit)
                                },
                            ),
                        )
                    }
                    try {
                        reportProgress(id, OperationStage.PACKAGING, 0L,
                            volume.nz.toLong(), OperationUnit.SLICES)
                        context.contentResolver.openOutputStream(output, "wt")?.use { stream ->
                            DerivedDicomWriter.writeZip(
                                volume,
                                stream,
                                onProgress = { complete, total ->
                                    reportProgress(id, OperationStage.PACKAGING,
                                        complete.toLong(), total.toLong(), OperationUnit.SLICES)
                                },
                                cancellationCheck = checkCanceled,
                            )
                        } ?: throw IllegalArgumentException("Cannot write DICOM ZIP")
                        volume.nz
                    } finally {
                        volume.close()
                    }
                }
                "Saved $count anonymized DERIVED Secondary Capture DICOM slices " +
                    "inside a ZIP archive. These are not original scanner DICOMs."
            }
        }
    }

    fun cancel() {
        val id = activeOperationId ?: return
        val operation = snapshots.lastOrNull { it.id == id } ?: return
        if (!operation.status.isTerminal && coordinator.requestCancel(id)) {
            activeSignal?.cancel()
            activeJob?.cancel(CancellationException("Conversion canceled by user"))
        }
    }

    val visibleOperation = if (busy) {
        activeOperationId?.let { id -> snapshots.lastOrNull { it.id == id } }
    } else null

    return ConversionControls(
        busy = busy,
        message = message,
        operation = visibleOperation,
        selectDicomFolder = {
            if (!busy) {
                message = null
                pickDicomFolder.launch(null)
            }
        },
        selectDicomSeries = {
            if (!busy) {
                message = null
                pickDicom.launch(arrayOf("*/*"))
            }
        },
        exportNiftiAsDicom = { record ->
            if (!busy) {
                message = null
                niftiSource = record
                createDicomZip.launch("NeuroView_DERIVED_DICOM.zip")
            }
        },
        cancel = ::cancel,
    )
}

private fun NiftiReadProgress.toOperationStage(): OperationStage = when (stage) {
    NiftiReadStage.READING_HEADER -> OperationStage.READING_HEADER
    NiftiReadStage.VALIDATING_VOLUME -> OperationStage.VALIDATING_VOLUME
    NiftiReadStage.SKIPPING_EXTENSIONS -> OperationStage.SKIPPING_EXTENSIONS
    NiftiReadStage.DECODING_VOLUME -> OperationStage.DECODING_VOLUME
    NiftiReadStage.PREPARING_DISK_BACKING -> OperationStage.PREPARING_DISK_BACKING
    NiftiReadStage.SAMPLING_INTENSITY -> OperationStage.SAMPLING_INTENSITY
}

private fun NiftiReadProgress.toOperationProgress(): OperationProgress? {
    if (completedUnits == null && totalUnits == null) return null
    val operationUnit = when (unit) {
        NiftiReadUnit.BYTES -> OperationUnit.BYTES
        NiftiReadUnit.VOXELS -> OperationUnit.VOXELS
        NiftiReadUnit.SAMPLES -> OperationUnit.ITEMS
        null -> return null
    }
    return OperationProgress(completedUnits, totalUnits, operationUnit)
}
