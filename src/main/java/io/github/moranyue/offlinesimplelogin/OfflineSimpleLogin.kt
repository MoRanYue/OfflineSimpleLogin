package io.github.moranyue.offlinesimplelogin

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.moranyue.offlinesimplelogin.auth.AuthManager
import io.github.moranyue.offlinesimplelogin.auth.PasswordHasher
import io.github.moranyue.offlinesimplelogin.auth.SessionManager
import io.github.moranyue.offlinesimplelogin.config.PluginConfig
import io.github.moranyue.offlinesimplelogin.database.DatabaseManager
import io.github.moranyue.offlinesimplelogin.database.PostgreSQLDatabaseManager
import io.github.moranyue.offlinesimplelogin.database.SQLiteDatabaseManager
import io.github.moranyue.offlinesimplelogin.listener.PlayerLoginListener
import io.github.moranyue.offlinesimplelogin.listener.PlayerRestrictionListener
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * OfflineSimpleLogin — Folia-compatible offline-mode player registration and login plugin.
 *
 * Designed to work alongside LimitedOfflineModePaper:
 * - LimitedOfflineModePaper handles Netty-level interception (allows offline players to join)
 * - OfflineSimpleLogin handles in-game registration and login authentication
 *
 * Supports both SQLite and PostgreSQL databases with Argon2id password hashing.
 *
 * Commands are registered using Paper 26.1.2's Brigadier-based LifecycleEventManager API.
 */
class OfflineSimpleLogin : JavaPlugin() {

    // ── Core components (initialized in onEnable) ───────────────────────────

    lateinit var pluginConfig: PluginConfig
        private set
    lateinit var databaseManager: DatabaseManager
        private set
    lateinit var passwordHasher: PasswordHasher
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var authManager: AuthManager
        private set

    val miniMessage: MiniMessage = MiniMessage.miniMessage()

    private var cleanupTaskId: Int? = null

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onEnable() {
        val startTime = System.currentTimeMillis()

        // 1. Load configuration
        pluginConfig = PluginConfig.load(this)
        logger.info("Configuration loaded")

        // 2. Initialize database
        databaseManager = createDatabaseManager()
        try {
            databaseManager.connect()
            databaseManager.createTables()
            logger.info("Database initialized (${pluginConfig.database.type})")
        } catch (e: Exception) {
            logger.severe("Failed to initialize database: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 3. Initialize authentication components
        passwordHasher = PasswordHasher(pluginConfig.argon2)
        sessionManager = SessionManager(databaseManager, pluginConfig.session)
        authManager = AuthManager(databaseManager, passwordHasher, sessionManager)

        // 4. Register event listeners
        server.pluginManager.registerEvents(PlayerLoginListener(this), this)
        server.pluginManager.registerEvents(PlayerRestrictionListener(this), this)
        logger.info("Listeners registered")

        // 5. Register commands via Paper 26.1.2 LifecycleEvents.COMMANDS API
        // Using proper Brigadier command trees with declared argument nodes
        // so the client accepts input with arguments.
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands: Commands = event.registrar()

            // /register <password> <confirm>  — two word arguments
            commands.register(
                Commands.literal("register")
                    .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirm", StringArgumentType.word())
                            .executes { ctx -> executeRegister(ctx.source, ctx) }
                        )
                    )
                    .build(),
                "Register a new account",
                listOf("reg")
            )

            // /login <password> — one word argument
            commands.register(
                Commands.literal("login")
                    .then(Commands.argument("password", StringArgumentType.word())
                        .executes { ctx -> executeLogin(ctx.source, ctx) }
                    )
                    .build(),
                "Login to your account",
                listOf("l")
            )

            // /logout — no arguments
            commands.register(
                Commands.literal("logout").executes { ctx ->
                    executeLogout(ctx.source)
                    1
                }.build(),
                "Logout from your account",
                emptyList()
            )

            // /changepassword <old> <new> — two word arguments
            commands.register(
                Commands.literal("changepassword")
                    .then(Commands.argument("old", StringArgumentType.word())
                        .then(Commands.argument("new", StringArgumentType.word())
                            .executes { ctx -> executeChangePassword(ctx.source, ctx) }
                        )
                    )
                    .build(),
                "Change your password",
                listOf("changepw", "cpw")
            )

            logger.info("All commands registered")
        }

        // 6. Schedule periodic session cleanup (every 30 seconds)
        if (isFoliaServer()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                this,
                { _ -> sessionManager.cleanExpiredSessions() },
                600L,
                600L
            )
        } else {
            val task = server.scheduler.runTaskTimerAsynchronously(
                this,
                Runnable { sessionManager.cleanExpiredSessions() },
                600L,
                600L
            )
            cleanupTaskId = task.taskId
        }

        // 7. Detect LimitedOfflineModePaper for cooperative logging
        val lomPlugin = server.pluginManager.getPlugin("LimitedOfflineMode")
        if (lomPlugin != null && lomPlugin.isEnabled) {
            logger.info("LimitedOfflineModePaper v${lomPlugin.description.version} detected — operating in cooperative mode")
        } else {
            logger.info("LimitedOfflineModePaper not detected — standalone mode")
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("OfflineSimpleLogin v${description.version} enabled (${elapsed}ms)")
    }

    override fun onDisable() {
        if (!isFoliaServer()) {
            cleanupTaskId?.let { server.scheduler.cancelTask(it) }
        }
        try {
            databaseManager.close()
        } catch (e: Exception) {
            logger.warning("Error closing database: ${e.message}")
        }
        logger.info("OfflineSimpleLogin disabled")
    }

    // ── Premium player check ──────────────────────────────────────────────

    /** Checks if a player authenticated via Mojang (online-mode). */
    private fun isPremiumPlayer(player: Player): Boolean {
        return player.playerProfile.properties.any { it.name == "textures" }
    }

    /** Rejects the command if the sender is a premium player. Returns true if rejected. */
    private fun rejectPremium(sender: org.bukkit.command.CommandSender): Boolean {
        if (sender is Player && isPremiumPlayer(sender)) {
            sender.sendMessage(miniMessage.deserialize(
                pluginConfig.message("premium-rejected", "<red>Premium players do not need to use this command")
            ))
            return true
        }
        return false
    }

    // ── Command executors ──────────────────────────────────────────────────

    /**
     * All /register, /login, and /changepassword commands execute Argon2id
     * password hashing/verification which is intentionally CPU-intensive
     * (default: 64MB memory, 3 iterations, ~1-3 seconds per call).
     *
     * To avoid blocking the server's main/global region thread, all
     * password operations are offloaded to the async scheduler.
     * The result is then scheduled back to the global region thread
     * for sending messages to the player.
     */

    private fun executeRegister(source: CommandSourceStack, ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val sender = source.sender
        if (sender !is Player) {
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("player-only")))
            return 1
        }
        if (rejectPremium(sender)) return 1
        val password = ctx.getArgument("password", String::class.java)
        val confirm = ctx.getArgument("confirm", String::class.java)
        if (password != confirm) {
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("passwords-do-not-match", "<red>Passwords do not match")))
            return 1
        }
        val ip = sender.address?.hostString ?: "unknown"
        val playerName = sender.name
        // ⚡ Run Argon2 hash on async thread to avoid blocking the server
        scheduleAsyncTask {
            try {
                val result = authManager.register(playerName, password, ip)
                scheduleGlobalTask {
                    val msg = when (result.status) {
                        AuthManager.AuthStatus.SUCCESS -> pluginConfig.message("register-success", "<green>Registration successful!")
                        AuthManager.AuthStatus.ALREADY_REGISTERED -> pluginConfig.message("already-registered", "<red>Already registered")
                        AuthManager.AuthStatus.PASSWORD_TOO_SHORT -> pluginConfig.message("password-too-short", "<red>Password too short")
                        AuthManager.AuthStatus.INVALID_USERNAME -> pluginConfig.message("invalid-username", "<red>Invalid username")
                        else -> pluginConfig.message("register-success", "<green>Registration successful!")
                    }
                    sender.sendMessage(miniMessage.deserialize(msg))
                }
            } catch (e: Exception) {
                logger.severe("Register error: ${e.message}")
                scheduleGlobalTask {
                    sender.sendMessage(miniMessage.deserialize(
                        pluginConfig.message("error-occurred", "<red>An error occurred").replace("{message}", e.message ?: "unknown")
                    ))
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun executeLogin(source: CommandSourceStack, ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val sender = source.sender
        if (sender !is Player) {
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("player-only")))
            return 1
        }
        if (rejectPremium(sender)) return 1
        val password = ctx.getArgument("password", String::class.java)
        val ip = sender.address?.hostString ?: "unknown"
        if (authManager.isAuthenticated(sender.name, ip)) {
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("already-logged-in", "<green>Already logged in")))
            return 1
        }
        val playerName = sender.name
        // ⚡ Run Argon2 verification on async thread to avoid blocking the server
        scheduleAsyncTask {
            try {
                val result = authManager.login(playerName, password, ip)
                scheduleGlobalTask {
                    val msg = when (result.status) {
                        AuthManager.AuthStatus.SUCCESS -> pluginConfig.message("login-success", "<green>Login successful!")
                        AuthManager.AuthStatus.NOT_REGISTERED -> pluginConfig.message("not-registered", "<red>Not registered")
                        AuthManager.AuthStatus.WRONG_PASSWORD -> pluginConfig.message("login-failed", "<red>Wrong password")
                        else -> pluginConfig.message("login-failed", "<red>Login failed")
                    }
                    sender.sendMessage(miniMessage.deserialize(msg))
                }
            } catch (e: Exception) {
                logger.severe("Login error: ${e.message}")
                scheduleGlobalTask {
                    sender.sendMessage(miniMessage.deserialize(
                        pluginConfig.message("error-occurred", "<red>An error occurred").replace("{message}", e.message ?: "unknown")
                    ))
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun executeLogout(source: CommandSourceStack): Int {
        val sender = source.sender
        if (sender !is Player) {
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("player-only")))
            return 1
        }
        if (rejectPremium(sender)) return 1
        try {
            authManager.logout(sender.name)
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("logout-success", "<green>Logged out successfully")))
        } catch (e: Exception) {
            logger.severe("Logout error: ${e.message}")
            sender.sendMessage(miniMessage.deserialize(
                pluginConfig.message("error-occurred", "<red>An error occurred").replace("{message}", e.message ?: "unknown")
            ))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun executeChangePassword(source: CommandSourceStack, ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val sender = source.sender
        if (sender !is Player) {
            sender.sendMessage(miniMessage.deserialize(pluginConfig.message("player-only")))
            return 1
        }
        if (rejectPremium(sender)) return 1
        val oldPassword = ctx.getArgument("old", String::class.java)
        val newPassword = ctx.getArgument("new", String::class.java)
        val playerName = sender.name
        // ⚡ Run Argon2 operations on async thread to avoid blocking the server
        scheduleAsyncTask {
            try {
                val result = authManager.changePassword(playerName, oldPassword, newPassword)
                scheduleGlobalTask {
                    val msg = when (result.status) {
                        AuthManager.AuthStatus.SUCCESS -> pluginConfig.message("password-changed", "<green>Password changed!")
                        AuthManager.AuthStatus.WRONG_PASSWORD -> pluginConfig.message("wrong-password", "<red>Current password is incorrect")
                        AuthManager.AuthStatus.PASSWORD_TOO_SHORT -> pluginConfig.message("password-too-short", "<red>Password too short")
                        AuthManager.AuthStatus.SAME_PASSWORD -> pluginConfig.message("same-password", "<red>New password cannot be the same as old")
                        else -> pluginConfig.message("wrong-password", "<red>Password change failed")
                    }
                    sender.sendMessage(miniMessage.deserialize(msg))
                }
            } catch (e: Exception) {
                logger.severe("ChangePassword error: ${e.message}")
                scheduleGlobalTask {
                    sender.sendMessage(miniMessage.deserialize(
                        pluginConfig.message("error-occurred", "<red>An error occurred").replace("{message}", e.message ?: "unknown")
                    ))
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    // ── Database factory ────────────────────────────────────────────────────

    private fun createDatabaseManager(): DatabaseManager {
        return when (pluginConfig.database.type.lowercase()) {
            "sqlite" -> SQLiteDatabaseManager(pluginConfig.database.sqlite, dataFolder.toPath())
            "postgresql" -> PostgreSQLDatabaseManager(pluginConfig.database.postgresql)
            else -> {
                logger.warning("Unknown database type '${pluginConfig.database.type}', falling back to SQLite")
                SQLiteDatabaseManager(pluginConfig.database.sqlite, dataFolder.toPath())
            }
        }
    }

    // ── Folia detection ─────────────────────────────────────────────────────

    fun isFoliaServer(): Boolean {
        return try {
            Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    // ── Folia-compatible scheduling helpers ─────────────────────────────────

    fun scheduleGlobalTask(runnable: Runnable) {
        if (isFoliaServer()) {
            Bukkit.getGlobalRegionScheduler().run(this) { runnable.run() }
        } else {
            server.scheduler.runTask(this, runnable)
        }
    }

    fun scheduleGlobalTaskLater(runnable: Runnable, delayTicks: Long) {
        if (isFoliaServer()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(this, { runnable.run() }, delayTicks)
        } else {
            server.scheduler.runTaskLater(this, runnable, delayTicks)
        }
    }

    fun scheduleAsyncTask(runnable: Runnable) {
        if (isFoliaServer()) {
            Bukkit.getAsyncScheduler().runNow(this) { runnable.run() }
        } else {
            server.scheduler.runTaskAsynchronously(this, runnable)
        }
    }
}
