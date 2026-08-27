package takagi.ru.monica.steam.friends.presentation

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
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.data.SteamFriendsCache
import takagi.ru.monica.steam.friends.domain.SteamFriendActionResult
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamFriendsViewModelTest {
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
    fun authenticationFailureForcesTheSharedResolverBeforeOneRetry() = runTest(scheduler) {
        val forceFlags = mutableListOf<Boolean>()
        val tokens = mutableListOf<String?>()
        val resolver = SteamAccountSessionResolver { account, forceRefresh ->
            forceFlags += forceRefresh
            if (forceRefresh) {
                account.copy(
                    accessToken = "fresh-token",
                    steamLoginSecure = "${account.steamId}||fresh-token"
                )
            } else {
                account
            }
        }
        val gateway = object : SteamFriendsGateway {
            override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
                tokens += account.accessToken
                if (account.accessToken != "fresh-token") {
                    throw SteamApiException(
                        message = "session expired",
                        eResult = 15,
                        httpStatusCode = 403
                    )
                }
                return SteamFriendsSnapshot(fetchedAt = fetchedAt)
            }

            override fun respondToInvite(
                account: SteamAccount,
                friendSteamId: String,
                accept: Boolean
            ) = SteamFriendActionResult(success = true)

            override fun changeRelationship(
                account: SteamAccount,
                friendSteamId: String,
                action: SteamFriendRelationshipAction
            ) = SteamFriendActionResult(success = true)
        }
        val viewModel = SteamFriendsViewModel(
            gateway = gateway,
            cache = MemoryFriendsCache(),
            sessionResolver = resolver,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()

        assertEquals(listOf(false, true), forceFlags)
        assertEquals(listOf("old-token", "fresh-token"), tokens)
        assertEquals(null, viewModel.uiState.value.failure)
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test
    fun sameLocalIdWithDifferentSteamIdStillSwitchesAccounts() = runTest(scheduler) {
        val fetchedSteamIds = mutableListOf<String>()
        val gateway = object : SteamFriendsGateway {
            override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
                fetchedSteamIds += account.steamId
                return SteamFriendsSnapshot(fetchedAt = fetchedAt)
            }

            override fun respondToInvite(
                account: SteamAccount,
                friendSteamId: String,
                accept: Boolean
            ) = SteamFriendActionResult(success = true)

            override fun changeRelationship(
                account: SteamAccount,
                friendSteamId: String,
                action: SteamFriendRelationshipAction
            ) = SteamFriendActionResult(success = true)
        }
        val viewModel = SteamFriendsViewModel(
            gateway = gateway,
            cache = MemoryFriendsCache(),
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        viewModel.selectAccount(account(steamId = "76561198000000009"))
        advanceUntilIdle()

        assertEquals(
            listOf("76561198000000009"),
            fetchedSteamIds
        )
    }

    @Test
    fun successfulRelationshipActionRefreshesAuthoritativeFriends() = runTest(scheduler) {
        val friend = SteamFriend(
            steamId = "76561198000000002",
            relationship = SteamFriendRelationship.FRIEND
        )
        var fetchCalls = 0
        val actions = mutableListOf<SteamFriendRelationshipAction>()
        val gateway = object : SteamFriendsGateway {
            override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
                fetchCalls++
                return SteamFriendsSnapshot(listOf(friend), fetchedAt)
            }

            override fun respondToInvite(
                account: SteamAccount,
                friendSteamId: String,
                accept: Boolean
            ) = SteamFriendActionResult(success = true)

            override fun changeRelationship(
                account: SteamAccount,
                friendSteamId: String,
                action: SteamFriendRelationshipAction
            ): SteamFriendActionResult {
                actions += action
                return SteamFriendActionResult(success = true)
            }
        }
        val viewModel = SteamFriendsViewModel(
            gateway = gateway,
            cache = MemoryFriendsCache(),
            ioDispatcher = dispatcher
        )
        viewModel.selectAccount(account())
        advanceUntilIdle()

        viewModel.changeRelationship(friend, SteamFriendRelationshipAction.BLOCK)
        advanceUntilIdle()

        assertEquals(listOf(SteamFriendRelationshipAction.BLOCK), actions)
        assertEquals(2, fetchCalls)
        assertEquals(true, viewModel.uiState.value.actionFeedback?.success)
        assertEquals(
            SteamFriendRelationshipAction.BLOCK,
            viewModel.uiState.value.actionFeedback?.relationshipAction
        )
    }

    @Test
    fun discoveryMergesExistingRelationshipBeforeRenderingTheCandidate() = runTest(scheduler) {
        val existing = SteamFriend(
            steamId = "76561198000000002",
            relationship = SteamFriendRelationship.REQUEST_OUTGOING,
            personaName = "Known account"
        )
        val gateway = object : SteamFriendsGateway {
            override fun fetch(account: SteamAccount, fetchedAt: Long) =
                SteamFriendsSnapshot(listOf(existing), fetchedAt)

            override fun respondToInvite(
                account: SteamAccount,
                friendSteamId: String,
                accept: Boolean
            ) = SteamFriendActionResult(success = true)

            override fun changeRelationship(
                account: SteamAccount,
                friendSteamId: String,
                action: SteamFriendRelationshipAction
            ) = SteamFriendActionResult(success = true)

            override fun findCandidates(account: SteamAccount, query: String) = listOf(
                SteamFriend(
                    steamId = existing.steamId,
                    personaName = "Search result"
                )
            )
        }
        val viewModel = SteamFriendsViewModel(
            gateway = gateway,
            cache = MemoryFriendsCache(),
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()
        viewModel.findFriendCandidates("Known account")
        advanceUntilIdle()

        val candidate = viewModel.uiState.value.discovery.results.single()
        assertEquals(SteamFriendRelationship.REQUEST_OUTGOING, candidate.relationship)
        assertEquals("Known account", candidate.personaName)
    }

    private fun account(steamId: String = "76561198000000001") = SteamAccount(
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
}

private class MemoryFriendsCache : SteamFriendsCache {
    private var snapshot: SteamFriendsSnapshot? = null

    override fun load(accountKey: String): SteamFriendsSnapshot? = snapshot

    override fun save(accountKey: String, snapshot: SteamFriendsSnapshot) {
        this.snapshot = snapshot
    }
}
