package takagi.ru.monica.steam.friends.groupchat.domain

import takagi.ru.monica.steam.data.SteamAccount

interface SteamGroupChatGateway {
    fun getMyGroups(account: SteamAccount): List<SteamGroupChatSummary>
    /**
     * Resolves the avatar from the full group state. Steam occasionally omits
     * avatar fields from GetMyChatRoomGroups while still returning them from
     * GetChatRoomGroupState.
     */
    fun getGroupAvatarUrl(account: SteamAccount, groupId: String): String = ""
    fun getHistory(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        before: SteamGroupChatHistoryBoundary? = null
    ): SteamGroupChatMessagePage
    fun sendMessage(account: SteamAccount, groupId: String, chatId: String, body: String): SteamGroupChatMessage
    fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String
    fun inviteFriend(account: SteamAccount, groupId: String, chatId: String, steamId: String)
    fun updateGroup(account: SteamAccount, groupId: String, name: String, tagline: String) {
        throw UnsupportedOperationException("Updating Steam group metadata is not supported")
    }
    fun updateGroupAvatar(account: SteamAccount, groupId: String, avatarSha: ByteArray) {
        throw UnsupportedOperationException("Updating Steam group avatar is not supported")
    }
    fun createChannel(
        account: SteamAccount,
        groupId: String,
        request: SteamGroupChatChannelCreateRequest
    ): SteamGroupChatRoom {
        throw UnsupportedOperationException("Creating Steam group channels is not supported")
    }
    fun deleteChannel(account: SteamAccount, groupId: String, chatId: String) {
        throw UnsupportedOperationException("Deleting Steam group channels is not supported")
    }
    fun renameChannel(account: SteamAccount, groupId: String, chatId: String, name: String) {
        throw UnsupportedOperationException("Renaming Steam group channels is not supported")
    }
    fun reorderChannel(account: SteamAccount, groupId: String, chatId: String, moveAfterChatId: String?) {
        throw UnsupportedOperationException("Reordering Steam group channels is not supported")
    }
    fun getAdminSnapshot(account: SteamAccount, groupId: String): SteamGroupChatAdminSnapshot {
        throw UnsupportedOperationException("Steam group administration is not supported")
    }
    fun createInviteLink(
        account: SteamAccount,
        groupId: String,
        secondsValid: Long,
        chatId: String?
    ): SteamGroupChatInviteLink {
        throw UnsupportedOperationException("Creating Steam group invite links is not supported")
    }
    fun deleteInviteLink(account: SteamAccount, groupId: String, inviteCode: String) {
        throw UnsupportedOperationException("Deleting Steam group invite links is not supported")
    }
    fun revokeInvite(account: SteamAccount, groupId: String, steamId: String) {
        throw UnsupportedOperationException("Revoking Steam group invitations is not supported")
    }
    fun setUserBanState(account: SteamAccount, groupId: String, steamId: String, banned: Boolean) {
        throw UnsupportedOperationException("Changing Steam group bans is not supported")
    }
    fun kickUser(account: SteamAccount, groupId: String, steamId: String, expirationSeconds: Int) {
        throw UnsupportedOperationException("Kicking Steam group members is not supported")
    }
    fun muteUser(account: SteamAccount, groupId: String, steamId: String, expirationSeconds: Int) {
        throw UnsupportedOperationException("Muting Steam group members is not supported")
    }
    fun createRole(account: SteamAccount, groupId: String, name: String) {
        throw UnsupportedOperationException("Creating Steam group roles is not supported")
    }
    fun renameRole(account: SteamAccount, groupId: String, roleId: String, name: String) {
        throw UnsupportedOperationException("Renaming Steam group roles is not supported")
    }
    fun deleteRole(account: SteamAccount, groupId: String, roleId: String) {
        throw UnsupportedOperationException("Deleting Steam group roles is not supported")
    }
    fun replaceRoleActions(account: SteamAccount, groupId: String, actions: SteamGroupChatRoleActions) {
        throw UnsupportedOperationException("Updating Steam group role permissions is not supported")
    }
    fun addRoleToUser(account: SteamAccount, groupId: String, roleId: String, steamId: String) {
        throw UnsupportedOperationException("Assigning Steam group roles is not supported")
    }
    fun removeRoleFromUser(account: SteamAccount, groupId: String, roleId: String, steamId: String) {
        throw UnsupportedOperationException("Removing Steam group roles is not supported")
    }
    fun updateMessageReaction(
        account: SteamAccount,
        message: SteamGroupChatMessage,
        type: SteamGroupChatReactionType,
        reaction: String,
        add: Boolean
    ) {
        throw UnsupportedOperationException("Steam group message reactions are not supported")
    }
    fun reportMessage(
        account: SteamAccount,
        message: SteamGroupChatMessage,
        reason: SteamGroupChatReportReason
    ) {
        throw UnsupportedOperationException("Reporting Steam group messages is not supported")
    }
    fun deleteMessage(account: SteamAccount, message: SteamGroupChatMessage) {
        throw UnsupportedOperationException("Deleting Steam group messages is not supported")
    }
    fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long)
}
