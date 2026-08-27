package takagi.ru.monica.steam.friends.chat.position.domain

internal data class SteamChatImeAnchorState(
    val imeVisible: Boolean = false,
    val wasAtBottomBeforeIme: Boolean = true,
    val followingIme: Boolean = false,
    val restored: Boolean = false
)

internal data class SteamChatImeAnchorResult(
    val state: SteamChatImeAnchorState,
    val shouldScrollToLatest: Boolean
)

internal fun reduceSteamChatImeAnchor(
    previous: SteamChatImeAnchorState,
    imeVisible: Boolean,
    atBottom: Boolean,
    restored: Boolean,
    hasMessages: Boolean
): SteamChatImeAnchorResult {
    val openingIme = !previous.imeVisible && imeVisible
    val followingIme = when {
        !imeVisible -> false
        openingIme -> previous.restored && previous.wasAtBottomBeforeIme
        else -> previous.followingIme
    }
    val bottomSnapshot = if (!imeVisible) atBottom else previous.wasAtBottomBeforeIme
    return SteamChatImeAnchorResult(
        state = SteamChatImeAnchorState(
            imeVisible = imeVisible,
            wasAtBottomBeforeIme = bottomSnapshot,
            followingIme = followingIme,
            restored = restored
        ),
        shouldScrollToLatest = restored && hasMessages && imeVisible && followingIme
    )
}
