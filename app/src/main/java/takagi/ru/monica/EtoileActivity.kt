package takagi.ru.monica

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import takagi.ru.monica.github.navigation.GithubLinkRouter
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.ui.theme.EtoileTheme

class EtoileActivity : BaseMonicaActivity() {
    private val pendingGithubUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingGithubUrl.value = intent?.data?.toString()?.takeIf { GithubLinkRouter.parse(it) != null }
        setContent {
            val settings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            val githubUrl by pendingGithubUrl.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            EtoileTheme(
                darkTheme = darkTheme,
                oledPureBlackEnabled = settings.oledPureBlackEnabled,
                colorScheme = settings.colorScheme,
                customPrimaryColor = settings.customPrimaryColor,
                customSecondaryColor = settings.customSecondaryColor,
                customTertiaryColor = settings.customTertiaryColor,
                customNeutralColor = settings.customNeutralColor,
                customNeutralVariantColor = settings.customNeutralVariantColor
            ) {
                GithubApp(
                    settings = settings,
                    settingsManager = settingsManager,
                    initialGithubUrl = githubUrl,
                    onGithubUrlConsumed = { url ->
                        if (pendingGithubUrl.value == url) pendingGithubUrl.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingGithubUrl.value = intent.data?.toString()?.takeIf { GithubLinkRouter.parse(it) != null }
    }
}
