package takagi.ru.monica.github.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveShapes

/**
 * Loading affordance for a paged list.
 *
 * A first load has nothing to preserve, so it renders placeholders shaped like the rows that are
 * about to arrive. A refresh keeps the existing rows visible and only adds a progress line, which
 * avoids replacing content the reader is already looking at.
 */
@Composable
fun GithubListLoadingState(
    isLoading: Boolean,
    hasItems: Boolean,
    row: GithubSkeletonRow,
    modifier: Modifier = Modifier,
    rowCount: Int = DEFAULT_ROW_COUNT
) {
    if (!isLoading) return
    if (hasItems) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        GithubSkeletonList(row = row, modifier = modifier, rowCount = rowCount)
    }
}

/** Placeholder silhouettes covering the list shapes Etoile renders. */
enum class GithubSkeletonRow {
    /** Avatar or status dot beside a title and one metadata line. */
    LIST,

    /** Tonal card with a title, metadata line, and trailing chips. */
    CARD,

    /** Leading icon beside a single line, for file trees and reference lists. */
    COMPACT
}

/**
 * First-load placeholder for a list.
 *
 * The shimmer animation is created once per list and shared by every placeholder inside it, so a
 * long list does not spawn one infinite animation per row.
 */
@Composable
fun GithubSkeletonList(
    row: GithubSkeletonRow,
    modifier: Modifier = Modifier,
    rowCount: Int = DEFAULT_ROW_COUNT
) {
    val brush = rememberGithubSkeletonBrush()
    val description = stringResource(R.string.github_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(if (row == GithubSkeletonRow.COMPACT) 4.dp else 10.dp)
    ) {
        repeat(rowCount) { index ->
            when (row) {
                GithubSkeletonRow.LIST -> SkeletonListRow(brush = brush, index = index)
                GithubSkeletonRow.CARD -> SkeletonCardRow(brush = brush, index = index)
                GithubSkeletonRow.COMPACT -> SkeletonCompactRow(brush = brush, index = index)
            }
        }
    }
}

@Composable
private fun SkeletonListRow(brush: Brush, index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        SkeletonBlock(brush = brush, width = 40.dp, height = 40.dp, shape = CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(brush = brush, width = titleWidth(index), height = 15.dp)
            SkeletonBlock(brush = brush, width = metadataWidth(index), height = 12.dp)
        }
    }
}

@Composable
private fun SkeletonCardRow(brush: Brush, index: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, GithubExpressiveShapes.container)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBlock(brush = brush, width = titleWidth(index), height = 16.dp)
        SkeletonBlock(brush = brush, width = metadataWidth(index), height = 12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(brush = brush, width = 64.dp, height = 20.dp, shape = GithubExpressiveShapes.compact)
            SkeletonBlock(brush = brush, width = 48.dp, height = 20.dp, shape = GithubExpressiveShapes.compact)
        }
    }
}

@Composable
private fun SkeletonCompactRow(brush: Brush, index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(brush = brush, width = 20.dp, height = 20.dp, shape = GithubExpressiveShapes.compact)
        Spacer(Modifier.width(12.dp))
        SkeletonBlock(brush = brush, width = titleWidth(index), height = 13.dp)
    }
}

@Composable
private fun SkeletonBlock(
    brush: Brush,
    width: Dp,
    height: Dp,
    shape: androidx.compose.ui.graphics.Shape = SkeletonBlockShape
) {
    Box(modifier = Modifier.size(width = width, height = height).background(brush, shape))
}

/**
 * A single travelling gradient shared by every placeholder in one list. The gradient spans a fixed
 * pixel window rather than each placeholder's own width so one brush suits every block size.
 */
@Composable
private fun rememberGithubSkeletonBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "githubSkeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_WINDOW_PX * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MILLIS, easing = LinearEasing)
        ),
        label = "githubSkeletonTranslate"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(translate, base, highlight) {
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(translate - SHIMMER_WINDOW_PX, 0f),
            end = Offset(translate, 0f)
        )
    }
}

// Staggering the widths keeps a column of placeholders from reading as a table.
private fun titleWidth(index: Int): Dp = TITLE_WIDTHS[index % TITLE_WIDTHS.size]

private fun metadataWidth(index: Int): Dp = METADATA_WIDTHS[index % METADATA_WIDTHS.size]

private val TITLE_WIDTHS = listOf(220.dp, 168.dp, 196.dp, 148.dp)
private val METADATA_WIDTHS = listOf(120.dp, 148.dp, 104.dp, 132.dp)
private val SkeletonBlockShape = RoundedCornerShape(6.dp)

private const val DEFAULT_ROW_COUNT = 6
private const val SHIMMER_WINDOW_PX = 420f
private const val SHIMMER_DURATION_MILLIS = 1_100
