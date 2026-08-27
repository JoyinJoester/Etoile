package takagi.ru.monica.steam.friends.chat.richmedia.domain

import takagi.ru.monica.steam.data.SteamAccount

sealed interface SteamChatAttachmentTarget {
    data class Friend(val steamId: String) : SteamChatAttachmentTarget

    data class GroupRoom(
        val groupId: String,
        val chatId: String
    ) : SteamChatAttachmentTarget
}

fun interface SteamChatCatalogGateway {
    fun loadCatalog(account: SteamAccount): SteamChatRichMediaCatalog
}

interface SteamChatAttachmentGateway {
    suspend fun inspect(rawUri: String): SteamChatPendingAttachment

    suspend fun upload(
        account: SteamAccount,
        target: SteamChatAttachmentTarget,
        attachment: SteamChatPendingAttachment,
        spoiler: Boolean,
        onProgress: (Float) -> Unit = {}
    ): SteamChatUploadedAttachment
}
