package takagi.ru.monica.steam.web.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class SteamWebBrowserController internal constructor() {
    internal var webView by mutableStateOf<WebView?>(null)
        private set

    val attached: Boolean
        get() = webView != null

    fun goBack() {
        webView?.takeIf(WebView::canGoBack)?.goBack()
    }

    fun goForward() {
        webView?.takeIf(WebView::canGoForward)?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    internal fun attach(view: WebView) {
        webView = view
    }

    internal fun detach(view: WebView) {
        if (webView === view) webView = null
    }
}

@Composable
fun rememberSteamWebBrowserController(): SteamWebBrowserController =
    remember { SteamWebBrowserController() }
