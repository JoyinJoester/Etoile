package takagi.ru.monica.steam.friends.chat.richmedia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentTarget

class SteamChatAttachmentTargetFieldsTest {
    @Test
    fun friendTargetUsesTheOfficialFriendCommitFields() {
        assertEquals(
            listOf(
                "friend_steamid" to "76561198000000001",
                "spoiler" to "1"
            ),
            SteamChatAttachmentTarget.Friend("76561198000000001")
                .commitFields(spoiler = true)
        )
    }

    @Test
    fun groupRoomTargetUsesTheOfficialGroupCommitFields() {
        assertEquals(
            listOf(
                "chat_group_id" to "123456789012345678",
                "chat_id" to "987654321098765432",
                "spoiler" to "0"
            ),
            SteamChatAttachmentTarget.GroupRoom(
                groupId = "123456789012345678",
                chatId = "987654321098765432"
            ).commitFields(spoiler = false)
        )
    }

    @Test
    fun invalidTargetsFailBeforeAnyUploadStarts() {
        assertThrows(IllegalArgumentException::class.java) {
            SteamChatAttachmentTarget.Friend("123").commitFields(false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SteamChatAttachmentTarget.GroupRoom("-1", "2").commitFields(false)
        }
    }
}
