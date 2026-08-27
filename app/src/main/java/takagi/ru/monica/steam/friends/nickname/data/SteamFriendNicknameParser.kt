package takagi.ru.monica.steam.friends.nickname.data

import takagi.ru.monica.steam.network.SteamProtoReader

internal object SteamFriendNicknameParser {
    fun parse(response: ByteArray): Map<String, String> {
        val nicknames = linkedMapOf<String, String>()
        SteamProtoReader(response).parseAll()
            .asSequence()
            .filter { it.number == NICKNAMES_FIELD }
            .mapNotNull { it.bytes }
            .forEach { encodedNickname ->
                val fields = SteamProtoReader(encodedNickname).parse()
                val accountId = fields[ACCOUNT_ID_FIELD]?.asFixed32UnsignedLong ?: return@forEach
                val nickname = fields[NICKNAME_FIELD]?.asString?.trim().orEmpty()
                if (accountId == 0L || nickname.isBlank()) return@forEach
                nicknames[(INDIVIDUAL_STEAM_ID_BASE + accountId).toString()] = nickname
            }
        return nicknames
    }

    private const val NICKNAMES_FIELD = 1
    private const val ACCOUNT_ID_FIELD = 1
    private const val NICKNAME_FIELD = 2
    private const val INDIVIDUAL_STEAM_ID_BASE = 76_561_197_960_265_728L
}
