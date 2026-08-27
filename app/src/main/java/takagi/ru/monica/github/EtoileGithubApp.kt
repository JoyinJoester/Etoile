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
import androidx.compose.material.icons.filled.SmartToy
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
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.di.GithubAppDependencies
import takagi.ru.monica.github.feature.actions.ActionsJobDetailScreen
import takagi.ru.monica.github.feature.actions.ActionsJobDetailViewModel
import takagi.ru.monica.github.feature.actions.ActionsRunDetailScreen
import takagi.ru.monica.github.feature.actions.ActionsRunDetailViewModel
import takagi.ru.monica.github.feature.actions.ActionsWorkflowsScreen
import takagi.ru.monica.github.feature.actions.ActionsWorkflowsViewModel
import takagi.ru.monica.github.feature.actions.WorkflowRunsScreen
import takagi.ru.monica.github.feature.actions.WorkflowRunsViewModel
import takagi.ru.monica.github.feature.copilot.CopilotPlaceholderScreen
import takagi.ru.monica.github.feature.explore.ExploreAction
import takagi.ru.monica.github.feature.explore.ExploreScreen
import takagi.ru.monica.github.feature.explore.ExploreUiState
import takagi.ru.monica.github.feature.explore.ExploreViewModel
import takagi.ru.monica.github.feature.auth.GithubSessionAction
import takagi.ru.monica.github.feature.auth.GithubSessionViewModel
import takagi.ru.monica.github.feature.auth.GithubAccountSheet
import takagi.ru.monica.github.feature.auth.GithubSignInSheet
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
import takagi.ru.monica.github.feature.starred.StarredCollectionsSheet
import takagi.ru.monica.github.feature.starred.StarredUiState
import takagi.ru.monica.github.feature.starred.StarredViewModel
import takagi.ru.monica.github.component.GithubAvatar
import takagi.ru.monica.github.component.GithubServiceStatusProvider
import takagi.ru.monica.github.component.GithubServiceStatusNotices
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
import takagi.ru.monica.github.settings.GithubSettingsSheet
import takagi.ru.monica.utils.SettingsManager

@Composable
fun EtoileGithubApp(
    settings: AppSettings,
    settingsManager: SettingsManager,
    initialGithubUrl: String? = null,
    onGithubUrlConsumed: (String) -> Unit = {}
) {
    var destination by rememberSaveable { mutableStateOf(GithubDestination.HOME) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var starredVisible by rememberSaveable { mutableStateOf(false) }
    var signInVisible by rememberSaveable { mutableStateOf(false) }
    var accountsVisible by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val dependencies = remember(context.applicationContext) { GithubAppDependencies(context.applicationContext) }
    val inboxFactory = remember(dependencies) { InboxViewModel.Factory(dependencies.notificationsRepository) }
    val starredFactory = remember(dependencies) { StarredViewModel.Factory(dependencies.starsRepository, dependencies.starCategoryStore) }
    val exploreFactory = remember(dependencies) {
        ExploreViewModel.Factory(
            repository = dependencies.repositorySearchRepository,
            globalSearch = dependencies.repositorySearchRepository
        )
    }
    val sessionFactory = remember(dependencies) {
        GithubSessionViewModel.Factory(
            repository = dependencies.authRepository,
            deviceAuthRepository = dependencies.deviceAuthRepository
        )
    }
    val inboxViewModel: InboxViewModel = viewModel(factory = inboxFactory)
    val starredViewModel: StarredViewModel = viewModel(factory = starredFactory)
    val exploreViewModel: ExploreViewModel = viewModel(factory = exploreFactory)
    val sessionViewModel: GithubSessionViewModel = viewModel(factory = sessionFactory)
    val inboxState by inboxViewModel.state.collectAsStateWithLifecycle()
    val exploreState by exploreViewModel.state.collectAsStateWithLifecycle()
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val starredState by starredViewModel.state.collectAsStateWithLifecycle()
    val rateLimits by dependencies.rateLimitMonitor.state.collectAsStateWithLifecycle()
    val cacheFallback by dependencies.cacheFallbackMonitor.state.collectAsStateWithLifecycle()
    val openUrl: (String) -> Unit = remember(context) {
        { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            Unit
        }
    }
    fun openNativeDestination(destination: GithubLinkDestination): Boolean {
        when (destination) {
            is GithubLinkDestination.Repository -> navController.navigate(
                GithubRepositoryRoute(destination.fullName)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.Issue -> navController.navigate(
                GithubIssueRoute(destination.fullName, destination.number)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.PullRequest -> navController.navigate(
                GithubPullRequestRoute(destination.fullName, destination.number)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.ActionsRun -> navController.navigate(
                GithubActionsRunRoute(destination.fullName, destination.runId)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.ActionsJob -> navController.navigate(
                GithubActionsJobRoute(destination.fullName, destination.jobId)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.Releases -> navController.navigate(
                GithubRepositoryReleasesRoute(destination.fullName)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.ReleaseTag -> navController.navigate(
                GithubReleaseTagRoute(destination.fullName, destination.tagName)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.Commit -> navController.navigate(
                GithubCommitRoute(destination.fullName, destination.sha)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.User -> navController.navigate(
                GithubUserProfileRoute(destination.login)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.UserFollowers -> navController.navigate(
                GithubUserFollowersRoute(destination.login)
            ) {
                launchSingleTop = true
            }
            is GithubLinkDestination.UserFollowing -> navController.navigate(
                GithubUserFollowingRoute(destination.login)
            ) {
                launchSingleTop = true
            }
        }
        return true
    }

    val openNotification: (GithubNotification) -> Unit = { notification ->
        val opened = GithubLinkRouter.parse(notification.subjectUrl)?.let(::openNativeDestination) == true
        if (!opened) openUrl(notification.subjectUrl ?: notification.repositoryUrl)
    }

    LaunchedEffect(initialGithubUrl) {
        initialGithubUrl?.let { url ->
            val opened = GithubLinkRouter.parse(url)?.let(::openNativeDestination) == true
            if (!opened) openUrl(url)
            onGithubUrlConsumed(url)
        }
    }

    LaunchedEffect(sessionState.session) {
        if (sessionState.session is GithubSession.SignedIn) signInVisible = false
        inboxViewModel.onSessionChanged(sessionState.session)
        starredViewModel.onSessionChanged(sessionState.session)
    }

    GithubServiceStatusProvider(
        rateLimit = rateLimits["core"],
        cacheFallback = cacheFallback
    ) {
        CompositionLocalProvider(
            LocalGithubAvatarRepository provides dependencies.avatarRepository,
            LocalGithubUserNavigator provides { login ->
                openNativeDestination(GithubLinkDestination.User(login))
            }
        ) {
            NavHost(
            navController = navController,
            startDestination = GithubHomeRoute,
            modifier = Modifier.fillMaxSize()
            ) {
        composable<GithubHomeRoute> {
            GithubAdaptiveScaffold(
                destination = destination,
                session = sessionState.session,
                onDestinationSelected = { destination = it },
                onOpenSettings = { settingsVisible = true }
            ) { contentModifier ->
                GithubDestinationContent(
                    destination = destination,
                    inboxState = inboxState,
                    exploreState = exploreState,
                    starredState = starredState,
                    session = sessionState.session,
                    savedAccountCount = sessionState.accounts.size,
                    settings = settings,
                    modifier = contentModifier,
                    onInboxAction = inboxViewModel::onAction,
                    onExploreAction = exploreViewModel::onAction,
                    onOpenSettings = { settingsVisible = true },
                    onSignIn = { signInVisible = true },
                    onSignOut = { sessionViewModel.onAction(GithubSessionAction.SignOut) },
                    onRetrySession = { sessionViewModel.onAction(GithubSessionAction.RetryRestore) },
                    onManageAccounts = { accountsVisible = true },
                    onOpenRepositories = {
                        navController.navigate(GithubUserRepositoriesRoute) {
                            launchSingleTop = true
                        }
                    },
                    onOpenStarred = { starredVisible = true },
                    onOpenOrganizations = {
                        navController.navigate(GithubOrganizationsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onOpenMyConversations = { kind ->
                        navController.navigate(GithubMyConversationsRoute(kind)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenFollowers = {
                        (sessionState.session as? GithubSession.SignedIn)?.account?.login?.let { login ->
                            navController.navigate(GithubUserFollowersRoute(login)) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenFollowing = {
                        (sessionState.session as? GithubSession.SignedIn)?.account?.login?.let { login ->
                            navController.navigate(GithubUserFollowingRoute(login)) {
                                launchSingleTop = true
                            }
                        }
                    },
                     onOpenRepository = { repository ->
                        navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                            launchSingleTop = true
                        }
                     },
                     onOpenUser = { login ->
                         navController.navigate(GithubUserProfileRoute(login)) {
                             launchSingleTop = true
                         }
                     },
                     onOpenConversation = { result ->
                         when (result.type) {
                             GithubIssueSearchType.ISSUE -> navController.navigate(
                                 GithubIssueRoute(result.repositoryFullName, result.number)
                             ) {
                                 launchSingleTop = true
                             }
                             GithubIssueSearchType.PULL_REQUEST -> navController.navigate(
                                 GithubPullRequestRoute(result.repositoryFullName, result.number)
                             ) {
                                 launchSingleTop = true
                             }
                         }
                     },
                     onOpenUrl = openUrl,
                    onOpenNotification = openNotification
                )
            }
        }
        composable<GithubUserRepositoriesRoute> {
            val account = (sessionState.session as? GithubSession.SignedIn)?.account
            LaunchedEffect(account) {
                if (account == null) navController.popBackStack()
            }
            if (account != null) {
                val factory = remember(dependencies) {
                    UserRepositoriesViewModel.Factory(dependencies.userRepositoriesRepository)
                }
                val repositoriesViewModel: UserRepositoriesViewModel = viewModel(
                    key = "user-repositories:${account.login}",
                    factory = factory
                )
                val repositoriesState by repositoriesViewModel.state.collectAsStateWithLifecycle()
                UserRepositoriesScreen(
                    state = repositoriesState,
                    accountLogin = account.login,
                    onAction = repositoriesViewModel::onAction,
                    onBack = { navController.popBackStack() },
                    onOpenRepository = { repository ->
                        navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenExternal = openUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composable<GithubOrganizationsRoute> {
            val account = (sessionState.session as? GithubSession.SignedIn)?.account
            LaunchedEffect(account) {
                if (account == null) navController.popBackStack()
            }
            if (account != null) {
                val factory = remember(dependencies) {
                    OrganizationsViewModel.Factory(dependencies.organizationsRepository)
                }
                val organizationsViewModel: OrganizationsViewModel = viewModel(
                    key = "organizations",
                    factory = factory
                )
                val organizationsState by organizationsViewModel.state.collectAsStateWithLifecycle()
                OrganizationsScreen(
                    state = organizationsState,
                    onAction = organizationsViewModel::onAction,
                    onBack = { navController.popBackStack() },
                    onOpenOrganization = { organization ->
                        navController.navigate(GithubUserProfileRoute(organization.login)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenExternal = openUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composable<GithubMyConversationsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubMyConversationsRoute>()
            val kind = route.conversationsKind
            val factory = remember(kind, dependencies) {
                MyConversationsViewModel.Factory(dependencies.repositorySearchRepository, kind)
            }
            val myConversationsViewModel: MyConversationsViewModel = viewModel(
                key = "my-conversations:$kind",
                factory = factory
            )
            val myConversationsState by myConversationsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(sessionState.session, myConversationsViewModel) {
                myConversationsViewModel.onSessionChanged(sessionState.session)
            }
            MyConversationsScreen(
                kind = kind,
                state = myConversationsState,
                session = sessionState.session,
                onAction = myConversationsViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenConversation = { result ->
                    when (result.type) {
                        GithubIssueSearchType.ISSUE -> navController.navigate(
                            GithubIssueRoute(result.repositoryFullName, result.number)
                        ) {
                            launchSingleTop = true
                        }
                        GithubIssueSearchType.PULL_REQUEST -> navController.navigate(
                            GithubPullRequestRoute(result.repositoryFullName, result.number)
                        ) {
                            launchSingleTop = true
                        }
                    }
                },
                onSignIn = { signInVisible = true },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubUserProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubUserProfileRoute>()
            val factory = remember(route.login, dependencies) {
                PublicUserProfileViewModel.Factory(route.login, dependencies.publicUserRepository)
            }
            val profileViewModel: PublicUserProfileViewModel = viewModel(
                key = "public-user:${route.login}",
                factory = factory
            )
            val profileState by profileViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(sessionState.session, profileViewModel) {
                profileViewModel.onSessionChanged(sessionState.session)
            }
            PublicUserProfileScreen(
                state = profileState,
                onAction = profileViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenRepository = { repository ->
                    navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                        launchSingleTop = true
                    }
                },
                onOpenFollowers = {
                    navController.navigate(GithubUserFollowersRoute(route.login)) {
                        launchSingleTop = true
                    }
                },
                onOpenFollowing = {
                    navController.navigate(GithubUserFollowingRoute(route.login)) {
                        launchSingleTop = true
                    }
                },
                viewerLogin = (sessionState.session as? GithubSession.SignedIn)?.account?.login,
                onSignIn = { signInVisible = true },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubUserFollowersRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubUserFollowersRoute>()
            GithubUserConnectionsDestination(
                login = route.login,
                kind = GithubUserConnectionKind.FOLLOWERS,
                repository = dependencies.publicUserRepository,
                onBack = { navController.popBackStack() },
                onOpenUser = { login ->
                    navController.navigate(GithubUserProfileRoute(login)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubUserFollowingRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubUserFollowingRoute>()
            GithubUserConnectionsDestination(
                login = route.login,
                kind = GithubUserConnectionKind.FOLLOWING,
                repository = dependencies.publicUserRepository,
                onBack = { navController.popBackStack() },
                onOpenUser = { login ->
                    navController.navigate(GithubUserProfileRoute(login)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryRoute>()
            val factory = remember(route.fullName, dependencies) {
                RepositoryDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.repositoryDetailsRepository,
                    actionsRepository = dependencies.repositoryActionsRepository
                )
            }
            val detailViewModel: RepositoryDetailViewModel = viewModel(
                key = route.fullName,
                factory = factory
            )
            val detailState by detailViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(sessionState.session, detailViewModel) {
                detailViewModel.onSessionChanged(sessionState.session)
            }
            RepositoryDetailScreen(
                state = detailState,
                onAction = detailViewModel::onAction,
                onBack = { navController.popBackStack() },
                onBrowseCode = { details ->
                    navController.navigate(
                        GithubRepositoryFilesRoute(
                            fullName = details.repository.fullName,
                            ref = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenBranches = { details ->
                    navController.navigate(
                        GithubRepositoryBranchesRoute(
                            fullName = details.repository.fullName,
                            defaultBranch = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenCollaborators = { details ->
                    navController.navigate(
                        GithubRepositoryCollaboratorsRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenWebhooks = { details ->
                    navController.navigate(GithubRepositoryWebhooksRoute(details.repository.fullName)) {
                        launchSingleTop = true
                    }
                },
                onOpenIssues = { details ->
                    navController.navigate(
                        GithubRepositoryIssuesRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenPullRequests = { details ->
                    navController.navigate(
                        GithubRepositoryPullRequestsRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenActions = { details ->
                    navController.navigate(
                        GithubRepositoryActionsRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenReleases = { details ->
                    navController.navigate(
                        GithubRepositoryReleasesRoute(details.repository.fullName)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenCommits = { details ->
                    navController.navigate(
                        GithubRepositoryCommitsRoute(
                            fullName = details.repository.fullName,
                            ref = details.defaultBranch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                canWrite = sessionState.session is GithubSession.SignedIn,
                onSignIn = { signInVisible = true },
                onOpenRepository = { repository ->
                    navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryBranchesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryBranchesRoute>()
            val factory = remember(route.fullName, route.defaultBranch, dependencies) {
                RepositoryBranchesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    defaultBranch = route.defaultBranch,
                    repository = dependencies.repositoryContentsRepository
                )
            }
            val branchesViewModel: RepositoryBranchesViewModel = viewModel(
                key = "branches:${route.fullName}",
                factory = factory
            )
            val branchesState by branchesViewModel.state.collectAsStateWithLifecycle()
            RepositoryBranchesScreen(
                state = branchesState,
                onAction = branchesViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenBranch = { branch ->
                    navController.navigate(
                        GithubRepositoryFilesRoute(
                            fullName = route.fullName,
                            ref = branch.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryCollaboratorsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryCollaboratorsRoute>()
            val factory = remember(route.fullName, dependencies) {
                RepositoryCollaboratorsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.repositoryDetailsRepository
                )
            }
            val collaboratorsViewModel: RepositoryCollaboratorsViewModel = viewModel(
                key = "collaborators:${route.fullName}",
                factory = factory
            )
            val collaboratorsState by collaboratorsViewModel.state.collectAsStateWithLifecycle()
            RepositoryCollaboratorsScreen(
                state = collaboratorsState,
                onAction = collaboratorsViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenUser = { login ->
                    navController.navigate(GithubUserProfileRoute(login)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryWebhooksRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryWebhooksRoute>()
            val factory = remember(route.fullName, dependencies) {
                RepositoryWebhooksViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.repositoryDetailsRepository
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
                onBack = { navController.popBackStack() },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryFilesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryFilesRoute>()
            val factory = remember(route.fullName, route.ref, route.path, dependencies) {
                RepositoryFilesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    ref = route.ref,
                    path = route.path,
                    repository = dependencies.repositoryContentsRepository
                )
            }
            val filesViewModel: RepositoryFilesViewModel = viewModel(
                key = "files:${route.fullName}:${route.ref}:${route.path}",
                factory = factory
            )
            val filesState by filesViewModel.state.collectAsStateWithLifecycle()
            RepositoryFilesScreen(
                state = filesState,
                onAction = filesViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenPath = { path ->
                    if (path != route.path) {
                        navController.navigate(route.copy(path = path)) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenFile = { item ->
                    navController.navigate(
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
                        navController.navigate(route.copy(ref = ref, path = "")) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryFileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryFileRoute>()
            val factory = remember(route.fullName, route.ref, route.path, dependencies) {
                RepositoryFileViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    ref = route.ref,
                    path = route.path,
                    repository = dependencies.repositoryContentsRepository
                )
            }
            val fileViewModel: RepositoryFileViewModel = viewModel(
                key = "file:${route.fullName}:${route.ref}:${route.path}",
                factory = factory
            )
            val fileState by fileViewModel.state.collectAsStateWithLifecycle()
            RepositoryFileScreen(
                state = fileState,
                onAction = fileViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryIssuesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryIssuesRoute>()
            val factory = remember(route.fullName, dependencies) {
                IssuesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.issuesRepository
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
                onBack = { navController.popBackStack() },
                onOpenIssue = { issue ->
                    navController.navigate(
                        GithubIssueRoute(fullName = route.fullName, number = issue.number)
                    ) {
                        launchSingleTop = true
                    }
                },
                canCreateIssue = sessionState.session is GithubSession.SignedIn,
                onCreateIssue = {
                    navController.navigate(GithubCreateIssueRoute(route.fullName)) {
                        launchSingleTop = true
                    }
                },
                onSignIn = { signInVisible = true },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryPullRequestsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryPullRequestsRoute>()
            val factory = remember(route.fullName, dependencies) {
                PullRequestsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.pullRequestsRepository
                )
            }
            val pullRequestsViewModel: PullRequestsViewModel = viewModel(
                key = "pull-requests:${route.fullName}",
                factory = factory
            )
            val pullRequestsState by pullRequestsViewModel.state.collectAsStateWithLifecycle()
            PullRequestsScreen(
                state = pullRequestsState,
                onAction = pullRequestsViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenPullRequest = { pullRequest ->
                    navController.navigate(
                        GithubPullRequestRoute(
                            fullName = route.fullName,
                            number = pullRequest.number
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubPullRequestRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubPullRequestRoute>()
            val factory = remember(route.fullName, route.number, dependencies) {
                PullRequestDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    number = route.number,
                    pullRequestsRepository = dependencies.pullRequestsRepository,
                    issuesRepository = dependencies.issuesRepository
                )
            }
            val pullRequestViewModel: PullRequestDetailViewModel = viewModel(
                key = "pull-request:${route.fullName}:${route.number}",
                factory = factory
            )
            val pullRequestState by pullRequestViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(sessionState.session, pullRequestViewModel) {
                pullRequestViewModel.onSessionChanged(sessionState.session)
            }
            PullRequestDetailScreen(
                state = pullRequestState,
                onAction = pullRequestViewModel::onAction,
                onBack = { navController.popBackStack() },
                canWrite = sessionState.session is GithubSession.SignedIn,
                onSignIn = { signInVisible = true },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryActionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryActionsRoute>()
            val factory = remember(route.fullName, dependencies) {
                ActionsWorkflowsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.actionsRepository
                )
            }
            val workflowsViewModel: ActionsWorkflowsViewModel = viewModel(
                key = "actions:${route.fullName}",
                factory = factory
            )
            val workflowsState by workflowsViewModel.state.collectAsStateWithLifecycle()
            ActionsWorkflowsScreen(
                state = workflowsState,
                onAction = workflowsViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenWorkflow = { workflow ->
                    navController.navigate(
                        GithubWorkflowRunsRoute(
                            fullName = route.fullName,
                            workflowId = workflow.id,
                            workflowName = workflow.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryReleasesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryReleasesRoute>()
            val factory = remember(route.fullName, dependencies) {
                ReleasesViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.releasesRepository
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
                onBack = { navController.popBackStack() },
                onOpenRelease = { release ->
                    navController.navigate(
                        GithubReleaseRoute(
                            fullName = route.fullName,
                            releaseId = release.id
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
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
                repository = dependencies.releasesRepository,
                onBack = { navController.popBackStack() },
                onOpenExternal = openUrl,
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
                repository = dependencies.releasesRepository,
                onBack = { navController.popBackStack() },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubRepositoryCommitsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubRepositoryCommitsRoute>()
            val factory = remember(route.fullName, route.ref, dependencies) {
                CommitsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    ref = route.ref,
                    repository = dependencies.commitsRepository
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
                onBack = { navController.popBackStack() },
                onOpenCommit = { commit ->
                    navController.navigate(GithubCommitRoute(route.fullName, commit.sha)) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubCommitRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubCommitRoute>()
            val factory = remember(route.fullName, route.sha, dependencies) {
                CommitDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    sha = route.sha,
                    repository = dependencies.commitsRepository
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
                onBack = { navController.popBackStack() },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubWorkflowRunsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubWorkflowRunsRoute>()
            val factory = remember(route.fullName, route.workflowId, route.workflowName, dependencies) {
                WorkflowRunsViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    workflowId = route.workflowId,
                    workflowName = route.workflowName,
                    repository = dependencies.actionsRepository
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
                onBack = { navController.popBackStack() },
                onOpenRun = { run ->
                    navController.navigate(
                        GithubActionsRunRoute(fullName = route.fullName, runId = run.id)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubActionsRunRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubActionsRunRoute>()
            val factory = remember(route.fullName, route.runId, dependencies) {
                ActionsRunDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    runId = route.runId,
                    repository = dependencies.actionsRepository
                )
            }
            val runViewModel: ActionsRunDetailViewModel = viewModel(
                key = "actions-run:${route.fullName}:${route.runId}",
                factory = factory
            )
            val runState by runViewModel.state.collectAsStateWithLifecycle()
            ActionsRunDetailScreen(
                state = runState,
                onAction = runViewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenJob = { job ->
                    navController.navigate(
                        GithubActionsJobRoute(fullName = route.fullName, jobId = job.id)
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubActionsJobRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubActionsJobRoute>()
            val factory = remember(route.fullName, route.jobId, dependencies) {
                ActionsJobDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    jobId = route.jobId,
                    repository = dependencies.actionsRepository
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
                onBack = { navController.popBackStack() },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubIssueRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubIssueRoute>()
            val factory = remember(route.fullName, route.number, dependencies) {
                IssueDetailViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    number = route.number,
                    repository = dependencies.issuesRepository
                )
            }
            val issueViewModel: IssueDetailViewModel = viewModel(
                key = "issue:${route.fullName}:${route.number}",
                factory = factory
            )
            val issueState by issueViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(sessionState.session, issueViewModel) {
                issueViewModel.onSessionChanged(sessionState.session)
            }
            IssueDetailScreen(
                state = issueState,
                onAction = issueViewModel::onAction,
                onBack = { navController.popBackStack() },
                canWrite = sessionState.session is GithubSession.SignedIn,
                onSignIn = { signInVisible = true },
                onOpenExternal = openUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubCreateIssueRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GithubCreateIssueRoute>()
            val factory = remember(route.fullName, dependencies) {
                CreateIssueViewModel.Factory(
                    owner = route.owner,
                    name = route.name,
                    repository = dependencies.issuesRepository
                )
            }
            val createViewModel: CreateIssueViewModel = viewModel(
                key = "create-issue:${route.fullName}",
                factory = factory
            )
            val createState by createViewModel.state.collectAsStateWithLifecycle()
            CreateIssueScreen(
                state = createState,
                canSubmit = sessionState.session is GithubSession.SignedIn,
                onAction = createViewModel::onAction,
                onBack = { navController.popBackStack() },
                onCreated = { issue ->
                    navController.popBackStack()
                    navController.navigate(
                        GithubIssueRoute(fullName = route.fullName, number = issue.number)
                    ) {
                        launchSingleTop = true
                    }
                },
                onSignIn = { signInVisible = true },
                modifier = Modifier.fillMaxSize()
            )
            }
        }
    }

    if (settingsVisible) {
        GithubSettingsSheet(
            settings = settings,
            onDismiss = { settingsVisible = false },
            onThemeSelected = { scope.launch { settingsManager.updateThemeMode(it) } },
            onPaletteSelected = { scope.launch { settingsManager.updateColorScheme(it) } },
            onLanguageSelected = { scope.launch { settingsManager.updateLanguage(it) } }
        )
    }

    if (starredVisible) {
        StarredCollectionsSheet(
            state = starredState,
            onAction = starredViewModel::onAction,
            onDismiss = { starredVisible = false },
            onSignIn = {
                starredVisible = false
                signInVisible = true
            },
            onOpenRepository = { repository ->
                starredVisible = false
                navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                    launchSingleTop = true
                }
            }
        )
    }

    if (signInVisible) {
        GithubSignInSheet(
            state = sessionState,
            onAction = sessionViewModel::onAction,
            onOpenUrl = openUrl,
            onDismiss = { signInVisible = false }
        )
    }
    if (accountsVisible) {
        GithubAccountSheet(
            state = sessionState,
            onAction = sessionViewModel::onAction,
            onAddAccount = {
                accountsVisible = false
                signInVisible = true
            },
            onDismiss = {
                accountsVisible = false
                sessionViewModel.onAction(GithubSessionAction.ClearAccountError)
            }
        )
    }
}
}

@Composable
private fun GithubUserConnectionsDestination(
    login: String,
    kind: GithubUserConnectionKind,
    repository: GithubPublicUserRepository,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val factory = remember(login, kind, repository) {
        GithubUserConnectionsViewModel.Factory(login, kind, repository)
    }
    val connectionsViewModel: GithubUserConnectionsViewModel = viewModel(
        key = "user-connections:$login:${kind.name}",
        factory = factory
    )
    val state by connectionsViewModel.state.collectAsStateWithLifecycle()
    GithubUserConnectionsScreen(
        state = state,
        onAction = connectionsViewModel::onAction,
        onBack = onBack,
        onOpenUser = onOpenUser,
        onOpenExternal = onOpenExternal,
        modifier = modifier
    )
}

@Composable
private fun GithubReleaseDetailDestination(
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

@Composable
private fun GithubAdaptiveScaffold(
    destination: GithubDestination,
    session: GithubSession,
    onDestinationSelected: (GithubDestination) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= GithubAdaptiveLayout.expandedWidth
        if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                GithubNavigationRail(destination, onDestinationSelected)
                GithubScaffold(
                    destination = destination,
                    session = session,
                    showBottomNavigation = false,
                    onDestinationSelected = onDestinationSelected,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) { modifier ->
                    content(modifier.widthIn(max = GithubAdaptiveLayout.contentMaxWidth).fillMaxSize())
                }
            }
        } else {
            GithubScaffold(
                destination = destination,
                session = session,
                showBottomNavigation = true,
                onDestinationSelected = onDestinationSelected,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxSize()
            ) { modifier ->
                content(modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GithubScaffold(
    destination: GithubDestination,
    session: GithubSession,
    showBottomNavigation: Boolean,
    onDestinationSelected: (GithubDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            GithubTopAppBar(
                destination = destination,
                session = session,
                onOpenInbox = { onDestinationSelected(GithubDestination.INBOX) },
                onOpenExplore = { onDestinationSelected(GithubDestination.EXPLORE) },
                onOpenProfile = { onDestinationSelected(GithubDestination.PROFILE) },
                onOpenSettings = onOpenSettings
            )
        },
        bottomBar = {
            if (showBottomNavigation) GithubBottomNavigation(destination, onDestinationSelected)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            content(Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GithubTopAppBar(
    destination: GithubDestination,
    session: GithubSession,
    onOpenInbox: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val title = destinationLabel(destination)
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        actions = {
            when (destination) {
                GithubDestination.HOME -> {
                    IconButton(onClick = onOpenExplore) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.github_explore)
                        )
                    }
                    val account = (session as? GithubSession.SignedIn)?.account
                    IconButton(onClick = onOpenProfile) {
                        if (account != null) {
                            GithubAvatar(
                                login = account.login,
                                avatarUrl = account.avatarUrl,
                                size = 30.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = stringResource(R.string.github_profile)
                            )
                        }
                    }
                }
                GithubDestination.EXPLORE -> IconButton(onClick = onOpenInbox) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.github_inbox)
                    )
                }
                GithubDestination.PROFILE -> IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.github_settings)
                    )
                }
                GithubDestination.INBOX -> Unit
                GithubDestination.COPILOT -> Unit
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun GithubBottomNavigation(
    destination: GithubDestination,
    onDestinationSelected: (GithubDestination) -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        githubNavigationItems.forEach { item ->
            NavigationBarItem(
                selected = destination == item.destination,
                onClick = { onDestinationSelected(item.destination) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(destinationLabel(item.destination)) }
            )
        }
    }
}

@Composable
private fun GithubNavigationRail(
    destination: GithubDestination,
    onDestinationSelected: (GithubDestination) -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        githubNavigationItems.forEach { item ->
            NavigationRailItem(
                selected = destination == item.destination,
                onClick = { onDestinationSelected(item.destination) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(destinationLabel(item.destination)) }
            )
        }
    }
}

@Composable
private fun GithubDestinationContent(
    destination: GithubDestination,
    inboxState: InboxUiState,
    exploreState: ExploreUiState,
    starredState: StarredUiState,
    session: GithubSession,
    savedAccountCount: Int,
    settings: AppSettings,
    modifier: Modifier,
    onInboxAction: (InboxAction) -> Unit,
    onExploreAction: (ExploreAction) -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetrySession: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenMyConversations: (MyConversationsKind) -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenRepository: (takagi.ru.monica.github.domain.GithubRepository) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenConversation: (GithubIssueSearchResult) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenNotification: (GithubNotification) -> Unit
) {
    Column(modifier = modifier) {
        GithubServiceStatusNotices(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(GithubExpressiveMotion.standardTween()) + slideInHorizontally(GithubExpressiveMotion.standardTween()) { it / 12 }) togetherWith
                    (fadeOut(GithubExpressiveMotion.quickTween()) + slideOutHorizontally(GithubExpressiveMotion.quickTween()) { -it / 16 })
            },
            label = "githubDestination"
        ) { target ->
            when (target) {
                GithubDestination.HOME -> HomeScreen(
                    session = session,
                    starredState = starredState,
                    onSignIn = onSignIn,
                    onRetrySession = onRetrySession,
                    onOpenStarred = onOpenStarred,
                    onOpenRepositories = onOpenRepositories,
                    onOpenOrganizations = onOpenOrganizations,
                    onOpenMyConversations = onOpenMyConversations,
                    onOpenRepository = onOpenRepository,
                    modifier = Modifier.fillMaxSize()
                )
                GithubDestination.INBOX -> InboxScreen(
                    state = inboxState,
                    onAction = onInboxAction,
                    onSignIn = onSignIn,
                    onOpenNotification = onOpenNotification
                )
                GithubDestination.EXPLORE -> ExploreScreen(
                    state = exploreState,
                    onAction = onExploreAction,
                    onOpenRepository = onOpenRepository,
                    onOpenUser = onOpenUser,
                    onOpenConversation = onOpenConversation,
                    onOpenExternal = onOpenUrl
                )
                GithubDestination.COPILOT -> CopilotPlaceholderScreen(
                    modifier = Modifier.fillMaxSize()
                )
                GithubDestination.PROFILE -> ProfileScreen(
                    settings = settings,
                    session = session,
                    savedAccountCount = savedAccountCount,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                    onRetrySession = onRetrySession,
                    onManageAccounts = onManageAccounts,
                    onOpenSettings = onOpenSettings,
                    onOpenRepositories = onOpenRepositories,
                    onOpenStarred = onOpenStarred,
                    onOpenOrganizations = onOpenOrganizations,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing
                )
            }
        }
    }
}

private data class NavigationItem(val destination: GithubDestination, val icon: ImageVector)

private val githubNavigationItems = listOf(
    NavigationItem(GithubDestination.HOME, Icons.Default.Home),
    NavigationItem(GithubDestination.INBOX, Icons.Default.Inbox),
    NavigationItem(GithubDestination.EXPLORE, Icons.Default.Search),
    NavigationItem(GithubDestination.COPILOT, Icons.Default.SmartToy),
    NavigationItem(GithubDestination.PROFILE, Icons.Default.Person)
)

@Composable
private fun destinationLabel(destination: GithubDestination): String = when (destination) {
    GithubDestination.HOME -> stringResource(R.string.github_home)
    GithubDestination.INBOX -> stringResource(R.string.github_inbox)
    GithubDestination.EXPLORE -> stringResource(R.string.github_explore)
    GithubDestination.COPILOT -> stringResource(R.string.github_copilot)
    GithubDestination.PROFILE -> stringResource(R.string.github_profile)
}
