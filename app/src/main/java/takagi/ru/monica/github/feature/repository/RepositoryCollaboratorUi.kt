package takagi.ru.monica.github.feature.repository

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubCollaboratorRole

/**
 * The list, the permission menu and the invite form all name the same roles,
 * so the label mapping lives once instead of once per screen.
 */
@Composable
internal fun collaboratorRoleText(role: GithubCollaboratorRole): String = stringResource(
    when (role) {
        GithubCollaboratorRole.READ -> R.string.github_role_read
        GithubCollaboratorRole.TRIAGE -> R.string.github_role_triage
        GithubCollaboratorRole.WRITE -> R.string.github_role_write
        GithubCollaboratorRole.MAINTAIN -> R.string.github_role_maintain
        GithubCollaboratorRole.ADMIN -> R.string.github_role_admin
        GithubCollaboratorRole.UNKNOWN -> R.string.github_role_unknown
    }
)

/**
 * Granting access and offering it are different promises, so the sentence the
 * list and the form share is chosen from the status code the write came back with.
 */
@Composable
internal fun collaboratorOutcomeText(feedback: RepositoryCollaboratorFeedback): String = when (feedback.outcome) {
    RepositoryCollaboratorOutcome.InvitationSent ->
        stringResource(R.string.github_invite_sent, feedback.login)
    RepositoryCollaboratorOutcome.AccessUpdated ->
        stringResource(R.string.github_permission_updated, feedback.login)
    RepositoryCollaboratorOutcome.Removed ->
        stringResource(R.string.github_collaborator_removed, feedback.login)
}
