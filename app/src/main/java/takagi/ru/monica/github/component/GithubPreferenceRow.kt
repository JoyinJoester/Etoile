package takagi.ru.monica.github.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle

/** Measure the already-composed labels once; resizing never subcomposes each preference row. */
@Composable
fun GithubPreferenceRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val material = LocalDesignStyle.current == DesignStyle.MATERIAL
    val hasValue = value.isNotBlank()
    Layout(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (material) 16.dp else 20.dp, vertical = 10.dp),
        content = {
            Box(Modifier.size(if (material) 32.dp else 40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(if (hasValue) value else "", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val iconPlaceable = measurables[0].measure(loose)
        val chevron = measurables[3].measure(loose)
        val leadingGap = 12.dp.roundToPx()
        val trailingGap = 8.dp.roundToPx()
        val valueGap = 12.dp.roundToPx()
        val stackedGap = 2.dp.roundToPx()
        val fixedWidth = iconPlaceable.width + leadingGap + trailingGap + chevron.width
        val textMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - fixedWidth).coerceAtLeast(0)
        } else Constraints.Infinity
        val titleWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(textMaxWidth)
        val valueWidth = measurables[2].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(textMaxWidth)
        val titlePlaceable = measurables[1].measure(loose.copy(minWidth = titleWidth, maxWidth = titleWidth))
        val valuePlaceable = measurables[2].measure(loose.copy(minWidth = valueWidth, maxWidth = valueWidth))
        // Actual glyph widths account for language and font scale, unlike character-count guesses.
        val stacked = hasValue && (valuePlaceable.width > 128.dp.roundToPx() ||
            titlePlaceable.width.toLong() + valueGap + valuePlaceable.width > textMaxWidth.toLong())
        val textHeight = if (stacked) titlePlaceable.height + stackedGap + valuePlaceable.height
            else maxOf(titlePlaceable.height, if (hasValue) valuePlaceable.height else 0)
        val naturalWidth = fixedWidth + if (stacked || !hasValue) {
            maxOf(titlePlaceable.width, if (hasValue) valuePlaceable.width else 0)
        } else titlePlaceable.width + valueGap + valuePlaceable.width
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.constrainWidth(naturalWidth)
        val height = constraints.constrainHeight(maxOf(iconPlaceable.height, chevron.height, textHeight))
        layout(width, height) {
            val textStart = iconPlaceable.width + leadingGap
            iconPlaceable.placeRelative(0, (height - iconPlaceable.height) / 2)
            chevron.placeRelative(width - chevron.width, (height - chevron.height) / 2)
            if (stacked) {
                val top = (height - textHeight) / 2
                titlePlaceable.placeRelative(textStart, top)
                valuePlaceable.placeRelative(textStart, top + titlePlaceable.height + stackedGap)
            } else {
                titlePlaceable.placeRelative(textStart, (height - titlePlaceable.height) / 2)
                if (hasValue) valuePlaceable.placeRelative(width - chevron.width - trailingGap - valuePlaceable.width,
                    (height - valuePlaceable.height) / 2)
            }
        }
    }
}
