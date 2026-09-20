package takagi.ru.monica.github.feature.auth

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubTechnicalLabel
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.navigation.GithubLoginNavigation
import takagi.ru.monica.github.navigation.GithubLoginNavigationPolicy

/**
 * 应用内登录 WebView:导航拦截、设备码自动注入与悬浮工具栏。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GithubLoginWebView(
    userCode: String?,
    url: String?,
    onOAuthCallback: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit
) {
    var progress by remember { mutableStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    webViewClient = object : WebViewClient() {
                        internal fun handleNavigation(targetUrl: String): Boolean = when (
                            GithubLoginNavigationPolicy.classify(targetUrl)
                        ) {
                            GithubLoginNavigation.CALLBACK -> {
                                onOAuthCallback(targetUrl)
                                true
                            }
                            GithubLoginNavigation.IN_WEBVIEW -> false
                            GithubLoginNavigation.EXTERNAL -> {
                                onOpenExternal(targetUrl)
                                true
                            }
                            GithubLoginNavigation.BLOCK -> true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = handleNavigation(request.url.toString())

                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                            handleNavigation(url)

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            currentUrl = url.orEmpty()
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url.orEmpty()

                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    WebView.setWebContentsDebuggingEnabled(false)
                    loadUrl(url ?: "https://github.com/login/device")
                    webView = this
                }
            },
            update = { view ->
                // url 从 null（请求中）变为就绪时重定向到设备码页
                if (url != null && currentUrl.isEmpty()) {
                    view.loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )
        if (progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            )
        }
        if (userCode == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        // 底部悬浮工具栏（Monica Steam 风格）。登录代码独立成行，避免把
        // 工具栏撑得过宽；形状取全局 MaterialTheme.shapes.extraLarge：
        // Material=28dp 圆角、Nothing=16dp 利落圆角、Miuix=squircle。
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (userCode != null) {
                    DeviceCodeChip(
                        code = userCode,
                        onFill = { onResult ->
                            val view = webView
                            if (view == null) onResult(false)
                            else injectDeviceCode(view, userCode, onResult)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.github_web_back))
                    }
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.github_web_forward))
                    }
                    IconButton(onClick = { webView?.reload() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.github_web_refresh))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.github_web_sign_in_close))
                    }
                }
            }
        }
    }
}

/** 点击代码填入网页；右侧按钮保留单独复制功能。 */
@Composable
internal fun DeviceCodeChip(
    code: String,
    onFill: ((Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var feedback by remember(code) { mutableStateOf<Int?>(null) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(3000)
            feedback = null
        }
    }
    Surface(
        onClick = {
            onFill { filled ->
                feedback = if (filled) R.string.github_device_code_filled else R.string.github_device_code_fill_unavailable
            }
        },
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(feedback ?: R.string.github_device_code_tap_to_fill),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Device Code", code))
                feedback = R.string.github_device_code_copied
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.github_device_code_copy),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Fill only after the user taps the code. Continue/authorization remain webpage actions.
internal fun injectDeviceCode(view: WebView, userCode: String, onResult: (Boolean) -> Unit) {
    val code = GithubDeviceCodeAutofill.normalize(userCode)
    if (code == null || !GithubDeviceCodeAutofill.isDevicePage(view.url)) {
        onResult(false)
        return
    }
    val script = runCatching {
        view.context.assets.open("github-device-code.js").bufferedReader().use { it.readText() }
            .replace("__ETOILE_DEVICE_CODE__", org.json.JSONObject.quote(code))
    }.getOrNull()
    if (script == null) {
        onResult(false)
        return
    }
    view.evaluateJavascript(script) { result -> onResult(result == "true") }
}
