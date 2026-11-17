package com.zoeykl.simpleboard;

import java.util.*;

/** Original English defaults. Ambiguous words such as well/were/ill stay intact. */
final class EnglishDefaults {
    static final Map<String,String> CONTRACTIONS;
    static final Map<String,String> NAMES;
    static {
        Map<String,String> contractions=new HashMap<>();
        for(String word:("don't doesn't didn't can't couldn't shouldn't won't wouldn't isn't aren't wasn't weren't hasn't haven't hadn't mustn't needn't "
            +"I'm I've you're you've you'll you'd they're they've they'll they'd we've that's there's what's who's how's where's when's why's "
            +"ain't couldn't've shouldn't've wouldn't've might've must've should've could've would've").split(" "))
            contractions.put(word.replace("'",""),word);
        CONTRACTIONS=Collections.unmodifiableMap(contractions);
        Map<String,String> names=new HashMap<>();
        for(String word:("I Monday Tuesday Wednesday Thursday Friday Saturday Sunday January February April June July August September October November December "
            +"English Spanish French German Italian Portuguese Japanese Korean Chinese Arabic Russian Hindi Bengali Dutch Swedish Norwegian Danish Finnish Polish Ukrainian Greek Latin Hebrew Turkish Vietnamese Thai Indonesian "
            +"America American Europe European Africa African Asia Asian Australia Australian Antarctica India Indian China Japan Korea Russia Germany France Italy Spain Portugal England Scotland Ireland Wales Canada Mexico Brazil Argentina Chile Peru Colombia Venezuela Ecuador Bolivia Uruguay Paraguay "
            +"Belgium Netherlands Switzerland Austria Poland Ukraine Romania Bulgaria Hungary Czechia Slovakia Slovenia Croatia Serbia Albania Greece Turkey Israel Palestine Lebanon Syria Jordan Egypt Morocco Algeria Tunisia Libya Sudan Ethiopia Kenya Uganda Tanzania Nigeria Ghana Senegal Cameroon Congo Rwanda Botswana Zimbabwe Zambia Mozambique Namibia Somalia Madagascar Norway Sweden Finland Denmark Iceland Estonia Latvia Lithuania Belarus Moldova Georgia Armenia Azerbaijan Kazakhstan Uzbekistan Afghanistan Pakistan Bangladesh Nepal Bhutan Myanmar Thailand Vietnam Cambodia Laos Malaysia Indonesia Singapore Philippines Taiwan Mongolia Iran Iraq Yemen Oman Qatar Bahrain Kuwait Saudi Dubai "
            +"London Paris Berlin Rome Madrid Lisbon Moscow Kyiv Beijing Tokyo Seoul Delhi Mumbai Kolkata Chennai Dhaka Kathmandu Islamabad Karachi Lahore Kabul Tehran Baghdad Jerusalem Cairo Nairobi Lagos Johannesburg Sydney Melbourne Toronto Ottawa Vancouver Montreal Boston Chicago Seattle Atlanta Detroit Philadelphia Pittsburgh Baltimore Houston Dallas Austin Denver Phoenix Portland Miami Orlando Nashville Memphis Cincinnati Cleveland Milwaukee Minneapolis Indianapolis Brooklyn Bronx Manhattan Queens "
            +"Android Google Samsung Bluetooth Firefox Mozilla Linux Ubuntu Debian Fedora Microsoft GitHub GitLab WhatsApp Instagram YouTube Reddit Netflix Spotify TikTok ChatGPT OpenAI SimpleBoard HeliBoard Vosk Whisper Wi-Fi USB GPS PDF APK HTML CSS JavaScript Python Java Kotlin SQL SQLite ONNX NVIDIA AMD Intel "
            +"Zo Zoey Zoe Alice Alicia Alison Allison Amanda Amelia Amy Andrea Angela Anna Anne Annie Ashley Audrey Barbara Beatrice Bella Beth Betty Beverly Bianca Brenda Brianna Brittany Caitlin Caitlyn Camila Carla Carmen Caroline Carolyn Cassandra Catherine Cecilia Charlotte Chelsea Chloe Christina Christine Claire Claudia Danielle Deborah Denise Diana Diane Dorothy Eleanor Elena Elizabeth Ella Ellen Emily Emma Erica Erin Evelyn Fiona Frances Hannah Heather Helen Isabella Jacqueline Jane Janet Janice Jennifer Jessica Jill Joanna Joanne Josephine Joyce Judith Julia Julie Karen Katherine Kathleen Kathryn Katie Kayla Kelly Kimberly Kristen Laura Lauren Leah Leslie Lillian Linda Lisa Lorraine Louise Lucy Lydia Madison Margaret Maria Marie Marilyn Martha Mary Megan Melissa Michelle Miranda Molly Monica Nancy Natalie Natasha Nicole Nina Olivia Pamela Patricia Paula Pauline Penelope Rachel Rebecca Renee Riley Rita Roberta Robin Rosa Ruby Ruth Sabrina Samantha Sandra Sara Sarah Savannah Shannon Sharon Sheila Shirley Sofia Sophia Stephanie Susan Suzanne Sylvia Teresa Theresa Tiffany Tracy Valerie Vanessa Veronica Victoria Violet Virginia Vivian Wendy Whitney Yvonne "
            +"Aaron Adam Adrian Alan Albert Alexander Andrew Anthony Arthur Benjamin Bradley Brandon Brian Bruce Bryan Caleb Cameron Carl Carlos Charles Christian Christopher Daniel David Dennis Derek Donald Douglas Dylan Edward Elijah Eric Ethan Eugene Evan Felix Francis Frank Gabriel Gary Gavin George Gerald Gregory Harold Harry Henry Howard Hugh Ian Isaac Jack Jacob James Jason Jeffrey Jeremy Jesse Joel John Jonathan Jordan Joseph Joshua Juan Julian Justin Kenneth Kevin Kyle Lawrence Leo Leonard Liam Logan Louis Lucas Luke Malcolm Marcus Mark Martin Mason Matthew Michael Nathan Nicholas Noah Oliver Oscar Owen Patrick Paul Peter Philip Phillip Rafael Ralph Raymond Richard Robert Roger Ronald Roy Russell Ryan Samuel Scott Sean Sebastian Seth Shawn Simon Stephen Steve Steven Theodore Thomas Timothy Tobias Tony Travis Trevor Tyler Victor Vincent Walter Wayne William Zachary").split(" "))names.put(word.toLowerCase(Locale.US),word);
        for(String ambiguous:new String[]{"mark","frank","ruby","robin","violet","mason","victor"})names.remove(ambiguous);
        NAMES=Collections.unmodifiableMap(names);
    }
    static String correction(String word,boolean contractions,boolean names){
        String lower=word.toLowerCase(Locale.US).replace('\u2019','\'');
        String replacement=contractions?CONTRACTIONS.get(lower):null;
        if(replacement==null&&contractions&&(lower.equals("i")||lower.startsWith("i'")))replacement="I"+lower.substring(1);
        if(replacement==null&&names)replacement=NAMES.get(lower);
        return replacement;
    }
    static String display(String word){return NAMES.getOrDefault(word.toLowerCase(Locale.US),word);}
    private EnglishDefaults(){}
}
