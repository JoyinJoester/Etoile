package takagi.ru.monica.steam.friends.chat.position.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatImeBottomAnchorPolicyTest {
    @Test
    fun openingImeKeepsTheLatestMessageVisibleWhenConversationWasAtBottom() {
        val result = reduceSteamChatImeAnchor(
            previous = SteamChatImeAnchorState(
                imeVisible = false,
                wasAtBottomBeforeIme = true,
                restored = true
            ),
            imeVisible = true,
            atBottom = false,
            restored = true,
            hasMessages = true
        )

        assertTrue(result.shouldScrollToLatest)
    }

    @Test
    fun openingImePreservesHistoryPositionWhenConversationWasScrolledUp() {
        val result = reduceSteamChatImeAnchor(
            previous = SteamChatImeAnchorState(
                imeVisible = false,
                wasAtBottomBeforeIme = false,
                restored = true
            ),
            imeVisible = true,
            atBottom = false,
            restored = true,
            hasMessages = true
        )

        assertFalse(result.shouldScrollToLatest)
    }

    @Test
    fun everyInsetAnimationLayoutKeepsTheLatestMessageAnchored() {
        val result = reduceSteamChatImeAnchor(
            previous = SteamChatImeAnchorState(
                imeVisible = true,
                wasAtBottomBeforeIme = true,
                followingIme = true,
                restored = true
            ),
            imeVisible = true,
            atBottom = false,
            restored = true,
            hasMessages = true
        )

        assertTrue(result.shouldScrollToLatest)
    }

    @Test
    fun visibleImeDoesNotOverrideAConversationThatIsStillRestoringHistory() {
        val result = reduceSteamChatImeAnchor(
            previous = SteamChatImeAnchorState(
                imeVisible = true,
                wasAtBottomBeforeIme = true,
                restored = false
            ),
            imeVisible = true,
            atBottom = true,
            restored = true,
            hasMessages = true
        )

        assertFalse(result.shouldScrollToLatest)
    }
}
