package takagi.ru.monica.github

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.DesignStyle
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.di.GithubAppDependencies
import takagi.ru.monica.github.data.GithubApkInsightsStore
import takagi.ru.monica.github.data.GithubApkManager
import takagi.ru.monica.github.data.GithubFdroidIndexRepository
import takagi.ru.monica.github.data.GithubStoreSourceStore
import takagi.ru.monica.github.feature.actions.ActionsJobDetailScreen
import takagi.ru.monica.github.feature.actions.ActionsJobDetailViewModel
import takagi.ru.monica.github.feature.actions.ActionsRunDetailScreen
import takagi.ru.monica.github.feature.actions.ActionsRunDetailViewModel
import takagi.ru.monica.github.feature.actions.ActionsWorkflowsScreen
import takagi.ru.monica.github.feature.actions.ActionsWorkflowsViewModel
import takagi.ru.monica.github.feature.actions.WorkflowRunsScreen
import takagi.ru.monica.github.feature.actions.WorkflowRunsViewModel
import takagi.ru.monica.github.feature.explore.ExploreAction
import takagi.ru.monica.github.feature.explore.ExploreScreen
import takagi.ru.monica.github.feature.explore.ExploreUiState
import takagi.ru.monica.github.feature.explore.ExploreViewModel
import takagi.ru.monica.github.feature.auth.GithubSessionAction
import takagi.ru.monica.github.feature.auth.GithubSessionViewModel
import takagi.ru.monica.github.feature.auth.GithubAccountsScreen
import takagi.ru.monica.github.feature.auth.GithubSignInScreen
import takagi.ru.monica.github.feature.commits.CommitDetailScreen
import takagi.ru.monica.github.feature.commits.CommitDetailViewModel
import takagi.ru.monica.github.feature.commits.CommitsScreen
import takagi.ru.monica.github.feature.commits.CommitsViewModel
import takagi.ru.monica.github.feature.home.HomeScreen
import takagi.ru.monica.github.feature.inbox.InboxAction
import takagi.ru.monica.github.feature.inbox.InboxScreen
import takagi.ru.monica.github.feature.inbox.InboxUiState
import takagi.ru.monica.github.feature.inbox.InboxViewModel
import takagi.ru.monica.github.feature.issues.IssueDetailScreen
import takagi.ru.monica.github.feature.issues.IssueDetailViewModel
import takagi.ru.monica.github.feature.issues.CreateIssueScreen
import takagi.ru.monica.github.feature.issues.CreateIssueViewModel
import takagi.ru.monica.github.feature.issues.IssuesScreen
import takagi.ru.monica.github.feature.issues.IssuesViewModel
import takagi.ru.monica.github.feature.mywork.MyConversationsKind
import takagi.ru.monica.github.feature.mywork.MyConversationsScreen
import takagi.ru.monica.github.feature.mywork.MyConversationsViewModel
import takagi.ru.monica.github.feature.organizations.OrganizationsScreen
import takagi.ru.monica.github.feature.organizations.OrganizationsViewModel
import takagi.ru.monica.github.feature.profile.ProfileScreen
import takagi.ru.monica.github.feature.profile.ProfileAction
import takagi.ru.monica.github.feature.profile.ProfileUiState
import takagi.ru.monica.github.feature.profile.ProfileViewModel
import takagi.ru.monica.github.feature.store.StoreAction
import takagi.ru.monica.github.feature.store.StoreScreen
import takagi.ru.monica.github.feature.store.StoreUiState
import takagi.ru.monica.github.feature.store.StoreViewModel
import takagi.ru.monica.github.feature.profile.UserRepositoriesScreen
import takagi.ru.monica.github.feature.profile.UserRepositoriesViewModel
import takagi.ru.monica.github.feature.profile.PublicUserProfileScreen
import takagi.ru.monica.github.feature.profile.PublicUserProfileViewModel
import takagi.ru.monica.github.feature.profile.GithubUserConnectionsScreen
import takagi.ru.monica.github.feature.profile.GithubUserConnectionsViewModel
import takagi.ru.monica.github.feature.pullrequest.PullRequestDetailScreen
import takagi.ru.monica.github.feature.pullrequest.PullRequestDetailViewModel
import takagi.ru.monica.github.feature.pullrequest.PullRequestsScreen
import takagi.ru.monica.github.feature.pullrequest.PullRequestsViewModel
import takagi.ru.monica.github.feature.repository.RepositoryDetailScreen
import takagi.ru.monica.github.feature.repository.RepositoryDetailViewModel
import takagi.ru.monica.github.feature.repository.RepositoryBranchesScreen
import takagi.ru.monica.github.feature.repository.RepositoryBranchesViewModel
import takagi.ru.monica.github.feature.repository.RepositoryCollaboratorsScreen
import takagi.ru.monica.github.feature.repository.RepositoryCollaboratorsViewModel
import takagi.ru.monica.github.feature.repository.RepositoryWebhooksScreen
import takagi.ru.monica.github.feature.repository.RepositoryWebhooksViewModel
import takagi.ru.monica.github.feature.repository.RepositoryFileScreen
import takagi.ru.monica.github.feature.repository.RepositoryFileViewModel
import takagi.ru.monica.github.feature.repository.RepositoryFilesScreen
import takagi.ru.monica.github.feature.repository.RepositoryFilesViewModel
import takagi.ru.monica.github.feature.releases.ReleaseDetailScreen
import takagi.ru.monica.github.feature.releases.ReleaseDetailViewModel
import takagi.ru.monica.github.feature.releases.ReleaseReference
import takagi.ru.monica.github.feature.releases.ReleasesScreen
import takagi.ru.monica.github.feature.releases.ReleasesViewModel
import takagi.ru.monica.github.feature.starred.GithubStarredScreen
import takagi.ru.monica.github.feature.starred.StarredUiState
import takagi.ru.monica.github.feature.starred.StarredViewModel
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubTimestampProvider
import takagi.ru.monica.github.component.LocalGithubUserNavigator
import takagi.ru.monica.github.component.LocalGithubAvatarRepository
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubNotification
import takagi.ru.monica.github.domain.GithubIssueSearchResult
import takagi.ru.monica.github.domain.GithubIssueSearchType
import takagi.ru.monica.github.domain.GithubReleasesRepository
import takagi.ru.monica.github.domain.GithubPublicUserRepository
import takagi.ru.monica.github.domain.GithubUserConnectionKind
import takagi.ru.monica.github.navigation.GithubDestination
import takagi.ru.monica.github.navigation.GithubActionsJobRoute
import takagi.ru.monica.github.navigation.GithubActionsRunRoute
import takagi.ru.monica.github.navigation.GithubHomeRoute
import takagi.ru.monica.github.navigation.GithubSignInRoute
import takagi.ru.monica.github.navigation.GithubSettingsRoute
import takagi.ru.monica.github.navigation.GithubAccountsRoute
import takagi.ru.monica.github.navigation.GithubStarredRoute
import takagi.ru.monica.github.navigation.GithubMyConversationsRoute
import takagi.ru.monica.github.navigation.GithubOrganizationsRoute
import takagi.ru.monica.github.navigation.GithubUserRepositoriesRoute
import takagi.ru.monica.github.navigation.GithubUserProfileRoute
import takagi.ru.monica.github.navigation.GithubUserFollowersRoute
import takagi.ru.monica.github.navigation.GithubUserFollowingRoute
import takagi.ru.monica.github.navigation.GithubCreateIssueRoute
import takagi.ru.monica.github.navigation.GithubCommitRoute
import takagi.ru.monica.github.navigation.GithubIssueRoute
import takagi.ru.monica.github.navigation.GithubPullRequestRoute
import takagi.ru.monica.github.navigation.GithubReleaseRoute
import takagi.ru.monica.github.navigation.GithubReleaseTagRoute
import takagi.ru.monica.github.navigation.GithubRepositoryFileRoute
import takagi.ru.monica.github.navigation.GithubRepositoryFilesRoute
import takagi.ru.monica.github.navigation.GithubRepositoryBranchesRoute
import takagi.ru.monica.github.navigation.GithubRepositoryCollaboratorsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryWebhooksRoute
import takagi.ru.monica.github.navigation.GithubRepositoryIssuesRoute
import takagi.ru.monica.github.navigation.GithubRepositoryActionsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryCommitsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryPullRequestsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryReleasesRoute
import takagi.ru.monica.github.navigation.GithubRepositoryRoute
import takagi.ru.monica.github.navigation.GithubWorkflowRunsRoute
import takagi.ru.monica.github.navigation.GithubLinkDestination
import takagi.ru.monica.github.navigation.GithubLinkRouter
import takagi.ru.monica.github.navigation.GithubNavigationTransitions
import takagi.ru.monica.github.settings.GithubSettingsScreen
import takagi.ru.monica.utils.SettingsManager

import androidx.navigation.NavGraphBuilder

/**
 * Actions 域导航图:工作流、运行与作业详情。
 */
internal fun NavGraphBuilder.githubActionsGraph(scope: GithubNavScope) {
        composable<GithubRepositoryActionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryActionsRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                ActionsWorkflowsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.actionsRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val workflowsViewModel: ActionsWorkflowsViewModel = viewModel(
                key = "actions:${route.fullName}",
                factory = factory
            )
            val workflowsState by workflowsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, workflowsViewModel) {
                workflowsViewModel.onSessionChanged(scope.sessionState.session)
            }
            ActionsWorkflowsScreen(
                state = workflowsState,
                onAction = workflowsViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenWorkflow = { workflow ->
                    scope.navController.navigate(
                        GithubWorkflowRunsRoute(
                            fullName = route.fullName,
                            workflowId = workflow.id,
                            workflowName = workflow.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        composable<GithubWorkflowRunsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubWorkflowRunsRoute>()
            val factory = remember(route.fullName, route.workflowId, route.workflowName, scope.dependencies) {
                WorkflowRunsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    workflowId = route.workflowId,
                    workflowName = route.workflowName,
                    repository = scope.dependencies.actionsRepository
                )
            }
            val runsViewModel: WorkflowRunsViewModel = viewModel(
                key = "workflow-runs:${route.fullName}:${route.workflowId}",
                factory = factory
            )
            val runsState by runsViewModel.state.collectAsStateWithLifecycle()
            WorkflowRunsScreen(
                state = runsState,
                onAction = runsViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenRun = { run ->
                    scope.navController.navigate(
                        GithubActionsRunRoute(fullName = route.fullName, runId = run.id)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubActionsRunRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubActionsRunRoute>()
            val factory = remember(route.fullName, route.runId, scope.dependencies) {
                ActionsRunDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    runId = route.runId,
                    repository = scope.dependencies.actionsRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val runViewModel: ActionsRunDetailViewModel = viewModel(
                key = "actions-run:${route.fullName}:${route.runId}",
                factory = factory
            )
            val runState by runViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, runViewModel) {
                runViewModel.onSessionChanged(scope.sessionState.session)
            }
            ActionsRunDetailScreen(
                state = runState,
                onAction = runViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenJob = { job ->
                    scope.navController.navigate(
                        GithubActionsJobRoute(fullName = route.fullName, jobId = job.id)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubActionsJobRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubActionsJobRoute>()
            val factory = remember(route.fullName, route.jobId, scope.dependencies) {
                ActionsJobDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    jobId = route.jobId,
                    repository = scope.dependencies.actionsRepository
                )
            }
            val jobViewModel: ActionsJobDetailViewModel = viewModel(
                key = "actions-job:${route.fullName}:${route.jobId}",
                factory = factory
            )
            val jobState by jobViewModel.state.collectAsStateWithLifecycle()
            ActionsJobDetailScreen(
                state = jobState,
                onAction = jobViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

}
