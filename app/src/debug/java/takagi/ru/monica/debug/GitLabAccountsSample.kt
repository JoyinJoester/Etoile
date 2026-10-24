package takagi.ru.monica.debug

import androidx.compose.runtime.*
import takagi.ru.monica.gitlab.domain.GitLabAccount
import takagi.ru.monica.gitlab.domain.GitLabAccounts
import takagi.ru.monica.gitlab.feature.GitLabAccountsScreen
import takagi.ru.monica.gitlab.feature.GitLabAccountsUiState

/** Local UI fixture; no real credentials or authentication requests. */
@Composable
fun GitLabAccountsSample(onBack: () -> Unit, report: (String) -> Unit) {
    val account = remember { GitLabAccount(42, "https://gitlab.com/", "sample-user", "Sample GitLab User", "https://gitlab.com/sample-user") }
    var state by remember { mutableStateOf(GitLabAccountsUiState(accounts = GitLabAccounts(listOf(account), account.key))) }
    GitLabAccountsScreen(
        state = state,
        onInstanceChanged = { state = state.copy(instanceInput = it) },
        onTokenChanged = { state = state.copy(tokenInput = it) },
        onClientIdChanged = { state = state.copy(clientIdInput = it) },
        onTokenSignIn = { report("GitLab token sign-in requested") },
        onBrowserSignIn = { report("GitLab browser sign-in requested") },
        onCancelSignIn = { state = state.copy(tokenInput = "", browserUrl = null) },
        onActivate = { report("GitLab account verified") },
        onRemove = { state = state.copy(accounts = GitLabAccounts()); report("GitLab local account removed") },
        onOpenBrowser = { report("Open GitLab"); true },
        onBrowserUnavailable = {},
        onBack = onBack
    )
}
