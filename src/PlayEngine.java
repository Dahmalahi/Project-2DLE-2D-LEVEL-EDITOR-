import javax.microedition.lcdui.*;

public class PlayEngine {

    // -------------------------------------------------
    //  PLAYER STATE
    // -------------------------------------------------
    public int playerX, playerY;
    public int playerDir;          // 0=down,1=up,2=left,3=right
    public int playerFrame;
    public boolean isSwimming;
    public boolean isSliding;
    public int slideDir;
    public boolean isDashing;
    public int dashTimer;
    public int dashCooldown;
    public boolean isClimbing;     // on ladder/vine tile
    public boolean isCrouching;

    // Step counter (drives encounter, quest, day-night)
    public int stepCount;

    // Carried / thrown object
    public boolean hasObject;
    public int carriedTile;
    public boolean isThrowing;
    public int throwX, throwY;
    public int throwDirX, throwDirY;
    public int throwTimer;

    // -------------------------------------------------
    //  ANIMATION PARTICLES
    // -------------------------------------------------
    public static final int MAX_PARTICLES = 64;
    public static final int PART_DUST      = 0;
    public static final int PART_SPLASH    = 1;
    public static final int PART_FOOTPRINT = 2;
    public static final int PART_SPARKLE   = 3;
    public static final int PART_FIRE      = 4;
    public static final int PART_SMOKE     = 5;   // NEW: used for dashing
    public static final int PART_LEAF      = 6;   // NEW: autumn/forest
    public static final int PART_HEAL      = 7;   // NEW: green+ healing

    public int[] particleX;
    public int[] particleY;
    public int[] particleVX;       // NEW: velocity
    public int[] particleVY;
    public int[] particleType;
    public int[] particleLife;
    public int[] particleMaxLife;
    public int[] particleFrame;
    public int particleCount;

    // -------------------------------------------------
    //  DIALOGUE STATE
    // -------------------------------------------------
    public boolean dialogueActive;
    public String[] currentDialogue;
    public int dialogueLine;
    public int dialogueCharPos;
    public long dialogueTimer;
    public String dialogueSpeaker;

    // -------------------------------------------------
    //  SCREEN EFFECTS
    // -------------------------------------------------
    public int shakeX, shakeY;
    public int shakeDuration;
    public int shakeIntensity;
    public int fadeLevel;          // 0=clear, 255=black
    public int fadeTarget;
    public int fadeSpeed;
    public boolean isFading;
    private int fadeRandSeed;

    // -------------------------------------------------
    //  MINIMAP
    // -------------------------------------------------
    public boolean minimapVisible;
    public boolean minimapExpanded;
    public boolean fogOfWar[][];   // explored tiles
    private int minimapScrollX, minimapScrollY;

    // -------------------------------------------------
    //  DAMAGE & INVINCIBILITY
    // -------------------------------------------------
    public boolean isHurt;
    public int hurtTimer;
    public int invincibleTimer;

    // -------------------------------------------------
    //  ENCOUNTER TRACKING
    // -------------------------------------------------
    public int encounterStep;       // steps since last encounter

    // -------------------------------------------------
    //  DAY / NIGHT  (driven by stepCount via GameConfig)
    // -------------------------------------------------
    public int dayPeriod;           // 0-6 (GameConfig.DAY_DAWN etc.)
    public int dayStepCounter;      // resets each period

    // -------------------------------------------------
    //  RANDOM SEED
    // -------------------------------------------------
    private int randomSeed;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public PlayEngine() {
        particleX       = new int[MAX_PARTICLES];
        particleY       = new int[MAX_PARTICLES];
        particleVX      = new int[MAX_PARTICLES];
        particleVY      = new int[MAX_PARTICLES];
        particleType    = new int[MAX_PARTICLES];
        particleLife    = new int[MAX_PARTICLES];
        particleMaxLife = new int[MAX_PARTICLES];
        particleFrame   = new int[MAX_PARTICLES];
        particleCount   = 0;

        randomSeed = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
        fadeRandSeed = randomSeed;
        reset();
    }

    public void reset() {
        playerX = 0; playerY = 0; playerDir = 0; playerFrame = 0;
        isSwimming = false; isSliding = false;
        isDashing = false; dashTimer = 0; dashCooldown = 0;
        isClimbing = false; isCrouching = false;
        stepCount = 0; encounterStep = 0;
        dayPeriod = 0; dayStepCounter = 0;
        hasObject = false; isThrowing = false;
        dialogueActive = false;
        isHurt = false; hurtTimer = 0; invincibleTimer = 0;
        shakeX = 0; shakeY = 0; shakeDuration = 0;
        fadeLevel = 0; fadeTarget = 0; fadeSpeed = 0; isFading = false;
        minimapVisible = true; minimapExpanded = false;
        particleCount = 0;
        fogOfWar = null;
    }

    // -------------------------------------------------
    //  FOG OF WAR
    // -------------------------------------------------
    public void initFog(int cols, int rows) {
        fogOfWar = new boolean[rows][cols];
    }

    public void revealAround(int cx, int cy, int radius) {
        if (fogOfWar == null) return;
        int rows = fogOfWar.length;
        int cols = fogOfWar[0].length;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < cols && y >= 0 && y < rows) {
                    if (Math.abs(dx) + Math.abs(dy) <= radius) {
                        fogOfWar[y][x] = true;
                    }
                }
            }
        }
    }

    // -------------------------------------------------
    //  MOVEMENT & COLLISION
    // -------------------------------------------------
    public boolean canMove(int[][][] mapData, int nx, int ny,
                           int mapCols, int mapRows) {
        if (nx < 0 || nx >= mapCols || ny < 0 || ny >= mapRows) return false;
        int tile = mapData[1][ny][nx];
        if (TileData.isSolid(tile)) return false;
        if (TileData.isWater(tile)) {
            isSwimming = true;
        } else {
            isSwimming = false;
        }
        return true;
    }

    public void move(int[][][] mapData, int dx, int dy,
                     int mapCols, int mapRows, int tileSize) {
        if (dialogueActive) return;
        if (isThrowing) return;
        if (isFading) return;

        // Direction
        if      (dy < 0) playerDir = 1;
        else if (dy > 0) playerDir = 0;
        else if (dx < 0) playerDir = 2;
        else if (dx > 0) playerDir = 3;

        // Dash speed: skip ahead one extra tile
        int steps = (isDashing) ? 2 : 1;

        for (int s = 0; s < steps; s++) {
            int newX = playerX + dx;
            int newY = playerY + dy;

            if (canMove(mapData, newX, newY, mapCols, mapRows)) {
                playerX = newX;
                playerY = newY;
                playerFrame = (playerFrame + 1) % 4;
                stepCount++;
                encounterStep++;
                dayStepCounter++;

                // Update day period
                if (dayStepCounter >= GameConfig.STEPS_PER_PERIOD) {
                    dayStepCounter = 0;
                    dayPeriod = (dayPeriod + 1) % 7;
                }

                // Tile effects
                int ground = mapData[0][playerY][playerX];
                int obj    = mapData[1][playerY][playerX];

                // Walk particles
                if (TileData.hasWalkAnim(ground)) {
                    spawnWalkParticle(playerX * tileSize + tileSize / 2,
                                      playerY * tileSize + tileSize - 2, ground);
                }

                // Damage tiles
                if (TileData.isDamage(ground) || TileData.isDamage(obj)) {
                    takeDamage(isDashing ? 5 : 10);  // dash reduces lava dmg
                    spawnParticle(playerX * tileSize + tileSize / 2,
                                  playerY * tileSize + tileSize / 2,
                                  PART_FIRE, 20, 0, -1);
                }

                // Slippery
                if (TileData.isSlippery(ground)) {
                    isSliding = true;
                    slideDir = playerDir;
                } else {
                    isSliding = false;
                }

                // Climbing
                isClimbing = (obj == TileData.TILE_LADDER || obj == TileData.TILE_VINE);

                // Fog reveal
                revealAround(playerX, playerY, minimapExpanded ? 4 : 3);

            } else {
                break; // Dash stops at wall
            }
        }

        // Dash smoke trail
        if (isDashing) {
            spawnParticle(playerX * tileSize + tileSize / 2,
                          playerY * tileSize + tileSize / 2,
                          PART_SMOKE, 12, 0, 0);
        }
    }

    // -------------------------------------------------
    //  DASH
    // -------------------------------------------------
    public boolean canDash() {
        return dashCooldown <= 0 && !isSwimming && !isClimbing;
    }

    public void startDash() {
        if (!canDash()) return;
        isDashing = true;
        dashTimer = GameConfig.DASH_SPEED;
        dashCooldown = 45;
        invincibleTimer = Math.max(invincibleTimer, dashTimer);
    }

    public void updateDash() {
        if (isDashing) {
            dashTimer--;
            if (dashTimer <= 0) isDashing = false;
        }
        if (dashCooldown > 0) dashCooldown--;
    }

    // -------------------------------------------------
    //  SLIDING
    // -------------------------------------------------
    public void handleSliding(int[][][] mapData, int mapCols, int mapRows,
                              int tileSize) {
        if (!isSliding) return;
        int dx = 0, dy = 0;
        switch (slideDir) {
            case 0: dy =  1; break;
            case 1: dy = -1; break;
            case 2: dx = -1; break;
            case 3: dx =  1; break;
        }
        int newX = playerX + dx, newY = playerY + dy;
        if (canMove(mapData, newX, newY, mapCols, mapRows)) {
            playerX = newX; playerY = newY;
            stepCount++;
            if (!TileData.isSlippery(mapData[0][playerY][playerX])) isSliding = false;
        } else {
            isSliding = false;
        }
    }

    // -------------------------------------------------
    //  SCREEN EFFECTS
    // -------------------------------------------------
    public void triggerShake(int intensity, int duration) {
        shakeIntensity = intensity;
        shakeDuration = duration;
    }

    public void startFade(boolean toBlack, int speed) {
        isFading = true;
        fadeTarget = toBlack ? 255 : 0;
        fadeSpeed = speed;
    }

    public void updateEffects() {
        // Shake
        if (shakeDuration > 0) {
            shakeDuration--;
            fadeRandSeed = nextRand(fadeRandSeed);
            shakeX = (fadeRandSeed % (shakeIntensity * 2 + 1)) - shakeIntensity;
            fadeRandSeed = nextRand(fadeRandSeed);
            shakeY = (fadeRandSeed % (shakeIntensity * 2 + 1)) - shakeIntensity;
        } else {
            shakeX = 0; shakeY = 0;
        }

        // Fade
        if (isFading) {
            if (fadeLevel < fadeTarget) {
                fadeLevel = Math.min(fadeTarget, fadeLevel + fadeSpeed);
            } else if (fadeLevel > fadeTarget) {
                fadeLevel = Math.max(fadeTarget, fadeLevel - fadeSpeed);
            }
            if (fadeLevel == fadeTarget && fadeTarget == 0) isFading = false;
        }
    }

    // -------------------------------------------------
    //  INTERACTION
    // -------------------------------------------------
    public void interact(int[][][] mapData, NPCManager npcMgr,
                         int mapCols, int mapRows) {
        int fx = playerX, fy = playerY;
        switch (playerDir) {
            case 0: fy++; break;
            case 1: fy--; break;
            case 2: fx--; break;
            case 3: fx++; break;
        }
        if (fx < 0 || fx >= mapCols || fy < 0 || fy >= mapRows) return;

        // NPC
        int npcId = npcMgr.findNPCAt(fx, fy);
        if (npcId >= 0) {
            startDialogueNPC(npcMgr, npcId);
            // Improve relation on each talk
            npcMgr.changeRelation(npcId, 1);
            npcMgr.npcTalkedTo[npcId] = true;
            return;
        }

        // Tile interactions
        int tile = mapData[1][fy][fx];

        if (TileData.isPickup(tile) && !hasObject) {
            hasObject = true;
            carriedTile = tile;
            mapData[1][fy][fx] = 0;
            spawnParticle(fx * 16 + 8, fy * 16 + 8, PART_SPARKLE, 20, 0, -1);
            return;
        }

        if (TileData.isPushable(tile)) {
            int px = fx, py = fy;
            switch (playerDir) {
                case 0: py++; break;
                case 1: py--; break;
                case 2: px--; break;
                case 3: px++; break;
            }
            if (px >= 0 && px < mapCols && py >= 0 && py < mapRows
                    && mapData[1][py][px] == 0) {
                mapData[1][py][px] = tile;
                mapData[1][fy][fx] = 0;
                triggerShake(1, 4);
            }
        }
    }

    // -------------------------------------------------
    //  THROW
    // -------------------------------------------------
    public void throwObject(int tileSize) {
        if (!hasObject || !TileData.isThrowable(carriedTile)) return;
        isThrowing = true;
        throwX = playerX * tileSize + tileSize / 2;
        throwY = playerY * tileSize + tileSize / 2;
        throwTimer = 24;
        switch (playerDir) {
            case 0: throwDirX =  0; throwDirY =  5; break;
            case 1: throwDirX =  0; throwDirY = -5; break;
            case 2: throwDirX = -5; throwDirY =  0; break;
            case 3: throwDirX =  5; throwDirY =  0; break;
        }
        hasObject = false;
    }

    /**
     * Overload called by EditorCanvas:
     *   playEngine.throwObject(mapData, MAP_COLS, MAP_ROWS, tileSize)
     * Ignores the map args (collision is handled in updateThrow) and delegates.
     */
    public void throwObject(int[][][] mapData, int cols, int rows, int tileSize) {
        throwObject(tileSize);
    }

    public void updateThrow(int[][][] mapData, int mapCols, int mapRows,
                            int tileSize) {
        if (!isThrowing) return;
        throwX += throwDirX;
        throwY += throwDirY;
        throwTimer--;
        int tx = throwX / tileSize, ty = throwY / tileSize;
        if (tx < 0 || tx >= mapCols || ty < 0 || ty >= mapRows
                || throwTimer <= 0
                || TileData.isSolid(mapData[1][ty][tx])) {
            isThrowing = false;
            if (tx >= 0 && tx < mapCols && ty >= 0 && ty < mapRows
                    && mapData[1][ty][tx] == 0) {
                mapData[1][ty][tx] = carriedTile;
            }
            triggerShake(2, 3);
        }
    }

    // -------------------------------------------------
    //  DIALOGUE
    // -------------------------------------------------
    public void startDialogueNPC(NPCManager npcMgr, int npcId) {
        int dlgId = npcMgr.getEffectiveDialogue(npcId);
        if (dlgId < 0) return;
        String[] lines = npcMgr.getDialogue(dlgId);
        if (lines == null || lines.length == 0) return;
        currentDialogue = lines;
        dialogueSpeaker = NPCManager.NPC_TYPE_NAMES[
            Math.min(npcMgr.npcType[npcId], NPCManager.NPC_TYPE_NAMES.length - 1)];
        dialogueActive = true;
        dialogueLine = 0;
        dialogueCharPos = 0;
        dialogueTimer = System.currentTimeMillis();
    }

    public void startDialogue(String[] lines, String speaker) {
        currentDialogue = lines;
        dialogueSpeaker = (speaker != null) ? speaker : "";
        dialogueActive = true;
        dialogueLine = 0;
        dialogueCharPos = 0;
        dialogueTimer = System.currentTimeMillis();
    }

    public void advanceDialogue() {
        if (!dialogueActive) return;
        if (dialogueCharPos < currentDialogue[dialogueLine].length()) {
            dialogueCharPos = currentDialogue[dialogueLine].length();
        } else {
            dialogueLine++;
            dialogueCharPos = 0;
            if (dialogueLine >= currentDialogue.length) {
                dialogueActive = false;
            }
        }
    }

    public void updateDialogue() {
        if (!dialogueActive) return;
        long now = System.currentTimeMillis();
        if (now - dialogueTimer > GameConfig.TEXT_SPEED_MED) {
            dialogueTimer = now;
            if (dialogueCharPos < currentDialogue[dialogueLine].length()) {
                dialogueCharPos++;
            }
        }
    }

    public String getDialogueText() {
        if (!dialogueActive || currentDialogue == null) return "";
        if (dialogueLine >= currentDialogue.length) return "";
        String line = currentDialogue[dialogueLine];
        return (dialogueCharPos >= line.length()) ? line
                                                   : line.substring(0, dialogueCharPos);
    }

    // -------------------------------------------------
    //  DAMAGE
    // -------------------------------------------------
    public void takeDamage(int amount) {
        if (invincibleTimer > 0) return;
        isHurt = true;
        hurtTimer = 30;
        invincibleTimer = 60;
    }

    public void updateDamage() {
        if (hurtTimer > 0) hurtTimer--;
        else isHurt = false;
        if (invincibleTimer > 0) invincibleTimer--;
    }

    // -------------------------------------------------
    //  PARTICLES
    // -------------------------------------------------
    public void spawnParticle(int x, int y, int type, int life, int vx, int vy) {
        if (particleCount >= MAX_PARTICLES) {
            // Drop oldest
            for (int i = 0; i < MAX_PARTICLES - 1; i++) {
                particleX[i] = particleX[i + 1];
                particleY[i] = particleY[i + 1];
                particleVX[i] = particleVX[i + 1];
                particleVY[i] = particleVY[i + 1];
                particleType[i] = particleType[i + 1];
                particleLife[i] = particleLife[i + 1];
                particleMaxLife[i] = particleMaxLife[i + 1];
                particleFrame[i] = particleFrame[i + 1];
            }
            particleCount = MAX_PARTICLES - 1;
        }
        int idx = particleCount++;
        particleX[idx] = x; particleY[idx] = y;
        particleVX[idx] = vx; particleVY[idx] = vy;
        particleType[idx] = type;
        particleLife[idx] = life; particleMaxLife[idx] = life;
        particleFrame[idx] = 0;
    }

    public void spawnWalkParticle(int x, int y, int tile) {
        if (TileData.isWater(tile)) {
            spawnParticle(x, y, PART_SPLASH, 12, 0, -1);
        } else if (tile == TileData.TILE_SNOW || tile == TileData.TILE_SAND) {
            spawnParticle(x, y, PART_FOOTPRINT, 40, 0, 0);
        } else {
            spawnParticle(x, y, PART_DUST, 12, 0, -1);
        }
    }

    public void spawnHealParticles(int cx, int cy) {
        for (int i = 0; i < 5; i++) {
            randomSeed = nextRand(randomSeed);
            int ox = (randomSeed % 12) - 6;
            randomSeed = nextRand(randomSeed);
            spawnParticle(cx + ox, cy - 4, PART_HEAL, 25, 0, -1);
        }
    }

    public void updateParticles() {
        for (int i = particleCount - 1; i >= 0; i--) {
            particleX[i] += particleVX[i];
            particleY[i] += particleVY[i];
            particleLife[i]--;
            particleFrame[i]++;

            // Type-specific motion
            switch (particleType[i]) {
                case PART_DUST:
                    particleY[i]--;
                    break;
                case PART_FIRE:
                    particleX[i] += (particleFrame[i] % 2 == 0) ? 1 : -1;
                    particleY[i]--;
                    break;
                case PART_SMOKE:
                    particleY[i]--;
                    if (particleFrame[i] % 3 == 0) particleX[i] += (particleFrame[i] % 6 < 3) ? 1 : -1;
                    break;
                case PART_LEAF:
                    particleY[i]++;
                    if (particleFrame[i] % 4 == 0) particleX[i] += (particleFrame[i] % 8 < 4) ? 1 : -1;
                    break;
                case PART_HEAL:
                    particleY[i]--;
                    break;
            }

            if (particleLife[i] <= 0) {
                for (int j = i; j < particleCount - 1; j++) {
                    particleX[j] = particleX[j + 1];
                    particleY[j] = particleY[j + 1];
                    particleVX[j] = particleVX[j + 1];
                    particleVY[j] = particleVY[j + 1];
                    particleType[j] = particleType[j + 1];
                    particleLife[j] = particleLife[j + 1];
                    particleMaxLife[j] = particleMaxLife[j + 1];
                    particleFrame[j] = particleFrame[j + 1];
                }
                particleCount--;
            }
        }
    }

    // -------------------------------------------------
    //  RENDER: PARTICLES
    // -------------------------------------------------
    public void drawParticles(Graphics g, int scrollX, int scrollY,
                              int offsetX, int offsetY, int tileSize) {
        for (int i = 0; i < particleCount; i++) {
            int px = particleX[i] - scrollX * tileSize + offsetX;
            int py = particleY[i] - scrollY * tileSize + offsetY;
            int life = particleLife[i];
            int maxL = Math.max(1, particleMaxLife[i]);

            switch (particleType[i]) {
                case PART_DUST:
                    g.setColor(0xCCBB99);
                    int sz = 1 + (life * 2 / maxL);
                    g.fillRect(px - sz / 2, py - sz / 2, sz, sz);
                    break;
                case PART_SPLASH:
                    g.setColor(0x66AAFF);
                    int r = particleFrame[i] / 2 + 1;
                    if (r < 6) g.drawArc(px - r, py - r, r * 2, r * 2, 0, 360);
                    break;
                case PART_FOOTPRINT:
                    if (life > maxL / 2) {
                        g.setColor(0x555555);
                        g.fillRect(px - 2, py - 1, 4, 2);
                    }
                    break;
                case PART_SPARKLE:
                    g.setColor(0xFFFF88);
                    g.drawLine(px - 3, py, px + 3, py);
                    g.drawLine(px, py - 3, px, py + 3);
                    break;
                case PART_FIRE:
                    g.setColor((particleFrame[i] % 2 == 0) ? 0xFF4400 : 0xFFAA00);
                    g.fillRect(px - 2, py - 2, 4, 4);
                    break;
                case PART_SMOKE:
                    int alpha = life * 3 / maxL;  // 0-3
                    g.setColor(0x999999 - alpha * 0x111111);
                    g.fillRect(px - 3, py - 3, 6, 6);
                    break;
                case PART_LEAF:
                    g.setColor(0x884400 + ((particleFrame[i] % 3) * 0x112200));
                    g.fillRect(px - 2, py - 2, 4, 3);
                    break;
                case PART_HEAL:
                    g.setColor(0x00FF66);
                    g.drawLine(px - 1, py, px + 1, py);
                    g.drawLine(px, py - 1, px, py + 1);
                    break;
            }
        }
    }

    // -------------------------------------------------
    //  RENDER: PLAYER
    // -------------------------------------------------
    public void drawPlayer(Graphics g, int scrollX, int scrollY,
                           int offsetX, int offsetY, int tileSize) {
        int px = (playerX - scrollX) * tileSize + offsetX;
        int py = (playerY - scrollY) * tileSize + offsetY;

        if (isHurt && (hurtTimer % 4) < 2) return;  // Blink on hurt

        // Crouching: shift down, smaller rect
        if (isCrouching) py += tileSize / 4;

        // Climbing: shift up slightly
        if (isClimbing) py -= tileSize / 6;

        // Swimming: half body visible
        int bodyH = isSwimming ? tileSize / 2 : (isCrouching ? tileSize / 2 : tileSize - 4);
        int bodyY = isSwimming ? py + tileSize / 2 : py + 2;

        // Dash glow
        if (isDashing) {
            g.setColor(0x88CCFF);
            g.fillRect(px, py, tileSize, tileSize);
        }

        // Body
        g.setColor(isHurt ? 0xFF4444 : 0x00AA00);
        g.fillRect(px + 2, bodyY, tileSize - 4, bodyH);

        // Carried object above head
        if (hasObject) {
            g.setColor(carriedTile < TileData.MAX_TILES
                       ? TileData.TILE_COLORS[carriedTile] : 0xFF00FF);
            g.fillRect(px + 2, py - 6, tileSize - 4, 6);
        }

        // Direction dot
        g.setColor(0xFFFFFF);
        int cx = px + tileSize / 2, cy = py + tileSize / 2;
        switch (playerDir) {
            case 0: g.fillRect(cx - 2, cy + 3, 4, 3); break;
            case 1: g.fillRect(cx - 2, cy - 6, 4, 3); break;
            case 2: g.fillRect(cx - 7, cy - 1, 3, 3); break;
            case 3: g.fillRect(cx + 4, cy - 1, 3, 3); break;
        }

        // Swimming waves
        if (isSwimming) {
            g.setColor(0x4488FF);
            int wY = py + tileSize - 4;
            for (int w = 0; w < 3; w++) {
                int wX = px + w * (tileSize / 3);
                g.drawLine(wX, wY + ((playerFrame + w) % 2), wX + tileSize / 4, wY);
            }
        }
    }

    // -------------------------------------------------
    //  RENDER: THROWN OBJECT
    // -------------------------------------------------
    public void drawThrownObject(Graphics g, int scrollX, int scrollY,
                                 int offsetX, int offsetY, int tileSize) {
        if (!isThrowing) return;
        int tx = throwX - scrollX * tileSize + offsetX;
        int ty = throwY - scrollY * tileSize + offsetY;
        // Rotation animation
        int rot = (throwTimer % 4) * 45;
        g.setColor(carriedTile < TileData.MAX_TILES
                   ? TileData.TILE_COLORS[carriedTile] : 0xFF00FF);
        g.fillRect(tx - 4, ty - 4, 8, 8);
        g.setColor(0xFFFFFF);
        g.drawRect(tx - 4, ty - 4, 7, 7);
    }

    // -------------------------------------------------
    //  RENDER: DIALOGUE BOX
    // -------------------------------------------------
    public void drawDialogue(Graphics g, int screenW, int screenH) {
        if (!dialogueActive) return;

        int boxH = 56;
        int boxY = screenH - boxH - 4;

        g.setColor(0x000033);
        g.fillRect(4, boxY, screenW - 8, boxH);
        g.setColor(0x4466AA);
        g.drawRect(4, boxY, screenW - 9, boxH - 1);
        g.drawRect(5, boxY + 1, screenW - 11, boxH - 3);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

        // Speaker name
        int textStartY = boxY + 8;
        if (dialogueSpeaker != null && dialogueSpeaker.length() > 0) {
            g.setFont(bold);
            g.setColor(0xFFDD44);
            g.drawString(dialogueSpeaker, 10, boxY + 4, Graphics.TOP | Graphics.LEFT);
            textStartY = boxY + 18;
        }

        // Dialogue text
        g.setFont(font);
        g.setColor(0xFFFFFF);
        g.drawString(getDialogueText(), 10, textStartY, Graphics.TOP | Graphics.LEFT);

        // Continue blinker
        if (dialogueCharPos >= currentDialogue[dialogueLine].length()) {
            if ((System.currentTimeMillis() / 400) % 2 == 0) {
                g.setColor(0xFFFF44);
                g.fillRect(screenW - 18, boxY + boxH - 12, 8, 8);
            }
        }
    }

    // -------------------------------------------------
    //  RENDER: HUD
    // -------------------------------------------------
    /**
     * Overload called by EditorCanvas: playEngine.drawHUD(g, screenW)
     * PlayEngine itself holds no HP/gold — draws a minimal placeholder HUD.
     * For a full HUD pass a PlayerData instance to drawHUD(g, screenW, player).
     */
    public void drawHUD(Graphics g, int screenW) {
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        // HP bar shell (no data — just the chrome so the layout is correct)
        g.setColor(0x440000);
        g.fillRect(5, 5, 54, 9);
        g.setColor(0x226622);
        g.fillRect(6, 6, 52, 7);  // full green bar as placeholder
        g.setColor(0xFFFFFF);
        g.drawRect(5, 5, 53, 8);
        g.setFont(small);
        g.setColor(0xAAAAAA);
        g.drawString("HP", 6, 15, Graphics.TOP | Graphics.LEFT);
    }

    public void drawHUD(Graphics g, int screenW, PlayerData player) {
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font bold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

        // HP bar
        int barW = 52;
        int hp = player.hp, maxHp = player.maxHp;
        g.setColor(0x440000);
        g.fillRect(5, 5, barW + 2, 9);
        g.setColor(hp > maxHp / 4 ? 0xFF2222 : 0xFF6600);
        g.fillRect(6, 6, (hp * barW) / Math.max(1, maxHp), 7);
        g.setColor(0xFFFFFF);
        g.drawRect(5, 5, barW + 1, 8);

        // MP bar
        int mp = player.mp, maxMp = player.maxMp;
        g.setColor(0x000044);
        g.fillRect(5, 16, barW + 2, 9);
        g.setColor(0x2244FF);
        g.fillRect(6, 17, (mp * barW) / Math.max(1, maxMp), 7);
        g.setColor(0xFFFFFF);
        g.drawRect(5, 16, barW + 1, 8);

        // Level & EXP
        g.setFont(bold);
        g.setColor(0xFFDD44);
        g.drawString("Lv" + player.level, 5, 27, Graphics.TOP | Graphics.LEFT);

        // Gold
        g.setFont(bold);
        g.setColor(0xFFD700);
        g.drawString("$" + player.gold, screenW - 4, 5, Graphics.TOP | Graphics.RIGHT);

        // Day period icon
        String[] periodIcons = {"✦","✦","☀","☀","🌇","✦","🌙"};
        String[] periodNames = {"Dawn","Morn","Noon","Aft","Eve","Dusk","Night"};
        if (dayPeriod >= 0 && dayPeriod < periodNames.length) {
            g.setFont(small);
            g.setColor(getDayColor(dayPeriod));
            g.drawString(periodNames[dayPeriod], screenW - 4, 17,
                         Graphics.TOP | Graphics.RIGHT);
        }

        // Dash indicator
        if (dashCooldown > 0) {
            g.setColor(0x446688);
            g.fillRect(5, 37, (dashCooldown * 30) / 45, 4);
            g.setColor(0x88CCFF);
            g.drawRect(5, 37, 30, 3);
        } else {
            g.setColor(0x88CCFF);
            g.fillRect(5, 37, 30, 4);
        }

        // Carried object
        if (hasObject) {
            g.setColor(carriedTile < TileData.MAX_TILES
                       ? TileData.TILE_COLORS[carriedTile] : 0xAA00AA);
            g.fillRect(screenW - 22, 28, 16, 16);
            g.setColor(0xFFFFFF);
            g.drawRect(screenW - 23, 27, 17, 17);
        }
    }

    private int getDayColor(int period) {
        switch (period) {
            case 0: return 0xAA88CC; // Dawn: purple
            case 1: return 0xFFCC66; // Morning: warm
            case 2: return 0xFFFFAA; // Noon: bright
            case 3: return 0xFFEE88; // Afternoon: yellow
            case 4: return 0xFF8844; // Evening: orange
            case 5: return 0x886644; // Dusk: brown
            default: return 0x4444AA; // Night: blue
        }
    }

    // -------------------------------------------------
    //  RENDER: MINIMAP
    // -------------------------------------------------
    public void drawMinimap(Graphics g, int screenW, int screenH,
                            int[][][] mapData, int mapCols, int mapRows) {
        if (!minimapVisible || mapData == null) return;

        int tileS = GameConfig.MINIMAP_TILE;
        int mapSize = GameConfig.MINIMAP_SIZE;

        // If expanded, center on screen
        int mmX, mmY, mmW, mmH;
        if (minimapExpanded) {
            mmW = mapCols * tileS;
            mmH = mapRows * tileS;
            mmX = (screenW - mmW) / 2;
            mmY = (screenH - mmH) / 2;
        } else {
            mmW = mapSize;
            mmH = mapSize;
            mmX = screenW - mapSize - 4;
            mmY = 4;
        }

        // Background
        g.setColor(0x000000);
        g.fillRect(mmX - 1, mmY - 1, mmW + 2, mmH + 2);

        int viewX = minimapExpanded ? 0 : playerX - mapSize / (tileS * 2);
        int viewY = minimapExpanded ? 0 : playerY - mapSize / (tileS * 2);

        for (int ty = 0; ty < mapRows; ty++) {
            for (int tx = 0; tx < mapCols; tx++) {
                // Fog of war
                if (GameConfig.MINIMAP_FOG && fogOfWar != null && !fogOfWar[ty][tx]) {
                    continue;
                }

                int drawX = mmX + (tx - viewX) * tileS;
                int drawY = mmY + (ty - viewY) * tileS;

                if (!minimapExpanded &&
                    (drawX < mmX || drawX >= mmX + mmW ||
                     drawY < mmY || drawY >= mmY + mmH)) {
                    continue;
                }

                int tile = mapData[0][ty][tx];
                int obj  = mapData[1][ty][tx];
                int color;

                if (obj != 0 && TileData.isSolid(obj)) {
                    color = 0x666666;
                } else {
                    color = getMinimapColor(tile);
                }

                if (tileS == 1) {
                    g.setColor(color);
                    g.drawLine(drawX, drawY, drawX, drawY);
                } else {
                    g.setColor(color);
                    g.fillRect(drawX, drawY, tileS, tileS);
                }
            }
        }

        // Player dot
        int pdX = mmX + (playerX - viewX) * tileS;
        int pdY = mmY + (playerY - viewY) * tileS;
        g.setColor(0x00FF00);
        g.fillRect(pdX, pdY, tileS, tileS);

        // Border
        g.setColor(0x446688);
        g.drawRect(mmX - 1, mmY - 1, mmW + 1, mmH + 1);
    }

    private int getMinimapColor(int tile) {
        switch (tile) {
            case TileData.TILE_GRASS:    return 0x228833;
            case TileData.TILE_WATER:    return 0x2244AA;
            case TileData.TILE_SAND:     return 0xCCAA66;
            case TileData.TILE_STONE:    return 0x777777;
            case TileData.TILE_PATH:     return 0xAA9977;
            case TileData.TILE_SNOW:     return 0xDDEEFF;
            case TileData.TILE_LAVA:     return 0xFF4400;
            case TileData.TILE_TREE:     return 0x115522;
            default:                     return 0x444444;
        }
    }

    // -------------------------------------------------
    //  RENDER: FADE OVERLAY (J2ME dithered)
    // -------------------------------------------------
    public void drawFadeOverlay(Graphics g, int screenW, int screenH) {
        if (fadeLevel <= 0) return;
        g.setColor(0x000000);
        // Dither density based on fadeLevel
        int skip = Math.max(1, 8 - fadeLevel / 32);
        for (int y = 0; y < screenH; y += skip) {
            for (int x = (y / skip) % skip; x < screenW; x += skip) {
                g.drawLine(x, y, x, y);
            }
        }
    }

    // -------------------------------------------------
    //  RANDOM
    // -------------------------------------------------
    private int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }
}