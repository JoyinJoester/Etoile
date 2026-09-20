package takagi.ru.monica.github.feature.issues

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.SavedStateHandle
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
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueComment
import takagi.ru.monica.github.domain.GithubIssueCommentDraft
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueTemplateCatalog
import takagi.ru.monica.github.domain.GithubIssueTemplatesRepository
import takagi.ru.monica.github.domain.GithubIssueTemplate
import takagi.ru.monica.github.domain.GithubBuiltInIssueTemplates
import takagi.ru.monica.github.domain.GithubIssueTemplateLanguage
import takagi.ru.monica.github.domain.GithubIssueFormAnswer
import takagi.ru.monica.github.domain.GithubIssueFormField
import takagi.ru.monica.github.domain.GithubIssueFormFieldType
import takagi.ru.monica.github.domain.GithubIssueFormOption
import takagi.ru.monica.github.domain.GithubIssueLabel
import takagi.ru.monica.github.domain.GithubIssueListQuery
import takagi.ru.monica.github.domain.GithubIssueMilestone
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubAccount
import takagi.ru.monica.github.domain.GithubSession
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubPage
import takagi.ru.monica.github.domain.GithubReactionContent
import takagi.ru.monica.github.domain.GithubReactionToggle
import takagi.ru.monica.github.domain.GithubSortDirection
import takagi.ru.monica.github.domain.GithubCollaboratorRole
import takagi.ru.monica.github.domain.GithubRepositoryDetails
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository
import takagi.ru.monica.github.domain.TestGithubRepositoryDetailsRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class IssuesViewModelTest {
    private val emptyTemplates = GithubIssueTemplatesRepository { _, _ -> Result.success(GithubIssueTemplateCatalog()) }
    @Test
    fun issueDraftRestoresAndClearsOnlyAfterSuccessfulSubmission() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = CreateIssueViewModel("openai", "codex", FakeIssuesRepository(), emptyTemplates, saved)
        advanceUntilIdle()
        viewModel.onAction(CreateIssueAction.SelectTemplate(null))
        viewModel.onAction(CreateIssueAction.TitleChanged("Draft title"))
        viewModel.onAction(CreateIssueAction.BodyChanged("Draft body"))
        val restored = CreateIssueViewModel("openai", "codex", FakeIssuesRepository(), emptyTemplates, saved)
        advanceUntilIdle()
        assertEquals("Draft title", restored.state.value.title)
        assertEquals("Draft body", restored.state.value.body)
        restored.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()
        assertFalse(saved.contains("issue_title"))
        assertFalse(saved.contains("issue_body"))
        assertTrue(restored.state.value.createdIssue != null)
    }

    @Test
    fun pendingIssueSubmissionKeepsItsDraftAndFailurePreservesIt() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<GithubIssue>>()
        val source = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun createIssue(owner: String, name: String, draft: GithubIssueDraft) = pending.await()
        }
        val saved = SavedStateHandle()
        val viewModel = CreateIssueViewModel("openai", "codex", source, emptyTemplates, saved)
        advanceUntilIdle()
        viewModel.onAction(CreateIssueAction.SelectTemplate(null))
        viewModel.onAction(CreateIssueAction.TitleChanged("Original"))
        viewModel.onAction(CreateIssueAction.BodyChanged("Original body"))
        viewModel.onAction(CreateIssueAction.Submit)
        viewModel.onAction(CreateIssueAction.TitleChanged("Unsaved edit"))
        viewModel.onAction(CreateIssueAction.BodyChanged("Unsaved body"))
        assertEquals("Original", viewModel.state.value.title)
        assertEquals("Original body", viewModel.state.value.body)
        pending.complete(Result.failure(IllegalStateException("Offline")))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.submitError)
        assertEquals("Original", saved.get<String>("issue_title"))
        assertEquals("Original body", saved.get<String>("issue_body"))
        viewModel.onAction(CreateIssueAction.TitleChanged("Updated"))
        assertEquals("Updated", viewModel.state.value.title)
    }
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun selectingMarkdownTemplatePrefillsAndPublishesMetadata() = runTest(dispatcher) {
        val source = FakeIssuesRepository()
        val template = GithubIssueTemplate("bug.md", "Bug", title = "[Bug] ", body = "## Steps", labels = listOf("bug"), assignees = listOf("alice"))
        val model = CreateIssueViewModel("openai", "codex", source, templates(template))
        advanceUntilIdle()
        assertTrue(model.state.value.isChoosingTemplate)
        model.onAction(CreateIssueAction.SelectTemplate(null))
        assertFalse(model.state.value.hasChosenTemplate)
        model.onAction(CreateIssueAction.Submit)
        assertNull(source.createdDraft)
        model.onAction(CreateIssueAction.SelectTemplate("bug.md"))
        assertEquals("[Bug] ", model.state.value.title)
        assertEquals("## Steps", model.state.value.body)
        assertTrue(model.state.value.canPublish)
        model.onAction(CreateIssueAction.TitleChanged("[Bug] Crash"))
        model.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()
        assertEquals(listOf("bug"), source.createdDraft?.labels)
        assertEquals(listOf("alice"), source.createdDraft?.assignees)
        assertEquals("## Steps", source.createdDraft?.body)
    }

    @Test
    fun templateChangesKeepEditedDraftUntilReplacementIsConfirmed() = runTest(dispatcher) {
        val template = GithubIssueTemplate("bug.md", "Bug", title = "[Bug] ", body = "## Steps")
        val model = CreateIssueViewModel("openai", "codex", FakeIssuesRepository(), templates(template, blankEnabled = true))
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(template.id))
        model.onAction(CreateIssueAction.BodyChanged("My carefully written report"))
        model.onAction(CreateIssueAction.ShowTemplateChooser)
        model.onAction(CreateIssueAction.SelectTemplate(null))
        assertTrue(model.state.value.showTemplateChangeConfirmation)
        assertEquals("My carefully written report", model.state.value.body)
        model.onAction(CreateIssueAction.CancelTemplateChange)
        model.onAction(CreateIssueAction.SelectTemplate(template.id))
        assertFalse(model.state.value.isChoosingTemplate)
        assertEquals("My carefully written report", model.state.value.body)
        model.onAction(CreateIssueAction.ShowTemplateChooser)
        model.onAction(CreateIssueAction.SelectTemplate(null))
        model.onAction(CreateIssueAction.ConfirmTemplateChange)
        assertNull(model.state.value.selectedTemplate)
        assertEquals("", model.state.value.body)
        assertEquals("", model.state.value.title)
        assertTrue(model.state.value.hasChosenTemplate)
    }

    @Test
    fun formCannotPublishUntilRequiredAnswersAndCheckboxesAreCompleteEvenFromPreview() = runTest(dispatcher) {
        val source = FakeIssuesRepository()
        val form = formTemplate()
        val model = CreateIssueViewModel("openai", "codex", source, templates(form))
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(form.id))
        model.onAction(CreateIssueAction.TogglePreview)
        model.onAction(CreateIssueAction.Submit)
        assertEquals(setOf("steps", "platform", "checks"), model.state.value.invalidFieldIds)
        assertFalse(model.state.value.isPreviewing)
        assertNull(source.createdDraft)
        model.onAction(CreateIssueAction.FormTextChanged("steps", "Open the app"))
        model.onAction(CreateIssueAction.ToggleFormOption("platform", 0))
        model.onAction(CreateIssueAction.ToggleFormOption("checks", 1))
        model.onAction(CreateIssueAction.Submit)
        assertEquals(setOf("checks"), model.state.value.invalidFieldIds)
        model.onAction(CreateIssueAction.ToggleFormOption("checks", 0))
        model.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()
        assertEquals("""
            ### Steps

            Open the app

            ### Platform

            Android

            ### Checklist

            - [x] Searched existing issues
            - [x] Can help test
        """.trimIndent(), source.createdDraft?.body)
        assertEquals(listOf("bug"), source.createdDraft?.labels)
    }

    @Test
    fun formDraftRestoresTemplateAndAnswersDuringReloadAndClearsOnlyOnSuccess() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val form = formTemplate()
        val source = FakeIssuesRepository()
        val model = CreateIssueViewModel("openai", "codex", source, templates(form), saved)
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(form.id))
        model.onAction(CreateIssueAction.TitleChanged("[Bug] Draft"))
        model.onAction(CreateIssueAction.FormTextChanged("steps", "Saved reproduction"))
        model.onAction(CreateIssueAction.ToggleFormOption("platform", 0))
        model.onAction(CreateIssueAction.ToggleFormOption("checks", 0))
        val restored = CreateIssueViewModel("openai", "codex", source, templates(form), saved)
        assertEquals("Saved reproduction", restored.state.value.answers["steps"]?.text)
        assertEquals(form, restored.state.value.selectedTemplate)
        assertFalse(restored.state.value.canPublish)
        advanceUntilIdle()
        restored.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()
        assertTrue(saved.keys().none { it.startsWith("issue_") })
        assertEquals("[Bug] Draft", source.createdDraft?.title)
        assertFalse(restored.state.value.hasChosenTemplate)
    }

    @Test
    fun failedTemplateLoadCanRetryWithoutReplacingAnExistingDraft() = runTest(dispatcher) {
        var attempts = 0
        val saved = SavedStateHandle(mapOf("issue_title" to "Draft", "issue_body" to "Saved body"))
        val source = FakeIssuesRepository()
        val templates = GithubIssueTemplatesRepository { _, _ ->
            if (attempts++ == 0) Result.failure(IllegalStateException("Offline")) else Result.success(GithubIssueTemplateCatalog())
        }
        val model = CreateIssueViewModel("openai", "codex", source, templates, saved)
        advanceUntilIdle()
        model.onAction(CreateIssueAction.Submit)
        assertTrue(model.state.value.templateLoadError)
        assertNull(source.createdDraft)
        model.onAction(CreateIssueAction.LoadTemplates)
        advanceUntilIdle()
        assertEquals("Draft", model.state.value.title)
        assertEquals("Saved body", model.state.value.body)
        assertTrue(model.state.value.canPublish)
    }

    @Test
    fun loadingTemplatesPreventsPublishingBeforeBlankPolicyIsKnown() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<GithubIssueTemplateCatalog>>()
        val source = FakeIssuesRepository()
        val saved = SavedStateHandle(mapOf("issue_title" to "Existing draft"))
        val model = CreateIssueViewModel("openai", "codex", source, GithubIssueTemplatesRepository { _, _ -> pending.await() }, saved)
        model.onAction(CreateIssueAction.Submit)
        assertNull(source.createdDraft)
        pending.complete(Result.success(GithubIssueTemplateCatalog(blankIssuesEnabled = false)))
        advanceUntilIdle()
        assertFalse(model.state.value.canPublish)
        assertEquals("Existing draft", model.state.value.title)
    }

    @Test
    fun reloadDoesNotOverwriteAnswersAndRemovedTemplateCannotBeSubmitted() = runTest(dispatcher) {
        val form = formTemplate()
        var catalog = GithubIssueTemplateCatalog(listOf(form), blankIssuesEnabled = false)
        val source = FakeIssuesRepository()
        val model = CreateIssueViewModel("openai", "codex", source, GithubIssueTemplatesRepository { _, _ -> Result.success(catalog) })
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(form.id))
        model.onAction(CreateIssueAction.FormTextChanged("steps", "My report"))
        catalog = catalog.copy(templates = emptyList())
        model.onAction(CreateIssueAction.LoadTemplates)
        advanceUntilIdle()
        assertFalse(model.state.value.selectionAllowed)
        assertEquals("My report", model.state.value.answers["steps"]?.text)
        model.onAction(CreateIssueAction.Submit)
        assertNull(source.createdDraft)
    }

    @Test
    fun invalidFormActionsCannotInjectOptionsOrBypassTheBodyLimit() = runTest(dispatcher) {
        val form = formTemplate()
        val model = CreateIssueViewModel("openai", "codex", FakeIssuesRepository(), templates(form))
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(form.id))
        model.onAction(CreateIssueAction.ToggleFormOption("platform", 99))
        model.onAction(CreateIssueAction.FormTextChanged("unknown", "ignored"))
        model.onAction(CreateIssueAction.FormTextChanged("platform", "injected"))
        model.onAction(CreateIssueAction.BodyChanged("bypass required fields"))
        assertEquals(form.initialAnswers(), model.state.value.answers)
        assertEquals("", model.state.value.body)
        model.onAction(CreateIssueAction.FormTextChanged("steps", "x".repeat(GithubIssueDraft.MAX_BODY_LENGTH)))
        assertTrue(model.state.value.bodyLengthError)
        assertEquals("", model.state.value.answers["steps"]?.text)
        model.onAction(CreateIssueAction.FormTextChanged("steps", "Short report"))
        assertFalse(model.state.value.bodyLengthError)
    }

    @Test
    fun repeatedSubmissionAndTemplateChangesAreIgnoredWhilePublishing() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<GithubIssue>>()
        var calls = 0
        val source = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun createIssue(owner: String, name: String, draft: GithubIssueDraft): Result<GithubIssue> {
                calls++
                return pending.await()
            }
        }
        val model = CreateIssueViewModel("openai", "codex", source, emptyTemplates)
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(null))
        model.onAction(CreateIssueAction.TitleChanged("A report"))
        model.onAction(CreateIssueAction.BodyChanged("Details"))
        model.onAction(CreateIssueAction.Submit)
        model.onAction(CreateIssueAction.Submit)
        model.onAction(CreateIssueAction.ShowTemplateChooser)
        model.onAction(CreateIssueAction.TitleChanged("Changed"))
        runCurrent()
        assertEquals(1, calls)
        assertEquals("A report", model.state.value.title)
        assertFalse(model.state.value.isChoosingTemplate)
        pending.complete(Result.failure(IllegalStateException("Offline")))
        advanceUntilIdle()
        assertTrue(model.state.value.submitError)
        assertEquals("Details", model.state.value.body)
    }

    private fun templates(vararg items: GithubIssueTemplate, blankEnabled: Boolean = false) =
        GithubIssueTemplatesRepository { _, _ -> Result.success(GithubIssueTemplateCatalog(items.toList(), blankEnabled)) }

    private fun formTemplate() = GithubIssueTemplate(
        id = "bug.yml", name = "Bug", title = "[Bug] ", isForm = true, labels = listOf("bug"),
        fields = listOf(
            GithubIssueFormField("steps", GithubIssueFormFieldType.TEXTAREA, "Steps", required = true),
            GithubIssueFormField("platform", GithubIssueFormFieldType.DROPDOWN, "Platform", required = true,
                options = listOf(GithubIssueFormOption("Android"), GithubIssueFormOption("Other"))),
            GithubIssueFormField("checks", GithubIssueFormFieldType.CHECKBOXES, "Checklist",
                options = listOf(GithubIssueFormOption("Searched existing issues", required = true), GithubIssueFormOption("Can help test")))
        )
    )

    @Test
    fun repositoryWithoutTemplatesOffersThreeBuiltInFormsInTheChosenLanguage() = runTest(dispatcher) {
        val model = CreateIssueViewModel("openai", "codex", FakeIssuesRepository(), emptyTemplates,
            initialTemplateLanguage = GithubIssueTemplateLanguage.CHINESE)
        advanceUntilIdle()
        assertTrue(model.state.value.isChoosingTemplate)
        assertEquals(listOf("问题反馈", "功能建议", "使用求助"), model.state.value.builtInTemplates.map { it.name })
        model.onAction(CreateIssueAction.SelectBuiltInLanguage(GithubIssueTemplateLanguage.ENGLISH))
        assertEquals(listOf("Bug report", "Feature request", "Question"), model.state.value.builtInTemplates.map { it.name })
        model.onAction(CreateIssueAction.SelectTemplate("builtin:bug:en"))
        assertTrue(model.state.value.selectedTemplate!!.isBuiltIn)
        assertTrue(model.state.value.canPublish)
        model.onAction(CreateIssueAction.Submit)
        assertEquals(setOf("behavior", "steps", "expected"), model.state.value.invalidFieldIds)
    }

    @Test
    fun builtInLanguageAndDraftRestoreWithoutApplyingLanguageChangesToExistingAnswers() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val source = FakeIssuesRepository()
        val model = CreateIssueViewModel("openai", "codex", source, emptyTemplates, saved)
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate("builtin:question:en"))
        model.onAction(CreateIssueAction.FormTextChanged("question", "How do I configure the app?"))
        model.onAction(CreateIssueAction.ShowTemplateChooser)
        model.onAction(CreateIssueAction.SelectBuiltInLanguage(GithubIssueTemplateLanguage.CHINESE))
        assertEquals("builtin:question:en", model.state.value.selectedTemplate?.id)
        model.onAction(CreateIssueAction.SelectTemplate("builtin:question:zh"))
        assertTrue(model.state.value.showTemplateChangeConfirmation)
        model.onAction(CreateIssueAction.CancelTemplateChange)
        model.onAction(CreateIssueAction.DismissTemplateChooser)
        val copiedSavedState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = CreateIssueViewModel("openai", "codex", source, emptyTemplates, copiedSavedState)
        advanceUntilIdle()
        assertEquals(GithubIssueTemplateLanguage.CHINESE, restored.state.value.builtInLanguage)
        assertEquals("How do I configure the app?", restored.state.value.answers["question"]?.text)
        assertTrue(restored.state.value.canPublish)
        restored.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()
        assertTrue(source.createdDraft!!.body!!.contains("### What do you need help with?\n\nHow do I configure the app?"))
        assertTrue(source.createdDraft!!.labels.isEmpty())
        assertTrue(source.createdDraft!!.assignees.isEmpty())
        assertFalse(copiedSavedState.contains("issue_form_answers"))
    }

    @Test
    fun builtInTemplatesRespectRepositoriesThatRequireTheirOwnTemplates() = runTest(dispatcher) {
        val source = FakeIssuesRepository()
        val model = CreateIssueViewModel("openai", "codex", source, templates(formTemplate()))
        advanceUntilIdle()
        model.onAction(CreateIssueAction.SelectTemplate(GithubBuiltInIssueTemplates.forLanguage(GithubIssueTemplateLanguage.CHINESE).first().id))
        assertFalse(model.state.value.hasChosenTemplate)
        assertFalse(model.state.value.canPublish)
        assertNull(source.createdDraft)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listLoadsNextPageWithoutDiscardingTheFirstPage() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(IssuesAction.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), viewModel.state.value.items.map { it.number })
        assertEquals(listOf(1, 2), repository.issuePages)
        assertFalse(viewModel.state.value.canLoadMore)
    }

    @Test
    fun selectingClosedFilterResetsItemsAndLoadsTheClosedEndpoint() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(IssuesAction.SelectState(GithubIssueState.CLOSED))
        advanceUntilIdle()

        assertEquals(GithubIssueState.CLOSED, viewModel.state.value.selectedState)
        assertTrue(viewModel.state.value.items.all { it.state == GithubIssueState.CLOSED })
        assertEquals(GithubIssueState.CLOSED, repository.issueStates.last())
    }

    @Test
    fun typedPhraseWaitsBeforeSearchingTheRepositoryOnTheServer() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()
        val listRequests = repository.issueQueries.size

        viewModel.onAction(IssuesAction.SearchChanged("cr"))
        runCurrent()
        viewModel.onAction(IssuesAction.SearchChanged("crash"))
        runCurrent()

        assertEquals(0, repository.searchQueries.size)
        assertEquals(listRequests, repository.issueQueries.size)

        advanceUntilIdle()

        assertEquals(listOf("crash" to GithubIssueState.OPEN), repository.searchQueries)
        assertEquals(listOf(501), viewModel.state.value.items.map(GithubIssue::number))
        assertTrue(viewModel.state.value.canLoadMore)
    }

    @Test
    fun clearingThePhraseReturnsToThePlainListEndpoint() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()
        viewModel.onAction(IssuesAction.SearchChanged("crash"))
        advanceUntilIdle()

        viewModel.onAction(IssuesAction.SearchChanged(""))
        advanceUntilIdle()

        assertEquals(1, repository.searchQueries.size)
        assertEquals(listOf(1, 1), repository.issuePages)
        assertEquals(listOf(1), viewModel.state.value.items.map(GithubIssue::number))
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun loadMoreAndFiltersFollowTheActivePhraseToTheSearchEndpoint() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()
        viewModel.onAction(IssuesAction.SearchChanged("crash"))
        advanceUntilIdle()

        viewModel.onAction(IssuesAction.LoadMore)
        advanceUntilIdle()
        val merged = viewModel.state.value.items.map(GithubIssue::number)

        viewModel.onAction(IssuesAction.SelectState(GithubIssueState.CLOSED))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 1), repository.searchPages)
        assertEquals(listOf(501, 502), merged)
        assertEquals(GithubIssueState.CLOSED, repository.searchQueries.last().second)
        assertEquals(listOf(501), viewModel.state.value.items.map(GithubIssue::number))
        assertTrue(viewModel.state.value.items.all { it.state == GithubIssueState.CLOSED })
    }

    @Test
    fun selectingOrderingResetsPaginationAndSendsOneCombinedQuery() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssuesViewModel("openai", "codex", repository)
        advanceUntilIdle()

        viewModel.onAction(
            IssuesAction.SelectOrdering(
                sort = GithubListSort.CREATED,
                direction = GithubSortDirection.ASC
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.issuePages)
        assertEquals(GithubListSort.CREATED, repository.issueQueries.last().sort)
        assertEquals(GithubSortDirection.ASC, repository.issueQueries.last().direction)
        assertEquals(1, viewModel.state.value.items.single().number)
    }

    @Test
    fun detailLoadsIssueAndCommentsIndependently() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.issue?.number)
        assertEquals("First comment", viewModel.state.value.comments.single().body)
        assertFalse(viewModel.state.value.isLoadingIssue)
        assertFalse(viewModel.state.value.isLoadingComments)
    }

    @Test
    fun createIssueValidatesAndPublishesTheCreatedIssue() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = CreateIssueViewModel("openai", "codex", repository, emptyTemplates)
        advanceUntilIdle()
        viewModel.onAction(CreateIssueAction.SelectTemplate(null))

        viewModel.onAction(CreateIssueAction.TitleChanged("New issue"))
        viewModel.onAction(CreateIssueAction.BodyChanged("Details"))
        viewModel.onAction(CreateIssueAction.Submit)
        advanceUntilIdle()

        assertEquals("New issue", repository.createdDraft?.title)
        assertEquals(77, viewModel.state.value.createdIssue?.number)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun detailCanCommentAndCloseAnIssueWithoutReloadingTheThread() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(IssueDetailAction.CommentChanged("A new comment"))
        viewModel.onAction(IssueDetailAction.SubmitComment)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.ToggleState)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.ToggleLock)
        advanceUntilIdle()

        assertEquals("A new comment", repository.createdComment?.body)
        assertEquals("A new comment", viewModel.state.value.comments.last().body)
        assertEquals(GithubIssueState.CLOSED, viewModel.state.value.issue?.state)
        assertTrue(viewModel.state.value.issue?.isLocked == true)
        assertFalse(viewModel.state.value.isUpdatingLock)
    }

    @Test
    fun signedInViewerCanToggleACommentReaction() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(
            IssueDetailAction.ToggleCommentReaction(501, GithubReactionContent.HEART)
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.comments.single().reactions.count(GithubReactionContent.HEART))
        assertTrue(GithubReactionContent.HEART in viewModel.state.value.activeReactions.getValue(501))
        assertFalse(501 in viewModel.state.value.reactionBusyCommentIds)
        assertEquals("joyins", repository.reactionViewers.single())
    }

    @Test
    fun detailLoadsLabelsOnDemandAndUpdatesSelection() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        assertFalse(viewModel.state.value.labelsLoaded)

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.LoadLabels)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.UpdateLabels(listOf("bug")))
        advanceUntilIdle()

        assertEquals(listOf("bug", "enhancement"), viewModel.state.value.availableLabels.map(GithubIssueLabel::name))
        assertEquals(listOf("bug"), repository.updatedLabels)
        assertEquals(listOf("bug"), viewModel.state.value.issue?.labels?.map(GithubIssueLabel::name))
        assertFalse(viewModel.state.value.isUpdatingLabels)
    }

    @Test
    fun detailLoadsAssigneesOnDemandAndUpdatesSelection() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        assertFalse(viewModel.state.value.assigneesLoaded)

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.LoadAssignees)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.UpdateAssignees(listOf("alice")))
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob"), viewModel.state.value.availableAssignees.map { it.login })
        assertEquals(listOf("alice"), repository.updatedAssignees)
        assertEquals(listOf("alice"), viewModel.state.value.issue?.assignees?.map { it.login })
        assertFalse(viewModel.state.value.isUpdatingAssignees)
    }

    @Test
    fun detailLoadsMilestonesOnDemandAndUpdatesSelection() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        assertFalse(viewModel.state.value.milestonesLoaded)

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.LoadMilestones)
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.UpdateMilestone(3))
        advanceUntilIdle()

        assertEquals(listOf("v1.0"), viewModel.state.value.availableMilestones.map(GithubIssueMilestone::title))
        assertEquals(3, repository.updatedMilestone)
        assertEquals(3, viewModel.state.value.issue?.milestone?.number)
        assertFalse(viewModel.state.value.isUpdatingMilestone)
    }

    @Test
    fun detailUpdatesContentAfterDraftValidation() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()

        viewModel.onAction(IssueDetailAction.UpdateContent("Updated title", "Updated body"))
        advanceUntilIdle()

        assertEquals("Updated title", viewModel.state.value.issue?.title)
        assertEquals("Updated body", viewModel.state.value.issue?.body)
        assertFalse(viewModel.state.value.contentValidationError)
        assertFalse(viewModel.state.value.contentUpdateError)
    }

    @Test
    fun failedCommentReactionKeepsCountsAndExposesLocalError() = runTest(dispatcher) {
        val repository = FakeIssuesRepository().apply {
            reactionResult = Result.failure(IllegalStateException("network"))
        }
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        advanceUntilIdle()

        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(
            IssueDetailAction.ToggleCommentReaction(501, GithubReactionContent.ROCKET)
        )
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.comments.single().reactions.count(GithubReactionContent.ROCKET))
        assertTrue(501 in viewModel.state.value.reactionErrorCommentIds)
        assertFalse(501 in viewModel.state.value.reactionBusyCommentIds)
    }

    @Test
    fun accountSwitchRevokesIssueManagementUntilTheNewRoleIsLoaded() = runTest(dispatcher) {
        val repository = FakeIssuesRepository()
        val details = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.ADMIN)
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canTriage)

        details.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInSession("reader", 2))
        assertEquals(GithubCollaboratorRole.UNKNOWN, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canTriage)
        assertFalse(viewModel.state.value.canLock)
        viewModel.onAction(IssueDetailAction.UpdateLabels(listOf("must-not-save")))
        viewModel.onAction(IssueDetailAction.ToggleState)
        advanceUntilIdle()

        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertEquals(emptyList<String>(), repository.updatedLabels)
        assertEquals(GithubIssueState.OPEN, viewModel.state.value.issue?.state)
    }

    @Test
    fun lateIssueRoleResponseCannotGrantThePreviousAccountsPermissions() = runTest(dispatcher) {
        val oldRole = PendingResult<GithubRepositoryDetails>()
        val fallback = TestGithubRepositoryDetailsRepository(GithubCollaboratorRole.READ)
        var requests = 0
        val details = object : GithubRepositoryDetailsRepository by fallback {
            override suspend fun details(owner: String, name: String) =
                if (++requests == 1) oldRole.await() else fallback.details(owner, name)
        }
        val viewModel = IssueDetailViewModel("openai", "codex", 1, FakeIssuesRepository(), details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        runCurrent()
        viewModel.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)

        oldRole.complete(Result.success(TestGithubRepositoryDetailsRepository.details(viewerRole = GithubCollaboratorRole.ADMIN)))
        advanceUntilIdle()

        assertEquals("reader", viewModel.state.value.viewerLogin)
        assertEquals(GithubCollaboratorRole.READ, viewModel.state.value.viewerRole)
        assertFalse(viewModel.state.value.canTriage)
    }

    @Test
    fun oldIssueCommentSuccessDoesNotClearTheNextAccountsPendingDraft() =
        verifyOldIssueCommentIgnored(failure = false)

    @Test
    fun oldIssueCommentFailureDoesNotFailTheNextAccountsPendingDraft() =
        verifyOldIssueCommentIgnored(failure = true)

    @Test
    fun sentIssueCommentDoesNotEraseTextTypedWhileTheRequestWasPending() = runTest(dispatcher) {
        val pending = PendingResult<GithubIssueComment>()
        val repository = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun addComment(owner: String, name: String, number: Int, draft: GithubIssueCommentDraft) =
                pending.await()
        }
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, TestGithubRepositoryDetailsRepository())
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.CommentChanged("Sent comment"))
        viewModel.onAction(IssueDetailAction.SubmitComment)
        runCurrent()
        viewModel.onAction(IssueDetailAction.CommentChanged("Next draft"))
        val sent = viewModel.state.value.comments.single().copy(id = 999, body = "Sent comment")
        pending.complete(Result.success(sent))
        advanceUntilIdle()

        assertEquals("Next draft", viewModel.state.value.commentDraft)
        assertEquals(sent, viewModel.state.value.comments.last())
        assertFalse(viewModel.state.value.isSubmittingComment)
    }

    @Test
    fun previousAccountsCloseAndLockResponsesDoNotChangeTheCurrentIssue() = runTest(dispatcher) {
        val oldState = PendingResult<GithubIssue>()
        val oldLock = PendingResult<GithubIssue>()
        val repository = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun updateIssueState(owner: String, name: String, number: Int, state: GithubIssueState) =
                oldState.await()
            override suspend fun updateIssueLock(owner: String, name: String, number: Int, locked: Boolean) =
                oldLock.await()
        }
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        viewModel.onAction(IssueDetailAction.ToggleState)
        viewModel.onAction(IssueDetailAction.ToggleLock)
        runCurrent()
        assertTrue(viewModel.state.value.isUpdatingState)
        assertTrue(viewModel.state.value.isUpdatingLock)

        details.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInSession("reader", 2))
        advanceUntilIdle()
        val current = viewModel.state.value
        assertFalse(current.isUpdatingState)
        assertFalse(current.isUpdatingLock)
        oldState.complete(Result.success(issue(1, GithubIssueState.CLOSED)))
        oldLock.complete(Result.success(issue(1, GithubIssueState.OPEN).copy(isLocked = true)))
        advanceUntilIdle()

        assertEquals(current, viewModel.state.value)
    }

    private fun verifyOldIssueCommentIgnored(failure: Boolean) = runTest(dispatcher) {
        val oldComment = PendingResult<GithubIssueComment>()
        val newComment = PendingResult<GithubIssueComment>()
        var requests = 0
        val repository = object : GithubIssuesRepository by FakeIssuesRepository() {
            override suspend fun addComment(owner: String, name: String, number: Int, draft: GithubIssueCommentDraft) =
                if (++requests == 1) oldComment.await() else newComment.await()
        }
        val details = TestGithubRepositoryDetailsRepository()
        val viewModel = IssueDetailViewModel("openai", "codex", 1, repository, details)
        viewModel.onSessionChanged(signedInSession("joyins"))
        advanceUntilIdle()
        val commentTemplate = viewModel.state.value.comments.single()
        viewModel.onAction(IssueDetailAction.CommentChanged("Old account draft"))
        viewModel.onAction(IssueDetailAction.SubmitComment)
        runCurrent()
        assertTrue(viewModel.state.value.isSubmittingComment)

        details.viewerRole = GithubCollaboratorRole.READ
        viewModel.onSessionChanged(signedInSession("reader", 2))
        assertEquals("Old account draft", viewModel.state.value.commentDraft)
        assertFalse(viewModel.state.value.isSubmittingComment)
        viewModel.onAction(IssueDetailAction.CommentChanged("New account draft"))
        viewModel.onAction(IssueDetailAction.SubmitComment)
        runCurrent()
        val current = viewModel.state.value
        assertTrue(current.isSubmittingComment)
        assertEquals(2, requests)

        oldComment.complete(
            if (failure) Result.failure(IllegalStateException("old request failed"))
            else Result.success(commentTemplate.copy(id = 998, body = "Old account draft", author = user("joyins")))
        )
        advanceUntilIdle()
        assertEquals(current, viewModel.state.value)

        val submitted = commentTemplate.copy(id = 999, body = "New account draft", author = user("reader"))
        newComment.complete(Result.success(submitted))
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.commentDraft)
        assertEquals(listOf(commentTemplate, submitted), viewModel.state.value.comments)
        assertFalse(viewModel.state.value.isSubmittingComment)
        assertFalse(viewModel.state.value.commentSubmitError)
    }

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

    private class FakeIssuesRepository : GithubIssuesRepository {
        val issuePages = mutableListOf<Int>()
        val issueStates = mutableListOf<GithubIssueState>()
        val issueQueries = mutableListOf<GithubIssueListQuery>()
        val searchQueries = mutableListOf<Pair<String, GithubIssueState>>()
        val searchPages = mutableListOf<Int>()
        var createdDraft: GithubIssueDraft? = null
        var createdComment: GithubIssueCommentDraft? = null
        var currentDetailState: GithubIssueState = GithubIssueState.OPEN
        var updatedLabels: List<String> = emptyList()
        var updatedAssignees: List<String> = emptyList()
        var updatedMilestone: Int? = null
        val reactionViewers = mutableListOf<String>()
        var reactionResult: Result<GithubReactionToggle> = Result.success(
            GithubReactionToggle(GithubReactionContent.HEART, active = true, reactionId = 1L)
        )

        override suspend fun issues(
            owner: String,
            name: String,
            query: GithubIssueListQuery,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubIssue>> {
            val state = query.state
            issuePages += page
            issueStates += state
            issueQueries += query
            val item = issue(
                number = if (state == GithubIssueState.CLOSED) 9 else page,
                state = state
            )
            return Result.success(GithubPage(listOf(item), nextPage = if (state == GithubIssueState.OPEN && page == 1) 2 else null))
        }

        override suspend fun searchInRepository(
            owner: String,
            name: String,
            text: String,
            query: GithubIssueListQuery,
            page: Int,
            perPage: Int
        ): Result<GithubPage<GithubIssue>> {
            searchQueries += text to query.state
            searchPages += page
            return Result.success(
                GithubPage(
                    items = listOf(issue(number = 500 + page, state = query.state)),
                    nextPage = if (page == 1) 2 else null
                )
            )
        }

        override suspend fun issue(owner: String, name: String, number: Int) =
            Result.success(issue(number, GithubIssueState.OPEN))

        override suspend fun comments(owner: String, name: String, number: Int, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    items = listOf(
                        GithubIssueComment(
                            id = 501,
                            body = "First comment",
                            author = user("maintainer"),
                            createdAt = "2026-08-16T01:00:00Z",
                            updatedAt = "2026-08-16T01:00:00Z",
                            htmlUrl = "https://github.com/openai/codex/issues/$number#issuecomment-501"
                        )
                    ),
                    nextPage = null
                )
            )

        override suspend fun createIssue(owner: String, name: String, draft: GithubIssueDraft): Result<GithubIssue> {
            createdDraft = draft
            return Result.success(issue(77, GithubIssueState.OPEN))
        }

        override suspend fun updateIssue(
            owner: String,
            name: String,
            number: Int,
            draft: GithubIssueDraft
        ) = Result.success(issue(number, currentDetailState).copy(title = draft.title, body = draft.body))

        override suspend fun addComment(
            owner: String,
            name: String,
            number: Int,
            draft: GithubIssueCommentDraft
        ): Result<GithubIssueComment> {
            createdComment = draft
            return Result.success(
                GithubIssueComment(
                    id = 999,
                    body = draft.body,
                    author = user("joyins"),
                    createdAt = "2026-08-16T02:00:00Z",
                    updatedAt = "2026-08-16T02:00:00Z",
                    htmlUrl = "https://github.com/openai/codex/issues/$number#issuecomment-999"
                )
            )
        }

        override suspend fun updateIssueState(
            owner: String,
            name: String,
            number: Int,
            state: GithubIssueState
        ): Result<GithubIssue> {
            currentDetailState = state
            return Result.success(issue(number, state))
        }

        override suspend fun updateIssueLock(
            owner: String,
            name: String,
            number: Int,
            locked: Boolean
        ) = Result.success(issue(number, currentDetailState).copy(isLocked = locked))

        override suspend fun labels(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(
                GithubPage(
                    listOf(
                        GithubIssueLabel("bug", "d73a4a", null),
                        GithubIssueLabel("enhancement", "a2eeef", null)
                    ),
                    null
                )
            )

        override suspend fun updateIssueLabels(
            owner: String,
            name: String,
            number: Int,
            labels: List<String>
        ): Result<GithubIssue> {
            updatedLabels = labels
            return Result.success(
                issue(number, currentDetailState).copy(
                    labels = labels.map { GithubIssueLabel(it, "d73a4a", null) }
                )
            )
        }

        override suspend fun assignees(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(user("alice"), user("bob")), null))

        override suspend fun updateIssueAssignees(
            owner: String,
            name: String,
            number: Int,
            assignees: List<String>
        ): Result<GithubIssue> {
            updatedAssignees = assignees
            return Result.success(
                issue(number, currentDetailState).copy(assignees = assignees.map(::user))
            )
        }

        override suspend fun milestones(owner: String, name: String, page: Int, perPage: Int) =
            Result.success(GithubPage(listOf(milestone()), null))

        override suspend fun updateIssueMilestone(
            owner: String,
            name: String,
            number: Int,
            milestoneNumber: Int?
        ): Result<GithubIssue> {
            updatedMilestone = milestoneNumber
            return Result.success(
                issue(number, currentDetailState).copy(
                    milestone = milestoneNumber?.let { milestone() }
                )
            )
        }

        override suspend fun toggleCommentReaction(
            owner: String,
            name: String,
            commentId: Long,
            content: GithubReactionContent,
            viewerLogin: String
        ): Result<GithubReactionToggle> {
            reactionViewers += viewerLogin
            return reactionResult.map { it.copy(content = content) }
        }
    }

    private companion object {
        fun issue(number: Int, state: GithubIssueState) = GithubIssue(
            id = number.toLong(),
            number = number,
            title = "Issue $number",
            body = "Body",
            state = state,
            author = user("alice"),
            labels = emptyList(),
            assignees = emptyList(),
            comments = 1,
            isLocked = false,
            createdAt = "2026-08-15T00:00:00Z",
            updatedAt = "2026-08-16T00:00:00Z",
            closedAt = if (state == GithubIssueState.CLOSED) "2026-08-16T00:00:00Z" else null,
            htmlUrl = "https://github.com/openai/codex/issues/$number"
        )

        fun user(login: String) = GithubUserSummary(
            login = login,
            avatarUrl = null,
            htmlUrl = "https://github.com/$login"
        )

        fun milestone() = GithubIssueMilestone(
            number = 3,
            title = "v1.0",
            description = "Launch",
            openIssues = 4,
            closedIssues = 6,
            dueOn = "2026-09-01T00:00:00Z"
        )

        fun signedInSession(login: String, id: Long = 1) = GithubSession.SignedIn(
            GithubAccount(
                id = id,
                login = login,
                name = null,
                bio = null,
                avatarUrl = "https://avatars.githubusercontent.com/u/1",
                htmlUrl = "https://github.com/$login",
                publicRepositories = 0,
                followers = 0,
                following = 0
            )
        )
    }
}
