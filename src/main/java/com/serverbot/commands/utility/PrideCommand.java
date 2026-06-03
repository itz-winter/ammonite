package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Pride flag overlay command
 */
public class PrideCommand implements SlashCommand {

    // Pride flag definitions with colors
    private static final Map<String, Color[]> PRIDE_FLAGS = new HashMap<>();

    static {
        // Traditional Pride Flag
        PRIDE_FLAGS.put("pride", new Color[] {
                new Color(228, 3, 3), // Red
                new Color(255, 140, 0), // Orange
                new Color(255, 237, 0), // Yellow
                new Color(0, 128, 38), // Green
                new Color(0, 76, 255), // Blue
                new Color(115, 41, 130) // Purple
        });

        // Progress Pride Flag
        PRIDE_FLAGS.put("progress", new Color[] {
                new Color(115, 41, 130), // Purple
                new Color(0, 76, 255), // Blue
                new Color(0, 128, 38), // Green
                new Color(255, 237, 0), // Yellow
                new Color(255, 140, 0), // Orange
                new Color(228, 3, 3), // Red
                new Color(0, 0, 0), // Black
                new Color(97, 57, 21), // Brown
                new Color(91, 206, 250), // Light Blue
                new Color(245, 169, 184), // Pink
                new Color(255, 255, 255) // White
        });

        // Transgender Flag
        PRIDE_FLAGS.put("trans", new Color[] {
                new Color(91, 206, 250), // Light Blue
                new Color(245, 169, 184), // Pink
                new Color(255, 255, 255), // White
                new Color(245, 169, 184), // Pink
                new Color(91, 206, 250) // Light Blue
        });

        // Bisexual Flag
        PRIDE_FLAGS.put("bi", new Color[] {
                new Color(214, 2, 112), // Pink
                new Color(214, 2, 112), // Pink
                new Color(155, 79, 150), // Purple
                new Color(0, 56, 168), // Blue
                new Color(0, 56, 168) // Blue
        });

        // Pansexual Flag
        PRIDE_FLAGS.put("pan", new Color[] {
                new Color(255, 33, 140), // Pink
                new Color(255, 216, 0), // Yellow
                new Color(33, 177, 255) // Blue
        });

        // Lesbian Flag
        PRIDE_FLAGS.put("lesbian", new Color[] {
                new Color(213, 45, 0), // Dark Orange
                new Color(255, 154, 86), // Orange
                new Color(255, 255, 255), // White
                new Color(211, 98, 164), // Pink
                new Color(163, 2, 98) // Dark Pink
        });

        // Asexual Flag
        PRIDE_FLAGS.put("ace", new Color[] {
                new Color(0, 0, 0), // Black
                new Color(164, 164, 164), // Gray
                new Color(255, 255, 255), // White
                new Color(128, 0, 128) // Purple
        });

        // Aromantic Flag
        PRIDE_FLAGS.put("aro", new Color[] {
                new Color(61, 165, 66), // Green
                new Color(167, 211, 121), // Light Green
                new Color(255, 255, 255), // White
                new Color(169, 169, 169), // Gray
                new Color(0, 0, 0) // Black
        });

        // Non-binary Flag
        PRIDE_FLAGS.put("nonbinary", new Color[] {
                new Color(255, 244, 51), // Yellow
                new Color(255, 255, 255), // White
                new Color(155, 89, 208), // Purple
                new Color(45, 45, 45) // Black
        });

        // Genderfluid Flag
        PRIDE_FLAGS.put("genderfluid", new Color[] {
                new Color(255, 117, 162), // Pink
                new Color(255, 255, 255), // White
                new Color(190, 24, 214), // Purple
                new Color(0, 0, 0), // Black
                new Color(51, 62, 189) // Blue
        });

        // Agender Flag
        PRIDE_FLAGS.put("agender", new Color[] {
                new Color(0, 0, 0), // Black
                new Color(186, 186, 186), // Gray
                new Color(255, 255, 255), // White
                new Color(184, 250, 184), // Light Green
                new Color(255, 255, 255), // White
                new Color(186, 186, 186), // Gray
                new Color(0, 0, 0) // Black
        });

        // Demisexual Flag
        PRIDE_FLAGS.put("demisexual", new Color[] {
                new Color(0, 0, 0), // Black
                new Color(163, 163, 163), // Gray
                new Color(255, 255, 255), // White
                new Color(102, 45, 145) // Purple
        });

        // Demiromantic Flag
        PRIDE_FLAGS.put("demiromantic", new Color[] {
                new Color(0, 0, 0), // Black
                new Color(163, 163, 163), // Gray
                new Color(255, 255, 255), // White
                new Color(0, 158, 73) // Green
        });

        // Polysexual Flag
        PRIDE_FLAGS.put("polysexual", new Color[] {
                new Color(246, 28, 174), // Pink/Magenta
                new Color(7, 213, 105), // Green
                new Color(28, 146, 246) // Blue
        });

        // Omnisexual Flag
        PRIDE_FLAGS.put("omnisexual", new Color[] {
                new Color(255, 121, 198), // Light Pink
                new Color(255, 154, 85), // Pink
                new Color(255, 206, 84), // Yellow
                new Color(127, 175, 225), // Light Blue
                new Color(109, 127, 228) // Blue/Purple
        });

        // Gender Questioning Flag
        PRIDE_FLAGS.put("questioning", new Color[] {
                new Color(237, 237, 237), // Light Gray
                new Color(181, 225, 181), // Light Green
                new Color(255, 255, 255), // White
                new Color(181, 225, 181), // Light Green
                new Color(237, 237, 237) // Light Gray
        });

        // Intersex Flag
        PRIDE_FLAGS.put("intersex", new Color[] {
                new Color(255, 218, 0), // Yellow
                new Color(255, 218, 0), // Yellow (with purple circle overlay)
                new Color(255, 218, 0), // Yellow
                new Color(255, 218, 0) // Yellow
        });

        // Polyamorous Flag
        PRIDE_FLAGS.put("polyamorous", new Color[] {
                new Color(0, 62, 165), // Blue
                new Color(236, 0, 140), // Red/Magenta
                new Color(0, 0, 0) // Black
        });

        // Neutrois Flag
        PRIDE_FLAGS.put("neutrois", new Color[] {
                new Color(255, 255, 255), // White
                new Color(34, 139, 34), // Green
                new Color(0, 0, 0) // Black
        });

        // Two-Spirit Flag
        PRIDE_FLAGS.put("twospirit", new Color[] {
                new Color(255, 255, 0), // Yellow
                new Color(255, 255, 255), // White
                new Color(255, 192, 203), // Pink
                new Color(0, 0, 0), // Black
                new Color(0, 0, 255) // Blue
        });

        // MLM/Vincian Flag (Men Loving Men)
        PRIDE_FLAGS.put("mlm", new Color[] {
                new Color(7, 141, 112), // Dark Green
                new Color(38, 206, 170), // Green
                new Color(152, 232, 193), // Light Green
                new Color(255, 255, 255), // White
                new Color(123, 173, 227), // Light Blue
                new Color(80, 73, 204), // Blue
                new Color(61, 26, 120) // Dark Blue
        });

        // Aroace Flag (Aromantic Asexual)
        PRIDE_FLAGS.put("aroace", new Color[] {
                new Color(230, 119, 0), // Orange
                new Color(255, 204, 51), // Yellow
                new Color(255, 255, 255), // White
                new Color(98, 174, 220), // Light Blue
                new Color(36, 73, 147) // Dark Blue
        });

        // Graysexual Flag
        PRIDE_FLAGS.put("graysexual", new Color[] {
                new Color(102, 102, 102), // Dark Gray
                new Color(172, 172, 172), // Light Gray
                new Color(255, 255, 255), // White
                new Color(128, 0, 128) // Purple
        });

        // Grayromantic Flag
        PRIDE_FLAGS.put("grayromantic", new Color[] {
                new Color(102, 102, 102), // Dark Gray
                new Color(172, 172, 172), // Light Gray
                new Color(255, 255, 255), // White
                new Color(34, 139, 34) // Green
        });

        // Bigender Flag
        PRIDE_FLAGS.put("bigender", new Color[] {
                new Color(196, 121, 162), // Pink
                new Color(237, 189, 218), // Light Pink
                new Color(155, 89, 208), // Purple
                new Color(255, 255, 255), // White
                new Color(155, 89, 208), // Purple
                new Color(106, 171, 226), // Light Blue
                new Color(54, 123, 199) // Blue
        });

        // Trigender Flag
        PRIDE_FLAGS.put("trigender", new Color[] {
                new Color(255, 153, 204), // Pink
                new Color(102, 204, 255), // Blue
                new Color(102, 255, 102), // Green
                new Color(255, 255, 102), // Yellow
                new Color(102, 255, 102), // Green
                new Color(102, 204, 255), // Blue
                new Color(255, 153, 204) // Pink
        });

        // Pangender Flag
        PRIDE_FLAGS.put("pangender", new Color[] {
                new Color(255, 247, 153), // Light Yellow
                new Color(255, 207, 207), // Light Pink
                new Color(187, 255, 253), // Light Cyan
                new Color(255, 255, 255), // White
                new Color(187, 255, 253), // Light Cyan
                new Color(255, 207, 207), // Light Pink
                new Color(255, 247, 153) // Light Yellow
        });

        // Omnigender Flag
        PRIDE_FLAGS.put("omnigender", new Color[] {
                new Color(255, 184, 222), // Light Pink
                new Color(255, 221, 155), // Light Orange
                new Color(255, 255, 204), // Light Yellow
                new Color(255, 255, 255), // White
                new Color(203, 219, 255), // Light Blue
                new Color(209, 196, 233), // Light Purple
                new Color(255, 184, 222) // Light Pink
        });

        // Demiboy Flag
        PRIDE_FLAGS.put("demiboy", new Color[] {
                new Color(127, 127, 127), // Gray
                new Color(196, 244, 255), // Light Blue
                new Color(255, 255, 255), // White
                new Color(196, 244, 255), // Light Blue
                new Color(127, 127, 127) // Gray
        });

        // Demigirl Flag
        PRIDE_FLAGS.put("demigirl", new Color[] {
                new Color(127, 127, 127), // Gray
                new Color(255, 184, 222), // Light Pink
                new Color(255, 255, 255), // White
                new Color(255, 184, 222), // Light Pink
                new Color(127, 127, 127) // Gray
        });

        // Additional Pride Flags
        PRIDE_FLAGS.put("aromantic-asexual", new Color[] {
                new Color(230, 119, 0), // Orange
                new Color(255, 204, 51), // Yellow
                new Color(255, 255, 255), // White
                new Color(98, 174, 220), // Light Blue
                new Color(36, 73, 147) // Dark Blue
        });

        // Queer Flag
        PRIDE_FLAGS.put("queer", new Color[] {
                new Color(0, 0, 0), // Black
                new Color(153, 153, 153), // Gray
                new Color(255, 255, 255), // White
                new Color(181, 126, 220), // Light Purple
                new Color(102, 45, 145) // Dark Purple
        });

        // Demigender Flag
        PRIDE_FLAGS.put("demigender", new Color[] {
                new Color(127, 127, 127), // Gray
                new Color(196, 244, 255), // Light Blue
                new Color(255, 255, 204), // Light Yellow
                new Color(255, 255, 255), // White
                new Color(255, 255, 204), // Light Yellow
                new Color(196, 244, 255), // Light Blue
                new Color(127, 127, 127) // Gray
        });

        // Androsexual Flag
        PRIDE_FLAGS.put("androsexual", new Color[] {
                new Color(102, 204, 255), // Light Blue
                new Color(51, 102, 255), // Blue
                new Color(153, 0, 153), // Purple
                new Color(255, 102, 153) // Pink
        });

        // Gynesexual Flag
        PRIDE_FLAGS.put("gynesexual", new Color[] {
                new Color(255, 102, 153), // Pink
                new Color(153, 0, 153), // Purple
                new Color(51, 102, 255), // Blue
                new Color(102, 204, 255) // Light Blue
        });

        // Abrosexual Flag
        PRIDE_FLAGS.put("abrosexual", new Color[] {
                new Color(70, 163, 79), // Green
                new Color(168, 212, 121), // Light Green
                new Color(255, 255, 255), // White
                new Color(246, 169, 184), // Pink
                new Color(214, 50, 108) // Dark Pink
        });

        // Pansexual/Panromantic Flag (Alternative)
        PRIDE_FLAGS.put("panromantic", new Color[] {
                new Color(255, 33, 140), // Pink
                new Color(255, 216, 0), // Yellow
                new Color(33, 177, 255) // Blue
        });

        // Platonic Flag
        PRIDE_FLAGS.put("platonic", new Color[] {
                new Color(255, 214, 0), // Yellow
                new Color(255, 140, 0), // Orange
                new Color(255, 255, 255), // White
                new Color(169, 169, 169), // Gray
                new Color(0, 0, 0) // Black
        });

        // Alterous Flag
        PRIDE_FLAGS.put("alterous", new Color[] {
                new Color(255, 165, 0), // Orange
                new Color(255, 255, 0), // Yellow
                new Color(255, 255, 255), // White
                new Color(169, 169, 169), // Gray
                new Color(0, 100, 0) // Dark Green
        });

        // Bear Brotherhood Flag
        PRIDE_FLAGS.put("bear", new Color[] {
                new Color(97, 57, 21), // Brown
                new Color(255, 140, 0), // Orange
                new Color(255, 237, 0), // Yellow
                new Color(255, 255, 224), // Beige
                new Color(255, 255, 255), // White
                new Color(169, 169, 169), // Gray
                new Color(0, 0, 0) // Black
        });

        // Leather Pride Flag
        PRIDE_FLAGS.put("leather", new Color[] {
                new Color(0, 0, 0), // Black
                new Color(0, 0, 139), // Navy Blue
                new Color(0, 0, 0), // Black
                new Color(0, 0, 139), // Navy Blue
                new Color(0, 0, 0), // Black
                new Color(0, 0, 139), // Navy Blue
                new Color(0, 0, 0), // Black
                new Color(0, 0, 139), // Navy Blue
                new Color(0, 0, 0) // Black
        });
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check if no required parameters provided - show help
        if (event.getOption("flag") == null) {
            showPrideHelp(event);
            return;
        }

        String actionType = event.getOption("type") != null ? event.getOption("type").getAsString() : "avatar";

        switch (actionType) {
            case "avatar" -> handleAvatar(event);
            case "url" -> handleUrl(event);
            case "custom" -> handleCustom(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Type",
                        "Invalid type: `" + actionType + "`\n" +
                                "Valid types: `avatar`, `url`, `custom`\n\n" +
                                "Use `/pride` without arguments to see the help guide."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        }
    }

    private void showPrideHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏳️‍🌈 Pride Command Help")
                .setDescription("Apply pride flag overlays to avatars or images")
                .setColor(0xFF69B4)
                .addField("**Basic Usage**",
                        "`/pride flag:<flag> [type:avatar] [user:@user] [style:overlay]`\n" +
                                "`/pride flag:<flag> type:url image_url:<url> [style:overlay]`\n" +
                                "`/pride flag:<flag> type:custom image:<file> [style:overlay]`",
                        false)
                .addField("**Parameters**",
                        "• `flag` - Pride flag to apply (required)\n" +
                                "• `type` - Type of image (avatar/url/custom, default: avatar)\n" +
                                "• `user` - User for avatar type (default: you)\n" +
                                "• `image_url` - URL for url type\n" +
                                "• `image` - File upload for custom type\n" +
                                "• `style` - How to apply flag (overlay/border, default: overlay)",
                        false)
                .addField("**Available Flags**",
                        "Traditional Pride, Progress Pride, Transgender, Bisexual, Pansexual, Lesbian, " +
                                "Asexual, Aromantic, Non-binary, Genderfluid, Agender, Demisexual, MLM, Aroace, " +
                                "and many more...",
                        false)
                .addField("**Examples**",
                        "`/pride flag:trans` - Apply trans flag to your avatar\n" +
                                "`/pride flag:pride user:@friend style:border` - Apply pride flag border to friend's avatar\n"
                                +
                                "`/pride flag:lesbian type:url image_url:https://example.com/image.png`",
                        false)
                .setFooter("Use -!help to dismiss future help messages");

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    private void handleAvatar(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();
        String flagName = event.getOption("flag").getAsString().toLowerCase();
        String style = event.getOption("style") != null ? event.getOption("style").getAsString() : "overlay";

        if (!PRIDE_FLAGS.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            String avatarUrl = targetUser.getAvatarUrl();
            if (avatarUrl == null) {
                avatarUrl = targetUser.getDefaultAvatarUrl();
            }

            BufferedImage result = applyPrideFlag(avatarUrl, flagName, style);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(result, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 Pride Avatar")
                    .setDescription("**User:** " + targetUser.getAsMention() + "\n" +
                            "**Flag:** " + capitalize(flagName) + "\n" +
                            "**Style:** " + capitalize(style))
                    .setColor(PRIDE_FLAGS.get(flagName)[0])
                    .setImage("attachment://pride_avatar.png");

            event.getHook().sendMessageEmbeds(embed.build())
                    .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageData, "pride_avatar.png"))
                    .queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Processing Failed",
                    "Failed to process avatar: " + e.getMessage())).queue();
        }
    }

    private void handleUrl(SlashCommandInteractionEvent event) {
        String imageUrl = event.getOption("image_url").getAsString();
        String flagName = event.getOption("flag").getAsString().toLowerCase();
        String style = event.getOption("style") != null ? event.getOption("style").getAsString() : "overlay";

        if (!PRIDE_FLAGS.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            BufferedImage result = applyPrideFlag(imageUrl, flagName, style);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(result, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 Pride Image")
                    .setDescription("**Flag:** " + capitalize(flagName) + "\n" +
                            "**Style:** " + capitalize(style))
                    .setColor(PRIDE_FLAGS.get(flagName)[0])
                    .setImage("attachment://pride_image.png");

            event.getHook().sendMessageEmbeds(embed.build())
                    .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageData, "pride_image.png"))
                    .queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Processing Failed",
                    "Failed to process image: " + e.getMessage())).queue();
        }
    }

    private void handleCustom(SlashCommandInteractionEvent event) {
        net.dv8tion.jda.api.entities.Message.Attachment attachment = event.getOption("image").getAsAttachment();
        String flagName = event.getOption("flag").getAsString().toLowerCase();
        String style = event.getOption("style") != null ? event.getOption("style").getAsString() : "overlay";

        if (!PRIDE_FLAGS.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check if attachment is an image
        if (!attachment.isImage()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid File",
                    "Please upload an image file (PNG, JPG, GIF, etc.)")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            String imageUrl = attachment.getUrl();
            BufferedImage result = applyPrideFlag(imageUrl, flagName, style);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(result, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 Pride Custom Image")
                    .setDescription("**Flag:** " + capitalize(flagName) + "\n" +
                            "**Style:** " + capitalize(style) + "\n" +
                            "**Original:** " + attachment.getFileName())
                    .setColor(PRIDE_FLAGS.get(flagName)[0])
                    .setImage("attachment://pride_custom.png");

            event.getHook().sendMessageEmbeds(embed.build())
                    .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageData, "pride_custom.png"))
                    .queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Processing Failed",
                    "Failed to process custom image: " + e.getMessage())).queue();
        }
    }

    private BufferedImage applyPrideFlag(String imageUrl, String flagName, String arrangement) throws IOException {
        // Download the image
        URL url = new URL(imageUrl);
        BufferedImage originalImage = ImageIO.read(url);

        if (originalImage == null) {
            throw new IOException("Could not read image from URL");
        }

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw original image
        g2d.drawImage(originalImage, 0, 0, null);

        Color[] flagColors = PRIDE_FLAGS.get(flagName);

        // Special handling for intersex flag (yellow background with purple circle)
        if ("intersex".equals(flagName)) {
            drawIntersexFlag(g2d, width, height, arrangement);
        } else {
            switch (arrangement.toLowerCase()) {
                case "overlay" -> drawOverlay(g2d, width, height, flagColors);
                case "border" -> drawBorder(g2d, width, height, flagColors);
                default -> drawOverlay(g2d, width, height, flagColors);
            }
        }

        g2d.dispose();
        return result;
    }

    private void drawOverlay(Graphics2D g2d, int width, int height, Color[] colors) {
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        int stripeHeight = height / colors.length;

        for (int i = 0; i < colors.length; i++) {
            g2d.setColor(colors[i]);
            g2d.fillRect(0, i * stripeHeight, width, stripeHeight);
        }
    }

    private void drawBorder(Graphics2D g2d, int width, int height, Color[] colors) {
        // Create a circular border with pride flag colors
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 2;

        // Draw concentric circles for each color
        int borderThickness = radius / 8; // Adjust thickness as needed
        int colorCount = colors.length;
        int ringThickness = borderThickness / colorCount;

        for (int i = 0; i < colorCount; i++) {
            g2d.setColor(colors[i]);
            int currentRadius = radius - (i * ringThickness);
            int innerRadius = currentRadius - ringThickness;

            // Create ring shape
            Ellipse2D.Double outerCircle = new Ellipse2D.Double(
                    centerX - currentRadius, centerY - currentRadius,
                    currentRadius * 2, currentRadius * 2);
            Ellipse2D.Double innerCircle = new Ellipse2D.Double(
                    centerX - innerRadius, centerY - innerRadius,
                    innerRadius * 2, innerRadius * 2);

            // Create area for the ring
            Area ring = new Area(outerCircle);
            ring.subtract(new Area(innerCircle));

            g2d.fill(ring);
        }
    }

    private void drawIntersexFlag(Graphics2D g2d, int width, int height, String arrangement) {
        Color yellow = new Color(255, 218, 0);
        Color purple = new Color(121, 2, 170);

        switch (arrangement.toLowerCase()) {
            case "overlay" -> {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2d.setColor(yellow);
                g2d.fillRect(0, 0, width, height);

                // Draw purple circle in center
                g2d.setColor(purple);
                int circleDiameter = Math.min(width, height) / 3;
                int circleX = (width - circleDiameter) / 2;
                int circleY = (height - circleDiameter) / 2;
                g2d.setStroke(new BasicStroke(circleDiameter / 10f));
                g2d.drawOval(circleX, circleY, circleDiameter, circleDiameter);
            }
            case "border" -> {
                // Create circular border for intersex flag
                int centerX = width / 2;
                int centerY = height / 2;
                int radius = Math.min(width, height) / 2;
                int borderThickness = radius / 6;

                // Yellow outer ring
                g2d.setColor(yellow);
                Ellipse2D.Double outerCircle = new Ellipse2D.Double(
                        centerX - radius, centerY - radius,
                        radius * 2, radius * 2);

                // Purple inner area for the circle symbol
                int innerRadius = radius - borderThickness;
                Ellipse2D.Double innerCircle = new Ellipse2D.Double(
                        centerX - innerRadius, centerY - innerRadius,
                        innerRadius * 2, innerRadius * 2);

                // Draw yellow border
                Area border = new Area(outerCircle);
                border.subtract(new Area(innerCircle));
                g2d.fill(border);

                // Draw purple circle symbol in the center
                g2d.setColor(purple);
                int symbolSize = borderThickness / 2;
                int symbolX = centerX - symbolSize / 2;
                int symbolY = centerY - symbolSize / 2;
                g2d.fillOval(symbolX, symbolY, symbolSize, symbolSize);
            }
            default -> {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2d.setColor(yellow);
                g2d.fillRect(0, 0, width, height);

                g2d.setColor(purple);
                int circleDiameter = Math.min(width, height) / 3;
                int circleX = (width - circleDiameter) / 2;
                int circleY = (height - circleDiameter) / 2;
                g2d.setStroke(new BasicStroke(circleDiameter / 10f));
                g2d.drawOval(circleX, circleY, circleDiameter, circleDiameter);
            }
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public static CommandData getCommandData() {
        // Flag choices (limited to 25 for Discord autocomplete)
        OptionData flagOption = new OptionData(OptionType.STRING, "flag", "Pride flag to apply", true);
        flagOption.addChoice("Pride (Traditional)", "pride");
        flagOption.addChoice("Progress Pride", "progress");
        flagOption.addChoice("Transgender", "trans");
        flagOption.addChoice("Bisexual", "bi");
        flagOption.addChoice("Pansexual", "pan");
        flagOption.addChoice("Lesbian", "lesbian");
        flagOption.addChoice("Asexual", "ace");
        flagOption.addChoice("Aromantic", "aro");
        flagOption.addChoice("Non-binary", "nonbinary");
        flagOption.addChoice("Genderfluid", "genderfluid");
        flagOption.addChoice("Agender", "agender");
        flagOption.addChoice("Demisexual", "demisexual");
        flagOption.addChoice("Demiromantic", "demiromantic");
        flagOption.addChoice("Polysexual", "polysexual");
        flagOption.addChoice("Omnisexual", "omnisexual");
        flagOption.addChoice("Questioning", "questioning");
        flagOption.addChoice("Intersex", "intersex");
        flagOption.addChoice("Polyamorous", "polyamorous");
        flagOption.addChoice("Neutrois", "neutrois");
        flagOption.addChoice("Two-Spirit", "twospirit");
        flagOption.addChoice("MLM/Vincian", "mlm");
        flagOption.addChoice("Aroace", "aroace");
        flagOption.addChoice("Graysexual", "graysexual");
        flagOption.addChoice("Grayromantic", "grayromantic");
        flagOption.addChoice("Bigender", "bigender");

        OptionData typeOption = new OptionData(OptionType.STRING, "type", "Type of image to process", false);
        typeOption.addChoice("Avatar (default)", "avatar");
        typeOption.addChoice("Image URL", "url");
        typeOption.addChoice("Upload File", "custom");

        OptionData styleOption = new OptionData(OptionType.STRING, "style", "How to apply the flag", false);
        styleOption.addChoice("Overlay (default)", "overlay");
        styleOption.addChoice("Border Frame", "border");

        return Commands.slash("pride", "Apply pride flag overlays to avatars or images")
                .addOptions(
                        flagOption,
                        typeOption,
                        new OptionData(OptionType.USER, "user", "User whose avatar to use (for avatar type)", false),
                        new OptionData(OptionType.STRING, "image_url", "Image URL (for url type)", false),
                        new OptionData(OptionType.ATTACHMENT, "image", "Image file (for custom type)", false),
                        styleOption);
    }

    @Override
    public String getName() {
        return "pride";
    }

    @Override
    public String getDescription() {
        return "Apply pride flag overlays to avatars or images";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    // Static method to get available flags (used by FlagsCommand)
    public static Map<String, Color[]> getAvailableFlags() {
        return new HashMap<>(PRIDE_FLAGS);
    }
}
