package takagi.ru.monica.github.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.github.design.GithubAdaptiveLayout

/** Uses the actual content width after navigation, insets, and split-window constraints. */
@Composable
fun GithubAdaptiveGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    minimumColumnWidth: Dp = 360.dp,
    maxColumns: Int = 2,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalSpacing: Dp = 20.dp,
    content: LazyGridScope.() -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth - contentPadding.calculateStartPadding(direction) -
            contentPadding.calculateEndPadding(direction)
        val columns = ((availableWidth + horizontalSpacing) /
            (minimumColumnWidth * fontScale + horizontalSpacing)).toInt().coerceIn(1, maxColumns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            content = content
        )
    }
}

fun LazyGridScope.githubFullSpanItem(
    key: Any? = null,
    contentType: Any? = null,
    content: @Composable LazyGridItemScope.() -> Unit
) {
    item(key = key, contentType = contentType, span = { GridItemSpan(maxLineSpan) }) {
        val scope = this
        Column(Modifier.fillMaxWidth()) { scope.content() }
    }
}

/** The main composition keeps its scroll and editor state as the window changes width. */
@Composable
fun GithubAdaptiveDetailLayout(
    modifier: Modifier = Modifier,
    sidebarFraction: Float = .34f,
    sidebar: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier, Boolean) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val currentContent by rememberUpdatedState(content)
    val mainPane = remember {
        movableContentOf<Modifier, Boolean> { paneModifier, expanded ->
            currentContent(paneModifier, expanded)
        }
    }
    BoxWithConstraints(modifier) {
        val expanded = maxWidth >= GithubAdaptiveLayout.detailWorkspaceWidth * fontScale
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                sidebar(Modifier.weight(sidebarFraction).fillMaxHeight())
                mainPane(Modifier.weight(1f - sidebarFraction).fillMaxHeight(), true)
            }
        } else {
            mainPane(Modifier.fillMaxSize(), false)
        }
    }
}
