# Kelpyyium Discord Bot

Kelpyyium is a feature-rich, Java-based Discord bot built with JDA (Java Discord API). It provides a full suite of moderation, economy, leveling, configuration, and utility tools — all manageable through both slash commands and prefix commands.

## Prerequisites

1. **Java Development Kit (JDK) 17+**
   - Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/javase-downloads.html).
   - Verify: `java -version`

2. **Apache Maven**
   - Download from [Maven's official website](https://maven.apache.org/download.cgi).
   - Verify: `mvn -version`

## Building

```powershell
git clone <repository-url>
cd <repository-directory>
mvn clean compile package
```

The output JAR will be in the `target` directory.

## Running

```batch
@echo off
java -Xmx2G -Xms1G -jar kelpyyium-2.3.014.jar nogui
pause
```

Running for the first time generates `config.json` and required directories automatically.

## Configuration

Edit `config.json` in the root directory before starting the bot:

| Key | Description | Default |
|-----|-------------|---------|
| `bot_token` | Your Discord bot token | — |
| `owner_id` | Primary bot owner's Discord user ID | — |
| `owner_ids` | Additional owner IDs (array) | `[]` |
| `default_prefix` | Prefix for text commands | `/` |
| `default_status_message` | Custom status text (overrides RPC) | `""` |
| `default_online_status` | `online`, `idle`, `dnd`, `invisible` | `"online"` |
| `default_rpc_type` | `watching`, `playing`, `listening`, `competing`, `streaming` | `"watching"` |
| `default_rpc_text` | Text shown in the RPC activity | `"for commands"` |
| `hide_owner_commands` | Hide owner-only slash commands from the command list | `false` |

## Logging

Logs are stored in the `logs` directory. Configure logging in `src/main/resources/logback.xml`.

## Features

### 🔧 Auto Configuration
- **`/autoconfig`** — Guided setup wizard that walks server owners through configuring log channels, feature toggles, welcome messages, and more in a step-by-step interactive flow.

### 🔨 Moderation
- `/warn`, `/unwarn`, `/warns` — Warning system with history
- `/ban`, `/unban`, `/softban` — Ban management with timed bans
- `/kick` — Kick users
- `/mute`, `/unmute` — Role-based muting with auto-created Muted role
- `/timeout` — Discord native timeout (up to 28 days)
- `/purge` — Bulk message deletion (by count or user)
- `/lockdown` — Channel lockdown toggle
- `/hist` — View full moderation history for a user
- Punishment DM notifications (`/punishmentdm`)
- Punishment appeal system with modal forms
- Suspicious account detection & alerts (`/suspiciousnotify`, `/suspiciouslist`)

### 💰 Economy
- `/balance`, `/pay`, `/baltop` — Currency system
- `/work`, `/daily` — Earn money
- `/gamble`, `/slots`, `/flip`, `/dice`, `/blackjack` — Gambling games
- `/rob` — Steal from other users
- `/bank` — Banking system with deposits, withdrawals, and loans
- `/setbalance`, `/addbalance`, `/subtractbalance` — Admin balance management
- `/chargeback` — Reverse transactions
- Configurable currency name per server

### 📊 Leveling
- `/rank`, `/level` — View rank card and level info
- `/leaderboard`, `/lb` — XP leaderboard
- `/xp` — View or manage XP
- `/levels` — Configure level-up rewards and settings

### 🎮 Games
- `/poker` — Multiplayer poker
- `/chess` — Chess games

### 🌐 Global Chat
- `/globalchat` — Link channels across servers for cross-server messaging
- Reply handling with clickable jump links to original messages
- Cross-server message deletion sync

### 🎫 Tickets
- `/ticket` — Ticket system with categories, claiming, and transcripts

### 🪪 Proxy (PluralKit-style)
- `/proxymember` — Create and manage proxy members
- `/proxysettings` — Configure proxy behavior

### ⚙️ Server Configuration
- `/settings` — View and modify server settings
- `/permissions` — Node-based permission system with user, role, and @everyone overrides
- `/logging` — Configure auto-logging channels (moderation, messages, members)
- `/log` — Create manual log entries
- `/automod` — Auto-moderation rules
- `/antispam` — Anti-spam configuration (message limits, caps, mentions, duplicates, punishments)
- `/welcome` — Welcome/leave message configuration
- `/reactionrole` — Reaction role management
- `/rolepersistence` — Restore roles when members rejoin
- `/prefix` — Change the server's command prefix
- `/rules` — Manage server rules display

### 🛡️ Permission System
- Granular node-based permissions (e.g., `mod.ban`, `economy.gambling.blackjack`)
- Automatic Discord permission mapping — members with Discord's Ban Members permission auto-receive `mod.ban`, `mod.warn`, etc.
- Per-user, per-role, and @everyone overrides
- Wildcard support (`mod.*`, `economy.*`)

### 🛠️ Utility
- `/help` — Command reference
- `/ping` — Bot latency
- `/info` — Bot info and stats
- `/serverstats` — Server statistics
- `/embed` — Create custom embeds
- `/echo`, `/talkas` — Send messages as the bot
- `/dadjoke`, `/joke` — Random jokes
- `/pride`, `/flags`, `/pronouns` — Pride flags and pronoun roles
- `/rules` — Display server rules
- `/privacy`, `/deletedata` — GDPR/privacy compliance

### 👑 Owner Commands
- `/status` — Change bot status message
- `/presence` — Change online status
- `/appearance` — Customize bot appearance
- `/restart` — Restart the bot
- `/config` — Raw config editor
- `/backup` — Server data backup/restore
- Can be hidden from slash command list via `hide_owner_commands` config

### 📝 Prefix Commands
All major commands also work with prefix syntax (default `!`):
- `!work`, `!daily`, `!balance`, `!pay`, `!baltop`, `!gamble`, `!slots`, `!flip`, `!dice`
- `!rank`, `!leaderboard`, `!xp`
- `!warn`, `!mute`, `!timeout`, `!kick`, `!ban`, `!purge`
- `!ping`, `!info`, `!serverinfo`, `!echo`, `!help`
- Aliases supported (e.g., `!bal`, `!lb`, `!r`, `!h`)