package takagi.ru.monica.github.design

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import takagi.ru.monica.R
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.releases.*
import takagi.ru.monica.ui.theme.EtoileTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeReleaseSafetyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var host: ActivityController<ComponentActivity>
    private val change = GithubActionOperation(GithubActionKind.RELEASE_CHANGE, 7)
    private val release = GithubRelease(7, "v1", "main", "Version one", "Notes", GithubUserSummary("owner", null, ""),
        false, false, "2026-10-21", null, "https://github.com/o/r/releases/tag/v1", emptyList())

    @Before fun setup() { host = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    @After fun teardown() { host.pause().stop().destroy() }

    @Test fun detailExplainsUncertainOperationAndAcknowledgesWithoutDeleting() {
        val actions = mutableListOf<ReleaseDetailAction>()
        render {
            ReleaseDetailScreen(ReleaseDetailUiState("o", "r", ReleaseReference.Id(7), release,
                isLoading = false, viewerAccountId = 1, canManage = true, journalReady = true,
                unconfirmedOperations = setOf(change)), actions::add, {}, {})
        }
        compose.onNodeWithText(label(R.string.github_release_uncertain)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.github_actions_acknowledge)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ReleaseDetailAction.Acknowledge(change, 1)), actions) }
    }

    @Test fun editorShowsHistoryFailureAndDisablesSave() {
        render {
            ReleasesScreen(ReleasesUiState("o", "r", isLoading = false, canManage = true,
                journalUnavailable = true, editor = ReleaseEditorDraft(1, tagName = "v2")), {}, {}, {}, {}, false)
        }
        compose.onNodeWithText(label(R.string.github_actions_journal_error)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.github_save_release)).assertIsNotEnabled()
    }

    @Test fun warningsScrollWithReleaseListInsteadOfHidingItsContents() {
        render {
            ReleasesScreen(ReleasesUiState("o", "r", items = listOf(release), isLoading = false, journalReady = true,
                unconfirmedOperations = (1L..12L).map { change.copy(targetId = it) }.toSet()), {}, {}, {}, {}, false)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Version one"))
        compose.onNodeWithText("Version one").assertIsDisplayed()
    }

    @Test fun repeatDeleteUsesOneDialogAndCancelDoesNotRetainConsent() {
        val actions = mutableListOf<ReleasesAction>()
        render {
            ReleasesScreen(ReleasesUiState("o", "r", items = listOf(release), isLoading = false,
                canManage = true, journalReady = true, viewerAccountId = 1, unconfirmedOperations = setOf(change)),
                actions::add, {}, {}, {}, false)
        }
        openDelete()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.github_delete_release_confirm)).assertIsDisplayed()
        compose.onNode(hasText(label(R.string.github_delete_release)) and hasClickAction()).performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.github_release_repeat_warning)).assertIsDisplayed()
        compose.onNode(hasText(label(R.string.github_cancel)) and hasClickAction()).performClick()
        compose.runOnIdle { assertTrue(actions.none { it is ReleasesAction.Delete }) }
        openDelete()
        compose.onNodeWithText(label(R.string.github_delete_release_confirm)).assertIsDisplayed()
        compose.onNode(hasText(label(R.string.github_delete_release)) and hasClickAction()).performClick()
        compose.onNode(hasText(label(R.string.github_actions_repeat)) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(listOf(ReleasesAction.Delete(7, 1, true)), actions.filterIsInstance<ReleasesAction.Delete>()) }
    }

    @Test fun losingPermissionInvalidatesPendingDeleteConfirmation() {
        var state by mutableStateOf(ReleasesUiState("o", "r", items = listOf(release), isLoading = false,
            canManage = true, journalReady = true, viewerAccountId = 1, unconfirmedOperations = setOf(change)))
        val actions = mutableListOf<ReleasesAction>()
        render { ReleasesScreen(state, actions::add, {}, {}, {}, false) }
        openDelete()
        compose.onNode(hasText(label(R.string.github_delete_release)) and hasClickAction()).performClick()
        compose.runOnIdle { state = state.copy(canManage = false) }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnIdle { state = state.copy(canManage = true) }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnIdle { assertTrue(actions.none { it is ReleasesAction.Delete }) }
    }

    @Test fun assetRemovalRequiresFreshRepeatConsentInOneDialog() {
        val operation = GithubActionOperation(GithubActionKind.RELEASE_ASSET_DELETE, 9)
        val asset = GithubReleaseAsset(9, "client.apk", null, "application/octet-stream", 123, 0, "today", "https://example.invalid/client.apk")
        val actions = mutableListOf<ReleaseDetailAction>()
        render {
            ReleaseDetailScreen(ReleaseDetailUiState("o", "r", ReleaseReference.Id(7), release.copy(assets = listOf(asset)),
                isLoading = false, viewerAccountId = 1, canManage = true, journalReady = true,
                unconfirmedOperations = setOf(operation)), actions::add, {}, {})
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(label(R.string.github_more_actions)))
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_release_remove_asset)).performClick()
        compose.onNode(hasText(label(R.string.github_release_remove_asset)) and hasClickAction()).performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.github_release_repeat_warning)).assertIsDisplayed()
        compose.onNode(hasText(label(R.string.github_actions_repeat)) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(listOf(ReleaseDetailAction.RemoveAsset(9, 1, true)), actions) }
    }

    @Test fun editingBodyAfterRepeatPromptInvalidatesSaveConsent() {
        var state by mutableStateOf(ReleasesUiState("o", "r", isLoading = false, canManage = true,
            journalReady = true, viewerAccountId = 1, editor = ReleaseEditorDraft(1, releaseId = 7, tagName = "v1", body = "before"),
            unconfirmedOperations = setOf(change)))
        val actions = mutableListOf<ReleasesAction>()
        render { ReleasesScreen(state, actions::add, {}, {}, {}, false) }
        compose.onNodeWithText(label(R.string.github_save_release)).performClick()
        compose.onNodeWithText(label(R.string.github_release_repeat_warning)).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(editor = state.editor!!.copy(body = "after")) }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnIdle { assertTrue(actions.none { it is ReleasesAction.Save }) }
        compose.onNodeWithText(label(R.string.github_save_release)).performClick()
        compose.onNode(hasText(label(R.string.github_actions_repeat)) and hasClickAction()).performClick()
        compose.runOnIdle {
            val saved = actions.filterIsInstance<ReleasesAction.Save>().single()
            assertEquals("after", saved.body)
            assertTrue(saved.repeatConfirmed)
        }
    }

    private fun openDelete() {
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.github_delete_release)).performClick()
    }
    private fun label(id: Int) = host.get().getString(id)
    private fun render(content: @Composable () -> Unit) {
        host.get().setContent { EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
            Surface(Modifier.fillMaxSize(), content = content)
        } }
    }
}
