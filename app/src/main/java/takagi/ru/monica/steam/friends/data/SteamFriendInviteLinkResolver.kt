package takagi.ru.monica.steam.friends.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamHttpClientProvider

fun interface SteamFriendInviteLinkResolver {
    fun resolve(url: String): HttpUrl
}

class SteamFriendInviteLinkRedirectResolver(
    client: OkHttpClient = SteamHttpClientProvider.client
) : SteamFriendInviteLinkResolver {
    private val redirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun resolve(url: String): HttpUrl {
        var current = url.toHttpUrl()
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            requireAllowedHost(current)
            if (isCommunityHost(current.host)) return current
            val request = Request.Builder()
                .url(current)
                .get()
                .header("User-Agent", "Etoile/1.0")
                .build()
            redirectClient.newCall(request).execute().use { response ->
                if (!response.isRedirect) {
                    throw SteamApiException("Steam friend invite link did not redirect")
                }
                if (redirectIndex >= MAX_REDIRECTS) {
                    throw SteamApiException("Steam friend invite redirect limit exceeded")
                }
                current = response.request.url.resolve(response.header("Location").orEmpty())
                    ?: throw SteamApiException("Steam friend invite redirect missing target")
            }
        }
        throw SteamApiException("Steam friend invite redirect limit exceeded")
    }

    private fun requireAllowedHost(url: HttpUrl) {
        require(url.isHttps) { "Steam friend invite requires HTTPS" }
        require(url.host == "s.team" || isCommunityHost(url.host)) {
            "Unsupported Steam friend invite host"
        }
    }

    private fun isCommunityHost(host: String): Boolean =
        host == "steamcommunity.com" || host.endsWith(".steamcommunity.com")

    private companion object {
        const val MAX_REDIRECTS = 3
    }
}
