package takagi.ru.monica.steam.itad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadApiKeyPolicy
import takagi.ru.monica.steam.itad.domain.ItadApiKeyValidationError

class ItadApiKeyPolicyTest {
    @Test
    fun trimsAndAcceptsPrintableKeyWithoutChangingItsContent() {
        val result = ItadApiKeyPolicy.validate("  example-key_123.abc  ")

        assertTrue(result.isValid)
        assertEquals("example-key_123.abc", result.normalizedKey)
        assertNull(result.error)
    }

    @Test
    fun rejectsBlankKey() {
        val result = ItadApiKeyPolicy.validate("   ")

        assertFalse(result.isValid)
        assertEquals(ItadApiKeyValidationError.EMPTY, result.error)
    }

    @Test
    fun rejectsHeaderControlCharacters() {
        val result = ItadApiKeyPolicy.validate("valid-part\r\nInjected: value")

        assertFalse(result.isValid)
        assertEquals(ItadApiKeyValidationError.CONTROL_CHARACTER, result.error)
    }

    @Test
    fun rejectsUnreasonablyLargeKey() {
        val result = ItadApiKeyPolicy.validate("a".repeat(ItadApiKeyPolicy.MAX_LENGTH + 1))

        assertFalse(result.isValid)
        assertEquals(ItadApiKeyValidationError.TOO_LONG, result.error)
    }
}
