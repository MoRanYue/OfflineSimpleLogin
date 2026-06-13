package io.github.moranyue.offlinesimplelogin.database

import java.time.Instant

/**
 * Represents a player session stored in the database.
 */
data class StoredSession(
    val username: String,
    val ip: String,
    val lastActivity: Instant,
    val expired: Boolean = false
)

/**
 * Database abstraction layer for player accounts and sessions.
 *
 * Implementations: SQLiteDatabaseManager, PostgreSQLDatabaseManager
 */
interface DatabaseManager : AutoCloseable {

    /**
     * Initialize the database connection.
     */
    fun connect()

    /**
     * Close the database connection and release resources.
     */
    override fun close()

    /**
     * Create the required database tables if they don't exist.
     */
    fun createTables()

    /**
     * Register a new player account.
     *
     * @param username the player's username
     * @param passwordHash the Argon2 password hash
     * @param ip the player's IP address
     * @return true if registration was successful, false if the username already exists
     */
    fun registerPlayer(username: String, passwordHash: String, ip: String): Boolean

    /**
     * Get the stored password hash for a player.
     *
     * @param username the player's username
     * @return the password hash, or null if the player is not registered
     */
    fun getPasswordHash(username: String): String?

    /**
     * Check if a player is registered.
     */
    fun isRegistered(username: String): Boolean

    /**
     * Update the last login timestamp and IP for a player.
     */
    fun updateLastLogin(username: String, ip: String)

    /**
     * Update a player's password hash.
     *
     * @param username the player's username
     * @param newPasswordHash the new Argon2 password hash
     */
    fun updatePassword(username: String, newPasswordHash: String)

    /**
     * Save a new login session for a player.
     */
    fun saveSession(username: String, ip: String)

    /**
     * Get the stored session for a player.
     */
    fun getSession(username: String): StoredSession?

    /**
     * Remove a player's session (on logout or expiration).
     */
    fun removeSession(username: String)

    /**
     * Clean up all expired sessions.
     *
     * @param timeoutSeconds session timeout in seconds
     */
    fun cleanExpiredSessions(timeoutSeconds: Int)
}
