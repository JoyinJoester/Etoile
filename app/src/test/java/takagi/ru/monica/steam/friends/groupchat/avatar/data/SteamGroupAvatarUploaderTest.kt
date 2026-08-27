package takagi.ru.monica.steam.friends.groupchat.avatar.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SteamGroupAvatarUploaderTest {
    @Test
    fun parsesOfficialUploadSha() {
        assertArrayEquals(
            ByteArray(20) { it.toByte() },
            parseSteamGroupAvatarSha("{\"sha\":\"000102030405060708090a0b0c0d0e0f10111213\"}")
        )
    }

    @Test
    fun rejectsMalformedUploadSha() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSteamGroupAvatarSha("{\"sha\":\"short\"}")
        }
    }
}
