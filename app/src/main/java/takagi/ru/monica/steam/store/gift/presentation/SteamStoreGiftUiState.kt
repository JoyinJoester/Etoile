package takagi.ru.monica.steam.store.gift.presentation

import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.store.domain.SteamCartItem
import takagi.ru.monica.steam.store.gift.domain.SteamStoreGiftFailure

data class SteamStoreGiftUiState(
    val pickerOpen: Boolean = false,
    val pendingItem: SteamCartItem? = null,
    val friends: List<SteamFriend> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val fromCache: Boolean = false,
    val failure: SteamStoreGiftFailure? = null
)
