package takagi.ru.monica.github.feature.issues

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubBuiltInIssueTemplates
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueFormAnswer
import takagi.ru.monica.github.domain.GithubIssueFormFieldType
import takagi.ru.monica.github.domain.GithubIssueTemplate
import takagi.ru.monica.github.domain.GithubIssueTemplateCatalog
import takagi.ru.monica.github.domain.GithubIssueTemplateLanguage
import takagi.ru.monica.github.domain.GithubIssueTemplatesRepository
import takagi.ru.monica.github.domain.GithubIssuesRepository

@Immutable
data class CreateIssueUiState(
    val owner: String,
    val name: String,
    val title: String = "",
    val body: String = "",
    val catalog: GithubIssueTemplateCatalog? = null,
    val builtInLanguage: GithubIssueTemplateLanguage = GithubIssueTemplateLanguage.ENGLISH,
    val selectedTemplate: GithubIssueTemplate? = null,
    val answers: Map<String, GithubIssueFormAnswer> = emptyMap(),
    val hasChosenTemplate: Boolean = false,
    val isChoosingTemplate: Boolean = true,
    val isLoadingTemplates: Boolean = true,
    val templateLoadError: Boolean = false,
    val showTemplateChangeConfirmation: Boolean = false,
    val pendingTemplateId: String? = null,
    val isPreviewing: Boolean = false,
    val invalidFieldIds: Set<String> = emptySet(),
    val bodyLengthError: Boolean = false,
    val isSubmitting: Boolean = false,
    val validationError: Boolean = false,
    val submitError: Boolean = false,
    val createdIssue: GithubIssue? = null
) {
    val fullName: String get() = "$owner/$name"
    val submissionBody: String get() = selectedTemplate?.takeIf { it.isForm }?.renderBody(answers) ?: body
    val builtInTemplates: List<GithubIssueTemplate> get() = GithubBuiltInIssueTemplates.forLanguage(builtInLanguage)
    val selectionAllowed: Boolean get() = catalog?.let { choices ->
        if (selectedTemplate == null) choices.blankIssuesEnabled
        else if (selectedTemplate.isBuiltIn) choices.blankIssuesEnabled && GithubBuiltInIssueTemplates.find(selectedTemplate.id) != null
        else selectedTemplate.isSupported && choices.templates.any { it.id == selectedTemplate.id && it.isSupported }
    } == true
    val canPublish: Boolean get() = hasChosenTemplate && !isChoosingTemplate && !isSubmitting &&
        !isLoadingTemplates && !templateLoadError && selectionAllowed
    val hasEdits: Boolean get() = title != selectedTemplate?.title.orEmpty() ||
        body != selectedTemplate?.body.orEmpty() || answers != selectedTemplate?.initialAnswers().orEmpty()
}

sealed interface CreateIssueAction {
    data class TitleChanged(val title: String) : CreateIssueAction
    data class BodyChanged(val body: String) : CreateIssueAction
    data class FormTextChanged(val fieldId: String, val text: String) : CreateIssueAction
    data class ToggleFormOption(val fieldId: String, val index: Int) : CreateIssueAction
    data class SelectTemplate(val id: String?) : CreateIssueAction
    data class SelectBuiltInLanguage(val language: GithubIssueTemplateLanguage) : CreateIssueAction
    data object LoadTemplates : CreateIssueAction
    data object ShowTemplateChooser : CreateIssueAction
    data object DismissTemplateChooser : CreateIssueAction
    data object ConfirmTemplateChange : CreateIssueAction
    data object CancelTemplateChange : CreateIssueAction
    data object TogglePreview : CreateIssueAction
    data object Submit : CreateIssueAction
    data object ConsumeCreatedIssue : CreateIssueAction
}

class CreateIssueViewModel(
    private val owner: String,
    private val name: String,
    private val repository: GithubIssuesRepository,
    private val templatesRepository: GithubIssueTemplatesRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    initialTemplateLanguage: GithubIssueTemplateLanguage = GithubIssueTemplateLanguage.ENGLISH
) : ViewModel() {
    private val savedTemplate = savedState.get<String>(TEMPLATE_KEY)?.let {
        runCatching { Json.decodeFromString<GithubIssueTemplate>(it) }.getOrNull()
    }
    private val savedAnswers = savedState.get<String>(ANSWERS_KEY)?.let {
        runCatching { Json.decodeFromString<Map<String, GithubIssueFormAnswer>>(it) }.getOrNull()
    }.orEmpty()
    private val hasSavedChoice = savedState.get<Boolean>(CHOICE_KEY)
        ?: (savedState.get<String>(TITLE_KEY).orEmpty().isNotEmpty() || savedState.get<String>(BODY_KEY).orEmpty().isNotEmpty())
    private val _state = MutableStateFlow(CreateIssueUiState(
        owner = owner, name = name,
        builtInLanguage = GithubIssueTemplateLanguage.entries.firstOrNull { it.name == savedState.get<String>(LANGUAGE_KEY) }
            ?: initialTemplateLanguage,
        title = savedState.get<String>(TITLE_KEY).orEmpty(),
        body = savedState.get<String>(BODY_KEY).orEmpty(),
        selectedTemplate = savedTemplate, answers = savedAnswers,
        hasChosenTemplate = hasSavedChoice, isChoosingTemplate = !hasSavedChoice
    ))
    val state: StateFlow<CreateIssueUiState> = _state.asStateFlow()
    private var templateJob: Job? = null

    init { loadTemplates() }

    fun onAction(action: CreateIssueAction) {
        if (action == CreateIssueAction.ConsumeCreatedIssue) {
            _state.update { it.copy(createdIssue = null) }
            return
        }
        if (_state.value.isSubmitting) return
        when (action) {
            is CreateIssueAction.TitleChanged -> if (action.title.length <= GithubIssueDraft.MAX_TITLE_LENGTH) {
                edit(_state.value.copy(title = action.title))
            }
            is CreateIssueAction.BodyChanged -> if (_state.value.selectedTemplate?.isForm != true &&
                action.body.length <= GithubIssueDraft.MAX_BODY_LENGTH) {
                edit(_state.value.copy(body = action.body))
            }
            is CreateIssueAction.FormTextChanged -> updateFormText(action)
            is CreateIssueAction.ToggleFormOption -> toggleFormOption(action)
            is CreateIssueAction.SelectTemplate -> selectTemplate(action.id)
            is CreateIssueAction.SelectBuiltInLanguage -> {
                savedState[LANGUAGE_KEY] = action.language.name
                _state.update { it.copy(builtInLanguage = action.language) }
            }
            CreateIssueAction.LoadTemplates -> loadTemplates()
            CreateIssueAction.ShowTemplateChooser -> _state.update { it.copy(isChoosingTemplate = true, isPreviewing = false) }
            CreateIssueAction.DismissTemplateChooser -> if (_state.value.hasChosenTemplate) {
                _state.update { it.copy(isChoosingTemplate = false, showTemplateChangeConfirmation = false) }
            }
            CreateIssueAction.ConfirmTemplateChange -> if (_state.value.showTemplateChangeConfirmation) {
                selectTemplate(_state.value.pendingTemplateId, confirmed = true)
            }
            CreateIssueAction.CancelTemplateChange -> _state.update {
                it.copy(showTemplateChangeConfirmation = false, pendingTemplateId = null)
            }
            CreateIssueAction.TogglePreview -> _state.update { it.copy(isPreviewing = !it.isPreviewing) }
            CreateIssueAction.Submit -> submit()
            CreateIssueAction.ConsumeCreatedIssue -> Unit
        }
    }

    private fun loadTemplates() {
        if (templateJob?.isActive == true) return
        _state.update { it.copy(isLoadingTemplates = true, templateLoadError = false) }
        templateJob = viewModelScope.launch {
            val result = templatesRepository.templates(owner, name)
            if (!isActive) return@launch
            result.fold(
                onSuccess = { catalog ->
                    _state.update { it.copy(catalog = catalog, isLoadingTemplates = false, templateLoadError = false) }
                },
                onFailure = { _state.update { it.copy(isLoadingTemplates = false, templateLoadError = true) } }
            )
        }
    }

    private fun selectTemplate(id: String?, confirmed: Boolean = false) {
        val current = _state.value
        val catalog = current.catalog ?: return
        if (current.isLoadingTemplates || current.templateLoadError) return
        val template = if (id == null) {
            if (!catalog.blankIssuesEnabled) return
            null
        } else catalog.templates.firstOrNull { it.id == id && it.isSupported }
            ?: GithubBuiltInIssueTemplates.find(id)?.takeIf { catalog.blankIssuesEnabled }
            ?: return
        if (current.hasChosenTemplate && current.selectedTemplate?.id == id) {
            _state.update { it.copy(isChoosingTemplate = false, showTemplateChangeConfirmation = false) }
            return
        }
        if (current.hasEdits && !confirmed) {
            _state.update { it.copy(showTemplateChangeConfirmation = true, pendingTemplateId = id) }
            return
        }
        val next = current.copy(
            selectedTemplate = template, hasChosenTemplate = true, isChoosingTemplate = false,
            title = template?.title.orEmpty(), body = template?.body.orEmpty(),
            answers = template?.initialAnswers().orEmpty(), invalidFieldIds = emptySet(),
            isPreviewing = false, showTemplateChangeConfirmation = false, pendingTemplateId = null
        )
        edit(next)
    }

    private fun updateFormText(action: CreateIssueAction.FormTextChanged) {
        val current = _state.value
        val field = current.selectedTemplate?.fields?.find { it.id == action.fieldId } ?: return
        if (field.type !in setOf(GithubIssueFormFieldType.INPUT, GithubIssueFormFieldType.TEXTAREA)) return
        if (action.text.length > GithubIssueDraft.MAX_BODY_LENGTH) return
        updateAnswer(field.id, GithubIssueFormAnswer(text = action.text))
    }

    private fun toggleFormOption(action: CreateIssueAction.ToggleFormOption) {
        val current = _state.value
        val field = current.selectedTemplate?.fields?.find { it.id == action.fieldId } ?: return
        if (field.type !in setOf(GithubIssueFormFieldType.DROPDOWN, GithubIssueFormFieldType.CHECKBOXES)) return
        if (action.index !in field.options.indices) return
        val selected = current.answers[field.id]?.selections.orEmpty()
        val selections = if (action.index in selected) selected - action.index
        else if (field.type == GithubIssueFormFieldType.DROPDOWN && !field.multiple) listOf(action.index)
        else selected + action.index
        updateAnswer(field.id, GithubIssueFormAnswer(selections = selections.sorted()))
    }

    private fun updateAnswer(id: String, answer: GithubIssueFormAnswer) {
        val current = _state.value
        val next = current.copy(answers = current.answers + (id to answer), invalidFieldIds = current.invalidFieldIds - id)
        if (next.submissionBody.length > GithubIssueDraft.MAX_BODY_LENGTH && next.submissionBody.length >= current.submissionBody.length) {
            _state.update { it.copy(bodyLengthError = true) }
            return
        }
        edit(next)
    }

    private fun edit(next: CreateIssueUiState) {
        _state.value = next.copy(validationError = false, submitError = false, bodyLengthError = false)
        savedState[TITLE_KEY] = next.title
        savedState[BODY_KEY] = next.body
        savedState[CHOICE_KEY] = next.hasChosenTemplate
        savedState[TEMPLATE_KEY] = next.selectedTemplate?.let { Json.encodeToString(it) }
        savedState[ANSWERS_KEY] = Json.encodeToString(next.answers)
    }

    private fun submit() {
        val current = _state.value
        if (!current.canPublish) return
        val invalidFields = current.selectedTemplate?.invalidFields(current.answers).orEmpty()
        val draft = GithubIssueDraft.fromInput(
            current.title, current.submissionBody,
            current.selectedTemplate?.labels.orEmpty(), current.selectedTemplate?.assignees.orEmpty()
        ).getOrNull()
        if (draft == null || invalidFields.isNotEmpty()) {
            _state.update { it.copy(
                validationError = current.title.isBlank(), invalidFieldIds = invalidFields,
                bodyLengthError = current.submissionBody.length > GithubIssueDraft.MAX_BODY_LENGTH,
                isPreviewing = false, submitError = false
            ) }
            return
        }
        _state.update { it.copy(isSubmitting = true, validationError = false, submitError = false) }
        viewModelScope.launch {
            repository.createIssue(owner, name, draft).fold(
                onSuccess = { issue ->
                    listOf(TITLE_KEY, BODY_KEY, CHOICE_KEY, TEMPLATE_KEY, ANSWERS_KEY).forEach { savedState.remove<Any>(it) }
                    _state.update { it.copy(
                        title = "", body = "", selectedTemplate = null, answers = emptyMap(),
                        hasChosenTemplate = false, isSubmitting = false, createdIssue = issue, submitError = false
                    ) }
                },
                onFailure = { _state.update { it.copy(isSubmitting = false, submitError = true) } }
            )
        }
    }

    class Factory(
        private val owner: String,
        private val name: String,
        private val repository: GithubIssuesRepository,
        private val templatesRepository: GithubIssueTemplatesRepository,
        private val initialTemplateLanguage: GithubIssueTemplateLanguage = GithubIssueTemplateLanguage.ENGLISH
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(CreateIssueViewModel::class.java))
            return CreateIssueViewModel(owner, name, repository, templatesRepository, extras.createSavedStateHandle(), initialTemplateLanguage) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateIssueViewModel::class.java))
            return CreateIssueViewModel(owner, name, repository, templatesRepository, initialTemplateLanguage = initialTemplateLanguage) as T
        }
    }

    private companion object {
        const val TITLE_KEY = "issue_title"
        const val BODY_KEY = "issue_body"
        const val CHOICE_KEY = "issue_template_chosen"
        const val TEMPLATE_KEY = "issue_template"
        const val ANSWERS_KEY = "issue_form_answers"
        const val LANGUAGE_KEY = "issue_builtin_language"
    }
}
