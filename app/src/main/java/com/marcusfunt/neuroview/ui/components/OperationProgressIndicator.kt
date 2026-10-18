package com.marcusfunt.neuroview.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import java.text.NumberFormat
import java.util.Locale

/** Shared truthful progress presentation for loaders, imports, and conversions. */
@Composable
fun OperationProgressIndicator(
    snapshot: OperationSnapshot,
    elapsedMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = snapshot.progress
    val fraction = progress?.fraction
    val percent = progress?.percent
    val showIndeterminate = fraction == null && snapshot.status in setOf(
        OperationStatus.RUNNING,
        OperationStatus.WAITING_FOR_PROVIDER,
        OperationStatus.SLOW,
        OperationStatus.SUSPECTED_STALL,
        OperationStatus.CANCELING,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snapshot.stage.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = NeuroChrome.ink,
            )
            if (percent != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeuroChrome.accent,
                    textAlign = TextAlign.End,
                )
            }
        }

        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = NeuroChrome.accent,
                trackColor = NeuroChrome.stroke.copy(alpha = 0.55f),
            )
        } else if (showIndeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = NeuroChrome.accent,
                trackColor = NeuroChrome.stroke.copy(alpha = 0.55f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            val countText = progress?.countText()
            if (countText != null) {
                Text(
                    text = countText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeuroChrome.muted,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = "Elapsed ${formatElapsed(elapsedMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = NeuroChrome.muted,
                textAlign = TextAlign.End,
            )
        }

        statusDescription(snapshot.status, snapshot.failure?.safeMessage)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (snapshot.status == OperationStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    NeuroChrome.muted
                },
            )
        }
    }
}

private fun OperationProgress.countText(): String? {
    val completed = completedUnits ?: return null
    val measurement = requireNotNull(unit)
    val completedText = formatCount(completed)
    val total = totalUnits
    return if (total == null) {
        "$completedText ${measurement.label(completed)}"
    } else {
        "$completedText of ${formatCount(total)} ${measurement.label(total)}"
    }
}

private fun OperationUnit.label(count: Long): String = when (this) {
    OperationUnit.BYTES -> if (count == 1L) "byte" else "bytes"
    OperationUnit.VOXELS -> if (count == 1L) "voxel" else "voxels"
    OperationUnit.FILES -> if (count == 1L) "file" else "files"
    OperationUnit.SLICES -> if (count == 1L) "slice" else "slices"
    OperationUnit.PIXELS -> if (count == 1L) "pixel" else "pixels"
    OperationUnit.ITEMS -> if (count == 1L) "item" else "items"
}

private fun formatCount(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

private fun formatElapsed(elapsedMs: Long): String {
    val seconds = elapsedMs.coerceAtLeast(0L) / 1_000L
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L)
}

private fun statusDescription(status: OperationStatus, failureMessage: String?): String? = when (status) {
    OperationStatus.QUEUED -> "Queued"
    OperationStatus.RUNNING -> null
    OperationStatus.WAITING_FOR_PROVIDER -> "Waiting for the selected file provider…"
    OperationStatus.SLOW -> "Taking longer than usual"
    OperationStatus.SUSPECTED_STALL -> "This step may be stuck"
    OperationStatus.FAILED -> failureMessage
    OperationStatus.CANCELING -> "Stopping…"
    OperationStatus.CANCELED -> "Canceled"
    OperationStatus.SUCCEEDED -> null
}
