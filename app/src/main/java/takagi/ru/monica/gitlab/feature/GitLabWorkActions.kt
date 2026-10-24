package takagi.ru.monica.gitlab.feature

import takagi.ru.monica.gitlab.domain.*

sealed interface GitLabWorkListAction {
    data class Section(val value: GitLabWorkSection) : GitLabWorkListAction
    data class SearchChanged(val value: String) : GitLabWorkListAction
    data object Search : GitLabWorkListAction
    data class Filter(val value: String) : GitLabWorkListAction
    data object Refresh : GitLabWorkListAction
    data object More : GitLabWorkListAction
    data object Retry : GitLabWorkListAction
    data class Editor(val open: Boolean) : GitLabWorkListAction
    data class Draft(val value: GitLabConversationDraft) : GitLabWorkListAction
    data class Create(val confirmedQuickActions: Boolean = false) : GitLabWorkListAction
    data class Run(val ref: String) : GitLabWorkListAction
}
sealed interface GitLabConversationAction {
    data object Refresh : GitLabConversationAction
    data class Notes(val more: Boolean = false) : GitLabConversationAction
    data class Diffs(val more: Boolean = false) : GitLabConversationAction
    data class ShowDiff(val show: Boolean) : GitLabConversationAction
    data class Editing(val open: Boolean) : GitLabConversationAction
    data class Draft(val value: GitLabConversationDraft) : GitLabConversationAction
    data class Comment(val value: String) : GitLabConversationAction
    data class Save(val confirmedQuickActions: Boolean = false) : GitLabConversationAction
    data class Send(val confirmedQuickActions: Boolean = false) : GitLabConversationAction
    data class ChangeState(val expected: String) : GitLabConversationAction
    data class Merge(val target: GitLabMergeTarget) : GitLabConversationAction
}
sealed interface GitLabPipelineAction {
    data object Refresh : GitLabPipelineAction
    data class Jobs(val more: Boolean = false) : GitLabPipelineAction
    data class Trace(val job: GitLabJob) : GitLabPipelineAction
    data object CloseTrace : GitLabPipelineAction
    data class Run(val expected: GitLabPipeline, val action: GitLabRunAction) : GitLabPipelineAction
    data class Job(val expected: GitLabJob, val action: GitLabRunAction) : GitLabPipelineAction
}
