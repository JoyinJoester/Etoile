package takagi.ru.monica.steam.friends.cache

internal fun <T> boundedSteamMessageCache(
    messages: List<T>,
    maximumRecentMessages: Int = DEFAULT_MAXIMUM_RECENT_MESSAGES,
    maximumRetainedUnconfirmed: Int = DEFAULT_MAXIMUM_RETAINED_UNCONFIRMED,
    retainOutsideRecentWindow: (T) -> Boolean
): List<T> {
    val recentLimit = maximumRecentMessages.coerceAtLeast(1)
    val recentStart = (messages.size - recentLimit).coerceAtLeast(0)
    if (recentStart == 0) return messages

    val retainedOlderIndices = messages.indices
        .asSequence()
        .take(recentStart)
        .filter { index -> retainOutsideRecentWindow(messages[index]) }
        .toList()
        .takeLast(maximumRetainedUnconfirmed.coerceAtLeast(0))
        .toHashSet()
    return messages.filterIndexed { index, _ ->
        index >= recentStart || index in retainedOlderIndices
    }
}

internal const val DEFAULT_MAXIMUM_RECENT_MESSAGES = 500
internal const val DEFAULT_MAXIMUM_RETAINED_UNCONFIRMED = 64
