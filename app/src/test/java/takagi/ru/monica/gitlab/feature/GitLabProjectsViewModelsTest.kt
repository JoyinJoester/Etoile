package takagi.ru.monica.gitlab.feature

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.gitlab.domain.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabProjectsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val account = GitLabAccount(42, "https://gitlab.com/", "alice", "Alice", "")
    private val project = GitLabProject(1, "Etoile", "team/Etoile", "", "private", "main", 0, 0, false)
    private val sha = "a".repeat(40)
    @Before fun prepareMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun listSearchResetsServerPagingAndPagingDeduplicatesStableIds() = runTest(dispatcher) {
        val auth = Auth(account)
        val repo = Repository()
        repo.projectsBlock = { _, _, page -> Result.success(if (page == 1) GitLabPage(listOf(project), 2)
            else GitLabPage(listOf(project, project.copy(id = 2)), null)) }
        val vm = GitLabProjectsViewModel(account.key, auth, repo)
        runCurrent()
        vm.onAction(GitLabProjectsAction.LoadMore)
        vm.onAction(GitLabProjectsAction.LoadMore)
        runCurrent()
        assertEquals(listOf(1L, 2L), vm.state.value.items.map { it.id })
        assertEquals(listOf("" to 1, "" to 2), repo.searches)
        vm.onAction(GitLabProjectsAction.SearchChanged("  hello  "))
        assertEquals(2, repo.searches.size)
        vm.onAction(GitLabProjectsAction.Search)
        runCurrent()
        assertEquals("hello" to 1, repo.searches.last())
        assertEquals(listOf(project), vm.state.value.items)
    }

    @Test fun failedNextPageRetainsItemsAndRetryFetchesTheSamePage() = runTest(dispatcher) {
        val repo = Repository()
        var fail = true
        repo.projectsBlock = { _, _, page ->
            if (page == 1) Result.success(GitLabPage(listOf(project), 2))
            else if (fail) Result.failure(GitLabReadException(GitLabReadError.PERMISSION))
            else Result.success(GitLabPage(listOf(project.copy(id = 2)), null))
        }
        val vm = GitLabProjectsViewModel(account.key, Auth(account), repo)
        runCurrent()
        vm.onAction(GitLabProjectsAction.LoadMore); runCurrent()
        assertEquals(listOf(project), vm.state.value.items)
        assertEquals(GitLabReadError.PERMISSION, vm.state.value.error)
        fail = false
        vm.onAction(GitLabProjectsAction.Retry); runCurrent()
        assertEquals(listOf(1, 2, 2), repo.searches.map { it.second })
        assertEquals(2, vm.state.value.items.size)
        assertNull(vm.state.value.error)
    }

    @Test fun projectRefreshRevalidatesWholeLoadedWindowWithoutIntermediateCollapse() = runTest(dispatcher) {
        val repo = Repository()
        val second = project.copy(id = 2)
        repo.projectsBlock = { _, _, page -> Result.success(GitLabPage(listOf(if (page == 1) project else second), page + 1)) }
        val vm = GitLabProjectsViewModel(account.key, Auth(account), repo)
        runCurrent(); vm.onAction(GitLabProjectsAction.LoadMore); runCurrent()
        val pending = CompletableDeferred<Result<GitLabPage<GitLabProject>>>()
        repo.projectsBlock = { _, _, page -> if (page == 1) Result.success(GitLabPage(listOf(project.copy(name = "Updated")), 7)) else pending.await() }
        vm.onAction(GitLabProjectsAction.Refresh); runCurrent()
        assertEquals(listOf(project, second), vm.state.value.items)
        assertEquals(3, vm.state.value.nextPage)
        pending.complete(Result.success(GitLabPage(listOf(second.copy(name = "Updated second")), 8))); runCurrent()
        assertEquals(listOf("Updated", "Updated second"), vm.state.value.items.map { it.name })
        assertEquals(listOf(1, 2, 1, 7), repo.searches.map { it.second })
        assertEquals(8, vm.state.value.nextPage)
    }

    @Test fun failedProjectRefreshRetainsOldWindowAndRetryReplaysRefresh() = runTest(dispatcher) {
        val repo = Repository()
        val second = project.copy(id = 2)
        repo.projectsBlock = { _, _, page -> Result.success(GitLabPage(listOf(if (page == 1) project else second), page + 1)) }
        val vm = GitLabProjectsViewModel(account.key, Auth(account), repo)
        runCurrent(); vm.onAction(GitLabProjectsAction.LoadMore); runCurrent()
        var fail = true
        repo.projectsBlock = { _, _, page -> if (page == 1) Result.success(GitLabPage(listOf(project.copy(name = "Updated")), 7))
            else if (fail) Result.failure(GitLabReadException(GitLabReadError.NETWORK))
            else Result.success(GitLabPage(emptyList(), null)) }
        vm.onAction(GitLabProjectsAction.Refresh); runCurrent()
        assertEquals(listOf(project, second), vm.state.value.items)
        assertEquals(3, vm.state.value.nextPage)
        fail = false; vm.onAction(GitLabProjectsAction.Retry); runCurrent()
        assertEquals(listOf(1, 2, 1, 7, 1, 7), repo.searches.map { it.second })
        assertEquals(listOf(project.copy(name = "Updated")), vm.state.value.items)
        assertNull(vm.state.value.nextPage)
        assertNull(vm.state.value.error)
    }

    @Test fun accountSwitchClearsListAndLateNonCooperativeResponseIsIgnored() = runTest(dispatcher) {
        val auth = Auth(account)
        val repo = Repository()
        val deferred = CompletableDeferred<Result<GitLabPage<GitLabProject>>>()
        repo.projectsBlock = { _, _, _ -> withContext(NonCancellable) { deferred.await() } }
        val vm = GitLabProjectsViewModel(account.key, auth, repo)
        runCurrent()
        auth.accounts.value = GitLabAccounts(listOf(account), "another-instance#42")
        runCurrent()
        assertNull(vm.state.value.account)
        assertTrue(vm.state.value.items.isEmpty())
        deferred.complete(Result.success(GitLabPage(listOf(project), null)))
        runCurrent()
        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test fun directoryAndFileReadsAlwaysUseResolvedCommitAndBackRetainsTheDirectory() = runTest(dispatcher) {
        val repo = Repository()
        val dir = GitLabTreeEntry("src", "src", GitLabEntryType.DIRECTORY)
        val file = GitLabTreeEntry("src/code.kt", "code.kt", GitLabEntryType.FILE)
        repo.treeBlock = { _, path, _ -> Result.success(GitLabPage(if (path.isEmpty()) listOf(dir) else listOf(file), null)) }
        val vm = GitLabProjectViewModel(account.key, 1, Auth(account), repo)
        runCurrent()
        assertEquals(sha, vm.state.value.revision!!.sha)
        vm.onAction(GitLabProjectAction.Open(dir)); runCurrent()
        vm.onAction(GitLabProjectAction.Open(file)); runCurrent()
        assertEquals("source", vm.state.value.file!!.text)
        assertEquals("src/code.kt", vm.state.value.filePath)
        assertTrue(repo.readShas.all { it == sha })
        assertEquals(listOf("main"), repo.refs)
        vm.onAction(GitLabProjectAction.Up); runCurrent()
        assertEquals("src", vm.state.value.path)
        assertNull(vm.state.value.filePath)
        assertNull(vm.state.value.file)
        assertEquals(listOf(file), vm.state.value.entries)
        vm.onAction(GitLabProjectAction.Up); runCurrent()
        assertEquals("", vm.state.value.path)
    }

    @Test fun changingRefClearsFileAndResolvesNewCommitBeforeRequestingRoot() = runTest(dispatcher) {
        val repo = Repository()
        repo.treeBlock = { _, _, _ -> Result.success(GitLabPage(listOf(GitLabTreeEntry("a", "a", GitLabEntryType.FILE)), null)) }
        val vm = GitLabProjectViewModel(account.key, 1, Auth(account), repo)
        runCurrent()
        vm.onAction(GitLabProjectAction.Open(vm.state.value.entries.single())); runCurrent()
        val delayed = CompletableDeferred<Result<GitLabRevision>>()
        repo.revisionBlock = { delayed.await() }
        vm.onAction(GitLabProjectAction.RefChanged("feature/new"))
        vm.onAction(GitLabProjectAction.ApplyRef); runCurrent()
        assertNull(vm.state.value.file)
        assertNull(vm.state.value.revision)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals("", vm.state.value.path)
        delayed.complete(Result.success(GitLabRevision("feature/new", "b".repeat(40))))
        runCurrent()
        assertEquals("b".repeat(40), repo.readShas.last())
        assertEquals(listOf("main", "feature/new"), repo.refs)
    }

    @Test fun accountRemovalClearsProjectPermissionsFileAndErrorsAndRejectsLateFile() = runTest(dispatcher) {
        val auth = Auth(account)
        val repo = Repository()
        val file = GitLabTreeEntry("a", "a", GitLabEntryType.FILE)
        repo.treeBlock = { _, _, _ -> Result.success(GitLabPage(listOf(file), null)) }
        val vm = GitLabProjectViewModel(account.key, 1, auth, repo)
        runCurrent()
        val deferred = CompletableDeferred<Result<GitLabFile>>()
        repo.fileBlock = { withContext(NonCancellable) { deferred.await() } }
        vm.onAction(GitLabProjectAction.Open(file)); runCurrent()
        auth.accounts.value = GitLabAccounts(); runCurrent()
        assertEquals(GitLabProjectUiState(), vm.state.value)
        deferred.complete(Result.success(GitLabFile("a", "private old account content", 27))); runCurrent()
        assertEquals(GitLabProjectUiState(), vm.state.value)
    }

    @Test fun emptyProjectNeverRequestsARevisionOrTree() = runTest(dispatcher) {
        val repo = Repository()
        repo.projectResult = Result.success(project.copy(defaultBranch = null))
        val vm = GitLabProjectViewModel(account.key, 1, Auth(account), repo)
        runCurrent()
        assertNotNull(vm.state.value.project)
        assertTrue(repo.refs.isEmpty())
        assertTrue(repo.readShas.isEmpty())
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test fun fileFailureIsRetriableAndNeverCreatesSuccessContent() = runTest(dispatcher) {
        val repo = Repository()
        val file = GitLabTreeEntry("a", "a", GitLabEntryType.FILE)
        repo.treeBlock = { _, _, _ -> Result.success(GitLabPage(listOf(file), null)) }
        repo.fileBlock = { Result.failure(GitLabReadException(GitLabReadError.UNSUPPORTED_FILE)) }
        val vm = GitLabProjectViewModel(account.key, 1, Auth(account), repo)
        runCurrent()
        vm.onAction(GitLabProjectAction.Open(file)); runCurrent()
        assertEquals(GitLabReadError.UNSUPPORTED_FILE, vm.state.value.error)
        assertNull(vm.state.value.file)
        repo.fileBlock = { Result.success(GitLabFile("a", "source", 6)) }
        vm.onAction(GitLabProjectAction.Retry); runCurrent()
        assertEquals("source", vm.state.value.file!!.text)
        assertNull(vm.state.value.error)
        assertEquals(listOf("main"), repo.refs)
    }

    @Test fun wrongAccountNeverRequestsAndRestoredMatchingAccountStartsLoading() = runTest(dispatcher) {
        val auth = Auth(account)
        auth.accounts.value = GitLabAccounts()
        val repo = Repository()
        val vm = GitLabProjectsViewModel(account.key, auth, repo)
        runCurrent()
        assertTrue(repo.searches.isEmpty())
        auth.accounts.value = GitLabAccounts(listOf(account), account.key); runCurrent()
        assertEquals(1, repo.searches.size)
        assertEquals(account, vm.state.value.account)
    }

    private inner class Repository : GitLabProjectsRepository {
        val searches = mutableListOf<Pair<String, Int>>()
        val refs = mutableListOf<String>()
        val readShas = mutableListOf<String>()
        var projectResult = Result.success(project)
        var projectsBlock: suspend (String, String, Int) -> Result<GitLabPage<GitLabProject>> = { _, _, _ -> Result.success(GitLabPage(emptyList(), null)) }
        var revisionBlock: suspend (String) -> Result<GitLabRevision> = { Result.success(GitLabRevision(it, sha)) }
        var treeBlock: suspend (String, String, Int) -> Result<GitLabPage<GitLabTreeEntry>> = { _, _, _ -> Result.success(GitLabPage(emptyList(), null)) }
        var fileBlock: suspend (String) -> Result<GitLabFile> = { Result.success(GitLabFile(it, "source", 6)) }
        override suspend fun projects(accountKey: String, search: String, page: Int): Result<GitLabPage<GitLabProject>> {
            searches += search to page
            return projectsBlock(accountKey, search, page)
        }
        override suspend fun project(accountKey: String, projectId: Long) = projectResult
        override suspend fun revision(accountKey: String, projectId: Long, ref: String): Result<GitLabRevision> { refs += ref; return revisionBlock(ref) }
        override suspend fun tree(accountKey: String, projectId: Long, sha: String, path: String, page: Int): Result<GitLabPage<GitLabTreeEntry>> {
            readShas += sha; return treeBlock(sha, path, page)
        }
        override suspend fun file(accountKey: String, projectId: Long, sha: String, path: String): Result<GitLabFile> { readShas += sha; return fileBlock(path) }
    }

    private class Auth(account: GitLabAccount) : GitLabAuthRepository {
        override val accounts = MutableStateFlow(GitLabAccounts(listOf(account), account.key))
        override suspend fun restore() = Result.success(Unit)
        override suspend fun signInWithToken(instance: String, token: String): Result<GitLabAccount> = error("unused")
        override suspend fun startBrowserSignIn(instance: String, clientId: String, allowWrites: Boolean): Result<String> = error("unused")
        override suspend fun completeBrowserSignIn(callback: String): Result<GitLabAccount> = error("unused")
        override suspend fun cancelBrowserSignIn() = Unit
        override suspend fun activate(key: String) = Result.success(Unit)
        override suspend fun remove(key: String) = Result.success(Unit)
    }
}
