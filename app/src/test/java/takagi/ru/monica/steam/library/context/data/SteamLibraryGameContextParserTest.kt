package takagi.ru.monica.steam.library.context.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudStatus
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamLibraryGameContextParserTest {
    @Test
    fun storeMetadataKeepsDlcIdsAndSteamCloudSupport() {
        val payload = Json.parseToJsonElement(
            """{
              "620":{"success":true,"data":{
                "dlc":[621,622,621],
                "categories":[{"id":2},{"id":23}]
              }}
            }"""
        ).jsonObject

        val metadata = SteamLibraryGameContextParser.parseStoreAppMetadata(620, payload)

        assertEquals(listOf(621, 622), metadata.dlcAppIds)
        assertTrue(metadata.supportsSteamCloud == true)
    }

    @Test
    fun storeMetadataDistinguishesKnownUnsupportedCloud() {
        val payload = Json.parseToJsonElement(
            """{"620":{"success":true,"data":{"categories":[{"id":2}]}}}"""
        ).jsonObject

        val metadata = SteamLibraryGameContextParser.parseStoreAppMetadata(620, payload)

        assertFalse(metadata.supportsSteamCloud ?: true)
    }

    @Test
    fun cloudParserCountsOnlyPersistedFiles() {
        val response = SteamProtoWriter().apply {
            writeUint64(1, 99L)
            writeMessage(2, cloudFile("save-a.sav", timestamp = 1_000L, size = 100L, state = 0))
            writeMessage(2, cloudFile("old.sav", timestamp = 2_000L, size = 999L, state = 1))
            writeMessage(2, cloudFile("save-b.sav", timestamp = 1_500L, size = 50L, state = 0))
            writeString(5, "Desktop")
            writeString(5, "Deck")
            writeUint64(6, 123L)
        }.toByteArray()

        val cloud = SteamLibraryGameContextParser.parseCloud(response)

        assertEquals(SteamLibraryCloudStatus.AVAILABLE, cloud.status)
        assertEquals(2, cloud.fileCount)
        assertEquals(150L, cloud.totalBytes)
        assertEquals(1_500L, cloud.lastUpdatedAtSeconds)
        assertEquals(99L, cloud.currentChangeNumber)
        assertEquals(123L, cloud.appBuildIdHighWaterMark)
        assertEquals(2, cloud.machineCount)
    }

    @Test
    fun emptySuccessfulCloudResponseMeansNoRemoteFiles() {
        val cloud = SteamLibraryGameContextParser.parseCloud(ByteArray(0))

        assertEquals(SteamLibraryCloudStatus.EMPTY, cloud.status)
        assertEquals(0, cloud.fileCount)
    }

    @Test
    fun dlcStoreItemsKeepNameAndLocalizedHeader() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeString(6, "Portal 2 Soundtrack")
                writeVarint(9, 621L)
                writeMessage(30, SteamProtoWriter().apply {
                    writeString(1, "steam/apps/621/\${FILENAME}?t=1")
                    writeString(4, "header_schinese.jpg")
                })
            })
        }.toByteArray()

        val dlc = SteamLibraryGameContextParser.parseDlcStoreItems(response).getValue(621)

        assertEquals("Portal 2 Soundtrack", dlc.name)
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/621/header_schinese.jpg?t=1",
            dlc.headerImageUrl
        )
    }

    private fun cloudFile(
        name: String,
        timestamp: Long,
        size: Long,
        state: Int
    ) = SteamProtoWriter().apply {
        writeString(1, name)
        writeUint64(3, timestamp)
        writeVarint(4, size)
        writeVarint(5, state.toLong())
    }
}
