package takagi.ru.monica.steam.friends.groupchat.avatar.domain

import takagi.ru.monica.steam.data.SteamAccount

interface SteamGroupAvatarUploadGateway {
    suspend fun upload(account: SteamAccount, rawUri: String): ByteArray
}
