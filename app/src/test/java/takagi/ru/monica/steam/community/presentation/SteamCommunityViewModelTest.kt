package takagi.ru.monica.steam.community.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.community.data.SteamCommunityCache
import takagi.ru.monica.steam.community.domain.SteamCommunityGateway
import takagi.ru.monica.steam.community.domain.SteamCommunityProfile
import takagi.ru.monica.steam.community.domain.SteamCommunityRecentGame
import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityEligibilityGateway
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockSource
import takagi.ru.monica.steam.community.eligibility.domain.CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamCommunityViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun authenticationFailureForcesSharedResolverBeforeOneRetry() = runTest(scheduler) {
        val forceFlags = mutableListOf<Boolean>()
        val tokens = mutableListOf<String?>()
        val gateway = SteamCommunityGateway { account ->
            tokens += account.accessToken
            if (account.accessToken != "fresh-token") {
                throw SteamApiException(
                    message = "session expired",
                    eResult = 15,
                    httpStatusCode = 403
                )
            }
            snapshot(account.steamId)
        }
        val resolver = SteamAccountSessionResolver { account, force ->
            forceFlags += force
            if (force) account.copy(accessToken = "fresh-token") else account
        }
        val viewModel = SteamCommunityViewModel(
            gateway = gateway,
            cache = MemoryCommunityCache(),
            sessionResolver = resolver,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()

        assertEquals(listOf(false, true), forceFlags)
        assertEquals(listOf("old-token", "fresh-token"), tokens)
        assertEquals("Alyx", viewModel.uiState.value.snapshot?.profile?.displayName)
        assertEquals(null, viewModel.uiState.value.failure)
    }

    @Test
    fun cachedFailedSectionIsRetainedAndMarkedStale() = runTest(scheduler) {
        val cache = MemoryCommunityCache().apply {
            save(snapshot(ACCOUNT_A).copy(steamLevel = 51))
        }
        val gateway = SteamCommunityGateway { account ->
            snapshot(account.steamId).copy(
                steamLevel = null,
                unavailableSections = setOf(SteamCommunitySection.LEVEL),
                fetchedAt = 200L
            )
        }
        val viewModel = SteamCommunityViewModel(
            gateway = gateway,
            cache = cache,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()

        assertEquals(51, viewModel.uiState.value.snapshot?.steamLevel)
        assertTrue(SteamCommunitySection.LEVEL in viewModel.uiState.value.staleSections)
        assertTrue(viewModel.uiState.value.fromCache)
        assertEquals(200L, cache.load(ACCOUNT_A)?.fetchedAt)
    }

    @Test
    fun cachedExactUnlockProgressWinsOverOfflineEstimate() = runTest(scheduler) {
        val exact = SteamCommunityUnlockProgress(
            status = SteamCommunityRestrictionStatus.LIMITED,
            source = SteamCommunityUnlockSource.STEAM_SUPPORT,
            spentUsdCents = 300,
            remainingUsdCents = 200,
            exactProgress = true,
            fetchedAt = 90L
        )
        val cache = MemoryCommunityCache().apply {
            save(snapshot(ACCOUNT_A).copy(unlockProgress = exact))
        }
        val eligibility = SteamCommunityEligibilityGateway {
            SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                remainingUsdCents = 500,
                exactProgress = false,
                fetchedAt = 200L
            )
        }
        val viewModel = SteamCommunityViewModel(
            gateway = SteamCommunityGateway { account ->
                snapshot(account.steamId).copy(steamLevel = 0)
            },
            cache = cache,
            eligibilityGateway = eligibility,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()

        assertEquals(200, viewModel.uiState.value.snapshot?.unlockProgress?.remainingUsdCents)
        assertTrue(
            SteamCommunitySection.ELIGIBILITY in viewModel.uiState.value.staleSections
        )
        assertTrue(viewModel.uiState.value.fromCache)
    }

    @Test
    fun unverifiedCachedUnlockStateCannotReplaceFreshUnknownState() {
        val cached = snapshot(ACCOUNT_A).copy(
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNRESTRICTED,
                source = SteamCommunityUnlockSource.STEAM_ACCOUNT_FLAGS,
                remainingUsdCents = 0,
                exactProgress = false,
                fetchedAt = 90L
            )
        )
        val fresh = snapshot(ACCOUNT_A).copy(
            steamLevel = 0,
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                remainingUsdCents = 500,
                exactProgress = false,
                fetchedAt = 200L
            )
        )

        val merged = mergeCommunitySnapshot(fresh, cached)

        assertEquals(
            SteamCommunityRestrictionStatus.UNKNOWN,
            merged.snapshot.unlockProgress?.status
        )
        assertFalse(SteamCommunitySection.ELIGIBILITY in merged.staleSections)
    }

    @Test
    fun obsoleteExactUnlockEvidenceCannotReplaceFreshUnknownState() {
        val cached = snapshot(ACCOUNT_A).copy(
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNRESTRICTED,
                source = SteamCommunityUnlockSource.STEAM_SUPPORT,
                remainingUsdCents = 0,
                exactProgress = true,
                evidenceRevision = 2,
                fetchedAt = 90L
            )
        )
        val fresh = snapshot(ACCOUNT_A).copy(
            steamLevel = 0,
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                remainingUsdCents = 500,
                exactProgress = false,
                fetchedAt = 200L
            )
        )

        val merged = mergeCommunitySnapshot(fresh, cached)

        assertEquals(
            SteamCommunityRestrictionStatus.UNKNOWN,
            merged.snapshot.unlockProgress?.status
        )
        assertFalse(SteamCommunitySection.ELIGIBILITY in merged.staleSections)
    }

    @Test
    fun positiveSteamLevelConfirmsCommunityAccessWhenEligibilityIsUnknown() = runTest(scheduler) {
        val eligibility = SteamCommunityEligibilityGateway {
            SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                remainingUsdCents = 500,
                exactProgress = false,
                fetchedAt = 200L
            )
        }
        val viewModel = SteamCommunityViewModel(
            gateway = SteamCommunityGateway { account ->
                snapshot(account.steamId).copy(steamLevel = 12)
            },
            cache = MemoryCommunityCache(),
            eligibilityGateway = eligibility,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()

        assertEquals(
            SteamCommunityRestrictionStatus.UNRESTRICTED,
            viewModel.uiState.value.snapshot?.unlockProgress?.status
        )
    }

    @Test
    fun accountSwitchKeepsLevelEvidenceScopedToSteamId() = runTest(scheduler) {
        val eligibility = SteamCommunityEligibilityGateway {
            SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                remainingUsdCents = 500,
                exactProgress = false
            )
        }
        val viewModel = SteamCommunityViewModel(
            gateway = SteamCommunityGateway { account ->
                snapshot(account.steamId).copy(
                    steamLevel = if (account.steamId == ACCOUNT_A) 12 else 0
                )
            },
            cache = MemoryCommunityCache(),
            eligibilityGateway = eligibility,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account(steamId = ACCOUNT_A))
        advanceUntilIdle()
        assertEquals(
            SteamCommunityRestrictionStatus.UNRESTRICTED,
            viewModel.uiState.value.snapshot?.unlockProgress?.status
        )

        viewModel.selectAccount(account(steamId = ACCOUNT_B))
        advanceUntilIdle()
        assertEquals(ACCOUNT_B, viewModel.uiState.value.snapshot?.accountSteamId)
        assertEquals(
            SteamCommunityRestrictionStatus.UNKNOWN,
            viewModel.uiState.value.snapshot?.unlockProgress?.status
        )
    }

    @Test
    fun cachedPositiveLevelStillConfirmsAccessWhenFreshLevelSectionFails() {
        val cached = snapshot(ACCOUNT_A).copy(
            steamLevel = 12,
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNRESTRICTED,
                source = SteamCommunityUnlockSource.STEAM_LEVEL,
                evidenceRevision = 3
            )
        )
        val fresh = snapshot(ACCOUNT_A).copy(
            steamLevel = null,
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE
            ),
            unavailableSections = setOf(SteamCommunitySection.LEVEL)
        )

        val merged = mergeCommunitySnapshot(fresh, cached)

        assertEquals(12, merged.snapshot.steamLevel)
        assertEquals(
            SteamCommunityRestrictionStatus.UNRESTRICTED,
            merged.snapshot.unlockProgress?.status
        )
        assertEquals(
            SteamCommunityUnlockSource.STEAM_LEVEL,
            merged.snapshot.unlockProgress?.source
        )
    }

    @Test
    fun cachedLimitedFlagSurvivesATransientUnknownRefresh() {
        val cached = snapshot(ACCOUNT_A).copy(
            steamLevel = 0,
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.LIMITED,
                source = SteamCommunityUnlockSource.STEAM_ACCOUNT_FLAGS,
                exactProgress = false,
                evidenceRevision = CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
            )
        )
        val fresh = snapshot(ACCOUNT_A).copy(
            steamLevel = 0,
            unlockProgress = SteamCommunityUnlockProgress(
                status = SteamCommunityRestrictionStatus.UNKNOWN,
                source = SteamCommunityUnlockSource.ESTIMATE,
                exactProgress = false,
                evidenceRevision = CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
            )
        )

        val merged = mergeCommunitySnapshot(fresh, cached)

        assertEquals(
            SteamCommunityRestrictionStatus.LIMITED,
            merged.snapshot.unlockProgress?.status
        )
        assertTrue(SteamCommunitySection.ELIGIBILITY in merged.staleSections)
    }

    @Test
    fun sameLocalIdWithDifferentSteamIdCannotPublishTheOldAccount() = runTest(scheduler) {
        val fetchedSteamIds = mutableListOf<String>()
        val gateway = SteamCommunityGateway { account ->
            fetchedSteamIds += account.steamId
            snapshot(account.steamId)
        }
        val viewModel = SteamCommunityViewModel(
            gateway = gateway,
            cache = MemoryCommunityCache(),
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account(steamId = ACCOUNT_A))
        viewModel.selectAccount(account(steamId = ACCOUNT_B))
        advanceUntilIdle()

        assertEquals(listOf(ACCOUNT_B), fetchedSteamIds)
        assertEquals(ACCOUNT_B, viewModel.uiState.value.snapshot?.accountSteamId)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun accountWithoutARealSteamIdDoesNotCallTheGateway() = runTest(scheduler) {
        var calls = 0
        val viewModel = SteamCommunityViewModel(
            gateway = SteamCommunityGateway { account ->
                calls++
                snapshot(account.steamId)
            },
            cache = MemoryCommunityCache(),
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account(steamId = "pending:account"))
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals(
            SteamCommunityFailureReason.ACCOUNT_REQUIRED,
            viewModel.uiState.value.failure
        )
    }

    private fun snapshot(steamId: String) = SteamCommunitySnapshot(
        accountSteamId = steamId,
        profile = SteamCommunityProfile(steamId = steamId, displayName = "Alyx"),
        steamLevel = 42,
        recentGames = listOf(SteamCommunityRecentGame(appId = 620, name = "Portal 2")),
        fetchedAt = 100L
    )

    private fun account(steamId: String = ACCOUNT_A) = SteamAccount(
        id = 1L,
        steamId = steamId,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "old-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$steamId||old-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_A = "76561198000000001"
        const val ACCOUNT_B = "76561198000000009"
    }
}

private class MemoryCommunityCache : SteamCommunityCache {
    private val snapshots = linkedMapOf<String, SteamCommunitySnapshot>()

    override fun load(accountSteamId: String): SteamCommunitySnapshot? = snapshots[accountSteamId]

    override fun save(snapshot: SteamCommunitySnapshot) {
        snapshots[snapshot.accountSteamId] = snapshot
    }
}
