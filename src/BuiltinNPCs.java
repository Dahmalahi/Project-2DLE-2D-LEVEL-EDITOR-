/**
 * BuiltinNPCs.java  v1.0
 *
 * Library of built-in ready-to-use NPC and Boss characters.
 * Each entry includes:
 *  - Name, type, colour, default dialogue
 *  - Built-in script source (compatible with ScriptLang)
 *  - Battle stats for enemy NPCs / bosses
 *
 * Usage in EditorCanvas:
 *   Press 5 in NPC mode → pick from BuiltinNPCs list
 *   The NPC is placed with its default script pre-attached.
 */
public class BuiltinNPCs {

    // =========================================================
    //  NPC PRESET IDs
    // =========================================================
    public static final int NPC_VILLAGER      = 0;
    public static final int NPC_GUARD         = 1;
    public static final int NPC_MERCHANT      = 2;
    public static final int NPC_INNKEEPER     = 3;
    public static final int NPC_ELDER         = 4;
    public static final int NPC_BLACKSMITH    = 5;
    public static final int NPC_HEALER        = 6;
    public static final int NPC_QUEST_GIVER   = 7;
    public static final int NPC_KNIGHT        = 8;
    public static final int NPC_WIZARD        = 9;

    // Boss IDs
    public static final int BOSS_SLIME_KING   = 10;
    public static final int BOSS_ORC_WARLORD  = 11;
    public static final int BOSS_LICH         = 12;
    public static final int BOSS_DRAGON_LORD  = 13;
    public static final int BOSS_DARK_KNIGHT  = 14;

    public static final int BUILTIN_COUNT     = 15;

    // =========================================================
    //  NAMES
    // =========================================================
    public static final String[] NAMES = {
        "Villager", "Guard", "Merchant", "Innkeeper", "Elder",
        "Blacksmith", "Healer", "Quest Giver", "Knight", "Wizard",
        // Bosses
        "Slime King", "Orc Warlord", "Lich", "Dragon Lord", "Dark Knight"
    };

    // =========================================================
    //  COLOURS (ARGB)
    // =========================================================
    public static final int[] COLORS = {
        0xFF88AAFF,  // Villager  — light blue
        0xFFBBBBBB,  // Guard     — silver
        0xFFFFCC44,  // Merchant  — gold
        0xFFFFAACC,  // Innkeeper — pink
        0xFFCCBB88,  // Elder     — tan
        0xFFFF8833,  // Blacksmith— orange
        0xFF88FF88,  // Healer    — green
        0xFFFFFF44,  // Quest Giver—yellow
        0xFF4488FF,  // Knight    — blue
        0xFF8844FF,  // Wizard    — purple
        // Bosses (vivid)
        0xFF00CC44,  // Slime King
        0xFF994400,  // Orc Warlord
        0xFF8800CC,  // Lich
        0xFFFF4400,  // Dragon Lord
        0xFF222244,  // Dark Knight
    };

    // =========================================================
    //  NPC TYPES (maps to NPCManager.NPC_* constants)
    // =========================================================
    public static final int[] NPC_TYPES = {
        NPCManager.NPC_FRIENDLY,   // Villager
        NPCManager.NPC_FRIENDLY,   // Guard
        NPCManager.NPC_SHOPKEEP,   // Merchant
        NPCManager.NPC_FRIENDLY,   // Innkeeper
        NPCManager.NPC_QUEST,      // Elder
        NPCManager.NPC_SHOPKEEP,   // Blacksmith
        NPCManager.NPC_FRIENDLY,   // Healer
        NPCManager.NPC_QUEST,      // Quest Giver
        NPCManager.NPC_FRIENDLY,   // Knight
        NPCManager.NPC_QUEST,      // Wizard
        NPCManager.NPC_BOSS,       // Slime King
        NPCManager.NPC_BOSS,       // Orc Warlord
        NPCManager.NPC_BOSS,       // Lich
        NPCManager.NPC_BOSS,       // Dragon Lord
        NPCManager.NPC_BOSS,       // Dark Knight
    };

    // =========================================================
    //  ENEMY TYPE (for bosses — matches EnemyData.ENEMY_* IDs)
    //  -1 means not a combat NPC
    // =========================================================
    public static final int[] ENEMY_IDS = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        0,   // Slime King  → EnemyData.ENEMY_SLIME (scaled)
        4,   // Orc Warlord → EnemyData.ENEMY_ORC
        7,   // Lich        → EnemyData.ENEMY_GHOST (stands in)
        9,   // Dragon Lord → EnemyData.ENEMY_DRAGON
        3,   // Dark Knight → EnemyData.ENEMY_SKELETON
    };

    // Level modifier for boss battles (boosts enemy stats)
    public static final int[] BOSS_LEVEL_MOD = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        3,   // Slime King
        5,   // Orc Warlord
        8,   // Lich
        15,  // Dragon Lord
        10,  // Dark Knight
    };

    // =========================================================
    //  BUILT-IN SCRIPT SOURCE
    //  Each entry is a complete .2dls script string.
    // =========================================================
    public static String getDefaultScript(int presetId) {
        switch (presetId) {

            // ── Villager ──────────────────────────────────────
            case NPC_VILLAGER:
                return
                    "// Villager greeting\n"
                  + "if switch " + (presetId + 100) + " on goto met\n"
                  + "text \"Hello there, traveller!\"\n"
                  + "text \"Welcome to our village.\"\n"
                  + "set switch " + (presetId + 100) + " on\n"
                  + "give exp 5\n"
                  + "goto done\n"
                  + "label met\n"
                  + "text \"Good day to you!\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Guard ─────────────────────────────────────────
            case NPC_GUARD:
                return
                    "// Guard blocking passage\n"
                  + "if switch " + (presetId + 100) + " on goto allowed\n"
                  + "text \"Halt! You may not pass!\"\n"
                  + "choice 2 \"I have business here.\" \"I will leave.\"\n"
                  + "if var 0 == 1 goto leave\n"
                  + "text \"State your name and purpose.\"\n"
                  + "set switch " + (presetId + 100) + " on\n"
                  + "text \"Very well. You may proceed.\"\n"
                  + "goto done\n"
                  + "label allowed\n"
                  + "text \"Move along, citizen.\"\n"
                  + "goto done\n"
                  + "label leave\n"
                  + "text \"As you wish.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Merchant ──────────────────────────────────────
            case NPC_MERCHANT:
                return
                    "// Merchant shop\n"
                  + "text \"Welcome to my shop!\"\n"
                  + "choice 2 \"Buy\" \"Leave\"\n"
                  + "if var 0 == 1 goto leave\n"
                  + "shop 0\n"
                  + "text \"Thank you for your business!\"\n"
                  + "label leave\n"
                  + "text \"Come back any time!\"\n"
                  + "end\n";

            // ── Innkeeper ─────────────────────────────────────
            case NPC_INNKEEPER:
                return
                    "// Innkeeper — rest for gold\n"
                  + "text \"Welcome, weary traveller!\"\n"
                  + "text \"A night's rest costs 10 gold.\"\n"
                  + "choice 2 \"Rest (10g)\" \"No thanks\"\n"
                  + "if var 0 == 1 goto leave\n"
                  + "if gold >= 10 goto can_afford\n"
                  + "text \"You don't have enough gold!\"\n"
                  + "goto leave\n"
                  + "label can_afford\n"
                  + "take gold 10\n"
                  + "inn\n"
                  + "heal\n"
                  + "text \"Good morning! You look refreshed.\"\n"
                  + "goto done\n"
                  + "label leave\n"
                  + "text \"Safe travels!\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Elder ─────────────────────────────────────────
            case NPC_ELDER:
                return
                    "// Village Elder — lore + quest hint\n"
                  + "text \"Ah, young adventurer...\"\n"
                  + "text \"Dark times have fallen upon us.\"\n"
                  + "text \"The dungeon to the north grows\"\n"
                  + "text \"ever more dangerous.\"\n"
                  + "if switch " + (presetId + 100) + " on goto already_warned\n"
                  + "set switch " + (presetId + 100) + " on\n"
                  + "text \"Take this for your journey.\"\n"
                  + "give item 0 2\n"
                  + "give exp 20\n"
                  + "goto done\n"
                  + "label already_warned\n"
                  + "text \"Be careful out there.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Blacksmith ────────────────────────────────────
            case NPC_BLACKSMITH:
                return
                    "// Blacksmith — weapon shop + craft\n"
                  + "text \"What can I forge for you?\"\n"
                  + "choice 3 \"Buy weapons\" \"Craft\" \"Leave\"\n"
                  + "if var 0 == 0 goto buy\n"
                  + "if var 0 == 1 goto craft\n"
                  + "text \"Come back when you need steel!\"\n"
                  + "end\n"
                  + "label buy\n"
                  + "shop 1\n"
                  + "end\n"
                  + "label craft\n"
                  + "craft\n"
                  + "end\n";

            // ── Healer ────────────────────────────────────────
            case NPC_HEALER:
                return
                    "// Healer — free healing once, then costs\n"
                  + "if switch " + (presetId + 100) + " on goto costs_gold\n"
                  + "text \"You look wounded! Let me help.\"\n"
                  + "heal\n"
                  + "sound 5\n"
                  + "text \"There. All better!\"\n"
                  + "set switch " + (presetId + 100) + " on\n"
                  + "goto done\n"
                  + "label costs_gold\n"
                  + "text \"Healing costs 20 gold.\"\n"
                  + "choice 2 \"Heal (20g)\" \"No\"\n"
                  + "if var 0 == 1 goto no\n"
                  + "if gold >= 20 goto afford\n"
                  + "text \"Not enough gold!\"\n"
                  + "goto done\n"
                  + "label afford\n"
                  + "take gold 20\n"
                  + "heal\n"
                  + "sound 5\n"
                  + "text \"Healing complete.\"\n"
                  + "goto done\n"
                  + "label no\n"
                  + "text \"Be careful out there.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Quest Giver ───────────────────────────────────
            case NPC_QUEST_GIVER:
                // Build quest giver script directly — no String.replace (not in J2ME 1.3)
                return
                    "// Quest giver\n"
                  + "if switch " + (presetId + 100) + " on goto check_done\n"
                  + "text \"I need your help!\"\n"
                  + "choice 2 \"I'll help!\" \"Not now.\"\n"
                  + "if var 0 == 1 goto declined\n"
                  + "startQuest 0\n"
                  + "set switch " + (presetId + 100) + " on\n"
                  + "text \"The quest begins!\"\n"
                  + "goto done\n"
                  + "label declined\n"
                  + "text \"Come back when ready.\"\n"
                  + "goto done\n"
                  + "label check_done\n"
                  + "checkQuest 0 quest_complete\n"
                  + "text \"Still working on it?\"\n"
                  + "goto done\n"
                  + "label quest_complete\n"
                  + "text \"You did it! Here's your reward.\"\n"
                  + "give item 0 3\n"
                  + "give exp 80\n"
                  + "give gold 50\n"
                  + "label done\n"
                  + "end\n";

            // ── Knight ────────────────────────────────────────
            case NPC_KNIGHT:
                return
                    "// Knight — training duel\n"
                  + "if switch " + (presetId + 100) + " on goto defeated\n"
                  + "text \"I am Sir Roland, Knight of the Realm!\"\n"
                  + "text \"Prove your worth in battle!\"\n"
                  + "choice 2 \"Fight\" \"Retreat\"\n"
                  + "if var 0 == 1 goto leave\n"
                  + "battle 1 3\n"
                  + "set switch " + (presetId + 100) + " on\n"
                  + "text \"Well fought! You have my respect.\"\n"
                  + "give exp 80\n"
                  + "give item 10 1\n"
                  + "goto done\n"
                  + "label defeated\n"
                  + "text \"You've grown stronger, warrior.\"\n"
                  + "goto done\n"
                  + "label leave\n"
                  + "text \"Courage will come in time.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Wizard ────────────────────────────────────────
            case NPC_WIZARD:
                return
                    "// Wizard — teaches skills\n"
                  + "text \"I sense magical potential in you.\"\n"
                  + "choice 3 \"Learn Fire\" \"Learn Ice\" \"Leave\"\n"
                  + "if var 0 == 0 goto fire\n"
                  + "if var 0 == 1 goto ice\n"
                  + "text \"Return when you seek knowledge.\"\n"
                  + "end\n"
                  + "label fire\n"
                  + "if gold >= 50 goto buy_fire\n"
                  + "text \"Fire magic costs 50 gold.\"\n"
                  + "end\n"
                  + "label buy_fire\n"
                  + "take gold 50\n"
                  + "learnSkill 4\n"
                  + "text \"The power of Fire is yours!\"\n"
                  + "end\n"
                  + "label ice\n"
                  + "if gold >= 50 goto buy_ice\n"
                  + "text \"Ice magic costs 50 gold.\"\n"
                  + "end\n"
                  + "label buy_ice\n"
                  + "take gold 50\n"
                  + "learnSkill 5\n"
                  + "text \"The power of Ice is yours!\"\n"
                  + "end\n";

            // ── Slime King (Boss) ──────────────────────────────
            case BOSS_SLIME_KING:
                return ScriptLang.templateBossBattle(0, presetId + 200);

            // ── Orc Warlord (Boss) ────────────────────────────
            case BOSS_ORC_WARLORD:
                return
                    "// Orc Warlord Boss\n"
                  + "if switch " + (presetId + 200) + " on goto dead\n"
                  + "text \"GRAAARGH! You dare enter my hall?!\"\n"
                  + "shake 4 20\n"
                  + "music 4\n"
                  + "battle 1 4\n"
                  + "set switch " + (presetId + 200) + " on\n"
                  + "text \"...Impossible. A human beat me!\"\n"
                  + "give exp 200\n"
                  + "give gold 100\n"
                  + "give item 11 1\n"
                  + "goto done\n"
                  + "label dead\n"
                  + "text \"You... you've grown powerful.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Lich (Boss) ────────────────────────────────────
            case BOSS_LICH:
                return
                    "// Lich Boss — undead magic user\n"
                  + "if switch " + (presetId + 200) + " on goto dead\n"
                  + "text \"Foolish mortal. You cannot kill\"\n"
                  + "text \"what is already dead!\"\n"
                  + "weather 3 60\n"
                  + "fadeout 3\n"
                  + "music 4\n"
                  + "fadein 3\n"
                  + "battle 1 7\n"
                  + "set switch " + (presetId + 200) + " on\n"
                  + "weather 0 0\n"
                  + "text \"My curse... is broken...\"\n"
                  + "give exp 350\n"
                  + "give gold 150\n"
                  + "give item 12 1\n"
                  + "goto done\n"
                  + "label dead\n"
                  + "text \"The Lich's power is sealed.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Dragon Lord (Boss) ────────────────────────────
            case BOSS_DRAGON_LORD:
                return
                    "// Dragon Lord — final boss\n"
                  + "if switch " + (presetId + 200) + " on goto dead\n"
                  + "text \"So the hero arrives at last.\"\n"
                  + "text \"The world shall burn!\"\n"
                  + "shake 5 40\n"
                  + "weather 4 100\n"
                  + "fadeout 2\n"
                  + "music 4\n"
                  + "fadein 2\n"
                  + "battle 1 9\n"
                  + "set switch " + (presetId + 200) + " on\n"
                  + "set switch " + GameConfig.SW_FINAL_BOSS_DONE + " on\n"
                  + "weather 0 0\n"
                  + "music 5\n"
                  + "text \"Impossible... the dragon falls!\"\n"
                  + "give exp 500\n"
                  + "give gold 500\n"
                  + "goto done\n"
                  + "label dead\n"
                  + "text \"The Dragon Lord is vanquished.\"\n"
                  + "label done\n"
                  + "end\n";

            // ── Dark Knight (Boss) ────────────────────────────
            case BOSS_DARK_KNIGHT:
                return
                    "// Dark Knight — story boss\n"
                  + "if switch " + (presetId + 200) + " on goto dead\n"
                  + "text \"We meet at last, hero.\"\n"
                  + "text \"I have been waiting for you.\"\n"
                  + "choice 2 \"Fight!\" \"Why?\"\n"
                  + "if var 0 == 1 goto talk\n"
                  + "goto battle\n"
                  + "label talk\n"
                  + "text \"I serve darkness by choice.\"\n"
                  + "text \"You cannot save me.\"\n"
                  + "label battle\n"
                  + "fadeout 3\n"
                  + "music 4\n"
                  + "fadein 3\n"
                  + "battle 1 3\n"
                  + "set switch " + (presetId + 200) + " on\n"
                  + "text \"Finish it... set me free.\"\n"
                  + "give exp 300\n"
                  + "give gold 200\n"
                  + "give item 21 1\n"
                  + "goto done\n"
                  + "label dead\n"
                  + "text \"The Dark Knight stands silent.\"\n"
                  + "label done\n"
                  + "end\n";

            default:
                return "// " + getName(presetId) + "\ntext \"Hello!\"\nend\n";
        }
    }

    // =========================================================
    //  ACCESSORS
    // =========================================================
    public static String getName(int id) {
        if (id < 0 || id >= NAMES.length) return "Unknown";
        return NAMES[id];
    }

    public static int getColor(int id) {
        if (id < 0 || id >= COLORS.length) return 0xFF888888;
        return COLORS[id];
    }

    public static int getNPCType(int id) {
        if (id < 0 || id >= NPC_TYPES.length) return NPCManager.NPC_FRIENDLY;
        return NPC_TYPES[id];
    }

    public static boolean isBoss(int id) {
        return id >= BOSS_SLIME_KING;
    }

    public static int getEnemyId(int id) {
        if (id < 0 || id >= ENEMY_IDS.length) return -1;
        return ENEMY_IDS[id];
    }

    public static int getBossLevelMod(int id) {
        if (id < 0 || id >= BOSS_LEVEL_MOD.length) return 0;
        return BOSS_LEVEL_MOD[id];
    }
}