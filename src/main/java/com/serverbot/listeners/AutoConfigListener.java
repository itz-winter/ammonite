package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for the bot joining a new guild and offers a guided setup wizard.
 * Also handles button/select-menu interactions for each setup step.
 *
 * Setup flow (steps):
 *  1. Intro — Yes/No
 *  2. Moderation log channel
 *  3. Message log channel
 *  4. Join/leave log channel
 *  5. Suspicious notify list
 *  6. Feature toggle
 *  7. Completion summary
 */
public class AutoConfigListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoConfigListener.class);

    /** Per-guild wizard state: guildId → current step + accumulated choices */
    private static final Map<String, SetupState> activeSetups = new ConcurrentHashMap<>();

    // GuildJoinEvent

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        // Try to send in system channel, then default channel
        TextChannel target = guild.getSystemChannel();
        if (target == null) {
            target = guild.getDefaultChannel() != null
                    ? guild.getDefaultChannel().asTextChannel()
                    : null;
        }
        if (target == null && !guild.getTextChannels().isEmpty()) {
            target = guild.getTextChannels().get(0);
        }
        if (target == null) return;

        sendSetupPrompt(target, guild);
    }

    /** Public so the /autoconfig command can reuse it. */
    public static void sendSetupPrompt(TextChannel channel, Guild guild) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.INFO + " Hello!")
                .setDescription(
                        "Thank you for adding me to **" + guild.getName() + "**!\n\n" +
                                "Would you like help setting up the bot and configuring its settings?\n" +
                                "This will walk you through setting up log channels, enabling features, and more.")
                .setColor(EmbedUtils.INFO_COLOR)
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(eb.build())
                .addComponents(ActionRow.of(
                        Button.success("ac_start:" + guild.getId(), "Yes, let's go!").withEmoji(Emoji.fromFormatted(CustomEmojis.SUCCESS)),
                        Button.danger("ac_skip:" + guild.getId(), "No thanks").withEmoji(Emoji.fromFormatted(CustomEmojis.ERROR))
                ))
                .queue();
    }

    // Button interactions

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("ac_")) return;

        String[] parts = id.split(":", 2);
        if (parts.length < 2) return;
        String action = parts[0];
        String guildId = parts[1];

        // Only server owner may interact
        if (event.getGuild() == null) return;
        if (!event.getUser().getId().equals(event.getGuild().getOwnerId())) {
            event.reply("Only the server owner can use the setup wizard.").setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "ac_start" -> {
                activeSetups.put(guildId, new SetupState(guildId));
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep2ModLog(event, guildId);
            }
            case "ac_skip" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                event.reply("Alright! If you change your mind, you can access the setup process later using `/autoconfig`.").setEphemeral(true).queue();
            }

            // Step 2: mod log — yes/no
            case "ac_modlog_yes" -> {
                event.deferEdit().queue();
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendModLogChannelSelect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_modlog_no" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep3MsgLog(event, guildId);
            }

            // Step 3: message log — yes/no
            case "ac_msglog_yes" -> {
                event.deferEdit().queue();
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendMsgLogChannelSelect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_msglog_no" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep4JoinLeaveLog(event, guildId);
            }

            // Step 4: join/leave log — yes/no
            case "ac_joinlog_yes" -> {
                event.deferEdit().queue();
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendJoinLogChannelSelect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_joinlog_no" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep5SuspiciousNotify(event, guildId);
            }

            // Step 5: suspicious notify — yes/no
            case "ac_suspicious_yes" -> {
                event.deferEdit().queue();
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendSuspiciousNotifySelect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_suspicious_no" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep6FeatureToggle(event, guildId);
            }

            // Step 6: feature toggle — yes/no
            case "ac_features_yes" -> {
                event.deferEdit().queue();
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendFeatureToggleSelect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_features_no" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep7Complete(event.getChannel().asTextChannel(), guildId);
            }

            // Step 5 done button (after selecting suspicious users/roles)
            case "ac_suspicious_done" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep6FeatureToggle(event, guildId);
            }

            // Step 6 done button (after toggling features)
            case "ac_features_done" -> {
                event.getMessage().delete().queue(s -> {}, err -> {});
                sendStep7Complete(event.getChannel().asTextChannel(), guildId);
            }

            default -> {}
        }
    }

    // Entity Select interactions (channel / role selectors)

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("ac_")) return;
        if (event.getGuild() == null) return;

        String guildId = event.getGuild().getId();
        SetupState state = activeSetups.get(guildId);
        if (state == null) return;

        switch (id) {
            case "ac_select_modlog" -> {
                GuildChannel selected = event.getMentions().getChannels().get(0);
                state.modLogChannelId = selected.getId();
                // Write to the keys that AutoLogUtils and /logging actually read
                ServerBot.getStorageManager().updateGuildSettings(guildId, "moderation_log_channel", selected.getId());
                ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_moderation", true);
                // Also set legacy logChannelId for /log command compatibility
                ServerBot.getStorageManager().updateGuildSettings(guildId, "logChannelId", selected.getId());
                ServerBot.getStorageManager().saveGuildSettings();
                event.getMessage().delete().queue(s -> {}, err -> {});
                event.reply(CustomEmojis.SUCCESS + " Moderation log channel set to <#" + selected.getId() + ">").setEphemeral(true).queue();
                sendStep3MsgLogDirect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_select_msglog" -> {
                GuildChannel selected = event.getMentions().getChannels().get(0);
                state.msgLogChannelId = selected.getId();
                // Write to the keys that AutoLogUtils and /logging actually read
                ServerBot.getStorageManager().updateGuildSettings(guildId, "message_log_channel", selected.getId());
                ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_message", true);
                ServerBot.getStorageManager().saveGuildSettings();
                event.getMessage().delete().queue(s -> {}, err -> {});
                event.reply(CustomEmojis.SUCCESS + " Message log channel set to <#" + selected.getId() + ">").setEphemeral(true).queue();
                sendStep4JoinLeaveLogDirect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_select_joinlog" -> {
                GuildChannel selected = event.getMentions().getChannels().get(0);
                state.joinLogChannelId = selected.getId();
                // Write to the keys that AutoLogUtils and /logging actually read
                ServerBot.getStorageManager().updateGuildSettings(guildId, "member_log_channel", selected.getId());
                ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_member", true);
                ServerBot.getStorageManager().saveGuildSettings();
                event.getMessage().delete().queue(s -> {}, err -> {});
                event.reply(CustomEmojis.SUCCESS + " Join/leave log channel set to <#" + selected.getId() + ">").setEphemeral(true).queue();
                sendStep5SuspiciousNotifyDirect(event.getChannel().asTextChannel(), guildId);
            }
            case "ac_select_suspicious" -> {
                // Can select multiple users/roles
                List<String> ids = new ArrayList<>();
                event.getMentions().getUsers().forEach(u -> ids.add(u.getId()));
                event.getMentions().getRoles().forEach(r -> ids.add(r.getId()));
                state.suspiciousNotifyIds = ids;
                ServerBot.getStorageManager().updateGuildSettings(guildId, "suspiciousNotifyList", ids);
                ServerBot.getStorageManager().saveGuildSettings();
                event.getMessage().delete().queue(s -> {}, err -> {});
                event.reply(CustomEmojis.SUCCESS + " Suspicious notify list updated (" + ids.size() + " entries).").setEphemeral(true).queue();
                sendStep6FeatureToggleDirect(event.getChannel().asTextChannel(), guildId);
            }
            default -> {}
        }
    }

    // String Select interaction (feature toggle)

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("ac_select_features")) return;
        if (event.getGuild() == null) return;

        String guildId = event.getGuild().getId();
        SetupState state = activeSetups.get(guildId);
        if (state == null) return;

        List<String> disabled = event.getValues(); // the features the user wants DISABLED
        state.disabledFeatures = disabled;

        // Use the correct setting keys that the rest of the codebase reads
        // (enableEconomy, enableLeveling, etc.)
        Map<String, String> featureToSettingKey = Map.of(
            "economy", "enableEconomy",
            "leveling", "enableLeveling",
            "games", "enableGames",
            "tickets", "enableTickets",
            "proxy", "enableProxy"
        );

        for (String feature : disabled) {
            String key = featureToSettingKey.getOrDefault(feature, feature + "Enabled");
            ServerBot.getStorageManager().updateGuildSettings(guildId, key, false);
        }
        // Enable features that were NOT selected
        for (String feature : List.of("economy", "leveling", "games", "tickets", "proxy")) {
            if (!disabled.contains(feature)) {
                String key = featureToSettingKey.getOrDefault(feature, feature + "Enabled");
                ServerBot.getStorageManager().updateGuildSettings(guildId, key, true);
            }
        }
        ServerBot.getStorageManager().saveGuildSettings();

        event.getMessage().delete().queue(s -> {}, err -> {});
        event.reply(CustomEmojis.SUCCESS + " Features updated. Disabled: " + (disabled.isEmpty() ? "none" : String.join(", ", disabled))).setEphemeral(true).queue();
        sendStep7Complete(event.getChannel().asTextChannel(), guildId);
    }

    // Step senders

    private void sendStep2ModLog(ButtonInteractionEvent event, String guildId) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Step 1/6 — Moderation Log Channel")
                .setDescription("Would you like to set up a log channel for moderation actions, such as bans, kicks, and warnings?")
                .setColor(EmbedUtils.INFO_COLOR);
        event.getChannel().asTextChannel().sendMessageEmbeds(eb.build())
                .addComponents(ActionRow.of(
                        Button.success("ac_modlog_yes:" + guildId, "Yes"),
                        Button.danger("ac_modlog_no:" + guildId, "No")
                )).queue();
    }

    private void sendModLogChannelSelect(TextChannel channel, String guildId) {
        channel.sendMessage("Please select the channel for moderation logs:")
                .addComponents(ActionRow.of(
                        EntitySelectMenu.create("ac_select_modlog", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setPlaceholder("Select a channel")
                                .setRequiredRange(1, 1)
                                .build()
                )).queue();
    }

    private void sendStep3MsgLog(ButtonInteractionEvent event, String guildId) {
        sendStep3MsgLogDirect(event.getChannel().asTextChannel(), guildId);
    }

    private void sendStep3MsgLogDirect(TextChannel channel, String guildId) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Step 2/6 — Message Log Channel")
                .setDescription("Would you like to set up a log channel for message logging, such as message edits and deletions?")
                .setColor(EmbedUtils.INFO_COLOR);
        channel.sendMessageEmbeds(eb.build())
                .addComponents(ActionRow.of(
                        Button.success("ac_msglog_yes:" + guildId, "Yes"),
                        Button.danger("ac_msglog_no:" + guildId, "No")
                )).queue();
    }

    private void sendMsgLogChannelSelect(TextChannel channel, String guildId) {
        channel.sendMessage("Please select the channel for message logs:")
                .addComponents(ActionRow.of(
                        EntitySelectMenu.create("ac_select_msglog", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setPlaceholder("Select a channel")
                                .setRequiredRange(1, 1)
                                .build()
                )).queue();
    }

    private void sendStep4JoinLeaveLog(ButtonInteractionEvent event, String guildId) {
        sendStep4JoinLeaveLogDirect(event.getChannel().asTextChannel(), guildId);
    }

    private void sendStep4JoinLeaveLogDirect(TextChannel channel, String guildId) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Step 3/6 — Join/Leave Log Channel")
                .setDescription("Would you like to set up a log channel for join and leave logging?")
                .setColor(EmbedUtils.INFO_COLOR);
        channel.sendMessageEmbeds(eb.build())
                .addComponents(ActionRow.of(
                        Button.success("ac_joinlog_yes:" + guildId, "Yes"),
                        Button.danger("ac_joinlog_no:" + guildId, "No")
                )).queue();
    }

    private void sendJoinLogChannelSelect(TextChannel channel, String guildId) {
        channel.sendMessage("Please select the channel for join/leave logs:")
                .addComponents(ActionRow.of(
                        EntitySelectMenu.create("ac_select_joinlog", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setPlaceholder("Select a channel")
                                .setRequiredRange(1, 1)
                                .build()
                )).queue();
    }

    private void sendStep5SuspiciousNotify(ButtonInteractionEvent event, String guildId) {
        sendStep5SuspiciousNotifyDirect(event.getChannel().asTextChannel(), guildId);
    }

    private void sendStep5SuspiciousNotifyDirect(TextChannel channel, String guildId) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Step 4/6 — Suspicious Account Notifications")
                .setDescription("Would you like to add users/roles to the suspicious notify list? " +
                        "They will receive a DM notification whenever a user is flagged as suspicious.")
                .setColor(EmbedUtils.INFO_COLOR);
        channel.sendMessageEmbeds(eb.build())
                .addComponents(ActionRow.of(
                        Button.success("ac_suspicious_yes:" + guildId, "Yes"),
                        Button.danger("ac_suspicious_no:" + guildId, "No")
                )).queue();
    }

    private void sendSuspiciousNotifySelect(TextChannel channel, String guildId) {
        channel.sendMessage("Please select the users and/or roles for suspicious account notifications:")
                .addComponents(ActionRow.of(
                        EntitySelectMenu.create("ac_select_suspicious", EntitySelectMenu.SelectTarget.USER, EntitySelectMenu.SelectTarget.ROLE)
                                .setPlaceholder("Select users/roles")
                                .setRequiredRange(1, 10)
                                .build()
                )).queue();
    }

    private void sendStep6FeatureToggle(ButtonInteractionEvent event, String guildId) {
        sendStep6FeatureToggleDirect(event.getChannel().asTextChannel(), guildId);
    }

    private void sendStep6FeatureToggleDirect(TextChannel channel, String guildId) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Step 5/6 — Disable Features")
                .setDescription("Would you like to disable any features of the bot that you do not want to use?")
                .setColor(EmbedUtils.INFO_COLOR);
        channel.sendMessageEmbeds(eb.build())
                .addComponents(ActionRow.of(
                        Button.success("ac_features_yes:" + guildId, "Yes"),
                        Button.danger("ac_features_no:" + guildId, "No")
                )).queue();
    }

    private void sendFeatureToggleSelect(TextChannel channel, String guildId) {
        StringSelectMenu menu = StringSelectMenu.create("ac_select_features")
                .setPlaceholder("Select features to DISABLE")
                .setRequiredRange(0, 5)
                .addOption("Economy", "economy", "Disable the economy system", Emoji.fromUnicode("💰"))
                .addOption("Leveling", "leveling", "Disable the leveling system", Emoji.fromUnicode("📈"))
                .addOption("Games", "games", "Disable games and gambling", Emoji.fromUnicode("🎮"))
                .addOption("Tickets", "tickets", "Disable the ticket system", Emoji.fromUnicode("🎫"))
                .addOption("Proxy", "proxy", "Disable the proxy/plural system", Emoji.fromUnicode("🔀"))
                .build();

        channel.sendMessage("Select the features you'd like to **disable**. Leave unselected to keep enabled.")
                .addComponents(ActionRow.of(menu))
                .addComponents(ActionRow.of(Button.primary("ac_features_done:" + guildId, "Done — Keep All Enabled")))
                .queue();
    }

    private void sendStep7Complete(TextChannel channel, String guildId) {
        SetupState state = activeSetups.remove(guildId);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SUCCESS + " Setup Complete!")
                .setDescription("You have completed the initial setup for the bot! Here's a summary of your configuration:")
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTimestamp(Instant.now());

        if (state != null) {
            eb.addField("Moderation Log", state.modLogChannelId != null ? "<#" + state.modLogChannelId + ">" : "Not set", true);
            eb.addField("Message Log", state.msgLogChannelId != null ? "<#" + state.msgLogChannelId + ">" : "Not set", true);
            eb.addField("Join/Leave Log", state.joinLogChannelId != null ? "<#" + state.joinLogChannelId + ">" : "Not set", true);
            eb.addField("Suspicious Notify", state.suspiciousNotifyIds != null ? state.suspiciousNotifyIds.size() + " entries" : "Not set", true);
            eb.addField("Disabled Features", state.disabledFeatures != null && !state.disabledFeatures.isEmpty()
                    ? String.join(", ", state.disabledFeatures) : "None", true);
        }

        eb.addField("Need to change something?",
                "You can always change these settings later:\n" +
                        "• `/logging` — Log channels\n" +
                        "• `/suspiciousnotify` — Suspicious notifications\n" +
                        "• `/config` — Feature toggles\n" +
                        "• `/help` — Full command reference\n" +
                        "• `/autoconfig` — Run this wizard again", false);

        channel.sendMessageEmbeds(eb.build()).queue();
    }

    // Setup state

    private static class SetupState {
        final String guildId;
        String modLogChannelId;
        String msgLogChannelId;
        String joinLogChannelId;
        List<String> suspiciousNotifyIds;
        List<String> disabledFeatures;

        SetupState(String guildId) {
            this.guildId = guildId;
        }
    }
}
