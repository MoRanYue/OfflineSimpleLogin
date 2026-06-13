package io.github.moranyue.offlinesimplelogin.command

import io.github.moranyue.offlinesimplelogin.OfflineSimpleLogin
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

/**
 * /logout
 *
 * Logs out from the current session. After logout, the player must login again.
 * Implements Paper 26.1.2 BasicCommand interface.
 */
class LogoutCommand(
    private val plugin: OfflineSimpleLogin
) : BasicCommand {

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

        try {
            plugin.authManager.logout(sender.name)
            sender.sendMessage(miniMessage.deserialize(
                plugin.pluginConfig.message("logout-success", "<green>Logged out successfully")
            ))
        } catch (e: Exception) {
            plugin.logger.severe("Logout command error for ${sender.name}: ${e.message}")
            sender.sendMessage(Component.text("§cAn error occurred: ${e.message}"))
        }
    }

    override fun permission(): String = "offlinesimplelogin.logout"

    override fun canUse(sender: org.bukkit.command.CommandSender): Boolean {
        return sender.hasPermission("offlinesimplelogin.logout")
    }
}
