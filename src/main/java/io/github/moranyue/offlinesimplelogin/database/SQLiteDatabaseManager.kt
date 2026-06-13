package io.github.moranyue.offlinesimplelogin.database

import io.github.moranyue.offlinesimplelogin.config.SQLiteConfig
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

/**
 * SQLite implementation of DatabaseManager.
 *
 * Stores player data in a local SQLite file. Uses WAL mode for better
 * concurrent read performance.
 */
class SQLiteDatabaseManager(
    private val config: SQLiteConfig,
    private val dataFolder: Path
) : DatabaseManager {

    private var connection: Connection? = null

    override fun connect() {
        val dbPath = dataFolder.resolve(config.file).toAbsolutePath()
        dbPath.parent.toFile().mkdirs()

        // Load SQLite JDBC driver
        Class.forName("org.sqlite.JDBC")

        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath}")
        logger?.info("Connected to SQLite database: ${dbPath}")

        // Performance optimizations
        connection?.createStatement()?.use { stmt ->
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA foreign_keys=ON")
            stmt.execute("PRAGMA synchronous=NORMAL")
            stmt.execute("PRAGMA cache_size=-64000") // 64MB cache
        }
    }

    override fun close() {
        try {
            connection?.close()
        } catch (e: Exception) {
            logger?.warning("Error closing SQLite connection: ${e.message}")
        }
        connection = null
    }

    override fun createTables() {
        connection?.createStatement()?.use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS offline_players (
                    username VARCHAR(16) PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    last_ip VARCHAR(45),
                    last_login TIMESTAMP,
                    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS offline_sessions (
                    username VARCHAR(16) PRIMARY KEY,
                    ip VARCHAR(45) NOT NULL,
                    last_activity TIMESTAMP NOT NULL,
                    expired BOOLEAN NOT NULL DEFAULT FALSE,
                    FOREIGN KEY (username) REFERENCES offline_players(username)
                )
                """.trimIndent()
            )
        }
    }

    override fun registerPlayer(username: String, passwordHash: String, ip: String): Boolean {
        val sql = "INSERT OR IGNORE INTO offline_players (username, password_hash, last_ip, last_login) VALUES (?, ?, ?, ?)"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, username.lowercase())
            stmt.setString(2, passwordHash)
            stmt.setString(3, ip)
            stmt.setString(4, Instant.now().toString())
            return stmt.executeUpdate() > 0
        }
        return false
    }

    override fun getPasswordHash(username: String): String? {
        val sql = "SELECT password_hash FROM offline_players WHERE username = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, username.lowercase())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getString("password_hash")
                }
            }
        }
        return null
    }

    override fun isRegistered(username: String): Boolean {
        val sql = "SELECT 1 FROM offline_players WHERE username = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, username.lowercase())
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
        return false
    }

    override fun updateLastLogin(username: String, ip: String) {
        val sql = "UPDATE offline_players SET last_ip = ?, last_login = ?, updated_at = ? WHERE username = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, ip)
            stmt.setString(2, Instant.now().toString())
            stmt.setString(3, Instant.now().toString())
            stmt.setString(4, username.lowercase())
            stmt.executeUpdate()
        }
    }

    override fun updatePassword(username: String, newPasswordHash: String) {
        val sql = "UPDATE offline_players SET password_hash = ?, updated_at = ? WHERE username = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, newPasswordHash)
            stmt.setString(2, Instant.now().toString())
            stmt.setString(3, username.lowercase())
            stmt.executeUpdate()
        }
    }

    override fun saveSession(username: String, ip: String) {
        val sql = """
            INSERT OR REPLACE INTO offline_sessions (username, ip, last_activity, expired)
            VALUES (?, ?, ?, FALSE)
        """.trimIndent()
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, username.lowercase())
            stmt.setString(2, ip)
            stmt.setString(3, Instant.now().toString())
            stmt.executeUpdate()
        }
    }

    override fun getSession(username: String): StoredSession? {
        val sql = "SELECT username, ip, last_activity, expired FROM offline_sessions WHERE username = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, username.lowercase())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return StoredSession(
                        username = rs.getString("username"),
                        ip = rs.getString("ip"),
                        lastActivity = Instant.parse(rs.getString("last_activity")),
                        expired = rs.getBoolean("expired")
                    )
                }
            }
        }
        return null
    }

    override fun removeSession(username: String) {
        val sql = "DELETE FROM offline_sessions WHERE username = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, username.lowercase())
            stmt.executeUpdate()
        }
    }

    override fun cleanExpiredSessions(timeoutSeconds: Int) {
        val sql = "DELETE FROM offline_sessions WHERE (strftime('%s', 'now') - strftime('%s', last_activity)) > ? OR expired = TRUE"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setInt(1, timeoutSeconds)
            val deleted = stmt.executeUpdate()
            if (deleted > 0) {
                logger?.finest("Cleaned $deleted expired sessions")
            }
        }
    }

    companion object {
        private val logger = java.util.logging.Logger.getLogger("OfflineSimpleLogin.SQLite")
    }
}
