package takagi.ru.monica.github.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveShapes

@Composable
fun GithubConversationContentEditor(
    sheetTitle: String,
    sheetSubtitle: String,
    title: String,
    body: String,
    titleLabel: String,
    bodyLabel: String,
    validationMessage: String,
    saveErrorMessage: String,
    maxTitleLength: Int,
    maxBodyLength: Int,
    isSaving: Boolean,
    validationError: Boolean,
    saveError: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    GithubModalBottomSheet(onDismissRequest = onDismiss) {
        GithubSheetHeader(
            title = sheetTitle,
            subtitle = sheetSubtitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                enabled = !isSaving,
                isError = validationError && title.isBlank(),
                label = { Text(titleLabel) },
                supportingText = {
                    GithubCharacterCounter(current = title.length, maximum = maxTitleLength)
                },
                maxLines = 3,
                shape = GithubExpressiveShapes.control,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                enabled = !isSaving,
                label = { Text(bodyLabel) },
                supportingText = {
                    GithubCharacterCounter(current = body.length, maximum = maxBodyLength)
                },
                minLines = 6,
                maxLines = 14,
                shape = GithubExpressiveShapes.control,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            if (validationError) {
                Text(
                    text = validationMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (saveError) {
                Text(
                    text = saveErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text(stringResource(R.string.github_cancel))
                }
                TextButton(onClick = onSave, enabled = !isSaving && title.isNotBlank()) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.github_save))
                }
            }
        }
    }
}
