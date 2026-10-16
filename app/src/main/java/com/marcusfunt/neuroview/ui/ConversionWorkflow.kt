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
import com.marcusfunt.neuroview.conversion.DicomReader
import com.marcusfunt.neuroview.conversion.DicomSeriesAssembler
import com.marcusfunt.neuroview.conversion.NiftiSeriesWriter
import com.marcusfunt.neuroview.mri.NiftiReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversionControls(
    val busy: Boolean,
    val message: String?,
    val selectDicomSeries: () -> Unit,
    val exportNiftiAsDicom: (ScanRecord) -> Unit,
)

@Composable
fun rememberConversionControls(): ConversionControls {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var dicomSource by remember { mutableStateOf(emptyList<Uri>()) }
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
                        val slices = source.mapIndexed { index, file ->
                            context.contentResolver.openInputStream(file)?.use {
                                DicomReader.read(it)
                            } ?: throw IllegalArgumentException(
                                "Cannot open DICOM file ${index + 1}"
                            )
                        }
                        val series = DicomSeriesAssembler.assemble(slices)
                        context.contentResolver.openOutputStream(output, "wt")?.use {
                            NiftiSeriesWriter.writeGz(series, it)
                        } ?: throw IllegalArgumentException("Cannot write NIfTI destination")
                        series.nz
                    }
                    message = "Saved $count DICOM slices as .nii.gz. " +
                        "Use Import NIfTI to add the result to your library."
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
                createNifti.launch("NeuroView_DICOM_derived.nii.gz")
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
                        val volume = source.use { NiftiReader.read(it) }
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
