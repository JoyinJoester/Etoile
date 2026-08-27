package takagi.ru.monica.github.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GithubAvatarRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDirectory: File
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val rewritten = chain.request().newBuilder()
                    .url(chain.request().url.toString().replace("https://", "http://"))
                    .build()
                chain.proceed(rewritten)
            }
            .build()
        server = MockWebServer().also { it.start() }
        cacheDirectory = Files.createTempDirectory("github-avatar-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun downloadsHttpsAvatarAndServesSubsequentReadsFromMemory() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("avatar-bytes")
        )
        val url = avatarUrl()
        val repository = GithubAvatarRepositoryImpl(
            client = client,
            cacheDirectory = cacheDirectory
        )

        val first = repository.bytes(url).getOrThrow()
        val second = repository.bytes(url).getOrThrow()

        assertArrayEquals("avatar-bytes".toByteArray(), first)
        assertArrayEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun staleDiskCacheIsReturnedWhenAvatarServerFails() = runTest {
        val url = avatarUrl()
        server.enqueue(MockResponse().setResponseCode(200).setBody("cached-avatar"))
        val firstRepository = GithubAvatarRepositoryImpl(client, cacheDirectory)
        firstRepository.bytes(url).getOrThrow()

        File(cacheDirectory, "${sha256Hex(url)}.img").setLastModified(1L)
        server.enqueue(MockResponse().setResponseCode(503))
        val secondRepository = GithubAvatarRepositoryImpl(client, cacheDirectory)

        assertArrayEquals("cached-avatar".toByteArray(), secondRepository.bytes(url).getOrThrow())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun nonHttpsAvatarUrlsAreRejectedBeforeNetworkAccess() = runTest {
        val repository = GithubAvatarRepositoryImpl(client, cacheDirectory)

        assertNull(repository.bytes("http://avatars.example/alice.png").getOrThrow())
        assertEquals(0, server.requestCount)
    }

    private fun avatarUrl(): String = "https://localhost:${server.port}/avatars/alice.png"
}
