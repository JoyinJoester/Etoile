package takagi.ru.monica.github.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.github.feature.repository.RepositoryWriteFailure

/**
 * Blocking speaks the same status codes as every other write, but the nouns are about an account
 * rather than a repository, so this is the block-flavoured wording over the shared taxonomy.
 */
@Composable
internal fun userBlockFailureText(failure: RepositoryWriteFailure): String = when (failure) {
    RepositoryWriteFailure.InvalidInput -> stringResource(R.string.github_block_invalid)
    RepositoryWriteFailure.Conflict -> stringResource(R.string.github_block_error)
    RepositoryWriteFailure.Forbidden -> stringResource(R.string.github_block_forbidden)
    RepositoryWriteFailure.NotFound -> stringResource(R.string.github_block_not_found)
    RepositoryWriteFailure.RateLimited -> stringResource(R.string.github_write_rate_limited)
    RepositoryWriteFailure.Network -> stringResource(R.string.github_block_error)
}
