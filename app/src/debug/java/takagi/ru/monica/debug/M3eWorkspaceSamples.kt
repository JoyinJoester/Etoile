package takagi.ru.monica.debug

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.github.GithubAdaptiveScaffold
import takagi.ru.monica.github.domain.FdroidApp
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionDay
import takagi.ru.monica.github.domain.GithubContributionWeek
import takagi.ru.monica.github.domain.GithubLabeledStar
import takagi.ru.monica.github.domain.GithubNotification
import takagi.ru.monica.github.domain.GithubNotificationReason
import takagi.ru.monica.github.domain.GithubProfileAchievement
import takagi.ru.monica.github.domain.GithubProfileAchievementState
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubStarLabel
import takagi.ru.monica.github.domain.GithubStoreApp
import takagi.ru.monica.github.feature.explore.ExploreAction
import takagi.ru.monica.github.feature.explore.ExploreScreen
import takagi.ru.monica.github.feature.explore.ExploreUiState
import takagi.ru.monica.github.feature.home.HomeContributionsState
import takagi.ru.monica.github.feature.home.HomeScreen
import takagi.ru.monica.github.feature.inbox.InboxAction
import takagi.ru.monica.github.feature.inbox.InboxScreen
import takagi.ru.monica.github.feature.inbox.InboxUiState
import takagi.ru.monica.github.feature.profile.ProfileScreen
import takagi.ru.monica.github.feature.profile.ProfileUiState
import takagi.ru.monica.github.feature.repository.RepositoryDetailScreen
import takagi.ru.monica.github.feature.repository.RepositoryDetailUiState
import takagi.ru.monica.github.feature.starred.StarredUiState
import takagi.ru.monica.github.feature.store.StoreAction
import takagi.ru.monica.github.feature.store.StoreCatalogTab
import takagi.ru.monica.github.feature.store.StoreScreen
import takagi.ru.monica.github.feature.store.StoreUiState
import takagi.ru.monica.github.navigation.GithubDestination
import takagi.ru.monica.github.settings.GithubSettingsScreen
import takagi.ru.monica.github.settings.GithubAppearanceScreen
import takagi.ru.monica.github.settings.GithubLanguageScreen
import takagi.ru.monica.ui.theme.EtoileTheme
import java.time.LocalDate
import java.util.Locale

/** Production layouts with local state only. No repositories, credentials or remote writes. */
@Composable
internal fun M3eWorkspaceSamples(
    initialPage: String,
    initialStyle: DesignStyle,
    initiallyDark: Boolean,
    localeTag: String,
    onReport: (String) -> Unit
) {
    var style by rememberSaveable { mutableStateOf(initialStyle) }
    var theme by rememberSaveable { mutableStateOf(if (initiallyDark) ThemeMode.DARK else ThemeMode.LIGHT) }
    var palette by rememberSaveable { mutableStateOf(ColorScheme.TECH_PURPLE) }
    var language by rememberSaveable { mutableStateOf(Language.SYSTEM) }
    var destination by rememberSaveable {
        mutableStateOf(GithubDestination.entries.firstOrNull {
            it.name.equals(initialPage.substringBefore('-'), ignoreCase = true)
        } ?: GithubDestination.HOME)
    }
    var settingsPage by rememberSaveable {
        mutableStateOf(initialPage.takeIf { it in setOf("settings", "appearance", "language") })
    }
    var showRepository by rememberSaveable { mutableStateOf(initialPage == "repository") }
    var largeScreenPage by rememberSaveable {
        mutableStateOf(initialPage.takeIf { it in largeScreenSamplePages })
    }
    var detailPage by rememberSaveable {
        mutableStateOf(initialPage.takeIf { it in setOf("pull-request", "actions-run", "actions-job") })
    }
    var explore by remember { mutableStateOf(ExploreUiState(repositories = sampleRepositories, isLoading = false)) }
    var inbox by remember {
        mutableStateOf(InboxUiState(items = sampleNotifications, unreadIds = setOf("1", "2"), requiresAuthentication = false))
    }
    var store by remember {
        mutableStateOf(StoreUiState(
            apps = sampleRepositories,
            sources = sampleRepositories.take(1),
            isLoading = false,
            catalogTab = if (initialPage == "store-error") StoreCatalogTab.FDROID else StoreCatalogTab.RECOMMENDED,
            fdroidError = initialPage == "store-error",
            fdroidApps = listOf(FdroidApp("org.etoile.sample", "Etoile", "开源 GitHub 工作空间", "0.1", null, null, null))
        ))
    }
    val settings = AppSettings(designStyle = style, themeMode = theme, colorScheme = palette, language = language)
    val session = if (initialPage == "home-guest") GithubSession.SignedOut else GithubSession.SignedIn(sampleAccount)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedConfiguration = remember(configuration, localeTag, language) {
        Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(when (language) {
                Language.CHINESE -> "zh"
                Language.ENGLISH -> "en"
                Language.VIETNAMESE -> "vi"
                Language.JAPANESE -> "ja"
                Language.RUSSIAN -> "ru"
                Language.SYSTEM -> localeTag
            }))
        }
    }
    val localizedContext = remember(context, localizedConfiguration) {
        context.createConfigurationContext(localizedConfiguration)
    }
    BackHandler(enabled = settingsPage != null || showRepository || detailPage != null || largeScreenPage != null) {
        when (settingsPage) {
            "appearance", "language" -> settingsPage = "settings"
            "settings" -> settingsPage = null
            else -> if (largeScreenPage != null) largeScreenPage = null else if (detailPage != null) {
                detailPage = if (detailPage == "actions-job") "actions-run" else null
            } else showRepository = false
        }
    }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides localizedConfiguration) {
        EtoileTheme(
            darkTheme = theme == ThemeMode.DARK,
            designStyle = style,
            colorScheme = palette
        ) {
            when {
                largeScreenPage != null -> LargeScreenSamples(
                    page = largeScreenPage.orEmpty(),
                    onBack = { largeScreenPage = null },
                    onReport = onReport
                )
                detailPage != null -> M3eDetailSamples(
                    page = detailPage.orEmpty(),
                    onBack = { detailPage = if (detailPage == "actions-job") "actions-run" else null },
                    onOpenJob = { detailPage = "actions-job" },
                    onReport = onReport
                )
                settingsPage == "settings" -> GithubSettingsScreen(
                    settings = settings,
                    onBack = { settingsPage = null },
                    onOpenAppearance = { settingsPage = "appearance" },
                    onOpenLanguage = { settingsPage = "language" }
                )
                settingsPage == "appearance" -> GithubAppearanceScreen(
                    settings = settings,
                    onBack = { settingsPage = "settings" },
                    onThemeSelected = { theme = it },
                    onPaletteSelected = { palette = it },
                    onDesignStyleSelected = { style = it }
                )
                settingsPage == "language" -> GithubLanguageScreen(
                    settings = settings,
                    onBack = { settingsPage = "settings" },
                    onLanguageSelected = { language = it }
                )
                showRepository -> RepositoryDetailScreen(
                    state = sampleRepositoryState,
                    onAction = { onReport("Repository: $it") },
                    onBack = { showRepository = false },
                    onBrowseCode = { onReport("Files") },
                    onOpenBranches = { onReport("Branches") },
                    onOpenTags = { onReport("Tags") },
                    onOpenCollaborators = { onReport("Collaborators") },
                    onOpenWebhooks = { onReport("Webhooks") },
                    onOpenIssues = { onReport("Issues") },
                    onOpenPullRequests = { onReport("Pull requests") },
                    onOpenActions = { onReport("Actions") },
                    onOpenReleases = { onReport("Releases") },
                    onOpenCommits = { onReport("Commits") },
                    isSignedIn = true,
                    onSignIn = { onReport("Sign in") },
                    onOpenRepository = { onReport(it.fullName) },
                    onOpenExternal = onReport
                )
                else -> GithubAdaptiveScaffold(
                    destination = destination,
                    session = session,
                    designStyle = style,
                    onDestinationSelected = { destination = it },
                    onOpenSettings = { settingsPage = "settings" }
                ) { modifier ->
                    when (destination) {
                        GithubDestination.HOME -> HomeScreen(
                            session = session,
                            starredState = sampleStars,
                            contributionsState = HomeContributionsState(calendar = sampleCalendar),
                            onSignIn = { onReport("Sign in") },
                            onRetrySession = {},
                            onRetryContributions = {},
                            onOpenStarred = { onReport("Starred") },
                            onOpenRepositories = { onReport("Repositories") },
                            onOpenOrganizations = { onReport("Organizations") },
                            onOpenMyConversations = { onReport("My work: $it") },
                            onOpenRepository = { showRepository = true },
                            onOpenExternal = onReport,
                            modifier = modifier
                        )
                        GithubDestination.INBOX -> InboxScreen(
                            state = inbox,
                            onAction = { action ->
                                inbox = when (action) {
                                    is InboxAction.SelectFilter -> inbox.copy(selectedFilter = action.filter)
                                    InboxAction.MarkAllRead -> inbox.copy(unreadIds = emptySet())
                                    is InboxAction.MarkDone -> inbox.copy(items = inbox.items.filterNot { it.id == action.id })
                                    is InboxAction.Unsubscribe -> inbox.copy(items = inbox.items.filterNot { it.id == action.id })
                                    is InboxAction.OpenNotification -> inbox.copy(unreadIds = inbox.unreadIds - action.id)
                                    else -> inbox
                                }
                            },
                            onSignIn = { onReport("Sign in") },
                            onOpenNotification = { onReport(it.title) },
                            modifier = modifier
                        )
                        GithubDestination.EXPLORE -> ExploreScreen(
                            state = explore,
                            onAction = { action ->
                                explore = when (action) {
                                    is ExploreAction.QueryChanged -> explore.copy(query = action.query)
                                    is ExploreAction.SearchKindSelected -> explore.copy(
                                        searchKind = action.kind,
                                        repositories = if (action.kind == takagi.ru.monica.github.feature.explore.ExploreSearchKind.REPOSITORIES) sampleRepositories else emptyList()
                                    )
                                    is ExploreAction.TopicSelected -> explore.copy(selectedTopic = action.topic)
                                    else -> explore
                                }
                            },
                            onOpenRepository = { showRepository = true },
                            onOpenUser = onReport,
                            onOpenConversation = { onReport(it.title) },
                            onOpenExternal = onReport,
                            modifier = modifier
                        )
                        GithubDestination.STORE -> StoreScreen(
                            state = store,
                            onAction = { action ->
                                store = when (action) {
                                    is StoreAction.Search -> store.copy(query = action.query)
                                    is StoreAction.CatalogTabSelected -> store.copy(catalogTab = action.tab)
                                    is StoreAction.SourceInputChanged -> store.copy(sourceInput = action.value)
                                    is StoreAction.OpenApp -> store.copy(selected = GithubStoreApp(action.repository))
                                    is StoreAction.OpenFdroidApp -> store.copy(selectedFdroid = action.app)
                                    StoreAction.CloseApp -> store.copy(selected = null, selectedFdroid = null)
                                    StoreAction.RetryFdroid -> store.copy(fdroidError = false)
                                    else -> store.also { onReport("Store: $action") }
                                }
                            },
                            onOpenRepository = { showRepository = true },
                            onOpenExternal = onReport,
                            onInstallApk = { onReport("Install requested") },
                            modifier = modifier
                        )
                        GithubDestination.PROFILE -> ProfileScreen(
                            session = session,
                            profileState = sampleProfile,
                            savedAccountCount = 2,
                            onProfileAction = {},
                            onSignIn = { onReport("Sign in") },
                            onSignOut = { onReport("Sign out") },
                            onRetrySession = {},
                            onManageAccounts = { onReport("Accounts") },
                            onOpenRepositories = { onReport("Repositories") },
                            onOpenStarred = { onReport("Starred") },
                            onOpenOrganizations = { onReport("Organizations") },
                            onOpenBlockedUsers = { onReport("Blocked users") },
                            onOpenFollowers = { onReport("Followers") },
                            onOpenFollowing = { onReport("Following") },
                            onOpenExternal = onReport,
                            modifier = modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

internal val sampleAccount = GithubAccount(
    id = 1, login = "etoile-developer", name = "Etoile Developer",
    bio = "开源 Android 工具与 GitHub 工作空间。Building thoughtful tools for developers.",
    avatarUrl = "", htmlUrl = "https://github.com/etoile-developer",
    publicRepositories = 42, followers = 128, following = 32
)

internal val sampleRepositories = listOf(
    GithubRepository(1, "m3e-canvas", "lnkiai/m3e-canvas", "在浏览器中拼装 Material 3 Expressive 界面，把页面连接成可点击的原型。", "TypeScript", 1240, "2026-09-15T09:00:00Z", false, "https://github.com/lnkiai/m3e-canvas"),
    GithubRepository(2, "Etoile", "etoile/Etoile", "将仓库、讨论、通知和开源应用带到你的 Android 设备。", "Kotlin", 864, "2026-09-15T08:00:00Z", false, "https://github.com/etoile/Etoile"),
    GithubRepository(3, "compose-samples", "android/compose-samples", "Jetpack Compose 示例与响应式布局。", "Kotlin", 22840, "2026-09-14T08:00:00Z", false, "https://github.com/android/compose-samples"),
    GithubRepository(4, "repository-with-a-long-name-for-layout-review", "sample/repository-with-a-long-name-for-layout-review", "检查较长的仓库名、说明和元信息不会覆盖相邻的操作。", "Kotlin", 108, "2026-09-13T08:00:00Z", false, "https://example.invalid")
)

internal val sampleCalendar: GithubContributionCalendar = run {
    val start = LocalDate.of(2025, 9, 21)
    val weeks = (0 until 51).map { week ->
        GithubContributionWeek((0 until 7).map { day ->
            val level = (week * 3 + day) % 5
            GithubContributionDay(start.plusDays((week * 7 + day).toLong()).toString(), level * 2, level)
        })
    }
    GithubContributionCalendar(weeks, weeks.sumOf { week -> week.days.sumOf { it.count } })
}

private val sampleNotifications = listOf(
    GithubNotification("1", GithubNotificationReason.REVIEW_REQUESTED, true, "请审查：完善 M3E 页面的导航、分组和宽屏布局", "PullRequest", "etoile/Etoile", "https://github.com/etoile/Etoile", "2026-09-16T05:00:00Z"),
    GithubNotification("2", GithubNotificationReason.MENTION, true, "在窄屏和大字体下保留完整的标题与操作空间", "Issue", "sample/repository-with-a-long-name-for-layout-review", "https://example.invalid", "2026-09-15T18:00:00Z"),
    GithubNotification("3", GithubNotificationReason.OTHER, false, "Material 3 Expressive 组件更新", "Release", "lnkiai/m3e-canvas", "https://github.com/lnkiai/m3e-canvas", "2026-09-15T09:00:00Z")
)

internal val sampleStars = StarredUiState(
    repositories = sampleRepositories.take(2).map { GithubLabeledStar(it, listOf(GithubStarLabel(1, "Android 与设计"))) },
    labels = listOf(GithubStarLabel(1, "Android 与设计")), requiresAuthentication = false
)

private val sampleProfile = ProfileUiState(
    login = sampleAccount.login, calendar = sampleCalendar, readmeLoaded = true,
    readme = "# 开源，让工作更轻松\n\n关注 Android、界面设计与开发者工具。\n\n## 正在做的事\n\n- 完善 Etoile 的三种视觉风格\n- 改善代码和讨论的阅读体验\n- 让所有操作在大字体下仍然清晰可用",
    longestStreak = 24, currentStreak = 8,
    achievements = GithubProfileAchievement.entries.mapIndexed { i, item -> GithubProfileAchievementState(item, i < 3) }
)

private val sampleRepositoryState = RepositoryDetailUiState(
    owner = "etoile", name = "Etoile",
    details = GithubRepositoryDetails(
        repository = sampleRepositories[1], ownerLogin = "etoile", ownerAvatarUrl = null,
        defaultBranch = "main", forks = 64, watchers = 128, openIssues = 12,
        license = "MIT", topics = listOf("android", "github", "material3", "kotlin"), isArchived = false, isFork = false
    ),
    readme = "# Etoile\n\n你的 Android GitHub 工作空间。\n\n## 三种视觉风格\n\nNothing 是默认主视觉，也可以在设置中选择 Material 3 Expressive 或 Miuix。\n\n## 为阅读和协作设计\n\n浏览仓库与代码、关注讨论、审查改动，并安装开源 Android 应用。\n\n## 响应式布局\n\n窄屏保持单栏；宽屏将概览与 README 分开。系统大字体始终优先于列数。",
    isLoadingDetails = false, isLoadingReadme = false, viewerLogin = sampleAccount.login
)
