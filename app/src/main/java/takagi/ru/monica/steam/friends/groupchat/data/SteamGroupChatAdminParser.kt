package takagi.ru.monica.steam.friends.groupchat.data

import takagi.ru.monica.steam.friends.chat.domain.steamId64FromAccountId
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatAdminSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatBan
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatInvite
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatInviteLink
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMember
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRole
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoleActions
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader

internal object SteamGroupChatAdminParser {
    fun parseGroupState(
        payload: ByteArray,
        fetchedAt: Long
    ): SteamGroupChatAdminSnapshot {
        val response = SteamProtoReader(payload).parse()
        val state = response[1]?.bytes?.let { SteamProtoReader(it).parseAll() }.orEmpty()
        return parseStateFields(state, fetchedAt)
    }

    fun parseInviteList(payload: ByteArray): List<SteamGroupChatInvite> =
        SteamProtoReader(payload).parseAll()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                val values = SteamProtoReader(field.bytes!!).parse()
                val accountId = values[1]?.asLong?.takeIf { it > 0 } ?: return@mapNotNull null
                SteamGroupChatInvite(
                    steamId = steamId64FromAccountId(accountId),
                    actorSteamId = values[2]?.asLong?.takeIf { it > 0 }
                        ?.let(::steamId64FromAccountId).orEmpty(),
                    invitedAt = values[3]?.asLong?.coerceAtLeast(0L) ?: 0L
                )
            }

    fun parseBanList(payload: ByteArray): List<SteamGroupChatBan> =
        SteamProtoReader(payload).parseAll()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                val values = SteamProtoReader(field.bytes!!).parse()
                val accountId = values[1]?.asLong?.takeIf { it > 0 } ?: return@mapNotNull null
                SteamGroupChatBan(
                    steamId = steamId64FromAccountId(accountId),
                    actorSteamId = values[2]?.asLong?.takeIf { it > 0 }
                        ?.let(::steamId64FromAccountId).orEmpty(),
                    bannedAt = values[3]?.asLong?.coerceAtLeast(0L) ?: 0L,
                    reason = values[4]?.asString.orEmpty()
                )
            }

    fun parseInviteLinks(payload: ByteArray): List<SteamGroupChatInviteLink> =
        SteamProtoReader(payload).parseAll()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field -> parseInviteLink(field.bytes!!) }

    fun parseCreatedInviteLink(
        payload: ByteArray,
        nowMillis: Long,
        chatId: String
    ): SteamGroupChatInviteLink? {
        val fields = SteamProtoReader(payload).parse()
        val code = fields[1]?.asString.orEmpty().takeIf(String::isNotBlank) ?: return null
        val seconds = fields[2]?.asLong?.coerceAtLeast(0L) ?: 0L
        return SteamGroupChatInviteLink(
            inviteCode = code,
            expiresAt = nowMillis + seconds * 1_000L,
            chatId = chatId
        )
    }

    private fun parseInviteLink(payload: ByteArray): SteamGroupChatInviteLink? {
        val fields = SteamProtoReader(payload).parse()
        val code = fields[1]?.asString.orEmpty().takeIf(String::isNotBlank) ?: return null
        val creator = fields[2]?.asFixed64UnsignedString.orEmpty()
            .takeIf(String::isNotBlank).orEmpty()
        val expires = fields[3]?.asLong?.coerceAtLeast(0L) ?: 0L
        return SteamGroupChatInviteLink(
            inviteCode = code,
            creatorSteamId = creator,
            expiresAt = when {
                expires <= 0L -> 0L
                expires < 10_000_000_000L -> expires * 1_000L
                else -> expires
            },
            chatId = fields[4]?.asUnsignedVarintString().orEmpty()
        )
    }

    private fun parseStateFields(
        state: List<SteamProtoField>,
        fetchedAt: Long
    ): SteamGroupChatAdminSnapshot {
        val header = state.firstOrNull { it.number == 1 && it.bytes != null }
            ?.bytes?.let { SteamProtoReader(it).parseAll() }.orEmpty()
        val groupId = header.firstOrNull { it.number == 1 }?.asUnsignedVarintString().orEmpty()
        val actions = header.filter { it.number == 19 && it.bytes != null }
            .mapNotNull { parseRoleActions(it.bytes!!) }
            .associateBy { it.roleId }
        val roles = header.filter { it.number == 18 && it.bytes != null }
            .mapNotNull { field ->
                val values = SteamProtoReader(field.bytes!!).parse()
                val id = values[1]?.asUnsignedVarintString().orEmpty()
                    .takeIf(String::isNotBlank) ?: return@mapNotNull null
                SteamGroupChatRole(
                    roleId = id,
                    name = values[2]?.asString.orEmpty().ifBlank { "Role" },
                    ordinal = values[3]?.asInt ?: 0,
                    actions = actions[id]
                )
            }
            .sortedWith(compareBy<SteamGroupChatRole> { it.ordinal }.thenBy { it.roleId })
        val members = state.filter { it.number == 2 && it.bytes != null }
            .mapNotNull { parseMember(it.bytes!!) }
        val kicked = state.filter { it.number == 7 && it.bytes != null }
            .mapNotNull { parseMember(it.bytes!!) }
        return SteamGroupChatAdminSnapshot(
            groupId = groupId,
            members = members,
            kicked = kicked,
            roles = roles,
            fetchedAt = fetchedAt
        )
    }

    private fun parseMember(payload: ByteArray): SteamGroupChatMember? {
        val values = SteamProtoReader(payload).parseAll()
        val accountId = values.firstOrNull { it.number == 1 }?.asLong?.takeIf { it > 0 }
            ?: return null
        val roleIds = values.filter { it.number == 7 }.flatMap { field ->
            when (field.wireType) {
                0 -> listOf(field.asUnsignedVarintString())
                2 -> SteamProtoReader.decodePackedVarints(field.bytes ?: byteArrayOf())
                    .map(::unsignedString)
                else -> emptyList()
            }
        }.distinct()
        return SteamGroupChatMember(
            steamId = steamId64FromAccountId(accountId),
            state = values.firstOrNull { it.number == 3 }?.asInt ?: 0,
            rank = values.firstOrNull { it.number == 4 }?.asInt ?: 0,
            roleIds = roleIds,
            kickExpiresAt = values.firstOrNull { it.number == 6 }?.asLong?.coerceAtLeast(0L) ?: 0L
        )
    }

    private fun parseRoleActions(payload: ByteArray): SteamGroupChatRoleActions? {
        val values = SteamProtoReader(payload).parse()
        val roleId = values[1]?.asUnsignedVarintString().orEmpty()
            .takeIf(String::isNotBlank) ?: return null
        return SteamGroupChatRoleActions(
            roleId = roleId,
            canCreateRenameDeleteChannel = values[2]?.asBool == true,
            canKick = values[3]?.asBool == true,
            canBan = values[4]?.asBool == true,
            canInvite = values[5]?.asBool == true,
            canChangeGroupMetadata = values[6]?.asBool == true,
            canChat = values[7]?.asBool != false,
            canViewHistory = values[8]?.asBool != false,
            canChangeGroupRoles = values[9]?.asBool == true,
            canChangeUserRoles = values[10]?.asBool == true,
            canMentionAll = values[11]?.asBool == true,
            canSetWatchingBroadcast = values[12]?.asBool == true
        )
    }

    private fun SteamProtoField.asUnsignedVarintString(): String =
        java.lang.Long.toUnsignedString(asLong)

    private fun unsignedString(value: Long): String = java.lang.Long.toUnsignedString(value)
}
