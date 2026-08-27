package takagi.ru.monica.github.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubDeviceAuthTest {
    @Test
    fun pollerHonorsIntervalAndAddsFiveSecondsAfterSlowDown() = runTest {
        var now = 0L
        val delays = mutableListOf<Long>()
        val token = GithubDeviceAccessToken("gho_token_12345678901234567890", "bearer", setOf("repo"))
        val repository = FakeDeviceRepository(
            ArrayDeque(
                listOf(
                    GithubDevicePollResult.Pending,
                    GithubDevicePollResult.SlowDown,
                    GithubDevicePollResult.Authorized(token)
                )
            )
        )
        val useCase = AwaitGithubDeviceAuthorizationUseCase(
            repository = repository,
            nowEpochMillis = { now },
            delayMillis = { delay -> delays += delay; now += delay }
        )

        val result = useCase(authorization(expiresAt = 60_000L)).getOrThrow()

        assertEquals(token, result)
        assertEquals(listOf(5_000L, 5_000L, 10_000L), delays)
        assertEquals(3, repository.polls)
    }

    @Test
    fun pollerStopsWithoutPollingAfterAuthorizationExpires() = runTest {
        var now = 0L
        val repository = FakeDeviceRepository(ArrayDeque(listOf(GithubDevicePollResult.Pending)))
        val useCase = AwaitGithubDeviceAuthorizationUseCase(
            repository = repository,
            nowEpochMillis = { now },
            delayMillis = { delay -> now += delay }
        )

        val error = useCase(authorization(expiresAt = 5_000L)).exceptionOrNull()

        assertTrue(error is GithubDeviceAuthorizationExpiredException)
        assertEquals(0, repository.polls)
    }

    private class FakeDeviceRepository(
        private val results: ArrayDeque<GithubDevicePollResult>
    ) : GithubDeviceAuthRepository {
        var polls = 0
        override val isConfigured: Boolean = true
        override suspend fun start(): Result<GithubDeviceAuthorization> = Result.failure(UnsupportedOperationException())
        override suspend fun poll(deviceCode: String): Result<GithubDevicePollResult> {
            polls += 1
            return Result.success(results.removeFirst())
        }
    }

    private fun authorization(expiresAt: Long) = GithubDeviceAuthorization(
        deviceCode = "1234567890123456789012345678901234567890",
        userCode = "ABCD-EFGH",
        verificationUri = "https://github.com/login/device",
        expiresAtEpochMillis = expiresAt,
        intervalSeconds = 5
    )
}
