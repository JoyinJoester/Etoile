package takagi.ru.monica.github.feature.repository

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.data.GithubApiException
import takagi.ru.monica.github.data.GithubSignedOutException
import takagi.ru.monica.github.domain.GithubCollaboratorRole

/** Why GitHub refused a repository write, so the screen can name a way out. */
enum class RepositoryWriteFailure {
    InvalidInput,
    Conflict,
    Forbidden,
    NotFound,
    RateLimited,
    Network
}

/** Every repository write path explains refusals with this one classifier. */
internal fun githubWriteFailure(error: Throwable): RepositoryWriteFailure {
    if (error is GithubSignedOutException) return RepositoryWriteFailure.Forbidden
    val apiError = error as? GithubApiException ?: return RepositoryWriteFailure.Network
    // GitHub answers an exhausted quota with 403 just as often as with 429.
    if (apiError.rateLimited) return RepositoryWriteFailure.RateLimited
    return when (apiError.statusCode) {
        401, 403 -> RepositoryWriteFailure.Forbidden
        404 -> RepositoryWriteFailure.NotFound
        409 -> RepositoryWriteFailure.Conflict
        422 -> RepositoryWriteFailure.InvalidInput
        429 -> RepositoryWriteFailure.RateLimited
        else -> RepositoryWriteFailure.Network
    }
}

/** Contents and refs share the taxonomy but not the nouns, so each keeps its own wording. */
@Composable
internal fun RepositoryFileFailureMessage(failure: RepositoryWriteFailure) {
    val message = when (failure) {
        RepositoryWriteFailure.InvalidInput -> R.string.github_file_change_invalid
        RepositoryWriteFailure.Conflict -> R.string.github_file_change_conflict
        RepositoryWriteFailure.Forbidden -> R.string.github_file_change_forbidden
        RepositoryWriteFailure.NotFound -> R.string.github_file_change_not_found
        RepositoryWriteFailure.RateLimited -> R.string.github_write_rate_limited
        RepositoryWriteFailure.Network -> R.string.github_file_change_error
    }
    Text(text = stringResource(message), color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun RepositoryRefFailureMessage(failure: RepositoryWriteFailure) {
    val message = when (failure) {
        RepositoryWriteFailure.InvalidInput -> R.string.github_ref_change_invalid
        RepositoryWriteFailure.Conflict -> R.string.github_ref_change_conflict
        RepositoryWriteFailure.Forbidden -> R.string.github_ref_change_forbidden
        RepositoryWriteFailure.NotFound -> R.string.github_ref_change_not_found
        RepositoryWriteFailure.RateLimited -> R.string.github_write_rate_limited
        RepositoryWriteFailure.Network -> R.string.github_ref_change_error
    }
    Text(text = stringResource(message), color = MaterialTheme.colorScheme.error)
}

/**
 * Star, watch and fork keep the action in their headline sentence, so this mapper
 * only names the refusal. The wording stays noun-neutral and never mentions typed
 * input: these actions are a single tap.
 */
@Composable
internal fun repositoryActionFailureText(failure: RepositoryWriteFailure): String = when (failure) {
    RepositoryWriteFailure.InvalidInput -> stringResource(R.string.github_action_invalid)
    RepositoryWriteFailure.Conflict -> stringResource(R.string.github_action_conflict)
    RepositoryWriteFailure.Forbidden -> stringResource(R.string.github_action_forbidden)
    RepositoryWriteFailure.NotFound -> stringResource(R.string.github_action_not_found)
    RepositoryWriteFailure.RateLimited -> stringResource(R.string.github_action_rate_limited)
    RepositoryWriteFailure.Network -> stringResource(R.string.github_action_network)
}

/** A comparison is a read, so it names a refusal in viewing terms instead of committing terms. */
@Composable
internal fun repositoryComparisonFailureText(failure: RepositoryWriteFailure): String = when (failure) {
    RepositoryWriteFailure.InvalidInput -> stringResource(R.string.github_compare_invalid)
    RepositoryWriteFailure.Conflict -> stringResource(R.string.github_compare_pending)
    RepositoryWriteFailure.Forbidden -> stringResource(R.string.github_compare_forbidden)
    RepositoryWriteFailure.NotFound -> stringResource(R.string.github_compare_not_found)
    RepositoryWriteFailure.RateLimited -> stringResource(R.string.github_compare_rate_limited)
    RepositoryWriteFailure.Network -> stringResource(R.string.github_compare_error)
}

/**
 * Screens hide write affordances the viewer cannot use, so say why rather than
 * leaving the page silently missing buttons. Stays blank while the role loads.
 * Each surface names its own right: pushing files or handing out access.
 */
@Composable
internal fun RepositoryWriteAccessHint(
    viewerLogin: String?,
    viewerRole: GithubCollaboratorRole,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    gate: (GithubCollaboratorRole) -> Boolean = GithubCollaboratorRole::canPush,
    @StringRes deniedMessage: Int = R.string.github_write_read_only
) {
    if (gate(viewerRole)) return
    if (viewerLogin == null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.github_write_sign_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSignIn) {
                Text(stringResource(R.string.github_sign_in))
            }
        }
    } else if (viewerRole != GithubCollaboratorRole.UNKNOWN) {
        Text(
            text = stringResource(deniedMessage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth()
        )
    }
}
