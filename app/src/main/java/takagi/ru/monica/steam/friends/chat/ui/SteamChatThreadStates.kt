package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R

@Composable
internal fun ChatDateSeparator(timestampSeconds: Long) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(timestampSeconds * 1_000L)),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
internal fun ChatThreadEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.steam_chat_thread_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun sameChatDay(firstSeconds: Long, secondSeconds: Long): Boolean {
    val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
    return formatter.format(Date(firstSeconds * 1_000L)) ==
        formatter.format(Date(secondSeconds * 1_000L))
}
