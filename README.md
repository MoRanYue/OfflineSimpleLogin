# OfflineSimpleLogin

[![Test and Release](https://github.com/MoRanYue/OfflineSimpleLogin/actions/workflows/main.yml/badge.svg)](https://github.com/MoRanYue/OfflineSimpleLogin/actions/workflows/main.yml)

A Folia-compatible Minecraft Paper plugin that requires offline-mode players to register and login. Passwords are encrypted with **Argon2id** and stored in **SQLite** or **PostgreSQL**. Designed to work alongside [LimitedOfflineModePaper](https://github.com/chank-op/LimitedOfflineMode-Paper).

## Features

- ✅ **Folia support** — Fully compatible with Folia's regionized multithreading
- ✅ **Offline-mode authentication** — Register/login for players joining without Mojang auth
- ✅ **Premium bypass** — Mojang-authenticated players are automatically detected and bypass the auth system
- ✅ **Argon2id hashing** — Industry-standard password hashing with configurable parameters
- ✅ **SQLite & PostgreSQL** — Choose your database backend
- ✅ **Session management** — IP-based auto-login with configurable timeout
- ✅ **Player restrictions** — Block movement, interaction, chat, damage until authenticated
- ✅ **Fully configurable** — All messages use MiniMessage format, configurable in `config.yml`
- ✅ **LimitedOfflineModePaper compatible** — Works seamlessly alongside LOM for Netty-level offline login bypass

## How It Works

```
Player connects → LimitedOfflineModePaper (Netty intercept)
               → Player joins server
               → OfflineSimpleLogin checks GameProfile
               │
               ├─ Premium player (has "textures" property)
               │  └─ Auto-authenticated — no prompt, no restrictions
               │
               └─ Offline player (no "textures" property)
                  ├─ Has valid IP session? → Auto-authenticated
                  ├─ Registered? → Show login prompt
                  └─ Not registered? → Show register prompt
```

## Requirements

- **Server**: Paper 26.1.2+ or Folia
- **Java**: 25+
- **Optional**: [LimitedOfflineModePaper](https://github.com/MoRanYue/LimitedOfflineMode-Paper) (for Netty-level offline login bypass)

## Installation

1. Download the latest JAR from [Releases](https://github.com/MoRanYue/OfflineSimpleLogin/releases)
2. Place the JAR in your server's `plugins/` folder
3. Restart the server
4. Configure `plugins/OfflineSimpleLogin/config.yml` as needed

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/register <password> <confirm>` | `offlinesimplelogin.register` | Register a new account |
| `/login <password>` | `offlinesimplelogin.login` | Login to your account |
| `/logout` | `offlinesimplelogin.logout` | Logout from your account |
| `/changepassword <old> <new>` | `offlinesimplelogin.changepassword` | Change your password |

All commands default to `true` for all players. Premium (online-mode) players are automatically rejected with a message.

## Configuration

### Database

```yaml
database:
  type: sqlite  # or "postgresql"
  sqlite:
    file: "data/offline-simple-login.db"
  postgresql:
    host: localhost
    port: 5432
    database: offlinesimplelogin
    username: minecraft
    password: ""
```

### Argon2

```yaml
argon2:
  salt-length: 16
  hash-length: 32
  parallelism: 1
  memory: 65536    # 64 MB
  iterations: 3
```

### Sessions

```yaml
session:
  timeout: 1800        # Session timeout (seconds)
  ip-auto-login: true   # Auto-login same IP within timeout
```

### Restrictions

```yaml
restrictions:
  show-title: true           # Show Title prompts on join
  prevent-movement: true
  prevent-interaction: true
  prevent-inventory: true
  prevent-chat: true
  prevent-damage: true
  prevent-dealing-damage: true
```

All messages in `config.yml` support [Adventure MiniMessage format](https://docs.advntr.dev/minimessage/format.html).

## Building from Source

```bash
# Build with default version (0.1.0+26.1.2)
./gradlew build

# Build with custom version
./gradlew build -Pver="vX.Y.Z"

# Build a release (strips version from filename for stable tags)
./gradlew -Pver="v0.1.0" release
```

Output JAR is in `build/libs/`.

## Architecture

```
OfflineSimpleLogin/
├── OfflineSimpleLogin.kt        # Main plugin class + Brigadier command executors
├── config/
│   └── PluginConfig.kt          # Configuration data classes
├── database/
│   ├── DatabaseManager.kt       # Database interface
│   ├── SQLiteDatabaseManager.kt # SQLite implementation (WAL mode)
│   └── PostgreSQLDatabaseManager.kt # PostgreSQL implementation (HikariCP)
├── auth/
│   ├── PasswordHasher.kt        # Argon2id hashing wrapper
│   ├── SessionManager.kt        # Session management (memory + DB)
│   └── AuthManager.kt           # Auth flow orchestration
└── listener/
    ├── PlayerLoginListener.kt   # Join event handling + premium detection
    └── PlayerRestrictionListener.kt # Unauthenticated player restrictions
```

## Dependencies

- [Paper API](https://papermc.io/) 26.1.2+
- [Kotlin](https://kotlinlang.org/) 2.4.0
- [Argon2-jvm](https://github.com/phxql/argon2-jvm) — Argon2 hashing
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) — SQLite driver
- [PostgreSQL JDBC](https://jdbc.postgresql.org/) — PostgreSQL driver
- [HikariCP](https://github.com/brettwooldridge/HikariCP) — Connection pooling
- [PaperLib](https://github.com/PaperMC/PaperLib) — Paper utilities

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
