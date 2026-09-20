package takagi.ru.monica.github.feature.auth

import java.net.URI
import java.util.Locale

internal object GithubDeviceCodeAutofill {
    fun normalize(code: String): String? {
        val value = code.trim().uppercase(Locale.ROOT)
        return value.takeIf { it.matches(Regex("[A-Z0-9]{4}-?[A-Z0-9]{4}")) }?.replace("-", "")
    }

    fun isDevicePage(url: String?): Boolean = runCatching {
        val uri = URI(url ?: return false)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.rawUserInfo == null && uri.port in listOf(-1, 443) &&
            uri.rawPath in listOf("/login/device", "/login/device/")
    }.getOrDefault(false)
}
