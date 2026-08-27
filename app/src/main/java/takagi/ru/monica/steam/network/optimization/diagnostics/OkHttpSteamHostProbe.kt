package takagi.ru.monica.steam.network.optimization.diagnostics

import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

internal class OkHttpSteamHostProbe(
    private val timeoutMillis: Long = 5_000L,
    private val clockNanos: () -> Long = System::nanoTime
) : SteamHostProbe {
    private val sharedDispatcher = Dispatcher()
    private val clientTemplate = OkHttpClient.Builder()
        .dispatcher(sharedDispatcher)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis * 2L, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun probe(target: SteamHostProbeTarget): SteamHostProbeResult =
        withContext(Dispatchers.IO) {
            val numericAddress = runCatching { InetAddress.getByName(target.address) }
                .getOrElse { error ->
                    return@withContext SteamHostProbeResult(
                        target = target,
                        status = SteamHostProbeStatus.CONNECTION_ERROR,
                        errorType = error::class.java.simpleName
                    )
                }
            val client = clientTemplate.newBuilder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return if (
                            SteamHostsRuleParser.normalizeHostname(hostname) ==
                            SteamHostsRuleParser.normalizeHostname(target.hostname)
                        ) {
                            listOf(numericAddress)
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                    }
                })
                .connectionPool(ConnectionPool(0, 1L, TimeUnit.MILLISECONDS))
                .build()
            val request = runCatching {
                Request.Builder()
                    .url("https://${target.hostname}/")
                    .head()
                    .header("User-Agent", "Etoile-Network-Diagnostics")
                    .header("Connection", "close")
                    .build()
            }.getOrElse { error ->
                return@withContext SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.CONNECTION_ERROR,
                    errorType = error::class.java.simpleName
                )
            }
            val startedAt = clockNanos()

            try {
                client.newCall(request).execute().use { response ->
                    SteamHostProbeResult(
                        target = target,
                        status = SteamHostProbeStatus.AVAILABLE,
                        latencyMillis = elapsedMillis(startedAt),
                        httpStatusCode = response.code
                    )
                }
            } catch (error: SocketTimeoutException) {
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.TIMEOUT,
                    latencyMillis = elapsedMillis(startedAt),
                    errorType = error::class.java.simpleName
                )
            } catch (error: SSLException) {
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.TLS_ERROR,
                    latencyMillis = elapsedMillis(startedAt),
                    errorType = error::class.java.simpleName
                )
            } catch (error: IOException) {
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.CONNECTION_ERROR,
                    latencyMillis = elapsedMillis(startedAt),
                    errorType = error::class.java.simpleName
                )
            } finally {
                client.connectionPool.evictAll()
            }
        }

    private fun elapsedMillis(startedAt: Long): Long =
        ((clockNanos() - startedAt) / 1_000_000L).coerceAtLeast(0L)
}
