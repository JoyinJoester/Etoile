package takagi.ru.monica.steam.friends.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot

class SteamChatStateUpdatesTest {
    @Test
    fun staleRemoteUnreadDoesNotOverwriteLocalAcknowledgement() {
        val local = snapshot(
            SteamChatSession(
                partnerSteamId = PARTNER_ID,
                lastMessageTimestamp = 120L,
                lastViewTimestamp = 120L,
                unreadCount = 0
            )
        )
        val remote = snapshot(
            SteamChatSession(
                partnerSteamId = PARTNER_ID,
                lastMessageTimestamp = 120L,
                lastViewTimestamp = 100L,
                unreadCount = 2
            )
        )

        val reconciled = reconcileSteamChatSessions(remote, local).sessions.single()

        assertEquals(0, reconciled.unreadCount)
        assertEquals(120L, reconciled.lastViewTimestamp)
    }

    @Test
    fun messageNewerThanLocalAcknowledgementRemainsUnread() {
        val local = snapshot(
            SteamChatSession(
                partnerSteamId = PARTNER_ID,
                lastMessageTimestamp = 120L,
                lastViewTimestamp = 120L,
                unreadCount = 0
            )
        )
        val remote = snapshot(
            SteamChatSession(
                partnerSteamId = PARTNER_ID,
                lastMessageTimestamp = 130L,
                lastViewTimestamp = 100L,
                unreadCount = 1
            )
        )

        val reconciled = reconcileSteamChatSessions(remote, local).sessions.single()

        assertEquals(1, reconciled.unreadCount)
        assertEquals(100L, reconciled.lastViewTimestamp)
    }

    @Test
    fun remoteRefreshKeepsLocalOnlyConversationRows() {
        val localOnly = SteamChatSession(
            partnerSteamId = PARTNER_ID,
            lastMessageTimestamp = 150L,
            lastViewTimestamp = 150L,
            unreadCount = 0
        )
        val remote = SteamChatSessionsSnapshot(
            accountSteamId = ACCOUNT_ID,
            sessions = emptyList(),
            fetchedAt = 2L
        )

        val reconciled = reconcileSteamChatSessions(remote, snapshot(localOnly))

        assertEquals(listOf(localOnly), reconciled.sessions)
    }

    private fun snapshot(session: SteamChatSession) = SteamChatSessionsSnapshot(
        accountSteamId = ACCOUNT_ID,
        sessions = listOf(session),
        fetchedAt = 1L
    )

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
        const val PARTNER_ID = "76561198000000002"
    }
}
