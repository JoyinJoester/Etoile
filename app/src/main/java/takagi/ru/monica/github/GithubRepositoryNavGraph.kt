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
import takagi.ru.monica.github.feature.repository.RepositoryCompareScreen
import takagi.ru.monica.github.feature.repository.RepositoryCompareViewModel
import takagi.ru.monica.github.feature.repository.RepositoryTagsScreen
import takagi.ru.monica.github.feature.repository.RepositoryTagsViewModel
import takagi.ru.monica.github.feature.repository.RepositoryCollaboratorsScreen
import takagi.ru.monica.github.feature.repository.RepositoryCollaboratorsViewModel
import takagi.ru.monica.github.feature.repository.InviteCollaboratorScreen
import takagi.ru.monica.github.feature.repository.InviteCollaboratorViewModel
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
import takagi.ru.monica.github.navigation.GithubRepositoryCompareRoute
import takagi.ru.monica.github.navigation.GithubRepositoryTagsRoute
import takagi.ru.monica.github.navigation.GithubRepositoryCollaboratorsRoute
import takagi.ru.monica.github.navigation.GithubInviteCollaboratorRoute
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
 * 仓库域导航图:仓库详情、文件、分支、协作者、Webhook、Releases 与提交。
 */
internal fun NavGraphBuilder.githubRepositoryGraph(scope: GithubNavScope) {
        composable<GithubRepositoryRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                RepositoryDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.repositoryDetailsRepository,
                    actionsRepository = scope.dependencies.repositoryActionsRepository
                )
            }
            val detailViewModel: RepositoryDetailViewModel = viewModel(
                key = route.fullName,
                factory = factory
            )
            val detailState by detailViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, detailViewModel) {
                detailViewModel.onSessionChanged(scope.sessionState.session)
            }
            RepositoryDetailScreen(
                state = detailState,
                onAction = detailViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onBrowseCode = { details ->
                    scope.navController.navigate(
                        GithubRepositoryFilesRoute(
                            fullName = details.repository.fullName,
                            ref = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenBranches = { details ->
                    scope.navController.navigate(
                        GithubRepositoryBranchesRoute(
                            fullName = details.repository.fullName,
                            defaultBranch = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenTags = { details ->
                    scope.navController.navigate(
                        GithubRepositoryTagsRoute(
                            fullName = details.repository.fullName,
                            defaultBranch = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenCollaborators = { details ->
                    scope.navController.navigate(
                        GithubRepositoryCollaboratorsRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenWebhooks = { details ->
                    scope.navController.navigate(GithubRepositoryWebhooksRoute(details.repository.fullName)) {
                        launchSingleTop = true
                    }
                },
                onOpenIssues = { details ->
                    scope.navController.navigate(
                        GithubRepositoryIssuesRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenPullRequests = { details ->
                    scope.navController.navigate(
                        GithubRepositoryPullRequestsRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenActions = { details ->
                    scope.navController.navigate(
                        GithubRepositoryActionsRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenReleases = { details ->
                    scope.navController.navigate(
                        GithubRepositoryReleasesRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenCommits = { details ->
                    scope.navController.navigate(
                        GithubRepositoryCommitsRoute(
                            fullName = details.repository.fullName,
                            ref = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                isSignedIn = scope.sessionState.session is GithubSession.SignedIn,
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenRepository = { repository ->
                    scope.navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = { url ->
                    if (url == "https://github.com/${route.fullName}/discussions") {
                        scope.navController.navigate(takagi.ru.monica.github.navigation.GithubRepositoryDiscussionsRoute(route.fullName)) {
                            launchSingleTop = true
                        }
                    } else scope.openUrl(url)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryBranchesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryBranchesRoute>()
            val factory = remember(route.fullName, route.defaultBranch, scope.dependencies) {
                RepositoryBranchesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    defaultBranch = route.defaultBranch,
                    repository = scope.dependencies.repositoryContentsRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val branchesViewModel: RepositoryBranchesViewModel = viewModel(
                key = "branches:${route.fullName}",
                factory = factory
            )
            val branchesState by branchesViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, branchesViewModel) {
                branchesViewModel.onSessionChanged(scope.sessionState.session)
            }
            RepositoryBranchesScreen(
                state = branchesState,
                onAction = branchesViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenBranch = { branch ->
                    scope.navController.navigate(
                        GithubRepositoryFilesRoute(
                            fullName = route.fullName,
                            ref = branch.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onCompare = { branch ->
                    scope.navController.navigate(
                        GithubRepositoryCompareRoute(
                            fullName = route.fullName,
                            base = route.defaultBranch,
                            head = branch.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryCompareRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryCompareRoute>()
            val factory = remember(route.fullName, route.base, route.head, scope.dependencies) {
                RepositoryCompareViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    base = route.base,
                    head = route.head,
                    repository = scope.dependencies.pullRequestsRepository
                )
            }
            val compareViewModel: RepositoryCompareViewModel = viewModel(
                key = "compare:${route.fullName}:${route.head}",
                factory = factory
            )
            val compareState by compareViewModel.state.collectAsStateWithLifecycle()
            RepositoryCompareScreen(
                state = compareState,
                onAction = compareViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryTagsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryTagsRoute>()
            val factory = remember(route.fullName, route.defaultBranch, scope.dependencies) {
                RepositoryTagsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    defaultBranch = route.defaultBranch,
                    repository = scope.dependencies.repositoryContentsRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val tagsViewModel: RepositoryTagsViewModel = viewModel(
                key = "tags:${route.fullName}",
                factory = factory
            )
            val tagsState by tagsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, tagsViewModel) {
                tagsViewModel.onSessionChanged(scope.sessionState.session)
            }
            RepositoryTagsScreen(
                state = tagsState,
                onAction = tagsViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenTag = { tag ->
                    scope.navController.navigate(
                        GithubRepositoryFilesRoute(
                            fullName = route.fullName,
                            ref = tag.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryCollaboratorsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryCollaboratorsRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                RepositoryCollaboratorsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val collaboratorsViewModel: RepositoryCollaboratorsViewModel = viewModel(
                key = "collaborators:${route.fullName}",
                factory = factory
            )
            val collaboratorsState by collaboratorsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, collaboratorsViewModel) {
                collaboratorsViewModel.onSessionChanged(scope.sessionState.session)
            }
            RepositoryCollaboratorsScreen(
                state = collaboratorsState,
                onAction = collaboratorsViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenUser = { login ->
                    scope.navController.navigate(GithubUserProfileRoute(login)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onInvite = {
                    scope.navController.navigate(GithubInviteCollaboratorRoute(route.fullName)) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubInviteCollaboratorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubInviteCollaboratorRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                InviteCollaboratorViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val inviteViewModel: InviteCollaboratorViewModel = viewModel(
                key = "invite-collaborator:${route.fullName}",
                factory = factory
            )
            val inviteState by inviteViewModel.state.collectAsStateWithLifecycle()
            InviteCollaboratorScreen(
                state = inviteState,
                fullName = route.fullName,
                onAction = inviteViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryWebhooksRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryWebhooksRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                RepositoryWebhooksViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val webhooksViewModel: RepositoryWebhooksViewModel = viewModel(
                key = "webhooks:${route.fullName}",
                factory = factory
            )
            val webhooksState by webhooksViewModel.state.collectAsStateWithLifecycle()
            RepositoryWebhooksScreen(
                state = webhooksState,
                onAction = webhooksViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryFilesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryFilesRoute>()
            val factory = remember(route.fullName, route.ref, route.path, scope.dependencies) {
                RepositoryFilesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    ref = route.ref,
                    path = route.path,
                    repository = scope.dependencies.repositoryContentsRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val filesViewModel: RepositoryFilesViewModel = viewModel(
                key = "files:${route.fullName}:${route.ref}:${route.path}",
                factory = factory
            )
            val filesState by filesViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, filesViewModel) {
                filesViewModel.onSessionChanged(scope.sessionState.session)
            }
            RepositoryFilesScreen(
                state = filesState,
                onAction = filesViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenPath = { path ->
                    if (path != route.path) {
                        scope.navController.navigate(route.copy(path = path)) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenFile = { item ->
                    scope.navController.navigate(
                        GithubRepositoryFileRoute(
                            fullName = route.fullName,
                            ref = route.ref,
                            path = item.path
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onSelectRef = { ref ->
                    if (ref != route.ref) {
                        scope.navController.navigate(route.copy(ref = ref, path = "")) {
                            launchSingleTop = true
                        }
                    }
                },
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryFileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryFileRoute>()
            val factory = remember(route.fullName, route.ref, route.path, scope.dependencies) {
                RepositoryFileViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    ref = route.ref,
                    path = route.path,
                    repository = scope.dependencies.repositoryContentsRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val fileViewModel: RepositoryFileViewModel = viewModel(
                key = "file:${route.fullName}:${route.ref}:${route.path}",
                factory = factory
            )
            val fileState by fileViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, fileViewModel) {
                fileViewModel.onSessionChanged(scope.sessionState.session)
            }
            RepositoryFileScreen(
                state = fileState,
                onAction = fileViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        composable<GithubRepositoryReleasesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryReleasesRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                ReleasesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.releasesRepository
                )
            }
            val releasesViewModel: ReleasesViewModel = viewModel(
                key = "releases:${route.fullName}",
                factory = factory
            )
            val releasesState by releasesViewModel.state.collectAsStateWithLifecycle()
            ReleasesScreen(
                state = releasesState,
                onAction = releasesViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenRelease = { release ->
                    scope.navController.navigate(
                        GithubReleaseRoute(
                            fullName = route.fullName,
                            releaseId = release.id
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubReleaseRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubReleaseRoute>()
            GithubReleaseDetailDestination(
                fullName = route.fullName,
                owner = route.owner,
                name = route.name,
                reference = ReleaseReference.Id(route.releaseId),
                repository = scope.dependencies.releasesRepository,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubReleaseTagRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubReleaseTagRoute>()
            GithubReleaseDetailDestination(
                fullName = route.fullName,
                owner = route.owner,
                name = route.name,
                reference = ReleaseReference.Tag(route.tagName),
                repository = scope.dependencies.releasesRepository,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryCommitsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryCommitsRoute>()
            val factory = remember(route.fullName, route.ref, scope.dependencies) {
                CommitsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    ref = route.ref,
                    repository = scope.dependencies.commitsRepository
                )
            }
            val commitsViewModel: CommitsViewModel = viewModel(
                key = "commits:${route.fullName}:${route.ref}",
                factory = factory
            )
            val commitsState by commitsViewModel.state.collectAsStateWithLifecycle()
            CommitsScreen(
                state = commitsState,
                onAction = commitsViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenCommit = { commit ->
                    scope.navController.navigate(GithubCommitRoute(route.fullName, commit.sha)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubCommitRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubCommitRoute>()
            val factory = remember(route.fullName, route.sha, scope.dependencies) {
                CommitDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    sha = route.sha,
                    repository = scope.dependencies.commitsRepository
                )
            }
            val commitViewModel: CommitDetailViewModel = viewModel(
                key = "commit:${route.fullName}:${route.sha}",
                factory = factory
            )
            val commitState by commitViewModel.state.collectAsStateWithLifecycle()
            CommitDetailScreen(
                state = commitState,
                onAction = commitViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
}

@Composable
internal fun GithubReleaseDetailDestination(
    fullName: String,
    owner: String,
    name: String,
    reference: ReleaseReference,
    repository: GithubReleasesRepository,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val referenceKey = when (reference) {
        is ReleaseReference.Id -> "id:${reference.value}"
        is ReleaseReference.Tag -> "tag:${reference.value}"
    }
    val factory = remember(fullName, reference, repository) {
        ReleaseDetailViewModel.Factory(
            owner = owner,
            name = name,
            reference = reference,
            repository = repository
        )
    }
    val releaseViewModel: ReleaseDetailViewModel = viewModel(
        key = "release:$fullName:$referenceKey",
        factory = factory
    )
    val releaseState by releaseViewModel.state.collectAsStateWithLifecycle()
    ReleaseDetailScreen(
        state = releaseState,
        onAction = releaseViewModel::onAction,
        onBack = onBack,
        onOpenExternal = onOpenExternal,
        modifier = modifier
    )
}
