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
import androidx.compose.foundation.layout.heightIn
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

@Composable
fun GithubSignInScreen(
    state: GithubSessionUiState,
    onAction: (GithubSessionAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBrowser: ((String) -> Boolean)? = null
) {
    val browserFlowAvailable = state.browserSignIn !is GithubBrowserSignInUiState.Unavailable
    val interactiveFlowUnavailable = !browserFlowAvailable &&
        state.deviceSignIn is GithubDeviceSignInUiState.Unavailable
    var tokenFormExpanded by rememberSaveable { mutableStateOf(interactiveFlowUnavailable) }
    var inAppWebSignIn by rememberSaveable { mutableStateOf(false) }
    var externalSignIn by rememberSaveable { mutableStateOf(true) }
    var browserOpenFailed by rememberSaveable { mutableStateOf(false) }
    var notificationPosted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val openBrowser: (String) -> Unit = { url ->
        externalSignIn = true
        inAppWebSignIn = false
        browserOpenFailed = !(onOpenBrowser?.invoke(url) ?: GithubSignInBrowser.open(context, url))
    }
    val webWaiting = state.deviceSignIn as? GithubDeviceSignInUiState.Waiting
    val browserOpening = state.browserSignIn as? GithubBrowserSignInUiState.Opening
    val browserWaiting = state.browserSignIn as? GithubBrowserSignInUiState.Waiting
    val browserAuthorizationUrl = browserOpening?.authorizationUrl ?: browserWaiting?.authorizationUrl
    val closeSignIn = {
        onAction(GithubSessionAction.ClearForm)
        onBack()
    }
    BackHandler {
        if (inAppWebSignIn) inAppWebSignIn = false else closeSignIn()
    }

    LaunchedEffect(browserOpening?.authorizationUrl) {
        val url = browserOpening?.authorizationUrl ?: return@LaunchedEffect
        // Consume the launch state before leaving the app, preventing reopen loops on return.
        onAction(GithubSessionAction.BrowserSignInOpened)
        if (externalSignIn) openBrowser(url) else inAppWebSignIn = true
    }

    LaunchedEffect(webWaiting?.userCode, webWaiting?.expiresAtEpochMillis, externalSignIn) {
        val waiting = webWaiting
        notificationPosted = if (externalSignIn && waiting != null) {
            GithubDeviceCodeNotification.show(context, waiting.userCode, waiting.expiresAtEpochMillis)
        } else {
            GithubDeviceCodeNotification.cancel(context)
            false
        }
    }
    DisposableEffect(context) {
        onDispose { GithubDeviceCodeNotification.cancel(context) }
    }

    // A callback can arrive through Android's intent route as well as from
    // WebView navigation. Dismiss the embedded page once verification starts;
    // otherwise a failed callback could reveal an unrelated device-flow page.
    LaunchedEffect(state.browserSignIn) {
        if (state.browserSignIn is GithubBrowserSignInUiState.Verifying ||
            state.browserSignIn is GithubBrowserSignInUiState.Failed
        ) {
            inAppWebSignIn = false
        }
    }

    LaunchedEffect(state.deviceSignIn) {
        if (!browserFlowAvailable &&
            (state.deviceSignIn is GithubDeviceSignInUiState.Verifying ||
                state.deviceSignIn is GithubDeviceSignInUiState.Failed ||
                state.deviceSignIn is GithubDeviceSignInUiState.Idle)
        ) {
            inAppWebSignIn = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_sign_in),
        subtitle = stringResource(R.string.github_sign_in_description),
        backContentDescription = stringResource(R.string.github_back),
        onBack = closeSignIn,
        modifier = modifier,
        contentMaxWidth = takagi.ru.monica.github.design.GithubAdaptiveLayout.formMaxWidth
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            if (interactiveFlowUnavailable) {
                DeviceFlowUnavailableNotice()
                Spacer(Modifier.height(12.dp))
                TokenSignInForm(state = state, onAction = onAction)
            } else {
                if (browserFlowAvailable) {
                    BrowserSignInCard(
                        browserSignIn = state.browserSignIn,
                        enabled = !state.isSubmitting && !state.isAccountActionRunning,
                        onStart = {
                            externalSignIn = true
                            browserOpenFailed = false
                            onAction(GithubSessionAction.StartBrowserSignIn)
                        },
                        onStartInApp = {
                            externalSignIn = false
                            browserOpenFailed = false
                            onAction(GithubSessionAction.StartBrowserSignIn)
                        },
                        onCancel = { onAction(GithubSessionAction.CancelBrowserSignIn) },
                        onReopen = { browserAuthorizationUrl?.let(openBrowser) },
                        onOpenInApp = { externalSignIn = false; browserOpenFailed = false; inAppWebSignIn = true }
                    )
                } else {
                    DeviceSignInCard(
                        deviceSignIn = state.deviceSignIn,
                        enabled = !state.isSubmitting && !state.isAccountActionRunning,
                        onStart = {
                            externalSignIn = true
                            browserOpenFailed = false
                            onAction(GithubSessionAction.StartDeviceSignIn)
                        },
                        onStartInApp = {
                            externalSignIn = false
                            browserOpenFailed = false
                            onAction(GithubSessionAction.StartDeviceSignIn)
                            inAppWebSignIn = true
                        },
                        onCancel = { onAction(GithubSessionAction.CancelDeviceSignIn) },
                        onOpenUrl = openBrowser,
                        notificationPosted = notificationPosted,
                        onOpenInApp = { externalSignIn = false; browserOpenFailed = false; inAppWebSignIn = true }
                    )
                }
                if (browserOpenFailed) {
                    Text(stringResource(R.string.github_login_browser_unavailable),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
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
    if (inAppWebSignIn && (browserAuthorizationUrl != null || !browserFlowAvailable)) {
        GithubLoginWebView(
            userCode = if (browserAuthorizationUrl != null) null else webWaiting?.userCode,
            url = browserAuthorizationUrl ?: webWaiting?.verificationUri,
            onOAuthCallback = { callbackUrl ->
                inAppWebSignIn = false
                onAction(GithubSessionAction.BrowserSignInCallback(callbackUrl))
            },
            onOpenExternal = onOpenUrl,
            onClose = { inAppWebSignIn = false }
        )
    }
    }
}

@Composable
private fun BrowserSignInCard(
    browserSignIn: GithubBrowserSignInUiState,
    enabled: Boolean,
    onStart: () -> Unit,
    onStartInApp: () -> Unit,
    onCancel: () -> Unit,
    onReopen: () -> Unit,
    onOpenInApp: () -> Unit
) {
    val failed = browserSignIn as? GithubBrowserSignInUiState.Failed
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GithubExpressiveShapes.prominent,
        color = if (failed == null) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer
    ) {
        val enterFade = GithubExpressiveMotion.standardTween<Float>()
        val exitFade = GithubExpressiveMotion.quickTween<Float>()
        AnimatedContent(
            targetState = browserSignIn,
            transitionSpec = { fadeIn(enterFade) togetherWith fadeOut(exitFade) },
            label = "github-browser-sign-in"
        ) { targetState ->
            when (targetState) {
                GithubBrowserSignInUiState.Idle -> BrowserSignInIdle(onStart, onStartInApp, enabled)
                is GithubBrowserSignInUiState.Opening -> DeviceSignInProgress(
                    title = stringResource(R.string.github_browser_sign_in_opening),
                    description = stringResource(R.string.github_browser_sign_in_opening_description)
                )
                is GithubBrowserSignInUiState.Waiting -> BrowserSignInWaiting(
                    onCancel = onCancel,
                    onReopen = onReopen,
                    onOpenInApp = onOpenInApp
                )
                GithubBrowserSignInUiState.Verifying -> DeviceSignInProgress(
                    title = stringResource(R.string.github_device_sign_in_verifying),
                    description = stringResource(R.string.github_device_sign_in_verifying_description)
                )
                is GithubBrowserSignInUiState.Failed -> BrowserSignInFailure(targetState.error, onStart, onStartInApp, enabled)
                GithubBrowserSignInUiState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun BrowserSignInIdle(onStart: () -> Unit, onStartInApp: () -> Unit, enabled: Boolean) {
    Column(modifier = Modifier.padding(22.dp)) {
        Text(
            text = stringResource(R.string.github_login_method_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.github_login_web_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        GithubLoginMethodButtons(onStart, onStartInApp, enabled)
    }
}

@Composable
private fun BrowserSignInWaiting(onCancel: () -> Unit, onReopen: () -> Unit, onOpenInApp: () -> Unit) {
    Column(modifier = Modifier.padding(22.dp)) {
        Text(
            text = stringResource(R.string.github_browser_sign_in_waiting),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.github_login_web_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        GithubLoginMethodButtons(onReopen, onOpenInApp, true)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun BrowserSignInFailure(error: GithubBrowserSignInError, onRetry: () -> Unit, onRetryInApp: () -> Unit, enabled: Boolean) {
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
                    GithubBrowserSignInError.DENIED -> R.string.github_browser_sign_in_denied
                    GithubBrowserSignInError.INVALID_CALLBACK -> R.string.github_browser_sign_in_invalid_callback
                    GithubBrowserSignInError.REQUEST_FAILED -> R.string.github_device_sign_in_request_failed
                    GithubBrowserSignInError.VERIFICATION_FAILED -> R.string.github_device_sign_in_verification_failed
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        GithubLoginMethodButtons(onRetry, onRetryInApp, enabled)
    }
}

@Composable
private fun DeviceSignInCard(
    deviceSignIn: GithubDeviceSignInUiState,
    enabled: Boolean,
    onStart: () -> Unit,
    onStartInApp: () -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    notificationPosted: Boolean,
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
        val enterFade = GithubExpressiveMotion.standardTween<Float>()
        val exitFade = GithubExpressiveMotion.quickTween<Float>()
        AnimatedContent(
            targetState = deviceSignIn,
            transitionSpec = { fadeIn(enterFade) togetherWith fadeOut(exitFade) },
            label = "github-device-sign-in"
        ) { targetState ->
            when (targetState) {
                GithubDeviceSignInUiState.Idle -> DeviceSignInIdle(onStart, onStartInApp, enabled)
                GithubDeviceSignInUiState.Requesting -> DeviceSignInProgress(
                    title = stringResource(R.string.github_device_sign_in_preparing),
                    description = stringResource(R.string.github_device_sign_in_preparing_description)
                )
                is GithubDeviceSignInUiState.Waiting -> DeviceSignInWaitingCompact(
                    waiting = targetState,
                    onCancel = onCancel,
                    onReopen = onOpenInApp,
                    onOpenBrowser = { onOpenUrl(targetState.verificationUri) },
                    notificationPosted = notificationPosted
                )
                GithubDeviceSignInUiState.Verifying -> DeviceSignInProgress(
                    title = stringResource(R.string.github_device_sign_in_verifying),
                    description = stringResource(R.string.github_device_sign_in_verifying_description)
                )
                is GithubDeviceSignInUiState.Failed -> DeviceSignInFailure(targetState.error, onStart, onStartInApp, enabled)
                GithubDeviceSignInUiState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun DeviceSignInIdle(onStart: () -> Unit, onStartInApp: () -> Unit, enabled: Boolean) {
    Column(modifier = Modifier.padding(22.dp)) {
        Text(
            text = stringResource(R.string.github_login_method_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.github_login_device_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        GithubLoginMethodButtons(onStart, onStartInApp, enabled)
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
private fun DeviceSignInWaitingCompact(
    waiting: GithubDeviceSignInUiState.Waiting,
    onCancel: () -> Unit,
    onReopen: () -> Unit,
    onOpenBrowser: () -> Unit,
    notificationPosted: Boolean
) {
    val context = LocalContext.current
    var copied by remember(waiting.userCode) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(3000); copied = false } }
    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.github_login_code_page_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.github_login_code_instructions), style = MaterialTheme.typography.bodyMedium)
        Surface(onClick = {
            GithubDeviceCodeNotification.copy(context, waiting.userCode)
            copied = true
        }, modifier = Modifier.fillMaxWidth(), shape = GithubExpressiveShapes.control,
            color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(if (copied) R.string.github_device_code_copied else R.string.github_login_code_tap_copy),
                    style = MaterialTheme.typography.labelMedium)
                Text(waiting.userCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            }
        }
        if (notificationPosted) Text(stringResource(R.string.github_login_notification_sent), style = MaterialTheme.typography.bodySmall)
        GithubLoginMethodButtons(onOpenBrowser, onReopen, true)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun GithubLoginMethodButtons(onBrowser: () -> Unit, onEmbedded: () -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBrowser, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = GithubExpressiveShapes.control) {
            Text(stringResource(R.string.github_login_external))
        }
        OutlinedButton(onClick = onEmbedded, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = GithubExpressiveShapes.control) {
            Text(stringResource(R.string.github_login_embedded))
        }
    }
}
@Composable
private fun DeviceSignInFailure(error: GithubDeviceSignInError, onRetry: () -> Unit, onRetryInApp: () -> Unit, enabled: Boolean) {
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
        GithubLoginMethodButtons(onRetry, onRetryInApp, enabled)
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
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
