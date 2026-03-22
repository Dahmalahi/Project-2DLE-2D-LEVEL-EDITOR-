import java.io.*;

/**
 * PlayerData.java - ENHANCED v2.0
 * Added: stamina, limit break gauge, title/class system,
 * elemental resistances, relationship flags, achievement hooks,
 * hotbar, dual-wield support, and crafting skill level.
 */
public class PlayerData {

    // -------------------------------------------------
    //  CLASS / TITLE (NEW)
    // -------------------------------------------------
    public static final int CLASS_WARRIOR  = 0;
    public static final int CLASS_MAGE     = 1;
    public static final int CLASS_ROGUE    = 2;
    public static final int CLASS_PALADIN  = 3;
    public static final int CLASS_RANGER   = 4;
    public static final int CLASS_MONK     = 5;
    public static final String[] CLASS_NAMES = {
        "Warrior","Mage","Rogue","Paladin","Ranger","Monk"
    };

    // -------------------------------------------------
    //  BASIC STATS
    // -------------------------------------------------
    public String name;
    public int classId;
    public int level;
    public int exp;
    public int expNext;

    public int hp;
    public int maxHp;
    public int mp;
    public int maxMp;
    public int sp;        // NEW: Stamina (used for dashes, special moves)
    public int maxSp;

    public int atk;
    public int def;
    public int spd;
    public int luk;
    public int mag;       // NEW: Magic power separate from atk
    public int res;       // NEW: Magic resistance
    public int gold;      // Carried gold (mirrored from InventorySystem for quick HUD access)

    // -------------------------------------------------
    //  POSITION
    // -------------------------------------------------
    public int mapId;
    public int x, y;
    public int direction;  // 0=down,1=up,2=left,3=right
    public int frame;
    public int prevX, prevY;   // NEW: for interpolated rendering

    // -------------------------------------------------
    //  MOVEMENT STATE
    // -------------------------------------------------
    public boolean isRunning;
    public boolean isSwimming;
    public boolean isSliding;
    public boolean isDashing;     // NEW
    public boolean isClimbing;    // NEW: ladders/ropes
    public boolean isCrouching;   // NEW
    public int slideDir;
    public int dashCooldown;      // NEW
    public int dashTimer;         // NEW

    // -------------------------------------------------
    //  BATTLE STATE
    // -------------------------------------------------
    public int atbGauge;
    public boolean isGuarding;
    public boolean isAlive;
    public int limitGauge;        // NEW: Limit Break (0-100)
    public int comboCount;        // NEW
    public long comboTimer;       // NEW
    public int turnCount;         // NEW: turns in current battle

    // -------------------------------------------------
    //  STATUS EFFECTS
    // -------------------------------------------------
    public static final int STATUS_NONE     = 0;
    public static final int STATUS_POISON   = 1;
    public static final int STATUS_BURN     = 2;
    public static final int STATUS_FREEZE   = 4;
    public static final int STATUS_PARALYZE = 8;
    public static final int STATUS_SLEEP    = 16;
    public static final int STATUS_BLIND    = 32;
    public static final int STATUS_SILENCE  = 64;   // NEW: can't cast spells
    public static final int STATUS_CONFUSE  = 128;  // NEW: random targeting
    public static final int STATUS_REGEN    = 256;  // NEW: HP regen per turn
    public static final int STATUS_BARRIER  = 512;  // NEW: absorbs 1 hit

    public int statusFlags;
    public int[] statusDuration;

    // -------------------------------------------------
    //  ELEMENTAL RESISTANCES (NEW)
    // -------------------------------------------------
    // +100 = immune, +50 = halved, 0 = normal, -50 = weak (x1.5), -100 = x2
    public int[] elemResist;  // indexed by EnemyData.ELEM_*

    // -------------------------------------------------
    //  BUFFS/DEBUFFS
    // -------------------------------------------------
    public int atkBuff;
    public int defBuff;
    public int spdBuff;
    public int magBuff;       // NEW
    public int resBuff;       // NEW
    public int buffDuration;

    // -------------------------------------------------
    //  EQUIPMENT SLOTS
    // -------------------------------------------------
    public static final int EQUIP_WEAPON  = 0;
    public static final int EQUIP_OFFHAND = 1;   // NEW: shield or second weapon
    public static final int EQUIP_ARMOR   = 2;
    public static final int EQUIP_HELM    = 3;
    public static final int EQUIP_ACCESS1 = 4;
    public static final int EQUIP_ACCESS2 = 5;   // NEW
    public static final int EQUIP_BOOTS   = 6;   // NEW

    public int[] equipment;

    // -------------------------------------------------
    //  SKILLS
    // -------------------------------------------------
    public int[] skills;
    public int skillCount;
    public int[] hotbar;          // NEW: quick-access skills
    public int[] skillCooldowns;  // NEW: per-skill cooldown counters

    // -------------------------------------------------
    //  CRAFTING (NEW)
    // -------------------------------------------------
    public int craftingLevel;     // 1-10
    public int craftingExp;

    // -------------------------------------------------
    //  RELATIONSHIP FLAGS (NEW)
    // -------------------------------------------------
    public int[] npcRelation;     // -100 to 100 per NPC
    public int factionReputation; // global faction rep

    // -------------------------------------------------
    //  COUNTERS
    // -------------------------------------------------
    public int stepCount;
    public int battleCount;
    public int escapeCount;
    public int killCount;         // NEW
    public int craftCount;        // NEW
    public int chestsOpened;      // NEW
    public int totalDmgDealt;     // NEW
    public int totalDmgTaken;     // NEW
    public long playTimeMs;       // NEW: accurate playtime

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public PlayerData() {
        name = "Hero";
        classId = CLASS_WARRIOR;
        level = 1;
        exp = 0;
        expNext = 100;

        maxHp = 100; hp = maxHp;
        maxMp = 20;  mp = maxMp;
        maxSp = 50;  sp = maxSp;

        atk = 10; def = 10; spd = 10; luk = 10;
        mag = 5;  res = 5;
        gold = 0;

        mapId = 0; x = 0; y = 0; direction = 0; frame = 0;
        prevX = 0; prevY = 0;

        isRunning = false; isSwimming = false;
        isSliding = false; isDashing = false;
        isClimbing = false; isCrouching = false;
        dashCooldown = 0; dashTimer = 0;

        isAlive = true; isGuarding = false;
        atbGauge = 0; limitGauge = 0;
        comboCount = 0; comboTimer = 0; turnCount = 0;

        statusFlags = STATUS_NONE;
        statusDuration = new int[10];

        elemResist = new int[6]; // EnemyData.MAX_ELEM

        atkBuff = 0; defBuff = 0; spdBuff = 0;
        magBuff = 0; resBuff = 0; buffDuration = 0;

        equipment = new int[GameConfig.MAX_EQUIPMENT];
        for (int i = 0; i < GameConfig.MAX_EQUIPMENT; i++) equipment[i] = -1;

        skills = new int[GameConfig.MAX_SKILLS];
        skillCount = 0;
        hotbar = new int[GameConfig.MAX_HOTBAR];
        for (int i = 0; i < GameConfig.MAX_HOTBAR; i++) hotbar[i] = -1;
        skillCooldowns = new int[GameConfig.MAX_SKILLS];

        craftingLevel = 1; craftingExp = 0;

        npcRelation = new int[GameConfig.MAX_NPCS];
        factionReputation = 0;

        stepCount = 0; battleCount = 0; escapeCount = 0;
        killCount = 0; craftCount = 0; chestsOpened = 0;
        totalDmgDealt = 0; totalDmgTaken = 0; playTimeMs = 0;
    }

    // -------------------------------------------------
    //  LEVEL UP
    // -------------------------------------------------
    public void addExp(int amount) {
        exp += amount;
        while (exp >= expNext && level < GameConfig.MAX_LEVEL) {
            exp -= expNext;
            levelUp();
        }
    }

    public void levelUp() {
        level++;
        expNext = calculateExpNext(level);

        // Class-based stat gains
        int hpGain, mpGain, atkGain, defGain, spdGain, magGain, resGain;
        switch (classId) {
            case CLASS_WARRIOR:
                hpGain = 14 + level/4; mpGain = 1; atkGain = 2;
                defGain = 2; spdGain = 1; magGain = 0; resGain = 1; break;
            case CLASS_MAGE:
                hpGain = 7 + level/5; mpGain = 5 + level/8; atkGain = 1;
                defGain = 1; spdGain = 1; magGain = 3; resGain = 2; break;
            case CLASS_ROGUE:
                hpGain = 10 + level/5; mpGain = 2; atkGain = 2;
                defGain = 1; spdGain = 3; magGain = 1; resGain = 1; break;
            case CLASS_PALADIN:
                hpGain = 12 + level/4; mpGain = 3; atkGain = 1;
                defGain = 2; spdGain = 1; magGain = 1; resGain = 2; break;
            case CLASS_RANGER:
                hpGain = 11 + level/5; mpGain = 2; atkGain = 2;
                defGain = 1; spdGain = 2; magGain = 1; resGain = 1; break;
            default: // MONK
                hpGain = 12 + level/4; mpGain = 2; atkGain = 2;
                defGain = 1; spdGain = 2; magGain = 1; resGain = 1; break;
        }

        maxHp  = Math.min(GameConfig.MAX_HP,   maxHp  + hpGain);
        maxMp  = Math.min(GameConfig.MAX_MP,   maxMp  + mpGain);
        maxSp  = Math.min(GameConfig.MAX_SP,   maxSp  + level/10 + 1);
        atk    = Math.min(GameConfig.MAX_STAT, atk    + atkGain);
        def    = Math.min(GameConfig.MAX_STAT, def    + defGain);
        spd    = Math.min(GameConfig.MAX_STAT, spd    + spdGain);
        mag    = Math.min(GameConfig.MAX_STAT, mag    + magGain);
        res    = Math.min(GameConfig.MAX_STAT, res    + resGain);
        luk    = Math.min(GameConfig.MAX_STAT, luk    + 1 + level/20);

        hp = maxHp; mp = maxMp; sp = maxSp;

        // NEW: Limit gauge boost on level up
        limitGauge = Math.min(GameConfig.LIMIT_GAUGE_MAX, limitGauge + 10);
    }

    private int calculateExpNext(int lv) {
        return 100 + (lv * lv * 10);
    }

    // -------------------------------------------------
    //  DASH (NEW)
    // -------------------------------------------------
    public boolean canDash() {
        return sp >= 10 && dashCooldown <= 0;
    }

    public void dash() {
        if (!canDash()) return;
        sp -= 10;
        dashTimer = 8;
        dashCooldown = 30;
        isDashing = true;
    }

    public void updateDash() {
        if (dashTimer > 0) { dashTimer--; }
        else isDashing = false;
        if (dashCooldown > 0) dashCooldown--;
        // SP regen
        if (sp < maxSp && !isDashing) {
            sp = Math.min(maxSp, sp + 1);
        }
    }

    // -------------------------------------------------
    //  STATUS EFFECTS
    // -------------------------------------------------
    public boolean hasStatus(int status) {
        return (statusFlags & status) != 0;
    }

    public void addStatus(int status, int duration) {
        statusFlags |= status;
        int idx = getStatusIndex(status);
        if (idx >= 0 && idx < statusDuration.length) {
            statusDuration[idx] = Math.max(statusDuration[idx], duration);
        }
    }

    public void removeStatus(int status) {
        statusFlags &= ~status;
    }

    public void updateStatus() {
        for (int i = 0; i < 10; i++) {
            if (statusDuration[i] > 0) {
                statusDuration[i]--;
                if (statusDuration[i] == 0) statusFlags &= ~(1 << i);
            }
        }
        if (hasStatus(STATUS_POISON)) {
            hp -= Math.max(1, maxHp / 20);
            if (hp < 1) hp = 1;
        }
        if (hasStatus(STATUS_BURN)) {
            hp -= Math.max(1, maxHp / 10);
            if (hp < 1) hp = 1;
        }
        // NEW: REGEN effect
        if (hasStatus(STATUS_REGEN)) {
            hp = Math.min(maxHp, hp + maxHp / 15);
        }
    }

    private int getStatusIndex(int status) {
        for (int i = 0; i < 10; i++) {
            if ((1 << i) == status) return i;
        }
        return -1;
    }

    // -------------------------------------------------
    //  BUFFS
    // -------------------------------------------------
    public void applyBuff(int atkMod, int defMod, int spdMod, int duration) {
        applyBuff(atkMod, defMod, spdMod, 0, 0, duration);
    }

    public void applyBuff(int atkMod, int defMod, int spdMod,
                          int magMod, int resMod, int duration) {
        atkBuff = atkMod; defBuff = defMod; spdBuff = spdMod;
        magBuff = magMod; resBuff = resMod;
        buffDuration = duration;
    }

    public void updateBuffs() {
        if (buffDuration > 0) {
            buffDuration--;
            if (buffDuration == 0) {
                atkBuff = 0; defBuff = 0; spdBuff = 0;
                magBuff = 0; resBuff = 0;
            }
        }
        // Update skill cooldowns
        for (int i = 0; i < GameConfig.MAX_SKILLS; i++) {
            if (skillCooldowns[i] > 0) skillCooldowns[i]--;
        }
    }

    // -------------------------------------------------
    //  COMBAT STATS (total with equipment + buffs)
    // -------------------------------------------------
    public int getTotalAtk() { return Math.max(1, atk + atkBuff); }
    public int getTotalDef() { return Math.max(0, def + defBuff); }
    public int getTotalSpd() { return Math.max(1, spd + spdBuff); }
    public int getTotalMag() { return Math.max(1, mag + magBuff); }
    public int getTotalRes() { return Math.max(0, res + resBuff); }

    // NEW: Element damage multiplier (100 = normal, 200 = x2, 50 = half)
    public int getElemMult(int elem) {
        if (elem <= 0 || elem >= elemResist.length) return 100;
        int r = elemResist[elem];
        if (r >= 100) return 0;         // immune
        if (r >= 50)  return 50;        // half
        if (r <= -100) return 200;      // double
        if (r <= -50)  return 150;      // weak
        return 100;                      // normal
    }

    // -------------------------------------------------
    //  LIMIT BREAK (NEW)
    // -------------------------------------------------
    public void addLimitGauge(int amount) {
        limitGauge = Math.min(GameConfig.LIMIT_GAUGE_MAX, limitGauge + amount);
    }

    public boolean canLimitBreak() {
        return limitGauge >= GameConfig.LIMIT_GAUGE_MAX;
    }

    public void useLimitBreak() {
        limitGauge = 0;
    }

    // -------------------------------------------------
    //  COMBO (NEW)
    // -------------------------------------------------
    public void registerHit() {
        long now = System.currentTimeMillis();
        if (now - comboTimer < GameConfig.CHAIN_WINDOW_MS) {
            comboCount = Math.min(GameConfig.MAX_COMBO, comboCount + 1);
        } else {
            comboCount = 1;
        }
        comboTimer = now;
    }

    public void resetCombo() {
        comboCount = 0;
    }

    public int getComboBonus() {
        return comboCount * GameConfig.COMBO_BONUS_PER;  // % bonus
    }

    // -------------------------------------------------
    //  SKILLS
    // -------------------------------------------------
    public void learnSkill(int skillId) {
        if (skillCount >= GameConfig.MAX_SKILLS) return;
        for (int i = 0; i < skillCount; i++) {
            if (skills[i] == skillId) return;
        }
        skills[skillCount++] = skillId;
    }

    public boolean knowsSkill(int skillId) {
        for (int i = 0; i < skillCount; i++) {
            if (skills[i] == skillId) return true;
        }
        return false;
    }

    public void setHotbar(int slot, int skillId) {
        if (slot < 0 || slot >= GameConfig.MAX_HOTBAR) return;
        hotbar[slot] = skillId;
    }

    public int getHotbar(int slot) {
        if (slot < 0 || slot >= GameConfig.MAX_HOTBAR) return -1;
        return hotbar[slot];
    }

    public boolean isSkillOnCooldown(int skillId) {
        if (skillId < 0 || skillId >= GameConfig.MAX_SKILLS) return false;
        return skillCooldowns[skillId] > 0;
    }

    public void triggerSkillCooldown(int skillId, int turns) {
        if (skillId < 0 || skillId >= GameConfig.MAX_SKILLS) return;
        skillCooldowns[skillId] = turns;
    }

    // -------------------------------------------------
    //  CRAFTING (NEW)
    // -------------------------------------------------
    public void addCraftingExp(int amount) {
        craftingExp += amount;
        int needed = 50 + craftingLevel * 50;
        if (craftingExp >= needed && craftingLevel < 10) {
            craftingExp -= needed;
            craftingLevel++;
        }
    }

    // -------------------------------------------------
    //  NPC RELATIONS (NEW)
    // -------------------------------------------------
    public void changeRelation(int npcId, int delta) {
        if (npcId < 0 || npcId >= npcRelation.length) return;
        npcRelation[npcId] = Math.max(-100, Math.min(100, npcRelation[npcId] + delta));
    }

    public int getRelation(int npcId) {
        if (npcId < 0 || npcId >= npcRelation.length) return 0;
        return npcRelation[npcId];
    }

    // -------------------------------------------------
    //  HEAL / DAMAGE
    // -------------------------------------------------
    public void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public void restoreMp(int amount) {
        mp = Math.min(maxMp, mp + amount);
    }

    public void restoreSp(int amount) {
        sp = Math.min(maxSp, sp + amount);
    }

    public void takeDamage(int amount) {
        // NEW: Barrier absorbs 1 hit entirely
        if (hasStatus(STATUS_BARRIER)) {
            removeStatus(STATUS_BARRIER);
            return;
        }
        if (isGuarding) {
            amount = amount * GameConfig.GUARD_REDUCTION / 100;
        }
        totalDmgTaken += amount;
        hp -= amount;
        if (hp <= 0) { hp = 0; isAlive = false; }
        // Limit gauge fills from damage
        addLimitGauge(amount / 5);
    }

    public void fullRestore() {
        hp = maxHp; mp = maxMp; sp = maxSp;
        statusFlags = STATUS_NONE;
        isAlive = true; limitGauge = 0;
        comboCount = 0;
    }

    // -------------------------------------------------
    //  SAVE / LOAD (expanded)
    // -------------------------------------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(GameConfig.SAVE_VERSION);
        dos.writeUTF(name);
        dos.writeInt(classId);
        dos.writeInt(level);
        dos.writeInt(exp);
        dos.writeInt(hp); dos.writeInt(maxHp);
        dos.writeInt(mp); dos.writeInt(maxMp);
        dos.writeInt(sp); dos.writeInt(maxSp);
        dos.writeInt(atk); dos.writeInt(def);
        dos.writeInt(spd); dos.writeInt(luk);
        dos.writeInt(mag); dos.writeInt(res);
        dos.writeInt(gold);
        dos.writeInt(mapId); dos.writeInt(x); dos.writeInt(y);
        dos.writeInt(direction);
        dos.writeInt(limitGauge);
        dos.writeInt(craftingLevel); dos.writeInt(craftingExp);

        for (int i = 0; i < GameConfig.MAX_EQUIPMENT; i++) dos.writeInt(equipment[i]);

        dos.writeInt(skillCount);
        for (int i = 0; i < skillCount; i++) dos.writeInt(skills[i]);

        for (int i = 0; i < GameConfig.MAX_HOTBAR; i++) dos.writeInt(hotbar[i]);

        dos.writeInt(stepCount); dos.writeInt(battleCount);
        dos.writeInt(killCount); dos.writeInt(craftCount);
        dos.writeInt(chestsOpened);
        dos.writeLong(playTimeMs);

        // elem resists
        dos.writeInt(elemResist.length);
        for (int i = 0; i < elemResist.length; i++) dos.writeInt(elemResist[i]);

        // npc relations (just count to avoid huge saves)
        dos.writeInt(Math.min(npcRelation.length, 64));
        for (int i = 0; i < Math.min(npcRelation.length, 64); i++) dos.writeInt(npcRelation[i]);

        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        int version = dis.readInt();
        name = dis.readUTF();
        classId = dis.readInt();
        level = dis.readInt();
        exp = dis.readInt();
        hp = dis.readInt(); maxHp = dis.readInt();
        mp = dis.readInt(); maxMp = dis.readInt();

        if (version >= 3) {
            sp = dis.readInt(); maxSp = dis.readInt();
            atk = dis.readInt(); def = dis.readInt();
            spd = dis.readInt(); luk = dis.readInt();
            mag = dis.readInt(); res = dis.readInt();
            gold = dis.readInt();
        } else {
            sp = maxSp = 50;
            atk = dis.readInt(); def = dis.readInt();
            spd = dis.readInt(); luk = dis.readInt();
            mag = 5; res = 5;
            gold = 0;
        }

        mapId = dis.readInt(); x = dis.readInt(); y = dis.readInt();
        direction = dis.readInt();

        if (version >= 3) {
            limitGauge = dis.readInt();
            craftingLevel = dis.readInt(); craftingExp = dis.readInt();
        }

        for (int i = 0; i < GameConfig.MAX_EQUIPMENT; i++) equipment[i] = dis.readInt();

        skillCount = dis.readInt();
        for (int i = 0; i < skillCount; i++) skills[i] = dis.readInt();

        if (version >= 3) {
            for (int i = 0; i < GameConfig.MAX_HOTBAR; i++) hotbar[i] = dis.readInt();
        }

        stepCount = dis.readInt(); battleCount = dis.readInt();

        if (version >= 3) {
            killCount = dis.readInt(); craftCount = dis.readInt();
            chestsOpened = dis.readInt();
            playTimeMs = dis.readLong();

            int erLen = dis.readInt();
            for (int i = 0; i < erLen && i < elemResist.length; i++) elemResist[i] = dis.readInt();

            int relLen = dis.readInt();
            for (int i = 0; i < relLen && i < npcRelation.length; i++) npcRelation[i] = dis.readInt();
        }

        expNext = calculateExpNext(level);
        isAlive = hp > 0;
    }
}
