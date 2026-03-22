public class AISystem {

    // -------------------------------------------------
    //  AI BEHAVIOR TYPES
    // -------------------------------------------------
    public static final int AI_IDLE       = 0;
    public static final int AI_WANDER     = 1;
    public static final int AI_PATROL     = 2;
    public static final int AI_CHASE      = 3;
    public static final int AI_FLEE       = 4;
    public static final int AI_GUARD      = 5;
    public static final int AI_FOLLOW     = 6;
    public static final int AI_STATIONARY = 7;
    public static final int AI_STALKER    = 8;   // NEW: circles player, waits to ambush
    public static final int AI_AMBUSH     = 9;   // NEW: stays still until player is close

    // -------------------------------------------------
    //  AI STATES
    // -------------------------------------------------
    public static final int STATE_IDLE    = 0;
    public static final int STATE_MOVING  = 1;
    public static final int STATE_ALERT   = 2;
    public static final int STATE_CHASING = 3;
    public static final int STATE_ATTACK  = 4;
    public static final int STATE_RETURN  = 5;
    public static final int STATE_WAITING = 6;
    public static final int STATE_STALK   = 7;   // NEW
    public static final int STATE_AMBUSH  = 8;   // NEW: waiting in ambush

    // -------------------------------------------------
    //  DETECTION SETTINGS
    // -------------------------------------------------
    public static final int VISION_RANGE_SHORT  = 3;
    public static final int VISION_RANGE_MEDIUM = 5;
    public static final int VISION_RANGE_LONG   = 8;
    public static final int HEARING_RANGE       = 2;
    public static final int LOSE_SIGHT_RANGE    = 10;

    // Alert radii — when one enemy spots player, nearby allies are notified
    public static final int ALERT_RADIUS        = 4;

    // -------------------------------------------------
    //  ENTITY DATA
    // -------------------------------------------------
    private int maxEntities;

    public int[] entityX;
    public int[] entityY;
    public int[] entityDir;

    public int[] behaviorType;
    public int[] currentState;
    public int[] visionRange;
    public int[] moveSpeed;

    // Patrol
    public int[][] patrolPathX;
    public int[][] patrolPathY;
    public int[] patrolLength;
    public int[] patrolIndex;
    public boolean[] patrolReverse;

    // Chase / target
    public int[] targetX;
    public int[] targetY;
    public int[] lastSeenX;
    public int[] lastSeenY;
    public int[] loseTimer;

    // Home
    public int[] homeX;
    public int[] homeY;

    // Timers
    public int[] moveTimer;
    public int[] waitTimer;
    public int[] actionTimer;

    // Alert flags
    public boolean[] isActive;
    public boolean[] isHostile;
    public boolean[] canSeePlayer;
    public boolean[] isAlerted;      // NEW: notified by nearby ally
    public int[] alertTimer;         // NEW: how long alert lasts

    // Stalker state
    public int[] stalkAngle;         // 0-7 (octants around player)
    public int[] stalkTimer;         // time before pounce

    // Ambush trigger range
    public int[] ambushRange;

    // Group ID (entities with same groupId alert each other)
    public int[] groupId;

    // Random seeds
    private int[] randomSeed;

    public int entityCount;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public AISystem(int maxEntities) {
        this.maxEntities = maxEntities;

        entityX   = new int[maxEntities];
        entityY   = new int[maxEntities];
        entityDir = new int[maxEntities];

        behaviorType = new int[maxEntities];
        currentState = new int[maxEntities];
        visionRange  = new int[maxEntities];
        moveSpeed    = new int[maxEntities];

        patrolPathX   = new int[maxEntities][8];
        patrolPathY   = new int[maxEntities][8];
        patrolLength  = new int[maxEntities];
        patrolIndex   = new int[maxEntities];
        patrolReverse = new boolean[maxEntities];

        targetX    = new int[maxEntities];
        targetY    = new int[maxEntities];
        lastSeenX  = new int[maxEntities];
        lastSeenY  = new int[maxEntities];
        loseTimer  = new int[maxEntities];

        homeX = new int[maxEntities];
        homeY = new int[maxEntities];

        moveTimer   = new int[maxEntities];
        waitTimer   = new int[maxEntities];
        actionTimer = new int[maxEntities];

        isActive    = new boolean[maxEntities];
        isHostile   = new boolean[maxEntities];
        canSeePlayer = new boolean[maxEntities];
        isAlerted   = new boolean[maxEntities];
        alertTimer  = new int[maxEntities];

        stalkAngle  = new int[maxEntities];
        stalkTimer  = new int[maxEntities];
        ambushRange = new int[maxEntities];
        groupId     = new int[maxEntities];

        randomSeed  = new int[maxEntities];
        entityCount = 0;
    }

    // -------------------------------------------------
    //  ENTITY MANAGEMENT
    // -------------------------------------------------
    public int addEntity(int x, int y, int behavior, boolean hostile) {
        if (entityCount >= maxEntities) return -1;

        int id = entityCount;
        entityX[id] = x; entityY[id] = y; entityDir[id] = 0;
        behaviorType[id] = behavior;
        currentState[id] = (behavior == AI_AMBUSH) ? STATE_AMBUSH : STATE_IDLE;
        visionRange[id] = VISION_RANGE_MEDIUM;
        moveSpeed[id] = 15;
        homeX[id] = x; homeY[id] = y;
        isActive[id] = true; isHostile[id] = hostile;
        canSeePlayer[id] = false;
        isAlerted[id] = false; alertTimer[id] = 0;
        stalkAngle[id] = 0; stalkTimer[id] = 0;
        ambushRange[id] = 3;
        groupId[id] = -1;
        randomSeed[id] = (int)(System.currentTimeMillis() & 0x7FFFFFFF) + id * 12345;
        entityCount++;
        return id;
    }

    public void removeEntity(int id) {
        if (id < 0 || id >= entityCount) return;
        for (int i = id; i < entityCount - 1; i++) copyEntity(i + 1, i);
        entityCount--;
    }

    private void copyEntity(int from, int to) {
        entityX[to] = entityX[from]; entityY[to] = entityY[from];
        entityDir[to] = entityDir[from];
        behaviorType[to] = behaviorType[from];
        currentState[to] = currentState[from];
        visionRange[to] = visionRange[from];
        moveSpeed[to] = moveSpeed[from];
        for (int i = 0; i < 8; i++) {
            patrolPathX[to][i] = patrolPathX[from][i];
            patrolPathY[to][i] = patrolPathY[from][i];
        }
        patrolLength[to] = patrolLength[from];
        patrolIndex[to] = patrolIndex[from];
        patrolReverse[to] = patrolReverse[from];
        targetX[to] = targetX[from]; targetY[to] = targetY[from];
        lastSeenX[to] = lastSeenX[from]; lastSeenY[to] = lastSeenY[from];
        loseTimer[to] = loseTimer[from];
        homeX[to] = homeX[from]; homeY[to] = homeY[from];
        moveTimer[to] = moveTimer[from]; waitTimer[to] = waitTimer[from];
        actionTimer[to] = actionTimer[from];
        isActive[to] = isActive[from]; isHostile[to] = isHostile[from];
        canSeePlayer[to] = canSeePlayer[from];
        isAlerted[to] = isAlerted[from]; alertTimer[to] = alertTimer[from];
        stalkAngle[to] = stalkAngle[from]; stalkTimer[to] = stalkTimer[from];
        ambushRange[to] = ambushRange[from];
        groupId[to] = groupId[from];
        randomSeed[to] = randomSeed[from];
    }

    public void setPatrolPath(int id, int[] pathX, int[] pathY, int length) {
        if (id < 0 || id >= entityCount) return;
        patrolLength[id] = Math.min(length, 8);
        for (int i = 0; i < patrolLength[id]; i++) {
            patrolPathX[id][i] = pathX[i];
            patrolPathY[id][i] = pathY[i];
        }
    }

    // -------------------------------------------------
    //  MAIN UPDATE
    // -------------------------------------------------
    public void updateAI(int playerX, int playerY, int[][][] mapData,
                         int mapCols, int mapRows) {
        // Pass 1: update detection
        for (int i = 0; i < entityCount; i++) {
            if (!isActive[i]) continue;
            if (moveTimer[i] > 0) moveTimer[i]--;
            if (waitTimer[i] > 0) waitTimer[i]--;
            if (actionTimer[i] > 0) actionTimer[i]--;
            if (loseTimer[i] > 0) loseTimer[i]--;
            if (alertTimer[i] > 0) alertTimer[i]--;
            else isAlerted[i] = false;

            canSeePlayer[i] = checkLineOfSight(i, playerX, playerY, mapData, mapCols, mapRows);
        }

        // Pass 2: alert propagation (hostile group members)
        for (int i = 0; i < entityCount; i++) {
            if (!isActive[i] || !isHostile[i] || !canSeePlayer[i]) continue;
            if (groupId[i] < 0) continue;
            for (int j = 0; j < entityCount; j++) {
                if (i == j || !isActive[j] || groupId[j] != groupId[i]) continue;
                int dist = Math.abs(entityX[i] - entityX[j]) + Math.abs(entityY[i] - entityY[j]);
                if (dist <= ALERT_RADIUS && !isAlerted[j]) {
                    isAlerted[j] = true;
                    alertTimer[j] = 120;
                    lastSeenX[j] = playerX;
                    lastSeenY[j] = playerY;
                }
            }
        }

        // Pass 3: behavior
        for (int i = 0; i < entityCount; i++) {
            if (!isActive[i]) continue;
            switch (behaviorType[i]) {
                case AI_IDLE:       updateIdle(i); break;
                case AI_WANDER:     updateWander(i, mapData, mapCols, mapRows, playerX, playerY); break;
                case AI_PATROL:     updatePatrol(i, mapData, mapCols, mapRows, playerX, playerY); break;
                case AI_CHASE:      updateChase(i, playerX, playerY, mapData, mapCols, mapRows); break;
                case AI_FLEE:       updateFlee(i, playerX, playerY, mapData, mapCols, mapRows); break;
                case AI_GUARD:      updateGuard(i, playerX, playerY, mapData, mapCols, mapRows); break;
                case AI_FOLLOW:     updateFollow(i, playerX, playerY, mapData, mapCols, mapRows); break;
                case AI_STATIONARY: updateStationary(i, playerX, playerY); break;
                case AI_STALKER:    updateStalker(i, playerX, playerY, mapData, mapCols, mapRows); break;
                case AI_AMBUSH:     updateAmbush(i, playerX, playerY, mapData, mapCols, mapRows); break;
            }
        }
    }

    // -------------------------------------------------
    //  IDLE
    // -------------------------------------------------
    private void updateIdle(int id) {
        currentState[id] = STATE_IDLE;
        if (waitTimer[id] <= 0) {
            randomSeed[id] = nextRand(randomSeed[id]);
            if (randomSeed[id] % 60 == 0) entityDir[id] = (randomSeed[id] / 60) % 4;
            waitTimer[id] = 30;
        }
    }

    // -------------------------------------------------
    //  WANDER
    // -------------------------------------------------
    private void updateWander(int id, int[][][] mapData, int mapCols, int mapRows,
                              int playerX, int playerY) {
        if (isHostile[id] && (canSeePlayer[id] || isAlerted[id])) {
            goChasePlayer(id, playerX, playerY, mapData, mapCols, mapRows);
            return;
        }
        currentState[id] = STATE_MOVING;
        if (moveTimer[id] > 0) return;
        randomSeed[id] = nextRand(randomSeed[id]);
        int action = randomSeed[id] % 10;
        if (action < 3) {
            randomSeed[id] = nextRand(randomSeed[id]);
            entityDir[id] = randomSeed[id] % 4;
        } else if (action < 8) {
            int nx = entityX[id], ny = entityY[id];
            switch (entityDir[id]) {
                case 0: ny++; break; case 1: ny--; break;
                case 2: nx--; break; case 3: nx++; break;
            }
            if (canMoveTo(nx, ny, mapData, mapCols, mapRows)) {
                entityX[id] = nx; entityY[id] = ny;
            }
        }
        moveTimer[id] = moveSpeed[id];
    }

    // -------------------------------------------------
    //  PATROL
    // -------------------------------------------------
    private void updatePatrol(int id, int[][][] mapData, int mapCols, int mapRows,
                              int playerX, int playerY) {
        if (isHostile[id] && (canSeePlayer[id] || isAlerted[id])) {
            goChasePlayer(id, playerX, playerY, mapData, mapCols, mapRows);
            return;
        }
        if (currentState[id] == STATE_RETURN) {
            if (loseTimer[id] > 0) {
                moveTowardsTarget(id, mapData, mapCols, mapRows);
                return;
            }
            targetX[id] = homeX[id]; targetY[id] = homeY[id];
            if (entityX[id] == homeX[id] && entityY[id] == homeY[id]) {
                currentState[id] = STATE_MOVING;
                patrolIndex[id] = 0;
            } else { moveTowardsTarget(id, mapData, mapCols, mapRows); return; }
        }
        currentState[id] = STATE_MOVING;
        if (patrolLength[id] == 0) { updateWander(id, mapData, mapCols, mapRows, playerX, playerY); return; }
        if (moveTimer[id] > 0) return;
        int px = patrolPathX[id][patrolIndex[id]];
        int py = patrolPathY[id][patrolIndex[id]];
        if (entityX[id] == px && entityY[id] == py) {
            if (patrolReverse[id]) {
                patrolIndex[id]--;
                if (patrolIndex[id] < 0) { patrolIndex[id] = 1; patrolReverse[id] = false; }
            } else {
                patrolIndex[id]++;
                if (patrolIndex[id] >= patrolLength[id]) {
                    patrolIndex[id] = patrolLength[id] - 2; patrolReverse[id] = true;
                }
            }
            waitTimer[id] = 30;
        } else {
            targetX[id] = px; targetY[id] = py;
            moveTowardsTarget(id, mapData, mapCols, mapRows);
        }
        moveTimer[id] = moveSpeed[id];
    }

    // -------------------------------------------------
    //  CHASE
    // -------------------------------------------------
    private void updateChase(int id, int playerX, int playerY,
                             int[][][] mapData, int mapCols, int mapRows) {
        if (canSeePlayer[id] || isAlerted[id]) {
            goChasePlayer(id, playerX, playerY, mapData, mapCols, mapRows);
        } else {
            if (loseTimer[id] > 0) {
                targetX[id] = lastSeenX[id]; targetY[id] = lastSeenY[id];
                if (entityX[id] == lastSeenX[id] && entityY[id] == lastSeenY[id]) {
                    currentState[id] = STATE_ALERT;
                    randomSeed[id] = nextRand(randomSeed[id]);
                    entityDir[id] = randomSeed[id] % 4;
                }
            } else {
                currentState[id] = STATE_RETURN;
                targetX[id] = homeX[id]; targetY[id] = homeY[id];
                if (entityX[id] == homeX[id] && entityY[id] == homeY[id])
                    currentState[id] = STATE_IDLE;
            }
            if (currentState[id] == STATE_CHASING || currentState[id] == STATE_RETURN)
                moveTowardsTarget(id, mapData, mapCols, mapRows);
        }
    }

    // -------------------------------------------------
    //  FLEE
    // -------------------------------------------------
    private void updateFlee(int id, int playerX, int playerY,
                            int[][][] mapData, int mapCols, int mapRows) {
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);
        if (dist < visionRange[id]) {
            currentState[id] = STATE_MOVING;
            if (moveTimer[id] > 0) return;
            int dx = entityX[id] - playerX, dy = entityY[id] - playerY;
            int nx = entityX[id], ny = entityY[id];
            if (Math.abs(dx) > Math.abs(dy)) { nx += (dx > 0) ? 1 : -1; entityDir[id] = (dx > 0) ? 3 : 2; }
            else { ny += (dy > 0) ? 1 : -1; entityDir[id] = (dy > 0) ? 0 : 1; }
            if (canMoveTo(nx, ny, mapData, mapCols, mapRows)) {
                entityX[id] = nx; entityY[id] = ny;
            } else {
                randomSeed[id] = nextRand(randomSeed[id]);
                int d = randomSeed[id] % 4;
                nx = entityX[id]; ny = entityY[id];
                switch (d) {
                    case 0: ny++; break; case 1: ny--; break;
                    case 2: nx--; break; case 3: nx++; break;
                }
                if (canMoveTo(nx, ny, mapData, mapCols, mapRows)) {
                    entityX[id] = nx; entityY[id] = ny; entityDir[id] = d;
                }
            }
            moveTimer[id] = moveSpeed[id] / 2;
        } else { currentState[id] = STATE_IDLE; }
    }

    // -------------------------------------------------
    //  GUARD
    // -------------------------------------------------
    private void updateGuard(int id, int playerX, int playerY,
                             int[][][] mapData, int mapCols, int mapRows) {
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);
        int homeDist = Math.abs(entityX[id] - homeX[id]) + Math.abs(entityY[id] - homeY[id]);

        if ((canSeePlayer[id] || isAlerted[id]) && isHostile[id]) {
            if (homeDist < visionRange[id] + 2) {
                currentState[id] = STATE_CHASING;
                targetX[id] = playerX; targetY[id] = playerY;
                if (dist <= 1) { currentState[id] = STATE_ATTACK; faceTowards(id, playerX, playerY); }
                else moveTowardsTarget(id, mapData, mapCols, mapRows);
            } else {
                currentState[id] = STATE_RETURN;
                targetX[id] = homeX[id]; targetY[id] = homeY[id];
                moveTowardsTarget(id, mapData, mapCols, mapRows);
            }
        } else {
            if (entityX[id] != homeX[id] || entityY[id] != homeY[id]) {
                currentState[id] = STATE_RETURN;
                targetX[id] = homeX[id]; targetY[id] = homeY[id];
                moveTowardsTarget(id, mapData, mapCols, mapRows);
            } else {
                currentState[id] = STATE_IDLE;
                if (canSeePlayer[id]) faceTowards(id, playerX, playerY);
            }
        }
    }

    // -------------------------------------------------
    //  FOLLOW
    // -------------------------------------------------
    private void updateFollow(int id, int playerX, int playerY,
                              int[][][] mapData, int mapCols, int mapRows) {
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);
        if (dist > 2) {
            currentState[id] = STATE_MOVING;
            targetX[id] = playerX; targetY[id] = playerY;
            moveTowardsTarget(id, mapData, mapCols, mapRows);
        } else {
            currentState[id] = STATE_IDLE;
            faceTowards(id, playerX, playerY);
        }
    }

    // -------------------------------------------------
    //  STATIONARY
    // -------------------------------------------------
    private void updateStationary(int id, int playerX, int playerY) {
        currentState[id] = STATE_IDLE;
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);
        if (dist <= visionRange[id]) faceTowards(id, playerX, playerY);
    }

    // -------------------------------------------------
    //  STALKER  (NEW)
    // -------------------------------------------------
    private void updateStalker(int id, int playerX, int playerY,
                               int[][][] mapData, int mapCols, int mapRows) {
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);

        if (!canSeePlayer[id]) {
            // Not visible — wander back to home
            currentState[id] = STATE_MOVING;
            targetX[id] = homeX[id]; targetY[id] = homeY[id];
            if (entityX[id] != homeX[id] || entityY[id] != homeY[id])
                moveTowardsTarget(id, mapData, mapCols, mapRows);
            else currentState[id] = STATE_IDLE;
            stalkTimer[id] = 0;
            return;
        }

        if (dist <= 1) {
            // Adjacent — attack!
            currentState[id] = STATE_ATTACK;
            faceTowards(id, playerX, playerY);
            return;
        }

        currentState[id] = STATE_STALK;

        if (moveTimer[id] > 0) return;

        // Circle around the player at radius 2-3
        stalkTimer[id]++;
        if (stalkTimer[id] > 60) {
            // Pounce!
            targetX[id] = playerX; targetY[id] = playerY;
            moveTowardsTarget(id, mapData, mapCols, mapRows);
            stalkTimer[id] = 0;
        } else {
            // Strafe perpendicular to player
            stalkAngle[id] = (stalkAngle[id] + 1) % 8;
            int[] offX = {0, 1, 2, 2, 2, 1, 0, -1};
            int[] offY = {-2, -2, -1, 0, 1, 2, 2, 1};
            int strafeX = playerX + offX[stalkAngle[id]];
            int strafeY = playerY + offY[stalkAngle[id]];
            targetX[id] = strafeX; targetY[id] = strafeY;
            moveTowardsTarget(id, mapData, mapCols, mapRows);
        }
        moveTimer[id] = moveSpeed[id];
    }

    // -------------------------------------------------
    //  AMBUSH  (NEW)
    // -------------------------------------------------
    private void updateAmbush(int id, int playerX, int playerY,
                              int[][][] mapData, int mapCols, int mapRows) {
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);

        if (currentState[id] == STATE_AMBUSH) {
            // Wait, just face player if visible
            if (canSeePlayer[id]) faceTowards(id, playerX, playerY);
            // Trigger when close enough
            if (dist <= ambushRange[id]) {
                currentState[id] = STATE_CHASING;
            }
            return;
        }

        // After triggered — chase like normal
        updateChase(id, playerX, playerY, mapData, mapCols, mapRows);
    }

    // -------------------------------------------------
    //  SHARED: GO CHASE PLAYER
    // -------------------------------------------------
    private void goChasePlayer(int id, int playerX, int playerY,
                               int[][][] mapData, int mapCols, int mapRows) {
        currentState[id] = STATE_CHASING;
        targetX[id] = playerX; targetY[id] = playerY;
        lastSeenX[id] = playerX; lastSeenY[id] = playerY;
        loseTimer[id] = 90;
        int dist = Math.abs(entityX[id] - playerX) + Math.abs(entityY[id] - playerY);
        if (dist <= 1) { currentState[id] = STATE_ATTACK; faceTowards(id, playerX, playerY); }
        else moveTowardsTarget(id, mapData, mapCols, mapRows);
    }

    // -------------------------------------------------
    //  MOVEMENT HELPERS
    // -------------------------------------------------
    private void moveTowardsTarget(int id, int[][][] mapData, int mapCols, int mapRows) {
        if (moveTimer[id] > 0) return;
        int dx = targetX[id] - entityX[id];
        int dy = targetY[id] - entityY[id];
        if (dx == 0 && dy == 0) return;
        boolean tryX = Math.abs(dx) >= Math.abs(dy);
        for (int attempt = 0; attempt < 2; attempt++) {
            int nx = entityX[id], ny = entityY[id], nd = entityDir[id];
            if (tryX && dx != 0) { nx = entityX[id] + (dx > 0 ? 1 : -1); nd = dx > 0 ? 3 : 2; }
            else if (dy != 0) { ny = entityY[id] + (dy > 0 ? 1 : -1); nd = dy > 0 ? 0 : 1; }
            if (canMoveTo(nx, ny, mapData, mapCols, mapRows)) {
                entityX[id] = nx; entityY[id] = ny; entityDir[id] = nd;
                break;
            }
            tryX = !tryX; nx = entityX[id]; ny = entityY[id];
        }
        moveTimer[id] = moveSpeed[id];
    }

    private boolean canMoveTo(int x, int y, int[][][] mapData, int mapCols, int mapRows) {
        if (x < 0 || x >= mapCols || y < 0 || y >= mapRows) return false;
        if (TileData.isSolid(mapData[1][y][x])) return false;
        if (TileData.isWater(mapData[0][y][x])) return false;
        for (int i = 0; i < entityCount; i++) {
            if (isActive[i] && entityX[i] == x && entityY[i] == y) return false;
        }
        return true;
    }

    private void faceTowards(int id, int tx, int ty) {
        int dx = tx - entityX[id], dy = ty - entityY[id];
        if (Math.abs(dx) > Math.abs(dy)) entityDir[id] = dx > 0 ? 3 : 2;
        else if (dy != 0) entityDir[id] = dy > 0 ? 0 : 1;
    }

    // -------------------------------------------------
    //  LINE OF SIGHT (Bresenham)
    // -------------------------------------------------
    private boolean checkLineOfSight(int id, int tx, int ty,
                                     int[][][] mapData, int mapCols, int mapRows) {
        int x1 = entityX[id], y1 = entityY[id];
        int dist = Math.abs(x1 - tx) + Math.abs(y1 - ty);
        if (dist > visionRange[id]) return false;

        // Direction cone check
        int dx = tx - x1, dy = ty - y1;
        boolean inCone = false;
        switch (entityDir[id]) {
            case 0: inCone = dy >= 0; break;
            case 1: inCone = dy <= 0; break;
            case 2: inCone = dx <= 0; break;
            case 3: inCone = dx >= 0; break;
        }
        if (dist <= HEARING_RANGE) inCone = true;
        if (!inCone) return false;

        // Bresenham wall check
        int x = x1, y = y1;
        int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int adx = Math.abs(dx), ady = Math.abs(dy);
        int err = adx - ady;
        while (x != tx || y != ty) {
            if (x >= 0 && x < mapCols && y >= 0 && y < mapRows) {
                if (TileData.isSolid(mapData[1][y][x]) && !(x == x1 && y == y1)) return false;
            }
            int e2 = 2 * err;
            if (e2 > -ady) { err -= ady; x += sx; }
            if (e2 <  adx) { err += adx; y += sy; }
        }
        return true;
    }

    // -------------------------------------------------
    //  RANDOM
    // -------------------------------------------------
    private int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    // -------------------------------------------------
    //  QUERY METHODS
    // -------------------------------------------------
    public boolean isPlayerDetected(int id) {
        if (id < 0 || id >= entityCount) return false;
        return canSeePlayer[id] || isAlerted[id];
    }

    public boolean isAttacking(int id) {
        if (id < 0 || id >= entityCount) return false;
        return currentState[id] == STATE_ATTACK;
    }

    public int getState(int id) {
        if (id < 0 || id >= entityCount) return STATE_IDLE;
        return currentState[id];
    }

    public int checkPlayerCollision(int px, int py) {
        for (int i = 0; i < entityCount; i++) {
            if (isActive[i] && entityX[i] == px && entityY[i] == py) return i;
        }
        return -1;
    }

    public int checkPlayerAdjacent(int px, int py) {
        for (int i = 0; i < entityCount; i++) {
            if (!isActive[i]) continue;
            int dist = Math.abs(entityX[i] - px) + Math.abs(entityY[i] - py);
            if (dist == 1 && currentState[i] == STATE_ATTACK) return i;
        }
        return -1;
    }

    /** Set group ID so entities alert each other */
    public void setGroup(int id, int gid) {
        if (id >= 0 && id < entityCount) groupId[id] = gid;
    }

    /** Set ambush trigger range */
    public void setAmbushRange(int id, int range) {
        if (id >= 0 && id < entityCount) ambushRange[id] = range;
    }
}
