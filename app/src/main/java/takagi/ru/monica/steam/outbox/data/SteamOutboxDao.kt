package takagi.ru.monica.steam.outbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SteamOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SteamOutboxEntity): Long

    @Query("SELECT * FROM steam_outbox WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SteamOutboxEntity?

    @Query("SELECT * FROM steam_outbox WHERE dedupe_key = :dedupeKey LIMIT 1")
    suspend fun getByDedupeKey(dedupeKey: String): SteamOutboxEntity?

    @Query(
        """
        SELECT * FROM steam_outbox
        WHERE account_id = :accountId
          AND status IN ('QUEUED', 'RETRYABLE')
          AND next_attempt_at <= :nowMillis
        ORDER BY created_at ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun findReady(accountId: Long, nowMillis: Long, limit: Int): List<SteamOutboxEntity>

    @Query(
        """
        UPDATE steam_outbox
        SET status = 'IN_FLIGHT',
            attempt_count = attempt_count + 1,
            updated_at = :nowMillis,
            last_error = NULL
        WHERE id = :id
          AND status IN ('QUEUED', 'RETRYABLE')
          AND (:ignoreSchedule = 1 OR next_attempt_at <= :nowMillis)
        """
    )
    suspend fun claim(id: String, nowMillis: Long, ignoreSchedule: Boolean = false): Int

    @Query(
        """
        UPDATE steam_outbox
        SET status = :status,
            attempt_count = :attemptCount,
            next_attempt_at = :nextAttemptAtMillis,
            updated_at = :updatedAtMillis,
            last_error = :lastError
        WHERE id = :id AND status = :expectedStatus
        """
    )
    suspend fun transition(
        id: String,
        expectedStatus: String,
        status: String,
        attemptCount: Int,
        nextAttemptAtMillis: Long,
        updatedAtMillis: Long,
        lastError: String?
    ): Int

    @Query(
        """
        SELECT * FROM steam_outbox
        WHERE account_id = :accountId
          AND status IN ('QUEUED', 'IN_FLIGHT', 'AWAITING_CONFIRMATION', 'RETRYABLE')
        ORDER BY created_at ASC, id ASC
        """
    )
    suspend fun findRecoverable(accountId: Long): List<SteamOutboxEntity>

    @Query("DELETE FROM steam_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM steam_outbox WHERE status IN ('COMPLETED', 'CANCELLED', 'PERMANENT_FAILURE') AND updated_at < :beforeMillis")
    suspend fun pruneTerminal(beforeMillis: Long): Int
}
