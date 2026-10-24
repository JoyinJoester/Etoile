package takagi.ru.monica.gitlab.feature

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import org.junit.After
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
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.gitlab.domain.*
import takagi.ru.monica.ui.theme.EtoileTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GitLabListRestorationTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val account = GitLabAccount(42, "https://gitlab.com/", "alice", "Alice", "")

    @Before fun createHost() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    @After fun destroyHost() { controller.pause().stop().destroy() }

    @Test fun returningToProjectListRetainsReadingPosition() = verifyRestoration(projects = true)
    @Test fun returningToWorkListRetainsReadingPosition() = verifyRestoration(projects = false)

    private fun verifyRestoration(projects: Boolean) {
        val visible = mutableStateOf(true)
        controller.get().setContent {
            EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
                Surface(Modifier.fillMaxSize()) {
                    val holder = rememberSaveableStateHolder()
                    if (visible.value) holder.SaveableStateProvider("list") {
                        if (projects) GitLabProjectsScreen(
                            GitLabProjectsUiState(account = account, items = (1L..80L).map {
                                GitLabProject(it, "Project $it", "team/project-$it", "", "private", "main", 0, 0, false)
                            }), {}, {}, {}, {}
                        ) else GitLabWorkListScreen(
                            GitLabWorkListState(account = account, conversations = (1L..80L).map {
                                GitLabConversation(it, it, "Issue $it", "", "opened", "author", 0, "today")
                            }), {}, 1, { _, _ -> }, {}, {}, {}
                        )
                    }
                }
            }
        }
        val matcher = if (projects) hasText("team/project-50") else hasText("Issue 50", substring = true)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(50)
        val before = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        val after = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot
        assertEquals("Returning to the saved list keeps its visible row", before.top, after.top, 1f)
    }
}
