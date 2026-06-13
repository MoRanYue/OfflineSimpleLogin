package io.github.moranyue.offlinesimplelogin.auth

import io.github.moranyue.offlinesimplelogin.config.Argon2Config
import io.github.moranyue.offlinesimplelogin.database.DatabaseManager

/**
 * Orchestrates the authentication flow: register, login, logout, change password.
 *
 * Results are returned as [AuthResult] which contains both a status code
 * and an optional message key for localization.
 */
class AuthManager(
    private val databaseManager: DatabaseManager,
    private val passwordHasher: PasswordHasher,
    private val sessionManager: SessionManager
) {

    /**
     * Register a new player account.
     *
     * @param username the player's username (case-insensitive)
     * @param password the plaintext password
     * @param ip the player's IP address
     * @return AuthResult indicating success or failure
     */
    fun register(username: String, password: String, ip: String): AuthResult {
        val normalized = normalize(username)

        // Validate username
        if (normalized.length < 1 || normalized.length > 16 || !normalized.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return AuthResult(AuthStatus.INVALID_USERNAME, "invalid-username")
        }

        // Validate password length
        if (password.length < 6) {
            return AuthResult(AuthStatus.PASSWORD_TOO_SHORT, "password-too-short")
        }

        // Check if already registered
        if (databaseManager.isRegistered(normalized)) {
            return AuthResult(AuthStatus.ALREADY_REGISTERED, "already-registered")
        }

        // Hash password and store
        val hash = passwordHasher.hash(password)
        val success = databaseManager.registerPlayer(normalized, hash, ip)

        if (!success) {
            return AuthResult(AuthStatus.ALREADY_REGISTERED, "already-registered")
        }

        // Auto-login after registration
        sessionManager.createSession(normalized, ip)

        return AuthResult(AuthStatus.SUCCESS, "register-success")
    }

    /**
     * Login to an existing account.
     *
     * @param username the player's username
     * @param password the plaintext password
     * @param ip the player's IP address
     * @return AuthResult indicating success or failure
     */
    fun login(username: String, password: String, ip: String): AuthResult {
        val normalized = normalize(username)

        // Check if registered
        if (!databaseManager.isRegistered(normalized)) {
            return AuthResult(AuthStatus.NOT_REGISTERED, "not-registered")
        }

        // Verify password
        val storedHash = databaseManager.getPasswordHash(normalized)
        if (storedHash == null || !passwordHasher.verify(password, storedHash)) {
            return AuthResult(AuthStatus.WRONG_PASSWORD, "login-failed")
        }

        // Create session
        sessionManager.createSession(normalized, ip)
        databaseManager.updateLastLogin(normalized, ip)

        return AuthResult(AuthStatus.SUCCESS, "login-success")
    }

    /**
     * Logout from an account.
     */
    fun logout(username: String): AuthResult {
        val normalized = normalize(username)
        sessionManager.removeSession(normalized)
        return AuthResult(AuthStatus.SUCCESS, "logout-success")
    }

    /**
     * Change password for an authenticated player.
     *
     * @param username the player's username
     * @param oldPassword the current password
     * @param newPassword the new password
     * @return AuthResult indicating success or failure
     */
    fun changePassword(username: String, oldPassword: String, newPassword: String): AuthResult {
        val normalized = normalize(username)

        // Validate new password
        if (newPassword.length < 6) {
            return AuthResult(AuthStatus.PASSWORD_TOO_SHORT, "password-too-short")
        }

        // Verify old password
        val storedHash = databaseManager.getPasswordHash(normalized)
        if (storedHash == null || !passwordHasher.verify(oldPassword, storedHash)) {
            return AuthResult(AuthStatus.WRONG_PASSWORD, "wrong-password")
        }

        // Check new password != old password
        if (oldPassword == newPassword) {
            return AuthResult(AuthStatus.SAME_PASSWORD, "same-password")
        }

        // Update password
        val newHash = passwordHasher.hash(newPassword)
        databaseManager.updatePassword(normalized, newHash)

        return AuthResult(AuthStatus.SUCCESS, "password-changed")
    }

    /**
     * Check if a player is currently authenticated with a valid session.
     */
    fun isAuthenticated(username: String, ip: String): Boolean {
        return sessionManager.isValidSession(normalize(username), ip)
    }

    /**
     * Get the authentication status for a player (without validating session).
     */
    fun getAuthenticationStatus(username: String): AuthStatus {
        val normalized = normalize(username)
        return if (databaseManager.isRegistered(normalized)) {
            AuthStatus.ALREADY_REGISTERED  // meaning registered
        } else {
            AuthStatus.NOT_REGISTERED
        }
    }

    private fun normalize(s: String): String = s.trim().lowercase()

    data class AuthResult(
        val status: AuthStatus,
        val messageKey: String
    )

    enum class AuthStatus {
        SUCCESS,
        ALREADY_REGISTERED,
        NOT_REGISTERED,
        WRONG_PASSWORD,
        PASSWORD_TOO_SHORT,
        SAME_PASSWORD,
        INVALID_USERNAME,
        SESSION_EXPIRED
    }
}
