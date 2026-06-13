package io.github.moranyue.offlinesimplelogin.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * Plugin configuration loaded from config.yml.
 */
data class PluginConfig(
    val database: DatabaseConfig,
    val argon2: Argon2Config,
    val session: SessionConfig,
    val restrictions: RestrictionsConfig,
    val messages: Map<String, String>
) {
    companion object {
        fun load(plugin: JavaPlugin): PluginConfig {
            plugin.saveDefaultConfig()
            plugin.reloadConfig()
            val config = plugin.config

            return PluginConfig(
                database = DatabaseConfig.load(config),
                argon2 = Argon2Config.load(config),
                session = SessionConfig.load(config),
                restrictions = RestrictionsConfig.load(config),
                messages = loadMessages(config)
            )
        }

        private fun loadMessages(config: FileConfiguration): Map<String, String> {
            val messages = mutableMapOf<String, String>()
            val section = config.getConfigurationSection("messages") ?: return messages
            for (key in section.getKeys(false)) {
                section.getString(key)?.let { messages[key] = it }
            }
            return messages
        }
    }

    fun message(key: String, default: String = ""): String {
        return messages[key] ?: default
    }
}

data class DatabaseConfig(
    val type: String,
    val sqlite: SQLiteConfig,
    val postgresql: PostgreSQLConfig
) {
    companion object {
        fun load(config: FileConfiguration): DatabaseConfig {
            val db = config.getConfigurationSection("database") ?: error("Missing 'database' section in config.yml")
            return DatabaseConfig(
                type = db.getString("type", "sqlite")!!,
                sqlite = SQLiteConfig.load(db.getConfigurationSection("sqlite")!!),
                postgresql = PostgreSQLConfig.load(db.getConfigurationSection("postgresql")!!)
            )
        }
    }
}

data class SQLiteConfig(
    val file: String
) {
    companion object {
        fun load(config: org.bukkit.configuration.ConfigurationSection): SQLiteConfig {
            return SQLiteConfig(
                file = config.getString("file", "data/offline-simple-login.db")!!
            )
        }
    }
}

data class PostgreSQLConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val poolSize: Int,
    val connectionTimeout: Long,
    val maxLifetime: Long
) {
    companion object {
        fun load(config: org.bukkit.configuration.ConfigurationSection): PostgreSQLConfig {
            return PostgreSQLConfig(
                host = config.getString("host", "localhost")!!,
                port = config.getInt("port", 5432),
                database = config.getString("database", "offlinesimplelogin")!!,
                username = config.getString("username", "minecraft")!!,
                password = config.getString("password", "")!!,
                poolSize = config.getInt("pool-size", 10),
                connectionTimeout = config.getLong("connection-timeout", 5000),
                maxLifetime = config.getLong("max-lifetime", 1800000)
            )
        }
    }
}

data class Argon2Config(
    val saltLength: Int,
    val hashLength: Int,
    val parallelism: Int,
    val memory: Int,
    val iterations: Int
) {
    companion object {
        fun load(config: FileConfiguration): Argon2Config {
            val a = config.getConfigurationSection("argon2") ?: error("Missing 'argon2' section in config.yml")
            return Argon2Config(
                saltLength = a.getInt("salt-length", 16),
                hashLength = a.getInt("hash-length", 32),
                parallelism = a.getInt("parallelism", 1),
                memory = a.getInt("memory", 65536),
                iterations = a.getInt("iterations", 3)
            )
        }
    }
}

data class SessionConfig(
    val timeout: Int,
    val ipAutoLogin: Boolean
) {
    companion object {
        fun load(config: FileConfiguration): SessionConfig {
            val s = config.getConfigurationSection("session") ?: error("Missing 'session' section in config.yml")
            return SessionConfig(
                timeout = s.getInt("timeout", 1800),
                ipAutoLogin = s.getBoolean("ip-auto-login", true)
            )
        }
    }
}

data class RestrictionsConfig(
    val showTitle: Boolean,
    val preventMovement: Boolean,
    val preventInteraction: Boolean,
    val preventInventory: Boolean,
    val preventChat: Boolean,
    val preventDamage: Boolean,
    val preventDealingDamage: Boolean
) {
    companion object {
        fun load(config: FileConfiguration): RestrictionsConfig {
            val r = config.getConfigurationSection("restrictions") ?: error("Missing 'restrictions' section in config.yml")
            return RestrictionsConfig(
                showTitle = r.getBoolean("show-title", true),
                preventMovement = r.getBoolean("prevent-movement", true),
                preventInteraction = r.getBoolean("prevent-interaction", true),
                preventInventory = r.getBoolean("prevent-inventory", true),
                preventChat = r.getBoolean("prevent-chat", true),
                preventDamage = r.getBoolean("prevent-damage", true),
                preventDealingDamage = r.getBoolean("prevent-dealing-damage", true)
            )
        }
    }
}
