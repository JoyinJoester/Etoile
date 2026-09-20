package takagi.ru.monica.github.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.ui.theme.NothingMonoFamily

/**
 * Nothing-flavoured building blocks: monospace ALL-CAPS labels, hairline borders
 * instead of shadows, and discrete segment bars. Colors come from the active
 * color scheme so these still read correctly under the app's other themes.
 */

/**
 * An instrument-panel label: monospace, upper case, wide tracking.
 * 只有 Nothing 风格才用等宽大写，其它风格退回普通标签体，避免观感串味。
 */
@Composable
fun GithubTechnicalLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val nothing = LocalDesignStyle.current == DesignStyle.NOTHING
    Text(
        text = if (nothing) text.uppercase() else text,
        modifier = modifier,
        style = if (nothing) {
            MaterialTheme.typography.labelSmall.copy(fontFamily = NothingMonoFamily)
        } else {
            MaterialTheme.typography.labelMedium
        },
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * A section heading with a trailing hairline rule and an optional count, the
 * "technical manual" header treatment.
 */
@Composable
fun GithubTechnicalSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GithubTechnicalLabel(label)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A flat, borderless-shadow container. Layering is expressed through a hairline
 * outline and background contrast rather than elevation.
 */
@Composable
fun GithubFlatCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * An outlined pill tag. Selected tags invert to the display color, which is the
 * one high-contrast moment the language allows per control.
 */
@Composable
fun GithubTechnicalTag(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tagContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GithubTechnicalLabel(label, color = contentColor)
            if (count != null) {
                GithubTechnicalLabel(
                    text = count.toString(),
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(999.dp),
            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
            content = tagContent
        )
    } else {
        Surface(
            modifier = modifier.heightIn(min = 32.dp),
            shape = RoundedCornerShape(999.dp),
            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
            content = tagContent
        )
    }
}

/**
 * The signature segmented bar: discrete blocks with square ends, no radius, no
 * gradient. Reads as an instrument gauge rather than a progress animation.
 */
@Composable
fun GithubSegmentedBar(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = 6.dp,
    filledColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val segments = total.coerceAtLeast(1)
    val litSegments = filled.coerceIn(0, segments)
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(segments) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(segmentHeight)
                    .background(if (index < litSegments) filledColor else emptyColor)
            )
        }
    }
}

/** A dot used as a bullet or state indicator. Small, monoline, no fill effects. */
@Composable
fun GithubTechnicalDot(
    modifier: Modifier = Modifier,
    size: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, RoundedCornerShape(999.dp))
    )
}

/**
 * A decorative dot-matrix panel: a fixed grid of small dots where a deterministic
 * subset is lit. Purely presentational — used as Nothing-style "technical" filler
 * on tiles and headers.
 */
@Composable
fun GithubDotMatrix(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    litRatio: Float = 0.35f,
    seed: Int = 0,
    litColor: Color = MaterialTheme.colorScheme.onSurface,
    unlitColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    dotSize: Dp = 3.dp,
    spacing: Dp = 4.dp
) {
    val total = (columns * rows).coerceAtLeast(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    val lit = ((index * 31 + seed * 17) % 100) < (litRatio * 100).toInt()
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(if (lit) litColor else unlitColor, RoundedCornerShape(999.dp))
                    )
                }
            }
        }
    }
}

/** A row of evenly spaced hairline dots, e.g. as a quiet divider inside tiles. */
@Composable
fun GithubDotDivider(
    modifier: Modifier = Modifier,
    dotSize: Dp = 3.dp,
    spacing: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(9) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = spacing / 2)
                    .size(dotSize)
                    .background(if (index % 2 == 0) color else color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            )
        }
    }
}
