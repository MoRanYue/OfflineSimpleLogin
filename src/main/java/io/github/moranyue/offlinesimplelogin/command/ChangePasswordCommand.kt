package io.github.moranyue.offlinesimplelogin.command

import io.github.moranyue.offlinesimplelogin.OfflineSimpleLogin
import io.github.moranyue.offlinesimplelogin.auth.AuthManager
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * /changepassword <old-password> <new-password>
 *
 * Changes the password for an authenticated player.
 * Implements Paper 26.1.2 BasicCommand interface.
 */
class ChangePasswordCommand(
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
            sender.sendMessage(Component.text("§cUsage: /changepassword <old-password> <new-password>"))
            return
        }

        val oldPassword = args[0]
        val newPassword = args[1]

        try {
            val result = authManager.changePassword(sender.name, oldPassword, newPassword)
            val message = when (result.status) {
                AuthManager.AuthStatus.SUCCESS ->
                    plugin.pluginConfig.message("password-changed", "<green>Password changed!")
                AuthManager.AuthStatus.WRONG_PASSWORD ->
                    plugin.pluginConfig.message("wrong-password", "<red>Current password is incorrect")
                AuthManager.AuthStatus.PASSWORD_TOO_SHORT ->
                    plugin.pluginConfig.message("password-too-short", "<red>Password too short")
                AuthManager.AuthStatus.SAME_PASSWORD ->
                    plugin.pluginConfig.message("same-password", "<red>New password cannot be the same as old")
                else ->
                    plugin.pluginConfig.message("wrong-password", "<red>Password change failed")
            }
            sender.sendMessage(miniMessage.deserialize(message))
        } catch (e: Exception) {
            plugin.logger.severe("ChangePassword command error for ${sender.name}: ${e.message}")
            sender.sendMessage(Component.text("§cAn error occurred: ${e.message}"))
        }
    }

    override fun permission(): String = "offlinesimplelogin.changepassword"

    override fun canUse(sender: org.bukkit.command.CommandSender): Boolean {
        return sender.hasPermission("offlinesimplelogin.changepassword")
    }
}
