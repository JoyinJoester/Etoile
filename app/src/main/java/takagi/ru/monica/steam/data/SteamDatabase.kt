package takagi.ru.monica.steam.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.outbox.data.SteamOutboxDao
import takagi.ru.monica.steam.outbox.data.SteamOutboxEntity

@Database(
    entities = [
        SteamAccountEntity::class,
        SteamSecurityEventEntity::class,
        SteamLibraryCacheEntity::class,
        SteamAchievementsCacheEntity::class,
        SteamOutboxEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SteamDatabase : RoomDatabase() {
    abstract fun steamAccountDao(): SteamAccountDao
    abstract fun steamSecurityEventDao(): SteamSecurityEventDao
    abstract fun steamLibraryCacheDao(): SteamLibraryCacheDao
    abstract fun steamOutboxDao(): SteamOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: SteamDatabase? = null

        fun getDatabase(context: Context): SteamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SteamDatabase::class.java,
                    "steam_database"
                )
                    .addMigrations(migration1To2(context.applicationContext))
                    .addMigrations(migration2To3())
                    .addMigrations(migration3To4())
                    .addMigrations(migration4To5())
                    .addMigrations(migration5To6())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun migration2To3(): Migration {
            return object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                    db.query("SELECT id FROM steam_accounts ORDER BY selected DESC, updatedAt DESC").use { cursor ->
                        var index = 0
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                            val values = ContentValues().apply {
                                put("sortOrder", index++)
                            }
                            db.update(
                                "steam_accounts",
                                SQLiteDatabase.CONFLICT_NONE,
                                values,
                                "id = ?",
                                arrayOf(id.toString())
                            )
                        }
                    }
                }
            }
        }

        private fun migration3To4(): Migration {
            return object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN groupName TEXT")
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN accentArgb INTEGER")
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE steam_accounts ADD COLUMN lastHealthCheckAt INTEGER")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS steam_security_events (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            accountId INTEGER,
                            type TEXT NOT NULL,
                            severity TEXT NOT NULL,
                            summary TEXT NOT NULL,
                            detail TEXT,
                            occurredAt INTEGER NOT NULL,
                            FOREIGN KEY(accountId) REFERENCES steam_accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_steam_security_events_accountId " +
                            "ON steam_security_events(accountId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_steam_security_events_occurredAt " +
                            "ON steam_security_events(occurredAt)"
                    )
                }
            }
        }

        private fun migration4To5(): Migration {
            return object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS steam_library_cache (
                            accountId INTEGER NOT NULL PRIMARY KEY,
                            payload TEXT NOT NULL,
                            fetchedAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            FOREIGN KEY(accountId) REFERENCES steam_accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS steam_achievements_cache (
                            accountId INTEGER NOT NULL,
                            appId INTEGER NOT NULL,
                            payload TEXT NOT NULL,
                            fetchedAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(accountId, appId),
                            FOREIGN KEY(accountId) REFERENCES steam_accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                }
            }
        }

        private fun migration1To2(context: Context): Migration {
            return object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    encryptExistingSteamRows(db, SecurityManager(context))
                }
            }
        }

        private fun migration5To6(): Migration {
            return object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS steam_outbox (
                            id TEXT NOT NULL PRIMARY KEY,
                            account_id INTEGER NOT NULL,
                            account_steam_id TEXT NOT NULL,
                            operation TEXT NOT NULL,
                            dedupe_key TEXT NOT NULL,
                            payload TEXT NOT NULL,
                            status TEXT NOT NULL,
                            attempt_count INTEGER NOT NULL,
                            next_attempt_at INTEGER NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            last_error TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_steam_outbox_account_id_status_next_attempt_at " +
                            "ON steam_outbox(account_id, status, next_attempt_at)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_steam_outbox_dedupe_key " +
                            "ON steam_outbox(dedupe_key)"
                    )
                }
            }
        }

        private fun encryptExistingSteamRows(
            db: SupportSQLiteDatabase,
            securityManager: SecurityManager
        ) {
            val columns = listOf(
                "steam_id",
                "accountName",
                "displayName",
                "deviceId",
                "sharedSecret",
                "identitySecret",
                "revocationCode",
                "tokenGid",
                "accessToken",
                "refreshToken",
                "steamLoginSecure",
                "rawSteamGuardJson"
            )
            db.query(
                "SELECT id, ${columns.joinToString(", ")} FROM steam_accounts"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val values = ContentValues()
                    columns.forEach { column ->
                        values.putEncrypted(column, cursor, securityManager)
                    }
                    db.update(
                        "steam_accounts",
                        SQLiteDatabase.CONFLICT_NONE,
                        values,
                        "id = ?",
                        arrayOf(id.toString())
                    )
                }
            }
        }

        private fun ContentValues.putEncrypted(
            column: String,
            cursor: Cursor,
            securityManager: SecurityManager
        ) {
            val index = cursor.getColumnIndexOrThrow(column)
            if (cursor.isNull(index)) {
                putNull(column)
                return
            }

            val value = cursor.getString(index)
            val encrypted = if (securityManager.looksLikeMonicaCiphertext(value)) {
                value
            } else {
                securityManager.encryptDataLegacyCompat(value)
            }
            put(column, encrypted)
        }
    }
}
