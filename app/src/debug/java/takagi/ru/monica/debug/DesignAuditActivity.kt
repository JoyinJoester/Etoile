package takagi.ru.monica.debug

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import takagi.ru.monica.github.feature.pullrequest.PullRequestReviewCommentCard
import takagi.ru.monica.github.feature.pullrequest.PullRequestReviewComposer
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.github.feature.issues.CreateIssueViewModel
import takagi.ru.monica.github.feature.issues.CreateIssueScreen
import takagi.ru.monica.github.feature.issues.CreateIssueAction
import takagi.ru.monica.github.feature.repository.RepositoryWebhooksScreen
import takagi.ru.monica.github.feature.repository.RepositoryWebhooksUiState
import takagi.ru.monica.github.feature.repository.RepositoryFilesScreen
import takagi.ru.monica.github.feature.repository.RepositoryFilesUiState
import takagi.ru.monica.github.feature.repository.RepositoryFilesAction
import takagi.ru.monica.github.feature.repository.RepositoryFileScreen
import takagi.ru.monica.github.feature.repository.RepositoryFileUiState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import takagi.ru.monica.ui.components.MarkdownPreviewText
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.github.feature.inbox.InboxScreen
import takagi.ru.monica.github.feature.inbox.InboxUiState
import takagi.ru.monica.github.feature.auth.GithubAccountsScreen
import takagi.ru.monica.github.feature.auth.GithubSessionUiState
import takagi.ru.monica.github.feature.releases.*
import takagi.ru.monica.github.feature.profile.PublicUserProfileScreen
import takagi.ru.monica.github.feature.profile.PublicUserProfileUiState
import takagi.ru.monica.github.domain.GithubPublicUser
import takagi.ru.monica.github.feature.actions.ActionsWorkflowRow
import takagi.ru.monica.github.feature.actions.ActionsLogPanel
import takagi.ru.monica.github.feature.actions.ActionsRunSummaryCard
import takagi.ru.monica.github.feature.actions.ActionsJobSummaryCard
import takagi.ru.monica.github.feature.organizations.OrganizationsScreen
import takagi.ru.monica.github.feature.organizations.OrganizationsUiState
import takagi.ru.monica.github.feature.starred.GithubStarredScreen
import takagi.ru.monica.github.feature.starred.StarredUiState
import takagi.ru.monica.ui.theme.EtoileTheme

/** Deterministic visual fixtures. No session, repository, or network writes. */
class DesignAuditActivity : ComponentActivity() {
    private var lastAction by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sample = intent.getStringExtra("sample") ?: "release"
        val dark = intent.getBooleanExtra("dark", true)
        val style = DesignStyle.entries.firstOrNull {
            it.name.equals(intent.getStringExtra("style"), ignoreCase = true)
        } ?: if (sample.startsWith("m3e-")) DesignStyle.MATERIAL else DesignStyle.NOTHING
        setContent {
            if (sample.startsWith("m3e-") || sample.startsWith("wide-")) {
                M3eWorkspaceSamples(
                    initialPage = sample.removePrefix("m3e-").removePrefix("wide-"),
                    initialStyle = style,
                    initiallyDark = dark,
                    localeTag = intent.getStringExtra("locale") ?: "en",
                    onReport = ::report
                )
                return@setContent
            }
            EtoileTheme(darkTheme = dark, designStyle = style) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                    when (sample) {
                        "store-reader" -> StoreReaderSample()
                        "discussions" -> DiscussionsSample()
                        "create-pr" -> CreatePullRequestSample()
                        "device-code-autofill" -> DeviceCodeAutofillSample()
                        "login-device-choice" -> BrowserLoginSample(oauth = false)
                        "login-oauth-choice" -> BrowserLoginSample(oauth = true)
                        "login-browser-unavailable" -> BrowserLoginSample(oauth = true, failBrowser = true)
                        "login-real-browser" -> BrowserLoginSample(oauth = false, realBrowser = true)
                        "repository-reader" -> {
                            val code = remember {
                                (1..220).joinToString("\n") { line ->
                                    "fun renderLine$line(value: String): String = value.trim().ifEmpty { \"line-$line\" }"
                                }
                            }
                            RepositoryFileScreen(
                                state = RepositoryFileUiState(
                                    owner = "sample", name = "android-client",
                                    ref = "main", path = "app/src/main/java/sample/LongReader.kt",
                                    content = GithubFileContent.Text(code), isLoading = false
                                ), onAction = {}, onBack = { finish() }, onOpenExternal = {}
                            )
                        }
                        "repository-writer" -> RepositoryWriterSample()
                        "explore-restore" -> {
                            val repository = androidx.compose.runtime.remember {
                                object : GithubRepositorySearchRepository {
                                    override suspend fun search(query: String, page: Int, perPage: Int): Result<GithubPage<GithubRepository>> =
                                        Result.success(GithubPage(emptyList(), null))
                                }
                            }
                            val model: takagi.ru.monica.github.feature.explore.ExploreViewModel = viewModel(
                                factory = takagi.ru.monica.github.feature.explore.ExploreViewModel.Factory(repository)
                            )
                            val state by model.state.collectAsState()
                            takagi.ru.monica.github.feature.explore.ExploreScreen(
                                state = state, onAction = model::onAction,
                                onOpenRepository = {}, onOpenUser = {}, onOpenConversation = {},
                                onOpenExternal = {}, modifier = Modifier.systemBarsPadding()
                            )
                        }
                        "explore-toolbar" -> {
                            var query by rememberSaveable { mutableStateOf("language:kotlin stars:>1000") }
                            var selected by rememberSaveable { mutableStateOf(0) }
                            var flip by rememberSaveable { mutableStateOf(false) }
                            val options = listOf("Repositories", "Users", "Code", "Issues", "Pull requests")
                            Column(Modifier.systemBarsPadding().padding(24.dp)) {
                                takagi.ru.monica.github.feature.explore.ExploreSearchToolbar(
                                    query = query, onQueryChange = { query = it },
                                    placeholder = "Search GitHub", label = "Search type",
                                    selectedLabel = options[selected], options = options,
                                    selectedIndex = selected, onSelected = { selected = it },
                                    clearDescription = "Clear search", flipVisible = true,
                                    flipMode = flip, onFlipModeChange = { flip = it },
                                    flipDescription = "Switch result layout"
                                )
                            }
                        }
                        "copilot" -> takagi.ru.monica.github.feature.copilot.CopilotPlaceholderScreen(
                            modifier = Modifier.systemBarsPadding()
                        )
                        "repository-files", "repository-file", "repository-files-error", "repository-files-empty" -> {
                            val initialRef = "feature/nothing-design-with-extended-repository-navigation"
                            var selectedRef by rememberSaveable { mutableStateOf(initialRef) }
                            var path by rememberSaveable { mutableStateOf(if (sample == "repository-file") "app/src/main/java/sample/repository/components" else "app/src/main/java/sample/repository") }
                            var refError by rememberSaveable { mutableStateOf(sample == "repository-files-error") }
                            val entries = if (path.endsWith("components")) {
                                listOf("GithubComponents.kt" to GithubContentType.FILE, "GithubComponentsTest.kt" to GithubContentType.FILE)
                            } else listOf(
                                "components" to GithubContentType.DIRECTORY,
                                "empty-directory" to GithubContentType.DIRECTORY,
                                "RepositoryContentsWithAnExceptionallyLongFileName.kt" to GithubContentType.FILE,
                                "README.md" to GithubContentType.FILE
                            )
                            RepositoryFilesScreen(
                                state = RepositoryFilesUiState(
                                    owner = "sample", name = "android-client", ref = selectedRef, path = path,
                                    branches = GithubPage(listOf(GithubBranch(initialRef, "a", false), GithubBranch("main", "b", true)), null),
                                    tags = GithubPage(listOf(GithubTag("v0.1.0-preview", "c")), null),
                                    tagsLoaded = true, branchesError = refError,
                                    isLoading = false, isLoadingBranches = false,
                                    items = if (sample == "repository-files-empty" || path.endsWith("empty-directory")) emptyList() else entries.mapIndexed { index, (name, type) ->
                                        GithubContentItem(name, listOf(path, name).filter(String::isNotEmpty).joinToString("/"), "$index", 2048, type, null, null)
                                    },
                                    viewerLogin = sampleAccount.login,
                                    viewerRole = GithubCollaboratorRole.ADMIN
                                ),
                                onAction = {
                                    if (it == RepositoryFilesAction.RetryBranches) refError = false
                                    report(it.toString())
                                },
                                onBack = { finish() }, onOpenPath = { path = it },
                                onOpenFile = { report("Open file: ${it.path}") },
                                onSelectRef = { selectedRef = it }, onOpenExternal = { report(it) },
                                onSignIn = { report("SignIn") }
                            )
                        }
                        "organizations" -> OrganizationsScreen(
                            state = OrganizationsUiState(
                                isLoading = false,
                                items = listOf(
                                    GithubOrganization(1, "nothing-community-labs", null, "Open source tools and design systems for Android developers."),
                                    GithubOrganization(2, "sample-maintainer-with-a-very-long-organization-name", null, "A long description to verify truncation and readable spacing in the dark Nothing layout.")
                                )
                            ),
                            onAction = { report(it.toString()) }, onBack = { finish() },
                            onOpenOrganization = { report(it.login) }, onOpenExternal = { report(it) }
                        )
                        "webhooks" -> RepositoryWebhooksScreen(
                            state = RepositoryWebhooksUiState(
                                owner = "sample", name = "android-client", isLoading = false,
                                items = listOf(
                                    GithubRepositoryWebhook(108, "web", true,
                                        listOf("push", "pull_request", "issues", "release", "workflow_run", "discussion", "repository"),
                                        503, "Service Unavailable", "The destination did not respond before the timeout. Check the receiver service and retry the delivery on GitHub."),
                                    GithubRepositoryWebhook(109, "web", false, listOf("push"), 200, "OK", "Delivery accepted")
                                )
                            ),
                            onAction = { report(it.toString()) }, onBack = { finish() },
                            onOpenExternal = { report(it) }
                        )
                        "issue-templates", "issue-templates-empty", "issue-templates-required", "issue-templates-load-error", "issue-templates-submit-error" -> {
                            IssueTemplateSample(sample, intent.getStringExtra("locale") ?: "en", onBack = { finish() }, onReport = ::report)
                        }
                        "create-issue" -> {
                            // This fixture only exercises editing and Android saved-state wiring.
                            // Guard against accidental repository access; Submit is intercepted below.
                            val repository = androidx.compose.runtime.remember {
                                java.lang.reflect.Proxy.newProxyInstance(
                                    GithubIssuesRepository::class.java.classLoader,
                                    arrayOf(GithubIssuesRepository::class.java)
                                ) { _, _, _ -> error("No repository calls in the draft fixture") } as GithubIssuesRepository
                            }
                            val model: CreateIssueViewModel = viewModel(factory = CreateIssueViewModel.Factory(
                                "sample", "android-client", repository,
                                GithubIssueTemplatesRepository { _, _ -> Result.success(GithubIssueTemplateCatalog()) }
                            ))
                            val state by model.state.collectAsState()
                            androidx.compose.runtime.LaunchedEffect(state.catalog) {
                                if (state.catalog != null && !state.hasChosenTemplate) model.onAction(CreateIssueAction.SelectTemplate(null))
                            }
                            CreateIssueScreen(
                                state = state, canSubmit = true,
                                onAction = { action ->
                                    if (action == CreateIssueAction.Submit) report("Draft submit intercepted")
                                    else model.onAction(action)
                                },
                                onBack = { finish() }, onCreated = {}, onSignIn = {}, onOpenExternal = { report(it) }
                            )
                        }
                        "actions-detail" -> {
                            Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                ActionsRunSummaryCard(
                                    run = GithubWorkflowRun(
                                        17, 42, "Android release", "Verify Android release across all supported architectures",
                                        108, "workflow_dispatch", GithubActionsStatus.COMPLETED, GithubActionsConclusion.FAILURE,
                                        "release/september-with-extended-github-features", "abc1234567890123456789",
                                        GithubUserSummary("android-release-maintainer-with-long-id", null, "https://example.invalid"),
                                        date, date, date, "https://example.invalid"
                                    ),
                                    canManage = true, actionError = true,
                                    onAction = { report("Run action: $it") }
                                )
                                ActionsJobSummaryCard(GithubWorkflowJob(
                                    9, 17, "Build and verify Android arm64 release", GithubActionsStatus.COMPLETED,
                                    GithubActionsConclusion.FAILURE, date, date, "https://example.invalid",
                                    "self-hosted-android-release-runner-01", listOf("self-hosted", "linux", "arm64"),
                                    listOf(
                                        GithubWorkflowStep(1, "Checkout repository and restore dependency cache", GithubActionsStatus.COMPLETED, GithubActionsConclusion.SUCCESS, date, date),
                                        GithubWorkflowStep(2, "Compile all release architectures and verify integration behavior", GithubActionsStatus.COMPLETED, GithubActionsConclusion.TIMED_OUT, date, date),
                                        GithubWorkflowStep(3, "Upload signed release packages and build reports", GithubActionsStatus.COMPLETED, GithubActionsConclusion.SKIPPED, date, date)
                                    )
                                ))
                            }
                        }
                        "actions" -> {
                            Column(Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                ActionsWorkflowRow(
                                    workflow = GithubWorkflow(
                                        42, "Build and verify all Android release architectures",
                                        ".github/workflows/android-release-with-integration-checks.yml",
                                        GithubWorkflowState.ACTIVE, "https://example.invalid", null, date, date
                                    ),
                                    onClick = { report("Open workflow") },
                                    canManage = true,
                                    hasDispatchError = true,
                                    onEnabledChanged = { report("Workflow enabled: $it") },
                                    onDispatch = { ref, inputs -> report("Dispatch: $ref $inputs") }
                                )
                                ActionsLogPanel(GithubActionsLog(
                                    (1..12).joinToString("\n") { "Step $it: compiling app/src/main/java/sample/LongRepositoryDetailsViewModel.kt for arm64-v8a with release checks" },
                                    isTruncated = true
                                ))
                            }
                        }
                        "review" -> {
                            var draft by rememberSaveable { mutableStateOf("Please preserve the new draft when the request completes.") }
                            Column(Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                PullRequestReviewCommentCard(
                                    comment = reviewComment, fullName = "sample/android-client", ref = "main",
                                    onOpenExternal = { report(it) }
                                )
                                PullRequestReviewComposer(
                                    body = draft, canWrite = true, isValidationError = false,
                                    isSubmitError = true, isSubmitting = false,
                                    onBodyChanged = { draft = it },
                                    onSubmit = { report("Review action: $it") }, onSignIn = {}
                                )
                            }
                        }
                        "accounts" -> GithubAccountsScreen(
                            state = GithubSessionUiState(
                                session = GithubSession.SignedIn(accounts.first()),
                                accounts = accounts
                            ),
                            onAction = { report(it.toString()) },
                            onAddAccount = { report("add account") },
                            onBack = { finish() }
                        )
                        "links" -> Column(Modifier.systemBarsPadding().padding(24.dp)) {
                            Text(buildAnnotatedString {
                                withLink(LinkAnnotation.Clickable("native", linkInteractionListener = { report("native clicked") })) {
                                    append("Native link control")
                                }
                            })
                            MarkdownPreviewText(
                                markdown = "[Absolute link](https://example.invalid/absolute)\n\n[Relative link](../relative)",
                                onOpenExternalLink = { report("markdown: $it") },
                                modifier = Modifier.padding(top = 32.dp)
                            )
                        }
                        "labels" -> GithubStarredScreen(
                            state = StarredUiState(
                                requiresAuthentication = false,
                                labelManagerVisible = true,
                                labels = (1L..30L).map {
                                    GithubStarLabel(it, "Collection $it · Android libraries and development tools")
                                }
                            ),
                            onAction = { report(it.toString()) },
                            onBack = { finish() }, onSignIn = {}, onOpenRepository = {}
                        )
                        "inbox" -> InboxScreen(
                            state = InboxUiState(
                                requiresAuthentication = false,
                                items = notifications,
                                unreadIds = setOf("1", "2"),
                                triageErrorIds = setOf("1")
                            ),
                            onAction = { report(it.toString()) },
                            onSignIn = {}, onOpenNotification = { report(it.title) },
                            modifier = Modifier.systemBarsPadding()
                        )
                        "profile" -> PublicUserProfileScreen(
                            state = PublicUserProfileUiState(
                                login = "sample-maintainer",
                                user = GithubPublicUser(1, "sample-maintainer", "Sample Maintainer", "A long profile bio for testing responsive Nothing layouts and complete text access.", null, "https://example.invalid/user", "@sample", "Remote", "https://example.invalid", 42, 1280, 86, true, "2018-01-01T00:00:00Z"),
                                isLoadingUser = false, isLoadingRepositories = false, isLoadingCalendar = false, isLoadingReadme = false
                            ), onAction = { report(it.toString()) }, onBack = { finish() }, onOpenRepository = {}, onOpenFollowers = {}, onOpenFollowing = {}, viewerLogin = null, onSignIn = {}, onOpenExternal = { report(it) }
                        )
                        "release" -> ReleaseDetailScreen(
                            state = ReleaseDetailUiState(
                                owner = "sample", name = "android-client",
                                reference = ReleaseReference.Id(1),
                                release = release, isLoading = false
                            ),
                            onAction = {}, onBack = { finish() },
                            onOpenExternal = { report(it) }
                        )
                        else -> Text(
                            "Unknown audit sample: $sample",
                            Modifier.systemBarsPadding().padding(24.dp)
                        )
                    }
                    if (lastAction.isNotEmpty()) {
                        Surface(Modifier.align(Alignment.BottomCenter).systemBarsPadding()) {
                            Text(lastAction, Modifier.padding(16.dp))
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun report(message: String) {
        lastAction = message
        android.util.Log.i("EtoileUiAudit", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val date = "2026-09-12T12:00:00Z"
        val reviewComment = GithubPullRequestReviewComment(
            id = 1, body = "Keep the draft visible when the network request fails.",
            path = "app/src/main/java/sample/LongRepositoryDetailViewModel.kt", line = 42,
            startLine = null, side = "RIGHT",
            diffHunk = "@@ -10,4 +10,12 @@\n" + (1..12).joinToString("\n") {
                "+ val field$it = repository.loadFullRepositoryDetails(owner, name, includeArchived = false)"
            },
            author = GithubUserSummary("sample-reviewer", null, "https://example.invalid"),
            createdAt = date, updatedAt = date, htmlUrl = "https://example.invalid/review"
        )
        val accounts = (1L..4L).map {
            GithubAccount(
                it, "sample-maintainer-with-long-username-$it", "Developer account $it · Open source projects",
                null, "", "https://example.invalid", 0, 0, 0
            )
        }
        val notifications = (1..6).map {
            GithubNotification(
                "$it", GithubNotificationReason.REVIEW_REQUESTED, it < 3,
                "Review requested: preserve repository pagination and restore editor state after device rotation ($it)",
                "PullRequest", "sample/very-long-android-client-repository-name",
                "https://example.invalid/notification/$it", date
            )
        }
        val release = GithubRelease(
            id = 1, tagName = "v2026.09.13-beta.12", targetCommitish = "release/android",
            name = "Android client — September preview",
            body = "[Compare changes](../../compare/main...release)\n\n" + (1..25).joinToString("\n\n") {
                "## Improvement $it\n\nRepository browsing, accessible controls, and restored editor state. " +
                    "This long release description exercises the shortcut to downloadable assets."
            },
            author = GithubUserSummary("sample-maintainer", null, "https://example.invalid"),
            isDraft = false, isPrerelease = true, createdAt = date, publishedAt = date,
            htmlUrl = "https://example.invalid/release",
            assets = listOf("arm64-v8a", "armeabi-v7a", "x86_64").mapIndexed { index, architecture ->
                GithubReleaseAsset(
                    id = index.toLong(),
                    name = "Etoile-Android-2026.09.13-preview-with-extended-github-features-$architecture.apk",
                    label = "Android · $architecture", contentType = "application/vnd.android.package-archive",
                    sizeBytes = 32000000L, downloadCount = 12457, createdAt = date,
                    downloadUrl = "https://example.invalid/$architecture.apk"
                )
            }
        )
    }
}
