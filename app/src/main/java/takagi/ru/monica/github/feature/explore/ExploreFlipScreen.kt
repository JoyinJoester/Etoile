package takagi.ru.monica.github.feature.explore

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDotMatrix
import takagi.ru.monica.github.component.GithubIssueSearchResultRow
import takagi.ru.monica.github.component.GithubListLoadingState
import takagi.ru.monica.github.component.GithubSkeletonRow
import takagi.ru.monica.github.component.GithubPagedListStatus
import takagi.ru.monica.github.component.GithubRepositoryRow
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubSearchField
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.component.GithubTechnicalTag
import takagi.ru.monica.github.component.GithubUserRow
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubCodeSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubUserSearchResult

/**
 * CapyReader 式翻页探索:整页卡片、视差与页码动效。
 */
/**
 * 翻页探索模式：参考 CapyReader 的翻页阅读，仓库以整页卡片呈现，
 * 横向滑动逐个"翻阅"，接近末页时自动追加搜索结果。
 */
@Composable
internal fun ExploreFlipMode(
    state: ExploreUiState,
    onAction: (ExploreAction) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    scopeLabel: String,
    searchLabel: String,
    searchKindOptions: List<String>,
    searchKindIndex: Int,
    onSearchKindSelected: (Int) -> Unit,
    topicLabels: List<String>,
    topicIndex: Int,
    onTopicSelected: (Int) -> Unit,
    flipMode: Boolean,
    onFlipModeChange: (Boolean) -> Unit,
    clearDescription: String,
    onOpenRepository: (GithubRepository) -> Unit,
    modifier: Modifier = Modifier
) {
    val repositories = state.repositories
    val pagerState = rememberPagerState(pageCount = { repositories.size })
    LaunchedEffect(pagerState.currentPage, repositories.size) {
        if (repositories.isNotEmpty() && pagerState.currentPage >= repositories.size - 2) {
            if (state.canLoadMore && !state.isLoadingMore) onAction(ExploreAction.LoadMore)
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = GithubAdaptiveLayout.contentMaxWidth).fillMaxSize()) {
        ExploreSearchToolbar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = placeholder,
            label = scopeLabel,
            selectedLabel = searchLabel,
            options = searchKindOptions,
            selectedIndex = searchKindIndex,
            onSelected = onSearchKindSelected,
            clearDescription = clearDescription,
            flipVisible = true,
            flipMode = flipMode,
            onFlipModeChange = onFlipModeChange,
            flipDescription = stringResource(R.string.github_explore_flip_toggle),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
        )
        GithubFilterRow(
            labels = topicLabels,
            selectedIndex = topicIndex,
            onSelected = onTopicSelected,
            modifier = Modifier.padding(top = 8.dp)
        )
        when {
            repositories.isEmpty() && state.isLoading -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                ExploreFlipSkeletonCard()
            }
            repositories.isEmpty() -> GithubPagedListStatus(
                itemCount = 0,
                isInitialLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                hasError = state.error,
                canLoadMore = state.canLoadMore,
                errorMessage = stringResource(R.string.github_search_error),
                emptyMessage = stringResource(R.string.github_no_search_results),
                onRetry = { onAction(ExploreAction.Retry) },
                emptyIcon = Icons.Default.Search,
                onLoadMore = { onAction(ExploreAction.LoadMore) },
                modifier = Modifier.padding(16.dp)
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    pageSpacing = 16.dp,
                    // 预组装相邻页:快滑时邻页已就绪,不会在滑动中闪现空白。
                    beyondViewportPageCount = 1
                ) { page ->
                    val repository = repositories.getOrNull(page) ?: return@HorizontalPager
                    // 翻页阅读感:相邻页略微缩小并降透明度,当前页完全醒目;
                    // 内容层做反向视差,卡片滑过时标题以稍慢的速度跟随。
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val distance = abs(pageOffset)
                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - (distance.coerceIn(0f, 1f) * 0.45f)
                            val scale = 1f - (distance.coerceIn(0f, 1f) * 0.05f)
                            scaleX = scale
                            scaleY = scale
                        }
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationX = pageOffset * 28.dp.toPx()
                            }
                        ) {
                            ExploreFlipCard(
                                repository = repository,
                                onClick = { onOpenRepository(repository) }
                            )
                        }
                    }
                }
                val indicatorTween = GithubExpressiveMotion.quickTween<IntOffset>()
                val indicatorFade = GithubExpressiveMotion.quickTween<Float>()
                AnimatedContent(
                    targetState = pagerState.currentPage,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInVertically(animationSpec = indicatorTween) { height -> if (forward) height / 2 else -height / 2 } +
                            fadeIn(indicatorFade)) togetherWith
                            (slideOutVertically(animationSpec = indicatorTween) { height -> if (forward) -height / 2 else height / 2 } +
                            fadeOut(indicatorFade))
                    },
                    label = "explore-page-indicator",
                    modifier = Modifier.fillMaxWidth()
                ) { current ->
                    Text(
                        text = stringResource(
                            R.string.github_explore_page_indicator,
                            current + 1,
                            repositories.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
    }
}

/** 翻页模式首屏加载骨架：与翻页卡片同构的占位卡，避免布局跳变。 */
@Composable
internal fun ExploreFlipSkeletonCard() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .padding(top = 12.dp)
                        .height(14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(7.dp)
                        )
                )
                GithubDotMatrix(
                    columns = 18,
                    rows = 3,
                    litRatio = 0f,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 30.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

@Composable
internal fun ExploreFlipCard(
    repository: GithubRepository,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        // Nothing canvas stays monochrome; language is encoded by the small status dot below.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = githubLanguageColor(repository.language),
                            shape = RoundedCornerShape(999.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                GithubTechnicalLabel(text = repository.language ?: stringResource(R.string.github_unknown_language))
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                GithubTechnicalLabel(text = formatCount(repository.stars))
            }
            Column {
                Text(
                    text = repository.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = repository.description ?: stringResource(R.string.github_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
                GithubDotMatrix(
                    columns = 18,
                    rows = 3,
                    seed = repository.fullName.hashCode(),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            GithubTechnicalTag(label = stringResource(R.string.github_explore_open_project))
        }
    }
}

/** 主流语言 → GitHub 语言色，用于翻页封面渐变。 */
internal fun githubLanguageColor(language: String?): Color = when (language?.lowercase()) {
    "kotlin" -> Color(0xFF7C4DFF)
    "java" -> Color(0xFFB07219)
    "python" -> Color(0xFF3572A5)
    "javascript" -> Color(0xFFC7A833)
    "typescript" -> Color(0xFF3178C6)
    "rust" -> Color(0xFFDEA584)
    "go" -> Color(0xFF00ADD8)
    "swift" -> Color(0xFFF05138)
    "dart" -> Color(0xFF00B4AB)
    "c++" -> Color(0xFFF34B7D)
    "c" -> Color(0xFF7C7C7C)
    "c#" -> Color(0xFF178600)
    "ruby" -> Color(0xFF701516)
    "shell" -> Color(0xFF89E051)
    "html" -> Color(0xFFE34C26)
    else -> Color(0xFF6B7A8F)
}

internal fun formatCount(count: Int): String = when {
    count >= 10000 -> "%.1fk".format(count / 1000f)
    else -> count.toString()
}

@Composable
internal fun GithubUserSearchRow(
    user: GithubUserSearchResult,
    onOpenUser: (String) -> Unit
) {
    GithubUserRow(
        login = user.login,
        avatarUrl = user.avatarUrl,
        supportingText = if (user.accountType.equals("Organization", ignoreCase = true)) {
            stringResource(R.string.github_organization)
        } else {
            stringResource(R.string.github_user)
        },
        onClick = { onOpenUser(user.login) }
    )
}

@Composable
internal fun GithubCodeSearchRow(
    result: GithubCodeSearchResult,
    onOpenExternal: (String) -> Unit
) {
    Surface(
        onClick = { onOpenExternal(result.htmlUrl) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = result.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 30.dp, top = 6.dp)
            )
            Text(
                text = result.repositoryFullName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 30.dp, top = 6.dp)
            )
        }
    }
}

