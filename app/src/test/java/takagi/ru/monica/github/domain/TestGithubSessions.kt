package takagi.ru.monica.github.domain

fun signedInGithubSession(login: String = "joyins") = GithubSession.SignedIn(
    GithubAccount(
        id = 1,
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
