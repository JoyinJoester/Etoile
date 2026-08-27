package takagi.ru.monica.steam.web.domain

enum class SteamWebFailureKind {
    NETWORK,
    HTTP,
    SSL,
    UNSAFE_NAVIGATION,
    RENDERER
}

data class SteamWebPageFailure(
    val kind: SteamWebFailureKind,
    val description: String? = null,
    val failingUrl: String? = null,
    val statusCode: Int? = null
)

data class SteamWebBrowserState(
    val currentUrl: String,
    val pageTitle: String? = null,
    val progress: Int = 0,
    val loading: Boolean = false,
    val contentVisible: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val failure: SteamWebPageFailure? = null,
    val rendererRecoveryCount: Int = 0
) {
    val normalizedProgress: Float
        get() = progress.coerceIn(0, 100) / 100f
}

sealed interface SteamWebNavigationCommand {
    data class LoadUrl(val url: String) : SteamWebNavigationCommand

    data class PostUrl(
        val url: String,
        val body: ByteArray
    ) : SteamWebNavigationCommand
}

fun interface SteamWebPageAutomation {
    fun onPageFinished(url: String): SteamWebNavigationCommand?
}
