package takagi.ru.monica.github.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubRepositoryCreateDraft
import takagi.ru.monica.github.domain.GithubUserRepositoriesRepository
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure
import takagi.ru.monica.github.feature.repository.githubWriteFailure

@Immutable
data class CreateRepositoryUiState(
    val name: String = "",
    val description: String = "",
    val isPrivate: Boolean = false,
    val autoInit: Boolean = false,
    val isSubmitting: Boolean = false,
    val failure: RepositoryWriteFailure? = null,
    val created: GithubRepository? = null
) {
    val isNameUsable: Boolean get() = GithubRepositoryCreateDraft.isValidName(name)

    // A typed-but-unusable name deserves a hint while a blank one is only a disabled button.
    val showNameHint: Boolean get() = name.isNotBlank() && !isNameUsable
    val canSubmit: Boolean get() = isNameUsable && !isSubmitting
}

sealed interface CreateRepositoryAction {
    data class NameChanged(val name: String) : CreateRepositoryAction
    data class DescriptionChanged(val description: String) : CreateRepositoryAction
    data object TogglePrivate : CreateRepositoryAction
    data object ToggleAutoInit : CreateRepositoryAction
    data object Submit : CreateRepositoryAction
    data object ConsumeCreated : CreateRepositoryAction
}

class CreateRepositoryViewModel(
    private val repository: GithubUserRepositoriesRepository,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _state = MutableStateFlow(CreateRepositoryUiState(
        name = savedState.get<String>(NAME_KEY).orEmpty(),
        description = savedState.get<String>(DESCRIPTION_KEY).orEmpty(),
        isPrivate = savedState.get<Boolean>(PRIVATE_KEY) ?: false,
        autoInit = savedState.get<Boolean>(AUTO_INIT_KEY) ?: false
    ))
    val state: StateFlow<CreateRepositoryUiState> = _state.asStateFlow()

    fun onAction(action: CreateRepositoryAction) {
        if (action == CreateRepositoryAction.ConsumeCreated) {
            _state.update { it.copy(created = null) }
            return
        }
        if (_state.value.isSubmitting) return
        when (action) {
            is CreateRepositoryAction.NameChanged -> edit(_state.value.copy(name = action.name))
            is CreateRepositoryAction.DescriptionChanged -> edit(_state.value.copy(description = action.description))
            CreateRepositoryAction.TogglePrivate -> edit(_state.value.copy(isPrivate = !_state.value.isPrivate))
            CreateRepositoryAction.ToggleAutoInit -> edit(_state.value.copy(autoInit = !_state.value.autoInit))
            CreateRepositoryAction.Submit -> submit()
            CreateRepositoryAction.ConsumeCreated -> Unit
        }
    }

    private fun edit(next: CreateRepositoryUiState) {
        _state.value = next.copy(failure = null)
        savedState[NAME_KEY] = next.name
        savedState[DESCRIPTION_KEY] = next.description
        savedState[PRIVATE_KEY] = next.isPrivate
        savedState[AUTO_INIT_KEY] = next.autoInit
    }

    private fun submit() {
        val current = _state.value
        val draft = GithubRepositoryCreateDraft.fromInput(
            name = current.name,
            description = current.description,
            isPrivate = current.isPrivate,
            autoInit = current.autoInit
        ).getOrNull() ?: return
        _state.update { it.copy(isSubmitting = true, failure = null) }
        viewModelScope.launch {
            repository.create(draft).fold(
                onSuccess = { created ->
                    listOf(NAME_KEY, DESCRIPTION_KEY, PRIVATE_KEY, AUTO_INIT_KEY).forEach { savedState.remove<Any>(it) }
                    _state.update { it.copy(
                        name = "", description = "", isPrivate = false, autoInit = false,
                        isSubmitting = false, failure = null, created = created
                    ) }
                },
                // A refused name stays on screen, so editing it beats retyping it.
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, failure = githubWriteFailure(error)) }
                }
            )
        }
    }

    class Factory(
        private val repository: GithubUserRepositoriesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(CreateRepositoryViewModel::class.java))
            return CreateRepositoryViewModel(repository, extras.createSavedStateHandle()) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateRepositoryViewModel::class.java))
            return CreateRepositoryViewModel(repository) as T
        }
    }

    private companion object {
        const val NAME_KEY = "create_repository_name"
        const val DESCRIPTION_KEY = "create_repository_description"
        const val PRIVATE_KEY = "create_repository_private"
        const val AUTO_INIT_KEY = "create_repository_auto_init"
    }
}
