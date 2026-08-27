package takagi.ru.monica.github.feature.actions

internal fun formatGithubActionsLog(source: String): String {
    if (source.isEmpty()) return source
    val output = StringBuilder(source.length)
    var index = 0
    while (index < source.length) {
        val character = source[index]
        when {
            character == '\u001B' && source.getOrNull(index + 1) == '[' -> {
                index += 2
                while (index < source.length) {
                    val marker = source[index++]
                    if (marker.code in 0x40..0x7E) break
                }
            }
            character == '\r' -> {
                output.append('\n')
                if (source.getOrNull(index + 1) == '\n') index++
                index++
            }
            character == '\n' || character == '\t' || !character.isISOControl() -> {
                output.append(character)
                index++
            }
            else -> index++
        }
    }
    return output.toString()
}
