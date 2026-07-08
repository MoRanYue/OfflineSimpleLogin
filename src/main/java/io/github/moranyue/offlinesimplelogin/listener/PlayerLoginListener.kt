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
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/**
 * Listens for player join/quit events and manages authentication state.
 *
 * On join:
 * - Premium (online-mode) players are silently authenticated — no prompt.
 * - Offline-mode players with valid stored session (IP auto-login) are silently authenticated.
 * - Offline-mode players registered but not logged in are shown the login prompt.
 * - Offline-mode players not registered are shown the registration prompt.
 *
 * Premium detection: compares the player's UUID against the computed offline-mode UUID
 * (UUID.nameUUIDFromBytes("OfflinePlayer:" + username)). If they match, the player is
 * offline-mode. Premium (Mojang-authenticated) players have a different UUID from Mojang.
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
     * Compares the player's UUID against the computed offline-mode UUID:
     *   offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:" + playerName)
     *
     * If the player's UUID matches the offline UUID, the player joined in offline mode.
     * Premium (Mojang-authenticated) players have a UUID from Mojang's auth server,
     * which will NOT match the offline UUID.
     *
     * @return true if the player is premium (online-mode), false if offline-mode
     */
    fun isPremiumPlayer(player: Player): Boolean {
        val offlineUuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + player.name).toByteArray(StandardCharsets.UTF_8)
        )
        return player.uniqueId != offlineUuid
    }

    private fun showLoginPrompt(player: Player) {
        val messages = plugin.pluginConfig.messages
        val config = plugin.pluginConfig.restrictions

        player.sendMessage(plugin.miniMessage.deserialize(
            messages["login-prompt"] ?: "<red>Please /login <password>"
        ))

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

        if (config.showTitle) {
            player.showTitle(Title.title(
                plugin.miniMessage.deserialize(messages["register-title"] ?: "<red>Please Register"),
                plugin.miniMessage.deserialize(messages["register-subtitle"] ?: "<gray>/register <password> <confirm>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
        }
    }
}
