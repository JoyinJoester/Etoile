package takagi.ru.monica.steam.token.loginsecurity.data

import okhttp3.Request

/** Stable request identity shared by every Steam MobileApp authentication request. */
internal object SteamMobileAuthRequestProfile {
    const val deviceFriendlyName = "Etoile"
    const val websiteId = "Mobile"
    const val platformType = 3L
    const val osType = -500L
    const val gamingDeviceType = 528L

    private const val userAgent = "okhttp/4.9.2"
    private const val mobileClientVersion = "777777 3.6.4"

    val headers: Map<String, String> = linkedMapOf(
        "User-Agent" to userAgent,
        "Accept" to "application/json, text/plain, */*",
        "Cookie" to "mobileClient=android; mobileClientVersion=$mobileClientVersion"
    )

    fun applyTo(builder: Request.Builder): Request.Builder = builder.apply {
        headers.forEach { (name, value) -> header(name, value) }
    }
}
