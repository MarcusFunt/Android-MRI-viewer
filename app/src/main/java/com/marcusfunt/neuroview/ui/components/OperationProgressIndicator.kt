package com.marcusfunt.neuroview.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcusfunt.neuroview.ui.operations.OperationProgress
import com.marcusfunt.neuroview.ui.operations.OperationSnapshot
import com.marcusfunt.neuroview.ui.operations.OperationStatus
import com.marcusfunt.neuroview.ui.operations.OperationUnit
import com.marcusfunt.neuroview.ui.screens.CatalogQuantity
import com.marcusfunt.neuroview.ui.screens.catalogQuantityText
import com.marcusfunt.neuroview.ui.theme.NeuroChrome
import com.marcusfunt.neuroview.ui.theme.LocalNeuroViewMotion
import com.marcusfunt.neuroview.ui.theme.MotionToken
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
    val duration = MotionToken.QUICK.durationMs(LocalNeuroViewMotion.current)
    val resources = LocalContext.current.resources
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = duration),
        label = "Operation progress",
    )
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
            AnimatedContent(
                targetState = snapshot.stage.label,
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                label = "Operation stage",
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            ) { stageLabel ->
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuroChrome.ink,
                )
            }
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
                progress = { animatedFraction },
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
            val countText = progress?.countText(resources)
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

private fun OperationProgress.countText(resources: android.content.res.Resources): String? {
    val completed = completedUnits ?: return null
    val measurement = requireNotNull(unit)
    val completedText = formatCount(completed)
    val total = totalUnits
    return if (total == null) {
        resources.catalogQuantityText(measurement.toCatalogQuantity(), completed, completedText)
    } else {
        "$completedText of ${resources.catalogQuantityText(
            measurement.toCatalogQuantity(),
            total,
            formatCount(total),
        )}"
    }
}

internal fun OperationUnit.toCatalogQuantity(): CatalogQuantity = when (this) {
    OperationUnit.BYTES -> CatalogQuantity.OPERATION_BYTES
    OperationUnit.VOXELS -> CatalogQuantity.OPERATION_VOXELS
    OperationUnit.FILES -> CatalogQuantity.OPERATION_FILES
    OperationUnit.SLICES -> CatalogQuantity.OPERATION_SLICES
    OperationUnit.PIXELS -> CatalogQuantity.OPERATION_PIXELS
    OperationUnit.ITEMS -> CatalogQuantity.OPERATION_ITEMS
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
