package com.brouken.player.together;

import java.security.SecureRandom;

/**
 * Friendly names for a device in a room — "Clever Mango" rather than "CPH2447".
 *
 * <p>The idea, the adjective-then-greengrocer shape and the original word lists come from
 * <a href="https://github.com/localsend/localsend">LocalSend</a> (Apache License 2.0), which solves
 * the same problem: something a stranger can read off a screen and repeat out loud, that says
 * nothing about the hardware or its owner. Their 38 adjectives and 26 fruits are all still here; the
 * lists are extended from there, because 988 names collide more often than a room of strangers can
 * forgive. 222 x 137 is 30414, which puts two people in a room of five drawing the same name at
 * around one chance in three thousand.
 *
 * <p>Kept to a few hundred words a side rather than a dictionary: every one of them has to be
 * readable at a glance and repeatable over the phone, which is the whole point of a name like this.
 *
 * <p>Not localised, exactly as LocalSend is not: its own Russian and Ukrainian locale files carry
 * only a note that the words may differ per locale and fall back to English, so a name generated
 * here reads the same as one generated there.
 */
public final class AliasGenerator {

    private static final String[] ADJECTIVES = {
            "Able", "Active", "Adorable", "Agile", "Alert", "Amber", "Amused", "Ancient", "Artful",
            "Autumn", "Balanced", "Beautiful", "Big", "Bold", "Brave", "Breezy", "Bright", "Brisk",
            "Calm", "Candid", "Careful", "Cheerful", "Chosen", "Civil", "Classic", "Clean",
            "Clear", "Clever", "Cool", "Cosmic", "Crafty", "Crimson", "Crisp", "Cunning",
            "Curious", "Cute", "Dapper", "Daring", "Dazzling", "Deep", "Deft", "Determined",
            "Devoted", "Diligent", "Distant", "Divine", "Eager", "Early", "Earnest", "Easy",
            "Efficient", "Elder", "Electric", "Elegant", "Endless", "Energetic", "Epic", "Equal",
            "Eternal", "Exact", "Fabled", "Faithful", "Famous", "Fantastic", "Fast", "Fearless",
            "Feisty", "Fertile", "Festive", "Fiery", "Fine", "Firm", "Fond", "Frank", "Free",
            "Fresh", "Friendly", "Frosty", "Gallant", "Gentle", "Genuine", "Gifted", "Glad",
            "Gleaming", "Glossy", "Golden", "Good", "Gorgeous", "Graceful", "Grand", "Grateful",
            "Great", "Handsome", "Happy", "Hardy", "Hearty", "Helpful", "Heroic", "Hidden",
            "Honest", "Hopeful", "Hot", "Humble", "Ideal", "Immense", "Inner", "Iron", "Jolly",
            "Joyful", "Keen", "Kind", "Lively", "Lovely", "Loyal", "Lucid", "Lucky", "Lunar",
            "Magic", "Major", "Merry", "Mighty", "Mild", "Modern", "Modest", "Mystic", "Neat",
            "Nice", "Nimble", "Noble", "Northern", "Novel", "Open", "Orbital", "Original",
            "Patient", "Peaceful", "Perfect", "Playful", "Pleasant", "Polished", "Polite",
            "Powerful", "Precise", "Pretty", "Prime", "Proud", "Prudent", "Quick", "Quiet",
            "Radiant", "Rapid", "Ready", "Refined", "Regal", "Reliable", "Rich", "Robust", "Royal",
            "Ruby", "Rustic", "Sage", "Scarlet", "Secret", "Serene", "Sharp", "Shining", "Silent",
            "Silver", "Sincere", "Skilled", "Sleek", "Smart", "Smooth", "Snowy", "Solar", "Solid",
            "Southern", "Special", "Spirited", "Splendid", "Spry", "Stately", "Steady", "Sterling",
            "Stormy", "Strategic", "Strong", "Sturdy", "Subtle", "Sunny", "Superb", "Supreme",
            "Swift", "Tactful", "Tender", "Thankful", "Thoughtful", "Tidy", "Timely", "Tranquil",
            "True", "Trusty", "Unique", "Upbeat", "Valiant", "Velvet", "Vibrant", "Vigilant",
            "Violet", "Vivid", "Warm", "Watchful", "Welcome", "Whole", "Wild", "Willing", "Wise",
            "Witty", "Wondrous", "Worthy", "Zealous", "Zesty",
    };

    /** Fruit in the loose sense LocalSend uses, which stretches to the whole greengrocer. */
    private static final String[] FRUITS = {
            "Almond", "Apple", "Apricot", "Artichoke", "Arugula", "Asparagus", "Aubergine",
            "Avocado", "Banana", "Barley", "Basil", "Beetroot", "Blackberry", "Blackcurrant",
            "Blueberry", "Broccoli", "Cabbage", "Cantaloupe", "Caper", "Cardamom", "Carrot",
            "Cashew", "Cauliflower", "Celery", "Chard", "Cherry", "Chestnut", "Chickpea",
            "Chicory", "Chili", "Chive", "Cilantro", "Clementine", "Cloudberry", "Clove",
            "Coconut", "Cranberry", "Cucumber", "Currant", "Damson", "Date", "Dill", "Durian",
            "Eggplant", "Elderberry", "Endive", "Fennel", "Fig", "Garlic", "Ginger", "Gooseberry",
            "Grape", "Grapefruit", "Guava", "Hazelnut", "Honeydew", "Horseradish", "Jackfruit",
            "Jicama", "Kale", "Kiwi", "Kohlrabi", "Kumquat", "Leek", "Lemon", "Lentil", "Lettuce",
            "Lime", "Longan", "Loquat", "Lychee", "Mandarin", "Mango", "Marrow", "Melon", "Millet",
            "Mint", "Mulberry", "Mushroom", "Nectarine", "Nutmeg", "Oat", "Okra", "Olive", "Onion",
            "Orange", "Oregano", "Papaya", "Paprika", "Parsley", "Parsnip", "Passionfruit", "Pea",
            "Peach", "Peanut", "Pear", "Pecan", "Pepper", "Persimmon", "Pineapple", "Pistachio",
            "Plantain", "Plum", "Pomegranate", "Pomelo", "Poppy", "Potato", "Pumpkin", "Quince",
            "Radish", "Raisin", "Raspberry", "Rhubarb", "Rosemary", "Rutabaga", "Saffron",
            "Sesame", "Shallot", "Sorrel", "Soybean", "Spinach", "Sprout", "Squash", "Strawberry",
            "Tamarind", "Tangerine", "Tarragon", "Thyme", "Tomato", "Turnip", "Vanilla", "Walnut",
            "Wasabi", "Watercress", "Watermelon", "Yam", "Zucchini",
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private AliasGenerator() {
    }

    public static String random() {
        return ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)]
                + " " + FRUITS[RANDOM.nextInt(FRUITS.length)];
    }
}
