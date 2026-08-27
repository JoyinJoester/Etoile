package takagi.ru.monica.steam.web.ui

import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import takagi.ru.monica.steam.web.domain.SteamWebClientMode
import takagi.ru.monica.steam.web.domain.SteamWebClientPolicy

internal fun WebView.configureForSteam(
    clientMode: SteamWebClientMode,
    backgroundColor: Int
) {
    setBackgroundColor(backgroundColor)
    alpha = 0f
    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)

    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        defaultTextEncodingName = "utf-8"
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        userAgentString = SteamWebClientPolicy.userAgent(
            mode = clientMode,
            defaultUserAgent = userAgentString
        )
        SteamWebClientPolicy.displayPolicy(clientMode).let { displayPolicy ->
            useWideViewPort = displayPolicy.useWideViewPort
            loadWithOverviewMode = displayPolicy.loadWithOverviewMode
            textZoom = displayPolicy.textZoomPercent
        }
        cacheMode = WebSettings.LOAD_DEFAULT
        databaseEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = true
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true
        setGeolocationEnabled(false)
        loadsImagesAutomatically = true
        blockNetworkImage = false
    }

    isVerticalScrollBarEnabled = true
    isHorizontalScrollBarEnabled = false
    scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    isScrollbarFadingEnabled = true
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    isFocusable = true
    isFocusableInTouchMode = true

    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
}
