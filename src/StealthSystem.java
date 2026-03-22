/**
 * StealthSystem.java — NEW in v2.0
 *
 * Governs the player's stealth level and its interactions with enemies.
 *
 * Stealth Level (0-100):
 *  0   = Fully detected — all enemies see you immediately
 *  100 = Fully hidden  — no enemy can detect you
 *
 * Stealth drains when:
 *  - Player moves in line-of-sight of an enemy (drain by distance/tile type)
 *  - Player attacks (full drain, but free surprise strike if > KILL threshold)
 *  - Player runs
 *
 * Stealth regenerates when:
 *  - Player stands still in shadow/cover tiles
 *  - Stealth skill is activated
 *
 * Detection:
 *  Each enemy checks: random(0-100) < detectChance
 *  detectChance = DETECT_BASE - stealth + enemy awareness
 */
public class StealthSystem {

    // =========================================================
    //  STEALTH TILE MODIFIERS
    //  How much stealth is GAINED per tile type (negative = loses stealth)
    // =========================================================
    private static final int[] TILE_STEALTH_MOD = new int[TileData.MAX_TILES];
    static {
        // Default: moving in open = drain
        for (int i = 0; i < TileData.MAX_TILES; i++) {
            TILE_STEALTH_MOD[i] = -3; // general movement drain
        }
        // Favourable tiles
        TILE_STEALTH_MOD[TileData.TILE_BUSH]       =  5; // hide in bush
        TILE_STEALTH_MOD[TileData.TILE_TALL_GRASS] =  8; // great cover
        TILE_STEALTH_MOD[TileData.TILE_CAVE]       =  3;
        TILE_STEALTH_MOD[TileData.TILE_NIGHT]      =  2;
        TILE_STEALTH_MOD[TileData.TILE_CARPET]     = -1; // quiet floor
        TILE_STEALTH_MOD[TileData.TILE_FLOOR_WOOD] = -4; // creaky
        TILE_STEALTH_MOD[TileData.TILE_FLOOR_TILE] = -2;
        // Water tiles are loud
        TILE_STEALTH_MOD[TileData.TILE_WATER]      = -8;
        TILE_STEALTH_MOD[TileData.TILE_SWAMP]      = -6;
    }

    // =========================================================
    //  STATE
    // =========================================================
    private int stealthLevel;      // 0-100
    private boolean isCrouching;
    private boolean isStealthMode; // skill-activated stealth
    private int detectionRing;     // 0=safe, 1=cautious, 2=alert, 3=detected

    // Alert timers per-enemy (indexed by enemy slot)
    private int[] enemyAlertTimer;
    private int[] enemyAwareness;  // how perceptive this enemy is (0-50)
    private int maxEnemies;

    // Regen timer
    private int regenTimer;
    private static final int REGEN_TICKS = 10; // ticks between regen pulses

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public StealthSystem(int maxEnemySlots) {
        this.maxEnemies = maxEnemySlots;
        enemyAlertTimer = new int[maxEnemySlots];
        enemyAwareness  = new int[maxEnemySlots];
        stealthLevel = 50; // start half-stealth
        isCrouching = false;
        isStealthMode = false;
        regenTimer = 0;
    }

    // =========================================================
    //  UPDATE (call each game tick)
    // =========================================================
    public void update(boolean playerMoving, boolean playerRunning, int currentTile) {
        if (playerRunning) {
            drain(8);
            return;
        }

        if (playerMoving) {
            int mod = TILE_STEALTH_MOD[currentTile >= 0 && currentTile < TileData.MAX_TILES
                                       ? currentTile : 0];
            if (isCrouching) {
                // Crouching halves drain, doubles gain
                if (mod < 0) mod /= 2;
                else         mod *= 2;
            }
            if (mod < 0) drain(-mod);
            else         regen(mod);
        } else {
            // Standing still — passive regen
            regenTimer++;
            if (regenTimer >= REGEN_TICKS) {
                regenTimer = 0;
                int passiveRegen = isCrouching ? 4 : 2;
                if (isStealthMode) passiveRegen *= 2;
                regen(passiveRegen);
            }
        }

        // Update enemy alert timers
        for (int i = 0; i < maxEnemies; i++) {
            if (enemyAlertTimer[i] > 0) {
                enemyAlertTimer[i]--;
            }
        }

        updateDetectionRing();
    }

    private void drain(int amount) {
        stealthLevel = GameConfig.clamp(stealthLevel - amount, 0, GameConfig.STEALTH_MAX);
    }

    private void regen(int amount) {
        stealthLevel = GameConfig.clamp(stealthLevel + amount, 0, GameConfig.STEALTH_MAX);
    }

    private void updateDetectionRing() {
        if      (stealthLevel >= 80) detectionRing = 0; // safe
        else if (stealthLevel >= 50) detectionRing = 1; // cautious
        else if (stealthLevel >= 25) detectionRing = 2; // alert
        else                         detectionRing = 3; // detected
    }

    // =========================================================
    //  DETECTION CHECK
    // =========================================================
    /**
     * Check if enemy[slot] detects the player.
     * @param slot       Enemy slot index
     * @param distance   Distance in tiles from enemy to player
     * @param visionRange Enemy vision range in tiles
     * @param randomVal  A 0-100 random value (pass from battle random seed)
     * @return true if detected
     */
    public boolean checkDetection(int slot, int distance, int visionRange, int randomVal) {
        if (slot < 0 || slot >= maxEnemies) return false;
        if (distance > visionRange) return false;

        // Detection chance: higher when stealth is low, enemy is perceptive, player is close
        int chance = GameConfig.STEALTH_DETECT_BASE
                   + enemyAwareness[slot]
                   - stealthLevel / 2
                   + (visionRange - distance) * 5;

        if (isStealthMode) chance -= 20;
        if (isCrouching)   chance -= 10;

        chance = GameConfig.clamp(chance, 0, 95);

        boolean detected = (randomVal % 100) < chance;
        if (detected) {
            enemyAlertTimer[slot] = 120; // enemy stays alert for 120 ticks
        }
        return detected;
    }

    /** Whether a specific enemy is currently alerted. */
    public boolean isEnemyAlerted(int slot) {
        if (slot < 0 || slot >= maxEnemies) return false;
        return enemyAlertTimer[slot] > 0;
    }

    // =========================================================
    //  ATTACK / EVENTS
    // =========================================================
    /** Call when player attacks — drains stealth fully. Returns true if surprise attack. */
    public boolean onPlayerAttack() {
        boolean surprise = stealthLevel >= GameConfig.STEALTH_KILL_THRESHOLD;
        stealthLevel = 0;
        updateDetectionRing();
        return surprise;
    }

    /** Call when player is spotted (e.g. triggered trap). */
    public void onDetected() {
        stealthLevel = 0;
        updateDetectionRing();
    }

    /** Skill: Vanish — temporarily boost stealth. */
    public void activateVanish() {
        isStealthMode = true;
        stealthLevel = GameConfig.clamp(stealthLevel + 40, 0, GameConfig.STEALTH_MAX);
        updateDetectionRing();
    }

    public void deactivateVanish() {
        isStealthMode = false;
    }

    // =========================================================
    //  SETTERS
    // =========================================================
    public void setCrouching(boolean v) {
        isCrouching = v;
    }

    public void setEnemyAwareness(int slot, int awareness) {
        if (slot >= 0 && slot < maxEnemies) {
            enemyAwareness[slot] = GameConfig.clamp(awareness, 0, 50);
        }
    }

    public void setStealthLevel(int level) {
        stealthLevel = GameConfig.clamp(level, 0, GameConfig.STEALTH_MAX);
        updateDetectionRing();
    }

    // =========================================================
    //  GETTERS
    // =========================================================
    public int  getStealthLevel()    { return stealthLevel; }
    public int  getDetectionRing()   { return detectionRing; }
    public boolean isCrouching()     { return isCrouching; }
    public boolean isStealthMode()   { return isStealthMode; }

    /** Human-readable stealth tier. */
    public String getStealthLabel() {
        if (stealthLevel >= 80) return "Hidden";
        if (stealthLevel >= 50) return "Cautious";
        if (stealthLevel >= 25) return "Noticed";
        return "Exposed";
    }
}
