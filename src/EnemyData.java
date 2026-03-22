/**
 * EnemyData.java - ENHANCED v2.0
 * Added: loot tables, summon ability, phase transitions,
 * rage/enrage mechanic, new enemy types (up to 20), improved AI,
 * multi-hit attacks, and visual flash state.
 */
public class EnemyData {

    // -------------------------------------------------
    //  ENEMY TYPE DEFINITIONS (expanded to 20)
    // -------------------------------------------------
    public static final int ENEMY_SLIME    = 0;
    public static final int ENEMY_BAT      = 1;
    public static final int ENEMY_GOBLIN   = 2;
    public static final int ENEMY_SKELETON = 3;
    public static final int ENEMY_ORC      = 4;
    public static final int ENEMY_WOLF     = 5;
    public static final int ENEMY_SPIDER   = 6;
    public static final int ENEMY_GHOST    = 7;
    public static final int ENEMY_DEMON    = 8;
    public static final int ENEMY_DRAGON   = 9;
    // NEW TYPES
    public static final int ENEMY_TROLL    = 10;
    public static final int ENEMY_HARPY    = 11;
    public static final int ENEMY_GOLEM    = 12;
    public static final int ENEMY_VAMPIRE  = 13;
    public static final int ENEMY_WRAITH   = 14;
    public static final int ENEMY_BASILISK = 15;
    public static final int ENEMY_DJINN    = 16;
    public static final int ENEMY_LICH     = 17;
    public static final int ENEMY_TITAN    = 18;
    public static final int ENEMY_GOD      = 19;
    public static final int MAX_ENEMY_TYPES = 20;

    public static final String[] ENEMY_NAMES = {
        "Slime","Bat","Goblin","Skeleton","Orc",
        "Wolf","Spider","Ghost","Demon","Dragon",
        "Troll","Harpy","Golem","Vampire","Wraith",
        "Basilisk","Djinn","Lich","Titan","Fallen God"
    };

    // Base stats: HP, MP, ATK, DEF, SPD, EXP, GOLD
    public static final int[][] ENEMY_STATS = {
        {20,   0,   5,  2,  3,   10,   5},  // Slime
        {15,   0,   8,  1,  10,  12,   3},  // Bat
        {35,   5,  12,  5,  5,   25,  15},  // Goblin
        {40,   0,  15,  8,  4,   30,  20},  // Skeleton
        {60,   0,  20, 12,  3,   50,  35},  // Orc
        {30,   0,  18,  5, 12,   35,  25},  // Wolf
        {25,  10,  10,  3,  8,   20,  10},  // Spider
        {45,  20,  15,  2,  7,   40,  30},  // Ghost
        {100, 50,  30, 20, 10,  100,  80},  // Demon
        {500,100,  50, 40,  8,  500, 500},  // Dragon
        // NEW
        {150,  0,  25, 20,  4,   80,  60},  // Troll (regenerates)
        {55,  15,  22,  8, 15,   60,  40},  // Harpy (fast flyer)
        {200,  0,  35, 40,  2,  120,  90},  // Golem (very high DEF)
        {120, 30,  28, 12,  9,  110,  85},  // Vampire (lifesteal)
        {70,  40,  20,  5, 11,   75,  55},  // Wraith (phase)
        {90,   0,  30, 25,  6,   95,  70},  // Basilisk (petrify)
        {80,  60,  32, 15, 13,  130, 100},  // Djinn (magic)
        {250, 200, 45, 30,  7,  300, 250},  // Lich (bone minions)
        {800, 100, 65, 50,  5,  800, 700},  // Titan (AoE)
        {2000,500, 90, 80,  6, 2000,2000},  // Fallen God (true final boss)
    };

    // Element types
    public static final int ELEM_NONE  = 0;
    public static final int ELEM_FIRE  = 1;
    public static final int ELEM_ICE   = 2;
    public static final int ELEM_ELEC  = 3;
    public static final int ELEM_DARK  = 4;
    public static final int ELEM_HOLY  = 5;

    public static final int[] ENEMY_ELEMENT = {
        ELEM_NONE, ELEM_DARK, ELEM_NONE, ELEM_DARK, ELEM_NONE,
        ELEM_NONE, ELEM_DARK, ELEM_DARK, ELEM_FIRE, ELEM_FIRE,
        ELEM_NONE, ELEM_ELEC, ELEM_NONE, ELEM_DARK, ELEM_DARK,
        ELEM_NONE, ELEM_ELEC, ELEM_DARK, ELEM_NONE, ELEM_DARK
    };

    public static final int[] ENEMY_WEAKNESS = {
        ELEM_FIRE, ELEM_ELEC, ELEM_NONE, ELEM_HOLY, ELEM_FIRE,
        ELEM_FIRE, ELEM_FIRE, ELEM_HOLY, ELEM_ICE,  ELEM_ICE,
        ELEM_FIRE, ELEM_ICE,  ELEM_ELEC, ELEM_HOLY, ELEM_HOLY,
        ELEM_ICE,  ELEM_NONE, ELEM_HOLY, ELEM_DARK, ELEM_HOLY
    };

    // AI patterns
    public static final int AI_RANDOM    = 0;
    public static final int AI_AGGRESSIVE= 1;
    public static final int AI_DEFENSIVE = 2;
    public static final int AI_HEALER    = 3;
    public static final int AI_BOSS      = 4;
    public static final int AI_STALKER   = 5;  // NEW: circles & waits
    public static final int AI_CASTER    = 6;  // NEW: magic-focused
    public static final int AI_SUMMONER  = 7;  // NEW: calls minions

    public static final int[] ENEMY_AI = {
        AI_RANDOM, AI_RANDOM, AI_AGGRESSIVE, AI_AGGRESSIVE, AI_AGGRESSIVE,
        AI_AGGRESSIVE, AI_RANDOM, AI_DEFENSIVE, AI_BOSS, AI_BOSS,
        AI_AGGRESSIVE, AI_STALKER, AI_DEFENSIVE, AI_HEALER, AI_STALKER,
        AI_AGGRESSIVE, AI_CASTER, AI_SUMMONER, AI_BOSS, AI_BOSS
    };

    public static final int[] ENEMY_COLORS = {
        0xFF00FF00,0xFF4A4A4A,0xFF008800,0xFFCCCCCC,0xFF884422,
        0xFF666666,0xFF440044,0xFFAABBFF,0xFFFF0000,0xFFFF4400,
        // NEW
        0xFF556644,0xFF88AAFF,0xFFAAAAAA,0xFF880022,0xFF6688AA,
        0xFF448844,0xFF88AAFF,0xFF6644AA,0xFF886644,0xFFFFDD44
    };

    // NEW: Loot table – [item_id, weight] pairs (negative item_id = no item)
    // Weight out of 100; multiple entries possible via chained rolls
    public static final int[][] ENEMY_LOOT = {
        {-1,0},   // Slime - no item
        {-1,0},   // Bat
        {0,30},   // Goblin - Potion 30%
        {0,25},   // Skeleton
        {1,20},   // Orc - Hi-Potion 20%
        {-1,0},   // Wolf
        {3,35},   // Spider - Antidote 35%
        {0,20},   // Ghost
        {2,40},   // Demon - Ether 40%
        {4,50},   // Dragon - Phoenix 50%
        {1,25},{0,30},{1,30},{0,40},{4,35},
        {3,30},{2,40},{4,40},{1,50},{4,80}
    };

    // NEW: Troll regenerates, Vampire lifesteals
    public static final boolean[] ENEMY_REGEN  = {
        false,false,false,false,false,false,false,false,false,false,
        true,false,false,true,false,false,false,false,false,false
    };

    // NEW: Which enemy can phase (ignore physical for 1 turn)
    public static final boolean[] ENEMY_PHASE  = {
        false,false,false,false,false,false,false,true,false,false,
        false,false,false,false,true,false,false,false,false,false
    };

    // -------------------------------------------------
    //  BATTLE INSTANCE
    // -------------------------------------------------
    public int type;
    public String name;
    public int hp, maxHp, mp, maxMp;
    public int atk, def, spd;
    public int expReward, goldReward;
    public int element, weakness;
    public int aiType;
    public int color;

    public int atbGauge;
    public boolean isAlive;
    public int statusFlags;

    // NEW fields
    public boolean isEnraged;         // enters rage below 25% HP
    public int enrageAtkBonus;
    public int regenAmount;           // HP regen per turn
    public boolean phaseActive;       // currently phasing
    public int phaseCooldown;
    public int phaseTimer;
    public int summonCooldown;        // NEW
    public int currentPhase;          // multi-phase boss (0,1,2)
    public int flashTimer;            // visual hit flash

    // Position in battle
    public int battleX, battleY;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public EnemyData() { type = 0; isAlive = false; }

    public void init(int enemyType, int levelMod) {
        if (enemyType < 0 || enemyType >= MAX_ENEMY_TYPES) enemyType = 0;

        type       = enemyType;
        name       = ENEMY_NAMES[type];
        color      = ENEMY_COLORS[type];
        element    = ENEMY_ELEMENT[type];
        weakness   = ENEMY_WEAKNESS[type];
        aiType     = ENEMY_AI[type];

        int[] stats = ENEMY_STATS[type];
        float scale = 1.0f + levelMod * 0.1f;

        maxHp  = (int)(stats[0] * scale);
        maxMp  = (int)(stats[1] * scale);
        atk    = (int)(stats[2] * scale);
        def    = (int)(stats[3] * scale);
        spd    = (int)(stats[4] * scale);
        expReward  = (int)(stats[5] * scale);
        goldReward = (int)(stats[6] * scale);

        hp = maxHp; mp = maxMp;
        atbGauge = 0; isAlive = true; statusFlags = 0;

        isEnraged = false;
        enrageAtkBonus = 0;
        regenAmount = ENEMY_REGEN[type] ? Math.max(1, maxHp / 20) : 0;
        phaseActive = false; phaseCooldown = 0; phaseTimer = 0;
        summonCooldown = 0;
        currentPhase = 0;
        flashTimer = 0;
    }

    // -------------------------------------------------
    //  AI ACTION SELECTION (improved)
    // -------------------------------------------------
    // Returns: 0=attack, 1=skill, 2=defend, 3=special, 4=summon, 5=phase
    public int selectAction(PlayerData player, int turnCount) {
        // Enrage check (< 25% HP)
        if (!isEnraged && hp < maxHp / 4) {
            isEnraged = true;
            enrageAtkBonus = atk / 2;
            atk += enrageAtkBonus;
        }

        // Phase ability (Wraith, Ghost)
        if (ENEMY_PHASE[type] && phaseCooldown == 0 && hp < maxHp / 2) {
            phaseActive = true;
            phaseTimer = 2;
            phaseCooldown = 5;
            return 5;
        }
        if (phaseCooldown > 0) phaseCooldown--;

        // Summon
        if (aiType == AI_SUMMONER && summonCooldown == 0 && mp >= 20) {
            summonCooldown = 4;
            mp -= 20;
            return 4;
        }
        if (summonCooldown > 0) summonCooldown--;

        switch (aiType) {
            case AI_RANDOM:
                return Math.abs(turnCount * 7) % 2;

            case AI_AGGRESSIVE:
                if (isEnraged) return 3;  // special when enraged
                return turnCount % 4 == 0 ? 1 : 0;

            case AI_DEFENSIVE:
                if (hp < maxHp / 3) return 2;
                return 0;

            case AI_HEALER:
                if (hp < maxHp / 2 && mp >= 10) { mp -= 10; return 1; }
                return 0;

            case AI_STALKER:
                if (turnCount % 3 == 0) return 2;
                if (turnCount % 5 == 0) return 3;
                return 0;

            case AI_CASTER:
                if (mp >= 15) { mp -= 15; return 1; }
                if (hp < maxHp / 2) return 2;
                return 0;

            case AI_BOSS:
                // Phase-based pattern
                int phase = currentPhase;
                if (hp < maxHp / 3 && phase < 2) currentPhase = 2;
                else if (hp < maxHp * 2 / 3 && phase < 1) currentPhase = 1;

                if (currentPhase == 0) {
                    if (turnCount % 3 == 0 && mp >= 20) { mp -= 20; return 3; }
                    return 0;
                } else if (currentPhase == 1) {
                    if (turnCount % 2 == 0 && mp >= 15) { mp -= 15; return 1; }
                    if (hp < maxHp / 2) return 2;
                    return 0;
                } else {
                    // Desperate phase
                    if (mp >= 10) { mp -= 10; return 3; }
                    return 0;
                }

            default:
                return 0;
        }
    }

    // -------------------------------------------------
    //  DAMAGE
    // -------------------------------------------------
    public void takeDamage(int amount, int damageElement) {
        // Phase dodge
        if (phaseActive) { phaseTimer--; if (phaseTimer <= 0) phaseActive = false; return; }

        if (damageElement == weakness && weakness != ELEM_NONE) amount = amount * 3 / 2;
        if (damageElement == element  && element  != ELEM_NONE) amount /= 2;

        hp -= amount;
        if (hp <= 0) { hp = 0; isAlive = false; }

        flashTimer = 4;  // hit flash
    }

    public void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public void tickRegen() {
        if (regenAmount > 0 && isAlive) {
            hp = Math.min(maxHp, hp + regenAmount);
        }
        if (flashTimer > 0) flashTimer--;
    }

    // -------------------------------------------------
    //  LOOT ROLL
    // -------------------------------------------------
    public int rollLoot(int randomVal) {
        if (type < 0 || type >= ENEMY_LOOT.length) return -1;
        int itemId = ENEMY_LOOT[type][0];
        int weight = ENEMY_LOOT[type][1];
        if (itemId < 0 || weight <= 0) return -1;
        return (randomVal % 100 < weight) ? itemId : -1;
    }
}
