package takagi.ru.monica.steam.store.requirements.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamStoreSystemRequirements(
    val minimum: String = "",
    val recommended: String = ""
) {
    val hasContent: Boolean
        get() = minimum.isNotBlank() || recommended.isNotBlank()
}
