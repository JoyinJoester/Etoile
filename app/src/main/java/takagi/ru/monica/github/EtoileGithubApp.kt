package takagi.ru.monica.github

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import takagi.ru.monica.github.navigation.GithubStoreDetailRoute
import takagi.ru.monica.github.feature.store.StoreReadingScreen
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import takagi.ru.monica.github.feature.home.HomeViewModel
import takagi.ru.monica.github.feature.home.HomeUiState
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
import takagi.ru.monica.github.component.GithubServiceStatusProvider
import takagi.ru.monica.github.component.GithubServiceStatusNotices
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
import takagi.ru.monica.github.domain.visibleRateLimits
import takagi.ru.monica.github.navigation.GithubDestination
import takagi.ru.monica.github.navigation.GithubActionsJobRoute
import takagi.ru.monica.github.navigation.GithubActionsRunRoute
import takagi.ru.monica.github.navigation.GithubHomeRoute
import takagi.ru.monica.github.navigation.GithubSignInRoute
import takagi.ru.monica.github.navigation.GithubSettingsRoute
import takagi.ru.monica.github.navigation.GithubAppearanceRoute
import takagi.ru.monica.github.navigation.GithubLanguageRoute
import takagi.ru.monica.github.navigation.GithubAccountsRoute
import takagi.ru.monica.github.navigation.GithubStarredRoute
import takagi.ru.monica.github.navigation.GithubMyConversationsRoute
import takagi.ru.monica.github.navigation.GithubOrganizationsRoute
import takagi.ru.monica.github.navigation.GithubBlockedUsersRoute
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
import takagi.ru.monica.github.settings.GithubAppearanceScreen
import takagi.ru.monica.github.settings.GithubLanguageScreen
import takagi.ru.monica.utils.SettingsManager

@Composable
fun EtoileGithubApp(
    settings: AppSettings,
    settingsManager: SettingsManager,
    initialGithubUrl: String? = null,
    onGithubUrlConsumed: (String) -> Unit = {},
    onExit: () -> Unit = {}
) {
    var destination by rememberSaveable { mutableStateOf(GithubDestination.HOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    val atRoot = currentEntry?.destination?.hasRoute<GithubHomeRoute>() == true
    BackHandler(enabled = atRoot) { showExitConfirmation = true }
    LaunchedEffect(atRoot) {
        if (!atRoot) showExitConfirmation = false
    }
    if (showExitConfirmation && atRoot) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.github_exit_title)) },
            text = { Text(stringResource(R.string.github_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onExit()
                }) { Text(stringResource(R.string.github_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.github_cancel))
                }
            }
        )
    }
    val dependencies = remember(context.applicationContext) { GithubAppDependencies(context.applicationContext) }
    val inboxFactory = remember(dependencies) { InboxViewModel.Factory(dependencies.notificationsRepository) }
    val starredFactory = remember(dependencies) { StarredViewModel.Factory(dependencies.starsRepository, dependencies.starLabelStore) }
    val exploreFactory = remember(dependencies) {
        ExploreViewModel.Factory(
            repository = dependencies.repositorySearchRepository,
            globalSearch = dependencies.repositorySearchRepository
        )
    }
    val profileFactory = remember(dependencies) {
        ProfileViewModel.Factory(
            contributionsRepository = dependencies.contributionsRepository,
            repositoryDetailsRepository = dependencies.repositoryDetailsRepository,
            publicUserRepository = dependencies.publicUserRepository
        )
    }
    val homeFactory = remember(dependencies) {
        HomeViewModel.Factory(contributionsRepository = dependencies.contributionsRepository)
    }
    val apkManager = remember(context.applicationContext) { GithubApkManager(context.applicationContext) }
    val storeFactory = remember(dependencies) {
        StoreViewModel.Factory(
            searchRepository = dependencies.repositorySearchRepository,
            releasesRepository = dependencies.releasesRepository,
            repositoryDetailsRepository = dependencies.repositoryDetailsRepository,
            sourceStore = GithubStoreSourceStore(context.applicationContext),
            apkManager = apkManager,
            fdroidIndexRepository = GithubFdroidIndexRepository(context.applicationContext),
            insightsStore = GithubApkInsightsStore(context.applicationContext)
        )
    }
    val sessionFactory = remember(dependencies) {
        GithubSessionViewModel.Factory(
            repository = dependencies.authRepository,
            deviceAuthRepository = dependencies.deviceAuthRepository,
            webAuthRepository = dependencies.webAuthRepository
        )
    }
    val inboxViewModel: InboxViewModel = viewModel(factory = inboxFactory)
    val starredViewModel: StarredViewModel = viewModel(factory = starredFactory)
    val exploreViewModel: ExploreViewModel = viewModel(factory = exploreFactory)
    val profileViewModel: ProfileViewModel = viewModel(factory = profileFactory)
    val homeViewModel: HomeViewModel = viewModel(factory = homeFactory)
    val storeViewModel: StoreViewModel = viewModel(factory = storeFactory)
    val sessionViewModel: GithubSessionViewModel = viewModel(factory = sessionFactory)
    val inboxState by inboxViewModel.state.collectAsStateWithLifecycle()
    val exploreState by exploreViewModel.state.collectAsStateWithLifecycle()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val storeState by storeViewModel.state.collectAsStateWithLifecycle()
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val starredState by starredViewModel.state.collectAsStateWithLifecycle()
    val homeContributionsState by homeViewModel.contributionsState.collectAsStateWithLifecycle()
    val rateLimits by dependencies.rateLimitMonitor.state.collectAsStateWithLifecycle()
    val cacheFallback by dependencies.cacheFallbackMonitor.state.collectAsStateWithLifecycle()
    val installUnavailableMessage = stringResource(R.string.github_store_install_unavailable)
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
            is GithubLinkDestination.Discussions -> navController.navigate(
                takagi.ru.monica.github.navigation.GithubRepositoryDiscussionsRoute(destination.fullName)
            ) { launchSingleTop = true }
            is GithubLinkDestination.Discussion -> navController.navigate(
                takagi.ru.monica.github.navigation.GithubDiscussionRoute(destination.fullName, destination.number)
            ) { launchSingleTop = true }
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

    val navScope = remember(dependencies) {
        GithubNavScope(
            dependencies = dependencies,
            navController = navController,
            sessionStateProvider = { sessionState },
            openUrl = openUrl
        )
    }

    LaunchedEffect(initialGithubUrl) {
        initialGithubUrl?.let { url ->
            if (dependencies.webAuthRepository.isCallbackUrl(url)) {
                sessionViewModel.onAction(GithubSessionAction.BrowserSignInCallback(url))
            } else {
                val opened = GithubLinkRouter.parse(url)?.let(::openNativeDestination) == true
                if (!opened) openUrl(url)
            }
            onGithubUrlConsumed(url)
        }
    }

    LaunchedEffect(storeState.selected?.repository?.fullName, storeState.selectedFdroid?.packageName) {
        if ((storeState.selected != null || storeState.selectedFdroid != null) &&
            navController.currentBackStackEntry?.destination?.hasRoute<GithubHomeRoute>() == true) {
            navController.navigate(GithubStoreDetailRoute) { launchSingleTop = true }
        }
    }

    LaunchedEffect(sessionState.session) {
        inboxViewModel.onSessionChanged(sessionState.session)
        starredViewModel.onSessionChanged(sessionState.session)
        profileViewModel.onSessionChanged(sessionState.session)
        (sessionState.session as? GithubSession.SignedIn)?.account?.login?.let { login ->
            homeViewModel.loadContributions(login)
        }
    }

    GithubServiceStatusProvider(
        rateLimits = visibleRateLimits(rateLimits),
        cacheFallback = cacheFallback
    ) {
        GithubTimestampProvider {
        CompositionLocalProvider(
            LocalGithubAvatarRepository provides dependencies.avatarRepository,
            LocalGithubUserNavigator provides { login ->
                openNativeDestination(GithubLinkDestination.User(login))
            }
        ) {
            NavHost(
            navController = navController,
            startDestination = GithubHomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { GithubNavigationTransitions.enter() },
            exitTransition = { GithubNavigationTransitions.exit() },
            popEnterTransition = { GithubNavigationTransitions.popEnter() },
            popExitTransition = { GithubNavigationTransitions.popExit() }
            ) {
        composable<GithubHomeRoute> {
            GithubAdaptiveScaffold(
                destination = destination,
                session = sessionState.session,
                designStyle = settings.designStyle,
                onDestinationSelected = { destination = it },
                onOpenSettings = { navController.navigate(GithubSettingsRoute) { launchSingleTop = true } }
            ) { contentModifier ->
                GithubDestinationContent(
                    destination = destination,
                    inboxState = inboxState,
                    exploreState = exploreState,
                    starredState = starredState,
                    profileState = profileState,
                    storeState = storeState,
                    homeState = HomeUiState(contributionsState = homeContributionsState),
                    session = sessionState.session,
                    savedAccountCount = sessionState.accounts.size,
                    modifier = contentModifier,
                    onInboxAction = inboxViewModel::onAction,
                    onExploreAction = exploreViewModel::onAction,
                    onProfileAction = profileViewModel::onAction,
                    onStoreAction = storeViewModel::onAction,
                    onRetryContributions = {
                        (sessionState.session as? GithubSession.SignedIn)?.account?.login?.let { login ->
                            homeViewModel.loadContributions(login)
                        }
                    },
                    onInstallApk = { file ->
                        runCatching { context.startActivity(apkManager.installIntent(file)) }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: installUnavailableMessage,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    },
                    onSignIn ={ navController.navigate(GithubSignInRoute) { launchSingleTop = true } },
                    onSignOut = { sessionViewModel.onAction(GithubSessionAction.SignOut) },
                    onRetrySession = { sessionViewModel.onAction(GithubSessionAction.RetryRestore) },
                    onManageAccounts = { navController.navigate(GithubAccountsRoute) { launchSingleTop = true } },
                    onOpenRepositories = {
                        navController.navigate(GithubUserRepositoriesRoute) {
                            launchSingleTop = true
                        }
                    },
                    onOpenStarred = { navController.navigate(GithubStarredRoute) { launchSingleTop = true } },
                    onOpenOrganizations = {
                        navController.navigate(GithubOrganizationsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onOpenBlockedUsers = {
                        navController.navigate(GithubBlockedUsersRoute) {
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

        githubAccountGraph(navScope)
        githubRepositoryGraph(navScope)
        githubConversationsGraph(navScope)
        githubActionsGraph(navScope)
        composable<GithubStoreDetailRoute> {
            LaunchedEffect(storeState.selected, storeState.selectedFdroid) {
                if (storeState.selected == null && storeState.selectedFdroid == null &&
                    navController.currentBackStackEntry?.destination?.hasRoute<GithubStoreDetailRoute>() == true) navController.popBackStack()
            }
            val closeStore = {
                storeViewModel.onAction(StoreAction.CloseApp)
                navController.popBackStack()
                Unit
            }
            BackHandler(onBack = closeStore)
            StoreReadingScreen(
                state = storeState,
                onAction = { action -> if (action == StoreAction.CloseApp) closeStore() else storeViewModel.onAction(action) },
                onOpenRepository = { repository ->
                    openNativeDestination(GithubLinkDestination.Repository(repository.fullName))
                },
                onOpenExternal = { url ->
                    if (GithubLinkRouter.parse(url)?.let(::openNativeDestination) != true) openUrl(url)
                },
                onOpenProject = { url ->
                    if (GithubLinkRouter.parse(url)?.let(::openNativeDestination) != true) {
                        val parts = url.substringAfter("github.com/", "").trimEnd('/').split('/')
                        if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                            openNativeDestination(GithubLinkDestination.Repository("${parts[0]}/${parts[1]}"))
                        } else openUrl(url)
                    }
                },
                onInstallApk = { file ->
                    runCatching { context.startActivity(apkManager.installIntent(file)) }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: installUnavailableMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    Unit
                }
            )
        }
        composable<GithubSignInRoute> {
            GithubSignInScreen(
                state = sessionState,
                onAction = sessionViewModel::onAction,
                onOpenUrl = openUrl,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
            // Mirror of the old sheet auto-dismiss: pop only when the session
            // changes to signed-in while this page is open, so opening it while
            // already signed in (adding an account) keeps the page on screen.
            var sessionEmissionSeen by remember { mutableStateOf(false) }
                LaunchedEffect(sessionState.session) {
                if (!sessionEmissionSeen) {
                    sessionEmissionSeen = true
                } else if (sessionState.session is GithubSession.SignedIn) {
                    navController.popBackStack()
                }
            }
        }
        composable<GithubSettingsRoute> {
            GithubSettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(GithubAppearanceRoute) { launchSingleTop = true } },
                onOpenLanguage = { navController.navigate(GithubLanguageRoute) { launchSingleTop = true } },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubAppearanceRoute> {
            GithubAppearanceScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onThemeSelected = { scope.launch { settingsManager.updateThemeMode(it) } },
                onPaletteSelected = { scope.launch { settingsManager.updateColorScheme(it) } },
                onDesignStyleSelected = { style ->
                    scope.launch {
                        settingsManager.updateDesignStyle(style)
                        when (style) {
                            // Nothing 锁定单色配色
                            DesignStyle.NOTHING ->
                                settingsManager.updateColorScheme(ColorScheme.NOTHING)
                            // 切回支持配色的设计时，若还停在 Nothing 单色则自动选默认配色
                            DesignStyle.MATERIAL ->
                                if (settings.colorScheme == ColorScheme.NOTHING) {
                                    settingsManager.updateColorScheme(ColorScheme.DEFAULT)
                                }
                            DesignStyle.MIUIX ->
                                if (settings.colorScheme == ColorScheme.NOTHING) {
                                    settingsManager.updateColorScheme(ColorScheme.MIUI_BLUE)
                                }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubLanguageRoute> {
            GithubLanguageScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onLanguageSelected = { language ->
                    if (language != settings.language) {
                        scope.launch { settingsManager.updateLanguage(language) }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubAccountsRoute> {
            GithubAccountsScreen(
                state = sessionState,
                onAction = sessionViewModel::onAction,
                onAddAccount = {
                    navController.navigate(GithubSignInRoute) {
                        launchSingleTop = true
                    }
                },
                onBack = {
                    sessionViewModel.onAction(GithubSessionAction.ClearAccountError)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<GithubStarredRoute> {
            val starredRouteViewModel: StarredViewModel = viewModel(
                key = "starred-page",
                factory = starredFactory
            )
            val starredRouteState by starredRouteViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(sessionState.session, starredRouteViewModel) {
                starredRouteViewModel.onSessionChanged(sessionState.session)
            }
            GithubStarredScreen(
                state = starredRouteState,
                onAction = starredRouteViewModel::onAction,
                onBack = { navController.popBackStack() },
                onSignIn = {
                    navController.navigate(GithubSignInRoute) {
                        launchSingleTop = true
                    }
                },
                onOpenRepository = { repository ->
                    navController.navigate(GithubRepositoryRoute(repository.fullName)) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
            }
        }
        }
    }
}
