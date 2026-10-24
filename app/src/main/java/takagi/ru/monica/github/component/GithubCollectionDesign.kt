package takagi.ru.monica.github.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle

/** Shared reading surface for collections on both providers. Apply before clickable. */
@Composable
fun Modifier.githubCollectionSurface(): Modifier =
    if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
        padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    } else this

@Composable
fun Modifier.githubCollectionPadding(vertical: Dp = 16.dp): Modifier = padding(
    horizontal = if (LocalDesignStyle.current == DesignStyle.MATERIAL) 16.dp else 0.dp,
    vertical = vertical
)

@Composable
fun GithubCollectionDivider(modifier: Modifier = Modifier) {
    if (LocalDesignStyle.current != DesignStyle.MATERIAL) {
        HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}
