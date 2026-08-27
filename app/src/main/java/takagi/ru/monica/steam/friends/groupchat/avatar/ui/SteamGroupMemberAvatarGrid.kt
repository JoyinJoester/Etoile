package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend

@Composable
internal fun SteamGroupMemberAvatarGrid(
    members: List<SteamFriend>,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val visibleMembers = remember(members) {
        members.distinctBy(SteamFriend::steamId).take(MAX_GROUP_AVATAR_MEMBERS)
    }
    val rows = remember(visibleMembers.size) { steamGroupAvatarRows(visibleMembers.size) }
    val accessibilityModifier = contentDescription?.let { description ->
        Modifier.semantics { this.contentDescription = description }
    } ?: Modifier

    Layout(
        content = {
            visibleMembers.forEach { member ->
                SteamGroupMemberAvatarTile(member)
            }
        },
        modifier = modifier
            .then(accessibilityModifier)
            .padding(3.dp)
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        if (measurables.isEmpty() || rows.isEmpty()) {
            layout(width, height) {}
        } else {
            val rowCount = rows.size
            val columnCount = rows.maxOrNull()?.coerceAtLeast(1) ?: 1
            val gap = (min(width, height) * GROUP_AVATAR_GAP_RATIO)
                .roundToInt()
                .coerceAtLeast(1)
            val tileWidth = (width - gap * (columnCount - 1)) / columnCount
            val tileHeight = (height - gap * (rowCount - 1)) / rowCount
            val tileSize = min(tileWidth, tileHeight).coerceAtLeast(1)
            val placeables = measurables.map { measurable ->
                measurable.measure(Constraints.fixed(tileSize, tileSize))
            }
            val gridHeight = rowCount * tileSize + (rowCount - 1) * gap

            layout(width, height) {
                var memberIndex = 0
                var y = (height - gridHeight) / 2
                rows.forEach { membersInRow ->
                    val rowWidth = membersInRow * tileSize + (membersInRow - 1) * gap
                    var x = (width - rowWidth) / 2
                    repeat(membersInRow) {
                        placeables[memberIndex].placeRelative(x, y)
                        memberIndex++
                        x += tileSize + gap
                    }
                    y += tileSize + gap
                }
            }
        }
    }
}

@Composable
private fun SteamGroupMemberAvatarTile(member: SteamFriend) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = member.avatarUrl) {
        value = member.avatarUrl
            .takeIf(String::isNotBlank)
            ?.let { loadSteamRemoteImage(context, it) }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(2.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        val avatar = bitmap
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = member.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

internal fun steamGroupAvatarRows(memberCount: Int): List<Int> = when (
    memberCount.coerceIn(0, MAX_GROUP_AVATAR_MEMBERS)
) {
    0 -> emptyList()
    1 -> listOf(1)
    2 -> listOf(2)
    3 -> listOf(1, 2)
    4 -> listOf(2, 2)
    5 -> listOf(2, 3)
    6 -> listOf(3, 3)
    7 -> listOf(1, 3, 3)
    8 -> listOf(2, 3, 3)
    else -> listOf(3, 3, 3)
}

private const val MAX_GROUP_AVATAR_MEMBERS = 9
private const val GROUP_AVATAR_GAP_RATIO = 0.045f
