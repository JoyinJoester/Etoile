package takagi.ru.monica.steam.friends.presentation

import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot

enum class SteamFriendsFailureReason {
    ACCOUNT_REQUIRED,
    SESSION_REQUIRED,
    NETWORK,
    UNAVAILABLE
}

data class SteamFriendActionFeedback(
    val steamId: String,
    val accepted: Boolean,
    val success: Boolean,
    val message: String? = null,
    val relationshipAction: SteamFriendRelationshipAction? = null
)

data class SteamFriendDiscoveryUiState(
    val submittedQuery: String = "",
    val results: List<SteamFriend> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val failure: SteamFriendsFailureReason? = null
)

data class SteamFriendsUiState(
    val accountId: Long? = null,
    val snapshot: SteamFriendsSnapshot? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val fromCache: Boolean = false,
    val failure: SteamFriendsFailureReason? = null,
    val actionSteamId: String? = null,
    val actionFeedback: SteamFriendActionFeedback? = null,
    val discovery: SteamFriendDiscoveryUiState = SteamFriendDiscoveryUiState()
)
