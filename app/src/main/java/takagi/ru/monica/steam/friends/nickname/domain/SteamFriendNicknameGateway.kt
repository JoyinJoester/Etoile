package takagi.ru.monica.steam.friends.nickname.domain

import takagi.ru.monica.steam.data.SteamAccount

fun interface SteamFriendNicknameGateway {
    fun fetch(account: SteamAccount): Map<String, String>
}
