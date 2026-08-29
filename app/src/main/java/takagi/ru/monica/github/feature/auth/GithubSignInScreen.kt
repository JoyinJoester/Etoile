package takagi.ru.monica.github.feature.auth

import android.annotation.SuppressLint
import android.content.ClipData
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
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
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes

@Composable
fun GithubSignInScreen(
    state: GithubSessionUiState,
    onAction: (GithubSessionAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceFlowUnavailable = state.deviceSignIn is GithubDeviceSignInUiState.Unavailable
    var tokenFormExpanded by rememberSaveable { mutableStateOf(deviceFlowUnavailable) }
    var inAppWebSignIn by rememberSaveable { mutableStateOf(false) }
    val webWaiting = state.deviceSignIn as? GithubDeviceSignInUiState.Waiting
    BackHandler(enabled = inAppWebSignIn) { inAppWebSignIn = false }

    Box(modifier = modifier.fillMaxSize()) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_sign_in),
        subtitle = stringResource(R.string.github_sign_in_description),
        backContentDescription = stringResource(R.string.github_back),
        onBack = {
            onAction(GithubSessionAction.ClearForm)
            onBack()
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            if (deviceFlowUnavailable) {
                DeviceFlowUnavailableNotice()
                Spacer(Modifier.height(12.dp))
                TokenSignInForm(state = state, onAction = onAction)
            } else {
                DeviceSignInCard(
                    deviceSignIn = state.deviceSignIn,
                    onStart = {
                        onAction(GithubSessionAction.StartDeviceSignIn)
                        inAppWebSignIn = true
                    },
                    onCancel = { onAction(GithubSessionAction.CancelDeviceSignIn) },
                    onOpenUrl = onOpenUrl,
                    onOpenInApp = { inAppWebSignIn = true }
                )
                SignInDivider()
                TextButton(
                    onClick = { tokenFormExpanded = !tokenFormExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Text(
                        text = stringResource(R.string.github_use_personal_access_token),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Icon(
                        imageVector = if (tokenFormExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                AnimatedVisibility(
                    visible = tokenFormExpanded,
                    enter = fadeIn(GithubExpressiveMotion.standardTween()) + expandVertically(),
                    exit = fadeOut(GithubExpressiveMotion.quickTween()) + shrinkVertically()
                ) {
                    TokenSignInForm(state = state, onAction = onAction)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (inAppWebSignIn) {
        GithubDeviceLoginWebView(
            userCode = webWaiting?.userCode,
            url = webWaiting?.verificationUri,
            onClose = { inAppWebSignIn = false }
        )
    }
    }
}

@Composable
private fun DeviceSignInCard(
    deviceSignIn: GithubDeviceSignInUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenInApp: () -> Unit
) {
    val failed = deviceSignIn as? GithubDeviceSignInUiState.Failed
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = if (failed == null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        AnimatedContent(
            targetState = deviceSignIn,
            transitionSpec = {
                fadeIn(GithubExpressiveMotion.standardTween()) togetherWith
                    fadeOut(GithubExpressiveMotion.quickTween())
            },
            label = "github-device-sign-in"
        ) { targetState ->
            when (targetState) {
                GithubDeviceSignInUiState.Idle -> DeviceSignInIdle(onStart)
                GithubDeviceSignInUiState.Requesting -> DeviceSignInProgress(
                    title = stringResource(R.string.github_device_sign_in_preparing),
                    description = stringResource(R.string.github_device_sign_in_preparing_description)
                )
                is GithubDeviceSignInUiState.Waiting -> DeviceSignInWaiting(
                    state = targetState,
                    onCancel = onCancel,
                    onOpenUrl = onOpenUrl,
                    onOpenInApp = onOpenInApp
                )
                GithubDeviceSignInUiState.Verifying -> DeviceSignInProgress(
                    title = stringResource(R.string.github_device_sign_in_verifying),
                    description = stringResource(R.string.github_device_sign_in_verifying_description)
                )
                is GithubDeviceSignInUiState.Failed -> DeviceSignInFailure(targetState.error, onStart)
                GithubDeviceSignInUiState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun DeviceSignInIdle(onStart: () -> Unit) {
    Column(modifier = Modifier.padding(22.dp)) {
        Text(
            text = stringResource(R.string.github_sign_in_in_app),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.github_sign_in_in_app_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Text(stringResource(R.string.github_sign_in))
        }
    }
}

@Composable
private fun DeviceSignInProgress(title: String, description: String) {
    Row(
        modifier = Modifier.padding(22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun DeviceSignInWaiting(
    state: GithubDeviceSignInUiState.Waiting,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenInApp: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardLabel = stringResource(R.string.github_device_code_title)
    var copied by remember(state.userCode) { mutableStateOf(false) }
    val expiresAt = remember(state.expiresAtEpochMillis) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.expiresAtEpochMillis))
    }

    Column(modifier = Modifier.padding(22.dp)) {
        Text(
            text = stringResource(R.string.github_device_code_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.github_device_code_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.control,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.userCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText(clipboardLabel, state.userCode).toClipEntry()
                            )
                            copied = true
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text(
                        text = stringResource(
                            if (copied) R.string.github_device_code_copied else R.string.github_device_code_copy
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.github_device_code_expires_at, expiresAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onOpenInApp,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Icon(Icons.Default.Public, contentDescription = null)
            Text(
                text = stringResource(R.string.github_sign_in_in_app),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        OutlinedButton(
            onClick = { onOpenUrl(state.verificationUri) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Text(
                text = stringResource(R.string.github_sign_in_browser),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.github_device_code_waiting),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun DeviceSignInFailure(error: GithubDeviceSignInError, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(22.dp)) {
        Text(
            text = stringResource(R.string.github_device_sign_in_failed_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            text = stringResource(
                when (error) {
                    GithubDeviceSignInError.REQUEST_FAILED -> R.string.github_device_sign_in_request_failed
                    GithubDeviceSignInError.DENIED -> R.string.github_device_sign_in_denied
                    GithubDeviceSignInError.EXPIRED -> R.string.github_device_sign_in_expired
                    GithubDeviceSignInError.VERIFICATION_FAILED -> R.string.github_device_sign_in_verification_failed
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Text(stringResource(R.string.github_retry))
        }
    }
}

@Composable
private fun DeviceFlowUnavailableNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = stringResource(R.string.github_device_sign_in_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SignInDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.github_sign_in_alternative),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TokenSignInForm(
    state: GithubSessionUiState,
    onAction: (GithubSessionAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GithubSectionHeader(title = stringResource(R.string.github_personal_access_token_title))
        Text(
            text = stringResource(R.string.github_personal_access_token_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = state.tokenInput,
            onValueChange = { onAction(GithubSessionAction.TokenChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSubmitting,
            singleLine = true,
            label = { Text(stringResource(R.string.github_access_token)) },
            supportingText = {
                Text(
                    when (state.signInError) {
                        GithubSignInError.INVALID_TOKEN -> stringResource(R.string.github_invalid_token)
                        GithubSignInError.REQUEST_FAILED -> stringResource(R.string.github_sign_in_failed)
                        null -> stringResource(R.string.github_token_security_note)
                    }
                )
            },
            isError = state.signInError != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (state.tokenInput.isNotBlank() && !state.isSubmitting) {
                        onAction(GithubSessionAction.SignIn)
                    }
                }
            ),
            shape = GithubExpressiveShapes.control
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAction(GithubSessionAction.SignIn) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = state.tokenInput.isNotBlank() && !state.isSubmitting,
            shape = GithubExpressiveShapes.control
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = stringResource(R.string.github_signing_in),
                    modifier = Modifier.padding(start = 10.dp)
                )
            } else {
                Text(stringResource(R.string.github_sign_in))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GithubDeviceLoginWebView(
    userCode: String?,
    url: String?,
    onClose: () -> Unit
) {
    var progress by remember { mutableStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var injectedFor by remember { mutableStateOf<String?>(null) }

    // 设备码就绪后，若已停留在设备码页则立即补注入
    LaunchedEffect(userCode, currentUrl) {
        val view = webView ?: return@LaunchedEffect
        if (userCode != null && injectedFor != userCode &&
            currentUrl.contains("github.com/login/device")
        ) {
            injectDeviceCode(view, userCode)
            injectedFor = userCode
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
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            currentUrl = url.orEmpty()
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url.orEmpty()
                            if (userCode != null && injectedFor != userCode &&
                                url.orEmpty().contains("github.com/login/device")
                            ) {
                                injectDeviceCode(view, userCode)
                                injectedFor = userCode
                            }
                            // OAuth 授权页自动点"授权"，减少一次手动点击
                            if (url.orEmpty().contains("login/oauth/authorize")) {
                                view.evaluateJavascript(AUTO_AUTHORIZE_JS, null)
                            }
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
                .padding(bottom = 96.dp)
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
        // 底部悬浮工具栏（Monica Steam 风格）
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.github_web_back))
                }
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.github_web_forward))
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.github_web_refresh))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.github_web_sign_in_close))
                }
            }
        }
    }
}

private val AUTO_AUTHORIZE_JS = """
        (function() {
          try {
            var button = document.querySelector('button[name="authorize"], input[name="authorize"], button[value="authorize"]');
            if (button) { button.click(); }
          } catch (e) {}
        })();
    """.trimIndent()

// 在 GitHub 设备码页自动填入一次性代码并提交，用户只需完成登录与授权
private fun injectDeviceCode(view: WebView, userCode: String) {
    val safeCode = userCode.filter { it.isLetterOrDigit() || it == '-' }
    if (safeCode.isEmpty()) return
    val js = """
        (function() {
          try {
            var input = document.querySelector('input[name="otp"]') ||
                        document.querySelector('input[name="user_code"]') ||
                        document.querySelector('input[autocomplete="one-time-code"]');
            if (input && !input.value) {
              input.value = '$safeCode';
              input.dispatchEvent(new Event('input', { bubbles: true }));
              var form = input.closest('form');
              if (form) { form.submit(); }
            }
          } catch (e) {}
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}
