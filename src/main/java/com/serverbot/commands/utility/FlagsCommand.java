package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Command to list and display pride flags
 */
public class FlagsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "list" -> handleList(event);
            case "display" -> handleDisplay(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Unknown Subcommand", "Unknown subcommand: " + subcommand)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        Map<String, Color[]> flags = PrideCommand.getAvailableFlags();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏳️‍🌈 Available Pride Flags")
                .setDescription("Here are all the available pride flags you can use:")
                .setColor(new Color(255, 20, 147)); // Deep pink

        StringJoiner flagList = new StringJoiner("\n");
        for (String flagName : flags.keySet()) {
            String displayName = capitalize(flagName);
            String description = getFlagDescription(flagName);
            flagList.add("**" + displayName + "** (`" + flagName + "`) - " + description);
        }

        embed.addField("Flags", flagList.toString(), false);
        embed.addField("Usage",
                "Use `/pride avatar <flag>` or `/pride image <url> <flag>` to apply a flag\n" +
                        "Use `/flags display <flag>` to see what a flag looks like",
                false);

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleDisplay(SlashCommandInteractionEvent event) {
        String flagName = event.getOption("flag").getAsString().toLowerCase();
        Map<String, Color[]> flags = PrideCommand.getAvailableFlags();

        if (!flags.containsKey(flagName)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Flag",
                    "Unknown flag: `" + flagName + "`\n" +
                            "Use `/flags list` to see available flags."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        try {
            Color[] colors = flags.get(flagName);
            BufferedImage flagImage;

            // Special handling for intersex flag
            if ("intersex".equals(flagName)) {
                flagImage = createIntersexFlag(400, 200);
            } else {
                flagImage = createFlagImage(colors, 400, 200);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(flagImage, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏳️‍🌈 " + capitalize(flagName) + " Pride Flag")
                    .setDescription(getFlagDescription(flagName))
                    .setColor(colors[0])
                    .setImage("attachment://flag.png")
                    .addField("Usage", "`/pride avatar " + flagName + "`\n`/pride image <url> " + flagName + "`",
                            false);

            event.getHook().sendMessageEmbeds(embed.build())
                    .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageData, "flag.png"))
                    .queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Display Failed",
                    "Failed to create flag display: " + e.getMessage())).queue();
        }
    }

    private BufferedImage createFlagImage(Color[] colors, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int stripeHeight = height / colors.length;

        for (int i = 0; i < colors.length; i++) {
            g2d.setColor(colors[i]);
            g2d.fillRect(0, i * stripeHeight, width, stripeHeight);
        }

        g2d.dispose();
        return image;
    }

    private BufferedImage createIntersexFlag(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Yellow background
        g2d.setColor(new Color(255, 218, 0));
        g2d.fillRect(0, 0, width, height);

        // Purple circle in center
        g2d.setColor(new Color(121, 2, 170));
        int circleDiameter = Math.min(width, height) / 3;
        int circleX = (width - circleDiameter) / 2;
        int circleY = (height - circleDiameter) / 2;
        g2d.setStroke(new BasicStroke(circleDiameter / 15f));
        g2d.drawOval(circleX, circleY, circleDiameter, circleDiameter);

        g2d.dispose();
        return image;
    }

    private String getFlagDescription(String flagName) {
        return switch (flagName.toLowerCase()) {
            case "pride" -> "Traditional 6-stripe rainbow pride flag representing LGBTQ+ community";
            case "progress" -> "Progress Pride flag including trans, POC, and traditional pride colors";
            case "trans" -> "Transgender pride flag with light blue, pink, and white stripes";
            case "bi" -> "Bisexual pride flag with pink, purple, and blue stripes";
            case "pan" -> "Pansexual pride flag with pink, yellow, and blue stripes";
            case "lesbian" -> "Lesbian pride flag with orange, white, and pink stripes";
            case "ace" -> "Asexual pride flag with black, gray, white, and purple stripes";
            case "aro" -> "Aromantic pride flag with green, light green, white, gray, and black stripes";
            case "nonbinary" -> "Non-binary pride flag with yellow, white, purple, and black stripes";
            case "genderfluid" -> "Genderfluid pride flag with pink, white, purple, black, and blue stripes";
            case "agender" -> "Agender pride flag representing those without gender identity";
            case "demisexual" ->
                "Demisexual pride flag for those who experience sexual attraction only after forming emotional bonds";
            case "demiromantic" ->
                "Demiromantic pride flag for those who experience romantic attraction only after forming emotional bonds";
            case "polysexual" -> "Polysexual pride flag for attraction to multiple but not all genders";
            case "omnisexual" ->
                "Omnisexual pride flag for attraction to all genders with awareness of gender differences";
            case "questioning" -> "Questioning pride flag for those exploring their gender identity";
            case "intersex" -> "Intersex pride flag representing intersex individuals with yellow and purple";
            case "polyamorous" -> "Polyamorous pride flag representing ethical non-monogamy";
            case "neutrois" -> "Neutrois pride flag for neutral or null gender identity";
            case "twospirit" -> "Two-Spirit pride flag representing Native American/Indigenous third gender roles";
            case "mlm" -> "MLM/Vincian pride flag representing men loving men";
            case "aroace" -> "Aromantic Asexual pride flag for those who are both aromantic and asexual";
            case "graysexual" -> "Graysexual pride flag for those on the asexual spectrum";
            case "grayromantic" -> "Grayromantic pride flag for those on the aromantic spectrum";
            case "bigender" -> "Bigender pride flag for those who identify as two genders";
            case "trigender" -> "Trigender pride flag for those who identify as three genders";
            case "pangender" -> "Pangender pride flag for those who identify with many or all genders";
            case "omnigender" -> "Omnigender pride flag for those who experience all gender identities";
            case "demiboy" -> "Demiboy pride flag for partial connection to being a boy/man";
            case "demigirl" -> "Demigirl pride flag for partial connection to being a girl/woman";
            case "queer" -> "Queer pride flag representing the broader LGBTQ+ community";
            case "demigender" -> "Demigender pride flag for partial connection to any gender identity";
            case "androsexual" -> "Androsexual pride flag for attraction to masculinity regardless of gender";
            case "gynesexual" -> "Gynesexual pride flag for attraction to femininity regardless of gender";
            case "abrosexual" -> "Abrosexual pride flag for those whose sexual orientation changes over time";
            case "panromantic" -> "Panromantic pride flag for romantic attraction to all genders";
            case "platonic" -> "Platonic pride flag representing deep non-romantic relationships";
            case "alterous" -> "Alterous pride flag for attraction that is neither romantic nor platonic";
            case "bear" -> "Bear Brotherhood pride flag representing the bear community within gay culture";
            case "leather" -> "Leather Pride flag representing the leather, BDSM, and fetish communities";
            default -> "Pride flag representing the " + capitalize(flagName) + " community";
        };
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public static CommandData getCommandData() {
        return Commands.slash("flags", "List and display pride flags")
                .addSubcommands(
                        new SubcommandData("list", "List all available pride flags"),
                        new SubcommandData("display", "Display a specific pride flag")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "flag", "Flag to display", true)
                                                .addChoices(
                                                        new Command.Choice("Pride (Traditional)", "pride"),
                                                        new Command.Choice("Progress Pride", "progress"),
                                                        new Command.Choice("Transgender", "trans"),
                                                        new Command.Choice("Bisexual", "bi"),
                                                        new Command.Choice("Pansexual", "pan"),
                                                        new Command.Choice("Lesbian", "lesbian"),
                                                        new Command.Choice("Asexual", "ace"),
                                                        new Command.Choice("Aromantic", "aro"),
                                                        new Command.Choice("Non-binary", "nonbinary"),
                                                        new Command.Choice("Genderfluid", "genderfluid"),
                                                        new Command.Choice("Agender", "agender"),
                                                        new Command.Choice("Demisexual", "demisexual"),
                                                        new Command.Choice("Demiromantic", "demiromantic"),
                                                        new Command.Choice("Polysexual", "polysexual"),
                                                        new Command.Choice("Omnisexual", "omnisexual"),
                                                        new Command.Choice("Questioning", "questioning"),
                                                        new Command.Choice("Intersex", "intersex"),
                                                        new Command.Choice("Polyamorous", "polyamorous"),
                                                        new Command.Choice("Neutrois", "neutrois"),
                                                        new Command.Choice("Two-Spirit", "twospirit"),
                                                        new Command.Choice("MLM/Vincian", "mlm"),
                                                        new Command.Choice("Aroace", "aroace"),
                                                        new Command.Choice("Graysexual", "graysexual"),
                                                        new Command.Choice("Grayromantic", "grayromantic"),
                                                        new Command.Choice("Bigender", "bigender")
                                                // Note: Discord allows max 25 choices. Use /flags list to see all
                                                // available flags
                                                )));
    }

    @Override
    public String getName() {
        return "flags";
    }

    @Override
    public String getDescription() {
        return "List and display pride flags";
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
