package takagi.ru.monica.github.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import takagi.ru.monica.github.design.GithubExpressiveShapes

data class GithubShortcut(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val supporting: String? = null
)

/** Work destinations reflow with text size; a tile never truncates its action. */
@Composable
fun GithubShortcutGrid(
    shortcuts: List<GithubShortcut>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val readableWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = when {
            readableWidth >= 760.dp -> 4
            readableWidth >= 320.dp -> 2
            else -> 1
        }
        val scheme = MaterialTheme.colorScheme
        val tones = listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.surfaceContainerHigh to scheme.onSurface,
            scheme.tertiaryContainer to scheme.onTertiaryContainer
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            shortcuts.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEachIndexed { columnIndex, shortcut ->
                        val tone = tones[(rowIndex * columns + columnIndex) % tones.size]
                        Surface(
                            onClick = shortcut.onClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = GithubExpressiveShapes.container,
                            color = tone.first,
                            contentColor = tone.second
                        ) {
                            Column(
                                modifier = Modifier.heightIn(min = 116.dp).padding(16.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(shortcut.label, style = MaterialTheme.typography.titleMedium)
                                shortcut.supporting?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
