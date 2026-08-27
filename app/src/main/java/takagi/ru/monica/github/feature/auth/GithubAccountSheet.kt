package takagi.ru.monica.github.feature.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession

@Composable
fun GithubAccountSheet(
    state: GithubSessionUiState,
    onAction: (GithubSessionAction) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    var removalCandidate by remember { mutableStateOf<GithubAccount?>(null) }
    val activeAccountId = (state.session as? GithubSession.SignedIn)?.account?.id

    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            GithubSheetHeader(
                title = stringResource(R.string.github_accounts),
                subtitle = stringResource(R.string.github_accounts_subtitle),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            )
            Spacer(Modifier.height(20.dp))

            if (state.accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.github_no_saved_accounts),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.forEach { account ->
                        GithubAccountRow(
                            account = account,
                            selected = account.id == activeAccountId,
                            enabled = !state.isAccountActionRunning,
                            onSelect = {
                                onAction(GithubSessionAction.SwitchAccount(account.id))
                            },
                            onRemove = { removalCandidate = account }
                        )
                    }
                }
            }

            if (state.isAccountActionRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.github_account_action_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.accountActionError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = GithubExpressiveShapes.control,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.github_account_action_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onAction(GithubSessionAction.ClearAccountError) }) {
                            Text(stringResource(R.string.github_dismiss))
                        }
                    }
                }
            }

            Button(
                onClick = onAddAccount,
                enabled = !state.isAccountActionRunning,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = GithubExpressiveShapes.control
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github_add_account))
            }
        }
    }

    removalCandidate?.let { account ->
        AlertDialog(
            onDismissRequest = { removalCandidate = null },
            title = { Text(stringResource(R.string.github_remove_account_title)) },
            text = { Text(stringResource(R.string.github_remove_account_message, account.login)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removalCandidate = null
                        onAction(GithubSessionAction.RemoveAccount(account.id))
                    }
                ) {
                    Text(
                        text = stringResource(R.string.github_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removalCandidate = null }) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
}

@Composable
private fun GithubAccountRow(
    account: GithubAccount,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onSelect,
        enabled = enabled && !selected,
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.control,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GithubAvatar(
                login = account.login,
                avatarUrl = account.avatarUrl,
                size = 44.dp,
                shape = GithubExpressiveShapes.control
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name ?: account.login,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (selected) {
                        stringResource(R.string.github_current_account, account.login)
                    } else {
                        "@${account.login}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.github_current),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.github_remove_account)
                    )
                }
            }
        }
    }
}
