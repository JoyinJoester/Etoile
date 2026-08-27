package takagi.ru.monica.steam.friends.chat.richmedia.data

import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentTarget

internal fun SteamChatAttachmentTarget.commitFields(spoiler: Boolean): List<Pair<String, String>> =
    when (this) {
        is SteamChatAttachmentTarget.Friend -> {
            require(steamId.matches(Regex("7656119\\d{10}"))) {
                "Valid Steam friend ID required"
            }
            listOf(
                "friend_steamid" to steamId,
                "spoiler" to spoiler.asSteamFlag()
            )
        }

        is SteamChatAttachmentTarget.GroupRoom -> {
            require(groupId.isUnsignedSteamId()) { "Valid Steam group ID required" }
            require(chatId.isUnsignedSteamId()) { "Valid Steam chat ID required" }
            listOf(
                "chat_group_id" to groupId,
                "chat_id" to chatId,
                "spoiler" to spoiler.asSteamFlag()
            )
        }
    }

private fun String.isUnsignedSteamId(): Boolean =
    toBigIntegerOrNull()?.signum()?.let { it >= 0 } == true

private fun Boolean.asSteamFlag(): String = if (this) "1" else "0"
