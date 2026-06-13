package io.github.moranyue.offlinesimplelogin.listener

import io.github.moranyue.offlinesimplelogin.OfflineSimpleLogin
import io.github.moranyue.offlinesimplelogin.config.RestrictionsConfig
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

/**
 * Restricts unauthenticated offline-mode players from performing various actions.
 *
 * Premium (online-mode) players are always exempt from restrictions.
 * All checks are fast in-memory lookups via SessionManager.
 * Configurable via the restrictions section in config.yml.
 */
class PlayerRestrictionListener(
    private val plugin: OfflineSimpleLogin
) : Listener {

    private val authManager get() = plugin.authManager
    private val config: RestrictionsConfig get() = plugin.pluginConfig.restrictions
    private val loginListener = PlayerLoginListener(plugin)

    /**
     * Allowed commands that unauthenticated players can use.
     */
    private val allowedCommands = listOf(
        "/register", "/reg",
        "/login", "/l",
        "/logout",
        "/changepassword", "/changepw", "/cpw"
    )

    /**
     * Check if a player is either:
     * 1. A premium (Mojang-authenticated) player — always authenticated
     * 2. An offline-mode player with a valid session
     */
    private fun isAuthenticated(player: Player): Boolean {
        // Premium players are always authenticated
        if (loginListener.isPremiumPlayer(player)) return true

        // Offline-mode players need a valid session
        val ip = player.address?.hostString ?: "unknown"
        return authManager.isAuthenticated(player.name, ip)
    }

    // ── Movement ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!config.preventMovement) return
        val player = event.player
        if (isAuthenticated(player)) return
        val from = event.from
        val to = event.to ?: return
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            event.isCancelled = true
        }
    }

    // ── Interaction ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!config.preventInteraction) return
        if (!isAuthenticated(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!config.preventInteraction) return
        if (!isAuthenticated(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!config.preventInteraction) return
        if (!isAuthenticated(event.player)) event.isCancelled = true
    }

    // ── Inventory ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!config.preventInventory) return
        val who = event.whoClicked
        if (who is Player && !isAuthenticated(who)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (!config.preventInventory) return
        val who = event.whoClicked
        if (who is Player && !isAuthenticated(who)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (!config.preventInventory) return
        val player = event.player
        if (player is Player && !isAuthenticated(player)) event.isCancelled = true
    }

    // ── Chat ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (!config.preventChat) return
        if (!isAuthenticated(event.player)) event.isCancelled = true
    }

    // ── Damage ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        if (!config.preventDamage) return
        val entity = event.entity
        if (entity is Player && !isAuthenticated(entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerDealDamage(event: EntityDamageByEntityEvent) {
        if (!config.preventDealingDamage) return
        val damager = event.damager
        if (damager is Player && !isAuthenticated(damager)) event.isCancelled = true
    }

    // ── Commands ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (isAuthenticated(player)) return
        val command = event.message.split(" ")[0].lowercase()
        if (command !in allowedCommands) {
            event.isCancelled = true
        }
    }
}
