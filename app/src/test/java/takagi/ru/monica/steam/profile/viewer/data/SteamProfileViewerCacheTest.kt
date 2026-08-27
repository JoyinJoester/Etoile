package takagi.ru.monica.steam.profile.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameDataVisibility
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileSummary
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot

class SteamProfileViewerCacheTest {
    @Test
    fun profileSnapshotCodecPreservesViewerAndTargetIdentity() {
        val snapshot = SteamProfileViewerSnapshot(
            viewerAccountId = 1L,
            viewerSteamId = VIEWER,
            target = SteamProfileSummary(
                steamId = TARGET,
                personaName = "Target",
                personaState = SteamPersonaState.ONLINE
            ),
            targetGames = emptyList(),
            viewerGames = emptyList(),
            gameDataVisibility = SteamProfileGameDataVisibility.PRIVATE,
            fetchedAt = 123L
        )

        assertEquals(
            snapshot,
            SteamProfileViewerCacheCodec.decodeProfile(
                SteamProfileViewerCacheCodec.encodeProfile(snapshot)
            )
        )
    }

    @Test
    fun legacyProfileSnapshotUsesSafeDefaultsForNewCommunityFields() {
        val legacy = """
            {
              "viewerAccountId":1,
              "viewerSteamId":"$VIEWER",
              "target":{"steamId":"$TARGET","personaName":"Target"},
              "targetGames":[],
              "viewerGames":[],
              "gameDataVisibility":"PRIVATE",
              "fetchedAt":123
            }
        """.trimIndent()

        val decoded = requireNotNull(SteamProfileViewerCacheCodec.decodeProfile(legacy))

        assertNull(decoded.friendCount)
        assertNull(decoded.groupCount)
        assertNull(decoded.badgeCount)
        assertTrue(decoded.badges.isEmpty())
    }

    @Test
    fun storageCodecBoundsLargeBadgeCatalogsWithoutChangingTheTotal() {
        val snapshot = SteamProfileViewerSnapshot(
            viewerAccountId = 1L,
            viewerSteamId = VIEWER,
            target = SteamProfileSummary(steamId = TARGET, personaName = "Target"),
            targetGames = emptyList(),
            viewerGames = emptyList(),
            gameDataVisibility = SteamProfileGameDataVisibility.AVAILABLE,
            fetchedAt = 123L,
            badgeCount = 400,
            badges = (1..400).map { badgeId ->
                SteamCommunityBadge(
                    badgeId = badgeId,
                    level = 1,
                    xp = 100,
                    completionTime = badgeId.toLong(),
                    scarcity = 0
                )
            }
        )

        val restored = requireNotNull(
            SteamProfileViewerCacheCodec.decodeProfile(
                SteamProfileViewerCacheCodec.encodeProfileForStorage(snapshot)
            )
        )

        assertEquals(250, restored.badges.size)
        assertEquals(400, restored.badgeCount)
    }

    private companion object {
        const val VIEWER = "76561198000000001"
        const val TARGET = "76561198000000002"
    }
}
