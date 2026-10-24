package takagi.ru.monica.github.feature.repository

import android.app.Application
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.drawToBitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import takagi.ru.monica.R
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.actions.*
import takagi.ru.monica.github.feature.releases.*
import takagi.ru.monica.ui.theme.EtoileTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepositoryConfirmationIsolationTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    @Before fun setup() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    @After fun cleanup() { controller.pause().stop().destroy() }

    @Test fun branchDeletionConfirmationCannotMoveToAnotherIdWithSameLoginOrReturnLater() {
        val original = RepositoryBranchesUiState("o", "r", "main", viewerAccountId = 1L, viewerLogin = "same-login",
            viewerRole = GithubCollaboratorRole.ADMIN, isLoading = false,
            items = listOf(GithubBranch("feature", "a".repeat(40), false)))
        var state by mutableStateOf(original)
        val actions = mutableListOf<RepositoryBranchesAction>()
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface {
                    RepositoryBranchesScreen(state, actions::add, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_delete_branch)).performClick()
        val warning = label(R.string.github_delete_branch_confirm, "feature")
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.runOnIdle { state = original.copy(viewerAccountId = 2L) }
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.runOnIdle { state = original }
        compose.onNodeWithText(warning).assertDoesNotExist()
        assertTrue(actions.none { it is RepositoryBranchesAction.DeleteBranch })
    }

    @Test fun webhookDeletionConfirmationCannotMoveToAnotherIdWithSameLoginOrReturnLater() {
        val hook = GithubRepositoryWebhook(11, "web", "https://example.com/hook", true,
            listOf("push"), 200, "ok", null)
        val original = RepositoryWebhooksUiState("o", "r", canManage = true, isLoading = false,
            viewerAccountId = 1L, viewerLogin = "same-login", items = listOf(hook))
        var state by mutableStateOf(original)
        val actions = mutableListOf<RepositoryWebhooksAction>()
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface { RepositoryWebhooksScreen(state, actions::add, {}, {}, {}) }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_webhook_delete)).performClick()
        val warning = label(R.string.github_webhook_delete_confirm, hook.url!!)
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.runOnIdle { state = original.copy(viewerAccountId = 2L) }
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.runOnIdle { state = original }
        compose.onNodeWithText(warning).assertDoesNotExist()
        assertTrue(actions.none { it is RepositoryWebhooksAction.Delete })
    }

    @Test fun actionsConfirmationCannotMoveToAnotherAccountWithTheSamePermissions() {
        val run = GithubWorkflowRun(9, 1, "CI", "Build", 12, "push", GithubActionsStatus.COMPLETED,
            GithubActionsConclusion.SUCCESS, "main", "a".repeat(40), GithubUserSummary("actor", null, ""),
            "2026-10-22T00:00:00Z", "2026-10-22T00:01:00Z", null, "https://github.com/o/r/actions/runs/9")
        val original = ActionsRunDetailUiState("o", "r", 9, run = run, isLoadingRun = false,
            isLoadingJobs = false, isLoadingArtifacts = false, viewerLogin = "same",
            viewerRole = GithubCollaboratorRole.ADMIN, viewerAccountId = 1L, journalReady = true)
        var state by mutableStateOf(original)
        val actions = mutableListOf<ActionsRunDetailAction>()
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface { ActionsRunDetailScreen(state, actions::add, {}, {}, {}, enableArtifactDownloads = false) }
            }
        }
        compose.onNodeWithText(label(R.string.github_actions_rerun)).performScrollTo().performClick()
        val warning = label(R.string.github_confirm_run_rerun)
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.runOnIdle { state = original.copy(viewerAccountId = 2L) }
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.runOnIdle { state = original }
        compose.onNodeWithText(warning).assertDoesNotExist()
        assertTrue(actions.none { it is ActionsRunDetailAction.PerformRunAction })
    }

    @Test fun publishingAReleaseRequiresReviewingTheEditorAndExplicitSave() {
        val release = GithubRelease(1, "v1", "main", "One", "Release notes",
            GithubUserSummary("actor", null, ""), true, false,
            "2026-10-22T00:00:00Z", null, "https://github.com/o/r/releases/tag/v1", emptyList())
        var state by mutableStateOf(ReleasesUiState("o", "r", items = listOf(release),
            isLoading = false, viewerAccountId = 1L, canManage = true, journalReady = true))
        val actions = mutableListOf<ReleasesAction>()
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface { ReleasesScreen(state, actions::add, {}, {}, {}, enableAssetAttachments = false) }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_publish_release)).performClick()
        val open = actions.last() as ReleasesAction.OpenEditor
        assertEquals(false, open.asDraft)
        assertTrue(actions.none { it is ReleasesAction.Save })
        compose.runOnIdle { state = state.copy(editor =
            ReleaseEditorDraft(1L, open.releaseId, "v1", "One", "Release notes", "main", open.asDraft ?: true)) }
        compose.onNodeWithText(label(R.string.github_save_release)).assertIsDisplayed()
        assertTrue(actions.none { it is ReleasesAction.Save })
        compose.onNodeWithText(label(R.string.github_save_release)).performClick()
        val save = actions.last() as ReleasesAction.Save
        assertEquals(1L, save.releaseId)
        assertEquals(1L, save.expectedAccountId)
        assertEquals(false, save.isDraft)
    }

    @Test fun workflowToggleAndDispatchCannotInheritAnotherAccountsConfirmation() {
        val original = workflowState(1)
        var state by mutableStateOf(original)
        val actions = mutableListOf<ActionsWorkflowsAction>()
        renderWorkflows({ state }, actions::add)
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_disable_workflow)).performClick()
        val warning = label(R.string.github_confirm_workflow_toggle)
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.runOnIdle { state = original.copy(viewerAccountId = 2) }
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.runOnIdle { state = original }
        compose.onNodeWithText(warning).assertDoesNotExist()
        openWorkflowDispatch()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_ref))).performTextReplacement("private-branch")
        compose.runOnIdle { state = original.copy(viewerAccountId = 2) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle { state = original }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertTrue(actions.none { it is ActionsWorkflowsAction.SetWorkflowEnabled || it is ActionsWorkflowsAction.DispatchWorkflow })
        openWorkflowDispatch()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_ref))).assertTextContains("private-branch")
        compose.onNodeWithText(label(R.string.github_run)).performClick()
        assertEquals(ActionsWorkflowsAction.DispatchWorkflow(7, "private-branch", emptyMap(), 1L), actions.last())
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_disable_workflow)).performClick()
        compose.onNode(hasText(label(R.string.github_disable_workflow)) and hasClickAction()).performClick()
        assertEquals(ActionsWorkflowsAction.SetWorkflowEnabled(7, false, 1L), actions.last())
    }

    @Test fun workflowDispatchNewHostRestoresOwnedFieldsButNeverReopensOrSubmits() {
        verifyDispatchRestoration(1)
    }

    @Test fun workflowDispatchNewAccountCannotRestoreAnotherAccountsFields() {
        verifyDispatchRestoration(2)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun workflowDispatchNarrowLargeFontKeepsInputsAndRunReachable() {
        val actions = mutableListOf<ActionsWorkflowsAction>()
        renderWorkflows({ workflowState(1) }, actions::add, fontScale = 1.5f)
        openWorkflowDispatch()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_ref)))
            .performScrollTo().performTextReplacement("feature/mobile-release")
        compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_inputs_hint)))
            .performScrollTo().performTextReplacement("environment=preview\ndry_run=true")
        compose.onNodeWithText(label(R.string.github_run)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val output = java.io.File("build/reports/acceptance/workflow-dispatch-narrow-large-font.png")
            requireNotNull(output.parentFile).mkdirs()
            output.outputStream().use { stream ->
                controller.get().window.decorView.drawToBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        compose.onNodeWithText(label(R.string.github_run)).performClick()
        assertEquals(ActionsWorkflowsAction.DispatchWorkflow(7, "feature/mobile-release",
            mapOf("environment" to "preview", "dry_run" to "true"), 1L), actions.last())
    }

    private fun verifyDispatchRestoration(restoredAccountId: Long) {
        var state by mutableStateOf(workflowState(1))
        val actions = mutableListOf<ActionsWorkflowsAction>()
        renderWorkflows({ state }, actions::add)
        openWorkflowDispatch()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_ref))).performTextReplacement("private-branch")
        compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_inputs_hint))).performTextReplacement("secret_input=private-value")
        val saved = Bundle()
        compose.runOnIdle { controller.pause().saveInstanceState(saved).stop().destroy() }
        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(saved); parcel.setDataPosition(0)
            parcel.readBundle(ComponentActivity::class.java.classLoader)!!
        } finally { parcel.recycle() }
        state = workflowState(restoredAccountId)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).create(restored).start().resume().visible()
        renderWorkflows({ state }, actions::add)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertTrue(actions.none { it is ActionsWorkflowsAction.DispatchWorkflow })
        openWorkflowDispatch()
        val ref = compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_ref)))
        val inputs = compose.onNode(hasSetTextAction() and hasText(label(R.string.github_actions_inputs_hint)))
        if (restoredAccountId == 1L) {
            ref.assertTextContains("private-branch")
            inputs.assertTextContains("secret_input=private-value")
        } else {
            ref.assertTextContains("main")
            inputs.assertTextEquals(label(R.string.github_actions_inputs_hint), "")
        }
        assertTrue(actions.none { it is ActionsWorkflowsAction.DispatchWorkflow })
    }

    @Test fun uncertainDispatchRequiresFreshConsentAndChangingFieldsInvalidatesIt() {
        val pending = GithubActionOperation(GithubActionKind.DISPATCH, 7)
        val actions = mutableListOf<ActionsWorkflowsAction>()
        renderWorkflows({ workflowState(1).copy(unconfirmedOperations = setOf(pending)) }, actions::add)
        openWorkflowDispatch()
        compose.onNodeWithText(label(R.string.github_run)).performScrollTo().performClick()
        val warning = label(R.string.github_actions_repeat_warning)
        compose.onNodeWithText(warning).assertIsDisplayed()
        assertTrue(actions.none { it is ActionsWorkflowsAction.DispatchWorkflow })
        compose.onNodeWithText(label(R.string.github_cancel)).performClick()
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.github_run)).performScrollTo().performClick()
        compose.onNode(hasText(label(R.string.github_actions_repeat)) and hasClickAction()).performClick()
        assertEquals(ActionsWorkflowsAction.DispatchWorkflow(7, "main", emptyMap(), 1L, true), actions.last())
        assertEquals(1, actions.count { it is ActionsWorkflowsAction.DispatchWorkflow })
    }

    @Test fun pendingWorkflowInspectionAndAcknowledgementNeverDispatch() {
        val pending = GithubActionOperation(GithubActionKind.DISPATCH, 7)
        val actions = mutableListOf<ActionsWorkflowsAction>()
        var inspected = 0
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface { ActionsWorkflowsScreen(workflowState(1).copy(unconfirmedOperations = setOf(pending)),
                    actions::add, {}, { inspected++ }, {}) }
            }
        }
        compose.onNodeWithText(label(R.string.github_actions_acknowledge)).performScrollTo().performClick()
        assertEquals(ActionsWorkflowsAction.Acknowledge(pending, 1L), actions.last())
        compose.onNodeWithText(label(R.string.github_actions_check_latest)).performScrollTo().performClick()
        assertEquals(1, inspected)
        assertTrue(actions.none { it is ActionsWorkflowsAction.DispatchWorkflow || it is ActionsWorkflowsAction.SetWorkflowEnabled })
    }

    @Test fun changedDispatchFieldsDropPendingRepeatConsent() {
        val workflow = workflowState(1).items.single()
        var ref by mutableStateOf("main")
        var inputs by mutableStateOf("environment=preview")
        var accountId by mutableStateOf(1L)
        var submissions = 0
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface { WorkflowDispatchScreen("o/r", workflow, ref, inputs, { ref = it }, { inputs = it }, false,
                    {}, { _, _, _ -> submissions++ }, unconfirmed = true, viewerAccountId = accountId) }
            }
        }
        val warning = label(R.string.github_actions_repeat_warning)
        listOf<() -> Unit>({ ref = "release" }, { inputs = "environment=production" }, { accountId = 2L }).forEach { change ->
            compose.onNodeWithText(label(R.string.github_run)).performScrollTo().performClick()
            compose.onNodeWithText(warning).assertIsDisplayed()
            compose.runOnIdle(change)
            compose.onNodeWithText(warning).assertDoesNotExist()
        }
        assertEquals(0, submissions)
    }

    private fun workflowState(accountId: Long) = ActionsWorkflowsUiState("o", "r", isLoading = false, journalReady = true,
        viewerAccountId = accountId, viewerLogin = "same-login", viewerRole = GithubCollaboratorRole.ADMIN,
        items = listOf(GithubWorkflow(7, "CI", ".github/workflows/ci.yml", GithubWorkflowState.ACTIVE,
            "https://github.com/o/r/actions/workflows/7", null, "2026-10-22T00:00:00Z", "2026-10-22T00:00:00Z")))

    private fun renderWorkflows(state: () -> ActionsWorkflowsUiState, action: (ActionsWorkflowsAction) -> Unit, fontScale: Float = 1f) {
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(density.density, fontScale)) {
                    Surface { ActionsWorkflowsScreen(state(), action, {}, {}, {}) }
                }
            }
        }
    }

    private fun openWorkflowDispatch() {
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.github_actions_dispatch)).performClick()
    }

    private fun label(id: Int, vararg args: Any): String = controller.get().getString(id, *args)
}
