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