package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pride flag overlay command - Translated from pride-bee reference algorithm
 */
public class PrideCommand implements SlashCommand {

    private static final int IMAGE_SIZE = 1024;

    private static final Map<String, String> FLAG_FILENAMES = new HashMap<>();
    private static final Color EMBED_COLOR = new Color(255, 105, 180);

    static {
        FLAG_FILENAMES.put("abrosexual", "abrosexual.png");
        FLAG_FILENAMES.put("aceflux", "aceflux.png");
        FLAG_FILENAMES.put("agender", "agender.png");
        FLAG_FILENAMES.put("ally", "ally.png");
        FLAG_FILENAMES.put("androgyne", "androgyne.png");
        FLAG_FILENAMES.put("aroace", "aroace.png");
        FLAG_FILENAMES.put("aroace2", "aroace2.png");
        FLAG_FILENAMES.put("aroflux", "aroflux.png");
        FLAG_FILENAMES.put("aromantic", "aromantic.png");
        FLAG_FILENAMES.put("asexual", "asexual.png");
        FLAG_FILENAMES.put("aurorian", "aurorian.png");
        FLAG_FILENAMES.put("bigender", "bigender.png");
        FLAG_FILENAMES.put("bisexual", "bisexual.png");
        FLAG_FILENAMES.put("boyflux", "boyflux.png");
        FLAG_FILENAMES.put("butch", "butch.png");
        FLAG_FILENAMES.put("butchlesbian", "butchlesbian.png");
        FLAG_FILENAMES.put("butchlesbian2", "butchlesbian2.png");
        FLAG_FILENAMES.put("butchlesbian3", "butchlesbian3.png");
        FLAG_FILENAMES.put("catgender", "catgender.png");
        FLAG_FILENAMES.put("cupioromantic", "cupioromantic.png");
        FLAG_FILENAMES.put("demibisexual", "demibisexual.png");
        FLAG_FILENAMES.put("demiboy", "demiboy.png");
        FLAG_FILENAMES.put("demigirl", "demigirl.png");
        FLAG_FILENAMES.put("deminonbinary", "deminonbinary.png");
        FLAG_FILENAMES.put("demiromantic", "demiromantic.png");
        FLAG_FILENAMES.put("demisexual", "demisexual.png");
        FLAG_FILENAMES.put("gay", "gay.png");
        FLAG_FILENAMES.put("genderfae", "genderfae.png");
        FLAG_FILENAMES.put("genderfaun", "genderfaun.png");
        FLAG_FILENAMES.put("genderfluid", "genderfluid.png");
        FLAG_FILENAMES.put("genderflux", "genderflux.png");
        FLAG_FILENAMES.put("genderqueer", "genderqueer.png");
        FLAG_FILENAMES.put("girlflux", "girlflux.png");
        FLAG_FILENAMES.put("graygender", "graygender.png");
        FLAG_FILENAMES.put("grayromantic", "grayromantic.png");
        FLAG_FILENAMES.put("graysexual", "graysexual.png");
        FLAG_FILENAMES.put("lesbian", "lesbian.png");
        FLAG_FILENAMES.put("lesboy", "lesboy.png");
        FLAG_FILENAMES.put("lgbt", "lgbt.png");
        FLAG_FILENAMES.put("lunarian", "lunarian.png");
        FLAG_FILENAMES.put("neptunic", "neptunic.png");
        FLAG_FILENAMES.put("nonbinary", "nonbinary.png");
        FLAG_FILENAMES.put("omnisexual", "omnisexual.png");
        FLAG_FILENAMES.put("pangender", "pangender.png");
        FLAG_FILENAMES.put("pansexual", "pansexual.png");
        FLAG_FILENAMES.put("polyamorous", "polyamorous.png");
        FLAG_FILENAMES.put("polysexual", "polysexual.png");
        FLAG_FILENAMES.put("queer", "queer.png");
        FLAG_FILENAMES.put("queerplatonic", "queerplatonic.png");
        FLAG_FILENAMES.put("queerplatonic2", "queerplatonic2.png");
        FLAG_FILENAMES.put("sapphic", "sapphic.png");
        FLAG_FILENAMES.put("selenosexual", "selenosexual.png");
        FLAG_FILENAMES.put("singularian", "singularian.png");
        FLAG_FILENAMES.put("solarian", "solarian.png");
        FLAG_FILENAMES.put("spacialian", "spacialian.png");
        FLAG_FILENAMES.put("stellarian", "stellarian.png");
        FLAG_FILENAMES.put("transfeminine", "transfeminine.png");
        FLAG_FILENAMES.put("transgender", "transgender.png");
        FLAG_FILENAMES.put("transmasculine", "transmasculine.png");
    }

    public static Set<String> getAvailableFlagNames() {
        return new HashSet<>(FLAG_FILENAMES.keySet());
    }

    public static BufferedImage getFlagImage(String flagName) throws IOException {
        String filename = FLAG_FILENAMES.get(flagName);
        if (filename == null) {
            throw new IOException("Unknown flag: " + flagName);
        }
        try (InputStream is = PrideCommand.class.getResourceAsStream("/flags/" + filename)) {
            if (is == null) {
                throw new IOException("Could not load resource for " + flagName);
            }
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new IOException("Could not decode image for " + flagName);
            }
            return image;
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
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
                        "`/pride flag:<flag> [type:avatar] [user:@user] [style:border]`\n" +
                                "`/pride flag:<flag> type:url image_url:<url> [style:border]`\n" +
                                "`/pride flag:<flag> type:custom image:<file> [style:border]`",
                        false)
                .addField("**Parameters**",
                        "• `flag` - Pride flag to apply (required)\n" +
                                "• `type` - Type of image (avatar/url/custom, default: avatar)\n" +
                                "• `user` - User for avatar type (default: you)\n" +
                                "• `image_url` - URL for url type\n" +
                                "• `image` - File upload for custom type\n" +
                                "• `style` - How to apply flag (overlay/border, default: border)\n" +
                                "• `border_style` - Shape of border when style=border (circular/frame, default: circular)\n" +
                                "• `border_thickness` - Border thickness in pixels (5–100, default: 20)",
                        false)
                .addField("**Available Flags**",
                        "Traditional Pride, Progress Pride, Transgender, Bisexual, Pansexual, Lesbian, " +
                                "Asexual, Aromantic, Non-binary, Genderfluid, Agender, Demisexual, MLM, Aroace, " +
                                "and many more...",
                        false)
                .addField("**Examples**",
                        "`/pride flag:trans` - Apply trans flag border to your avatar\n" +
                                "`/pride flag:pride user:@friend` - Apply pride flag border to friend's avatar\n"
                                +
                                "`/pride flag:lesbian type:url image_url:https://example.com/image.png style:overlay`",
                        false)
                .setFooter("Use -!help to dismiss future help messages");

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        List<Command.Choice> choices = new ArrayList<>();
        String input = event.getFocusedOption().getValue().toLowerCase();

        for (String flagName : FLAG_FILENAMES.keySet()) {
            if (input.isEmpty() || flagName.contains(input)) {
                choices.add(new Command.Choice(flagName, flagName));
                if (choices.size() >= 25) {
                    break;
                }
            }
        }

        event.replyChoices(choices).queue();
    }

    private void handleAvatar(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();
        String flagName = event.getOption("flag").getAsString().toLowerCase();
        String style = event.getOption("style") != null ? event.getOption("style").getAsString() : "border";
        String borderStyle = event.getOption("border_style") != null ? event.getOption("border_style").getAsString() : "circular";
        int borderThickness = event.getOption("border_thickness") != null ? (int) event.getOption("border_thickness").getAsLong() : 20;

        if (!FLAG_FILENAMES.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            // Use highest-resolution avatar URL (4096px) like AvatarCommand does
            String avatarUrl = targetUser.getEffectiveAvatarUrl() + "?size=4096";
            avatarUrl = com.serverbot.utils.AvatarCacheManager.cacheAvatar(avatarUrl);

            BufferedImage result = applyPrideFlag(avatarUrl, flagName, style, borderStyle, borderThickness);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(result, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 Pride Avatar")
                    .setDescription("**User:** " + targetUser.getAsMention() + "\n" +
                            "**Flag:** " + capitalize(flagName) + "\n" +
                            "**Style:** " + capitalize(style) +
                            (style.equals("border") ? "\n**Border:** " + capitalize(borderStyle) + " · " + borderThickness + "px" : ""))
                    .setColor(EMBED_COLOR)
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
        String style = event.getOption("style") != null ? event.getOption("style").getAsString() : "border";
        String borderStyle = event.getOption("border_style") != null ? event.getOption("border_style").getAsString() : "circular";
        int borderThickness = event.getOption("border_thickness") != null ? (int) event.getOption("border_thickness").getAsLong() : 20;

        if (!FLAG_FILENAMES.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            BufferedImage result = applyPrideFlag(imageUrl, flagName, style, borderStyle, borderThickness);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(result, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 Pride Image")
                    .setDescription("**Flag:** " + capitalize(flagName) + "\n" +
                            "**Style:** " + capitalize(style) +
                            (style.equals("border") ? "\n**Border:** " + capitalize(borderStyle) + " · " + borderThickness + "px" : ""))
                    .setColor(EMBED_COLOR)
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
        String style = event.getOption("style") != null ? event.getOption("style").getAsString() : "border";
        String borderStyle = event.getOption("border_style") != null ? event.getOption("border_style").getAsString() : "circular";
        int borderThickness = event.getOption("border_thickness") != null ? (int) event.getOption("border_thickness").getAsLong() : 20;

        if (!FLAG_FILENAMES.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (!attachment.isImage()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid File",
                    "Please upload an image file (PNG, JPG, GIF, etc.)")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            String imageUrl = attachment.getUrl();
            BufferedImage result = applyPrideFlag(imageUrl, flagName, style, borderStyle, borderThickness);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(result, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 Pride Custom Image")
                    .setDescription("**Flag:** " + capitalize(flagName) + "\n" +
                            "**Style:** " + capitalize(style) +
                            (style.equals("border") ? "\n**Border:** " + capitalize(borderStyle) + " · " + borderThickness + "px" : "") + "\n" +
                            "**Original:** " + attachment.getFileName())
                    .setColor(EMBED_COLOR)
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

    // ========================================================================
    // Image processing - translated from pride-bee reference algorithm
    // ========================================================================

    private BufferedImage applyPrideFlag(String imageUrl, String flagName, String arrangement, String borderStyle, int borderThickness) throws IOException {
        // Download the source image
        URL url = new URL(imageUrl);
        BufferedImage sourceImage = ImageIO.read(url);
        if (sourceImage == null) {
            throw new IOException("Could not read image from URL");
        }

        // Resize source to 1024x1024 (same as reference: IMAGE_SIZE=1024)
        BufferedImage sourceResized = resizeImage(sourceImage, IMAGE_SIZE, IMAGE_SIZE);

        // Load the flag image
        BufferedImage flagImage = loadFlagImage(flagName);
        if (flagImage == null) {
            throw new IOException("Could not load flag image: " + flagName);
        }
        // Resize flag to 1024x1024
        BufferedImage flagResized = resizeImage(flagImage, IMAGE_SIZE, IMAGE_SIZE);

        BufferedImage result;
        switch (arrangement.toLowerCase()) {
            case "overlay" -> result = renderOverlay(flagResized, sourceResized);
            case "border" -> {
                if ("frame".equals(borderStyle)) {
                    result = renderFrame(flagResized, sourceResized, borderThickness);
                } else {
                    result = renderCircularBorder(flagResized, sourceResized, borderThickness);
                }
            }
            default -> result = renderCircularBorder(flagResized, sourceResized, borderThickness);
        }

        return result;
    }

    /**
     * Renders the flag as a circular border around the avatar.
     * Algorithm (from reference):
     * 1. Make avatar circular
     * 2. Resize circular avatar to IMAGE_SIZE - padding*2 (padding = borderThickness)
     * 3. On a canvas with the flag, overlay the circular avatar at (padding, padding)
     * 4. Clip the whole result to a circle
     */
    private BufferedImage renderCircularBorder(BufferedImage flagImage, BufferedImage avatarImage, int borderThickness) {
        int padding = borderThickness;
        int innerSize = IMAGE_SIZE - padding * 2;

        // Step 1: Make avatar circular
        BufferedImage circularAvatar = applyCircleClip(avatarImage);

        // Step 2: Resize circular avatar to inner size
        BufferedImage resizedAvatar = resizeImage(circularAvatar, innerSize, innerSize);

        // Step 3: On a canvas with the flag, overlay the circular avatar
        BufferedImage composited = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = composited.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Draw flag as background
        g2d.drawImage(flagImage, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);

        // Draw circular avatar on top, centered with padding
        g2d.drawImage(resizedAvatar, padding, padding, innerSize, innerSize, null);

        g2d.dispose();

        // Step 4: Clip the whole result to a circle
        return applyCircleClip(composited);
    }

    /**
     * Renders the flag as an overlay blended over the full avatar (multiply blend).
     * Algorithm (from reference with mask=true):
     * 1. Composite flag over avatar with multiply blend
     * 2. Clip to circle
     */
    private BufferedImage renderOverlay(BufferedImage flagImage, BufferedImage avatarImage) {
        BufferedImage result = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Draw the avatar as base
        g2d.drawImage(avatarImage, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);

        // Multiply blend the flag over the avatar
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2d.drawImage(flagImage, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        g2d.dispose();

        return applyCircleClip(result);
    }

    /**
     * Renders the flag as a rectangular frame border around the avatar.
     * The avatar stays in the center, flag stripes appear on all four sides.
     */
    private BufferedImage renderFrame(BufferedImage flagImage, BufferedImage avatarImage, int borderThickness) {
        int padding = borderThickness;
        int innerSize = IMAGE_SIZE - padding * 2;

        BufferedImage result = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Draw flag as background
        g2d.drawImage(flagImage, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);

        // Draw avatar on top, centered
        BufferedImage resizedAvatar = resizeImage(avatarImage, innerSize, innerSize);
        g2d.drawImage(resizedAvatar, padding, padding, innerSize, innerSize, null);

        g2d.dispose();

        return result;
    }

    /**
     * Creates a circular clip on the image. Everything outside the
     * largest inscribed circle becomes transparent.
     */
    private BufferedImage applyCircleClip(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int diameter = Math.min(width, height);

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Create an ellipse-shaped clip
        Ellipse2D.Double circle = new Ellipse2D.Double(
            (width - diameter) / 2.0,
            (height - diameter) / 2.0,
            diameter,
            diameter
        );
        g2d.setClip(circle);

        // Draw the source image clipped to the circle
        g2d.drawImage(source, 0, 0, null);

        g2d.dispose();

        return result;
    }

    /**
     * Resizes an image to the specified dimensions.
     */
    private BufferedImage resizeImage(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }

    private BufferedImage loadFlagImage(String flagName) {
        String filename = FLAG_FILENAMES.getOrDefault(flagName, flagName + ".png");
        try (InputStream is = getClass().getResourceAsStream("/flags/" + filename)) {
            if (is == null) return null;
            return ImageIO.read(is);
        } catch (IOException e) {
            return null;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public static CommandData getCommandData() {
        OptionData flagOption = new OptionData(OptionType.STRING, "flag", "Pride flag to apply", true)
                .setAutoComplete(true);

        OptionData typeOption = new OptionData(OptionType.STRING, "type", "Type of image to process", false);
        typeOption.addChoice("Avatar (default)", "avatar");
        typeOption.addChoice("Image URL", "url");
        typeOption.addChoice("Upload File", "custom");

        OptionData styleOption = new OptionData(OptionType.STRING, "style", "How to apply the flag", false);
        styleOption.addChoice("Overlay", "overlay");
        styleOption.addChoice("Border Frame (default)", "border");

        OptionData borderStyleOption = new OptionData(OptionType.STRING, "border_style", "Shape of the border (when style is border, default: circular)", false);
        borderStyleOption.addChoice("Circular (default)", "circular");
        borderStyleOption.addChoice("Frame", "frame");

        OptionData borderThicknessOption = new OptionData(OptionType.INTEGER, "border_thickness", "Border thickness in pixels (when style is border, default: 20)", false)
                .setMinValue(5)
                .setMaxValue(100);

        return Commands.slash("pride", "Apply pride flag overlays to avatars or images")
                .addOptions(
                        flagOption,
                        typeOption,
                        new OptionData(OptionType.USER, "user", "User whose avatar to use (for avatar type)", false),
                        new OptionData(OptionType.STRING, "image_url", "Image URL (for url type)", false),
                        new OptionData(OptionType.ATTACHMENT, "image", "Image file (for custom type)", false),
                        styleOption,
                        borderStyleOption,
                        borderThicknessOption);
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
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

}
