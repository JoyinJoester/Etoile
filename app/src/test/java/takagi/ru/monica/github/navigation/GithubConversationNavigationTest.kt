package takagi.ru.monica.github.navigation

import android.app.Application
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
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
import takagi.ru.monica.github.GithubConversationNavScope
import takagi.ru.monica.github.githubConversationsGraph
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.auth.GithubSessionUiState
import takagi.ru.monica.ui.theme.EtoileTheme
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Exercises the production NavGraph with real NavBackStackEntries, lifecycle and saved state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GithubConversationNavigationTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var nav: NavHostController
    private val fixtures = ConversationNavigationFixtures()
    private var createdDestinations = 0

    @Before fun createHost() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        render()
    }
    @After fun destroyHost() { controller.pause().stop().destroy() }

    private fun render() {
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface(Modifier.fillMaxSize()) {
                    val navigation = rememberNavController()
                    nav = navigation
                    val session by fixtures.sessions.collectAsState()
                    val latest = rememberUpdatedState(session)
                    val scope = remember(navigation) {
                        object : GithubConversationNavScope {
                            override val dependencies = fixtures
                            override val navController = navigation
                            override val sessionState get() = GithubSessionUiState(session = latest.value)
                            override val openUrl: (String) -> Unit = { error("Unexpected external link") }
                        }
                    }
                    DisposableEffect(navigation) {
                        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                            if (destination.hasRoute<GithubPullRequestRoute>()) createdDestinations++
                        }
                        navigation.addOnDestinationChangedListener(listener)
                        onDispose { navigation.removeOnDestinationChangedListener(listener) }
                    }
                    NavHost(navigation, startDestination = GithubHomeRoute,
                        enterTransition = { GithubNavigationTransitions.enter() },
                        exitTransition = { GithubNavigationTransitions.exit() },
                        popEnterTransition = { GithubNavigationTransitions.popEnter() },
                        popExitTransition = { GithubNavigationTransitions.popExit() }, sizeTransform = null) {
                        composable<GithubHomeRoute> { Text("Test home") }
                        composable<GithubAccountsRoute> { Text("Test account picker") }
                        composable<GithubSignInRoute> { Text("Test sign in") }
                        githubConversationsGraph(scope)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun retainedDiscussionSeparatesDraftsWhenAccountChangesWhileAnotherDestinationIsVisible() {
        openDiscussion()
        openDiscussionEditor()
        input(R.string.discussion_subject, "Account A title")
        input(R.string.discussion_body, "Account A body")
        backEditor()
        switchThroughAccountDestination(2)
        compose.onNodeWithText("Discussion account 2").assertExists()
        compose.onNodeWithText("Private body account 1").assertDoesNotExist()
        openDiscussionEditor()
        textField(R.string.discussion_subject).assertTextContains("Discussion account 2")
        input(R.string.discussion_subject, "Account B title")
        backEditor()
        switchThroughAccountDestination(1)
        openDiscussionEditor()
        textField(R.string.discussion_subject).assertTextContains("Account A title")
        textField(R.string.discussion_body).assertTextContains("Account A body")
        compose.runOnIdle {
            assertTrue(fixtures.discussionBindings.containsAll(listOf(1L, 2L)))
            assertFalse(fixtures.discussionReads.contains(null))
            assertTrue(fixtures.discussionWrites.isEmpty())
        }
    }

    @Test fun discussionConfirmationIsLostAfterNavigationAndSameLoginAccountSwitch() {
        openDiscussion()
        val mark = label(R.string.discussion_mark_answer)
        val message = label(R.string.discussion_answer_confirm)
        compose.onNodeWithText(mark).performScrollTo().performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { nav.navigate(GithubAccountsRoute) }
        compose.waitForIdle()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText(mark).performScrollTo().performClick()
        compose.runOnIdle { fixtures.sessions.value = ConversationNavigationFixtures.signedIn(2) }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { assertTrue(fixtures.discussionWrites.isEmpty()) }
        compose.onNodeWithText(mark).performScrollTo().performClick()
        compose.onAllNodesWithText(mark).filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(listOf("answer:2"), fixtures.discussionWrites) }
    }

    @Test fun newHostRestoresDiscussionDraftFromSavedBundleWithoutRestoringDeleteConfirmation() {
        openDiscussion()
        openDiscussionEditor()
        input(R.string.discussion_subject, "Saved discussion title")
        input(R.string.discussion_body, "Saved discussion body")
        backEditor()
        val delete = label(R.string.discussion_delete_comment)
        compose.onNodeWithText(delete).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.discussion_delete_comment_confirm)).assertIsDisplayed()
        recreateFromSavedBundle()
        compose.runOnIdle { assertEquals(GithubDiscussionRoute("o/r", 7), nav.currentBackStackEntry!!.toRoute<GithubDiscussionRoute>()) }
        compose.onNodeWithText(label(R.string.discussion_delete_comment_confirm)).assertDoesNotExist()
        openDiscussionEditor()
        textField(R.string.discussion_subject).assertTextContains("Saved discussion title")
        textField(R.string.discussion_body).assertTextContains("Saved discussion body")
        compose.runOnIdle { assertTrue(fixtures.discussionWrites.isEmpty()) }
    }

    @Test fun retainedCreatePullRequestRestoresOnlyTheSelectedAccountsDraft() {
        openPullRequests()
        openPrEditor()
        fillPr("Account A PR", "Private A draft")
        backEditor()
        switchThroughAccountDestination(2)
        openPrEditor()
        assertEquals("", textField(R.string.discussion_subject).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Account A PR").assertDoesNotExist()
        fillPr("Account B PR", "Private B draft")
        backEditor()
        switchThroughAccountDestination(1)
        openPrEditor()
        textField(R.string.discussion_subject).assertTextContains("Account A PR")
        textField(R.string.discussion_body).assertTextContains("Private A draft")
        compose.runOnIdle { assertTrue(fixtures.pullRequestWrites.isEmpty()) }
    }

    @Test fun createPullRequestSuccessWaitsForResumedDestinationAndNavigatesExactlyOnce() {
        val completion = CompletableDeferred<Result<GithubPullRequest>>()
        fixtures.createPullRequest = { completion.await() }
        openPullRequests()
        openPrEditor()
        fillPr("Ready to create", "Reviewed body")
        submitPr()
        compose.runOnIdle { assertEquals(1, fixtures.pullRequestWrites.size); controller.pause() }
        compose.runOnIdle { completion.complete(Result.success(ConversationNavigationFixtures.createdPr())) }
        compose.runOnIdle {
            assertTrue(nav.currentDestination!!.hasRoute<GithubRepositoryPullRequestsRoute>())
            assertEquals(0, createdDestinations)
            controller.resume()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(GithubPullRequestRoute("o/r", 10), nav.currentBackStackEntry!!.toRoute<GithubPullRequestRoute>())
            assertEquals(1, createdDestinations)
            nav.popBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(nav.currentDestination!!.hasRoute<GithubRepositoryPullRequestsRoute>())
            assertEquals(1, createdDestinations)
            assertEquals(1, fixtures.pullRequestWrites.size)
        }
    }

    @Test fun oldCreateSuccessAfterAccountNavigationCannotNavigateOrEraseTheOriginalDraft() {
        lateinit var completion: Continuation<Result<GithubPullRequest>>
        fixtures.createPullRequest = { suspendCoroutine { completion = it } }
        openPullRequests()
        openPrEditor()
        fillPr("Keep A original", "Keep A body")
        submitPr()
        switchThroughAccountDestination(2)
        textField(R.string.discussion_subject).assertDoesNotExist()
        compose.runOnIdle { completion.resume(Result.success(ConversationNavigationFixtures.createdPr())) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(nav.currentDestination!!.hasRoute<GithubRepositoryPullRequestsRoute>())
            assertEquals(0, createdDestinations)
        }
        switchThroughAccountDestination(1)
        openPrEditor()
        textField(R.string.discussion_subject).assertTextContains("Keep A original")
        textField(R.string.discussion_body).assertTextContains("Keep A body")
        compose.runOnIdle { assertEquals(1, fixtures.pullRequestWrites.size) }
    }

    @Test fun newHostRestoresCreatePullRequestFieldsWithoutSubmitting() {
        openPullRequests()
        openPrEditor()
        fillPr("Saved PR title", "Saved PR body")
        recreateFromSavedBundle()
        compose.runOnIdle { assertTrue(nav.currentDestination!!.hasRoute<GithubRepositoryPullRequestsRoute>()) }
        textField(R.string.discussion_subject).assertDoesNotExist()
        openPrEditor()
        textField(R.string.github_create_pr_base).assertTextContains("main")
        textField(R.string.github_create_pr_head).assertTextContains("feature")
        textField(R.string.discussion_subject).assertTextContains("Saved PR title")
        textField(R.string.discussion_body).assertTextContains("Saved PR body")
        compose.runOnIdle { assertTrue(fixtures.pullRequestWrites.isEmpty()); assertEquals(0, createdDestinations) }
    }

    private fun openDiscussion() { compose.runOnIdle { nav.navigate(GithubDiscussionRoute("o/r", 7)) }; compose.waitForIdle() }
    private fun openPullRequests() { compose.runOnIdle { nav.navigate(GithubRepositoryPullRequestsRoute("o/r")) }; compose.waitForIdle() }
    private fun openDiscussionEditor() {
        compose.onNodeWithContentDescription(label(R.string.github_more_actions)).performClick()
        compose.onNodeWithText(label(R.string.discussion_edit)).performClick()
    }
    private fun openPrEditor() { compose.onNodeWithContentDescription(label(R.string.github_create_pr)).performClick() }
    private fun backEditor() { compose.onNodeWithContentDescription(label(R.string.github_back)).performClick() }
    private fun textField(resource: Int) = compose.onNode(hasSetTextAction() and hasText(label(resource)))
    private fun input(resource: Int, value: String) { textField(resource).performScrollTo().performTextReplacement(value) }
    private fun fillPr(title: String, body: String) {
        input(R.string.github_create_pr_base, "main"); input(R.string.github_create_pr_head, "feature")
        input(R.string.discussion_subject, title); input(R.string.discussion_body, body)
    }
    private fun submitPr() { compose.onAllNodesWithText(label(R.string.github_create_pr)).filter(hasClickAction()).onLast().performScrollTo().performClick() }
    private fun switchThroughAccountDestination(accountId: Long) {
        compose.runOnIdle { nav.navigate(GithubAccountsRoute) }
        compose.onNodeWithText("Test account picker").assertIsDisplayed()
        compose.runOnIdle { fixtures.sessions.value = ConversationNavigationFixtures.signedIn(accountId) }
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
    }
    private fun recreateFromSavedBundle() {
        val state = Bundle()
        val oldNavigation = nav
        val oldViewModelStore = nav.currentBackStackEntry!!.viewModelStore
        compose.runOnIdle { controller.pause().saveInstanceState(state).stop().destroy() }
        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(state); parcel.setDataPosition(0)
            parcel.readBundle(ComponentActivity::class.java.classLoader)!!
        } finally { parcel.recycle() }
        controller = Robolectric.buildActivity(ComponentActivity::class.java).create(restored).start().resume().visible()
        render()
        compose.runOnIdle {
            assertNotSame(oldNavigation, nav)
            assertNotSame(oldViewModelStore, nav.currentBackStackEntry!!.viewModelStore)
        }
    }
    private fun label(resource: Int) = controller.get().getString(resource)
}
