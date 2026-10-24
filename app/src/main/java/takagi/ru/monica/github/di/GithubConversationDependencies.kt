package takagi.ru.monica.github.di

import takagi.ru.monica.github.domain.GithubAuthRepository
import takagi.ru.monica.github.domain.GithubDiscussionsRepository
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubIssueTemplatesRepository
import takagi.ru.monica.github.domain.GithubPullRequestsRepository
import takagi.ru.monica.github.domain.GithubRepositoryContentsRepository
import takagi.ru.monica.github.domain.GithubRepositoryDetailsRepository

/** Conversation navigation uses domain services without constructing credentials or network clients. */
interface GithubConversationDependencies {
    val authRepository: GithubAuthRepository
    val discussionsRepository: GithubDiscussionsRepository
    val issuesRepository: GithubIssuesRepository
    val issueTemplatesRepository: GithubIssueTemplatesRepository
    val pullRequestsRepository: GithubPullRequestsRepository
    val repositoryContentsRepository: GithubRepositoryContentsRepository
    val repositoryDetailsRepository: GithubRepositoryDetailsRepository
}
