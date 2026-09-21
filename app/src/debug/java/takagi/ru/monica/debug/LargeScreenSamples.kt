package takagi.ru.monica.debug

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import takagi.ru.monica.github.GithubAdaptiveScaffold
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.actions.*
import takagi.ru.monica.github.feature.auth.*
import takagi.ru.monica.github.feature.commits.*
import takagi.ru.monica.github.feature.explore.*
import takagi.ru.monica.github.feature.issues.*
import takagi.ru.monica.github.feature.mywork.*
import takagi.ru.monica.github.feature.organizations.*
import takagi.ru.monica.github.feature.profile.*
import takagi.ru.monica.github.feature.pullrequest.*
import takagi.ru.monica.github.feature.releases.*
import takagi.ru.monica.github.feature.repository.*
import takagi.ru.monica.github.feature.starred.*
import takagi.ru.monica.github.feature.store.*
import takagi.ru.monica.github.navigation.GithubDestination

internal val largeScreenSamplePages = setOf(
    "issues", "issue", "pull-requests", "commits", "commit", "releases", "release",
    "branches", "contributors", "followers", "following", "blocked-users", "repositories", "organizations",
    "webhooks", "files", "code", "markdown", "starred", "my-issues", "my-prs",
    "compare",
    "workflows", "workflow-runs", "public-profile", "sign-in", "accounts",
    "store-detail", "fdroid-detail", "fdroid", "store-sources",
    "explore-users", "explore-code", "explore-issues", "explore-prs"
)

/** Real routed screens with deterministic data and local-only action handlers. */
@Composable
internal fun LargeScreenSamples(page: String, onBack: () -> Unit, onReport: (String) -> Unit) {
    var query by rememberSaveable(page) { mutableStateOf("") }
    var draft by rememberSaveable(page) { mutableStateOf("") }
    var count by rememberSaveable(page) { mutableIntStateOf(4) }
    var closed by rememberSaveable(page) { mutableStateOf(false) }
    var following by rememberSaveable(page) { mutableStateOf(false) }
    var blocked by rememberSaveable(page) { mutableStateOf(false) }
    val nextPage = if (count < 8) 2 else null
    val session = GithubSession.SignedIn(sampleAccount)
    val rows = remember {
        List(8) { index ->
            sampleRepositories[index % sampleRepositories.size].copy(
                id = index.toLong() + 1,
                name = "project-${index + 1}",
                fullName = "etoile/project-${index + 1}"
            )
        }
    }
    when (page) {
        "issues" -> IssuesScreen(
            state = IssuesUiState(
                "etoile", "android-client", items = wideIssues.take(count), nextPage = nextPage,
                searchQuery = query, selectedState = if (closed) GithubIssueState.CLOSED else GithubIssueState.OPEN,
                isLoading = false
            ),
            onAction = {
                when (it) {
                    is IssuesAction.SearchChanged -> query = it.query
                    is IssuesAction.SelectState -> closed = it.state == GithubIssueState.CLOSED
                    IssuesAction.LoadMore -> count += 4
                    else -> onReport("Issues: $it")
                }
            },
            onBack = onBack, onOpenIssue = { onReport(it.title) }, canCreateIssue = true,
            onCreateIssue = { onReport("Create issue") }, onSignIn = {}, onOpenExternal = onReport
        )
        "issue" -> IssueDetailScreen(
            state = IssueDetailUiState(
                "etoile", "android-client", 41, issue = wideIssues.first(),
                comments = listOf(GithubIssueComment(701, "The discussion stays readable beside the issue summary.",
                    wideAuthor, wideDate, wideDate, wideUrl)),
                isLoadingIssue = false, isLoadingComments = false, commentDraft = draft,
                viewerLogin = sampleAccount.login
            ),
            onAction = { if (it is IssueDetailAction.CommentChanged) draft = it.comment else onReport("Issue: $it") },
            onBack = onBack, canWrite = true, onSignIn = {}, onOpenExternal = onReport
        )
        "pull-requests" -> PullRequestsScreen(
            state = PullRequestsUiState(
                "etoile", "android-client", isLoading = false, nextPage = nextPage, searchQuery = query,
                items = List(count) { detailPullRequest.copy(id = it.toLong(), number = 128 + it, title = "Change ${it + 1}: ${wideTitles[it % wideTitles.size]}") }
            ),
            onAction = {
                if (it is PullRequestsAction.SearchChanged) query = it.query
                else if (it == PullRequestsAction.LoadMore) count += 4 else onReport("PRs: $it")
            },
            onBack = onBack, onOpenPullRequest = { onReport(it.title) }, onOpenExternal = onReport
        )
        "commits" -> CommitsScreen(
            CommitsUiState("etoile", "android-client", "main", items = wideCommits.take(count), nextPage = nextPage, isLoading = false),
            onAction = { if (it == CommitsAction.LoadMore) count += 4 else onReport("Commits: $it") },
            onBack = onBack, onOpenCommit = { onReport(it.sha) }, onOpenExternal = onReport
        )
        "commit" -> CommitDetailScreen(
            CommitDetailUiState("etoile", "android-client", wideCommits.first().sha, details = wideCommitDetails, isLoading = false),
            onAction = {}, onBack = onBack, onOpenExternal = onReport
        )
        "releases" -> ReleasesScreen(
            ReleasesUiState("etoile", "android-client", items = List(count) { wideRelease.copy(id = 100L + it, tagName = "v1.${it}.0", name = "Release ${it + 1}: ${wideTitles[it % wideTitles.size]}") }, nextPage = nextPage, isLoading = false),
            onAction = { if (it == ReleasesAction.LoadMore) count += 4 else onReport("Releases: $it") },
            onBack = onBack, onOpenRelease = { onReport(it.displayName) }, onOpenExternal = onReport
        )
        "release" -> ReleaseDetailScreen(
            ReleaseDetailUiState("etoile", "android-client", ReleaseReference.Id(wideRelease.id), release = wideRelease, isLoading = false),
            onAction = {}, onBack = onBack, onOpenExternal = onReport
        )
        "branches" -> RepositoryBranchesScreen(
            RepositoryBranchesUiState("etoile", "android-client", "main", query = query, isLoading = false,
                items = listOf("main", "feature/adaptive-navigation", "fix/readable-large-text", "release/september-preview").mapIndexed { i, branch -> GithubBranch(branch, "a".repeat(39) + i, i == 0) }),
            onAction = { if (it is RepositoryBranchesAction.Search) query = it.query else onReport("Branches: $it") },
            onBack = onBack, onOpenBranch = { onReport(it.name) }, onOpenExternal = onReport,
            onCompare = { onReport("Branches: Compare " + it.name) },
            onSignIn = { onReport("Branches: SignIn") }
        )
        "compare" -> RepositoryCompareScreen(
            RepositoryCompareUiState("etoile", "android-client", "main", "feature/adaptive-navigation",
                comparison = GithubBranchComparison("diverged", 3, 1, 3, compareFiles, false), isLoading = false),
            onAction = { onReport("Compare: $it") },
            onBack = onBack, onOpenExternal = onReport
        )
        "contributors" -> RepositoryCollaboratorsScreen(
            RepositoryCollaboratorsUiState("etoile", "android-client", query = query, isLoading = false,
                viewerLogin = sampleAccount.login, viewerRole = GithubCollaboratorRole.ADMIN,
                items = wideUsers.mapIndexed { i, user ->
                    if (i == 1) GithubCollaborator(user, GithubCollaboratorRole.WRITE)
                    else GithubCollaborator(user, GithubCollaboratorRole.UNKNOWN, 420 - i * 30)
                }),
            onAction = { if (it is RepositoryCollaboratorsAction.Search) query = it.query else onReport("Contributors: $it") },
            onBack = onBack, onOpenUser = onReport, onOpenExternal = onReport,
            onSignIn = { onReport("Contributors: SignIn") },
            onInvite = { onReport("Contributors: Invite") }
        )
        "followers", "following" -> GithubUserConnectionsScreen(
            GithubUserConnectionsUiState("etoile-developer", if (page == "followers") GithubUserConnectionKind.FOLLOWERS else GithubUserConnectionKind.FOLLOWING, users = wideUsers, isLoading = false),
            onAction = { onReport("Connections: $it") }, onBack = onBack, onOpenUser = onReport, onOpenExternal = onReport
        )
        "blocked-users" -> GithubBlockedUsersScreen(
            GithubBlockedUsersUiState(users = wideUsers, isLoading = false, requiresAuthentication = false),
            onAction = { onReport("Blocked: $it") }, onBack = onBack, onOpenUser = onReport, onSignIn = {}
        )
        "repositories" -> UserRepositoriesScreen(
            UserRepositoriesUiState(items = rows.take(count), nextPage = nextPage, isLoading = false), accountLogin = sampleAccount.login,
            onAction = { if (it == UserRepositoriesAction.LoadMore) count += 4 else onReport("Repositories: $it") },
            onBack = onBack, onOpenRepository = { onReport(it.fullName) }, onOpenExternal = onReport,
            onCreateRepository = { onReport("Create repository") }
        )
        "organizations" -> OrganizationsScreen(
            OrganizationsUiState(isLoading = false, items = List(6) { GithubOrganization(it.toLong(), "open-source-team-${it + 1}", null, "Building accessible Android tools and open source communities.") }),
            onAction = { onReport("Organizations: $it") }, onBack = onBack,
            onOpenOrganization = { onReport(it.login) }, onOpenExternal = onReport
        )
        "webhooks" -> RepositoryWebhooksScreen(
            RepositoryWebhooksUiState("etoile", "android-client", isLoading = false, items = List(4) {
                GithubRepositoryWebhook(it.toLong(), "Delivery ${it + 1}", url = null, isActive = it % 2 == 0, events = listOf("push", "pull_request", "issues", "release"), lastResponseCode = 200, lastResponseStatus = "OK", lastResponseMessage = "Delivery accepted")
            }),
            onAction = { onReport("Webhooks: $it") }, onBack = onBack, onOpenExternal = onReport
        )
        "files" -> RepositoryFilesScreen(
            RepositoryFilesUiState("etoile", "android-client", "main", "app/src/main", isLoading = false,
                isLoadingBranches = false, branches = GithubPage(listOf(GithubBranch("main", "a", true)), null),
                items = listOf("java", "res", "AndroidManifest.xml", "README.md", "GithubAdaptiveLayout.kt", "RepositoryDetailsWithLongNames.kt").mapIndexed { i, name ->
                    GithubContentItem(name, "app/src/main/$name", "$i", 2048, if (i < 2) GithubContentType.DIRECTORY else GithubContentType.FILE, null, null)
                },
                viewerLogin = sampleAccount.login, viewerRole = GithubCollaboratorRole.ADMIN),
            onAction = { onReport("Files: $it") }, onBack = onBack, onOpenPath = onReport,
            onOpenFile = { onReport(it.name) }, onSelectRef = onReport, onOpenExternal = onReport,
            onSignIn = { onReport("Files: SignIn") }
        )
        "code", "markdown" -> RepositoryFileScreen(
            RepositoryFileUiState("etoile", "android-client", "main", if (page == "code") "GithubAdaptiveLayout.kt" else "README.md",
                content = GithubFileContent.Text(if (page == "code") (1..120).joinToString("\n") { "fun renderLine$it(value: String): String = value.trim().ifEmpty { \"line-$it\" }" } else wideReadme), isLoading = false),
            onAction = {}, onBack = onBack, onOpenExternal = onReport
        )
        "starred" -> GithubStarredScreen(
            sampleStars.copy(repositories = rows.take(count).map { GithubLabeledStar(it, sampleStars.labels) }),
            onAction = { onReport("Starred: $it") }, onBack = onBack, onSignIn = {}, onOpenRepository = { onReport(it.fullName) }
        )
        "my-issues", "my-prs" -> MyConversationsScreen(
            kind = if (page == "my-issues") MyConversationsKind.ISSUES else MyConversationsKind.PULL_REQUESTS,
            state = MyConversationsUiState(items = wideConversations.take(count).map {
                if (page == "my-prs") it.copy(type = GithubIssueSearchType.PULL_REQUEST) else it
            }, requiresAuthentication = false, nextPage = nextPage),
            session = session, onAction = { if (it == MyConversationsAction.LoadMore) count += 4 else onReport("My work: $it") },
            onBack = onBack, onOpenConversation = { onReport(it.title) }, onSignIn = {}
        )
        "workflows" -> ActionsWorkflowsScreen(
            ActionsWorkflowsUiState("etoile", "android-client", isLoading = false, items = List(4) {
                GithubWorkflow(it.toLong(), "Workflow ${it + 1}: ${wideTitles[it]}", ".github/workflows/check-$it.yml", GithubWorkflowState.ACTIVE, wideUrl, null, wideDate, wideDate)
            }),
            onAction = { onReport("Workflows: $it") }, onBack = onBack, onOpenWorkflow = { onReport(it.name) }, onOpenExternal = onReport
        )
        "workflow-runs" -> WorkflowRunsScreen(
            WorkflowRunsUiState("etoile", "android-client", 7, "Android Build", isLoading = false, items = List(4) { detailRun.copy(id = it.toLong(), runNumber = it + 101, displayTitle = "Build ${it + 1}: ${wideTitles[it]}") }),
            onAction = { onReport("Runs: $it") }, onBack = onBack, onOpenRun = { onReport(it.displayTitle.orEmpty()) }, onOpenExternal = onReport
        )
        "public-profile" -> PublicUserProfileScreen(
            PublicUserProfileUiState("etoile-developer", user = widePublicUser, repositories = rows.take(3),
                calendar = sampleCalendar, readme = wideReadme, isLoadingUser = false, isLoadingRepositories = false,
                isLoadingCalendar = false, isLoadingReadme = false, isFollowing = following,
                isBlocked = blocked),
            onAction = {
                when (it) {
                    PublicUserProfileAction.ToggleFollowing -> following = !following
                    PublicUserProfileAction.ToggleBlocked -> blocked = !blocked
                    else -> onReport("Profile: $it")
                }
            },
            onBack = onBack, onOpenRepository = { onReport(it.fullName) }, onOpenFollowers = { onReport("Followers") },
            onOpenFollowing = { onReport("Following") }, viewerLogin = "layout-reviewer", onSignIn = {}, onOpenExternal = onReport
        )
        "sign-in" -> GithubSignInScreen(
            GithubSessionUiState(session = GithubSession.SignedOut, tokenInput = draft),
            onAction = { if (it is GithubSessionAction.TokenChanged) draft = it.value else onReport("Local sign-in action") },
            onOpenUrl = onReport, onBack = onBack
        )
        "accounts" -> GithubAccountsScreen(
            GithubSessionUiState(session = session, accounts = listOf(sampleAccount, sampleAccount.copy(id = 2, login = "work-account"))),
            onAction = { onReport("Accounts: $it") }, onAddAccount = { onReport("Add account") }, onBack = onBack
        )
        "store-detail", "fdroid-detail", "fdroid", "store-sources" -> {
            var store by remember(page) { mutableStateOf(StoreUiState(
                apps = rows, sources = rows.take(2), isLoading = false, fdroidApps = wideFdroidApps,
                catalogTab = when (page) {
                    "fdroid" -> StoreCatalogTab.FDROID
                    else -> StoreCatalogTab.RECOMMENDED
                },
                selected = if (page == "store-detail") GithubStoreApp(sampleRepositories[1], wideRelease) else null,
                selectedFdroid = if (page == "fdroid-detail") wideFdroidApps.first() else null
            )) }
            GithubAdaptiveScaffold(GithubDestination.STORE, session, LocalDesignStyle.current,
                onDestinationSelected = { onReport(it.name) }, onOpenSettings = { onReport("Settings") }) { modifier ->
                // Production inputs live in StoreViewModel; mirror that lifetime during
                // fixture activity recreation when the emulator changes window size.
                StoreScreen(store.copy(sourceInput = draft, query = query), onAction = { action ->
                    store = when (action) {
                        is StoreAction.Search -> store.also { query = action.query }
                        is StoreAction.CatalogTabSelected -> store.copy(catalogTab = action.tab)
                        is StoreAction.SourceInputChanged -> store.also { draft = action.value }
                        is StoreAction.OpenApp -> store.copy(selected = GithubStoreApp(action.repository, wideRelease))
                        is StoreAction.OpenFdroidApp -> store.copy(selectedFdroid = action.app)
                        StoreAction.CloseApp -> store.copy(selected = null, selectedFdroid = null)
                        else -> store.also { onReport("Local store action: $action") }
                    }
                }, onOpenRepository = { onReport(it.fullName) }, onOpenExternal = onReport, onInstallApk = {}, modifier = modifier)
            }
        }
        "explore-users", "explore-code", "explore-issues", "explore-prs" -> {
            val kind = when (page) {
                "explore-users" -> ExploreSearchKind.USERS
                "explore-code" -> ExploreSearchKind.CODE
                "explore-prs" -> ExploreSearchKind.PULL_REQUESTS
                else -> ExploreSearchKind.ISSUES
            }
            GithubAdaptiveScaffold(GithubDestination.EXPLORE, session, LocalDesignStyle.current,
                onDestinationSelected = { onReport(it.name) }, onOpenSettings = { onReport("Settings") }) { modifier ->
                ExploreScreen(
                    ExploreUiState(query = "adaptive", searchKind = kind, isLoading = false,
                        users = if (kind == ExploreSearchKind.USERS) wideUsers.mapIndexed { i, user -> GithubUserSearchResult(i.toLong(), user.login, null, wideUrl, "User") } else emptyList(),
                        code = if (kind == ExploreSearchKind.CODE) List(6) { GithubCodeSearchResult("$it", "Adaptive${it + 1}.kt", "app/src/main/Adaptive${it + 1}.kt", "$it", "etoile/android-client", wideUrl) } else emptyList(),
                        conversations = if (kind in listOf(ExploreSearchKind.ISSUES, ExploreSearchKind.PULL_REQUESTS)) wideConversations.take(4).map {
                            if (kind == ExploreSearchKind.PULL_REQUESTS) it.copy(type = GithubIssueSearchType.PULL_REQUEST) else it
                        } else emptyList()),
                    onAction = { onReport("Explore: $it") }, onOpenRepository = { onReport(it.fullName) }, onOpenUser = onReport,
                    onOpenConversation = { onReport(it.title) }, onOpenExternal = onReport, modifier = modifier
                )
            }
        }
    }
}

private const val wideDate = "2026-09-17T01:00:00Z"
private const val wideUrl = "https://example.invalid/etoile/android-client"
private val wideAuthor = GithubUserSummary("android-maintainer", null, wideUrl)
private val compareFiles = listOf(
    GithubPullRequestFile(
        sha = "compare-1",
        filename = "app/src/main/java/takagi/ru/monica/github/feature/repository/RepositoryCompareScreen.kt",
        status = "added", additions = 194, deletions = 0, changes = 194,
        patch = "@@ -0,0 +1,4 @@\n+@Composable\n+fun RepositoryCompareScreen(\n+    state: RepositoryCompareUiState,\n+    onAction: (RepositoryCompareAction) -> Unit,",
        blobUrl = "https://example.invalid/etoile/android-client/blob/feature/adaptive-navigation/RepositoryCompareScreen.kt",
        rawUrl = null
    ),
    GithubPullRequestFile(
        sha = "compare-2",
        filename = "app/src/main/java/takagi/ru/monica/github/navigation/GithubWebUrls.kt",
        status = "modified", additions = 9, deletions = 1, changes = 10,
        patch = "@@ -89,1 +89,9 @@\n-    fun tags(fullName: String): String = build(fullName, \"tags\")\n+    fun tags(fullName: String): String = build(fullName, \"tags\")\n+\n+    fun compare(fullName: String, base: String, head: String): String",
        blobUrl = "https://example.invalid/etoile/android-client/blob/feature/adaptive-navigation/GithubWebUrls.kt",
        rawUrl = null
    ),
    GithubPullRequestFile(
        sha = "compare-3",
        filename = "app/src/main/java/takagi/ru/monica/github/feature/repository/RepositoryBranchesScreen.kt",
        status = "modified", additions = 6, deletions = 2, changes = 8,
        patch = "@@ -249,2 +249,6 @@\n-                        onClick = { onOpenBranch(branch) },\n+                        onClick = { onOpenBranch(branch) },\n+                        onCompare = { onCompare(branch) },",
        blobUrl = "https://example.invalid/etoile/android-client/blob/feature/adaptive-navigation/RepositoryBranchesScreen.kt",
        rawUrl = null
    )
)

private val wideTitles = listOf(
    "Keep every page comfortable on a large display",
    "Preserve drafts when the window changes size",
    "Make navigation and search easier to reach",
    "Support large text in lists and detail panels"
)
private val wideReadme = """
    # A workspace that fits your screen

    Browse repositories, follow discussions, and review changes with enough space for the work at hand.

    ## Reading and navigation

    Details stay beside the conversation on wide windows. On smaller windows, the page returns to one column.

    - Open repositories and inspect their files.
    - Review changes and discuss improvements.
    - Choose an issue template in Chinese or English.

    ## Accessible at larger text sizes

    Controls wrap naturally and lists use fewer columns as text grows. You can keep reading without horizontal scrolling.
""".trimIndent()
private val wideIssues = List(8) {
    GithubIssue(41L + it, 41 + it, "Issue ${it + 1}: ${wideTitles[it % wideTitles.size]}", wideReadme,
        GithubIssueState.OPEN, wideAuthor, listOf(GithubIssueLabel("enhancement", "6750A4", null)),
        listOf(wideAuthor), 1, false, wideDate, wideDate, null, wideUrl)
}
private val wideConversations = wideIssues.map {
    GithubIssueSearchResult(it.id, it.number, it.title, it.state, GithubIssueSearchType.ISSUE, false,
        it.author, it.labels, it.comments, "etoile/android-client", wideDate, wideDate, wideUrl)
}
private val wideCommits = List(8) {
    GithubCommit("abcdef0123456789012345678901234567890123$it", "Commit ${it + 1}: ${wideTitles[it % wideTitles.size]}",
        "Android Maintainer", wideAuthor.login, null, wideDate, "Android Maintainer", wideDate, wideUrl, true)
}
private val wideCommitDetails = GithubCommitDetails(wideCommits.first(), 48, 12, 60,
    List(3) { GithubCommitFile("app/src/main/Adaptive${it + 1}.kt", null, GithubCommitFileStatus.MODIFIED, 16, 4, 20, wideUrl, null,
        "@@ -20,3 +20,3 @@\n- val columns = 1\n+ val columns = adaptiveColumnCount(width, fontScale)\n  Workspace(columns)") })
internal val wideRelease = GithubRelease(202, "v1.2.0", "main", "A more spacious Android workspace", wideReadme,
    wideAuthor, false, false, wideDate, wideDate, wideUrl,
    listOf("arm64-v8a", "armeabi-v7a", "x86_64").mapIndexed { i, abi ->
        GithubReleaseAsset(500L + i, "Etoile-1.2.0-$abi.apk", null, "application/vnd.android.package-archive", 32_000_000L, 240,
            wideDate, "$wideUrl/download/$abi.apk")
    })
private val wideUsers = List(8) { GithubUserSummary("contributor-${it + 1}", null, wideUrl) }
private val widePublicUser = GithubPublicUser(3, "etoile-developer", "Etoile Developer",
    "Building useful Android tools for open source communities.", null, wideUrl, "Open Source", "Shanghai", null, 42, 128, 32, true)
private val wideFdroidApps = List(6) { FdroidApp("org.etoile.sample$it", "Open App ${it + 1}", wideTitles[it % wideTitles.size], "1.2.0", null, null, null) }
