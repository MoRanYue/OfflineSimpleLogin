package io.github.moranyue.offlinesimplelogin.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.moranyue.offlinesimplelogin.config.PostgreSQLConfig
import java.sql.Connection
import java.time.Instant

/**
 * PostgreSQL implementation of DatabaseManager.
 *
 * Uses HikariCP connection pool for efficient database access.
 */
class PostgreSQLDatabaseManager(
    private val config: PostgreSQLConfig
) : DatabaseManager {

    private var dataSource: HikariDataSource? = null

    override fun connect() {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}"
            username = config.username
            password = config.password
            maximumPoolSize = config.poolSize
            connectionTimeout = config.connectionTimeout
            maxLifetime = config.maxLifetime
            driverClassName = "org.postgresql.Driver"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        }

        dataSource = HikariDataSource(hikariConfig)
        logger?.info("Connected to PostgreSQL database at ${config.host}:${config.port}/${config.database}")
    }

    override fun close() {
        try {
            dataSource?.close()
        } catch (e: Exception) {
            logger?.warning("Error closing PostgreSQL data source: ${e.message}")
        }
        dataSource = null
    }

    private fun getConnection(): Connection {
        return dataSource?.connection ?: throw IllegalStateException("Database not connected")
    }

    override fun createTables() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS offline_players (
                        username VARCHAR(16) PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        last_ip VARCHAR(45),
                        last_login TIMESTAMP WITH TIME ZONE,
                        registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                    )
                    """.trimIndent()
                )

                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS offline_sessions (
                        username VARCHAR(16) PRIMARY KEY,
                        ip VARCHAR(45) NOT NULL,
                        last_activity TIMESTAMP WITH TIME ZONE NOT NULL,
                        expired BOOLEAN NOT NULL DEFAULT FALSE,
                        FOREIGN KEY (username) REFERENCES offline_players(username)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    override fun registerPlayer(username: String, passwordHash: String, ip: String): Boolean {
        val sql = """
            INSERT INTO offline_players (username, password_hash, last_ip, last_login)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (username) DO NOTHING
        """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username.lowercase())
                stmt.setString(2, passwordHash)
                stmt.setString(3, ip)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun getPasswordHash(username: String): String? {
        val sql = "SELECT password_hash FROM offline_players WHERE username = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username.lowercase())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getString("password_hash")
                    }
                }
            }
        }
        return null
    }

    override fun isRegistered(username: String): Boolean {
        val sql = "SELECT 1 FROM offline_players WHERE username = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username.lowercase())
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
        return false
    }

    override fun updateLastLogin(username: String, ip: String) {
        val sql = "UPDATE offline_players SET last_ip = ?, last_login = NOW(), updated_at = NOW() WHERE username = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, ip)
                stmt.setString(2, username.lowercase())
                stmt.executeUpdate()
            }
        }
    }

    override fun updatePassword(username: String, newPasswordHash: String) {
        val sql = "UPDATE offline_players SET password_hash = ?, updated_at = NOW() WHERE username = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, newPasswordHash)
                stmt.setString(2, username.lowercase())
                stmt.executeUpdate()
            }
        }
    }

    override fun saveSession(username: String, ip: String) {
        val sql = """
            INSERT INTO offline_sessions (username, ip, last_activity, expired)
            VALUES (?, ?, NOW(), FALSE)
            ON CONFLICT (username) DO UPDATE SET
                ip = EXCLUDED.ip,
                last_activity = NOW(),
                expired = FALSE
        """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username.lowercase())
                stmt.setString(2, ip)
                stmt.executeUpdate()
            }
        }
    }

    override fun getSession(username: String): StoredSession? {
        val sql = "SELECT username, ip, last_activity, expired FROM offline_sessions WHERE username = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username.lowercase())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return StoredSession(
                            username = rs.getString("username"),
                            ip = rs.getString("ip"),
                            lastActivity = rs.getTimestamp("last_activity").toInstant(),
                            expired = rs.getBoolean("expired")
                        )
                    }
                }
            }
        }
        return null
    }

    override fun removeSession(username: String) {
        val sql = "DELETE FROM offline_sessions WHERE username = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username.lowercase())
                stmt.executeUpdate()
            }
        }
    }

    override fun cleanExpiredSessions(timeoutSeconds: Int) {
        val sql = """
            DELETE FROM offline_sessions 
            WHERE (EXTRACT(EPOCH FROM (NOW() - last_activity)) > ?) OR expired = TRUE
        """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, timeoutSeconds)
                val deleted = stmt.executeUpdate()
                if (deleted > 0) {
                    logger?.finest("Cleaned $deleted expired sessions")
                }
            }
        }
    }

    companion object {
        private val logger = java.util.logging.Logger.getLogger("OfflineSimpleLogin.PostgreSQL")
    }
}
