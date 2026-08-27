package takagi.ru.monica.steam.itad.domain

enum class ItadApiKeyValidationError {
    EMPTY,
    TOO_LONG,
    CONTROL_CHARACTER
}

data class ItadApiKeyValidation(
    val normalizedKey: String? = null,
    val error: ItadApiKeyValidationError? = null
) {
    val isValid: Boolean
        get() = normalizedKey != null && error == null
}

object ItadApiKeyPolicy {
    const val MAX_LENGTH = 512

    fun validate(rawKey: String): ItadApiKeyValidation {
        val normalized = rawKey.trim()
        if (normalized.isEmpty()) {
            return ItadApiKeyValidation(error = ItadApiKeyValidationError.EMPTY)
        }
        if (normalized.length > MAX_LENGTH) {
            return ItadApiKeyValidation(error = ItadApiKeyValidationError.TOO_LONG)
        }
        if (normalized.any(Char::isISOControl)) {
            return ItadApiKeyValidation(error = ItadApiKeyValidationError.CONTROL_CHARACTER)
        }
        return ItadApiKeyValidation(normalizedKey = normalized)
    }
}
