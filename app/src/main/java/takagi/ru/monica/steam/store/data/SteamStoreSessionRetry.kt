package takagi.ru.monica.steam.store.data

internal open class SteamStoreSessionException(message: String) : IllegalStateException(message)

internal class SteamStoreAccountRegionException(
    message: String = "无法读取当前 Steam 账号的商店地区，请刷新后重试"
) : SteamStoreSessionException(message)

internal class SteamStoreWishlistSessionException(
    message: String = "Steam 愿望单会话已失效，请刷新后重试"
) : SteamStoreSessionException(message)

internal class SteamStoreIgnoreSessionException(
    message: String = "Steam 商店偏好会话已失效，请刷新账号后重试"
) : SteamStoreSessionException(message)

internal class SteamStoreFamilyViewException(
    message: String = "Steam 家庭监护可能仍处于锁定状态，请前往 Steam 输入 PIN 解锁"
) : IllegalStateException(message)

internal fun throwSteamStoreHttpFailure(
    responseCode: Int,
    steamLoginSecure: String?,
    fallbackMessage: () -> String,
): Nothing {
    if (responseCode == 403 && steamLoginSecure?.isNotBlank() == true) {
        throw SteamStoreFamilyViewException()
    }
    throw IllegalStateException(fallbackMessage())
}

internal data class SteamStoreAccountCredentials(
    val accessToken: String?,
    val steamLoginSecure: String?,
    val steamId: String? = null
)

internal suspend fun <T> executeSteamStoreAccountRetry(
    initialCredentials: SteamStoreAccountCredentials,
    forceRefreshCredentials: suspend () -> SteamStoreAccountCredentials,
    request: suspend (SteamStoreAccountCredentials) -> T
): T {
    return try {
        request(initialCredentials)
    } catch (error: SteamStoreSessionException) {
        val refreshedCredentials = forceRefreshCredentials()
        if (refreshedCredentials == initialCredentials) throw error
        request(refreshedCredentials)
    }
}
