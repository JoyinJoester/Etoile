package takagi.ru.monica.debug

import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.feature.auth.*

/** Deterministic login UI state. No token exchange or remote writes. */
@Composable
internal fun BrowserLoginSample(oauth: Boolean, realBrowser: Boolean = false, failBrowser: Boolean = false) {
    val context = LocalContext.current
    val initial = remember {
        GithubSessionUiState(session = GithubSession.SignedOut,
            browserSignIn = if (oauth) GithubBrowserSignInUiState.Idle else GithubBrowserSignInUiState.Unavailable,
            deviceSignIn = if (oauth) GithubDeviceSignInUiState.Unavailable else GithubDeviceSignInUiState.Idle)
    }
    var state by remember { mutableStateOf(initial) }
    var starts by remember { mutableIntStateOf(0) }
    var launches by remember { mutableIntStateOf(0) }
    var clipboard by remember { mutableStateOf("") }
    val fakeBrowser: (String) -> Boolean = { launches++; !failBrowser }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("Local test: starts=$starts browser=$launches")
        Row {
            TextButton(onClick = {
                clipboard = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            }) { Text("Read copied test code") }
            TextButton(onClick = {
                state = initial
            }) { Text("Complete local login") }
        }
        Text(clipboard)
        GithubSignInScreen(state = state, modifier = Modifier.weight(1f),
            onOpenBrowser = if (realBrowser) null else fakeBrowser,
            onOpenUrl = {}, onBack = {}, onAction = { action ->
                state = when (action) {
                    GithubSessionAction.StartDeviceSignIn -> {
                        starts++
                        state.copy(deviceSignIn = GithubDeviceSignInUiState.Waiting(
                            "AB12-CD34", "https://github.com/login/device", System.currentTimeMillis() + 300_000L))
                    }
                    GithubSessionAction.StartBrowserSignIn -> {
                        starts++
                        state.copy(browserSignIn = GithubBrowserSignInUiState.Opening(
                            "https://github.com/login/oauth/authorize?client_id=local-test-only&state=local-test-only"))
                    }
                    GithubSessionAction.BrowserSignInOpened -> state.copy(browserSignIn =
                        GithubBrowserSignInUiState.Waiting((state.browserSignIn as GithubBrowserSignInUiState.Opening).authorizationUrl))
                    GithubSessionAction.CancelBrowserSignIn, GithubSessionAction.CancelDeviceSignIn, GithubSessionAction.ClearForm -> initial
                    else -> state
                }
            })
    }
}
