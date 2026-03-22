import javax.microedition.lcdui.*;

/**
 * BattleSystem.java - ENHANCED v2.0
 * New: combo multiplier, limit break, AoE skills, multi-target,
 * loot drops, status infliction, enemy summons, multi-phase bosses,
 * ATB v2 (ATB_MAX=1000), animated combat log, escape difficulty curve.
 */
public class BattleSystem {

    // -------------------------------------------------
    //  BATTLE STATE
    // -------------------------------------------------
    public static final int STATE_INIT        = 0;
    public static final int STATE_PLAYER_TURN = 1;
    public static final int STATE_ENEMY_TURN  = 2;
    public static final int STATE_ACTION      = 3;
    public static final int STATE_RESULT      = 4;
    public static final int STATE_VICTORY     = 5;
    public static final int STATE_DEFEAT      = 6;
    public static final int STATE_ESCAPE      = 7;
    public static final int STATE_END         = 8;
    public static final int STATE_LIMIT       = 9;   // NEW: Limit Break cinematic

    // Action types
    public static final int ACT_ATTACK  = 0;
    public static final int ACT_SKILL   = 1;
    public static final int ACT_ITEM    = 2;
    public static final int ACT_GUARD   = 3;
    public static final int ACT_ESCAPE  = 4;
    public static final int ACT_LIMIT   = 5;   // NEW

    // -------------------------------------------------
    //  BATTLE DATA
    // -------------------------------------------------
    private PlayerData player;
    private InventorySystem inventory;
    private EnemyData[] enemies;
    private int enemyCount;
    private int state;
    private int turnCount;

    private int currentAction;
    private int currentSkill;
    private int currentTarget;    // -1 = AoE
    private int currentItem;

    private boolean useATB;
    private int atbSpeed;

    // Results
    private int totalExp, totalGold;
    private boolean playerWon;
    private int[] lootItems;      // NEW
    private int lootCount;

    // Animation
    private int animTimer;
    private int animPhase;
    private String actionText;
    private String subText;       // NEW: secondary line
    private int damageDisplay;
    private int damageX, damageY;
    private boolean isCrit;       // NEW
    private int screenFlash;      // NEW: flash frames

    // Menu
    private int menuSelection;
    private int skillSelection;
    private int itemSelection;
    private int targetSelection;
    private boolean inSkillMenu;
    private boolean inItemMenu;
    private boolean inTargetMenu;
    private boolean inLimitMenu;  // NEW

    // Combat log (NEW)
    private static final int LOG_SIZE = 6;
    private String[] combatLog;
    private int logHead;

    // Random seed
    private int randomSeed;

    // Screen shake (NEW)
    private int shakeTimer;
    private int shakeX, shakeY;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public BattleSystem() {
        enemies = new EnemyData[GameConfig.MAX_ENEMIES];
        for (int i = 0; i < GameConfig.MAX_ENEMIES; i++) enemies[i] = new EnemyData();
        combatLog = new String[LOG_SIZE];
        for (int i = 0; i < LOG_SIZE; i++) combatLog[i] = "";
        lootItems = new int[16];
        reset();
    }

    public void reset() {
        state = STATE_INIT; enemyCount = 0; turnCount = 0;
        menuSelection = 0; skillSelection = 0; itemSelection = 0;
        targetSelection = 0; inSkillMenu = false; inItemMenu = false;
        inTargetMenu = false; inLimitMenu = false;
        useATB = false; atbSpeed = 1;
        totalExp = 0; totalGold = 0; lootCount = 0;
        playerWon = false; animTimer = 0; animPhase = 0;
        actionText = ""; subText = "";
        damageDisplay = 0; isCrit = false; screenFlash = 0;
        shakeTimer = 0; shakeX = 0; shakeY = 0;
        logHead = 0;
        randomSeed = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
    }

    // -------------------------------------------------
    //  BATTLE SETUP
    // -------------------------------------------------
    public void startBattle(PlayerData p, InventorySystem inv,
                            int[] enemyTypes, int levelMod) {
        reset();
        this.player = p;
        this.inventory = inv;

        enemyCount = Math.min(enemyTypes.length, GameConfig.MAX_ENEMIES);
        for (int i = 0; i < enemyCount; i++) {
            enemies[i].init(enemyTypes[i], levelMod);
            // Spread formation
            enemies[i].battleX = 120 + (i % 3) * 50;
            enemies[i].battleY = 30  + (i / 3) * 45;
        }

        player.atbGauge = 0;
        player.isGuarding = false;
        player.turnCount = 0;
        player.battleCount++;

        addLog("Battle start!");
        state = STATE_PLAYER_TURN;
    }

    public void startRandomEncounter(PlayerData p, InventorySystem inv,
                                     int regionLevel) {
        int numEnemies = 1 + nextRandom() % 3;
        int[] types = new int[numEnemies];
        for (int i = 0; i < numEnemies; i++) {
            int maxType = Math.min(regionLevel / 5 + 3, EnemyData.MAX_ENEMY_TYPES);
            types[i] = nextRandom() % maxType;
        }
        startBattle(p, inv, types, regionLevel / 10);
    }

    private int nextRandom() {
        randomSeed = (randomSeed * 1103515245 + 12345) & 0x7FFFFFFF;
        return randomSeed;
    }

    // -------------------------------------------------
    //  BATTLE UPDATE
    // -------------------------------------------------
    public void update() {
        animTimer++;

        // Screen shake decay
        if (shakeTimer > 0) {
            shakeTimer--;
            shakeX = (shakeTimer % 2 == 0) ? 3 : -3;
            shakeY = (shakeTimer % 3 == 0) ? 2 : -1;
        } else {
            shakeX = shakeY = 0;
        }
        if (screenFlash > 0) screenFlash--;

        // Tick enemy regen each 3rd frame
        if (animTimer % 3 == 0) {
            for (int i = 0; i < enemyCount; i++) {
                if (enemies[i].isAlive) enemies[i].tickRegen();
            }
        }

        switch (state) {
            case STATE_PLAYER_TURN:
                if (useATB) updateATB();
                break;

            case STATE_ENEMY_TURN:
                if (animTimer > 25) executeEnemyTurn();
                break;

            case STATE_ACTION:
                if (animTimer > 50) {
                    animPhase++;
                    if (animPhase > 2) checkBattleEnd();
                }
                break;

            case STATE_RESULT:
                if (animTimer > 75) nextTurn();
                break;
        }
    }

    private void updateATB() {
        if (!player.isGuarding) {
            player.atbGauge += player.getTotalSpd() * atbSpeed * 10;
            if (player.atbGauge > GameConfig.ATB_MAX) player.atbGauge = GameConfig.ATB_MAX;
        }
        for (int i = 0; i < enemyCount; i++) {
            if (enemies[i].isAlive) {
                enemies[i].atbGauge += enemies[i].spd * atbSpeed * 10;
                if (enemies[i].atbGauge >= GameConfig.ATB_MAX) {
                    enemies[i].atbGauge = 0;
                    currentTarget = i;
                    state = STATE_ENEMY_TURN;
                    animTimer = 0;
                    return;
                }
            }
        }
    }

    // -------------------------------------------------
    //  PLAYER ACTIONS
    // -------------------------------------------------
    public void selectAction(int action) {
        currentAction = action;
        switch (action) {
            case ACT_ATTACK:
                inTargetMenu = true;
                targetSelection = findFirstAliveEnemy();
                break;
            case ACT_SKILL:
                inSkillMenu = true; skillSelection = 0; break;
            case ACT_ITEM:
                inItemMenu = true; itemSelection = 0; break;
            case ACT_GUARD:
                player.isGuarding = true;
                actionText = player.name + " braces for impact!";
                addLog(actionText);
                state = STATE_RESULT; animTimer = 0;
                break;
            case ACT_ESCAPE:
                attemptEscape(); break;
            case ACT_LIMIT:
                if (player.canLimitBreak()) {
                    executeLimitBreak();
                }
                break;
        }
    }

    public void selectSkill(int skillId) {
        if (!SkillSystem.canUse(skillId, player)) return;
        if (player.isSkillOnCooldown(skillId)) return;
        currentSkill = skillId;
        inSkillMenu = false;
        int type = SkillSystem.getType(skillId);
        // AoE skills auto-target all enemies
        if (SkillSystem.isAoe(skillId)) {
            currentTarget = -1;
            executePlayerAction();
        } else if (type == SkillSystem.STYPE_HEAL || type == SkillSystem.STYPE_BUFF) {
            currentTarget = -1;
            executePlayerAction();
        } else {
            inTargetMenu = true;
            targetSelection = findFirstAliveEnemy();
        }
    }

    public void selectItem(int itemId) {
        if (!inventory.hasItem(itemId)) return;
        currentItem = itemId;
        inItemMenu = false;
        executePlayerAction();
    }

    public void selectTarget(int target) {
        currentTarget = target;
        inTargetMenu = false;
        executePlayerAction();
    }

    private void executePlayerAction() {
        animTimer = 0; animPhase = 0;
        state = STATE_ACTION;
        player.isGuarding = false;

        switch (currentAction) {
            case ACT_ATTACK:
                executeAttack();
                break;
            case ACT_SKILL:
                executeSkill();
                break;
            case ACT_ITEM:
                inventory.useItem(currentItem, player);
                actionText = "Used " + InventorySystem.getItemName(currentItem) + "!";
                addLog(actionText);
                break;
        }
        player.atbGauge = 0;
        player.turnCount++;
    }

    private void executeAttack() {
        if (currentTarget < 0 || currentTarget >= enemyCount) return;
        EnemyData target = enemies[currentTarget];
        if (!target.isAlive) return;

        int rand = nextRandom();
        boolean crit = (rand % 100) < (player.luk / 8 + 5);
        int damage = SkillSystem.calculateDamage(SkillSystem.SKILL_ATTACK,
                                                  player, target, rand);
        if (crit) { damage = damage * GameConfig.CRIT_MULTIPLIER; }
        // Combo bonus
        player.registerHit();
        damage = damage * (100 + player.getComboBonus()) / 100;

        target.takeDamage(damage, EnemyData.ELEM_NONE);
        player.totalDmgDealt += damage;

        isCrit = crit;
        actionText = player.name + (crit ? " CRITICAL HIT!" : " attacks!");
        subText = "-" + damage + " HP";
        damageDisplay = damage;
        damageX = target.battleX; damageY = target.battleY;
        addLog(player.name + " -> " + target.name + ": " + damage + (crit ? "!!" : ""));
        triggerShake(crit ? 8 : 4);
    }

    private void executeSkill() {
        int mpCost = SkillSystem.getMpCost(currentSkill);
        player.mp -= mpCost;
        player.triggerSkillCooldown(currentSkill,
                                     SkillSystem.getCooldown(currentSkill));

        int type = SkillSystem.getType(currentSkill);
        actionText = player.name + " uses " + SkillSystem.getName(currentSkill) + "!";
        addLog(actionText);

        if (type == SkillSystem.STYPE_HEAL) {
            int heal = SkillSystem.calculateHeal(currentSkill, player);
            player.heal(heal);
            subText = "+" + heal + " HP";
            damageDisplay = -heal;

        } else if (type == SkillSystem.STYPE_BUFF) {
            int power = SkillSystem.getPower(currentSkill);
            if (currentSkill == SkillSystem.SKILL_BUFF_ATK)
                player.applyBuff(power, 0, 0, 5);
            else if (currentSkill == SkillSystem.SKILL_BUFF_DEF)
                player.applyBuff(0, power, 0, 5);
            subText = "Buff applied!";

        } else if (SkillSystem.isAoe(currentSkill)) {
            // Hit all enemies
            int totalDmg = 0;
            for (int i = 0; i < enemyCount; i++) {
                if (enemies[i].isAlive) {
                    int d = SkillSystem.calculateDamage(currentSkill, player,
                                                        enemies[i], nextRandom());
                    player.registerHit();
                    d = d * (100 + player.getComboBonus()) / 100;
                    enemies[i].takeDamage(d, SkillSystem.getElement(currentSkill));
                    player.totalDmgDealt += d;
                    totalDmg += d;
                }
            }
            subText = "ALL enemies -" + totalDmg + " HP";
            triggerShake(6);
            screenFlash = GameConfig.SCREEN_FLASH_DUR;

        } else if (currentTarget >= 0 && currentTarget < enemyCount) {
            EnemyData target = enemies[currentTarget];
            if (target.isAlive) {
                int rand = nextRandom();
                boolean crit = rand % 100 < player.luk / 8 + 3;
                int d = SkillSystem.calculateDamage(currentSkill, player, target, rand);
                if (crit) d = d * GameConfig.CRIT_MULTIPLIER;
                player.registerHit();
                d = d * (100 + player.getComboBonus()) / 100;
                target.takeDamage(d, SkillSystem.getElement(currentSkill));
                player.totalDmgDealt += d;
                isCrit = crit;
                subText = "-" + d + " HP" + (crit ? " CRIT!" : "");
                damageDisplay = d;
                damageX = target.battleX; damageY = target.battleY;
                triggerShake(crit ? 9 : 5);
            }
            // Status infliction
            int statusEffect = SkillSystem.getStatusEffect(currentSkill);
            if (statusEffect > 0 && currentTarget >= 0) {
                enemies[currentTarget].statusFlags |= statusEffect;
                addLog("Status inflicted!");
            }
        }
    }

    private void executeLimitBreak() {
        player.useLimitBreak();
        state = STATE_ACTION; animTimer = 0; animPhase = 0;

        int totalDmg = 0;
        for (int i = 0; i < enemyCount; i++) {
            if (enemies[i].isAlive) {
                int d = player.getTotalAtk() * 3 +
                        (player.mag > 0 ? player.getTotalMag() * 2 : 0);
                d += nextRandom() % (d / 4 + 1);
                enemies[i].takeDamage(d, EnemyData.ELEM_NONE);
                player.totalDmgDealt += d;
                totalDmg += d;
            }
        }
        actionText = "*** LIMIT BREAK ***";
        subText = "Total: " + totalDmg + " damage!";
        addLog(actionText + " " + subText);
        triggerShake(12); screenFlash = 15;
        player.atbGauge = 0;
    }

    // -------------------------------------------------
    //  ENEMY TURN
    // -------------------------------------------------
    private void executeEnemyTurn() {
        EnemyData enemy = null;
        int idx = -1;
        // Find enemy with most ATB or oldest pending
        for (int i = 0; i < enemyCount; i++) {
            if (enemies[i].isAlive &&
                (enemy == null || enemies[i].atbGauge > enemy.atbGauge)) {
                enemy = enemies[i]; idx = i;
            }
        }
        if (enemy == null) { checkBattleEnd(); return; }
        enemies[idx].atbGauge = 0;

        int action = enemy.selectAction(player, turnCount);
        animPhase = 0; state = STATE_ACTION; animTimer = 0;

        switch (action) {
            case 0: // Normal attack
                int damage = Math.max(1, enemy.atk - player.getTotalDef() / 2);
                damage = damage * (90 + nextRandom() % 20) / 100;
                player.takeDamage(damage);
                actionText = enemy.name + " attacks!";
                subText = "You take " + damage + " damage!";
                addLog(enemy.name + " hits player: -" + damage);
                triggerShake(4);
                break;
            case 1: // Skill / Magic
                int mdmg = Math.max(1, enemy.atk + 5 - player.getTotalRes() / 2);
                mdmg = mdmg * (85 + nextRandom() % 30) / 100;
                player.takeDamage(mdmg);
                actionText = enemy.name + " casts a spell!";
                subText = "You take " + mdmg + " magic damage!";
                addLog(enemy.name + " magic: -" + mdmg);
                triggerShake(5); screenFlash = 4;
                break;
            case 2: // Defend
                actionText = enemy.name + " takes a defensive stance!";
                subText = "";
                addLog(enemy.name + " defends.");
                break;
            case 3: // Special
                int sdmg = Math.max(1, enemy.atk * 2 - player.getTotalDef());
                sdmg = sdmg * (90 + nextRandom() % 20) / 100;
                player.takeDamage(sdmg);
                actionText = enemy.name + " uses a SPECIAL ATTACK!";
                subText = "You take " + sdmg + " damage!";
                addLog(enemy.name + " special: -" + sdmg);
                triggerShake(10); screenFlash = 8;
                break;
            case 4: // Summon
                actionText = enemy.name + " summons a minion!";
                subText = "A new enemy appears!";
                addLog(enemy.name + " summons!");
                // Add a minion (same type but weaker)
                if (enemyCount < GameConfig.MAX_ENEMIES) {
                    int minionType = Math.max(0, enemy.type - 1);
                    enemies[enemyCount].init(minionType, 0);
                    enemies[enemyCount].battleX = enemy.battleX + 40;
                    enemies[enemyCount].battleY = enemy.battleY + 20;
                    enemyCount++;
                }
                break;
            case 5: // Phase
                actionText = enemy.name + " phases out of reality!";
                subText = "(Attacks will miss for 2 turns)";
                addLog(enemy.name + " phases!");
                break;
        }
    }

    // -------------------------------------------------
    //  BATTLE FLOW
    // -------------------------------------------------
    private void attemptEscape() {
        int chance = 40 + (player.getTotalSpd() - getAverageEnemySpeed()) * 3;
        chance -= player.escapeCount * 5;  // gets harder
        if (nextRandom() % 100 < Math.max(10, chance)) {
            state = STATE_ESCAPE;
            player.escapeCount++;
            player.resetCombo();
            actionText = "Got away safely!";
            addLog("Escaped!");
        } else {
            actionText = "Can't escape!";
            addLog("Escape failed.");
            state = STATE_ENEMY_TURN; animTimer = 0;
        }
    }

    private void nextTurn() {
        turnCount++;
        player.updateStatus();
        player.updateBuffs();
        player.updateDash();

        if (!player.isAlive) { state = STATE_DEFEAT; return; }
        if (countAliveEnemies() == 0) { calculateRewards(); state = STATE_VICTORY; return; }

        if (useATB) {
            state = STATE_PLAYER_TURN;
        } else {
            if (turnCount % 2 == 0) { state = STATE_ENEMY_TURN; animTimer = 0; }
            else state = STATE_PLAYER_TURN;
        }
    }

    private void checkBattleEnd() {
        if (!player.isAlive) state = STATE_DEFEAT;
        else if (countAliveEnemies() == 0) { calculateRewards(); state = STATE_VICTORY; }
        else { state = STATE_RESULT; animTimer = 0; }
    }

    private void calculateRewards() {
        totalExp = 0; totalGold = 0; lootCount = 0;
        for (int i = 0; i < enemyCount; i++) {
            if (!enemies[i].isAlive) {
                totalExp  += enemies[i].expReward;
                totalGold += enemies[i].goldReward;
                // Loot drop
                int drop = enemies[i].rollLoot(nextRandom());
                if (drop >= 0 && lootCount < lootItems.length) {
                    lootItems[lootCount++] = drop;
                    inventory.addItem(drop, 1);
                }
                player.killCount++;
            }
        }
        // Combo exp bonus
        int comboBonus = (player.comboCount * GameConfig.CHAIN_EXP_BONUS) / 100;
        totalExp = totalExp * (100 + comboBonus) / 100;

        player.addExp(totalExp);
        inventory.addGold(totalGold);
        player.resetCombo();
        playerWon = true;

        addLog("Victory! +" + totalExp + "XP +" + totalGold + "G");
        SkillSystem.checkLearnSkills(player);
    }

    // -------------------------------------------------
    //  HELPERS
    // -------------------------------------------------
    private int countAliveEnemies() {
        int n = 0;
        for (int i = 0; i < enemyCount; i++) if (enemies[i].isAlive) n++;
        return n;
    }

    private int findFirstAliveEnemy() {
        for (int i = 0; i < enemyCount; i++) if (enemies[i].isAlive) return i;
        return 0;
    }

    private int getAverageEnemySpeed() {
        int total = 0, count = 0;
        for (int i = 0; i < enemyCount; i++) {
            if (enemies[i].isAlive) { total += enemies[i].spd; count++; }
        }
        return count > 0 ? total / count : 5;
    }

    private void triggerShake(int intensity) {
        shakeTimer = intensity;
    }

    private void addLog(String msg) {
        combatLog[logHead % LOG_SIZE] = msg;
        logHead++;
    }

    // -------------------------------------------------
    //  INPUT HANDLING
    // -------------------------------------------------
    public void handleKey(int keyCode) {
        if (state == STATE_VICTORY || state == STATE_DEFEAT || state == STATE_ESCAPE) return;
        if (state != STATE_PLAYER_TURN) return;

        int action = -1;
        if (inTargetMenu) {
            if (keyCode == KEY_LEFT)  { targetSelection = prevAliveEnemy(targetSelection); }
            if (keyCode == KEY_RIGHT) { targetSelection = nextAliveEnemy(targetSelection); }
            if (keyCode == KEY_FIRE)  { selectTarget(targetSelection); }
            if (keyCode == KEY_BACK)  { inTargetMenu = false; }
            return;
        }
        if (inSkillMenu) {
            if (keyCode == KEY_UP)   skillSelection = Math.max(0, skillSelection - 1);
            if (keyCode == KEY_DOWN) skillSelection = Math.min(player.skillCount - 1, skillSelection + 1);
            if (keyCode == KEY_FIRE) {
                if (skillSelection < player.skillCount)
                    selectSkill(player.skills[skillSelection]);
            }
            if (keyCode == KEY_BACK) inSkillMenu = false;
            return;
        }
        if (inItemMenu) {
            if (keyCode == KEY_UP)   itemSelection = Math.max(0, itemSelection - 1);
            if (keyCode == KEY_DOWN) itemSelection = Math.min(inventory.getSlotCount() - 1, itemSelection + 1);
            if (keyCode == KEY_FIRE) {
                if (itemSelection < inventory.getSlotCount())
                    selectItem(inventory.getItemAt(itemSelection));
            }
            if (keyCode == KEY_BACK) inItemMenu = false;
            return;
        }

        // Main menu
        if (keyCode == KEY_UP)   menuSelection = Math.max(0, menuSelection - 1);
        if (keyCode == KEY_DOWN) menuSelection = Math.min(player.canLimitBreak() ? 5 : 4, menuSelection + 1);
        if (keyCode == KEY_FIRE) selectAction(menuSelection);
    }

    private static final int KEY_UP   = 1;
    private static final int KEY_DOWN = 2;
    private static final int KEY_LEFT = 3;
    private static final int KEY_RIGHT= 4;
    private static final int KEY_FIRE = 5;
    private static final int KEY_BACK = 6;

    private int nextAliveEnemy(int cur) {
        for (int i = 1; i <= enemyCount; i++) {
            int idx = (cur + i) % enemyCount;
            if (enemies[idx].isAlive) return idx;
        }
        return cur;
    }
    private int prevAliveEnemy(int cur) {
        for (int i = 1; i <= enemyCount; i++) {
            int idx = (cur - i + enemyCount) % enemyCount;
            if (enemies[idx].isAlive) return idx;
        }
        return cur;
    }

    // -------------------------------------------------
    //  RENDERING
    // -------------------------------------------------
    public void draw(Graphics g, int screenW, int screenH) {
        int ox = shakeX, oy = shakeY;

        // Background gradient
        drawBattleBackground(g, screenW, screenH, ox, oy);

        // Screen flash overlay
        if (screenFlash > 0) {
            int alpha = screenFlash * 15;
            g.setColor(0xFFFFFF);
            // Simple flash via bright band at top
            g.fillRect(0, 0, screenW, 5);
        }

        // Draw enemies
        for (int i = 0; i < enemyCount; i++) {
            drawEnemy(g, i, ox, oy);
        }

        // Draw player
        drawPlayerBattle(g, screenW, screenH, ox, oy);

        // ATB bars
        drawATBBars(g, screenW, screenH);

        // Damage number
        if (damageDisplay != 0 && animPhase < 3) {
            drawDamageNumber(g, damageDisplay, damageX + ox,
                             damageY + oy - animPhase * 8, isCrit);
        }

        // Menu
        drawBattleMenu(g, screenW, screenH);

        // Action text
        drawActionText(g, screenW, screenH);

        // Combat log
        drawCombatLog(g, screenW, screenH);

        // Victory / Defeat
        if (state == STATE_VICTORY) drawVictory(g, screenW, screenH);
        if (state == STATE_DEFEAT)  drawDefeat(g, screenW, screenH);
    }

    private void drawBattleBackground(Graphics g, int w, int h, int ox, int oy) {
        // Dark gradient from bottom
        for (int y = 0; y < h; y++) {
            int shade = 0x10 + (y * 0x1A / h);
            g.setColor(shade, shade * 2 / 3, shade);
            g.drawLine(ox, oy + y, ox + w, oy + y);
        }
        // Ground line
        g.setColor(0x334433);
        g.fillRect(ox, oy + h / 2, w, 4);
    }

    private void drawEnemy(Graphics g, int i, int ox, int oy) {
        EnemyData e = enemies[i];
        int ex = e.battleX + ox;
        int ey = e.battleY + oy;
        int ew = 36, eh = 40;

        if (!e.isAlive) {
            // Dead: faded gray
            g.setColor(0x444444);
            g.fillRect(ex, ey, ew, eh);
            g.setColor(0x222222);
            g.drawRect(ex, ey, ew - 1, eh - 1);
            return;
        }

        // Flash on hit
        int col = (e.flashTimer > 0 && e.flashTimer % 2 == 0) ? 0xFFFFFF : e.color;
        g.setColor(col);
        g.fillRect(ex, ey, ew, eh);

        // Phase effect
        if (e.phaseActive) {
            g.setColor(0x8888FF);
            g.drawRect(ex - 2, ey - 2, ew + 4, eh + 4);
        }

        // Eyes
        g.setColor(0xFF0000);
        g.fillRect(ex + 8, ey + 10, 5, 5);
        g.fillRect(ex + 22, ey + 10, 5, 5);

        // Enraged indicator
        if (e.isEnraged) {
            g.setColor(0xFF4400);
            g.drawString("!", ex + ew / 2, ey - 8, Graphics.TOP | Graphics.HCENTER);
        }

        // Boss phase indicator
        if (e.aiType == EnemyData.AI_BOSS && e.currentPhase > 0) {
            g.setColor(e.currentPhase == 1 ? 0xFFAA00 : 0xFF0000);
            g.drawRect(ex - 1, ey - 1, ew + 2, eh + 2);
        }

        // HP bar
        int barW = ew;
        int barH = 4;
        int barY = ey + eh + 2;
        g.setColor(0x440000);
        g.fillRect(ex, barY, barW, barH);
        int hpW = (e.hp * barW) / Math.max(1, e.maxHp);
        int hpColor = e.hp > e.maxHp * 2 / 3 ? 0x00CC00
                    : e.hp > e.maxHp / 3     ? 0xFFAA00 : 0xFF2200;
        g.setColor(hpColor);
        g.fillRect(ex, barY, hpW, barH);

        // Name + HP
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        g.setColor(0xFFFFFF);
        g.drawString(e.name, ex + ew / 2, barY + barH + 1,
                     Graphics.TOP | Graphics.HCENTER);
    }

    private void drawPlayerBattle(Graphics g, int w, int h, int ox, int oy) {
        int px = 30 + ox, py = h / 2 - 30 + oy;
        int pw = 30, ph = 40;

        // Player sprite
        int playerColor = player.isAlive ? 0x44AA44 : 0x444444;
        if (player.isGuarding) playerColor = 0x4488FF;
        g.setColor(playerColor);
        g.fillRect(px, py, pw, ph);

        // Eyes
        g.setColor(0xFFFFFF);
        g.fillRect(px + 6, py + 8, 5, 5);
        g.fillRect(px + 18, py + 8, 5, 5);

        // Held weapon indicator
        g.setColor(0xDDCC88);
        g.fillRect(px + pw, py + 10, 8, 3);

        // HP bar
        g.setColor(0x440000);
        g.fillRect(px, h - 40 + oy, 80, 8);
        int phw = (player.hp * 80) / Math.max(1, player.maxHp);
        g.setColor(player.hp > player.maxHp / 3 ? 0x00CC00 : 0xFF2200);
        g.fillRect(px, h - 40 + oy, phw, 8);
        g.setColor(0xFFFFFF);
        g.drawRect(px, h - 40 + oy, 79, 7);

        // MP bar
        g.setColor(0x000044);
        g.fillRect(px, h - 30 + oy, 80, 6);
        int pmw = (player.mp * 80) / Math.max(1, player.maxMp);
        g.setColor(0x4466FF);
        g.fillRect(px, h - 30 + oy, pmw, 6);

        // Limit gauge (NEW)
        if (player.limitGauge > 0) {
            g.setColor(0x332200);
            g.fillRect(px, h - 22 + oy, 80, 5);
            int lw = (player.limitGauge * 80) / GameConfig.LIMIT_GAUGE_MAX;
            g.setColor(player.canLimitBreak() ? 0xFFDD00 : 0xFF8800);
            g.fillRect(px, h - 22 + oy, lw, 5);
        }

        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        g.setColor(0xEEEEEE);
        g.drawString(player.name + " Lv" + player.level,
                     px, h - 15 + oy, Graphics.TOP | Graphics.LEFT);
        g.drawString("HP:" + player.hp + "/" + player.maxHp,
                     px, h - 8 + oy, Graphics.TOP | Graphics.LEFT);
    }

    private void drawATBBars(Graphics g, int w, int h) {
        if (!useATB) return;
        int bx = w - 70;
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);

        // Player ATB
        g.setColor(0x002200);
        g.fillRect(bx, h - 60, 60, 6);
        int patb = (player.atbGauge * 60) / GameConfig.ATB_MAX;
        g.setColor(0x00FF44);
        g.fillRect(bx, h - 60, patb, 6);
        g.setColor(0xAAAAAA);
        g.drawString("ATB", bx - 22, h - 61, Graphics.TOP | Graphics.LEFT);

        // Enemy ATBs
        for (int i = 0; i < enemyCount && i < 4; i++) {
            if (!enemies[i].isAlive) continue;
            int by = h - 50 + i * 10;
            g.setColor(0x220000);
            g.fillRect(bx, by, 60, 5);
            int eatb = (enemies[i].atbGauge * 60) / GameConfig.ATB_MAX;
            g.setColor(0xFF4400);
            g.fillRect(bx, by, eatb, 5);
        }
    }

    private void drawDamageNumber(Graphics g, int dmg, int x, int y, boolean crit) {
        Font f = Font.getFont(Font.FACE_SYSTEM,
                              crit ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                              Font.SIZE_LARGE);
        g.setFont(f);
        String s = dmg < 0 ? "+" + (-dmg) : "-" + dmg;
        g.setColor(dmg < 0 ? 0x00FF88 : (crit ? 0xFFDD00 : 0xFF4444));
        g.drawString(s, x + 18, y + 20, Graphics.TOP | Graphics.HCENTER);
    }

    private void drawBattleMenu(Graphics g, int w, int h) {
        if (state != STATE_PLAYER_TURN) return;
        if (inSkillMenu) { drawSkillMenu(g, w, h); return; }
        if (inItemMenu)  { drawItemMenu(g, w, h);  return; }
        if (inTargetMenu){ drawTargetHighlight(g);  return; }

        String[] items = player.canLimitBreak()
            ? new String[]{"Attack","Skill","Item","Guard","Escape","LIMIT!"}
            : new String[]{"Attack","Skill","Item","Guard","Escape"};
        int count = items.length;

        int mw = 90, mh = count * 14 + 10;
        int mx = w - mw - 4, my = h - mh - 4;
        g.setColor(0x001122);
        g.fillRect(mx, my, mw, mh);
        g.setColor(0x2244AA);
        g.drawRect(mx, my, mw - 1, mh - 1);

        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        for (int i = 0; i < count; i++) {
            boolean sel = i == menuSelection;
            if (sel) { g.setColor(0x2255AA); g.fillRect(mx + 2, my + 5 + i * 14, mw - 4, 13); }
            g.setColor(i == count - 1 && player.canLimitBreak() ? 0xFFDD00
                       : sel ? 0xFFFFFF : 0xAABBCC);
            g.drawString((sel ? ">" : " ") + items[i], mx + 6, my + 5 + i * 14,
                         Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawSkillMenu(Graphics g, int w, int h) {
        int count = player.skillCount;
        if (count == 0) { inSkillMenu = false; return; }
        int mw = 120, mh = Math.min(count, 6) * 14 + 20;
        int mx = w / 2 - mw / 2, my = h / 2 - mh / 2;
        g.setColor(0x000033);
        g.fillRect(mx, my, mw, mh);
        g.setColor(0x4455BB);
        g.drawRect(mx, my, mw - 1, mh - 1);

        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        g.setColor(0xFFFF88);
        g.drawString("-- SKILLS --", mx + mw / 2, my + 3, Graphics.TOP | Graphics.HCENTER);

        int start = Math.max(0, skillSelection - 4);
        for (int i = start; i < count && i < start + 6; i++) {
            int sid = player.skills[i];
            boolean sel = i == skillSelection;
            boolean onCD = player.isSkillOnCooldown(sid);
            boolean canUse = SkillSystem.canUse(sid, player) && !onCD;

            if (sel) { g.setColor(0x223377); g.fillRect(mx + 2, my + 14 + (i - start) * 14, mw - 4, 13); }
            g.setColor(!canUse ? 0x666666 : sel ? 0xFFFFFF : 0xAABBCC);
            String cd = onCD ? "[" + player.skillCooldowns[sid] + "]" : "";
            g.drawString(SkillSystem.getName(sid) + " " +
                         SkillSystem.getMpCost(sid) + "MP " + cd,
                         mx + 6, my + 14 + (i - start) * 14,
                         Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawItemMenu(Graphics g, int w, int h) {
        int count = inventory.getSlotCount();
        if (count == 0) { inItemMenu = false; return; }
        int mw = 110, mh = Math.min(count, 6) * 14 + 20;
        int mx = w / 2 - mw / 2, my = h / 2 - mh / 2;
        g.setColor(0x002200);
        g.fillRect(mx, my, mw, mh);
        g.setColor(0x44AA44);
        g.drawRect(mx, my, mw - 1, mh - 1);

        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        g.setColor(0x88FF88);
        g.drawString("-- ITEMS --", mx + mw / 2, my + 3, Graphics.TOP | Graphics.HCENTER);

        int start = Math.max(0, itemSelection - 4);
        for (int i = start; i < count && i < start + 6; i++) {
            int iid = inventory.getItemAt(i);
            boolean sel = i == itemSelection;
            if (sel) { g.setColor(0x226622); g.fillRect(mx + 2, my + 14 + (i - start) * 14, mw - 4, 13); }
            g.setColor(sel ? 0xFFFFFF : 0xAADDAA);
            g.drawString(InventorySystem.getItemName(iid) + " x" + inventory.getCountAt(i),
                         mx + 6, my + 14 + (i - start) * 14,
                         Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawTargetHighlight(Graphics g) {
        if (targetSelection < 0 || targetSelection >= enemyCount) return;
        EnemyData t = enemies[targetSelection];
        int pulse = (int)(System.currentTimeMillis() / 200) % 2;
        g.setColor(pulse == 0 ? 0xFFFF00 : 0xFF8800);
        g.drawRect(t.battleX - 2, t.battleY - 2, 40, 44);
        g.drawRect(t.battleX - 3, t.battleY - 3, 42, 46);
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        g.setColor(0xFFFF44);
        g.drawString("Target: " + t.name, t.battleX, t.battleY - 12,
                     Graphics.TOP | Graphics.LEFT);
    }

    private void drawActionText(Graphics g, int w, int h) {
        if (actionText.length() == 0) return;
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setFont(f);
        int tw = f.stringWidth(actionText) + 10;
        int tx = (w - tw) / 2, ty = h / 2 - 20;
        g.setColor(0x000022);
        g.fillRect(tx, ty, tw, f.getHeight() + 4);
        g.setColor(isCrit ? 0xFFDD00 : 0xFFFFFF);
        g.drawString(actionText, w / 2, ty + 2, Graphics.TOP | Graphics.HCENTER);

        if (subText.length() > 0) {
            Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(sf);
            g.setColor(0xAADDFF);
            g.drawString(subText, w / 2, ty + f.getHeight() + 4, Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void drawCombatLog(Graphics g, int w, int h) {
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        int logX = 4, logY = 4;
        for (int i = 0; i < LOG_SIZE; i++) {
            int idx = (logHead - LOG_SIZE + i + LOG_SIZE * 2) % LOG_SIZE;
            String entry = combatLog[idx];
            if (entry == null || entry.length() == 0) continue;
            int alpha = i * 50 + 50;  // older = more faded
            g.setColor(alpha, alpha, (int)(alpha * 1.3f));
            g.drawString(entry, logX, logY + i * (f.getHeight() + 1),
                         Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawVictory(Graphics g, int w, int h) {
        g.setColor(0, 0, 0);
        for (int y = 0; y < h; y += 2) g.drawLine(0, y, w, y);
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        g.setFont(f);
        g.setColor(0xFFDD00);
        g.drawString("VICTORY!", w / 2, h / 3, Graphics.TOP | Graphics.HCENTER);
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        g.setFont(sf);
        g.setColor(0xAAFFAA);
        g.drawString("EXP +" + totalExp, w / 2, h / 3 + 30, Graphics.TOP | Graphics.HCENTER);
        g.drawString("Gold +" + totalGold, w / 2, h / 3 + 45, Graphics.TOP | Graphics.HCENTER);
        if (lootCount > 0) {
            g.setColor(0xFFDD88);
            for (int i = 0; i < lootCount; i++) {
                g.drawString("Got: " + InventorySystem.getItemName(lootItems[i]),
                             w / 2, h / 3 + 60 + i * 14, Graphics.TOP | Graphics.HCENTER);
            }
        }
    }

    private void drawDefeat(Graphics g, int w, int h) {
        g.setColor(0, 0, 0);
        for (int y = 0; y < h; y += 2) g.drawLine(0, y, w, y);
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        g.setFont(f);
        g.setColor(0xFF2222);
        g.drawString("GAME OVER", w / 2, h / 3, Graphics.TOP | Graphics.HCENTER);
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(sf);
        g.setColor(0xAAAAAA);
        g.drawString("Press any key to continue", w / 2, h / 3 + 30, Graphics.TOP | Graphics.HCENTER);
    }

    // -------------------------------------------------
    //  GETTERS
    // -------------------------------------------------
    public int getState()     { return state; }
    public boolean isOver()   { return state == STATE_VICTORY || state == STATE_DEFEAT || state == STATE_ESCAPE; }
    public boolean playerWon(){ return playerWon; }
    public int getTotalExp()  { return totalExp; }
    public int getTotalGold() { return totalGold; }
    public void setATB(boolean on) { useATB = on; }
}
