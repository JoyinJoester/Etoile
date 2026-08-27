package takagi.ru.monica.steam.library

import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamLibraryCloudMetadataTest {
    @Test
    fun storeMetadataReadsPackedSteamCloudCategory() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(9, 620L)
                writeMessage(22, SteamProtoWriter().apply {
                    writePackedVarints(3, listOf(2L, 23L))
                })
            })
        }.toByteArray()

        val metadata = SteamGameLibraryService.parseStoreItems(response).getValue(620)

        assertTrue(metadata.supportsSteamCloud == true)
    }
}
