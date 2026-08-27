package takagi.ru.monica.github.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import takagi.ru.monica.R

@Composable
fun GithubCharacterCounter(
    current: Int,
    maximum: Int,
    modifier: Modifier = Modifier
) {
    require(maximum > 0)
    val normalizedCurrent = current.coerceAtLeast(0)
    val accessibleDescription = stringResource(
        R.string.github_character_count_accessibility,
        normalizedCurrent,
        maximum
    )

    Text(
        text = stringResource(R.string.github_character_count, normalizedCurrent, maximum),
        style = MaterialTheme.typography.bodySmall,
        color = if (normalizedCurrent > maximum) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.clearAndSetSemantics {
            contentDescription = accessibleDescription
        }
    )
}
