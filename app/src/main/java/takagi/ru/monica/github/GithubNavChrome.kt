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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.ui.unit.Dp
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
import takagi.ru.monica.github.design.LocalDesignStyle
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
import takagi.ru.monica.github.feature.home.HomeUiState
import takagi.ru.monica.github.feature.home.HomeViewModel
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


/**
 * 导航壳层:自适应脚手架、顶栏、底栏/侧栏与首页各标签的内容分发。
 */
@Composable
internal fun GithubAdaptiveScaffold(
    destination: GithubDestination,
    session: GithubSession,
    designStyle: DesignStyle,
    onDestinationSelected: (GithubDestination) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= GithubAdaptiveLayout.expandedWidth
        val desktop = maxWidth >= GithubAdaptiveLayout.desktopNavigationWidth
        val contentWidth = GithubAdaptiveLayout.wideContentMaxWidth
        Row(modifier = Modifier.fillMaxSize()) {
            if (expanded) {
                if (desktop) {
                    GithubDesktopNavigation(destination, onDestinationSelected)
                } else {
                    GithubNavigationRail(destination, onDestinationSelected)
                }
            }
            GithubScaffold(
                destination = destination,
                session = session,
                showBottomNavigation = !expanded,
                designStyle = designStyle,
                onDestinationSelected = onDestinationSelected,
                onOpenSettings = onOpenSettings,
                contentMaxWidth = contentWidth,
                modifier = Modifier.weight(1f).fillMaxSize()
            ) { modifier ->
                content(modifier.widthIn(max = contentWidth).fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GithubScaffold(
    destination: GithubDestination,
    session: GithubSession,
    showBottomNavigation: Boolean,
    designStyle: DesignStyle,
    onDestinationSelected: (GithubDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentMaxWidth: Dp = GithubAdaptiveLayout.wideContentMaxWidth,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                GithubTopAppBar(
                    destination = destination,
                    session = session,
                    onOpenInbox = { onDestinationSelected(GithubDestination.INBOX) },
                    onOpenExplore = { onDestinationSelected(GithubDestination.EXPLORE) },
                    onOpenProfile = { onDestinationSelected(GithubDestination.PROFILE) },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()
                )
            }
        },
        bottomBar = {
            if (showBottomNavigation) GithubBottomNavigation(destination, designStyle, onDestinationSelected)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            content(Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GithubTopAppBar(
    destination: GithubDestination,
    session: GithubSession,
    onOpenInbox: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = destinationLabel(destination)
    TopAppBar(
        modifier = modifier,
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
                GithubDestination.PROFILE -> Unit
                GithubDestination.INBOX -> Unit
                GithubDestination.STORE -> Unit
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.github_settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
internal fun GithubBottomNavigation(
    destination: GithubDestination,
    designStyle: DesignStyle,
    onDestinationSelected: (GithubDestination) -> Unit
) {
    if (designStyle == DesignStyle.MIUIX) {
        MiuixNavigationBar(color = MiuixTheme.colorScheme.surface) {
            githubNavigationItems.forEach { item ->
                MiuixNavigationBarItem(
                    selected = destination == item.destination,
                    onClick = { onDestinationSelected(item.destination) },
                    icon = item.icon,
                    label = destinationLabel(item.destination)
                )
            }
        }
        return
    }
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        githubNavigationItems.forEach { item ->
            NavigationBarItem(
                selected = destination == item.destination,
                onClick = { onDestinationSelected(item.destination) },
                icon = {
                    Icon(
                        if (destination == item.destination || designStyle != DesignStyle.MATERIAL) item.icon else item.outlinedIcon,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        navigationLabel(item.destination, designStyle),
                        maxLines = if (designStyle == DesignStyle.MATERIAL) 2 else 1,
                        softWrap = designStyle == DesignStyle.MATERIAL,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
internal fun GithubDesktopNavigation(
    destination: GithubDestination,
    onDestinationSelected: (GithubDestination) -> Unit
) {
    Column(
        modifier = Modifier.width(240.dp).fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        githubNavigationItems.forEach { item ->
            NavigationDrawerItem(
                selected = destination == item.destination,
                onClick = { onDestinationSelected(item.destination) },
                label = { Text(destinationLabel(item.destination)) },
                icon = { Icon(item.icon, contentDescription = null) },
                shape = takagi.ru.monica.github.design.GithubExpressiveShapes.control,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun GithubNavigationRail(
    destination: GithubDestination,
    onDestinationSelected: (GithubDestination) -> Unit
) {
    val expressive = LocalDesignStyle.current == DesignStyle.MATERIAL
    NavigationRail(
        modifier = Modifier
            .width(if (expressive) GithubAdaptiveLayout.navigationRailWidth else 80.dp)
            .fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            githubNavigationItems.forEach { item ->
                NavigationRailItem(
                    selected = destination == item.destination,
                    onClick = { onDestinationSelected(item.destination) },
                    icon = {
                        Icon(
                            if (destination == item.destination || !expressive) item.icon else item.outlinedIcon,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            navigationLabel(item.destination, LocalDesignStyle.current),
                            maxLines = if (expressive) 2 else 1,
                            softWrap = expressive,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun GithubDestinationContent(
    destination: GithubDestination,
    inboxState: InboxUiState,
    exploreState: ExploreUiState,
    starredState: StarredUiState,
    profileState: ProfileUiState,
    storeState: StoreUiState,
    homeState: HomeUiState,
    session: GithubSession,
    savedAccountCount: Int,
    modifier: Modifier,
    onInboxAction: (InboxAction) -> Unit,
    onExploreAction: (ExploreAction) -> Unit,
    onProfileAction: (ProfileAction) -> Unit,
    onStoreAction: (StoreAction) -> Unit,
    onRetryContributions: () -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetrySession: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
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
        val enterTween = GithubExpressiveMotion.standardTween<Float>()
        val enterOffsetTween = GithubExpressiveMotion.standardTween<IntOffset>()
        val exitTween = GithubExpressiveMotion.quickTween<Float>()
        val exitOffsetTween = GithubExpressiveMotion.quickTween<IntOffset>()
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(enterTween) + slideInHorizontally(enterOffsetTween) { it / 12 }) togetherWith
                    (fadeOut(exitTween) + slideOutHorizontally(exitOffsetTween) { -it / 16 })
            },
            label = "githubDestination"
        ) { target ->
            when (target) {
                GithubDestination.HOME -> HomeScreen(
                    session = session,
                    starredState = starredState,
                    contributionsState = homeState.contributionsState,
                    onSignIn = onSignIn,
                    onRetrySession = onRetrySession,
                    onRetryContributions = onRetryContributions,
                    onOpenStarred = onOpenStarred,
                    onOpenRepositories = onOpenRepositories,
                    onOpenOrganizations = onOpenOrganizations,
                    onOpenMyConversations = onOpenMyConversations,
                    onOpenRepository = onOpenRepository,
                    onOpenExternal = onOpenUrl,
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
                GithubDestination.STORE -> StoreScreen(
                    state = storeState.copy(selected = null, selectedFdroid = null),
                    onAction = onStoreAction,
                    onOpenRepository = onOpenRepository,
                    onOpenExternal = onOpenUrl,
                    onInstallApk = onInstallApk,
                    modifier = Modifier.fillMaxSize()
                )
                GithubDestination.PROFILE -> ProfileScreen(
                    session = session,
                    profileState = profileState,
                    savedAccountCount = savedAccountCount,
                    onProfileAction = onProfileAction,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                    onRetrySession = onRetrySession,
                    onManageAccounts = onManageAccounts,
                    onOpenRepositories = onOpenRepositories,
                    onOpenStarred = onOpenStarred,
                    onOpenOrganizations = onOpenOrganizations,
                    onOpenBlockedUsers = onOpenBlockedUsers,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                    onOpenExternal = onOpenUrl
                )
            }
        }
    }
}

private data class NavigationItem(
    val destination: GithubDestination,
    val icon: ImageVector,
    val outlinedIcon: ImageVector = icon
)

private val githubNavigationItems = listOf(
    NavigationItem(GithubDestination.HOME, Icons.Default.Home, Icons.Outlined.Home),
    NavigationItem(GithubDestination.INBOX, Icons.Default.Inbox, Icons.Outlined.Inbox),
    NavigationItem(GithubDestination.EXPLORE, Icons.Default.Search),
    NavigationItem(GithubDestination.STORE, Icons.Default.ShoppingCart, Icons.Outlined.ShoppingCart),
    NavigationItem(GithubDestination.PROFILE, Icons.Default.Person, Icons.Outlined.Person)
)

@Composable
private fun navigationLabel(destination: GithubDestination, designStyle: DesignStyle): String {
    if (designStyle != DesignStyle.MATERIAL) return destinationLabel(destination)
    return stringResource(
        when (destination) {
            GithubDestination.HOME -> R.string.github_nav_home
            GithubDestination.INBOX -> R.string.github_nav_inbox
            GithubDestination.EXPLORE -> R.string.github_nav_explore
            GithubDestination.STORE -> R.string.github_nav_store
            GithubDestination.PROFILE -> R.string.github_nav_profile
        }
    )
}

@Composable
internal fun destinationLabel(destination: GithubDestination): String = when (destination) {
    GithubDestination.HOME -> stringResource(R.string.github_home)
    GithubDestination.INBOX -> stringResource(R.string.github_inbox)
    GithubDestination.EXPLORE -> stringResource(R.string.github_explore)
    GithubDestination.STORE -> stringResource(R.string.github_store_tab)
    GithubDestination.PROFILE -> stringResource(R.string.github_profile)
}
