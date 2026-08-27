package takagi.ru.monica.github.feature.auth

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.github.component.GithubModalBottomSheet
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.component.GithubSheetHeader
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes

@Composable
fun GithubSignInSheet(
    state: GithubSessionUiState,
    onAction: (GithubSessionAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val deviceFlowUnavailable = state.deviceSignIn is GithubDeviceSignInUiState.Unavailable
    var tokenFormExpanded by rememberSaveable { mutableStateOf(deviceFlowUnavailable) }
    val dismiss = {
        onAction(GithubSessionAction.ClearForm)
        onDismiss()
    }

    GithubModalBottomSheet(onDismissRequest = dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            SignInHeader()
            Spacer(Modifier.height(20.dp))

            if (deviceFlowUnavailable) {
                DeviceFlowUnavailableNotice()
                TokenSignInForm(state = state, onAction = onAction)
            } else {
                DeviceSignInCard(
                    deviceSignIn = state.deviceSignIn,
                    onStart = { onAction(GithubSessionAction.StartDeviceSignIn) },
                    onCancel = { onAction(GithubSessionAction.CancelDeviceSignIn) },
                    onOpenUrl = onOpenUrl
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
        }
    }
}

@Composable
private fun SignInHeader() {
    GithubSheetHeader(
        title = stringResource(R.string.github_sign_in),
        subtitle = stringResource(R.string.github_sign_in_description)
    ) {
        Surface(
            shape = GithubExpressiveShapes.control,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp).size(28.dp)
            )
        }
    }
}

@Composable
private fun DeviceSignInCard(
    deviceSignIn: GithubDeviceSignInUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit
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
                    onOpenUrl = onOpenUrl
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
            text = stringResource(R.string.github_device_sign_in_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.github_device_sign_in_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Text(stringResource(R.string.github_device_sign_in_action))
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
    onOpenUrl: (String) -> Unit
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
            onClick = { onOpenUrl(state.verificationUri) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            shape = GithubExpressiveShapes.control
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Text(
                text = stringResource(R.string.github_device_code_open_github),
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
