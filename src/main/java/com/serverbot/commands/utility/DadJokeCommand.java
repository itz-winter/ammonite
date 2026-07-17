package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Random;

/**
 * Dad joke command for generating random dad jokes
 */
public class DadJokeCommand implements SlashCommand {

    private static final Random random = new Random();
    
    private static final String[] DAD_JOKES = {
        "Why don't scientists trust atoms? Because they make up everything!",
        "I told my wife she was drawing her eyebrows too high. She looked surprised.",
        "What do you call a fake noodle? An impasta!",
        "Why did the scarecrow win an award? He was outstanding in his field!",
        "I used to hate facial hair, but then it grew on me.",
        "What do you call a bear with no teeth? A gummy bear!",
        "Why don't eggs tell jokes? They'd crack each other up!",
        "What's the best thing about Switzerland? I don't know, but the flag is a big plus.",
        "I invented a new word: Plagiarism!",
        "Did you hear about the mathematician who's afraid of negative numbers? He'll stop at nothing to avoid them!",
        "Why did the coffee file a police report? It got mugged!",
        "What did the ocean say to the beach? Nothing, it just waved.",
        "Why don't skeletons fight each other? They don't have the guts.",
        "What do you call a sleeping bull? A bulldozer!",
        "I'm reading a book about anti-gravity. It's impossible to put down!",
        "Why did the bicycle fall over? Because it was two tired!",
        "What do you call a dinosaur that crashes his car? Tyrannosaurus Wrecks!",
        "I would avoid the sushi if I was you. It's a little fishy.",
        "Want to hear a joke about construction? I'm still working on it.",
        "What do you call a factory that makes okay products? A satisfactory!",
        "Dear Math, grow up and solve your own problems.",
        "What did the janitor say when he jumped out of the closet? Supplies!",
        "Have you heard about the chocolate record player? It sounds pretty sweet.",
        "What did the grape do when he got stepped on? He let out a little wine!",
        "I don't trust stairs. They're always up to something.",
        "What do you call a pig that does karate? A pork chop!",
        "Why did the golfer wear two pairs of pants? In case he got a hole in one!",
        "What's the difference between a fish and a piano? You can't tuna fish!",
        "How does a penguin build its house? Igloos it together!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What did one wall say to the other wall? I'll meet you at the corner!",
        "Why did the cookie go to the doctor? Because it felt crumbly!",
        "What do you call a belt made of watches? A waist of time!",
        "Why did the banana go to the doctor? It wasn't peeling well!",
        "What's orange and sounds like a parrot? A carrot!",
        "Why don't elephants use computers? They're afraid of the mouse!",
        "What do you call a cow with no legs? Ground beef!",
        "Why did the tomato turn red? Because it saw the salad dressing!",
        "What's the best way to watch a fly fishing tournament? Live stream!",
        "Why did the math book look so sad? Because it had too many problems!",
        "What do you call a can opener that doesn't work? A can't opener!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What did the left eye say to the right eye? Between you and me, something smells.",
        "Why did the stadium get hot after the game? All of the fans left!",
        "What do you call a dog magician? A labracadabrador!",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What's the difference between a poorly dressed man on a tricycle and a well-dressed man on a bicycle? Attire!",
        "Why did the picture go to jail? Because it was framed!",
        "What did the big flower say to the little flower? Hi, bud!",
        "I'm afraid for the calendar. Its days are numbered.",
        "Why don't eggs tell jokes? They'd crack each other up.",
        "I used to hate facial hair, but then it grew on me.",
        "I don't trust stairs. They're always up to something.",
        "Why did the scarecrow win an award? Because he was outstanding in his field.",
        "I only know 25 letters of the alphabet. I don't know y.",
        "Did you hear about the guy who invented Lifesavers? He made a mint.",
        "I told my wife she was drawing her eyebrows too high. She looked surprised.",
        "Why don't skeletons fight each other? They don't have the guts.",
        "I used to play piano by ear. Now I use my hands.",
        "Why did the math book look sad? Because it had too many problems.",
        "I asked my dog what's two minus two. He said nothing.",
        "Why did the bicycle fall over? Because it was two-tired.",
        "I once tried to catch fog. Mist.",
        "I'm reading a book about anti-gravity. It's impossible to put down.",
        "Why don't oysters donate to charity? Because they're shellfish.",
        "What do you call fake spaghetti? An impasta.",
        "I would tell you a joke about construction, but I'm still working on it.",
        "Why did the coffee file a police report? It got mugged.",
        "What do you call cheese that isn't yours? Nacho cheese.",
        "Why did the golfer bring two pairs of pants? In case he got a hole in one.",
        "I used to be addicted to soap. But I'm clean now.",
        "Why did the tomato blush? Because it saw the salad dressing.",
        "What do you call a factory that makes okay products? A satisfactory.",
        "Why can't you hear a pterodactyl go to the bathroom? Because the P is silent.",
        "I ordered a chicken and an egg online. I'll let you know which comes first.",
        "Why did the picture go to jail? Because it was framed.",
        "I don't play soccer because I enjoy the sport. I'm just doing it for kicks.",
        "What do you call an alligator in a vest? An investigator.",
        "Why did the math teacher break up with the calculator? It was too calculated.",
        "I used to be a baker, but I couldn't make enough dough.",
        "Why do cows wear bells? Because their horns don't work.",
        "What did the janitor say when he jumped out of the closet? Supplies!",
        "I told a joke about a roof once. It went over everyone's head.",
        "Why did the computer go to the doctor? Because it caught a virus.",
        "What do you call a sleeping bull? A bulldozer.",
        "I tried to write a joke about time travel. You didn't like it.",
        "Why don't some couples go to the gym? Because some relationships don't work out.",
        "What do you call an elephant that doesn't matter? An irrelephant.",
        "Why did the cookie go to the hospital? Because it felt crummy.",
        "I used to be a banker, but I lost interest.",
        "Why did the chicken join a band? Because it had the drumsticks.",
        "I don't trust atoms. They make up everything.",
        "What do you call a belt made of watches? A waist of time.",
        "Why did the stadium get hot after the game? All the fans left.",
        "I tried to learn origami, but I folded.",
        "Why did the grape stop in the middle of the road? Because it ran out of juice.",
        "What's brown and sticky? A stick.",
        "I told my friend ten jokes to make him laugh. Sadly, no pun in ten did.",
        "Why do bees have sticky hair? Because they use honeycombs.",
        "I'd tell you a joke about pizza, but it's a little cheesy.",
        "Why did the fish blush? Because it saw the ocean's bottom.",
        "Why did the belt get arrested? For holding up a pair of pants.",
        "Why don't scientists trust atoms? Because they make up everything!",
        "What do you call a snowman with a six-pack? An abdominal snowman.",
        "Why did the coffee file a police report? It got mugged.",
        "What do you call a dinosaur with an extensive vocabulary? A thesaurus.",
        "Why did the cookie go to the doctor? Because it felt crummy.",
        "What do you call a fake noodle? An impasta.",
        "Why did the bicycle fall over? Because it was two-tired.",
        "I used to be addicted to soap, but I'm clean now.",
        "What do you call a bear with no teeth? A gummy bear.",
        "Why don't skeletons fight each other? They don't have the guts.",
        "What do you call a sleeping bull? A bulldozer.",
        "Why did the math book look sad? Because it had too many problems.",
        "I told my wife she was drawing her eyebrows too high. She looked surprised.",
    };

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String joke = DAD_JOKES[random.nextInt(DAD_JOKES.length)];
        event.reply(joke).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("dadjoke", "Get a random dad joke");
    }

    @Override
    public String getName() {
        return "dadjoke";
    }

    @Override
    public String getDescription() {
        return "Generate a random dad joke";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }
}
