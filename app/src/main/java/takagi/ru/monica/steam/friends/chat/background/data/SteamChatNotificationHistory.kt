package takagi.ru.monica.steam.friends.chat.background.data

internal data class SteamChatNotificationHistoryUpdate(
    val claimed: Boolean,
    val encodedHistory: String
)

internal object SteamChatNotificationHistory {
    fun claim(
        encodedHistory: String?,
        notificationKey: String,
        maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES
    ): SteamChatNotificationHistoryUpdate {
        val current = decode(encodedHistory)
        if (notificationKey in current) {
            return SteamChatNotificationHistoryUpdate(
                claimed = false,
                encodedHistory = encode(current)
            )
        }
        val bounded = (current + notificationKey)
            .takeLast(maximumEntries.coerceAtLeast(1))
        return SteamChatNotificationHistoryUpdate(
            claimed = true,
            encodedHistory = encode(bounded)
        )
    }

    fun release(
        encodedHistory: String?,
        notificationKey: String
    ): String = encode(
        decode(encodedHistory).filterNot { existing -> existing == notificationKey }
    )

    internal fun decode(encodedHistory: String?): List<String> = encodedHistory
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { it.matches(KEY_PATTERN) }
        .distinct()
        .toList()

    private fun encode(entries: List<String>): String = entries.joinToString("\n")

    private val KEY_PATTERN = Regex("[0-9a-f]{64}")
    private const val DEFAULT_MAXIMUM_ENTRIES = 192
}
