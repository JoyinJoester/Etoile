package takagi.ru.monica

import androidx.compose.runtime.Composable
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.github.EtoileGithubApp
import takagi.ru.monica.utils.SettingsManager

/**
 * Stable entry point kept in the legacy package while GitHub features live in
 * their own maintainable presentation/data/domain packages.
 */
@Composable
fun GithubApp(
    settings: AppSettings,
    settingsManager: SettingsManager,
    initialGithubUrl: String? = null,
    onGithubUrlConsumed: (String) -> Unit = {}
) {
    EtoileGithubApp(
        settings = settings,
        settingsManager = settingsManager,
        initialGithubUrl = initialGithubUrl,
        onGithubUrlConsumed = onGithubUrlConsumed
    )
}
