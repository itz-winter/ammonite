# Ammonite Discord Bot

Ammonite is a feature-rich, Java-based Discord bot built with JDA (Java Discord API). It provides a full suite of moderation, economy, leveling, configuration, utility, games, proxy, and support tools — all manageable through both slash commands and prefix commands.

### [Official Website, Guide, Documentation, and more!](https://ammonite.kelpw.ing/)

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
java -Xmx2G -Xms1G -jar Ammonite-1.0.0.jar nogui
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
| `database_path` | Path to the SQLite database file | `data/serverbot.db` |
| `default_prefix` | Prefix for text commands | `/` |
| `default_status_message` | Custom status text (overrides RPC) | `""` |
| `default_online_status` | `online`, `idle`, `dnd`, `invisible` | `"online"` |
| `default_rpc_type` | `watching`, `playing`, `listening`, `competing`, `streaming` | `"watching"` |
| `default_rpc_text` | Text shown in the RPC activity | `"for commands"` |
| `hide_owner_commands` | Hide owner-only slash commands from the command list | `false` |
| `support_server_invite` | Invite link to your support server | `""` |
| `bot_version` | Bot version string displayed in info commands | `"1.0.0"` |
| `spotify_client_id` | Spotify API client ID (for Spotify link support) | `""` |
| `spotify_client_secret` | Spotify API client secret | `""` |
| `spotify_country_code` | Country code for Spotify market lookups | `"US"` |

## Logging

Logs are stored in the `logs` directory. Configure logging in `src/main/resources/logback.xml`.

## Features

- **Global Chat Relay**: Seamlessly relay messages across multiple servers with cross-server deletion and reply jump links.
- **Moderation Tools**: Ban, kick, mute, timeout, warn, softban, lockdown, purge, and more — with history tracking.
- **Anti-Spam**: Configurable anti-spam detection with a GUI management interface.
- **Suspicious Account Detection**: Automatically flags and reports newly-created accounts joining your server, with configurable notification channels and roles.
- **Auto-Logging**: Automatic event logging (joins, leaves, edits, deletes, bans, etc.) to a designated channel.
- **Auto-Config**: Guided server setup assistant.
- **Automod**: Built-in automoderator configuration.
- **Welcome Messages**: Customizable welcome messages and banners for new members.
- **Reaction Roles**: Assign roles based on message reactions.
- **Role Persistence**: Restore roles to members who rejoin the server.
- **Economy System**: Per-server economy with balance, bank, daily rewards, gambling (blackjack, dice, slots, coin flip), work, rob, and transactions.
- **Leveling System**: XP and level tracking with rank cards and a leaderboard.
- **Ticket System**: Support ticket creation and management.
- **Games**: Blackjack, Poker, Chess, and more.
- **Music Playback**: Stream music from YouTube and other sources in voice channels via LavaPlayer, including Spotify link support.
- **Proxy System**: Multi-account proxy/character system for roleplay servers.
- **Backup**: Server configuration backup and restore.
- **Permissions Management**: Fine-grained command permission overrides per server.
- **Server Stats**: Live server statistics display.
- **Utility Commands**: Avatar, server info, echo, embed builder, pronoun tags, pride flags, jokes, dad jokes, rules, privacy, support, appearance customization, and more.
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

> **Note:** A server must always have at least one prefix — `/prefix remove` will refuse to remove the last one. The default prefix is `/`.

## Music

The bot supports music playback in voice channels via LavaPlayer, including YouTube and Spotify links.

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

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request with your changes. For major changes, please open an issue first to discuss what you would like to change.

## Bot Discord Profile Bio (short)

...and it's another general-purpose bot-

Heya! I am Ammonite, a powerful, configurable, feature-packed general-purpose bot. I have a ton of features-moderation, economy, leveling, utility, and more! I am the perfect fit for any server. I am always here to help!

Help: `/help [command]`
Features: https://ammonite.kelpw.ing/features
Quick reference: https://ammonite.kelpw.ing/commands
