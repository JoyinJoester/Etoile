package takagi.ru.monica.steam.community.presentation

import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot

enum class SteamCommunityFailureReason {
    ACCOUNT_REQUIRED,
    SESSION_REQUIRED,
    NETWORK,
    UNAVAILABLE
}

data class SteamCommunityUiState(
    val accountId: Long? = null,
    val accountSteamId: String? = null,
    val snapshot: SteamCommunitySnapshot? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val fromCache: Boolean = false,
    val staleSections: Set<SteamCommunitySection> = emptySet(),
    val failure: SteamCommunityFailureReason? = null
)
