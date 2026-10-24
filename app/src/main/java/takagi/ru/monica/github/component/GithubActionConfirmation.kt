package takagi.ru.monica.github.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

internal class GithubActionConfirmation {
    var requested by mutableStateOf(false)
        private set

    fun request() { requested = true }
    fun cancel() { requested = false }
    fun confirm(action: () -> Unit) {
        if (!requested) return
        requested = false
        action()
    }
}

/** Returns a request callback. Only the dialog's explicit confirmation runs the action. */
@Composable
fun githubConfirmedAction(
    title: String,
    message: String,
    subject: String,
    enabled: Boolean,
    onConfirm: () -> Unit
): () -> Unit = githubConfirmedAction(title, message, subject, enabled, null, onConfirm)

@Composable
fun githubConfirmedAction(
    title: String,
    message: String,
    subject: String,
    enabled: Boolean,
    confirmationKey: Any?,
    onConfirm: () -> Unit
): () -> Unit {
    // A changed target, action or permission invalidates the previous confirmation.
    // Do not persist pending destructive actions across process recreation.
    val confirmation = remember(title, subject, enabled, confirmationKey) { GithubActionConfirmation() }
    if (confirmation.requested && enabled) {
        AlertDialog(
            onDismissRequest = confirmation::cancel,
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(subject, style = MaterialTheme.typography.titleSmall)
                    Text(message)
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    if (enabled) confirmation.confirm(onConfirm)
                }) { Text(title, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = confirmation::cancel) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
    return { if (enabled) confirmation.request() }
}
