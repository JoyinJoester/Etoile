package takagi.ru.monica.github.design

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.drawToBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.home.HomeScreen
import takagi.ru.monica.github.feature.starred.StarredUiState
import takagi.ru.monica.github.feature.discussions.DiscussionsScreen
import takagi.ru.monica.github.feature.discussions.DiscussionsUiState
import takagi.ru.monica.github.feature.discussions.DiscussionDetailScreen
import takagi.ru.monica.github.settings.GithubSettingsScreen
import takagi.ru.monica.github.settings.GithubAboutScreen
import takagi.ru.monica.github.settings.GithubLegalScreen
import takagi.ru.monica.gitlab.domain.*
import takagi.ru.monica.gitlab.feature.*
import takagi.ru.monica.ui.components.MarkdownPreviewText
import takagi.ru.monica.github.component.GithubFilterRow
import takagi.ru.monica.github.component.GithubUserRow
import takagi.ru.monica.ui.theme.EtoileTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeDesignRenderingTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @Before fun createHost() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }

    @After fun destroyHost() { controller.pause().stop().destroy() }

    @Test
    fun userIdentityRemainsLeftAlignedBesideAvatarAndFilterSelectionWorks() {
        var selectedIndex = -1
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        var selected by remember { mutableIntStateOf(0) }
                        GithubFilterRow(listOf("Open", "Closed", "All"), selected, {
                            selected = it
                            selectedIndex = it
                        })
                        GithubUserRow("android-maintainer", null, "@android-maintainer", {})
                    }
                }
            }
        }
        compose.onNodeWithText("Closed").performClick()
        compose.runOnIdle { assertEquals(1, selectedIndex) }
        val name = compose.onNodeWithText("android-maintainer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithText("@android-maintainer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Identity must leave space for the avatar", name.left > 60f)
        assertTrue("Identity stays close to the avatar", name.left < 110f)
        assertEquals("Identity text shares a left edge", name.left, handle.left, 1f)
        snapshot("user-identity-light")
    }

    @Test fun homeKeepsSevenOrderedRowsInDarkTheme() { verifyHome(dark = true) }

    @Test fun preferenceRowWrapsWideGlyphSummariesInsteadOfEllipsizingThem() {
        val value = "界".repeat(16)
        var clicks = 0
        render(dark = false) {
            Column(Modifier.padding(16.dp)) {
                takagi.ru.monica.github.component.GithubPreferenceRow(
                    androidx.compose.material.icons.Icons.Default.Settings, "Appearance", value,
                    onClick = { clicks++ })
            }
        }
        val titleBounds = compose.onNodeWithText("Appearance", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val valueNode = compose.onNodeWithText(value, useUnmergedTree = true)
        val valueBounds = valueNode.fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        valueNode.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        snapshot("preference-row-wide-glyphs")
        assertTrue("A wide summary must wrap fully below its title", layouts.isNotEmpty() &&
            layouts.all { !it.hasVisualOverflow && (0 until it.lineCount).none(it::isLineEllipsized) })
        assertEquals(titleBounds.left, valueBounds.left, 1f)
        assertTrue(valueBounds.top >= titleBounds.bottom)
        valueNode.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp-mdpi")
    fun preferenceRowKeepsLargeFontTextReadableAndItsTouchTarget() {
        val value = "System default color scheme"
        render(dark = false, fontScale = 1.5f) {
            Column(Modifier.padding(16.dp)) {
                takagi.ru.monica.github.component.GithubPreferenceRow(
                    androidx.compose.material.icons.Icons.Default.Settings, "Appearance", value, onClick = {})
            }
        }
        val title = compose.onNodeWithText("Appearance", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithText(value, useUnmergedTree = true)
        val bounds = summary.fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, bounds.left, 1f)
        assertTrue(bounds.top >= title.bottom && bounds.right <= 304f)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        summary.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty() && layouts.all { !it.hasVisualOverflow })
        assertTrue(compose.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot.height >= 56f)
    }

    @Test fun preferenceRowMirrorsTheInlineValueInRightToLeftLayouts() {
        render(dark = false) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                Column(Modifier.padding(16.dp)) {
                    takagi.ru.monica.github.component.GithubPreferenceRow(
                        androidx.compose.material.icons.Icons.Default.Settings, "Language", "English", onClick = {})
                }
            }
        }
        val title = compose.onNodeWithText("Language", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("English", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(value.right < title.left)
        assertEquals(title.center.y, value.center.y, 2f)
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp-mdpi")
    fun quotaNoticeKeepsTheWarningAndResetTimeReadableAtLargeFont() {
        render(dark = false, fontScale = 1.5f) {
            takagi.ru.monica.github.component.GithubServiceStatusNotices(
                rateLimits = listOf(GithubRateLimitSnapshot("search", 10, 0, 10, 1_790_000_000)),
                cacheFallback = null, modifier = Modifier.padding(16.dp))
        }
        val title = label(R.string.github_rate_limit_exhausted_search)
        val supporting = compose.onNode(hasText("0 of 10", substring = true))
        listOf(compose.onNodeWithText(title), supporting).forEach { node ->
            node.assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Status text must wrap without clipping or ellipsis", layouts.isNotEmpty() &&
                layouts.all { !it.hasVisualOverflow && (0 until it.lineCount).none(it::isLineEllipsized) })
        }
        snapshot("quota-notice-narrow-large-font")
    }

    @Test
    @Config(qualifiers = "w1000dp-h900dp-mdpi")
    fun homeKeepsSevenOrderedRowsOnWideScreens() { verifyHome(dark = false) }

    private fun verifyHome(dark: Boolean) {
        val opened = mutableListOf<String>()
        render(dark) {
            HomeScreen(GithubSession.SignedIn(githubAccount), StarredUiState(), null,
                onSignIn = {}, onRetrySession = {}, onRetryContributions = {},
                onOpenStarred = { opened += "stars" }, onOpenRepositories = { opened += "repositories" },
                onOpenOrganizations = { opened += "organizations" }, onOpenMyConversations = { opened += it.name },
                onOpenRepository = {}, onOpenExternal = { opened += it })
        }
        val labels = listOf(R.string.github_starred, R.string.github_repositories, R.string.github_organizations,
            R.string.github_my_issues, R.string.github_my_pull_requests, R.string.github_discussions, R.string.github_projects)
        val bounds = labels.map { compose.onNodeWithText(label(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (before, after) ->
            assertEquals("Every work entry stays in the same column", before.left, after.left, 1f)
            assertTrue("Entries retain their existing order", after.top >= before.bottom)
        }
        labels.forEach { compose.onNodeWithText(label(it)).performClick() }
        assertEquals(7, opened.size)
        assertEquals(listOf("stars", "repositories", "organizations", "ISSUES", "PULL_REQUESTS"), opened.take(5))
        snapshot(if (dark) "home-dark" else "home-wide-light")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp-mdpi")
    fun settingsRemainReadableAtLargeFontOnNarrowScreen() {
        var opened = ""
        render(dark = false, fontScale = 1.5f) {
            GithubSettingsScreen(AppSettings(designStyle = DesignStyle.MATERIAL), {}, { opened = "appearance" },
                { opened = "language" }, { opened = "scale" })
        }
        listOf(R.string.github_appearance, R.string.github_language, R.string.github_display_scale).forEach {
            compose.onNodeWithText(label(it)).assertIsDisplayed()
        }
        compose.onNodeWithText(label(R.string.github_language)).performClick()
        assertEquals("language", opened)
        snapshot("settings-narrow-large-font")
    }

    @Test fun aboutOpensBundledPrivacyWithoutRequiringAnAccountOrNetwork() {
        var document by mutableStateOf<String?>(null)
        render(dark = false) {
            if (document == null) GithubAboutScreen({}, { document = it }, {})
            else GithubLegalScreen(document!!, { document = null })
        }
        compose.onNodeWithText(label(R.string.github_privacy)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Accounts and authorization").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Accounts and authorization").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.github_legal_load_failed)).assertDoesNotExist()
        snapshot("privacy-light")
        compose.onNodeWithContentDescription(label(R.string.github_back)).performClick()
        compose.onNodeWithText(label(R.string.github_license)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("GNU GENERAL PUBLIC LICENSE", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.github_legal_load_failed)).assertDoesNotExist()
        val assets = controller.get().assets
        assertTrue(assets.open("legal/LICENSE.txt").bufferedReader().use { it.readText() }.contains("END OF TERMS AND CONDITIONS"))
        val notices = assets.open("legal/THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
        listOf("googlesansflex-OFL.txt", "spacegrotesk-OFL.txt", "spacemono-OFL.txt", "doto-OFL.txt").forEach {
            assertTrue("Offline font notice missing: $it", notices.contains("## $it"))
        }
        assertTrue(notices.contains("SIL OPEN FONT LICENSE"))
        assertTrue("Offline dependency inventory missing", notices.contains("org.jsoup:jsoup"))
        assertTrue("Offline Apache license missing", notices.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        assertTrue("Offline MIT attribution missing", notices.contains("Jonathan Hedley"))
        assertTrue("Offline Rust notices missing", notices.contains("jni-sys"))
    }

    @Test fun discussionsUseProductionListAndOpenSelectedItem() {
        val discussion = GithubDiscussion("sample", 42, "Improve the Android reading experience", "Readable discussions with clear hierarchy.",
            "https://example.com/discussion", "maintainer", GithubDiscussionCategory("q", "Questions", true), 12, false)
        var opened: GithubDiscussion? = null
        render(dark = true) {
            DiscussionsScreen(DiscussionsUiState(items = listOf(discussion), repositoryId = "sample"), {}, {}, {}, {}, { opened = it })
        }
        compose.onNodeWithText(discussion.title).performClick()
        assertEquals(discussion, opened)
        snapshot("discussions-dark")
    }

    @Test fun gitLabCommentConfirmationRetainsDraftAfterFailureAndClosesOnlyAfterSuccess() {
        var state by mutableStateOf(GitLabConversationState(account = gitlabAccount, kind = GitLabConversationKind.ISSUE, item = conversation))
        val actions = mutableListOf<GitLabConversationAction>()
        render(dark = false) {
            GitLabConversationScreen(state, 42, 7, { action ->
                actions += action
                when (action) {
                    is GitLabConversationAction.Comment -> state = state.copy(comment = action.value)
                    is GitLabConversationAction.Send -> state = state.copy(writing = true, writeError = null, writeSucceeded = false)
                    else -> Unit
                }
            }, {}, {})
        }
        compose.onNodeWithText(label(R.string.gitlab_send_comment)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("/close")
        compose.onAllNodesWithText(label(R.string.gitlab_send_comment)).filter(hasClickAction()).onLast().performClick()
        assertTrue(actions.none { it is GitLabConversationAction.Send })
        compose.onNodeWithText(label(R.string.github_cancel)).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("/close")
        compose.onAllNodesWithText(label(R.string.gitlab_send_comment)).filter(hasClickAction()).onLast().performClick()
        compose.onAllNodesWithText(label(R.string.gitlab_submit_commands)).filter(hasClickAction()).onLast().performClick()
        assertEquals(true, (actions.last() as GitLabConversationAction.Send).confirmedQuickActions)
        compose.runOnIdle { state = state.copy(writing = false, writeError = GitLabReadError.NETWORK) }
        compose.onNode(hasSetTextAction()).assertTextContains("/close")
        snapshot("gitlab-comment-failure-light")
        compose.onAllNodesWithText(label(R.string.gitlab_send_comment)).filter(hasClickAction()).onLast().performClick()
        compose.onAllNodesWithText(label(R.string.gitlab_submit_commands)).filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { state = state.copy(writing = false, comment = "", writeSucceeded = true) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText(conversation.title, substring = false).assertIsDisplayed()
    }

    @Test fun gitLabManagementStillRequiresConfirmationInMoreMenu() {
        val actions = mutableListOf<GitLabConversationAction>()
        render(dark = true) {
            GitLabConversationScreen(GitLabConversationState(account = gitlabAccount, kind = GitLabConversationKind.ISSUE, item = conversation),
                42, 7, { actions += it }, {}, {})
        }
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.gitlab_close_conversation)).performClick()
        assertTrue(actions.isEmpty())
        compose.onAllNodesWithText(label(R.string.gitlab_close_conversation)).filter(hasClickAction()).onLast().performClick()
        assertEquals(GitLabConversationAction.ChangeState("opened"), actions.single())
        snapshot("gitlab-detail-dark")
    }

    @Test fun gitLabJobMenuConfirmsRetryAndMainRowOpensLog() {
        val job = GitLabJob(99, 12, "android-unit-tests", "test", "failed")
        val actions = mutableListOf<GitLabPipelineAction>()
        render(dark = false) {
            GitLabPipelineScreen(GitLabPipelineState(account = gitlabAccount,
                pipeline = GitLabPipeline(12, "main", "a".repeat(40), "failed", "2026-10-21"), jobs = listOf(job)),
                42, 12, { actions += it }, {}, {})
        }
        compose.onNodeWithText(job.name).performClick()
        assertEquals(GitLabPipelineAction.Trace(job), actions.single())
        actions.clear()
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onAllNodesWithText(label(R.string.gitlab_retry_run)).filter(hasClickAction()).onLast().performClick()
        assertTrue(actions.isEmpty())
        compose.onAllNodesWithText(label(R.string.gitlab_retry_run)).filter(hasClickAction()).onLast().performClick()
        assertEquals(GitLabPipelineAction.Job(job, GitLabRunAction.RETRY), actions.single())
        snapshot("gitlab-pipeline-light")
    }

    @Test fun gitLabJobListRetainsReadingPositionAfterClosingLog() {
        val jobs = (1..60).map { GitLabJob(it.toLong(), 12, "android-job-$it", "test", "success") }
        var state by mutableStateOf(GitLabPipelineState(account = gitlabAccount,
            pipeline = GitLabPipeline(12, "main", "a".repeat(40), "success", "2026-10-21"), jobs = jobs))
        render(dark = false) {
            GitLabPipelineScreen(state, 42, 12, { action ->
                state = when (action) {
                    is GitLabPipelineAction.Trace -> state.copy(traceJob = action.job,
                        trace = (1..80).joinToString("\n") { "Log output $it" })
                    GitLabPipelineAction.CloseTrace -> state.copy(traceJob = null, trace = null)
                    else -> state
                }
            }, {}, {})
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        val before = compose.onNodeWithText("android-job-30").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("android-job-30").performClick()
        compose.onNodeWithText("Log output 1").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(50)
        compose.onNodeWithContentDescription(label(R.string.github_back)).performClick()
        val after = compose.onNodeWithText("android-job-30").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
    }

    @Test fun markdownRendersHeadingAndTableInsteadOfRawMarkup() {
        render(dark = false) {
            MarkdownPreviewText("# Native reading preview\n\n**Readable Markdown**\n\n| Platform | Status |\n| --- | --- |\n| GitHub | Ready |\n| GitLab | Ready |\n\n> Review before publishing\n\n```kotlin\nval readable = true\n```", modifier = Modifier.padding(16.dp))
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Native reading preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Native reading preview").assertIsDisplayed()
        compose.onNodeWithText("# Native reading preview").assertDoesNotExist()
        snapshot("markdown-light")
    }

    @Test fun discussionVirtualizesCommentsAndRetainsThreadDraftAndReadingPosition() {
        var replyLoads = 0
        val discussion = GithubDiscussion("long", 42, "A long discussion", "A short opening body", "https://example.com/42", "maintainer",
            GithubDiscussionCategory("q", "Questions", true), 80, false)
        val comments = (1..80).map { GithubDiscussionComment("c$it", "Comment body $it", "author-$it", false) }
        render(dark = false) {
            DiscussionDetailScreen(discussion, onEdit = { _, _ -> Result.success(discussion) }, onOpenLink = {}, signedIn = true,
                loadComments = { Result.success(GithubDiscussionComments(comments, null)) }, onMarkAnswer = { _, _ -> Result.success(Unit) },
                loadReplies = { _, _ -> replyLoads++; Result.success(GithubDiscussionComments(emptyList(), null)) },
                replyToComment = { _, _ -> Result.success("reply") }, editComment = { _, _ -> Result.success(comments.first()) },
                deleteComment = { Result.success(Unit) }, onReply = { Result.success("reply") }, onBack = {})
        }
        compose.onNodeWithText("author-1").assertIsDisplayed()
        compose.onNodeWithText("author-80").assertDoesNotExist()
        compose.onAllNodesWithText(label(R.string.discussion_thread_replies)).onFirst().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Keep this thread draft")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(70)
        compose.onNodeWithText("author-70").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNode(hasSetTextAction()).assertTextContains("Keep this thread draft")
        assertEquals("Recycled comments reuse their loaded reply connection", 1, replyLoads)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(70)
        val before = compose.onNodeWithText("author-70").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(label(R.string.discussion_reply)).performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.github_back)).performClick()
        val after = compose.onNodeWithText("author-70").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        snapshot("discussion-long-light")
    }

    @Test fun discussionUncertainReplyRequiresExplicitConfirmationBeforeResending() {
        var writes = 0
        val discussion = GithubDiscussion("D", 7, "Review before resending", "Opening body",
            "https://github.com/o/r/discussions/7", "owner", GithubDiscussionCategory("Q", "Q&A", true), 0, false)
        render(dark = false) {
            DiscussionDetailScreen(discussion, { _, _ -> Result.success(discussion) }, {}, true,
                loadComments = { Result.success(GithubDiscussionComments(emptyList(), null)) },
                onMarkAnswer = { _, _ -> error("unused") },
                loadReplies = { _, _ -> error("unused") }, replyToComment = { _, _ -> error("unused") },
                editComment = { _, _ -> error("unused") }, deleteComment = { error("unused") },
                onReply = { writes++; Result.success("sent") }, onBack = {}, viewerAccountId = 1,
                draft = takagi.ru.monica.github.feature.discussions.DiscussionDetailDraft(reply = "Possibly sent"),
                unconfirmedOperations = setOf("reply"))
        }
        compose.onNodeWithContentDescription(label(R.string.discussion_reply)).performClick()
        compose.onNodeWithText(label(R.string.discussion_unconfirmed)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.github_submit_comment)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithText(label(R.string.github_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNode(hasSetTextAction()).assertTextContains("Possibly sent")
        compose.onNodeWithText(label(R.string.github_submit_comment)).performScrollTo().performClick()
        compose.onAllNodesWithText(label(R.string.discussion_reply)).filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun discussionUncertainThreadReplyRequiresConfirmationAndRefreshOnlyReads() {
        var writes = 0
        var reads = 0
        var revision by mutableLongStateOf(0)
        render(dark = false) {
            val scope = rememberCoroutineScope()
            val state = remember {
                takagi.ru.monica.github.feature.discussions.DiscussionThreadState(
                    takagi.ru.monica.github.feature.discussions.DiscussionCommentsLoader(scope) {
                        reads++; Result.success(GithubDiscussionComments(emptyList(), null))
                    }, expanded = true, draft = "Possibly sent nested reply")
            }
            Column {
                takagi.ru.monica.github.feature.discussions.DiscussionReplies(state, scope, true,
                    send = { writes++; Result.success("sent") }, onOpenLink = {},
                    delete = { error("unused") },
                    viewerAccountId = 1, uncertainReply = true, refreshKey = revision, threadId = "C1")
            }
        }
        compose.runOnIdle { assertEquals(1, reads); revision++ }
        compose.runOnIdle { assertEquals(2, reads); assertEquals(0, writes) }
        val reply = label(R.string.discussion_reply)
        compose.onAllNodesWithText(reply).filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithText(label(R.string.github_cancel)).performClick()
        compose.onAllNodesWithText(reply).filter(hasClickAction()).onLast().performClick()
        compose.onAllNodesWithText(reply).filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun discussionAnswerChangesRequireConfirmationAndAccountChangesInvalidateIt() {
        var accountId by mutableStateOf(1L)
        var writes = 0
        var answer = false
        val discussion = GithubDiscussion("D", 7, "Confirm an answer", "Opening body",
            "https://github.com/o/r/discussions/7", "same-login", GithubDiscussionCategory("Q", "Q&A", true), 1, false)
        render(dark = false) {
            DiscussionDetailScreen(discussion, { _, _ -> Result.success(discussion) }, {}, true,
                loadComments = { Result.success(GithubDiscussionComments(listOf(
                    GithubDiscussionComment("C", "Candidate answer", "same-login", answer, true, true)), null)) },
                onMarkAnswer = { _, accepted -> writes++; answer = accepted; Result.success(Unit) },
                loadReplies = { _, _ -> Result.success(GithubDiscussionComments(emptyList(), null)) },
                replyToComment = { _, _ -> Result.success("new") },
                editComment = { _, _ -> error("unused") }, deleteComment = { error("unused") },
                onReply = { error("unused") }, onBack = {}, viewerAccountId = accountId)
        }
        val mark = label(R.string.discussion_mark_answer)
        val unmark = label(R.string.discussion_unmark_answer)
        val message = label(R.string.discussion_answer_confirm)
        compose.onNodeWithText(mark).performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        assertEquals(0, writes)
        compose.runOnIdle { accountId = 2 }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { accountId = 1 }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText(mark).performClick()
        compose.onNodeWithText(label(R.string.github_cancel)).performClick()
        assertEquals(0, writes)
        compose.onNodeWithText(mark).performClick()
        compose.onAllNodesWithText(mark).onLast().performClick()
        compose.runOnIdle { assertEquals(1, writes); assertTrue(answer) }
        compose.onNodeWithText(unmark).performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        assertEquals(1, writes)
        compose.onAllNodesWithText(unmark).onLast().performClick()
        compose.runOnIdle { assertEquals(2, writes); assertTrue(!answer) }
    }

    @Test fun discussionCleanCommentEditorUsesLatestBodyWhileTypedDraftSurvivesCancel() {
        var comment by mutableStateOf(GithubDiscussionComment("C", "Original body", "author", false, canEdit = true))
        var revision by mutableLongStateOf(0)
        var writes = 0
        val discussion = GithubDiscussion("D", 7, "Edit a comment", "Opening body", "https://github.com/o/r/discussions/7",
            "owner", GithubDiscussionCategory("Q", "Q&A", true), 1, false)
        render(dark = false) {
            DiscussionDetailScreen(discussion, { _, _ -> error("unused") }, {}, true,
                loadComments = { Result.success(GithubDiscussionComments(listOf(comment), null)) },
                onMarkAnswer = { _, _ -> error("unused") }, loadReplies = { _, _ -> error("unused") },
                replyToComment = { _, _ -> error("unused") }, editComment = { _, _ -> writes++; Result.success(comment) },
                deleteComment = { error("unused") }, onReply = { error("unused") }, onBack = {}, refreshKey = revision)
        }
        val edit = label(R.string.discussion_edit_comment)
        compose.onNodeWithText(edit).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Original body")
        compose.onNodeWithContentDescription(label(R.string.github_back)).performClick()
        compose.runOnIdle { comment = comment.copy(body = "Server updated body"); revision++ }
        compose.onNodeWithText(edit).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Server updated body")
        compose.onNode(hasSetTextAction()).performTextReplacement("Keep typed draft")
        compose.onNodeWithContentDescription(label(R.string.github_back)).performClick()
        compose.runOnIdle { comment = comment.copy(body = "Newer server body"); revision++ }
        compose.onNodeWithText(edit).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Keep typed draft")
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun discussionDeleteConfirmationDoesNotRestoreWithSavedPageState() {
        var shown by mutableStateOf(true)
        var writes = 0
        val comment = GithubDiscussionComment("C", "Original comment", "author", false, canDelete = true)
        render(dark = false) {
            val holder = rememberSaveableStateHolder()
            if (shown) holder.SaveableStateProvider("same-account-and-comment") {
                takagi.ru.monica.github.feature.discussions.DiscussionCommentActions(comment,
                    delete = { writes++; Result.success(Unit) }, onDeleted = {})
            }
        }
        val delete = label(R.string.discussion_delete_comment)
        val message = label(R.string.discussion_delete_comment_confirm)
        compose.onNodeWithText(delete).performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { shown = false }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { shown = true }
        compose.onNodeWithText(message).assertDoesNotExist()
        assertEquals(0, writes)
        compose.onNodeWithText(delete).performClick()
        compose.onAllNodesWithText(delete).onLast().performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun discussionDeletionSurvivesRecyclingAndBlocksBackUntilItsResultIsApplied() {
        val completion = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
        var deletes = 0
        var backs = 0
        val discussion = GithubDiscussion("D", 7, "Long discussion", "Opening body",
            "https://github.com/o/r/discussions/7", "owner", GithubDiscussionCategory("Q", "Q&A", true), 50, false)
        val comments = (1..50).map { GithubDiscussionComment("C$it", "Body $it", "author-$it", false, canDelete = true) }
        render(dark = false) {
            DiscussionDetailScreen(discussion, { _, _ -> Result.success(discussion) }, {}, true,
                loadComments = { Result.success(GithubDiscussionComments(comments, null)) },
                onMarkAnswer = { _, _ -> error("unused") },
                loadReplies = { _, _ -> Result.success(GithubDiscussionComments(emptyList(), null)) },
                replyToComment = { _, _ -> error("unused") }, editComment = { _, _ -> error("unused") },
                deleteComment = { deletes++; completion.await() }, onReply = { error("unused") },
                onBack = { backs++ }, viewerAccountId = 1)
        }
        val delete = label(R.string.discussion_delete_comment)
        compose.onAllNodesWithText(delete).onFirst().performClick()
        compose.onAllNodesWithText(delete).onLast().performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(35)
        compose.onNodeWithText(label(R.string.discussion_delete_comment_confirm)).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.discussion_back)).performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        compose.runOnIdle { completion.complete(Result.success(Unit)) }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("author-2").assertIsDisplayed()
        compose.onNodeWithText("author-1").assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.discussion_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(1, deletes) }
    }

    @Test
    @Config(qualifiers = "w320dp-h760dp-mdpi")
    fun discussionEditUsesAFullPageAndKeepsDraftOnFailureAtLargeFont() {
        val discussion = GithubDiscussion("D", 7, "Original title", "Original body",
            "https://github.com/o/r/discussions/7", "owner", GithubDiscussionCategory("Q", "Q&A", true), 0, false, canEdit = true)
        var draft by mutableStateOf(takagi.ru.monica.github.feature.discussions.DiscussionDetailDraft())
        var saves = 0
        render(dark = false, fontScale = 1.4f) {
            DiscussionDetailScreen(discussion, onEdit = { _, _ -> saves++; Result.failure(java.io.IOException("response lost")) },
                onOpenLink = {}, signedIn = true,
                loadComments = { Result.success(GithubDiscussionComments(emptyList(), null)) },
                onMarkAnswer = { _, _ -> error("unused") },
                loadReplies = { _, _ -> Result.success(GithubDiscussionComments(emptyList(), null)) },
                replyToComment = { _, _ -> error("unused") }, editComment = { _, _ -> error("unused") },
                deleteComment = { error("unused") }, onReply = { error("unused") }, onBack = {}, draft = draft,
                onEditDraftChange = { title, body -> draft = draft.copy(editTitle = title, editBody = body) })
        }
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.discussion_edit)).performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("Edited title")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("Keep edited body")
        compose.runOnIdle { assertEquals("Edited title", draft.editTitle); assertEquals("Keep edited body", draft.editBody) }
        compose.onNodeWithText(label(R.string.discussion_save)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.discussion_edit_error)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithContentDescription(label(R.string.discussion_back)).performClick()
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.discussion_edit)).performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().assertTextContains("Edited title")
        compose.onAllNodes(hasSetTextAction()).onLast().assertTextContains("Keep edited body")
    }

    @Test fun gitLabRendersMarkdownAndRestoresCommentReadingPositionAfterEditing() {
        val item = conversation.copy(description = "# Rendered GitLab description", webUrl = "https://gitlab.example.com/group/repo/-/issues/7")
        var state by mutableStateOf(GitLabConversationState(account = gitlabAccount, kind = GitLabConversationKind.ISSUE, item = item,
            notes = (1..60).map { GitLabNote(it.toLong(), "Body $it", "reader-$it", "today", false) }))
        render(dark = true) {
            GitLabConversationScreen(state, 42, 7, { action ->
                if (action is GitLabConversationAction.Editing) state = state.copy(editing = action.open)
            }, {}, {})
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Rendered GitLab description").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Rendered GitLab description").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(25)
        val before = compose.onAllNodes(hasText("reader-", substring = true)).onFirst().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.gitlab_edit)).performClick()
        compose.onNodeWithText(label(R.string.gitlab_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.github_back)).performClick()
        val after = compose.onAllNodes(hasText("reader-", substring = true)).onFirst().fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
    }

    @Test fun gitLabMarkdownFileUsesTheSharedReadingLayout() {
        render(dark = false) {
            GitLabProjectScreen(GitLabProjectUiState(account = gitlabAccount,
                project = GitLabProject(42, "Client", "group/client", "", "public", "main", 12, 2, false),
                revision = GitLabRevision("main", "a".repeat(40)), filePath = "docs/README.md",
                file = GitLabFile("docs/README.md", "# GitLab file preview\n\n| Platform | Status |\n| --- | --- |\n| GitLab | Ready |", 100)), {}, {}, {})
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("GitLab file preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("GitLab file preview").assertIsDisplayed()
        compose.onNodeWithText("# GitLab file preview").assertDoesNotExist()
        snapshot("gitlab-markdown-file-light")
    }

    private fun render(dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
        controller.get().setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                EtoileTheme(darkTheme = dark, designStyle = DesignStyle.MATERIAL) {
                    Surface(Modifier.fillMaxSize(), content = content)
                }
            }
        }
    }

    private fun label(id: Int) = controller.get().getString(id)

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val file = File("build/reports/native-design/$name.png")
        file.parentFile?.mkdirs()
        val bitmap = compose.runOnIdle { controller.get().window.decorView.drawToBitmap() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private val githubAccount = GithubAccount(1, "maintainer", "Maintainer", null, "", "https://example.com/maintainer", 42, 120, 30)
        private val gitlabAccount = GitLabAccount(1, "https://gitlab.example.com", "maintainer", "Maintainer", "https://gitlab.example.com/maintainer")
        private val conversation = GitLabConversation(7, 7, "Keep the client readable", "Review the current implementation and improve its information hierarchy.", "opened", "maintainer", 1, "2026-10-21")
    }
}
