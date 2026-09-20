package takagi.ru.monica.github.feature.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubRepositoryActionsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryFeatures
import takagi.ru.monica.github.domain.GithubRepositoryFeature
import takagi.ru.monica.github.domain.GithubBranchProtection
import takagi.ru.monica.github.domain.GithubCollaborator
import takagi.ru.monica.github.domain.GithubCollaboratorChange
import takagi.ru.monica.github.domain.GithubCollaboratorInvite
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubRepositoryWebhook
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.GithubRepositorySettings
import takagi.ru.monica.github.domain.GithubRepositorySettingsEdit
import takagi.ru.monica.github.domain.GithubRepositoryViewerState
import takagi.ru.monica.github.domain.GithubSession
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialLoadPublishesMetadataAndReadmeIndependently() = runTest(dispatcher) {
        val repository = FakeRepository()

        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()

        assertEquals("openai/codex", viewModel.state.value.details?.repository?.fullName)
        assertEquals("# Codex", viewModel.state.value.readme)
        assertFalse(viewModel.state.value.isLoadingDetails)
        assertFalse(viewModel.state.value.isLoadingReadme)
        assertFalse(viewModel.state.value.detailsError)
        assertFalse(viewModel.state.value.readmeError)
    }

    @Test
    fun retryRecoversAFailedDetailsRequestWithoutDiscardingReadme() = runTest(dispatcher) {
        val repository = FakeRepository(detailsResult = Result.failure(IllegalStateException("offline")))
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.detailsError)
        assertEquals("# Codex", viewModel.state.value.readme)

        repository.detailsResult = Result.success(details())
        viewModel.onAction(RepositoryDetailAction.RetryDetails)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.detailsError)
        assertEquals("openai/codex", viewModel.state.value.details?.repository?.fullName)
        assertEquals("# Codex", viewModel.state.value.readme)
    }

    @Test
    fun refreshKeepsVisibleContentUntilBothRequestsComplete() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.Refresh)
        assertTrue(viewModel.state.value.isRefreshing)
        assertEquals("openai/codex", viewModel.state.value.details?.repository?.fullName)
        assertEquals("# Codex", viewModel.state.value.readme)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoadingDetails)
        assertFalse(viewModel.state.value.isLoadingReadme)
    }

    @Test
    fun signedInViewerCanStarWatchAndForkWithoutMixingOperationStates() = runTest(dispatcher) {
        val actions = FakeActionsRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", FakeRepository(), actions)
        advanceUntilIdle()

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertEquals(GithubRepositoryViewerState(false, false), viewModel.state.value.viewerState)

        viewModel.onAction(RepositoryDetailAction.ToggleStar)
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.ToggleWatch)
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.Fork)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.viewerState?.isStarred == true)
        assertTrue(viewModel.state.value.viewerState?.isWatching == true)
        assertEquals("joyins/codex", viewModel.state.value.forkedRepository?.fullName)
        assertEquals(43, viewModel.state.value.details?.forks)
        assertFalse(viewModel.state.value.isUpdatingStar)
        assertFalse(viewModel.state.value.isUpdatingWatch)
        assertFalse(viewModel.state.value.isForking)

        viewModel.onSessionChanged(GithubSession.SignedOut)
        assertEquals(null, viewModel.state.value.viewerState)
    }

    @Test
    fun refusedViewerActionsNameTheReasonGitHubGave() = runTest(dispatcher) {
        val actions = FakeActionsRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", FakeRepository(), actions)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        actions.starRequest = { Result.failure(GithubApiException(403, rateLimited = true)) }
        viewModel.onAction(RepositoryDetailAction.ToggleStar)
        advanceUntilIdle()
        assertEquals(RepositoryWriteFailure.RateLimited, viewModel.state.value.starFailure)
        assertEquals(false, viewModel.state.value.viewerState?.isStarred)
        assertFalse(viewModel.state.value.isUpdatingStar)

        actions.watchRequest = { Result.failure(GithubApiException(403)) }
        viewModel.onAction(RepositoryDetailAction.ToggleWatch)
        advanceUntilIdle()
        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.watchFailure)

        actions.forkRequest = { Result.failure(IllegalStateException("offline")) }
        viewModel.onAction(RepositoryDetailAction.Fork)
        advanceUntilIdle()
        assertEquals(RepositoryWriteFailure.Network, viewModel.state.value.forkFailure)

        actions.starRequest = { Result.success(true) }
        viewModel.onAction(RepositoryDetailAction.ToggleStar)
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.starFailure)
        assertEquals(true, viewModel.state.value.viewerState?.isStarred)
    }

    @Test
    fun signedInViewerCanUpdateTopicsAndStateReflectsServerResult() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()

        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("Android", "Kotlin")))
        advanceUntilIdle()

        assertEquals(listOf("Android", "Kotlin"), repository.updatedTopics)
        assertEquals(listOf("android", "kotlin"), viewModel.state.value.details?.topics)
        assertFalse(viewModel.state.value.isUpdatingTopics)
        assertEquals(null, viewModel.state.value.topicsFailure)
    }

    @Test
    fun refusedTopicsUpdateKeepsServerTopicsAndNamesTheReason() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        advanceUntilIdle()
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        repository.topicsRequest = { Result.failure(GithubApiException(403, rateLimited = true)) }
        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("rust")))
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.RateLimited, viewModel.state.value.topicsFailure)
        assertEquals(listOf("ai", "developer-tools"), viewModel.state.value.details?.topics)
        assertFalse(viewModel.state.value.isUpdatingTopics)

        repository.topicsRequest = { Result.success(listOf("rust")) }
        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("rust")))
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.topicsFailure)
        assertEquals(listOf("rust"), viewModel.state.value.details?.topics)
    }

    @Test
    fun adminSettingsWritesSendOneFieldAndReadBackTheServerSnapshot() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canManageSettings)

        viewModel.onAction(RepositoryDetailAction.SetVisibility(isPrivate = true))
        assertTrue(viewModel.state.value.isUpdatingSettings)
        advanceUntilIdle()

        assertEquals(GithubRepositorySettingsEdit(isPrivate = true), repository.updatedSettings)
        assertEquals(true, viewModel.state.value.details?.repository?.isPrivate)
        assertEquals(false, viewModel.state.value.details?.isArchived)
        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.settingsFailure)

        viewModel.onAction(RepositoryDetailAction.SetArchived(isArchived = true))
        advanceUntilIdle()

        assertEquals(GithubRepositorySettingsEdit(isArchived = true), repository.updatedSettings)
        assertEquals(true, viewModel.state.value.details?.isArchived)
        // An unrelated answer must not reset the visibility the repository already confirmed.
        assertEquals(true, viewModel.state.value.details?.repository?.isPrivate)
    }

    @Test
    fun refusedSettingsNameTheReasonAndKeepTheConfirmedValue() = runTest(dispatcher) {
        val cases = listOf(
            GithubApiException(403) to RepositoryWriteFailure.Forbidden,
            GithubApiException(403, rateLimited = true) to RepositoryWriteFailure.RateLimited,
            GithubApiException(404) to RepositoryWriteFailure.NotFound,
            GithubApiException(422) to RepositoryWriteFailure.InvalidInput,
            IllegalStateException("offline") to RepositoryWriteFailure.Network
        )
        cases.forEach { (error, expected) ->
            val repository = FakeRepository()
            val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
            viewModel.onSessionChanged(GithubSession.SignedIn(account()))
            advanceUntilIdle()
            repository.settingsRequest = { Result.failure(error) }

            viewModel.onAction(RepositoryDetailAction.SetArchived(isArchived = true))
            advanceUntilIdle()

            assertEquals(expected, viewModel.state.value.settingsFailure)
            assertEquals(false, viewModel.state.value.details?.isArchived)
            assertFalse(viewModel.state.value.isUpdatingSettings)
        }
    }

    @Test
    fun aSettingThatAlreadyMatchesTheRepositoryIsNeverSent() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.SetVisibility(isPrivate = false))
        advanceUntilIdle()

        assertEquals(null, repository.updatedSettings)
        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.settingsFailure)
    }

    @Test
    fun featureTogglesSendOneFieldAndNeverClobberTheOtherSettings() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.SetVisibility(isPrivate = true))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.SetFeature(GithubRepositoryFeature.Wiki, false))
        advanceUntilIdle()

        assertEquals(GithubRepositorySettingsEdit(hasWiki = false), repository.updatedSettings)
        assertEquals(false, viewModel.state.value.details?.features?.hasWiki)
        // The server snapshot must not undo what an earlier write already confirmed.
        assertEquals(true, viewModel.state.value.details?.repository?.isPrivate)
        assertEquals(true, viewModel.state.value.details?.features?.hasIssues)

        viewModel.onAction(RepositoryDetailAction.SetFeature(GithubRepositoryFeature.Projects, false))
        advanceUntilIdle()

        assertEquals(GithubRepositorySettingsEdit(hasProjects = false), repository.updatedSettings)
        assertEquals(false, viewModel.state.value.details?.features?.hasProjects)
        assertEquals(false, viewModel.state.value.details?.features?.hasWiki)
        assertEquals(true, viewModel.state.value.details?.repository?.isPrivate)
    }

    @Test
    fun refusedFeatureToggleKeepsTheConfirmedSwitchAndNamesTheReason() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        repository.settingsRequest = { Result.failure(GithubApiException(403)) }

        viewModel.onAction(RepositoryDetailAction.SetFeature(GithubRepositoryFeature.Issues, false))
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.Forbidden, viewModel.state.value.settingsFailure)
        assertEquals(true, viewModel.state.value.details?.features?.hasIssues)
        assertFalse(viewModel.state.value.isUpdatingSettings)
    }

    @Test
    fun aFeatureThatAlreadyMatchesTheRepositoryIsNeverSent() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.SetFeature(GithubRepositoryFeature.Issues, true))
        advanceUntilIdle()

        assertEquals(null, repository.updatedSettings)
        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.settingsFailure)
    }

    @Test
    fun descriptionEditsSendOnlyTheDescriptionAndTrimTheDraft() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.SetFeature(GithubRepositoryFeature.Wiki, false))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.UpdateDescription("  A coding agent for phones  "))
        assertTrue(viewModel.state.value.isUpdatingSettings)
        advanceUntilIdle()

        assertEquals(GithubRepositorySettingsEdit(description = "A coding agent for phones"), repository.updatedSettings)
        assertEquals("A coding agent for phones", viewModel.state.value.details?.repository?.description)
        // The reply is a full repository object, but a description write must still not move a toggle.
        assertEquals(false, viewModel.state.value.details?.features?.hasWiki)
        assertEquals(null, viewModel.state.value.settingsFailure)
    }

    @Test
    fun refusedDescriptionKeepsTheConfirmedTextAndNamesTheReason() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        repository.settingsRequest = { Result.failure(GithubApiException(422)) }

        viewModel.onAction(RepositoryDetailAction.UpdateDescription("Renamed in a hurry"))
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.InvalidInput, viewModel.state.value.settingsFailure)
        assertEquals("A coding agent", viewModel.state.value.details?.repository?.description)
        assertFalse(viewModel.state.value.isUpdatingSettings)
    }

    @Test
    fun clearingTheDescriptionIsSentAsAnEmptyStringAndReadsBackAsNone() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.UpdateDescription(""))
        advanceUntilIdle()

        // An empty string is a deliberate clear; leaving the field out would mean "do not touch".
        assertEquals(GithubRepositorySettingsEdit(description = ""), repository.updatedSettings)
        assertNull(viewModel.state.value.details?.repository?.description)
    }

    @Test
    fun aDescriptionThatAlreadyMatchesTheRepositoryIsNeverSent() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.UpdateDescription("  A coding agent  "))
        advanceUntilIdle()

        assertEquals(null, repository.updatedSettings)
        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.settingsFailure)
    }

    @Test
    fun defaultBranchEditsSendOnlyTheBranchAndTrimTheDraft() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        viewModel.onAction(RepositoryDetailAction.SetFeature(GithubRepositoryFeature.Wiki, false))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.SetDefaultBranch("  release/1.2  "))
        assertTrue(viewModel.state.value.isUpdatingSettings)
        advanceUntilIdle()

        assertEquals(GithubRepositorySettingsEdit(defaultBranch = "release/1.2"), repository.updatedSettings)
        assertEquals("release/1.2", viewModel.state.value.details?.defaultBranch)
        // The reply is a full repository object; a branch write must not move a toggle.
        assertEquals(false, viewModel.state.value.details?.features?.hasWiki)
        assertEquals(null, viewModel.state.value.settingsFailure)
    }

    @Test
    fun refusedDefaultBranchKeepsTheConfirmedBranchAndNamesTheReason() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        repository.settingsRequest = { Result.failure(GithubApiException(422)) }

        viewModel.onAction(RepositoryDetailAction.SetDefaultBranch("does-not-exist"))
        advanceUntilIdle()

        assertEquals(RepositoryWriteFailure.InvalidInput, viewModel.state.value.settingsFailure)
        assertEquals("main", viewModel.state.value.details?.defaultBranch)
        assertFalse(viewModel.state.value.isUpdatingSettings)
    }

    @Test
    fun aDefaultBranchThatAlreadyMatchesTheRepositoryIsNeverSent() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        viewModel.onAction(RepositoryDetailAction.SetDefaultBranch("  main  "))
        advanceUntilIdle()

        assertEquals(null, repository.updatedSettings)
        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.settingsFailure)
    }

    @Test
    fun switchingAccountsIgnoresSuccessfulMutationsFromThePreviousAccount() =
        verifyLateMutationsIgnored(failure = false)

    @Test
    fun switchingAccountsIgnoresFailedMutationsFromThePreviousAccount() =
        verifyLateMutationsIgnored(failure = true)

    @Test
    fun switchingBackToTheOriginalAccountStillIgnoresItsEarlierMutations() =
        verifyLateMutationsIgnored(failure = false, returnToOriginalAccount = true)

    @Test
    fun refreshedProfileForTheSameAccountDoesNotInterruptItsMutation() = runTest(dispatcher) {
        val actions = FakeActionsRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", FakeRepository(), actions)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        val pendingStar = PendingResult<Boolean>()
        actions.starRequest = { pendingStar.await() }
        viewModel.onAction(RepositoryDetailAction.ToggleStar)
        runCurrent()
        viewModel.onSessionChanged(GithubSession.SignedIn(account().copy(name = "Updated profile")))

        assertTrue(viewModel.state.value.isUpdatingStar)
        pendingStar.complete(Result.success(true))
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.viewerState?.isStarred)
        assertFalse(viewModel.state.value.isUpdatingStar)
    }

    @Test
    fun oldMetadataAndViewerRequestsCannotRestoreThePreviousAccountsPermissions() = runTest(dispatcher) {
        val repository = FakeRepository()
        val actions = FakeActionsRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, actions)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        val oldDetails = PendingResult<GithubRepositoryDetails>()
        val oldReadme = PendingResult<String?>()
        val oldProtection = PendingResult<GithubBranchProtection?>()
        val oldViewer = PendingResult<GithubRepositoryViewerState>()
        repository.detailsRequest = { oldDetails.await() }
        repository.readmeRequest = { oldReadme.await() }
        repository.protectionRequest = { oldProtection.await() }
        actions.viewerRequest = { oldViewer.await() }
        viewModel.onAction(RepositoryDetailAction.Refresh)
        viewModel.onAction(RepositoryDetailAction.RetryBranchProtection)
        viewModel.onAction(RepositoryDetailAction.RetryViewerState)
        runCurrent()

        repository.detailsRequest = null
        repository.readmeRequest = null
        repository.protectionRequest = null
        actions.viewerRequest = null
        repository.detailsResult = Result.success(details(GithubCollaboratorRole.READ))
        repository.readmeResult = Result.success("# Current account content")
        actions.viewerResult = Result.success(GithubRepositoryViewerState(false, true))
        viewModel.onSessionChanged(GithubSession.SignedIn(account(2, "reader")))
        advanceUntilIdle()
        val currentAccountState = viewModel.state.value
        assertEquals(GithubCollaboratorRole.READ, currentAccountState.viewerRole)
        assertFalse(currentAccountState.canAdminister)

        oldDetails.complete(Result.success(details(GithubCollaboratorRole.ADMIN).copy(forks = 999)))
        oldReadme.complete(Result.success("# Old account content"))
        oldProtection.complete(Result.success(GithubBranchProtection("old", 99, 9, true)))
        oldViewer.complete(Result.success(GithubRepositoryViewerState(true, true)))
        advanceUntilIdle()

        assertEquals(currentAccountState, viewModel.state.value)
    }

    @Test
    fun signingOutClearsPrivateContentEvenWhenThePublicReloadFails() = runTest(dispatcher) {
        val privateDetails = details().let { it.copy(repository = it.repository.copy(isPrivate = true)) }
        val repository = FakeRepository(detailsResult = Result.success(privateDetails))
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, FakeActionsRepository())
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.details?.repository?.isPrivate == true)
        assertEquals("# Codex", viewModel.state.value.readme)

        repository.detailsResult = Result.failure(IllegalStateException("not visible"))
        repository.readmeResult = Result.failure(IllegalStateException("not visible"))
        viewModel.onSessionChanged(GithubSession.SignedOut)

        assertEquals(null, viewModel.state.value.details)
        assertEquals(null, viewModel.state.value.readme)
        assertEquals(null, viewModel.state.value.branchProtection)
        assertEquals(null, viewModel.state.value.viewerState)
        assertFalse(viewModel.state.value.canInteract)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.details)
        assertEquals(null, viewModel.state.value.readme)
        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        assertTrue(viewModel.state.value.detailsError)
    }

    private fun verifyLateMutationsIgnored(
        failure: Boolean,
        returnToOriginalAccount: Boolean = false
    ) = runTest(dispatcher) {
        val repository = FakeRepository()
        val actions = FakeActionsRepository()
        val viewModel = RepositoryDetailViewModel("openai", "codex", repository, actions)
        viewModel.onSessionChanged(GithubSession.SignedIn(account()))
        advanceUntilIdle()

        val oldStar = PendingResult<Boolean>()
        val oldWatch = PendingResult<Boolean>()
        val oldFork = PendingResult<GithubRepository>()
        val oldTopics = PendingResult<List<String>>()
        val oldSettings = PendingResult<GithubRepositorySettings>()
        actions.starRequest = { oldStar.await() }
        actions.watchRequest = { oldWatch.await() }
        actions.forkRequest = { oldFork.await() }
        repository.topicsRequest = { oldTopics.await() }
        repository.settingsRequest = { oldSettings.await() }
        viewModel.onAction(RepositoryDetailAction.ToggleStar)
        viewModel.onAction(RepositoryDetailAction.ToggleWatch)
        viewModel.onAction(RepositoryDetailAction.Fork)
        viewModel.onAction(RepositoryDetailAction.UpdateTopics(listOf("old-account")))
        viewModel.onAction(RepositoryDetailAction.SetArchived(isArchived = true))
        runCurrent()
        assertTrue(viewModel.state.value.isUpdatingStar)
        assertTrue(viewModel.state.value.isUpdatingWatch)
        assertTrue(viewModel.state.value.isForking)
        assertTrue(viewModel.state.value.isUpdatingTopics)
        assertTrue(viewModel.state.value.isUpdatingSettings)

        repository.detailsResult = Result.success(details(GithubCollaboratorRole.READ))
        viewModel.onSessionChanged(GithubSession.SignedIn(account(2, "reader")))
        assertFalse(viewModel.state.value.isUpdatingStar)
        assertFalse(viewModel.state.value.isUpdatingWatch)
        assertFalse(viewModel.state.value.isForking)
        assertFalse(viewModel.state.value.isUpdatingTopics)
        assertFalse(viewModel.state.value.isUpdatingSettings)
        assertEquals(null, viewModel.state.value.viewerState)
        assertFalse(viewModel.state.value.canEditTopics)
        assertFalse(viewModel.state.value.canManageSettings)
        advanceUntilIdle()

        if (returnToOriginalAccount) {
            repository.detailsResult = Result.success(details())
            viewModel.onSessionChanged(GithubSession.SignedIn(account()))
            advanceUntilIdle()
        }
        val currentAccountState = viewModel.state.value
        assertEquals(GithubRepositoryViewerState(false, false), currentAccountState.viewerState)
        assertEquals(null, currentAccountState.forkedRepository)

        val oldError = IllegalStateException("previous account request failed")
        oldStar.complete(if (failure) Result.failure(oldError) else Result.success(true))
        oldWatch.complete(if (failure) Result.failure(oldError) else Result.success(true))
        oldFork.complete(
            if (failure) Result.failure(oldError)
            else Result.success(details().repository.copy(fullName = "joyins/codex"))
        )
        oldTopics.complete(if (failure) Result.failure(oldError) else Result.success(listOf("old-account")))
        oldSettings.complete(
            if (failure) Result.failure(oldError)
            else Result.success(GithubRepositorySettings(isPrivate = false, isArchived = true))
        )
        advanceUntilIdle()

        assertEquals(currentAccountState, viewModel.state.value)
    }

    /** A server callback may finish even after its calling coroutine is cancelled. */
    private class PendingResult<T> {
        private var continuation: Continuation<Result<T>>? = null

        suspend fun await(): Result<T> = suspendCoroutine { pending ->
            check(continuation == null)
            continuation = pending
        }

        fun complete(result: Result<T>) {
            checkNotNull(continuation).resume(result)
            continuation = null
        }
    }

    private class FakeRepository(
        var detailsResult: Result<GithubRepositoryDetails> = Result.success(details()),
        var readmeResult: Result<String?> = Result.success("# Codex")
    ) : GithubRepositoryDetailsRepository {
        var updatedTopics: List<String>? = null
        var updatedSettings: GithubRepositorySettingsEdit? = null
        var serverSettings: GithubRepositorySettings? = null
        var settingsRequest: (suspend () -> Result<GithubRepositorySettings>)? = null
        var detailsRequest: (suspend () -> Result<GithubRepositoryDetails>)? = null
        var readmeRequest: (suspend () -> Result<String?>)? = null
        var protectionRequest: (suspend () -> Result<GithubBranchProtection?>)? = null
        var topicsRequest: (suspend () -> Result<List<String>>)? = null
        override suspend fun details(owner: String, name: String) = detailsRequest?.invoke() ?: detailsResult
        override suspend fun readme(owner: String, name: String, ref: String?) = readmeRequest?.invoke() ?: readmeResult
        override suspend fun branchProtection(owner: String, name: String, branch: String) =
            protectionRequest?.invoke() ?: Result.success(GithubBranchProtection(branch, 2, 1, enforceAdmins = true))
        override suspend fun updateTopics(owner: String, name: String, topics: List<String>): Result<List<String>> {
            updatedTopics = topics
            return topicsRequest?.invoke() ?: Result.success(topics.map(String::lowercase))
        }
        override suspend fun updateSettings(
            owner: String,
            name: String,
            edit: GithubRepositorySettingsEdit
        ): Result<GithubRepositorySettings> {
            updatedSettings = edit
            val loaded = detailsResult.getOrNull()
            val previous = serverSettings ?: GithubRepositorySettings(
                isPrivate = loaded?.repository?.isPrivate ?: false,
                isArchived = loaded?.isArchived ?: false,
                features = loaded?.features ?: GithubRepositoryFeatures(),
                description = loaded?.repository?.description,
                defaultBranch = loaded?.defaultBranch ?: "main"
            )
            val result = settingsRequest?.invoke() ?: Result.success(
                previous.copy(
                    isPrivate = edit.isPrivate ?: previous.isPrivate,
                    isArchived = edit.isArchived ?: previous.isArchived,
                    description = (edit.description ?: previous.description)?.takeIf(String::isNotBlank),
                    defaultBranch = edit.defaultBranch ?: previous.defaultBranch,
                    features = previous.features.copy(
                        hasIssues = edit.hasIssues ?: previous.features.hasIssues,
                        hasWiki = edit.hasWiki ?: previous.features.hasWiki,
                        hasProjects = edit.hasProjects ?: previous.features.hasProjects
                    )
                )
            )
            if (result.isSuccess) serverSettings = result.getOrNull()
            return result
        }
        override suspend fun collaborators(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubCollaborator>(emptyList(), null))
        override suspend fun setCollaborator(
            owner: String,
            name: String,
            invite: GithubCollaboratorInvite
        ): Result<GithubCollaboratorChange> = Result.failure(UnsupportedOperationException())
        override suspend fun removeCollaborator(owner: String, name: String, login: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun webhooks(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage<GithubRepositoryWebhook>(emptyList(), null))
    }

    private class FakeActionsRepository : GithubRepositoryActionsRepository {
        var viewerResult = Result.success(GithubRepositoryViewerState(isStarred = false, isWatching = false))
        var viewerRequest: (suspend () -> Result<GithubRepositoryViewerState>)? = null
        var starRequest: (suspend () -> Result<Boolean>)? = null
        var watchRequest: (suspend () -> Result<Boolean>)? = null
        var forkRequest: (suspend () -> Result<GithubRepository>)? = null

        override suspend fun viewerState(owner: String, name: String) =
            viewerRequest?.invoke() ?: viewerResult

        override suspend fun setStarred(owner: String, name: String, starred: Boolean) =
            starRequest?.invoke() ?: Result.success(starred)

        override suspend fun setWatching(owner: String, name: String, watching: Boolean) =
            watchRequest?.invoke() ?: Result.success(watching)

        override suspend fun fork(owner: String, name: String) = forkRequest?.invoke() ?: Result.success(
            GithubRepository(
                id = 12,
                name = name,
                fullName = "joyins/$name",
                description = "Fork",
                language = "Rust",
                stars = 0,
                updatedAt = "2026-08-16T00:00:00Z",
                isPrivate = false,
                htmlUrl = "https://github.com/joyins/$name"
            )
        )
    }

    private companion object {
        fun details(
            viewerRole: GithubCollaboratorRole = GithubCollaboratorRole.ADMIN
        ) = GithubRepositoryDetails(
            repository = GithubRepository(
                id = 11,
                name = "codex",
                fullName = "openai/codex",
                description = "A coding agent",
                language = "Rust",
                stars = 1000,
                updatedAt = "2026-08-16T00:00:00Z",
                isPrivate = false,
                htmlUrl = "https://github.com/openai/codex"
            ),
            ownerLogin = "openai",
            ownerAvatarUrl = "https://avatars.example/openai",
            defaultBranch = "main",
            forks = 42,
            watchers = 7,
            openIssues = 13,
            license = "MIT",
            topics = listOf("ai", "developer-tools"),
            isArchived = false,
            isFork = false,
            viewerRole = viewerRole
        )

        fun account(id: Long = 1, login: String = "joyins") = GithubAccount(
            id = id,
            login = login,
            name = "Joyins",
            bio = null,
            avatarUrl = "https://avatars.example/joyins",
            htmlUrl = "https://github.com/joyins",
            publicRepositories = 1,
            followers = 1,
            following = 1
        )
    }
}
