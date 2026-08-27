package takagi.ru.monica.github.domain

import kotlinx.coroutines.delay

data class GithubDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAtEpochMillis: Long,
    val intervalSeconds: Int
) {
    override fun toString(): String =
        "GithubDeviceAuthorization(deviceCode=<redacted>, userCode=$userCode, verificationUri=$verificationUri, expiresAtEpochMillis=$expiresAtEpochMillis, intervalSeconds=$intervalSeconds)"
}

data class GithubDeviceAccessToken(
    val accessToken: String,
    val tokenType: String,
    val scopes: Set<String>
) {
    override fun toString(): String =
        "GithubDeviceAccessToken(accessToken=<redacted>, tokenType=$tokenType, scopes=$scopes)"
}

sealed interface GithubDevicePollResult {
    data object Pending : GithubDevicePollResult
    data object SlowDown : GithubDevicePollResult
    data class Authorized(val token: GithubDeviceAccessToken) : GithubDevicePollResult
    data object Expired : GithubDevicePollResult
    data object Denied : GithubDevicePollResult
}

interface GithubDeviceAuthRepository {
    val isConfigured: Boolean
    suspend fun start(): Result<GithubDeviceAuthorization>
    suspend fun poll(deviceCode: String): Result<GithubDevicePollResult>
}

class GithubDeviceFlowNotConfiguredException : IllegalStateException("GitHub OAuth device flow is not configured")
class GithubDeviceAuthorizationDeniedException : IllegalStateException("GitHub device authorization was denied")
class GithubDeviceAuthorizationExpiredException : IllegalStateException("GitHub device authorization expired")
class GithubDeviceFlowProtocolException(val errorCode: String) :
    IllegalStateException("GitHub device flow failed")

class AwaitGithubDeviceAuthorizationUseCase(
    private val repository: GithubDeviceAuthRepository,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val delayMillis: suspend (Long) -> Unit = { delay(it) }
) {
    suspend operator fun invoke(
        authorization: GithubDeviceAuthorization
    ): Result<GithubDeviceAccessToken> {
        var intervalSeconds = authorization.intervalSeconds.coerceAtLeast(MINIMUM_INTERVAL_SECONDS)
        while (nowEpochMillis() < authorization.expiresAtEpochMillis) {
            delayMillis(intervalSeconds * 1_000L)
            if (nowEpochMillis() >= authorization.expiresAtEpochMillis) {
                return Result.failure(GithubDeviceAuthorizationExpiredException())
            }
            val result = repository.poll(authorization.deviceCode).getOrElse {
                return Result.failure(it)
            }
            when (result) {
                GithubDevicePollResult.Pending -> Unit
                GithubDevicePollResult.SlowDown -> {
                    intervalSeconds = (intervalSeconds + SLOW_DOWN_INCREMENT_SECONDS)
                        .coerceAtMost(MAXIMUM_INTERVAL_SECONDS)
                }
                is GithubDevicePollResult.Authorized -> return Result.success(result.token)
                GithubDevicePollResult.Expired -> {
                    return Result.failure(GithubDeviceAuthorizationExpiredException())
                }
                GithubDevicePollResult.Denied -> {
                    return Result.failure(GithubDeviceAuthorizationDeniedException())
                }
            }
        }
        return Result.failure(GithubDeviceAuthorizationExpiredException())
    }

    private companion object {
        const val MINIMUM_INTERVAL_SECONDS = 5
        const val SLOW_DOWN_INCREMENT_SECONDS = 5
        const val MAXIMUM_INTERVAL_SECONDS = 300
    }
}
