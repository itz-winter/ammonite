package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Random;

/**
 * Joke command for generating random jokes
 */
public class JokeCommand implements SlashCommand {

    private static final Random random = new Random();
    
    private static final String[] JOKES = {
        "Why did the programmer quit his job? He didn't get arrays!",
        "A SQL query goes into a bar, walks up to two tables and asks: 'Can I join you?'",
        "Why do Java developers wear glasses? Because they can't C#!",
        "How many programmers does it take to change a light bulb? None, that's a hardware problem!",
        "Why did the developer go broke? Because he used up all his cache!",
        "What's a computer's favorite snack? Microchips!",
        "Why don't robots ever panic? They have good backup!",
        "What do you call a programmer from Finland? Nerdic!",
        "Why did the computer go to the doctor? It had a virus!",
        "What's the object-oriented way to become wealthy? Inheritance!",
        "Why do programmers prefer dark mode? Because light attracts bugs!",
        "What do you call 8 hobbits? A hobbyte!",
        "Why did the smartphone need glasses? It lost all its contacts!",
        "What's the difference between a cat and a comma? A cat has claws at the end of paws, and a comma is a pause at the end of a clause!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What do you call a fish wearing a bowtie? Sofishticated!",
        "Why don't eggs tell jokes? They'd crack each other up!",
        "What do you call a fake noodle? An impasta!",
        "Why did the math book look so sad? Because it had too many problems!",
        "What do you call a sleeping bull? A bulldozer!",
        "Why did the coffee file a police report? It got mugged!",
        "What's the best way to communicate with a fish? Drop it a line!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What do you call a dinosaur that crashes his car? Tyrannosaurus Wrecks!",
        "Why did the bicycle fall over? Because it was two tired!",
        "What do you call a belt made of watches? A waist of time!",
        "Why did the cookie go to the doctor? Because it felt crumbly!",
        "What's orange and sounds like a parrot? A carrot!",
        "Why don't elephants use computers? They're afraid of the mouse!",
        "What do you call a cow with no legs? Ground beef!",
        "Why did the tomato turn red? Because it saw the salad dressing!",
        "What's the best way to watch a fly fishing tournament? Live stream!",
        "Why did the stadium get hot after the game? All of the fans left!",
        "What do you call a dog magician? A labracadabrador!",
        "Why did the picture go to jail? Because it was framed!",
        "What did the big flower say to the little flower? Hi, bud!",
        "Why don't skeletons fight each other? They don't have the guts!",
        "What did the ocean say to the beach? Nothing, it just waved!",
        "Why did the golfer wear two pairs of pants? In case he got a hole in one!",
        "What's the difference between a fish and a piano? You can't tuna fish!",
        "How does a penguin build its house? Igloos it together!",
        "What did one wall say to the other wall? I'll meet you at the corner!",
        "Why did the banana go to the doctor? It wasn't peeling well!",
        "What do you call a can opener that doesn't work? A can't opener!",
        "What did the left eye say to the right eye? Between you and me, something smells!",
        "What do you call a pig that does karate? A pork chop!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What's the difference between a poorly dressed man on a tricycle and a well-dressed man on a bicycle? Attire!",
        "Why did the scarecrow win an award? He was outstanding in his field!",
        "I told my wife she was drawing her eyebrows too high. She looked surprised!",
        "What's the best thing about Switzerland? I don't know, but the flag is a big plus!",
        "I invented a new word: Plagiarism!",
        "Did you hear about the mathematician who's afraid of negative numbers? He'll stop at nothing to avoid them!",
        "I'm reading a book about anti-gravity. It's impossible to put down!",
        "Want to hear a joke about construction? I'm still working on it!",
        "What do you call a factory that makes okay products? A satisfactory!",
        "Why did the chicken join a band? Because it had the drumsticks!",
        "Why did the coffee file a police report? It got mugged!",
    };

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String joke = JOKES[random.nextInt(JOKES.length)];
        
        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
            "😄 Random Joke",
            joke
        )).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("joke", "Get a random joke");
    }

    @Override
    public String getName() {
        return "joke";
    }

    @Override
    public String getDescription() {
        return "Generate a random joke";
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
