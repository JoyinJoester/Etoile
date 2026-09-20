package takagi.ru.monica.github.feature.discussions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscussionSummaryTest {
    @Test fun formatsHeadingsListsAndBlankLines() {
        assertEquals("使用方法 支持 Markdown 打开项目 参与讨论",
            discussionSummary("## 使用方法\n\n支持 **Markdown**\n\n- 打开项目\n- 参与讨论"))
    }

    @Test fun showsLinkLabelAndPreservesCodeOperators() {
        assertEquals("阅读 指南 使用 a * b 和 snake_case()",
            discussionSummary("阅读 [指南](https://example.com) 使用 `a * b` 和 `snake_case()`"))
    }

    @Test fun preservesFencedCodeWithoutLanguageOrFence() {
        assertEquals("val x = a * b", discussionSummary("```kotlin\nval x = a * b\n```"))
    }

    @Test fun emptyFormattingDoesNotCreatePreview() {
        assertEquals("", discussionSummary("\n\n---\n\n"))
    }

    @Test fun preservesLiteralBracketsAndImageDescription() {
        assertEquals("values[0] screenshot", discussionSummary("values[0] ![screenshot](https://example.com/image.png)"))
    }

    @Test fun boundsLongBodiesWithoutBreakingEmoji() {
        val preview = discussionSummary("a".repeat(239) + "😀" + "b".repeat(10_000))
        assertEquals("a".repeat(239) + "…", preview)
        assertTrue(discussionSummary("a".repeat(10_000)).length <= 241)
    }
}
