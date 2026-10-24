package takagi.ru.monica.gitlab.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.githubConfirmedAction
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.design.GithubAdaptiveLayout

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitLabAccountsScreen(
    state: GitLabAccountsUiState,
    onInstanceChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onClientIdChanged: (String) -> Unit,
    onTokenSignIn: () -> Unit,
    onBrowserSignIn: () -> Unit,
    onCancelSignIn: () -> Unit,
    onActivate: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenBrowser: (String) -> Boolean,
    onBrowserUnavailable: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBrowseProjects: (String) -> Unit = {},
    onAllowWritesChanged: (Boolean) -> Unit = {}
) {
    var browserLogin by rememberSaveable { mutableStateOf(true) }
    val close = { onCancelSignIn(); onBack() }
    BackHandler(onBack = close)
    LaunchedEffect(state.browserUrl) {
        state.browserUrl?.let { if (!onOpenBrowser(it)) onBrowserUnavailable() }
    }
    GithubDetailScaffold(
        title = stringResource(R.string.gitlab_accounts),
        showGithubServiceStatus = false,
        subtitle = stringResource(R.string.gitlab_accounts_subtitle),
        backContentDescription = stringResource(R.string.github_back),
        onBack = close,
        modifier = modifier,
        contentMaxWidth = GithubAdaptiveLayout.formMaxWidth
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.accounts.accounts.forEach { account ->
                key(account.key) {
                    val remove = githubConfirmedAction(
                        title = stringResource(R.string.github_remove_account_title),
                        message = stringResource(R.string.gitlab_remove_account_message),
                        subject = "${account.username}\n${account.instanceUrl}",
                        enabled = !state.busy,
                        onConfirm = { onRemove(account.key) }
                    )
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GithubAvatar(login = account.username, avatarUrl = null)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text(account.name.ifBlank { account.username }, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("@${account.username}", style = MaterialTheme.typography.bodyMedium)
                                    Text(account.instanceUrl, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (state.accounts.activeKey == account.key) Text(stringResource(R.string.gitlab_active_account),
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (state.accounts.activeKey == account.key) {
                                FilledTonalButton(enabled = !state.busy, onClick = { onBrowseProjects(account.key) }) {
                                    Text(stringResource(R.string.gitlab_projects))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(enabled = !state.busy, onClick = { onActivate(account.key) }) {
                                    Text(stringResource(R.string.gitlab_verify_account))
                                }
                                TextButton(onClick = { onOpenBrowser(account.instanceUrl) }) {
                                    Text(stringResource(R.string.gitlab_open_instance))
                                }
                                IconButton(enabled = !state.busy, onClick = remove) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.github_remove_account))
                                }
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.gitlab_add_account), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.instanceInput, onValueChange = onInstanceChanged,
                label = { Text(stringResource(R.string.gitlab_instance_url)) },
                supportingText = { Text(stringResource(R.string.gitlab_instance_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, enabled = !state.busy && state.browserUrl == null,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = browserLogin, onClick = { browserLogin = true },
                    enabled = !state.busy && state.browserUrl == null,
                    label = { Text(stringResource(R.string.gitlab_browser_login)) })
                FilterChip(selected = !browserLogin, onClick = { browserLogin = false },
                    enabled = !state.busy && state.browserUrl == null,
                    label = { Text(stringResource(R.string.gitlab_token_login)) })
            }
            if (browserLogin || state.browserUrl != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = state.allowWrites, onCheckedChange = onAllowWritesChanged, enabled = !state.busy && state.browserUrl == null)
                    Text(stringResource(R.string.gitlab_allow_writes), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = state.clientIdInput, onValueChange = onClientIdChanged,
                    label = { Text(stringResource(R.string.gitlab_application_id)) },
                    supportingText = { Text(stringResource(R.string.gitlab_oauth_hint)) },
                    singleLine = true, enabled = !state.busy && state.browserUrl == null,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onBrowserSignIn,
                    enabled = !state.busy && state.browserUrl == null && state.clientIdInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.gitlab_browser_login))
                }
                if (state.browserUrl != null) {
                    Text(stringResource(R.string.gitlab_waiting_browser))
                    TextButton(onClick = { state.browserUrl.let { if (!onOpenBrowser(it)) onBrowserUnavailable() } }) {
                        Text(stringResource(R.string.gitlab_reopen_browser))
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.tokenInput, onValueChange = onTokenChanged,
                    label = { Text(stringResource(R.string.gitlab_access_token)) },
                    supportingText = { Text(stringResource(R.string.gitlab_token_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = onTokenSignIn,
                    enabled = !state.busy && state.tokenInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.gitlab_token_login))
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.busy || state.browserUrl != null) TextButton(onClick = onCancelSignIn) {
                Text(stringResource(R.string.github_cancel))
            }
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    GitLabSignInError.INVALID_INPUT -> R.string.gitlab_invalid_input
                    GitLabSignInError.REQUEST_FAILED -> R.string.gitlab_request_failed
                    GitLabSignInError.INVALID_CALLBACK -> R.string.gitlab_invalid_callback
                    GitLabSignInError.BROWSER_UNAVAILABLE -> R.string.gitlab_browser_unavailable
                }), color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.gitlab_feature_scope), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
}
