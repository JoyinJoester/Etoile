package takagi.ru.monica.steam.friends.chat.position.domain

internal data class SteamChatJumpMessage(
    val id: String,
    val timestamp: Long,
    val incoming: Boolean
)

internal data class SteamChatJumpToLatestState(
    val initialized: Boolean = false,
    val readThroughTimestamp: Long = 0L,
    val latestMessageId: String? = null,
    val wasAtBottom: Boolean = true
)

internal data class SteamChatJumpToLatestResult(
    val state: SteamChatJumpToLatestState,
    val unreadBelowCount: Int
) {
    val visible: Boolean get() = unreadBelowCount > 0
}

internal fun reduceSteamChatJumpToLatest(
    previous: SteamChatJumpToLatestState,
    initialAcknowledgedTimestamp: Long,
    visibleThroughTimestamp: Long,
    messagesBelow: Int,
    restored: Boolean,
    messages: List<SteamChatJumpMessage>
): SteamChatJumpToLatestResult {
    if (!restored || messages.isEmpty()) {
        return SteamChatJumpToLatestResult(previous, unreadBelowCount = 0)
    }

    val latestMessage = messages.last()
    val latestChanged = previous.initialized && previous.latestMessageId != latestMessage.id
    val followedNewMessageFromBottom = latestChanged && previous.wasAtBottom
    var readThroughTimestamp = if (previous.initialized) {
        previous.readThroughTimestamp
    } else {
        initialAcknowledgedTimestamp.coerceAtLeast(0L)
    }
    readThroughTimestamp = maxOf(
        readThroughTimestamp,
        visibleThroughTimestamp.coerceAtLeast(0L)
    )
    if (messagesBelow == 0 || followedNewMessageFromBottom) {
        readThroughTimestamp = maxOf(readThroughTimestamp, latestMessage.timestamp)
    }

    val state = SteamChatJumpToLatestState(
        initialized = true,
        readThroughTimestamp = readThroughTimestamp,
        latestMessageId = latestMessage.id,
        wasAtBottom = messagesBelow == 0
    )
    val unreadBelowCount = messages
        .takeLast(messagesBelow.coerceIn(0, messages.size))
        .count { message ->
            message.incoming &&
                message.timestamp > 0L &&
                message.timestamp > readThroughTimestamp
        }
    return SteamChatJumpToLatestResult(state, unreadBelowCount)
}
