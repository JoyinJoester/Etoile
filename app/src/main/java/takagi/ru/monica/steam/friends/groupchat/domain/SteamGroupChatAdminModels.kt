package takagi.ru.monica.steam.friends.groupchat.domain

data class SteamGroupChatRoleActions(
    val roleId: String,
    val canCreateRenameDeleteChannel: Boolean = false,
    val canKick: Boolean = false,
    val canBan: Boolean = false,
    val canInvite: Boolean = false,
    val canChangeGroupMetadata: Boolean = false,
    val canChat: Boolean = true,
    val canViewHistory: Boolean = true,
    val canChangeGroupRoles: Boolean = false,
    val canChangeUserRoles: Boolean = false,
    val canMentionAll: Boolean = false,
    val canSetWatchingBroadcast: Boolean = false
)

data class SteamGroupChatRole(
    val roleId: String,
    val name: String,
    val ordinal: Int,
    val actions: SteamGroupChatRoleActions? = null
)

data class SteamGroupChatMember(
    val steamId: String,
    val state: Int = 0,
    val rank: Int = 0,
    val roleIds: List<String> = emptyList(),
    val kickExpiresAt: Long = 0L
)

data class SteamGroupChatInvite(
    val steamId: String,
    val actorSteamId: String = "",
    val invitedAt: Long = 0L
)

data class SteamGroupChatBan(
    val steamId: String,
    val actorSteamId: String = "",
    val bannedAt: Long = 0L,
    val reason: String = ""
)

data class SteamGroupChatInviteLink(
    val inviteCode: String,
    val creatorSteamId: String = "",
    val expiresAt: Long = 0L,
    val chatId: String = ""
) {
    /** Steam's shareable chat deep-link format. */
    val shareUrl: String get() = "https://s.team/chat/$inviteCode"
}

data class SteamGroupChatAdminSnapshot(
    val groupId: String,
    val members: List<SteamGroupChatMember> = emptyList(),
    val kicked: List<SteamGroupChatMember> = emptyList(),
    val roles: List<SteamGroupChatRole> = emptyList(),
    val invites: List<SteamGroupChatInvite> = emptyList(),
    val bans: List<SteamGroupChatBan> = emptyList(),
    val inviteLinks: List<SteamGroupChatInviteLink> = emptyList(),
    val fetchedAt: Long = 0L
)
