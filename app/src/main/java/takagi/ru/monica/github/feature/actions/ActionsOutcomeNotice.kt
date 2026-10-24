package takagi.ru.monica.github.feature.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/** Inspection reads remote state; acknowledgement only removes this local warning. */
@Composable
internal fun ActionsOutcomeNotice(subject: String, onCheck: () -> Unit, onAcknowledge: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(subject, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.github_actions_unconfirmed), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onCheck, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.github_actions_check_latest))
        }
        TextButton(onClick = onAcknowledge, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.github_actions_acknowledge))
        }
    }
}
