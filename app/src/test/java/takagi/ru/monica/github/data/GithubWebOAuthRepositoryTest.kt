package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.domain.GithubWebOAuthDeniedException
import takagi.ru.monica.github.domain.GithubWebOAuthInvalidCallbackException

class GithubWebOAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var pendingStore: FakePendingStore

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        pendingStore = FakePendingStore()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun startBuildsStateAndPkceAuthorizationWithoutExposingClientSecret() {
        val repository = repository()

        val authorization = repository.start().getOrThrow()
        val url = authorization.authorizationUrl.toHttpUrl()

        assertEquals("client-id-1234567890", url.queryParameter("client_id"))
        assertEquals("etoile://oauth", url.queryParameter("redirect_uri"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertTrue(url.queryParameter("scope").orEmpty().split(" ").contains("user:follow"))
        assertEquals(pendingStore.value?.state, url.queryParameter("state"))
        assertTrue(url.queryParameter("code_challenge")?.length == 43)
        assertFalse(authorization.authorizationUrl.contains(CLIENT_SECRET))
        assertFalse(authorization.toString().contains(url.queryParameter("state").orEmpty()))
    }

    @Test
    fun validCallbackExchangesCodeWithPkceAndClearsPendingState() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"gho_123456789012345678901234567890","token_type":"bearer","scope":"repo notifications"}"""
            )
        )
        val repository = repository()
        val authorization = repository.start().getOrThrow()
        val state = authorization.authorizationUrl.toHttpUrl().queryParameter("state")

        val token = repository.exchangeCallback(
            "etoile://oauth?code=temporary-code-123&state=$state"
        ).getOrThrow()
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("/login/oauth/access_token", request.path)
        assertTrue(body.contains("client_secret=$CLIENT_SECRET"))
        assertTrue(body.contains("code=temporary-code-123"))
        assertTrue(body.contains("code_verifier="))
        assertNull(request.getHeader("Authorization"))
        assertEquals("gho_123456789012345678901234567890", token.accessToken)
        assertNull(pendingStore.value)
    }

    @Test
    fun mismatchedStateAndForgedDenialAreRejectedWithoutNetworkRequest() = runTest {
        val repository = repository()
        repository.start().getOrThrow()

        val invalid = repository.exchangeCallback(
            "etoile://oauth?error=access_denied&state=attacker-state"
        ).exceptionOrNull()

        assertTrue(invalid is GithubWebOAuthInvalidCallbackException)
        assertEquals(0, server.requestCount)
        assertNull(pendingStore.value)
    }

    @Test
    fun browserCallbackAfterRepositoryRecreationUsesTheSavedPkceVerifier() = runTest {
        val first = repository()
        val state = first.start().getOrThrow().authorizationUrl.toHttpUrl().queryParameter("state")
        val verifier = pendingStore.value!!.codeVerifier
        server.enqueue(MockResponse().setBody(
            """{"access_token":"gho_123456789012345678901234567890","token_type":"bearer"}"""))

        val recreated = repository()
        assertTrue(recreated.exchangeCallback("etoile://oauth?code=temporary-code-123&state=$state").isSuccess)
        assertTrue(server.takeRequest().body.readUtf8().contains("code_verifier=$verifier"))
        assertNull(pendingStore.value)
        assertTrue(recreated.exchangeCallback("etoile://oauth?code=temporary-code-123&state=$state").isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun callbackAfterTheAuthorizationExpiresCannotExchangeAToken() = runTest {
        val repository = repository()
        val state = repository.start().getOrThrow().authorizationUrl.toHttpUrl().queryParameter("state")
        pendingStore.value = pendingStore.value!!.copy(createdAtEpochMillis = -600_000L)
        assertTrue(repository.exchangeCallback("etoile://oauth?code=temporary-code-123&state=$state")
            .exceptionOrNull() is GithubWebOAuthInvalidCallbackException)
        assertNull(pendingStore.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun matchingDenialIsReportedAndNoSecretDisablesWebFlow() = runTest {
        val repository = repository()
        val state = repository.start().getOrThrow().authorizationUrl.toHttpUrl().queryParameter("state")

        val denied = repository.exchangeCallback(
            "etoile://oauth?error=access_denied&state=$state"
        ).exceptionOrNull()

        assertTrue(denied is GithubWebOAuthDeniedException)
        assertFalse(repository(clientSecret = "").isConfigured)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun callbackValidationRequiresTheExactAuthorityShape() {
        val repository = repository()

        assertTrue(repository.isCallbackUrl("etoile://oauth?code=ok&state=ok"))
        assertFalse(repository.isCallbackUrl("etoile://oauth:443?code=ok&state=ok"))
        assertFalse(repository.isCallbackUrl("etoile://oauth@evil.example?code=ok&state=ok"))
        assertFalse(repository.isCallbackUrl("etoile://oauth?code=ok&state=ok#fragment"))
    }

    @Test
    fun brokerFlowNeedsNoSecretAndChecksStateBeforeExchange() = runTest {
        var exchanges = 0
        var receivedVerifier: String? = null
        val repository = GithubWebOAuthRepository(
            client = OkHttpClient(), clientId = "Iv23test-client-id", clientSecret = "",
            callbackUri = "etoile://oauth", pendingStore = pendingStore, scopes = emptySet(),
            exchangeCode = { _, verifier ->
                exchanges++
                receivedVerifier = verifier
                Result.success(takagi.ru.monica.github.domain.GithubDeviceAccessToken(
                    "ghu_" + "a".repeat(40), "bearer", emptySet(), authorizationSource = "github_app"))
            }
        )
        assertTrue(repository.isConfigured)
        repository.start().getOrThrow()
        assertTrue(repository.exchangeCallback("etoile://oauth?code=temporary-code&state=wrong").isFailure)
        assertEquals(0, exchanges)
        val url = repository.start().getOrThrow().authorizationUrl.toHttpUrl()
        val verifier = pendingStore.value!!.codeVerifier
        val callback = "etoile://oauth?code=temporary-code&state=${url.queryParameter("state")}"
        assertEquals("github_app", repository.exchangeCallback(callback).getOrThrow().authorizationSource)
        assertEquals(verifier, receivedVerifier)
        assertTrue(repository.exchangeCallback(callback).isFailure)
        assertEquals(1, exchanges)
        assertEquals(0, server.requestCount)
    }

    private fun repository(clientSecret: String = CLIENT_SECRET) = GithubWebOAuthRepository(
        client = OkHttpClient(),
        clientId = "client-id-1234567890",
        clientSecret = clientSecret,
        callbackUri = "etoile://oauth",
        pendingStore = pendingStore,
        authorizationUrl = server.url("/login/oauth/authorize").toString(),
        accessTokenUrl = server.url("/login/oauth/access_token").toString(),
        nowEpochMillis = { 1_000L },
        randomBytes = { size -> ByteArray(size) { index -> (index + 1).toByte() } }
    )

    private class FakePendingStore : GithubPendingOAuthStore {
        var value: GithubPendingOAuth? = null
        override fun read(): GithubPendingOAuth? = value
        override fun write(value: GithubPendingOAuth) { this.value = value }
        override fun clear() { value = null }
    }

    private companion object {
        const val CLIENT_SECRET = "secret-123456789012345678901234567890"
    }
}
