# Kelpyyium Discord Bot

Kelpyyium is a feature-rich, Java-based Discord bot built with JDA (Java Discord API). It provides a full suite of moderation, economy, leveling, configuration, and utility tools — all manageable through both slash commands and prefix commands.

### [Official Website, Guide, Documentation, and more!](https://kelpyyium.pages.dev/)

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

- **Global Chat Relay**: Seamlessly relay messages across multiple servers.
- **Moderation Tools**: Includes commands for managing users and maintaining server integrity.
- **Utility Commands**: Offers various helpful commands for server management.
- **Reply Handling**: Relayed replies include clickable jump links to the original message.
- **Cross-Server Message Deletion**: Deletes relayed copies when a message is deleted in one server.
- **Economy System**: Per-server economy with balance, shop, and transactions.
- **Leveling System**: XP and level tracking with customizable rank cards.
- **Ticket System**: Support ticket creation and management.
- **Reaction Roles**: Assign roles based on message reactions.
- **Music Playback**: Stream music from YouTube and other sources in voice channels.
- **Prefix Commands**: Traditional text-based commands alongside slash commands.

## Prefix Commands

Each server can configure its own set of command prefixes independently.

| Subcommand | Description |
| --- | --- |
| `/prefix set prefix:!` | Replace all prefixes with a single new one |
| `/prefix add prefix:-k` | Add an additional prefix (multiple can be active at once) |
| `/prefix remove prefix:!` | Remove a specific prefix |
| `/prefix enable command:ping` | Re-enable a previously disabled command |
| `/prefix disable command:ping` | Disable a specific command for this server |
| `/prefix enable-all` | Re-enable all prefix commands |
| `/prefix disable-all` | Disable all prefix commands server-wide |
| `/prefix status` | Show all active prefixes and disabled commands |
| `/prefix list` | List all available prefix commands |

> **Note:** A server must always have at least one prefix — `/prefix remove` will refuse to remove the last one. The default prefix is `!`.

## Music

The bot supports music playback in voice channels via LavaPlayer and YouTube.

| Command | Description |
| --- | --- |
| `!play <query or URL>` | Play a song or add it to the queue |
| `!skip` | Skip the current song |
| `!stop` | Stop playback and clear the queue |
| `!queue` | Show the current queue |
| `!pause` / `!resume` | Pause or resume playback |
| `!nowplaying` | Show the currently playing song |
| `!volume <0-100>` | Adjust the playback volume |
| `!leave` | Disconnect the bot from voice |

> The bot will automatically disconnect when the voice channel is empty or when kicked.
