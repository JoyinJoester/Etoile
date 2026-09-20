package takagi.ru.monica.github.feature.explore

import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.component.githubFullSpanItem
import takagi.ru.monica.github.component.GithubAdaptiveGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.ExpandMore
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onAction: (ExploreAction) -> Unit,
    onOpenRepository: (GithubRepository) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenConversation: (GithubIssueSearchResult) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val topics = ExploreTopic.entries
    val topicLabels = listOf(
        stringResource(R.string.github_topic_for_you),
        stringResource(R.string.github_topic_kotlin),
        stringResource(R.string.github_topic_android),
        stringResource(R.string.github_topic_compose)
    )
    val searchKinds = ExploreSearchKind.entries
    val searchKindLabels = listOf(
        stringResource(R.string.github_search_repositories),
        stringResource(R.string.github_search_users),
        stringResource(R.string.github_search_code),
        stringResource(R.string.github_search_issues),
        stringResource(R.string.github_search_pull_requests)
    )
    val searchLabel = when (state.searchKind) {
        ExploreSearchKind.REPOSITORIES -> stringResource(R.string.github_search_repositories)
        ExploreSearchKind.USERS -> stringResource(R.string.github_search_users)
        ExploreSearchKind.CODE -> stringResource(R.string.github_search_code)
        ExploreSearchKind.ISSUES -> stringResource(R.string.github_search_issues)
        ExploreSearchKind.PULL_REQUESTS -> stringResource(R.string.github_search_pull_requests)
    }
    val resultTitle = when {
        state.isCurated -> stringResource(R.string.github_trending_repositories)
        state.searchKind == ExploreSearchKind.USERS -> stringResource(R.string.github_search_users)
        state.searchKind == ExploreSearchKind.CODE -> stringResource(R.string.github_search_code)
        state.searchKind == ExploreSearchKind.ISSUES -> stringResource(R.string.github_search_issues)
        state.searchKind == ExploreSearchKind.PULL_REQUESTS ->
            stringResource(R.string.github_search_pull_requests)
        else -> stringResource(R.string.github_search_results)
    }
    var flipMode by rememberSaveable { mutableStateOf(false) }
    val flipVisible = state.searchKind == ExploreSearchKind.REPOSITORIES
    val showFlip = flipMode && flipVisible

    // 列表 ↔ 翻页切换用淡入淡出衔接，贴合 CapyReader 的阅读模式切换手感。
    Crossfade(
        targetState = showFlip,
        animationSpec = GithubExpressiveMotion.standardTween(),
        label = "explore-mode",
        modifier = modifier
    ) { isFlip ->
        if (isFlip) {
            ExploreFlipMode(
                state = state,
                onAction = onAction,
                query = state.query,
                onQueryChange = { onAction(ExploreAction.QueryChanged(it)) },
                placeholder = stringResource(R.string.github_search_placeholder),
                scopeLabel = stringResource(R.string.github_search_scope),
                searchLabel = searchLabel,
                searchKindOptions = searchKindLabels,
                searchKindIndex = searchKinds.indexOf(state.searchKind),
                onSearchKindSelected = { onAction(ExploreAction.SearchKindSelected(searchKinds[it])) },
                topicLabels = topicLabels,
                topicIndex = topics.indexOf(state.selectedTopic),
                onTopicSelected = { onAction(ExploreAction.TopicSelected(topics[it])) },
                flipMode = flipMode,
                onFlipModeChange = { flipMode = it },
                clearDescription = stringResource(R.string.github_clear_search),
                onOpenRepository = onOpenRepository,
                modifier = Modifier.fillMaxSize()
            )
        } else {

            // One scroll container keeps the search controls and results in the same
            // reading order and avoids a cramped fixed header on small phones.
            GithubAdaptiveGrid(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
                githubFullSpanItem(key = "search") {
                    ExploreSearchToolbar(
                        query = state.query,
                        onQueryChange = { onAction(ExploreAction.QueryChanged(it)) },
                        placeholder = stringResource(R.string.github_search_placeholder),
                        label = stringResource(R.string.github_search_scope),
                        selectedLabel = searchLabel,
                        options = searchKindLabels,
                        selectedIndex = searchKinds.indexOf(state.searchKind),
                        onSelected = { onAction(ExploreAction.SearchKindSelected(searchKinds[it])) },
                        clearDescription = stringResource(R.string.github_clear_search),
                        flipVisible = flipVisible,
                        flipMode = flipMode,
                        onFlipModeChange = { flipMode = it },
                        flipDescription = stringResource(R.string.github_explore_flip_toggle),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (state.searchKind == ExploreSearchKind.REPOSITORIES) {
                    githubFullSpanItem(key = "topics") {
                        GithubFilterRow(
                            labels = topicLabels,
                            selectedIndex = topics.indexOf(state.selectedTopic),
                            onSelected = { onAction(ExploreAction.TopicSelected(topics[it])) },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                githubFullSpanItem(key = "results-header") {
                    GithubSectionHeader(title = resultTitle, compact = true)
                }
                if (state.isLoading) {
                    githubFullSpanItem(key = "loading") {
                        GithubListLoadingState(
                            isLoading = true,
                            hasItems = state.itemCount > 0,
                            row = GithubSkeletonRow.LIST,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                when (state.searchKind) {
                    ExploreSearchKind.REPOSITORIES -> items(state.repositories, key = GithubRepository::id) { repository ->
                        GithubRepositoryRow(
                            repository = repository,
                            descriptionFallback = stringResource(R.string.github_no_description),
                            languageFallback = stringResource(R.string.github_unknown_language),
                            updatedFallback = stringResource(R.string.github_updated_recently),
                            onClick = { onOpenRepository(repository) }
                        )
                    }
                    ExploreSearchKind.USERS -> items(state.users, key = GithubUserSearchResult::id) { user ->
                        GithubUserSearchRow(user = user, onOpenUser = onOpenUser)
                    }
                    ExploreSearchKind.CODE -> items(state.code, key = GithubCodeSearchResult::id) { result ->
                        GithubCodeSearchRow(result = result, onOpenExternal = onOpenExternal)
                    }
                    ExploreSearchKind.ISSUES,
                    ExploreSearchKind.PULL_REQUESTS -> items(
                        state.conversations,
                        key = GithubIssueSearchResult::id
                    ) { result ->
                        GithubIssueSearchResultRow(
                            result = result,
                            onClick = { onOpenConversation(result) }
                        )
                    }
                }
                githubFullSpanItem(key = "list-status") {
                    GithubPagedListStatus(
                        itemCount = state.itemCount,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        hasError = state.error,
                        canLoadMore = state.canLoadMore,
                        errorMessage = stringResource(R.string.github_search_error),
                        emptyMessage = stringResource(
                            if (state.query.isBlank() && state.searchKind != ExploreSearchKind.REPOSITORIES) {
                                R.string.github_search_enter_query
                            } else {
                                R.string.github_no_search_results
                            }
                        ),
                        onRetry = { onAction(ExploreAction.Retry) },
                        emptyIcon = Icons.Default.Search,
                        onLoadMore = { onAction(ExploreAction.LoadMore) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExploreSearchToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    label: String,
    selectedLabel: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    clearDescription: String,
    flipVisible: Boolean,
    flipMode: Boolean,
    onFlipModeChange: (Boolean) -> Unit,
    flipDescription: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val expressive = LocalDesignStyle.current == DesignStyle.MATERIAL

    val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
        GithubSearchField(
            value = query,
            onValueChange = onQueryChange,
            label = placeholder,
            modifier = fieldModifier,
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = clearDescription
                        )
                    }
                }
            } else {
                null
            },
            compact = true
        )
    }
    val flipControl: @Composable () -> Unit = {
        if (flipVisible) {
            FilledTonalIconToggleButton(
                checked = flipMode,
                onCheckedChange = onFlipModeChange,
                modifier = Modifier.size(if (expressive) 48.dp else 56.dp)
            ) {
                Icon(
                    imageVector = if (flipMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.AutoStories,
                    contentDescription = flipDescription
                )
            }
        }
    }
    val scopeControl: @Composable (Modifier) -> Unit = { scopeModifier ->
        Box(scopeModifier) {
            if (expressive) {
                FilledTonalButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        selectedLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = label,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(
                            R.string.github_explore_filter_description,
                            label,
                            selectedLabel
                        )
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        modifier = Modifier.semantics { selected = index == selectedIndex },
                        text = { Text(option) },
                        leadingIcon = if (index == selectedIndex) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            if (index != selectedIndex) onSelected(index)
                        }
                    )
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Preserve room for queries when actions or large fonts would squeeze the field.
        val stacked = expressive || maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                searchField(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (expressive) {
                        scopeControl(Modifier.weight(1f))
                        flipControl()
                    } else {
                        Text(
                            selectedLabel,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        flipControl()
                        scopeControl(Modifier)
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                searchField(Modifier.weight(1f))
                flipControl()
                scopeControl(Modifier)
            }
        }
    }
}
