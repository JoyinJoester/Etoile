package takagi.ru.monica.steam.outbox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steam_outbox",
    indices = [
        Index(value = ["account_id", "status", "next_attempt_at"]),
        Index(value = ["dedupe_key"], unique = true)
    ]
)
data class SteamOutboxEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "account_steam_id") val accountSteamId: String,
    val operation: String,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    val payload: String,
    val status: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAtMillis: Long,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(name = "last_error") val lastError: String? = null
)
