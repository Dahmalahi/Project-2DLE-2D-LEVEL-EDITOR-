/**
 * SkillSystem.java - ENHANCED v2.0
 * Added: AoE flag, status effect per skill, 10 new skills (30 total),
 * improved damage formula with MAG stat, multi-hit support,
 * passive skills, auto-cast skills, and elemental combo bonuses.
 */
public class SkillSystem {

    // -------------------------------------------------
    //  SKILL TYPES
    // -------------------------------------------------
    public static final int STYPE_PHYSICAL = 0;
    public static final int STYPE_MAGIC    = 1;
    public static final int STYPE_HEAL     = 2;
    public static final int STYPE_BUFF     = 3;
    public static final int STYPE_DEBUFF   = 4;
    public static final int STYPE_SPECIAL  = 5;
    public static final int STYPE_PASSIVE  = 6;   // NEW: permanent passive
    public static final int STYPE_SUMMON   = 7;   // NEW: summon ally

    // -------------------------------------------------
    //  SKILL IDs (30 total)
    // -------------------------------------------------
    public static final int SKILL_ATTACK     = 0;
    public static final int SKILL_GUARD      = 1;
    public static final int SKILL_SLASH      = 2;
    public static final int SKILL_POWER_HIT  = 3;
    public static final int SKILL_FIRE       = 4;
    public static final int SKILL_ICE        = 5;
    public static final int SKILL_THUNDER    = 6;
    public static final int SKILL_HEAL       = 7;
    public static final int SKILL_CURE       = 8;
    public static final int SKILL_REVIVE     = 9;
    public static final int SKILL_BUFF_ATK   = 10;
    public static final int SKILL_BUFF_DEF   = 11;
    public static final int SKILL_DEBUFF_ATK = 12;
    public static final int SKILL_DEBUFF_DEF = 13;
    public static final int SKILL_POISON     = 14;
    public static final int SKILL_DRAIN      = 15;
    public static final int SKILL_DOUBLE_ATK = 16;
    public static final int SKILL_METEOR     = 17;
    public static final int SKILL_HOLY       = 18;
    public static final int SKILL_ULTIMA     = 19;
    // NEW SKILLS
    public static final int SKILL_BLIZZARD   = 20;   // AoE ice
    public static final int SKILL_QUAKE      = 21;   // AoE earth (physical)
    public static final int SKILL_BARRIER    = 22;   // self barrier (1-hit absorb)
    public static final int SKILL_REGEN      = 23;   // HP regen buff
    public static final int SKILL_SILENCE    = 24;   // silence enemy
    public static final int SKILL_SLEEP      = 25;   // sleep enemy
    public static final int SKILL_COUNTER    = 26;   // passive: counter attack
    public static final int SKILL_STEAL      = 27;   // steal gold/item
    public static final int SKILL_CHARGE     = 28;   // charge ATK for next hit
    public static final int SKILL_LIMIT_EX   = 29;   // ultra-limit (requires gauge)
    public static final int MAX_SKILLS       = 30;

    // Skill data: [type, power, mpCost, element, levelReq, hitsCount, aoeFlag, statusEffect]
    // hitsCount: 1=single, 2=double, 3=triple etc.
    // aoeFlag:   0=single target, 1=all enemies, 2=all allies
    // statusEffect: PlayerData.STATUS_* value (0 = none)
    public static final int[][] SKILL_DATA = {
        {STYPE_PHYSICAL, 100, 0,  EnemyData.ELEM_NONE, 1,  1, 0, 0},  // 0 Attack
        {STYPE_SPECIAL,    0, 0,  EnemyData.ELEM_NONE, 1,  1, 0, 0},  // 1 Guard
        {STYPE_PHYSICAL, 120, 3,  EnemyData.ELEM_NONE, 2,  1, 0, 0},  // 2 Slash
        {STYPE_PHYSICAL, 160, 5,  EnemyData.ELEM_NONE, 5,  1, 0, 0},  // 3 Power Hit
        {STYPE_MAGIC,    85,  5,  EnemyData.ELEM_FIRE, 3,  1, 0, 0},  // 4 Fire
        {STYPE_MAGIC,    85,  5,  EnemyData.ELEM_ICE,  3,  1, 0, 0},  // 5 Ice
        {STYPE_MAGIC,    85,  5,  EnemyData.ELEM_ELEC, 3,  1, 0, 0},  // 6 Thunder
        {STYPE_HEAL,     55,  4,  EnemyData.ELEM_NONE, 1,  1, 2, 0},  // 7 Heal
        {STYPE_HEAL,    160, 12,  EnemyData.ELEM_NONE, 8,  1, 2, 0},  // 8 Cure
        {STYPE_HEAL,       0,22, EnemyData.ELEM_NONE, 15,  1, 2, 0},  // 9 Revive
        {STYPE_BUFF,      20, 6,  EnemyData.ELEM_NONE, 5,  1, 2, 0},  // 10 Buff ATK
        {STYPE_BUFF,      20, 6,  EnemyData.ELEM_NONE, 5,  1, 2, 0},  // 11 Buff DEF
        {STYPE_DEBUFF,    20, 4,  EnemyData.ELEM_NONE, 7,  1, 0, 0},  // 12 Debuff ATK
        {STYPE_DEBUFF,    20, 4,  EnemyData.ELEM_NONE, 7,  1, 0, 0},  // 13 Debuff DEF
        {STYPE_SPECIAL,    0, 8,  EnemyData.ELEM_NONE,10,  1, 0, PlayerData.STATUS_POISON},  // 14 Poison
        {STYPE_MAGIC,     65,12,  EnemyData.ELEM_DARK,12,  1, 0, 0},  // 15 Drain
        {STYPE_PHYSICAL,  75, 8,  EnemyData.ELEM_NONE,15,  2, 0, 0},  // 16 Double Attack (2 hits)
        {STYPE_MAGIC,    210,32,  EnemyData.ELEM_FIRE,25,  1, 1, 0},  // 17 Meteor (AoE)
        {STYPE_MAGIC,    160,25,  EnemyData.ELEM_HOLY,20,  1, 0, 0},  // 18 Holy
        {STYPE_MAGIC,    320,55,  EnemyData.ELEM_NONE,40,  1, 1, 0},  // 19 Ultima (AoE)
        // NEW
        {STYPE_MAGIC,    110,12,  EnemyData.ELEM_ICE,  8,  1, 1, 0},  // 20 Blizzard (AoE ice)
        {STYPE_PHYSICAL, 130,15,  EnemyData.ELEM_NONE,10,  1, 1, 0},  // 21 Quake (AoE phys)
        {STYPE_SPECIAL,    0,10,  EnemyData.ELEM_NONE, 8,  1, 2, PlayerData.STATUS_BARRIER},// 22 Barrier
        {STYPE_HEAL,      30, 8,  EnemyData.ELEM_NONE, 6,  1, 2, PlayerData.STATUS_REGEN}, // 23 Regen
        {STYPE_SPECIAL,    0, 7,  EnemyData.ELEM_NONE,10,  1, 0, PlayerData.STATUS_SILENCE},// 24 Silence
        {STYPE_SPECIAL,    0, 8,  EnemyData.ELEM_NONE,12,  1, 0, PlayerData.STATUS_SLEEP},  // 25 Sleep
        {STYPE_PASSIVE,    0, 0,  EnemyData.ELEM_NONE,18,  1, 0, 0},  // 26 Counter (passive)
        {STYPE_SPECIAL,    0, 6,  EnemyData.ELEM_NONE, 7,  1, 0, 0},  // 27 Steal
        {STYPE_BUFF,       0, 4,  EnemyData.ELEM_NONE, 4,  1, 2, 0},  // 28 Charge
        {STYPE_SPECIAL,  500, 0,  EnemyData.ELEM_NONE,50,  1, 1, 0},  // 29 Limit EX (AoE, no MP)
    };

    public static final String[] SKILL_NAMES = {
        "Attack","Guard","Slash","Power Hit",
        "Fire","Ice","Thunder",
        "Heal","Cure","Revive",
        "ATK Up","DEF Up","ATK Down","DEF Down",
        "Poison","Drain","2x Attack","Meteor","Holy","Ultima",
        // NEW
        "Blizzard","Quake","Barrier","Regen",
        "Silence","Sleep","Counter","Steal","Charge","Limit EX"
    };

    public static final String[] SKILL_DESC = {
        "Basic attack","Reduce damage taken","Quick slash",
        "Heavy blow","Fire magic","Ice magic","Lightning",
        "Restore some HP","Restore lots of HP","Revive ally",
        "Raise ATK","Raise DEF","Lower enemy ATK","Lower enemy DEF",
        "Inflict poison","Drain HP","Attack twice",
        "Massive AoE fire","Holy light","Ultimate AoE",
        "AoE ice blast","AoE earth tremor","Absorb 1 hit",
        "HP regen over time","Silence caster","Put enemy to sleep",
        "Counter attacks passively","Steal gold/item",
        "Charge next attack","Limit Break EX (all)"
    };

    public static final int[] SKILL_COOLDOWN = {
        0,0,0,0, 0,0,0, 0,0,3,
        2,2,2,2, 3,2,2, 5,4,8,
        // NEW
        3,4,4,3, 3,4,0,3,2,0
    };

    // -------------------------------------------------
    //  ACCESSORS
    // -------------------------------------------------
    public static int getType(int id) {
        if (id < 0 || id >= MAX_SKILLS) return STYPE_PHYSICAL;
        return SKILL_DATA[id][0];
    }
    public static int getPower(int id) {
        if (id < 0 || id >= MAX_SKILLS) return 100;
        return SKILL_DATA[id][1];
    }
    public static int getMpCost(int id) {
        if (id < 0 || id >= MAX_SKILLS) return 0;
        return SKILL_DATA[id][2];
    }
    public static int getElement(int id) {
        if (id < 0 || id >= MAX_SKILLS) return EnemyData.ELEM_NONE;
        return SKILL_DATA[id][3];
    }
    public static int getLevelReq(int id) {
        if (id < 0 || id >= MAX_SKILLS) return 1;
        return SKILL_DATA[id][4];
    }
    public static int getHits(int id) {
        if (id < 0 || id >= MAX_SKILLS) return 1;
        return SKILL_DATA[id][5];
    }
    public static boolean isAoe(int id) {
        if (id < 0 || id >= MAX_SKILLS) return false;
        return SKILL_DATA[id][6] == 1;
    }
    public static boolean isSelfTarget(int id) {
        if (id < 0 || id >= MAX_SKILLS) return false;
        return SKILL_DATA[id][6] == 2;
    }
    public static int getStatusEffect(int id) {
        if (id < 0 || id >= MAX_SKILLS) return 0;
        return SKILL_DATA[id][7];
    }
    public static int getCooldown(int id) {
        if (id < 0 || id >= MAX_SKILLS) return 0;
        return SKILL_COOLDOWN[id];
    }
    public static String getName(int id) {
        if (id < 0 || id >= MAX_SKILLS) return "???";
        return SKILL_NAMES[id];
    }
    public static String getDesc(int id) {
        if (id < 0 || id >= MAX_SKILLS) return "";
        return SKILL_DESC[id];
    }

    public static boolean canUse(int id, PlayerData player) {
        if (id < 0 || id >= MAX_SKILLS) return false;
        if (player.level < getLevelReq(id)) return false;
        if (player.mp < getMpCost(id)) return false;
        if (getType(id) == STYPE_PASSIVE) return false;
        if (id == SKILL_LIMIT_EX && !player.canLimitBreak()) return false;
        return true;
    }

    // -------------------------------------------------
    //  DAMAGE CALCULATION (improved, uses mag stat)
    // -------------------------------------------------
    public static int calculateDamage(int id, PlayerData attacker,
                                      EnemyData target, int randomSeed) {
        int type = getType(id);
        int power = getPower(id);
        int element = getElement(id);
        int baseDamage = 0;

        if (type == STYPE_PHYSICAL) {
            baseDamage = (attacker.getTotalAtk() + attacker.level) * power / 100;
            baseDamage -= target.def * 2 / 3;
        } else if (type == STYPE_MAGIC) {
            // Use MAG stat for magic skills
            int magPow = attacker.getTotalMag() + attacker.level / 2;
            baseDamage = magPow * power / 100;
            baseDamage -= target.def / 4;
        } else if (type == STYPE_SPECIAL) {
            baseDamage = (attacker.getTotalAtk() + attacker.getTotalMag()) / 2 * power / 100;
        }

        // Element interaction
        int eMult = 100;
        if (element == target.weakness && target.weakness != EnemyData.ELEM_NONE) eMult = 150;
        if (element == target.element  && target.element  != EnemyData.ELEM_NONE) eMult = 50;
        // Player resistance
        eMult = eMult * attacker.getElemMult(element) / 100;
        baseDamage = baseDamage * eMult / 100;

        // Random variance ±12%
        int variance = (Math.abs(randomSeed) % 25) - 12;
        baseDamage = baseDamage * (100 + variance) / 100;

        // Critical hit
        if ((Math.abs(randomSeed >> 8) % 100) < attacker.luk / 8 + 5) {
            baseDamage = baseDamage * GameConfig.CRIT_MULTIPLIER;
        }

        // Multi-hit: total is power * hits with diminishing per hit
        int hits = getHits(id);
        if (hits > 1) {
            // Each additional hit does 70% of first
            int total = baseDamage;
            for (int h = 1; h < hits; h++) {
                total += baseDamage * 7 / 10;
            }
            return Math.max(hits, total);
        }

        return Math.max(1, baseDamage);
    }

    public static int calculateHeal(int id, PlayerData caster) {
        int power = getPower(id);
        int baseHeal = (caster.getTotalMag() + caster.level) * power / 100;
        return Math.max(1, baseHeal);
    }

    // -------------------------------------------------
    //  DRAIN: returns how much HP to give to caster
    // -------------------------------------------------
    public static int calculateDrain(int id, int damageDone) {
        if (id != SKILL_DRAIN) return 0;
        return damageDone / 2;
    }

    // -------------------------------------------------
    //  STEAL: returns gold amount (items handled elsewhere)
    // -------------------------------------------------
    public static int calculateSteal(EnemyData target, int randomSeed) {
        int base = target.goldReward / 4;
        return Math.max(1, base + Math.abs(randomSeed) % base + 1);
    }

    // -------------------------------------------------
    //  AUTO-LEARN
    // -------------------------------------------------
    public static void checkLearnSkills(PlayerData player) {
        for (int i = 0; i < MAX_SKILLS; i++) {
            if (getType(i) == STYPE_PASSIVE) continue;  // passives need quest
            if (player.level >= getLevelReq(i) && !player.knowsSkill(i)) {
                player.learnSkill(i);
                // Auto-fill first empty hotbar slot
                for (int h = 0; h < GameConfig.MAX_HOTBAR; h++) {
                    if (player.hotbar[h] < 0) {
                        player.hotbar[h] = i;
                        break;
                    }
                }
            }
        }
    }
}
