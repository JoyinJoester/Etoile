package takagi.ru.monica.github.di

import android.content.Context
import okhttp3.OkHttpClient
import java.io.File
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.github.data.GithubCredentialInterceptor
import takagi.ru.monica.github.data.GithubCredentialRefresher
import takagi.ru.monica.github.data.GithubTokenRefreshRepository
import takagi.ru.monica.github.data.GithubAuthServiceClient
import takagi.ru.monica.github.data.GithubAccountApi
import takagi.ru.monica.github.data.GithubActionsRepositoryImpl
import takagi.ru.monica.github.data.GithubAvatarRepositoryImpl
import takagi.ru.monica.github.data.GithubApiRepositorySearchRepository
import takagi.ru.monica.github.data.GithubAuthenticatedRequests
import takagi.ru.monica.github.data.GithubAuthRepositoryImpl
import takagi.ru.monica.github.data.GithubCacheFallbackStore
import takagi.ru.monica.github.data.GithubCommitsRepositoryImpl
import takagi.ru.monica.github.data.GithubContributionsRepositoryImpl
import takagi.ru.monica.github.data.GithubEncryptedCacheStore
import takagi.ru.monica.github.data.GithubInvalidatingCacheStore
import takagi.ru.monica.github.data.GithubNetwork
import takagi.ru.monica.github.data.GithubIssuesRepositoryImpl
import takagi.ru.monica.github.data.GithubIssueTemplatesRepositoryImpl
import takagi.ru.monica.github.data.GithubNotificationsRepositoryImpl
import takagi.ru.monica.github.data.GithubOAuthDeviceAuthRepository
import takagi.ru.monica.github.data.GithubEncryptedPendingOAuthStore
import takagi.ru.monica.github.data.GithubWebOAuthRepository
import takagi.ru.monica.github.data.GithubOrganizationsRepositoryImpl
import takagi.ru.monica.github.data.GithubPreferencesStarLabelStore
import takagi.ru.monica.github.data.GithubPullRequestsRepositoryImpl
import takagi.ru.monica.github.data.GithubPublicUserRepositoryImpl
import takagi.ru.monica.github.data.GithubRepositoryDetailsRepositoryImpl
import takagi.ru.monica.github.data.GithubReleasesRepositoryImpl
import takagi.ru.monica.github.data.GithubRepositoryActionsRepositoryImpl
import takagi.ru.monica.github.data.GithubRepositoryContentsRepositoryImpl
import takagi.ru.monica.github.data.GithubSecureTokenStore
import takagi.ru.monica.github.data.GithubStarsRepositoryImpl
import takagi.ru.monica.github.data.GithubUserRepositoriesRepositoryImpl
import takagi.ru.monica.github.domain.GithubAuthRepository
import takagi.ru.monica.github.domain.GithubActionsRepository
import takagi.ru.monica.github.domain.GithubAvatarRepository
import takagi.ru.monica.github.domain.GithubCacheFallbackMonitor
import takagi.ru.monica.github.domain.GithubCommitsRepository
import takagi.ru.monica.github.domain.GithubContributionsRepository
import takagi.ru.monica.github.domain.GithubDeviceAuthRepository
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubIssueTemplatesRepository
import takagi.ru.monica.github.domain.GithubNotificationsRepository
import takagi.ru.monica.github.domain.GithubOrganizationsRepository
import takagi.ru.monica.github.domain.GithubRepositorySearchRepository
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubPullRequestsRepository
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubRateLimitMonitor
import takagi.ru.monica.github.domain.GithubStarLabelStore
import takagi.ru.monica.github.domain.GithubStarsRepository
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository
import takagi.ru.monica.github.domain.GithubWebAuthRepository

/**
 * Small composition root for the GitHub feature. The UI depends on domain
 * interfaces while concrete networking and storage implementations are wired
 * here once, making future replacement and testing straightforward.
 */
class GithubAppDependencies(
    context: Context,
    client: OkHttpClient = GithubNetwork.client
) {
    private val applicationContext = context.applicationContext
    private val tokenStore = GithubSecureTokenStore(applicationContext)
    private val cacheFallbackStore = GithubCacheFallbackStore()
    private val cacheStore = GithubInvalidatingCacheStore(
        delegate = GithubEncryptedCacheStore(applicationContext),
        onInvalidated = cacheFallbackStore::clear
    )
    private val tokenRefreshRepository = GithubTokenRefreshRepository(
        client, BuildConfig.GITHUB_OAUTH_CLIENT_ID, BuildConfig.GITHUB_OAUTH_CLIENT_SECRET
    )
    private val authService = BuildConfig.GITHUB_AUTH_SERVICE_URL.takeIf(String::isNotBlank)?.let {
        GithubAuthServiceClient(client, it)
    }
    private val credentialRefresher = GithubCredentialRefresher(tokenStore, tokenRefreshRepository::refresh,
        appRefresh = authService?.let { service -> { token -> service.refresh(token) } })
    private val authenticatedClient = client.newBuilder()
        .addInterceptor(GithubCredentialInterceptor(credentialRefresher)).build()
    private val authenticatedRequests = GithubAuthenticatedRequests(tokenStore)

    val rateLimitMonitor: GithubRateLimitMonitor = GithubNetwork.rateLimitStore
    val cacheFallbackMonitor: GithubCacheFallbackMonitor = cacheFallbackStore

    val authRepository: GithubAuthRepository = GithubAuthRepositoryImpl(
        tokenStore = tokenStore,
        accountApi = GithubAccountApi(client),
        cacheStore = cacheStore,
        credentialRefresher = credentialRefresher
    )
    val deviceAuthRepository: GithubDeviceAuthRepository = GithubOAuthDeviceAuthRepository(
        client = client,
        clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID
    )
    val webAuthRepository: GithubWebAuthRepository = authService?.let { service ->
        GithubWebOAuthRepository(
            client = client,
            clientId = BuildConfig.GITHUB_APP_CLIENT_ID,
            clientSecret = "",
            callbackUri = BuildConfig.GITHUB_OAUTH_CALLBACK_URI,
            pendingStore = GithubEncryptedPendingOAuthStore(applicationContext, "github_app"),
            scopes = emptySet(),
            exchangeCode = service::exchange
        )
    } ?: GithubWebOAuthRepository(
        client = client,
        clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID,
        clientSecret = BuildConfig.GITHUB_OAUTH_CLIENT_SECRET,
        callbackUri = BuildConfig.GITHUB_OAUTH_CALLBACK_URI,
        pendingStore = GithubEncryptedPendingOAuthStore(applicationContext)
    )
    val discussionsRepository: takagi.ru.monica.github.domain.GithubDiscussionsRepository =
        takagi.ru.monica.github.data.GithubDiscussionsRepositoryImpl(authenticatedRequests, authenticatedClient)

    val notificationsRepository: GithubNotificationsRepository =
        GithubNotificationsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val issuesRepository: GithubIssuesRepository =
        GithubIssuesRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val pullRequestsRepository: GithubPullRequestsRepository =
        GithubPullRequestsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val actionsRepository: GithubActionsRepository =
        GithubActionsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val repositorySearchRepository: GithubApiRepositorySearchRepository =
        GithubApiRepositorySearchRepository(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val repositoryDetailsRepository: GithubRepositoryDetailsRepository =
        GithubRepositoryDetailsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val contributionsRepository: GithubContributionsRepository =
        GithubContributionsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient
        )
    val releasesRepository: GithubReleasesRepository =
        GithubReleasesRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val commitsRepository: GithubCommitsRepository =
        GithubCommitsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val repositoryActionsRepository: GithubRepositoryActionsRepository =
        GithubRepositoryActionsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore
        )
    val repositoryContentsRepository: GithubRepositoryContentsRepository =
        GithubRepositoryContentsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val issueTemplatesRepository: GithubIssueTemplatesRepository =
        GithubIssueTemplatesRepositoryImpl(repositoryContentsRepository)
    val starsRepository: GithubStarsRepository =
        GithubStarsRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val userRepositoriesRepository: GithubUserRepositoriesRepository =
        GithubUserRepositoriesRepositoryImpl(
            requests = authenticatedRequests,
            client = authenticatedClient,
            cacheStore = cacheStore,
            cacheStatusReporter = cacheFallbackStore
        )
    val publicUserRepository: GithubPublicUserRepository = GithubPublicUserRepositoryImpl(
        requests = authenticatedRequests,
        client = authenticatedClient,
        cacheStore = cacheStore,
        cacheStatusReporter = cacheFallbackStore
    )
    val organizationsRepository: GithubOrganizationsRepository = GithubOrganizationsRepositoryImpl(
        requests = authenticatedRequests,
        client = authenticatedClient,
        cacheStore = cacheStore,
        cacheStatusReporter = cacheFallbackStore
    )
    val avatarRepository: GithubAvatarRepository = GithubAvatarRepositoryImpl(
        client = authenticatedClient,
        cacheDirectory = File(applicationContext.cacheDir, "github_avatars")
    )
    val starLabelStore: GithubStarLabelStore =
        GithubPreferencesStarLabelStore(applicationContext)
}
