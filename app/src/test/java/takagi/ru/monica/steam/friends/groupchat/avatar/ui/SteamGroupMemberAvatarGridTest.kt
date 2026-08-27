package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamGroupMemberAvatarGridTest {
    @Test
    fun arrangesOneToNineMembersLikeACompactGroupAvatar() {
        assertEquals(listOf(1), steamGroupAvatarRows(1))
        assertEquals(listOf(2), steamGroupAvatarRows(2))
        assertEquals(listOf(1, 2), steamGroupAvatarRows(3))
        assertEquals(listOf(2, 2), steamGroupAvatarRows(4))
        assertEquals(listOf(2, 3), steamGroupAvatarRows(5))
        assertEquals(listOf(3, 3), steamGroupAvatarRows(6))
        assertEquals(listOf(1, 3, 3), steamGroupAvatarRows(7))
        assertEquals(listOf(2, 3, 3), steamGroupAvatarRows(8))
        assertEquals(listOf(3, 3, 3), steamGroupAvatarRows(9))
    }

    @Test
    fun limitsTheCompositeAvatarToNineMembers() {
        assertEquals(9, steamGroupAvatarRows(14).sum())
    }
}
