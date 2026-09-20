package takagi.ru.monica.github.feature.discussions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.github.domain.GithubDiscussion

@Composable
fun DiscussionsScreen(
    state: DiscussionsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (GithubDiscussion) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.discussion_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.discussion_back)) } }, actions = {
                IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, stringResource(R.string.discussion_refresh)) }
                IconButton(onClick = onCreate, enabled = state.repositoryId != null) { Icon(Icons.Default.Add, stringResource(R.string.discussion_new)) }
            })
        }
    ) { padding ->
        when {
            state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            state.loadError && state.items.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.discussion_load_error), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.discussion_retry)) }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.items.isEmpty() && !state.loading) item {
                    Text(stringResource(R.string.discussion_empty), modifier = Modifier.padding(vertical = 24.dp))
                }
                if (state.loadError) item {
                    Text(stringResource(R.string.discussion_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.discussion_retry)) }
                }
                items(state.items, key = GithubDiscussion::id) { discussion ->
                    val summary = remember(discussion.body) { discussionSummary(discussion.body) }
                    ElevatedCard(onClick = { onOpen(discussion) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("#${discussion.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(discussion.category.name, modifier = Modifier.weight(1f).padding(start = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(discussion.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (summary.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.discussion_comment_count, discussion.comments) + if (discussion.answered) " · " + stringResource(R.string.discussion_answered) else "", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (state.nextCursor != null) item { OutlinedButton(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) stringResource(R.string.discussion_loading) else stringResource(R.string.discussion_more)) } }
            }
        }
    }
}

@Composable
fun DiscussionComposer(state: DiscussionsUiState, onEdit: (String, String, String?) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit, onRetryCategories: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.discussion_new)) }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.submitting) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.discussion_back))
            }
        })
    }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(state.title, { onEdit(it, state.body, state.categoryId) }, label = { Text(stringResource(R.string.discussion_subject)) }, singleLine = true, enabled = !state.submitting, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.body, { onEdit(state.title, it, state.categoryId) }, label = { Text(stringResource(R.string.discussion_body)) }, minLines = 8, enabled = !state.submitting, modifier = Modifier.fillMaxWidth())
        Text(state.categories.firstOrNull { it.id == state.categoryId }?.name ?: stringResource(R.string.discussion_select_category))
        if (state.categoriesError) {
            Text(stringResource(R.string.discussion_categories_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetryCategories, enabled = !state.submitting) { Text(stringResource(R.string.discussion_retry)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.categories.forEach { category ->
            FilterChip(selected = state.categoryId == category.id, enabled = !state.submitting,
                onClick = { onEdit(state.title, state.body, category.id) }, label = { Text(category.name) })
        }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, enabled = !state.submitting) { Text(stringResource(R.string.discussion_cancel)) }
            Button(onClick = onSubmit, enabled = !state.submitting && state.categories.any { it.id == state.categoryId } && state.title.isNotBlank() && state.body.isNotBlank()) { if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(R.string.discussion_publish)) }
        }
        if (state.submitError) Text(stringResource(R.string.discussion_publish_error), color = MaterialTheme.colorScheme.error)
    }
    }
}
