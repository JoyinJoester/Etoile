package takagi.ru.monica.steam.community.eligibility.data

import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityAccountInfo
import takagi.ru.monica.steam.network.SteamProtoReader

internal object SteamCommunityAccountInfoParser {
    private const val LIMITED_USER_FLAG = 4096L
    private const val FORCED_LIMITED_USER_FLAG = 8192L

    fun parse(body: ByteArray): SteamCommunityAccountInfo {
        val fields = SteamProtoReader(body).parse()
        val flagsField = fields[7]
        val flags = flagsField?.asLong ?: 0L
        return SteamCommunityAccountInfo(
            countryCode = fields[2]?.asString.orEmpty().trim().uppercase(),
            accountFlags = flags,
            limited = flagsField?.let {
                flags and (LIMITED_USER_FLAG or FORCED_LIMITED_USER_FLAG) != 0L
            }
        )
    }
}
