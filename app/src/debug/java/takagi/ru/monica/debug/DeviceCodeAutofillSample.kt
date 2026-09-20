package takagi.ru.monica.debug

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import takagi.ru.monica.github.feature.auth.DeviceCodeChip
import takagi.ru.monica.github.feature.auth.injectDeviceCode

/** Local HTML only, using the production chip and WebView fill script. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DeviceCodeAutofillSample() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageStatus by remember { mutableStateOf("Loading local page") }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.blockNetworkLoads = true
                    val html = """
                        <title>Waiting for tap</title>
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>body{font:18px sans-serif;background:#141820;color:white;padding:12px}
                        input{width:8%;font-size:22px;padding:8px 1px;color:white;background:#222;border:1px solid #aaa}
                        button{margin-top:28px;padding:16px;font-size:18px}</style>
                        <h2>Device code · local test</h2><p>Tap the app code below to replace all eight cells.</p>
                        <form onsubmit="event.preventDefault();document.title='Continued by user';document.querySelector('#status').textContent=document.title">
                        ${(1..8).joinToString("") { "<input maxlength='1' value='X' aria-label='Code $it'>" }}
                        <button>Continue</button></form><p id="status">Waiting for tap</p>
                        <script>document.addEventListener('input',function(){
                        document.title=Array.from(document.querySelectorAll('input')).map(x=>x.value).join('');
                        document.querySelector('#status').textContent=document.title;
                        });</script>
                    """.trimIndent()
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                            val body = if (request.url.toString() == "https://github.com/login/device") html else ""
                            return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(body.toByteArray()))
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView, title: String) { pageStatus = title }
                    }
                    loadUrl("https://github.com/login/device")
                    webView = this
                }
            }
        )
        Text(pageStatus)
        DeviceCodeChip(code = "AB12-CD34", onFill = { result ->
            webView?.let { injectDeviceCode(it, "AB12-CD34", result) } ?: result(false)
        })
    }
}
