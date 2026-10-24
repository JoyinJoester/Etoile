package takagi.ru.monica.gitlab.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import takagi.ru.monica.github.di.GithubAppDependencies
import takagi.ru.monica.github.navigation.*
import takagi.ru.monica.gitlab.domain.*
import takagi.ru.monica.gitlab.feature.*

fun NavGraphBuilder.gitLabWorkRoutes(navController: NavHostController, dependencies: GithubAppDependencies) {
    composable<GitLabWorkRoute> { entry ->
        val route = entry.toRoute<GitLabWorkRoute>()
        val section = GitLabWorkSection.entries.firstOrNull { it.name == route.section } ?: GitLabWorkSection.ISSUES
        val factory = remember(route, dependencies) { GitLabViewModelFactory(GitLabWorkListViewModel::class.java) {
            GitLabWorkListViewModel(route.accountKey, route.projectId, section, dependencies.gitLabAuthRepository, dependencies.gitLabWorkRepository, entry.savedStateHandle)
        } }
        val model: GitLabWorkListViewModel = viewModel(factory = factory)
        val state by model.state.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!state.loading) model.refresh() }
        LaunchedEffect(state.created?.id, state.createdPipeline?.id, state.account?.key) {
            state.created?.let { item ->
                val kind = state.kind
                model.consumeCreated()
                navController.navigate(GitLabConversationRoute(route.accountKey, route.projectId, kind.name, item.number))
            }
            state.createdPipeline?.let { pipeline ->
                model.consumeCreated()
                navController.navigate(GitLabPipelineRoute(route.accountKey, route.projectId, pipeline.id))
            }
        }
        GitLabWorkListScreen(state, model::onAction, route.projectId,
            onConversation = { kind, number -> navController.navigate(GitLabConversationRoute(route.accountKey, route.projectId, kind.name, number)) { launchSingleTop = true } },
            onPipeline = { id -> navController.navigate(GitLabPipelineRoute(route.accountKey, route.projectId, id)) { launchSingleTop = true } },
            onAccounts = { navController.navigate(GitLabAccountsRoute) }, onBack = { navController.popBackStack() })
    }
    composable<GitLabConversationRoute> { entry ->
        val route = entry.toRoute<GitLabConversationRoute>()
        val kind = GitLabConversationKind.entries.firstOrNull { it.name == route.kind } ?: GitLabConversationKind.ISSUE
        val factory = remember(route, dependencies) { GitLabViewModelFactory(GitLabConversationViewModel::class.java) {
            GitLabConversationViewModel(route.accountKey, route.projectId, kind, route.number, dependencies.gitLabAuthRepository, dependencies.gitLabWorkRepository, entry.savedStateHandle)
        } }
        val model: GitLabConversationViewModel = viewModel(factory = factory)
        val state by model.state.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!state.loading) model.refresh() }
        GitLabConversationScreen(state, route.projectId, route.number, model::onAction,
            onAccounts = { navController.navigate(GitLabAccountsRoute) }, onBack = { navController.popBackStack() })
    }
    composable<GitLabPipelineRoute> { entry ->
        val route = entry.toRoute<GitLabPipelineRoute>()
        val factory = remember(route, dependencies) { GitLabViewModelFactory(GitLabPipelineViewModel::class.java) {
            GitLabPipelineViewModel(route.accountKey, route.projectId, route.pipelineId, dependencies.gitLabAuthRepository, dependencies.gitLabWorkRepository)
        } }
        val model: GitLabPipelineViewModel = viewModel(factory = factory)
        val state by model.state.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!state.loading) model.refresh() }
        GitLabPipelineScreen(state, route.projectId, route.pipelineId, model::onAction,
            onAccounts = { navController.navigate(GitLabAccountsRoute) }, onBack = { navController.popBackStack() })
    }
}
