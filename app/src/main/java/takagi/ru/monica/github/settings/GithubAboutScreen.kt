package takagi.ru.monica.github.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.R
import takagi.ru.monica.github.component.*
import takagi.ru.monica.ui.components.MarkdownPreviewText

@Composable
fun GithubAboutScreen(onBack: () -> Unit, onOpenDocument: (String) -> Unit, onOpenExternal: (String) -> Unit) {
    GithubDetailScaffold(title = stringResource(R.string.github_about), onBack = onBack,
        backContentDescription = stringResource(R.string.github_back), showGithubServiceStatus = false) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.github_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.titleMedium)
                    Text(BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.github_about_build, BuildConfig.GIT_SHA), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.github_about_unofficial), style = MaterialTheme.typography.bodyMedium)
            GithubPreferenceGroup {
                GithubPreferenceRow(Icons.Default.PrivacyTip, stringResource(R.string.github_privacy),
                    stringResource(R.string.github_legal_offline), onClick = { onOpenDocument("privacy") })
                GithubPreferenceGroupDivider()
                GithubPreferenceRow(Icons.Default.Description, stringResource(R.string.github_license), "GNU GPL v3",
                    onClick = { onOpenDocument("license") })
                GithubPreferenceGroupDivider()
                GithubPreferenceRow(Icons.Default.Description, stringResource(R.string.github_third_party_notices),
                    stringResource(R.string.github_legal_offline), onClick = { onOpenDocument("notices") })
                GithubPreferenceGroupDivider()
                GithubPreferenceRow(Icons.Default.Code, stringResource(R.string.github_source_code), "JoyinJoester/Etoile",
                    onClick = { onOpenExternal("https://github.com/JoyinJoester/Etoile") })
                GithubPreferenceGroupDivider()
                GithubPreferenceRow(Icons.Default.Feedback, stringResource(R.string.github_feedback),
                    stringResource(R.string.github_feedback_privacy), onClick = { onOpenExternal("https://github.com/JoyinJoester/Etoile/issues") })
            }
        }
    }
}

@Composable
fun GithubLegalScreen(document: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val chinese = LocalConfiguration.current.locales[0]?.language == "zh"
    val path = when (document) {
        "license" -> "legal/LICENSE.txt"
        "notices" -> "legal/THIRD_PARTY_NOTICES.md"
        else -> if (chinese) "legal/privacy-zh.md" else "legal/privacy-en.md"
    }
    val title = when (document) {
        "license" -> R.string.github_license
        "notices" -> R.string.github_third_party_notices
        else -> R.string.github_privacy
    }
    var attempt by remember(path) { mutableIntStateOf(0) }
    val content by key(path) {
        produceState<Result<String>?>(null, attempt) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
            }
        }
    }
    val scroll = key(path) { rememberScrollState() }
    GithubDetailScaffold(title = stringResource(title), onBack = onBack,
        backContentDescription = stringResource(R.string.github_back), showGithubServiceStatus = false) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp)) {
            val result = content
            when {
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                result.isFailure -> GithubMessageState(stringResource(R.string.github_legal_load_failed),
                    actionLabel = stringResource(R.string.github_retry), onAction = { attempt++ })
                document == "license" -> SelectionContainer { Text(result.getOrThrow(), style = MaterialTheme.typography.bodyMedium) }
                else -> MarkdownPreviewText(result.getOrThrow(), documentBaseUrl = "https://github.com/JoyinJoester/Etoile/blob/main/THIRD_PARTY_NOTICES.md")
            }
        }
    }
}
