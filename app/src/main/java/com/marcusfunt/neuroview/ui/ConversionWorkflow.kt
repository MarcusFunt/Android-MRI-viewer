package com.marcusfunt.neuroview.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
import java.util.IdentityHashMap
import com.marcusfunt.neuroview.mri.NiftiReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversionControls(
    val busy: Boolean,
    val message: String?,
    val selectDicomSeries: () -> Unit,
    val selectDicomFolder: () -> Unit,
    val exportNiftiAsDicom: (ScanRecord) -> Unit,
)

@Composable
fun rememberConversionControls(): ConversionControls {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var dicomSource by remember { mutableStateOf(emptyList<Uri>()) }
    var ignoredFolderFiles by remember { mutableStateOf(0) }
    var niftiSource by remember { mutableStateOf<ScanRecord?>(null) }

    // The document is newly created through SAF; attempt removal if a write fails.
    fun reportFailure(output: Uri, error: Throwable) {
        val cleaned = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, output)
        }.getOrDefault(false)
        message = (error.message ?: "Conversion could not finish") +
            if (cleaned) " (Incomplete output removed.)"
            else " (Check the destination for an incomplete output.)"
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
            busy = true
            message = "Converting DICOM series locally…"
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        require(source.isNotEmpty() && source.size <= 1024) {
                            "Select 1–1024 DICOM files"
                        }
                        // First pass reads metadata only. No 3D array or cumulative heap
                        // limit: the writer reopens one DICOM slice at a time in sorted order.
                        val sources = IdentityHashMap<com.marcusfunt.neuroview.conversion.DicomSlice, Uri>()
                        val slices = source.mapIndexed { index, uri ->
                            val slice = context.contentResolver.openInputStream(uri)?.use {
                                DicomReader.read(it, includePixels = false)
                            } ?: throw IllegalArgumentException(
                                "Cannot open DICOM file ${index + 1}"
                            )
                            sources[slice] = uri
                            slice
                        }
                        val series = DicomSeriesAssembler.assemble(slices)
                        context.contentResolver.openOutputStream(output, "wt")?.use { stream ->
                            NiftiSeriesWriter.writeGz(series, stream) { metadata ->
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
                            }
                        } ?: throw IllegalArgumentException("Cannot write NIfTI destination")
                        series.nz
                    }
                    message = "Saved $count DICOM slices as .nii.gz." +
                        (if (ignoredFolderFiles > 0) " Skipped $ignoredFolderFiles non-DICOM files." else "") +
                        " Use Import NIfTI to add the result to your library."
                } catch (cancelled: CancellationException) {
                    withContext(Dispatchers.IO) { reportFailure(output, cancelled) }
                    throw cancelled
                } catch (error: Exception) {
                    withContext(Dispatchers.IO) { reportFailure(output, error) }
                } catch (_: OutOfMemoryError) {
                    withContext(Dispatchers.IO) {
                        reportFailure(output, IllegalStateException("Not enough memory for this DICOM series"))
                    }
                } finally {
                    busy = false
                    dicomSource = emptyList()
                    ignoredFolderFiles = 0
                }
            }
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
            busy = true
            message = "Checking DICOM files in the selected folder…"
            scope.launch {
                try {
                    val selection = withContext(Dispatchers.IO) {
                        DicomFolderScanner.scan(context.contentResolver, treeUri)
                    }
                    dicomSource = selection.documents
                    ignoredFolderFiles = selection.ignoredFiles
                    message = "Found ${selection.documents.size} DICOM slices. Choose where to save the NIfTI."
                    busy = false
                    createNifti.launch("NeuroView_DICOM_derived.nii.gz")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    dicomSource = emptyList()
                    ignoredFolderFiles = 0
                    message = "DICOM folder could not be opened: ${error.message ?: "Unknown error"}"
                } catch (_: OutOfMemoryError) {
                    dicomSource = emptyList()
                    ignoredFolderFiles = 0
                    message = "Not enough memory to inspect this DICOM folder"
                } finally {
                    busy = false
                }
            }
        }
    }

    val createDicomZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { output ->
        val record = niftiSource
        if (output != null && record != null && !busy) {
            busy = true
            message = "Exporting anonymized derived DICOM slices…"
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        val source = context.contentResolver.openInputStream(
                            Uri.parse(record.documentUri),
                        ) ?: throw IllegalArgumentException("Source NIfTI no longer accessible")
                        val volume = source.use { NiftiReader.read(it, MriMemoryBudget.maxVoxels(Runtime.getRuntime().maxMemory())) }
                        context.contentResolver.openOutputStream(output, "wt")?.use {
                            DerivedDicomWriter.writeZip(volume, it)
                        } ?: throw IllegalArgumentException("Cannot write DICOM ZIP")
                        volume.nz
                    }
                    message = "Saved $count anonymized DERIVED Secondary Capture DICOM " +
                        "slices inside a ZIP archive. These are not original scanner DICOMs."
                } catch (cancelled: CancellationException) {
                    withContext(Dispatchers.IO) { reportFailure(output, cancelled) }
                    throw cancelled
                } catch (error: Exception) {
                    withContext(Dispatchers.IO) { reportFailure(output, error) }
                } catch (_: OutOfMemoryError) {
                    withContext(Dispatchers.IO) {
                        reportFailure(output, IllegalStateException("Not enough memory for export"))
                    }
                } finally {
                    busy = false
                    niftiSource = null
                }
            }
        }
    }

    return ConversionControls(
        busy = busy,
        message = message,
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
    )
}
