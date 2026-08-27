package takagi.ru.monica.github.navigation

import kotlinx.serialization.Serializable
import takagi.ru.monica.github.feature.mywork.MyConversationsKind

@Serializable
data object GithubHomeRoute

@Serializable
data object GithubUserRepositoriesRoute

@Serializable
data object GithubOrganizationsRoute

// kind is stored as its name: navigation restores enum route arguments
// reflectively via Class.forName, which R8 renaming breaks.
@Serializable
data class GithubMyConversationsRoute(val kind: String) {
    constructor(kind: MyConversationsKind) : this(kind.name)

    val conversationsKind: MyConversationsKind get() = MyConversationsKind.valueOf(kind)
}

@Serializable
data class GithubUserProfileRoute(val login: String)

@Serializable
data class GithubUserFollowersRoute(val login: String)

@Serializable
data class GithubUserFollowingRoute(val login: String)

@Serializable
data class GithubRepositoryRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryFilesRoute(
    val fullName: String,
    val ref: String,
    val path: String = ""
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryBranchesRoute(
    val fullName: String,
    val defaultBranch: String
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryCollaboratorsRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryWebhooksRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryFileRoute(
    val fullName: String,
    val ref: String,
    val path: String
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryIssuesRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryPullRequestsRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryActionsRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryReleasesRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubRepositoryCommitsRoute(
    val fullName: String,
    val ref: String
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubCommitRoute(
    val fullName: String,
    val sha: String
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubReleaseRoute(
    val fullName: String,
    val releaseId: Long
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubReleaseTagRoute(
    val fullName: String,
    val tagName: String
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubWorkflowRunsRoute(
    val fullName: String,
    val workflowId: Long,
    val workflowName: String
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubActionsRunRoute(
    val fullName: String,
    val runId: Long
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubActionsJobRoute(
    val fullName: String,
    val jobId: Long
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubIssueRoute(
    val fullName: String,
    val number: Int
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubPullRequestRoute(
    val fullName: String,
    val number: Int
) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}

@Serializable
data class GithubCreateIssueRoute(val fullName: String) {
    val owner: String get() = fullName.substringBefore('/')
    val name: String get() = fullName.substringAfter('/')
}
