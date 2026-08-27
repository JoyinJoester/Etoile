package takagi.ru.monica.steam.itad.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import takagi.ru.monica.steam.itad.domain.ItadApiKeyPolicy
import takagi.ru.monica.steam.itad.domain.ItadApiKeyValidationError

sealed interface ItadCredentialSaveResult {
    data object Saved : ItadCredentialSaveResult
    data object WriteFailed : ItadCredentialSaveResult
    data class Invalid(val error: ItadApiKeyValidationError) : ItadCredentialSaveResult
}

fun interface ItadApiKeyProvider {
    fun readApiKey(): String?
}

class ItadCredentialStore(context: Context) : ItadApiKeyProvider {
    private val applicationContext = context.applicationContext

    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            applicationContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun readApiKey(): String? {
        val stored = preferences.getString(API_KEY, null) ?: return null
        return ItadApiKeyPolicy.validate(stored).normalizedKey
    }

    fun saveApiKey(rawKey: String): ItadCredentialSaveResult {
        val validation = ItadApiKeyPolicy.validate(rawKey)
        val normalized = validation.normalizedKey
            ?: return ItadCredentialSaveResult.Invalid(
                validation.error ?: ItadApiKeyValidationError.EMPTY
            )
        return if (preferences.edit().putString(API_KEY, normalized).commit()) {
            ItadCredentialSaveResult.Saved
        } else {
            ItadCredentialSaveResult.WriteFailed
        }
    }

    fun clearApiKey(): Boolean = preferences.edit().remove(API_KEY).commit()

    private companion object {
        const val PREFERENCES_NAME = "monica_itad_credentials"
        const val API_KEY = "api_key"
    }
}
