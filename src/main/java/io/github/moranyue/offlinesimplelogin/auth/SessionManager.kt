package io.github.moranyue.offlinesimplelogin.auth

import io.github.moranyue.offlinesimplelogin.config.SessionConfig
import io.github.moranyue.offlinesimplelogin.database.DatabaseManager
import io.github.moranyue.offlinesimplelogin.database.StoredSession
import java.time.Instant

/**
 * Manages player authentication sessions.
 *
 * Sessions are persisted in the database and validated on each player action.
 * Supports IP-based auto-login for reconnecting players within the timeout window.
 */
class SessionManager(
    private val databaseManager: DatabaseManager,
    private val config: SessionConfig
) {

    /**
     * In-memory cache of active sessions for fast lookup.
     * Maps lowercase username -> session info.
     */
    private val activeSessions = mutableMapOf<String, SessionInfo>()

    /**
     * Create a new session for a player after successful login.
     * Persists to database (for offline-mode players who have a DB record).
     */
    fun createSession(username: String, ip: String) {
        val now = Instant.now()
        activeSessions[username.lowercase()] = SessionInfo(username.lowercase(), ip, now)
        databaseManager.saveSession(username, ip)
    }

    /**
     * Create a memory-only session for premium (online-mode) players.
     * Does NOT persist to database — premium players have no DB record.
     */
    fun createMemorySession(username: String, ip: String) {
        val now = Instant.now()
        activeSessions[username.lowercase()] = SessionInfo(username.lowercase(), ip, now)
    }

    /**
     * Remove a player's session on logout.
     */
    fun removeSession(username: String) {
        activeSessions.remove(username.lowercase())
        databaseManager.removeSession(username)
    }

    /**
     * Check if a player has a valid session.
     *
     * @param username the player's username
     * @param ip the player's current IP address
     * @return true if the player has a valid, non-expired session
     */
    fun isValidSession(username: String, ip: String): Boolean {
        val key = username.lowercase()

        // Check in-memory cache first
        val cached = activeSessions[key]
        if (cached != null) {
            if (!isExpired(cached.lastActivity)) {
                // Update last activity
                cached.lastActivity = Instant.now()
                return true
            } else {
                // Session expired in cache
                activeSessions.remove(key)
                return false
            }
        }

        // Fall back to database check (for server restarts)
        val stored = databaseManager.getSession(username)
        if (stored != null && !stored.expired && !isExpired(stored.lastActivity)) {
            // Check IP if auto-login is enabled
            if (config.ipAutoLogin && stored.ip == ip) {
                // Restore to cache
                activeSessions[key] = SessionInfo(key, ip, Instant.now())
                databaseManager.saveSession(username, ip)
                return true
            }
        }

        return false
    }

    /**
     * Check if a stored session from the database is still valid.
     * Used for IP auto-login on join.
     */
    fun isValidStoredSession(session: StoredSession, currentIp: String): Boolean {
        if (session.expired || isExpired(session.lastActivity)) {
            return false
        }
        return config.ipAutoLogin && session.ip == currentIp
    }

    /**
     * Clean up expired sessions from both cache and database.
     */
    fun cleanExpiredSessions() {
        val now = Instant.now()
        activeSessions.entries.removeAll { (_, session) ->
            isExpired(session.lastActivity)
        }
        databaseManager.cleanExpiredSessions(config.timeout)
    }

    /**
     * Check if a last-activity timestamp is expired relative to the configured timeout.
     */
    private fun isExpired(lastActivity: Instant): Boolean {
        return Instant.now().epochSecond - lastActivity.epochSecond > config.timeout
    }

    /**
     * In-memory session information.
     */
    class SessionInfo(
        val username: String,
        val ip: String,
        var lastActivity: Instant
    )
}
