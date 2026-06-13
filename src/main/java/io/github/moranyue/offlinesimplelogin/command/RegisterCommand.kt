package io.github.moranyue.offlinesimplelogin.command

import io.github.moranyue.offlinesimplelogin.OfflineSimpleLogin
import io.github.moranyue.offlinesimplelogin.auth.AuthManager
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * /register <password> <confirm>
 *
 * Registers a new account. Requires the player to be unregistered.
 * Auto-login after successful registration.
 *
 * Implements Paper 26.1.2 BasicCommand interface.
 */
class RegisterCommand(
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

        if (args.size < 2) {
            sender.sendMessage(Component.text("§cUsage: /register <password> <confirm>"))
            return
        }

        val password = args[0]
        val confirm = args[1]

        if (password != confirm) {
            sender.sendMessage(miniMessage.deserialize(
                plugin.pluginConfig.message("passwords-do-not-match", "<red>Passwords do not match")
            ))
            return
        }

        val ip = sender.address?.hostString ?: "unknown"

        try {
            val result = authManager.register(sender.name, password, ip)
            val message = when (result.status) {
                AuthManager.AuthStatus.SUCCESS ->
                    plugin.pluginConfig.message("register-success", "<green>Registration successful!")
                AuthManager.AuthStatus.ALREADY_REGISTERED ->
                    plugin.pluginConfig.message("already-registered", "<red>Already registered")
                AuthManager.AuthStatus.PASSWORD_TOO_SHORT ->
                    plugin.pluginConfig.message("password-too-short", "<red>Password too short")
                AuthManager.AuthStatus.INVALID_USERNAME ->
                    plugin.pluginConfig.message("invalid-username", "<red>Invalid username")
                else ->
                    plugin.pluginConfig.message("register-success", "<green>Registration successful!")
            }
            sender.sendMessage(miniMessage.deserialize(message))
        } catch (e: Exception) {
            plugin.logger.severe("Register command error for ${sender.name}: ${e.message}")
            sender.sendMessage(Component.text("§cAn error occurred: ${e.message}"))
        }
    }

    override fun permission(): String = "offlinesimplelogin.register"

    override fun canUse(sender: org.bukkit.command.CommandSender): Boolean {
        return sender.hasPermission("offlinesimplelogin.register")
    }
}
