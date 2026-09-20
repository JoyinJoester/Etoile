package takagi.ru.monica.github.domain

data class GithubPullRequestTemplate(val repository: String, val path: String, val body: String?) {
    val id: String get() = "$repository:$path"
}
