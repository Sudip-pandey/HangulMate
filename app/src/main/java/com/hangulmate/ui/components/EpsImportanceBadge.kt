package com.hangulmate.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hangulmate.R
import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.ui.theme.Spacing
import com.hangulmate.ui.theme.TaegeukRed

/**
 * Small coloured badge showing a word's EPS-TOPIK importance.
 * High = red (exam-critical), medium = indigo, low = neutral.
 */
@Composable
fun EpsImportanceBadge(
    importance: EpsImportance,
    modifier: Modifier = Modifier,
) {
    val (labelRes, containerColor, contentColor) = when (importance) {
        EpsImportance.HIGH -> Triple(
            R.string.eps_high,
            TaegeukRed,
            MaterialTheme.colorScheme.onPrimary,
        )
        EpsImportance.MEDIUM -> Triple(
            R.string.eps_medium,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        EpsImportance.LOW -> Triple(
            R.string.eps_low,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.xs,
            ),
        )
    }
}
