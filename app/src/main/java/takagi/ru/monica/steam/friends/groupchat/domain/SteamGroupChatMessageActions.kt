package takagi.ru.monica.steam.friends.groupchat.domain

enum class SteamGroupChatReportReason(val steamValue: Int) {
    HARASSMENT(3),
    SCAM(13),
    SPAM(28),
    OTHER(2)
}
