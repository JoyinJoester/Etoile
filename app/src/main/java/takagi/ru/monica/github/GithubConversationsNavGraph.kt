package takagi.ru.monica.github

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import takagi.ru.monica.github.feature.pullrequest.CreatePullRequestViewModel
import takagi.ru.monica.github.feature.pullrequest.CreatePullRequestScreen
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
import androidx.compose.ui.platform.LocalConfiguration
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
import takagi.ru.monica.github.feature.discussions.DiscussionsScreen
import takagi.ru.monica.github.feature.discussions.DiscussionsViewModel
import takagi.ru.monica.github.feature.discussions.DiscussionDetailScreen
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
import takagi.ru.monica.github.domain.GithubIssueTemplateLanguage
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
import takagi.ru.monica.github.navigation.GithubRepositoryDiscussionsRoute
import takagi.ru.monica.github.navigation.GithubDiscussionRoute
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
 * 议题与 PR 域导航图:列表、详情与创建议题。
 */
internal fun NavGraphBuilder.githubConversationsGraph(scope: GithubNavScope) {
        composable<GithubRepositoryDiscussionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryDiscussionsRoute>()
            val accountId = (scope.sessionState.session as? GithubSession.SignedIn)?.account?.id
            val factory = remember(route.fullName, scope.dependencies, accountId) {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                        DiscussionsViewModel(route.owner, route.name, scope.dependencies.discussionsRepository,
                            extras.createSavedStateHandle(), accountId) as T
                }
            }
            val vm: DiscussionsViewModel = viewModel(key = "discussions:${route.fullName}:$accountId", factory = factory)
            var previousDiscussionVm by remember { mutableStateOf<DiscussionsViewModel?>(null) }
            LaunchedEffect(vm) {
                previousDiscussionVm?.takeIf { it !== vm }?.cancelPendingRequests()
                previousDiscussionVm = vm
            }
            androidx.compose.runtime.DisposableEffect(vm) {
                if (vm.state.value.repositoryId == null && !vm.state.value.loading) vm.load()
                if (vm.state.value.categories.isEmpty()) vm.loadCategories()
                onDispose { vm.cancelPendingRequests(clearAccountData = false) }
            }
            val state by vm.state.collectAsStateWithLifecycle()
            androidx.activity.compose.BackHandler(state.composing) { vm.setComposing(false) }
            LaunchedEffect(state.created) {
                if (state.created != null) vm.consumeCreated()
            }
            if (state.composing) {
                takagi.ru.monica.github.feature.discussions.DiscussionComposer(state, vm::edit, vm::create, { vm.setComposing(false) }, vm::loadCategories)
            } else DiscussionsScreen(state = state, onBack = { scope.navController.popBackStack() }, onRefresh = { vm.load() }, onLoadMore = { vm.load(true) },
                onCreate = { if (scope.sessionState.session is GithubSession.SignedIn) vm.setComposing(true) else scope.navController.navigate(GithubSignInRoute) }, onOpen = { discussion ->
                    scope.navController.navigate(GithubDiscussionRoute(route.fullName, discussion.number))
                },
                modifier = Modifier.fillMaxSize())
        }
        composable<GithubDiscussionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubDiscussionRoute>()
            val accountId = (scope.sessionState.session as? GithubSession.SignedIn)?.account?.id
            androidx.compose.runtime.key(accountId) {
            var discussion by remember { mutableStateOf<takagi.ru.monica.github.domain.GithubDiscussion?>(null) }
            var failed by remember { mutableStateOf(false) }
            var attempt by remember { mutableStateOf(0) }
            LaunchedEffect(route.fullName, route.number, attempt) {
                failed = false
                scope.dependencies.discussionsRepository.detail(route.owner, route.name, route.number)
                    .fold(onSuccess = { discussion = it }, onFailure = { failed = true })
            }
            val loaded = discussion
            if (loaded == null) {
                takagi.ru.monica.github.component.GithubDetailScaffold(
                    title = stringResource(R.string.discussion_title),
                    onBack = { scope.navController.popBackStack() },
                    backContentDescription = stringResource(R.string.discussion_back)
                ) { padding ->
                    Column(Modifier.padding(padding).padding(20.dp)) {
                        if (failed) {
                            Text(stringResource(R.string.discussion_load_error))
                            androidx.compose.material3.TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.discussion_retry)) }
                        } else androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else DiscussionDetailScreen(discussion = loaded,
                onEdit = { title, body -> scope.dependencies.discussionsRepository.edit(loaded.id, title, body) },
                onOpenLink = { target -> scope.openUrl(takagi.ru.monica.github.navigation.GithubWebUrls.resolveMarkdownLink(route.fullName, "HEAD", "", target)) },
                loadReplies = scope.dependencies.discussionsRepository::replies,
                editComment = scope.dependencies.discussionsRepository::editComment,
                deleteComment = scope.dependencies.discussionsRepository::deleteComment,
                replyToComment = { id, body -> scope.dependencies.discussionsRepository.replyToComment(loaded.id, id, body) },
                onMarkAnswer = scope.dependencies.discussionsRepository::markAnswer,
                loadComments = { cursor -> scope.dependencies.discussionsRepository.comments(loaded.id, cursor) },
                signedIn = scope.sessionState.session is GithubSession.SignedIn,
                onReply = { body -> scope.dependencies.discussionsRepository.reply(loaded.id, body) },
                onBack = { scope.navController.popBackStack() }, modifier = Modifier.fillMaxSize())
        }
            }
        composable<GithubRepositoryIssuesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryIssuesRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                IssuesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.issuesRepository
                )
            }
            val issuesViewModel: IssuesViewModel = viewModel(
                key = "issues:${route.fullName}",
                factory = factory
            )
            val issuesState by issuesViewModel.state.collectAsStateWithLifecycle()
            IssuesScreen(
                state = issuesState,
                onAction = issuesViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenIssue = { issue ->
                    scope.navController.navigate(
                        GithubIssueRoute(fullName = route.fullName, number = issue.number)
                    ) {
                        launchSingleTop = true
                    }
                },
                canCreateIssue = scope.sessionState.session is GithubSession.SignedIn,
                onCreateIssue = {
                    scope.navController.navigate(GithubCreateIssueRoute(route.fullName)) {
                        launchSingleTop = true
                    }
                },
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryPullRequestsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryPullRequestsRoute>()
            val factory = remember(route.fullName, scope.dependencies) {
                PullRequestsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.pullRequestsRepository
                )
            }
            val pullRequestsViewModel: PullRequestsViewModel = viewModel(
                key = "pull-requests:${route.fullName}",
                factory = factory
            )
            val pullRequestsState by pullRequestsViewModel.state.collectAsStateWithLifecycle()
            val createAccountId = (scope.sessionState.session as? GithubSession.SignedIn)?.account?.id
            val createFactory = remember(route.fullName, scope.dependencies, createAccountId) {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                        CreatePullRequestViewModel(
                            { page -> scope.dependencies.repositoryContentsRepository.branches(route.owner, route.name, page) },
                            { draft -> scope.dependencies.pullRequestsRepository.create(route.owner, route.name, draft) },
                            extras.createSavedStateHandle(),
                            { base, head, headRepository -> scope.dependencies.pullRequestsRepository.compare(route.owner, route.name, base, head, headRepository) },
                            { takagi.ru.monica.github.data.GithubPullRequestTemplates(scope.dependencies.repositoryContentsRepository).load(route.owner, route.name) }) as T
                }
            }
            val createVm: CreatePullRequestViewModel = viewModel(key = "create-pr:${route.fullName}:$createAccountId", factory = createFactory)
            val createState by createVm.state.collectAsStateWithLifecycle()
            var creating by rememberSaveable(createAccountId) { mutableStateOf(false) }
            androidx.compose.runtime.DisposableEffect(createVm) { onDispose { createVm.cancel() } }
            androidx.activity.compose.BackHandler(creating) { if (!createState.submitting) creating = false }
            LaunchedEffect(createState.created) {
                createState.created?.let {
                    creating = false; createVm.consumeCreated()
                    pullRequestsViewModel.onAction(takagi.ru.monica.github.feature.pullrequest.PullRequestsAction.Retry)
                    scope.navController.navigate(GithubPullRequestRoute(route.fullName, it.number))
                }
            }
            if (creating) CreatePullRequestScreen(route.fullName, createState, createVm::edit, createVm::submit,
                createVm::loadBranches, { creating = false }, createVm::preview, scope.openUrl,
                createVm::loadTemplates, createVm::applyTemplate)
            else PullRequestsScreen(
                onCreate = {
                    if (createAccountId == null) scope.navController.navigate(GithubSignInRoute)
                    else creating = true
                },
                state = pullRequestsState,
                onAction = pullRequestsViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onOpenPullRequest = { pullRequest ->
                    scope.navController.navigate(
                        GithubPullRequestRoute(
                            fullName = route.fullName,
                            number = pullRequest.number
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubPullRequestRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubPullRequestRoute>()
            val factory = remember(route.fullName, route.number, scope.dependencies) {
                PullRequestDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    number = route.number,
                    pullRequestsRepository = scope.dependencies.pullRequestsRepository,
                    issuesRepository = scope.dependencies.issuesRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val pullRequestViewModel: PullRequestDetailViewModel = viewModel(
                key = "pull-request:${route.fullName}:${route.number}",
                factory = factory
            )
            val pullRequestState by pullRequestViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, pullRequestViewModel) {
                pullRequestViewModel.onSessionChanged(scope.sessionState.session)
            }
            PullRequestDetailScreen(
                state = pullRequestState,
                onAction = pullRequestViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                isSignedIn = scope.sessionState.session is GithubSession.SignedIn,
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        composable<GithubIssueRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubIssueRoute>()
            val factory = remember(route.fullName, route.number, scope.dependencies) {
                IssueDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    number = route.number,
                    repository = scope.dependencies.issuesRepository,
                    detailsRepository = scope.dependencies.repositoryDetailsRepository
                )
            }
            val issueViewModel: IssueDetailViewModel = viewModel(
                key = "issue:${route.fullName}:${route.number}",
                factory = factory
            )
            val issueState by issueViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(scope.sessionState.session, issueViewModel) {
                issueViewModel.onSessionChanged(scope.sessionState.session)
            }
            IssueDetailScreen(
                state = issueState,
                onAction = issueViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                canWrite = scope.sessionState.session is GithubSession.SignedIn,
                onSignIn = { scope.navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                onOpenExternal = scope.openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubCreateIssueRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubCreateIssueRoute>()
            val templateLanguage = if (LocalConfiguration.current.locales[0]?.language == "zh")
                GithubIssueTemplateLanguage.CHINESE else GithubIssueTemplateLanguage.ENGLISH
            val factory = remember(route.fullName, scope.dependencies, templateLanguage) {
                CreateIssueViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = scope.dependencies.issuesRepository,
                    templatesRepository = scope.dependencies.issueTemplatesRepository,
                    initialTemplateLanguage = templateLanguage
                )
            }
            val createViewModel: CreateIssueViewModel = viewModel(
                key = "create-issue:${route.fullName}",
                factory = factory
            )
            val createState by createViewModel.state.collectAsStateWithLifecycle()
            CreateIssueScreen(
                state = createState,
                canSubmit = scope.sessionState.session is GithubSession.SignedIn,
                onOpenExternal = scope.openUrl,
                onAction = createViewModel::onAction,
                onBack = { scope.navController.popBackStack() },
                onCreated = { issue ->
                    scope.navController.popBackStack()
                    scope.navController.navigate(
                        GithubIssueRoute(fullName = route.fullName, number = issue.number)
                    ) {
                        launchSingleTop = true
                    }
                },
                onSignIn = {
                    scope.navController.navigate(GithubSignInRoute) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

}
