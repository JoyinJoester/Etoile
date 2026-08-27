package takagi.ru.monica.steam.friends.chat.actions.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamChatMessageActionPositionTest {
    @Test
    fun anchorsPopupBelowOrAboveTheMessageBubble() {
        val below = MessageAnchoredPositionProvider(edgeMargin = 16, anchorGap = 12)
            .calculatePosition(
                anchorBounds = IntRect(80, 400, 380, 520),
                windowSize = IntSize(1080, 1920),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(300, 400)
            )
        assertEquals(IntOffset(80, 532), below)

        val above = MessageAnchoredPositionProvider(edgeMargin = 16, anchorGap = 12)
            .calculatePosition(
                anchorBounds = IntRect(700, 1700, 1040, 1800),
                windowSize = IntSize(1080, 1920),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(300, 400)
            )
        assertEquals(IntOffset(740, 1288), above)
    }
}
