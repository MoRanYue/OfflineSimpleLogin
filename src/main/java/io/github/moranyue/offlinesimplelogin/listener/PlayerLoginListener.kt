package io.github.moranyue.offlinesimplelogin.listener

import io.github.moranyue.offlinesimplelogin.OfflineSimpleLogin
import io.github.moranyue.offlinesimplelogin.auth.AuthManager
import io.github.moranyue.offlinesimplelogin.database.DatabaseManager
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.time.Duration

/**
 * Listens for player join/quit events and manages authentication state.
 *
 * On join:
 * - Premium (online-mode) players are silently authenticated — no prompt.
 * - Offline-mode players with valid stored session (IP auto-login) are silently authenticated.
 * - Offline-mode players registered but not logged in are shown the login prompt.
 * - Offline-mode players not registered are shown the registration prompt.
 *
 * Premium detection: checks for the "textures" property in the player's GameProfile,
 * which is only present for Mojang-authenticated (online-mode) accounts.
 *
 * On quit:
 * - In-memory session is preserved for IP auto-login on reconnect.
 */
class PlayerLoginListener(
    private val plugin: OfflineSimpleLogin
) : Listener {

    private val authManager: AuthManager
        get() = plugin.authManager
    private val databaseManager: DatabaseManager
        get() = plugin.databaseManager

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val username = player.name
        val ip = player.address?.hostString ?: "unknown"

        // ── Premium player (Mojang-authenticated) — skip auth entirely ────────
        // Use memory-only session — premium players have no DB record, so we
        // must NOT call databaseManager.saveSession() (would hit FK constraint).
        if (isPremiumPlayer(player)) {
            plugin.sessionManager.createMemorySession(username, ip)
            plugin.logger.finest("Premium player $username auto-authenticated (memory only)")
            return
        }

        // ── Offline-mode player — check session or prompt ────────────────────
        plugin.scheduleAsyncTask {
            // Check for valid stored session (IP auto-login)
            val session = databaseManager.getSession(username)
            if (session != null && plugin.sessionManager.isValidStoredSession(session, ip)) {
                plugin.sessionManager.createSession(username, ip)
                plugin.logger.finest("Player $username auto-authenticated via IP session")
                return@scheduleAsyncTask
            }

            // Not authenticated — show appropriate prompt
            plugin.scheduleGlobalTask {
                if (databaseManager.isRegistered(username)) {
                    showLoginPrompt(player)
                } else {
                    showRegisterPrompt(player)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Session remains in database for IP auto-login on reconnect
        // In-memory cache is preserved until timeout
    }

    /**
     * Detects whether a player authenticated through Mojang's servers (premium).
     *
     * Checks for the "textures" property in the GameProfile, which is only
     * present for Mojang-authenticated accounts. Offline-mode players injected
     * by LimitedOfflineModePaper lack this property.
     */
    fun isPremiumPlayer(player: Player): Boolean {
        return player.playerProfile.properties.any { it.name == "textures" }
    }

    private fun showLoginPrompt(player: Player) {
        val messages = plugin.pluginConfig.messages
        val config = plugin.pluginConfig.restrictions

        player.sendMessage(plugin.miniMessage.deserialize(
            messages["login-prompt"] ?: "<red>Please /login <password>"
        ))

        // Show/Hide Title based on config
        if (config.showTitle) {
            player.showTitle(Title.title(
                plugin.miniMessage.deserialize(messages["login-title"] ?: "<red>Please Login"),
                plugin.miniMessage.deserialize(messages["login-subtitle"] ?: "<gray>/login <password>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
        }
    }

    private fun showRegisterPrompt(player: Player) {
        val messages = plugin.pluginConfig.messages
        val config = plugin.pluginConfig.restrictions

        player.sendMessage(plugin.miniMessage.deserialize(
            messages["register-prompt"] ?: "<red>Please /register <password> <confirm>"
        ))

        // Show/Hide Title based on config
        if (config.showTitle) {
            player.showTitle(Title.title(
                plugin.miniMessage.deserialize(messages["register-title"] ?: "<red>Please Register"),
                plugin.miniMessage.deserialize(messages["register-subtitle"] ?: "<gray>/register <password> <confirm>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
        }
    }
}
