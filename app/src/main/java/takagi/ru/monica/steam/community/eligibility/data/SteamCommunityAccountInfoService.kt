package takagi.ru.monica.steam.community.eligibility.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityAccountInfo
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

internal class SteamCommunityAccountInfoService(
    private val cmClient: SteamCmClient = SteamCmClient(),
    private val timeoutMillis: Long = 4_000L
) {
    suspend fun fetch(account: SteamAccount): SteamCommunityAccountInfo? {
        cmClient.latestEvent(account, SteamCmProtocol.EMSG_CLIENT_ACCOUNT_INFO)
            ?.let { return SteamCommunityAccountInfoParser.parse(it.body) }

        return try {
            withTimeoutOrNull(timeoutMillis) {
                coroutineScope {
                    val incoming = async(start = CoroutineStart.UNDISPATCHED) {
                        cmClient.eventsFor(account).first {
                            it.eMsg == SteamCmProtocol.EMSG_CLIENT_ACCOUNT_INFO
                        }
                    }
                    withContext(Dispatchers.IO) { cmClient.connect(account) }
                    val envelope = cmClient.latestEvent(
                        account,
                        SteamCmProtocol.EMSG_CLIENT_ACCOUNT_INFO
                    ) ?: incoming.await()
                    SteamCommunityAccountInfoParser.parse(envelope.body)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "community eligibility account_info_failed type=${error.javaClass.simpleName}"
            )
            null
        }
    }
}
