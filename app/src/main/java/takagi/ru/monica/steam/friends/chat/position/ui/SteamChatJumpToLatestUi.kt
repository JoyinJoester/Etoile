package takagi.ru.monica.steam.friends.chat.position.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatJumpMessage
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatJumpToLatestState
import takagi.ru.monica.steam.friends.chat.position.domain.reduceSteamChatJumpToLatest

internal data class SteamChatJumpToLatestUiState(
    val visible: Boolean = false,
    val unreadBelowCount: Int = 0
)

@Composable
internal fun rememberSteamChatJumpToLatestState(
    conversationKey: String,
    initialAcknowledgedTimestamp: Long,
    messages: List<SteamChatJumpMessage>,
    lastVisibleMessageId: String?,
    messagesBelow: Int,
    restored: Boolean
): SteamChatJumpToLatestUiState {
    val acknowledgedAtEntry = remember(conversationKey) {
        initialAcknowledgedTimestamp.coerceAtLeast(0L)
    }
    var markerState by remember(conversationKey) {
        mutableStateOf(SteamChatJumpToLatestState())
    }
    val visibleThroughTimestamp = messages
        .lastOrNull { it.id == lastVisibleMessageId }
        ?.timestamp
        ?: 0L
    val result = reduceSteamChatJumpToLatest(
        previous = markerState,
        initialAcknowledgedTimestamp = acknowledgedAtEntry,
        visibleThroughTimestamp = visibleThroughTimestamp,
        messagesBelow = messagesBelow,
        restored = restored,
        messages = messages
    )
    SideEffect {
        if (markerState != result.state) markerState = result.state
    }
    return SteamChatJumpToLatestUiState(
        visible = result.visible,
        unreadBelowCount = result.unreadBelowCount
    )
}

private val SteamChatJumpButtonSlotSize = 72.dp

@Composable
internal fun SteamChatJumpToLatestButton(
    visible: Boolean,
    messagesBelow: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(
        targetState = visible,
        label = "steam-chat-jump-button"
    )
    val alpha by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "steam-chat-jump-alpha"
    ) { shown -> if (shown) 1f else 0f }
    val scale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "steam-chat-jump-scale"
    ) { shown -> if (shown) 1f else 0.82f }

    Box(
        modifier = modifier.size(SteamChatJumpButtonSlotSize),
        contentAlignment = Alignment.Center
    ) {
        if (transition.currentState || transition.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                        clip = false
                    },
                contentAlignment = Alignment.Center
            ) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Text(
                                text = if (messagesBelow > 999) "999+" else messagesBelow.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                ) {
                    Surface(
                        onClick = onClick,
                        enabled = visible,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        tonalElevation = 5.dp,
                        shadowElevation = 5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.steam_chat_jump_to_latest),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
