package takagi.ru.monica.steam.friends.groupchat.data

import java.util.Locale
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatChannelCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatAdminSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessagePage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatInviteLink
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoleActions
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReactionType
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReportReason
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamGroupChatService(
    private val cm: SteamCmGateway = SteamCmClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SteamGroupChatGateway {
    override fun getMyGroups(account: SteamAccount): List<SteamGroupChatSummary> =
        SteamGroupChatParser.parseGroups(call(account, "GetMyChatRoomGroups", SteamProtoWriter()))

    override fun getGroupAvatarUrl(account: SteamAccount, groupId: String): String =
        SteamGroupChatParser.parseGroupStateAvatarUrl(
            call(account, "GetChatRoomGroupState", SteamProtoWriter().apply {
                writeUint64(1, groupId.requireUnsignedId("group"))
            })
        )

    override fun getHistory(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        before: SteamGroupChatHistoryBoundary?
    ): SteamGroupChatMessagePage = SteamGroupChatParser.parseHistory(
        payload = call(account, "GetMessageHistory", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            before?.let {
                writeVarint(3, it.timestamp)
                writeVarint(4, it.ordinal.toLong())
            }
            writeVarint(7, 50L)
        }),
        groupId = groupId,
        chatId = chatId
    )

    override fun sendMessage(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        body: String
    ): SteamGroupChatMessage {
        val normalized = body.trim()
        require(normalized.isNotBlank()) { "Steam group message is empty" }
        val response = call(account, "SendChatMessage", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            writeString(3, normalized)
            writeBool(4, true)
        })
        return SteamGroupChatParser.parseSentMessage(response, groupId, chatId, account.steamId, normalized)
    }

    override fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String {
        val name = request.name.trim()
        require(name.length in 1..64) { "Steam group name must contain 1-64 characters" }
        val response = call(account, "CreateChatRoomGroup", SteamProtoWriter().apply {
            writeString(3, name)
            request.inviteeSteamIds.distinct().forEach { writeFixed64(4, it.requireSteamId64()) }
        })
        return SteamGroupChatParser.parseCreatedGroupId(response)
            .takeIf(String::isNotBlank) ?: error("Steam did not return the created group ID")
    }

    override fun inviteFriend(account: SteamAccount, groupId: String, chatId: String, steamId: String) {
        call(account, "InviteFriendToChatRoomGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeFixed64(2, steamId.requireSteamId64())
            writeUint64(3, chatId.requireUnsignedId("chat"))
        })
    }

    override fun updateGroup(
        account: SteamAccount,
        groupId: String,
        name: String,
        tagline: String
    ) {
        val normalizedName = name.trim()
        require(normalizedName.length in 1..64) { "Steam group name must contain 1-64 characters" }
        call(account, "RenameChatRoomGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, normalizedName)
        })
        call(account, "SetChatRoomGroupTagline", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, tagline.trim().take(128))
        })
    }

    override fun updateGroupAvatar(account: SteamAccount, groupId: String, avatarSha: ByteArray) {
        require(avatarSha.size == 20) { "Steam group avatar SHA must contain 20 bytes" }
        call(account, "SetChatRoomGroupAvatar", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeBytes(2, avatarSha)
        })
    }

    override fun createChannel(
        account: SteamAccount,
        groupId: String,
        request: SteamGroupChatChannelCreateRequest
    ): SteamGroupChatRoom {
        val name = request.name.trim()
        require(name.length in 1..64) { "Steam channel name must contain 1-64 characters" }
        val response = call(account, "CreateChatRoom", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, name)
            writeBool(3, request.allowVoice)
        })
        return SteamGroupChatParser.parseCreatedRoom(response)
            ?: error("Steam did not return the created channel")
    }

    override fun deleteChannel(account: SteamAccount, groupId: String, chatId: String) {
        call(account, "DeleteChatRoom", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
        })
    }

    override fun renameChannel(account: SteamAccount, groupId: String, chatId: String, name: String) {
        val normalized = name.trim()
        require(normalized.length in 1..64) { "Steam channel name must contain 1-64 characters" }
        call(account, "RenameChatRoom", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            writeString(3, normalized)
        })
    }

    override fun reorderChannel(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        moveAfterChatId: String?
    ) {
        call(account, "ReorderChatRoom", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            moveAfterChatId?.takeIf(String::isNotBlank)?.let {
                writeUint64(3, it.requireUnsignedId("chat"))
            }
        })
    }

    override fun getAdminSnapshot(account: SteamAccount, groupId: String): SteamGroupChatAdminSnapshot {
        val normalizedGroupId = groupId.requireUnsignedId("group")
        val state = SteamGroupChatAdminParser.parseGroupState(
            call(account, "GetChatRoomGroupState", SteamProtoWriter().apply {
                writeUint64(1, normalizedGroupId)
            }),
            fetchedAt = nowMillis()
        )
        val invites = runCatching {
            SteamGroupChatAdminParser.parseInviteList(
                call(account, "GetInviteList", SteamProtoWriter().apply {
                    writeUint64(1, normalizedGroupId)
                })
            )
        }.getOrDefault(emptyList())
        val bans = runCatching {
            SteamGroupChatAdminParser.parseBanList(
                call(account, "GetBanList", SteamProtoWriter().apply {
                    writeUint64(1, normalizedGroupId)
                })
            )
        }.getOrDefault(emptyList())
        val links = runCatching {
            SteamGroupChatAdminParser.parseInviteLinks(
                call(account, "GetInviteLinksForGroup", SteamProtoWriter().apply {
                    writeUint64(1, normalizedGroupId)
                })
            )
        }.getOrDefault(emptyList())
        return state.copy(
            groupId = state.groupId.ifBlank { groupId },
            invites = invites,
            bans = bans,
            inviteLinks = links
        )
    }

    override fun createInviteLink(
        account: SteamAccount,
        groupId: String,
        secondsValid: Long,
        chatId: String?
    ): SteamGroupChatInviteLink {
        require(secondsValid in 0L..UINT32_MAX) { "Steam invite duration is out of range" }
        val response = call(account, "CreateInviteLink", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeVarint(2, secondsValid)
            chatId?.takeIf(String::isNotBlank)?.let {
                writeUint64(3, it.requireUnsignedId("chat"))
            }
        })
        return SteamGroupChatAdminParser.parseCreatedInviteLink(
            response,
            nowMillis = nowMillis(),
            chatId = chatId.orEmpty()
        ) ?: error("Steam did not return an invite code")
    }

    override fun deleteInviteLink(account: SteamAccount, groupId: String, inviteCode: String) {
        require(inviteCode.isNotBlank()) { "Steam invite code required" }
        call(account, "DeleteInviteLink", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, inviteCode)
        })
    }

    override fun revokeInvite(account: SteamAccount, groupId: String, steamId: String) {
        call(account, "RevokeInviteToGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeFixed64(2, steamId.requireSteamId64())
        })
    }

    override fun setUserBanState(account: SteamAccount, groupId: String, steamId: String, banned: Boolean) {
        call(account, "SetUserBanState", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeFixed64(2, steamId.requireSteamId64())
            writeBool(3, banned)
        })
    }

    override fun kickUser(account: SteamAccount, groupId: String, steamId: String, expirationSeconds: Int) {
        require(expirationSeconds >= 0) { "Steam kick expiration must be non-negative" }
        call(account, "KickUserFromGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeFixed64(2, steamId.requireSteamId64())
            writeVarint(3, expirationSeconds.toLong())
        })
    }

    override fun muteUser(account: SteamAccount, groupId: String, steamId: String, expirationSeconds: Int) {
        require(expirationSeconds >= 0) { "Steam mute expiration must be non-negative" }
        call(account, "MuteUserInGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeFixed64(2, steamId.requireSteamId64())
            writeVarint(3, expirationSeconds.toLong())
        })
    }

    override fun createRole(account: SteamAccount, groupId: String, name: String) {
        val normalized = name.trim()
        require(normalized.length in 1..64) { "Steam role name must contain 1-64 characters" }
        call(account, "CreateRole", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, normalized)
        })
    }

    override fun renameRole(account: SteamAccount, groupId: String, roleId: String, name: String) {
        val normalized = name.trim()
        require(normalized.length in 1..64) { "Steam role name must contain 1-64 characters" }
        call(account, "RenameRole", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, roleId.requireUnsignedId("role"))
            writeString(3, normalized)
        })
    }

    override fun deleteRole(account: SteamAccount, groupId: String, roleId: String) {
        call(account, "DeleteRole", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, roleId.requireUnsignedId("role"))
        })
    }

    override fun replaceRoleActions(
        account: SteamAccount,
        groupId: String,
        actions: SteamGroupChatRoleActions
    ) {
        call(account, "ReplaceRoleActions", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, actions.roleId.requireUnsignedId("role"))
            writeMessage(4, SteamProtoWriter().apply {
                writeUint64(1, actions.roleId.requireUnsignedId("role"))
                writeBool(2, actions.canCreateRenameDeleteChannel)
                writeBool(3, actions.canKick)
                writeBool(4, actions.canBan)
                writeBool(5, actions.canInvite)
                writeBool(6, actions.canChangeGroupMetadata)
                writeBool(7, actions.canChat)
                writeBool(8, actions.canViewHistory)
                writeBool(9, actions.canChangeGroupRoles)
                writeBool(10, actions.canChangeUserRoles)
                writeBool(11, actions.canMentionAll)
                writeBool(12, actions.canSetWatchingBroadcast)
            })
        })
    }

    override fun addRoleToUser(account: SteamAccount, groupId: String, roleId: String, steamId: String) {
        call(account, "AddRoleToUser", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(3, roleId.requireUnsignedId("role"))
            writeFixed64(4, steamId.requireSteamId64())
        })
    }

    override fun removeRoleFromUser(account: SteamAccount, groupId: String, roleId: String, steamId: String) {
        call(account, "DeleteRoleFromUser", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(3, roleId.requireUnsignedId("role"))
            writeFixed64(4, steamId.requireSteamId64())
        })
    }

    override fun updateMessageReaction(
        account: SteamAccount,
        message: SteamGroupChatMessage,
        type: SteamGroupChatReactionType,
        reaction: String,
        add: Boolean
    ) {
        requireConfirmedMessage(message)
        val normalized = reaction.trim().trim(':')
        require(normalized.isNotBlank()) { "Steam reaction required" }
        call(account, "UpdateMessageReaction", SteamProtoWriter().apply {
            writeUint64(1, message.groupId.requireUnsignedId("group"))
            writeUint64(2, message.chatId.requireUnsignedId("chat"))
            writeVarint(3, message.timestamp)
            writeVarint(4, message.ordinal.toLong())
            writeVarint(5, if (type == SteamGroupChatReactionType.STICKER) 2L else 1L)
            writeString(6, if (type == SteamGroupChatReactionType.EMOTICON) ":$normalized:" else normalized)
            writeBool(7, add)
        })
    }

    override fun reportMessage(
        account: SteamAccount,
        message: SteamGroupChatMessage,
        reason: SteamGroupChatReportReason
    ) {
        requireConfirmedMessage(message)
        require(message.senderSteamId != account.steamId) { "Own Steam messages cannot be reported" }
        call(account, "ReportMessage", SteamProtoWriter().apply {
            writeUint64(1, message.groupId.requireUnsignedId("group"))
            writeUint64(2, message.chatId.requireUnsignedId("chat"))
            writeFixed64(3, message.senderSteamId.requireSteamId64())
            writeVarint(4, message.timestamp)
            writeVarint(5, message.ordinal.toLong())
            writeVarint(6, reason.steamValue.toLong())
            writeString(7, message.body)
            writeString(8, Locale.getDefault().language.ifBlank { "en" })
            writeVarint(9, CHAT_ROOM_MESSAGE_SUBJECT_TYPE)
        })
    }

    override fun deleteMessage(account: SteamAccount, message: SteamGroupChatMessage) {
        requireConfirmedMessage(message)
        call(account, "DeleteChatMessages", SteamProtoWriter().apply {
            writeUint64(1, message.groupId.requireUnsignedId("group"))
            writeUint64(2, message.chatId.requireUnsignedId("chat"))
            writeMessage(3, SteamProtoWriter().apply {
                writeVarint(1, message.timestamp)
                writeVarint(2, message.ordinal.toLong())
            })
        })
    }

    override fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long) {
        if (timestamp <= 0L) return
        call(account, "AckChatMessage", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            writeVarint(3, timestamp)
        })
    }

    private fun call(account: SteamAccount, method: String, request: SteamProtoWriter): ByteArray =
        cm.callService(account, "ChatRoom.$method#1", request.toByteArray())

    private fun String.requireSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "Valid Steam ID required" }
        return toLong()
    }

    private fun requireConfirmedMessage(message: SteamGroupChatMessage) {
        require(message.timestamp > 0L && message.ordinal != Int.MAX_VALUE) {
            "Steam group message has not been confirmed"
        }
    }

    private fun String.requireUnsignedId(label: String): String = apply {
        require(toBigIntegerOrNull()?.signum()?.let { it >= 0 } == true) { "Valid Steam $label ID required" }
    }

    private companion object {
        const val UINT32_MAX = 4_294_967_295L
        const val CHAT_ROOM_MESSAGE_SUBJECT_TYPE = 5L
    }
}
