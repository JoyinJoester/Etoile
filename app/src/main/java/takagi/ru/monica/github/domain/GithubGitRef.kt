package takagi.ru.monica.github.domain

/**
 * Branch naming rules from `git check-ref-format`. Unicode names are legal, so
 * validation must not be limited to an ASCII character class.
 */
object GithubGitRef {
    const val MAX_BRANCH_NAME_LENGTH = 255

    private const val FORBIDDEN_CHARACTERS = "~^:?*[\\"

    fun isValidBranchName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_BRANCH_NAME_LENGTH) return false
        if (name == "@" || name.startsWith("-") || name.contains("@{")) return false
        if (name.contains("..") || name.endsWith(".lock")) return false
        if (name.any { it <= ' ' || it == '\u007f' || it in FORBIDDEN_CHARACTERS }) return false
        return name.split('/').all { it.isNotEmpty() && !it.startsWith(".") && !it.endsWith(".") }
    }

    /** Tags follow the same `check-ref-format` rules as branches, including namespaced names. */
    fun isValidTagName(name: String): Boolean = isValidBranchName(name)
}
