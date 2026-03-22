/**
 * GameConfig.java — Central configuration hub for the entire RPG engine.
 *
 * ENHANCEMENTS v2.0:
 *  - Expanded map/party/inventory/skill limits
 *  - Crafting, fishing, farming, mounting sub-system constants
 *  - Elemental affinity matrix (9 elements)
 *  - Reputation/faction system constants
 *  - Day/Night cycle timing
 *  - Difficulty presets (Easy / Normal / Hard / Nightmare)
 *  - Combo & chain-attack parameters
 *  - Stealth system constants
 *  - Expanded global variable/switch blocks
 *  - UI timing & animation constants
 *  - New Game+ and permadeath support
 */
public class GameConfig {

    // =========================================================
    //  MAP LIMITS
    // =========================================================
    public static final int MAX_MAP_WIDTH      = 128;
    public static final int MAX_MAP_HEIGHT     = 128;
    public static final int MAX_MAPS           = 50;
    public static final int MAX_LAYERS         = 5;   // BG, Wall, Obj, Event, Shadow
    public static final int LAYER_BG           = 0;
    public static final int LAYER_WALL         = 1;
    public static final int LAYER_OBJ          = 2;
    public static final int LAYER_EVENT        = 3;
    public static final int LAYER_SHADOW       = 4;

    // =========================================================
    //  PLAYER / PARTY LIMITS
    // =========================================================
    public static final int MAX_LEVEL          = 99;
    public static final int MAX_HP             = 9999;
    public static final int MAX_MP             = 999;
    public static final int MAX_TP             = 100;   // Tactical/Limit Points
    public static final int MAX_STAT           = 999;
    public static final int MAX_INVENTORY      = 200;
    public static final int MAX_EQUIPMENT      = 8;    // weapon,armor,helm,access,shield,ring,ring,relic
    public static final int MAX_SKILLS         = 40;
    public static final int MAX_PASSIVE_SKILLS  = 10;
    public static final int MAX_PARTY          = 4;

    // =========================================================
    //  BATTLE SETTINGS
    // =========================================================
    public static final int MAX_ENEMIES           = 8;
    public static final int ATB_MAX               = 100;
    public static final int CRIT_MULTIPLIER       = 2;
    public static final int GUARD_REDUCTION       = 50;
    public static final int BACK_ATTACK_BONUS     = 150;  // % damage multiplier
    public static final int PINCER_ATTACK_BONUS   = 125;
    public static final int COMBO_WINDOW_FRAMES   = 45;
    public static final int MAX_COMBO_HITS        = 12;
    public static final int MAX_COMBO             = 12;    // alias used by PlayerData
    public static final int COMBO_BONUS_PER       = 5;    // % damage bonus per combo hit
    public static final int CHAIN_WINDOW_MS       = 1500; // ms window to continue a combo chain
    public static final int CHAIN_EXP_BONUS       = 20;   // % extra EXP per combo hit at battle end
    public static final int LIMIT_BREAK_THRESHOLD = 25;   // % HP to start charging limit gauge
    public static final int LIMIT_GAUGE_MAX       = 100;  // limit gauge fills to this value
    public static final int SCREEN_FLASH_DUR      = 8;    // frames for battle screen flash effect
    public static final int STEAL_BASE_CHANCE     = 30;
    public static final int ESCAPE_BASE_CHANCE    = 60;
    public static final int COUNTER_CHANCE        = 15;
    public static final int MAX_BOSS_PHASES       = 4;
    public static final int MAX_SUMMONS           = 3;
    public static final int MAX_HOTBAR            = 8;    // quick-access skill slots
    public static final int MAX_SP                = 200;  // Stamina Points max
    public static final int DASH_SPEED            = 8;    // dash duration in frames
    public static final int STEPS_PER_PERIOD      = 3600; // walk steps before day advances one period

    // =========================================================
    //  ELEMENTAL AFFINITY MATRIX
    //  [attackElem][defElem] * 10  → divide by 10 for multiplier
    //  Elements: 0=NONE 1=FIRE 2=ICE 3=ELEC 4=DARK 5=HOLY 6=WIND 7=EARTH 8=WATER
    // =========================================================
    public static final int[][] ELEM_MULTIPLIER = {
        //  NON  FIR  ICE  ELC  DRK  HLY  WND  ETH  WTR
        {   10,  10,  10,  10,  10,  10,  10,  10,  10 }, // NONE
        {   10,   5,  20,  10,  10,  10,  10,   5,   5 }, // FIRE
        {   10,  20,   5,  10,  10,  10,  10,  10,  15 }, // ICE
        {   10,  10,  10,   5,  10,  10,  20,  10,  15 }, // ELEC
        {   10,  10,  10,  10,   5,  20,  10,  10,  10 }, // DARK
        {   10,  10,  10,  10,  20,   5,  10,  10,  10 }, // HOLY
        {   10,  10,  10,  10,  10,  10,   5,  15,  10 }, // WIND
        {   10,  10,  10,  10,  10,  10,   5,  10,  20 }, // EARTH
        {   10,  15,  10,   5,  10,  10,  10,  20,  10 }, // WATER
    };

    // =========================================================
    //  STEALTH SYSTEM
    // =========================================================
    public static final int STEALTH_MAX          = 100;
    public static final int STEALTH_DETECT_BASE  = 30;
    public static final int STEALTH_MOVE_PENALTY = 5;
    public static final int STEALTH_CROUCH_BONUS = 40;
    public static final int STEALTH_KILL_THRESHOLD = 80;  // instakill if stealth > this

    // =========================================================
    //  REPUTATION / FACTION SYSTEM
    // =========================================================
    public static final int MAX_FACTIONS         = 10;
    public static final int REP_HATED            = -100;
    public static final int REP_HOSTILE          = -60;
    public static final int REP_UNFRIENDLY       = -20;
    public static final int REP_NEUTRAL          = 0;
    public static final int REP_FRIENDLY         = 30;
    public static final int REP_HONORED          = 60;
    public static final int REP_EXALTED          = 100;
    public static final int FACTION_VILLAGERS    = 0;
    public static final int FACTION_GUARDS       = 1;
    public static final int FACTION_MERCHANTS    = 2;
    public static final int FACTION_THIEVES      = 3;
    public static final int FACTION_MAGES        = 4;
    public static final int FACTION_KNIGHTS      = 5;
    public static final int FACTION_BANDITS      = 6;
    public static final int FACTION_MONSTERS     = 7;
    public static final int FACTION_UNDEAD       = 8;
    public static final int FACTION_ELEMENTALS   = 9;

    // =========================================================
    //  DAY / NIGHT CYCLE
    // =========================================================
    public static final int DAY_TICKS_PER_HOUR   = 60;
    public static final int HOUR_DAWN            = 5;
    public static final int HOUR_MORNING         = 7;
    public static final int HOUR_NOON            = 12;
    public static final int HOUR_DUSK            = 18;
    public static final int HOUR_EVENING         = 20;
    public static final int HOUR_NIGHT           = 22;
    // Overlay darkness alpha per hour (0=day, 200=very dark)
    public static final int[] HOUR_DARKNESS = {
        160, 180, 200, 180, 120,
         60,  20,   0,   0,   0,
          0,   0,   0,   0,   0,
          0,  10,  30,  60, 100,
        120, 140, 150, 160
    };

    // =========================================================
    //  CRAFTING SYSTEM
    // =========================================================
    public static final int MAX_RECIPES          = 80;
    public static final int MAX_RECIPE_INGREDIENTS = 4;
    public static final int MAX_RECIPE_INGR      = 4;    // alias used by CraftingSystem
    public static final int CRAFT_SUCCESS_BASE   = 70;
    public static final int CRAFT_FAIL_RATE      = 30;   // base fail %, reduced by craft skill
    public static final int CRAFT_QUALITY_TIERS  = 3;
    public static final int STATION_ANVIL        = 0;
    public static final int STATION_ALCHEMY      = 1;
    public static final int STATION_COOKING      = 2;
    public static final int STATION_ENCHANT      = 3;

    // =========================================================
    //  FISHING SYSTEM
    // =========================================================
    public static final int MAX_FISH_TYPES       = 20;
    public static final int FISH_NIBBLE_MIN      = 60;
    public static final int FISH_NIBBLE_MAX      = 180;
    public static final int FISH_REEL_WINDOW     = 30;
    public static final int FISH_ESCAPE_CHANCE   = 25;

    // =========================================================
    //  FARMING SYSTEM
    // =========================================================
    public static final int MAX_FARM_PLOTS       = 16;
    public static final int MAX_CROP_TYPES       = 12;
    public static final int CROP_GROWTH_STAGES   = 4;
    public static final int CROP_WATER_DAYS      = 2;

    // =========================================================
    //  MOUNT SYSTEM
    // =========================================================
    public static final int MAX_MOUNTS           = 6;
    public static final int MOUNT_WALK_SPEED     = 60;
    public static final int MOUNT_SPRINT_SPEED   = 35;
    public static final int MOUNT_BOND_MAX       = 100;

    // =========================================================
    //  EVENT SYSTEM
    // =========================================================
    public static final int MAX_SWITCHES         = 300;
    public static final int MAX_VARIABLES        = 200;
    public static final int MAX_EVENTS           = 100;
    public static final int MAX_EVENT_CMDS       = 200;
    public static final int MAX_EVENT_PAGES      = 5;
    public static final int MAX_COMMON_EVENTS    = 50;

    // =========================================================
    //  QUEST SYSTEM
    // =========================================================
    public static final int MAX_QUESTS           = 80;
    public static final int MAX_QUEST_STEPS      = 20;
    public static final int MAX_QUEST_OBJECTIVES = 5;
    public static final int MAX_QUEST_REWARDS    = 4;

    // =========================================================
    //  NPC / ENEMY
    // =========================================================
    public static final int MAX_NPCS             = 128;
    public static final int MAX_ENEMY_TYPES      = 80;

    // =========================================================
    //  TIMING (ms)
    // =========================================================
    public static final int TEXT_SPEED_FAST      = 20;
    public static final int TEXT_SPEED_MED       = 50;
    public static final int TEXT_SPEED_SLOW      = 100;
    public static final int TEXT_SPEED_INSTANT   = 0;
    public static final int WALK_SPEED           = 100;
    public static final int RUN_SPEED            = 50;
    public static final int SWIM_SPEED           = 150;
    public static final int SLIDE_SPEED          = 40;
    public static final int MOUNT_SPEED          = 60;
    public static final int WALK_ANIM_SPEED      = 120;
    public static final int DAMAGE_FLASH_DURATION= 300;
    public static final int LEVELUP_ANIM_DURATION= 2000;
    public static final int TRANSITION_SPEED     = 300;

    // =========================================================
    //  ENCOUNTER SETTINGS
    // =========================================================
    public static final int ENCOUNTER_MIN_STEPS  = 8;
    public static final int ENCOUNTER_MAX_STEPS  = 30;
    public static final int ENCOUNTER_RATE_FLOOR = 5;
    public static final int ENCOUNTER_SPRINT_DIV = 2;

    // =========================================================
    //  DIFFICULTY PRESETS
    // =========================================================
    public static final int DIFF_EASY            = 0;
    public static final int DIFF_NORMAL          = 1;
    public static final int DIFF_HARD            = 2;
    public static final int DIFF_NIGHTMARE       = 3;
    public static final int[] DIFF_ENEMY_HP_MUL  = {  7, 10, 14, 20 };
    public static final int[] DIFF_ENEMY_ATK_MUL = {  7, 10, 13, 18 };
    public static final int[] DIFF_EXP_MUL       = { 12, 10, 10, 10 };
    public static final int[] DIFF_GOLD_MUL      = { 12, 10,  8,  6 };

    // =========================================================
    //  SAVE SYSTEM
    // =========================================================
    public static final int MAX_SAVE_SLOTS       = 9;
    public static final int AUTOSAVE_INTERVAL    = 300;
    public static final int SAVE_VERSION         = 4;

    // =========================================================
    //  UI / HUD
    // =========================================================
    public static final int HUD_MARGIN           = 4;
    public static final int HUD_HP_BAR_W         = 80;
    public static final int HUD_HP_BAR_H         = 8;
    public static final int HUD_TP_BAR_W         = 60;
    public static final int HUD_TP_BAR_H         = 6;
    public static final int MINIMAP_SIZE         = 64;
    public static final int MINIMAP_TILE         = 2;    // px per tile on minimap
    public static final boolean MINIMAP_FOG      = true; // show fog-of-war on minimap
    public static final int COLOR_HP_FULL        = 0xFF22BB44;
    public static final int COLOR_HP_MID         = 0xFFFFAA00;
    public static final int COLOR_HP_LOW         = 0xFFDD2222;
    public static final int COLOR_MP_BAR         = 0xFF3366FF;
    public static final int COLOR_TP_BAR         = 0xFFFFDD00;
    public static final int COLOR_EXP_BAR        = 0xFF9944FF;
    public static final int COLOR_CRIT_TEXT      = 0xFFFF4400;
    public static final int COLOR_MISS_TEXT      = 0xFFAAAAAA;
    public static final int COLOR_HEAL_TEXT      = 0xFF44FF88;

    // =========================================================
    //  SHOP SYSTEM
    // =========================================================
    public static final int MAX_SHOP_ITEMS       = 24;
    public static final int SELL_PRICE_RATIO     = 50;
    public static final int SELL_RATE            = 50;    // alias used by ShopSystem
    public static final int HAGGLE_MAX           = 20;    // max % discount from haggling
    public static final int APPRAISAL_COST_BASE  = 20;
    public static final int NPC_WANDER_RANGE     = 3;     // tiles an NPC can wander from home

    // =========================================================
    //  MINI-GAMES
    // =========================================================
    public static final int SLOT_MIN_BET         = 10;
    public static final int SLOT_MAX_BET         = 500;
    public static final int ARENA_MAX_ROUNDS     = 10;
    public static final int ARENA_BASE_PRIZE     = 100;

    // =========================================================
    //  GLOBAL SWITCHES
    // =========================================================
    public static final int SW_GAME_STARTED      = 0;
    public static final int SW_INTRO_DONE        = 1;
    public static final int SW_HAS_KEY           = 2;
    public static final int SW_BOSS_DEFEATED     = 3;
    public static final int SW_TUTORIAL_DONE     = 4;
    public static final int SW_STEALTHY_MODE     = 5;
    public static final int SW_NIGHT_VISION      = 6;
    public static final int SW_BOAT_UNLOCKED     = 7;
    public static final int SW_AIRSHIP_UNLOCKED  = 8;
    public static final int SW_MOUNT_UNLOCKED    = 9;
    public static final int SW_CRAFTING_UNLOCKED = 10;
    public static final int SW_FISHING_UNLOCKED  = 11;
    public static final int SW_FARMING_UNLOCKED  = 12;
    public static final int SW_FINAL_BOSS_DONE   = 13;
    public static final int SW_TRUE_ENDING       = 14;
    public static final int SW_NEW_GAME_PLUS     = 15;
    public static final int SW_PERMADEATH        = 16;

    // =========================================================
    //  GLOBAL VARIABLES
    // =========================================================
    public static final int VAR_GOLD             = 0;
    public static final int VAR_PLAYTIME         = 1;
    public static final int VAR_KILL_COUNT       = 2;
    public static final int VAR_STEP_COUNT       = 3;
    public static final int VAR_DAY_COUNT        = 4;
    public static final int VAR_HOUR_OF_DAY      = 5;
    public static final int VAR_DIFFICULTY       = 6;
    public static final int VAR_STEALTH          = 7;
    public static final int VAR_COMBO_COUNT      = 8;
    public static final int VAR_TP               = 9;
    public static final int VAR_ARENA_WINS       = 10;
    public static final int VAR_FISH_CAUGHT      = 11;
    public static final int VAR_CROPS_HARVESTED  = 12;
    public static final int VAR_DUNGEON_FLOOR    = 13;
    public static final int VAR_CURRENT_MOUNT    = 14;
    public static final int VAR_WEATHER          = 15;
    public static final int VAR_EXP              = 16;   // used by EventSystem CMD_GIVE_EXP
    public static final int VAR_HP               = 17;   // used by EventSystem CMD_HEAL
    // Variables 20-29: faction reputation (one per faction)
    public static final int VAR_REP_BASE         = 20;

    // =========================================================
    //  HELPER METHODS
    // =========================================================
    public static int scaleEnemyHp(int base, int difficulty) {
        if (difficulty < 0 || difficulty >= DIFF_ENEMY_HP_MUL.length) difficulty = DIFF_NORMAL;
        return base * DIFF_ENEMY_HP_MUL[difficulty] / 10;
    }

    public static int scaleEnemyAtk(int base, int difficulty) {
        if (difficulty < 0 || difficulty >= DIFF_ENEMY_ATK_MUL.length) difficulty = DIFF_NORMAL;
        return base * DIFF_ENEMY_ATK_MUL[difficulty] / 10;
    }

    public static int scaleExpReward(int base, int difficulty) {
        if (difficulty < 0 || difficulty >= DIFF_EXP_MUL.length) difficulty = DIFF_NORMAL;
        return base * DIFF_EXP_MUL[difficulty] / 10;
    }

    public static int getElementMultiplier(int atkElem, int defElem) {
        if (atkElem < 0 || atkElem >= ELEM_MULTIPLIER.length) return 10;
        if (defElem < 0 || defElem >= ELEM_MULTIPLIER[0].length) return 10;
        return ELEM_MULTIPLIER[atkElem][defElem];
    }

    public static int getDarknessAlpha(int hour) {
        if (hour < 0 || hour >= HOUR_DARKNESS.length) return 0;
        return HOUR_DARKNESS[hour];
    }

    public static String getRepLabel(int rep) {
        if (rep <= REP_HATED)      return "Hated";
        if (rep <= REP_HOSTILE)    return "Hostile";
        if (rep <= REP_UNFRIENDLY) return "Unfriendly";
        if (rep <  REP_FRIENDLY)   return "Neutral";
        if (rep <  REP_HONORED)    return "Friendly";
        if (rep <  REP_EXALTED)    return "Honored";
        return "Exalted";
    }

    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static String getDifficultyName(int diff) {
        switch (diff) {
            case DIFF_EASY:      return "Easy";
            case DIFF_NORMAL:    return "Normal";
            case DIFF_HARD:      return "Hard";
            case DIFF_NIGHTMARE: return "Nightmare";
            default:             return "Normal";
        }
    }
}
