package io.github.moranyue.offlinesimplelogin.command

import io.github.moranyue.offlinesimplelogin.OfflineSimpleLogin
import io.github.moranyue.offlinesimplelogin.auth.AuthManager
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * /login <password>
 *
 * Logs in to an existing account. Requires the player to be registered.
 * Implements Paper 26.1.2 BasicCommand interface.
 */
class LoginCommand(
    private val plugin: OfflineSimpleLogin
) : BasicCommand {

    private val authManager: AuthManager
        get() = plugin.authManager
    private val miniMessage: MiniMessage
        get() = plugin.miniMessage

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        val sender = source.sender
        if (sender !is Player) {
            sender.sendMessage(miniMessage.deserialize(
                plugin.pluginConfig.message("player-only", "<red>This command can only be used by players")
            ))
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("§cUsage: /login <password>"))
            return
        }

        val password = args[0]
        val ip = sender.address?.hostString ?: "unknown"

        // Check if already authenticated
        if (authManager.isAuthenticated(sender.name, ip)) {
            sender.sendMessage(miniMessage.deserialize(
                plugin.pluginConfig.message("already-logged-in", "<green>You are already logged in")
            ))
            return
        }

        try {
            val result = authManager.login(sender.name, password, ip)
            val message = when (result.status) {
                AuthManager.AuthStatus.SUCCESS ->
                    plugin.pluginConfig.message("login-success", "<green>Login successful!")
                AuthManager.AuthStatus.NOT_REGISTERED ->
                    plugin.pluginConfig.message("not-registered", "<red>Not registered")
                AuthManager.AuthStatus.WRONG_PASSWORD ->
                    plugin.pluginConfig.message("login-failed", "<red>Wrong password")
                else ->
                    plugin.pluginConfig.message("login-failed", "<red>Login failed")
            }
            sender.sendMessage(miniMessage.deserialize(message))
        } catch (e: Exception) {
            plugin.logger.severe("Login command error for ${sender.name}: ${e.message}")
            sender.sendMessage(Component.text("§cAn error occurred: ${e.message}"))
        }
    }

    override fun permission(): String = "offlinesimplelogin.login"

    override fun canUse(sender: org.bukkit.command.CommandSender): Boolean {
        return sender.hasPermission("offlinesimplelogin.login")
    }
}
