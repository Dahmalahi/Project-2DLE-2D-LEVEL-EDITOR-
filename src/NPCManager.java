import java.util.Vector;
import java.io.*;

public class NPCManager {

    // -------------------------------------------------
    //  NPC TYPES
    // -------------------------------------------------
    public static final int NPC_FRIENDLY  = 0;
    public static final int NPC_SHOPKEEP  = 1;
    public static final int NPC_QUEST     = 2;
    public static final int NPC_ENEMY     = 3;
    public static final int NPC_BOSS      = 4;
    public static final int NPC_INN       = 5;   // NEW
    public static final int NPC_TRAINER   = 6;   // NEW: teaches skills
    public static final int NPC_GUARD     = 7;   // NEW: town guard
    public static final int NPC_ARTISAN   = 8;   // NEW: crafting NPC

    public static final String[] NPC_TYPE_NAMES = {
        "Friendly", "Shopkeeper", "Quest", "Enemy", "Boss",
        "Innkeeper", "Trainer", "Guard", "Artisan"
    };

    // -------------------------------------------------
    //  NPC MOOD
    // -------------------------------------------------
    public static final int MOOD_NEUTRAL  = 0;
    public static final int MOOD_HAPPY    = 1;
    public static final int MOOD_ANGRY    = 2;
    public static final int MOOD_AFRAID   = 3;
    public static final int MOOD_EXCITED  = 4;
    public static final int MOOD_SAD      = 5;

    // -------------------------------------------------
    //  MOVEMENT BEHAVIOR
    // -------------------------------------------------
    public static final int MOVE_STATIC   = 0;  // Stand still
    public static final int MOVE_WANDER   = 1;  // Random wander in radius
    public static final int MOVE_PATROL   = 2;  // Follow waypoints
    public static final int MOVE_FOLLOW   = 3;  // Follow player
    public static final int MOVE_SCHEDULE = 4;  // Time-based route

    // -------------------------------------------------
    //  NPC DATA STRUCTURE
    // -------------------------------------------------
    public static final int MAX_NPCS = 128;
    public static final int MAX_WAYPOINTS = 6;
    public static final int MAX_SCHEDULE_SLOTS = 4;  // per NPC day schedule

    public int[] npcX;
    public int[] npcY;
    public int[] npcType;
    public int[] npcDir;         // 0=down,1=up,2=left,3=right
    public int[] npcSpriteId;
    public int[] npcDialogueId;
    public boolean[] npcActive;
    public int[] npcMapId;        // Which map the NPC lives on

    // Movement
    public int[] npcMoveBehavior;
    public int[] npcHomeX;
    public int[] npcHomeY;
    public int[] npcWanderRadius;
    public int[] npcMoveTimer;
    public int[] npcMoveSpeed;    // frames between moves
    public int[] npcMoveDir;      // last attempted direction
    public int[] npcRandomSeed;

    // Patrol waypoints
    public int[][] npcWaypointX;
    public int[][] npcWaypointY;
    public int[] npcWaypointCount;
    public int[] npcWaypointIndex;
    public boolean[] npcWaypointReverse;

    // Schedule: [npcId][slot] = {periodStart, destX, destY, faceDir}
    public int[][] npcSchedulePeriod;  // period 0-6 of day (GameConfig)
    public int[][] npcScheduleX;
    public int[][] npcScheduleY;
    public int[] npcScheduleCount;
    public int[] npcCurrentSchedule;  // which schedule slot active

    // Personality
    public int[] npcMood;
    public int[] npcMoodTimer;
    public int[] npcRelation;     // -100 to 100 relationship with player
    public int[] npcShopId;       // ShopSystem shop index (-1 if not shopkeeper)

    // Interaction flags
    public boolean[] npcTalkedTo;
    public int[] npcInteractScript;  // EventSystem script ID to run on talk

    // Animation
    public int[] npcAnimFrame;
    public int[] npcAnimTimer;
    public boolean[] npcIsMovingAnim;

    public int npcCount;

    // Dialogues: each entry is a String[] of lines
    private Vector dialogues;

    // Random seed for wander AI
    private int globalSeed;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public NPCManager() {
        npcX = new int[MAX_NPCS];
        npcY = new int[MAX_NPCS];
        npcType = new int[MAX_NPCS];
        npcDir = new int[MAX_NPCS];
        npcSpriteId = new int[MAX_NPCS];
        npcDialogueId = new int[MAX_NPCS];
        npcActive = new boolean[MAX_NPCS];
        npcMapId = new int[MAX_NPCS];

        npcMoveBehavior = new int[MAX_NPCS];
        npcHomeX = new int[MAX_NPCS];
        npcHomeY = new int[MAX_NPCS];
        npcWanderRadius = new int[MAX_NPCS];
        npcMoveTimer = new int[MAX_NPCS];
        npcMoveSpeed = new int[MAX_NPCS];
        npcMoveDir = new int[MAX_NPCS];
        npcRandomSeed = new int[MAX_NPCS];

        npcWaypointX = new int[MAX_NPCS][MAX_WAYPOINTS];
        npcWaypointY = new int[MAX_NPCS][MAX_WAYPOINTS];
        npcWaypointCount = new int[MAX_NPCS];
        npcWaypointIndex = new int[MAX_NPCS];
        npcWaypointReverse = new boolean[MAX_NPCS];

        npcSchedulePeriod = new int[MAX_NPCS][MAX_SCHEDULE_SLOTS];
        npcScheduleX = new int[MAX_NPCS][MAX_SCHEDULE_SLOTS];
        npcScheduleY = new int[MAX_NPCS][MAX_SCHEDULE_SLOTS];
        npcScheduleCount = new int[MAX_NPCS];
        npcCurrentSchedule = new int[MAX_NPCS];

        npcMood = new int[MAX_NPCS];
        npcMoodTimer = new int[MAX_NPCS];
        npcRelation = new int[MAX_NPCS];
        npcShopId = new int[MAX_NPCS];

        npcTalkedTo = new boolean[MAX_NPCS];
        npcInteractScript = new int[MAX_NPCS];

        npcAnimFrame = new int[MAX_NPCS];
        npcAnimTimer = new int[MAX_NPCS];
        npcIsMovingAnim = new boolean[MAX_NPCS];

        npcCount = 0;
        dialogues = new Vector();
        globalSeed = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
    }

    // -------------------------------------------------
    //  NPC MANAGEMENT
    // -------------------------------------------------
    public int addNPC(int x, int y, int type) {
        if (npcCount >= MAX_NPCS) return -1;

        int id = npcCount;
        npcX[id] = x;
        npcY[id] = y;
        npcType[id] = type;
        npcDir[id] = 0;
        npcSpriteId[id] = type;
        npcDialogueId[id] = -1;
        npcActive[id] = true;
        npcMapId[id] = 0;

        npcMoveBehavior[id] = MOVE_STATIC;
        npcHomeX[id] = x;
        npcHomeY[id] = y;
        npcWanderRadius[id] = GameConfig.NPC_WANDER_RANGE;
        npcMoveTimer[id] = 0;
        npcMoveSpeed[id] = 20;
        npcMoveDir[id] = 0;
        npcRandomSeed[id] = globalSeed + id * 7919;

        npcWaypointCount[id] = 0;
        npcWaypointIndex[id] = 0;
        npcWaypointReverse[id] = false;
        npcScheduleCount[id] = 0;
        npcCurrentSchedule[id] = -1;

        npcMood[id] = MOOD_NEUTRAL;
        npcMoodTimer[id] = 0;
        npcRelation[id] = 0;
        npcShopId[id] = -1;

        npcTalkedTo[id] = false;
        npcInteractScript[id] = -1;

        npcAnimFrame[id] = 0;
        npcAnimTimer[id] = 0;
        npcIsMovingAnim[id] = false;

        npcCount++;
        return id;
    }

    // Quick setup helpers
    public int addShopNPC(int x, int y, int shopId) {
        int id = addNPC(x, y, NPC_SHOPKEEP);
        if (id >= 0) npcShopId[id] = shopId;
        return id;
    }

    public int addPatrolNPC(int x, int y, int type, int[] wx, int[] wy, int len) {
        int id = addNPC(x, y, type);
        if (id >= 0) {
            setWaypoints(id, wx, wy, len);
            npcMoveBehavior[id] = MOVE_PATROL;
        }
        return id;
    }

    public int addWanderNPC(int x, int y, int type, int radius) {
        int id = addNPC(x, y, type);
        if (id >= 0) {
            npcMoveBehavior[id] = MOVE_WANDER;
            npcWanderRadius[id] = radius;
        }
        return id;
    }

    public void removeNPC(int id) {
        if (id < 0 || id >= npcCount) return;
        for (int i = id; i < npcCount - 1; i++) {
            copyNPC(i + 1, i);
        }
        npcCount--;
    }

    private void copyNPC(int from, int to) {
        npcX[to] = npcX[from];
        npcY[to] = npcY[from];
        npcType[to] = npcType[from];
        npcDir[to] = npcDir[from];
        npcSpriteId[to] = npcSpriteId[from];
        npcDialogueId[to] = npcDialogueId[from];
        npcActive[to] = npcActive[from];
        npcMapId[to] = npcMapId[from];
        npcMoveBehavior[to] = npcMoveBehavior[from];
        npcHomeX[to] = npcHomeX[from];
        npcHomeY[to] = npcHomeY[from];
        npcWanderRadius[to] = npcWanderRadius[from];
        npcMoveTimer[to] = npcMoveTimer[from];
        npcMoveSpeed[to] = npcMoveSpeed[from];
        npcMood[to] = npcMood[from];
        npcRelation[to] = npcRelation[from];
        npcShopId[to] = npcShopId[from];
        npcTalkedTo[to] = npcTalkedTo[from];
        npcInteractScript[to] = npcInteractScript[from];
        npcRandomSeed[to] = npcRandomSeed[from];
        npcWaypointCount[to] = npcWaypointCount[from];
        npcWaypointIndex[to] = npcWaypointIndex[from];
        npcWaypointReverse[to] = npcWaypointReverse[from];
        for (int i = 0; i < MAX_WAYPOINTS; i++) {
            npcWaypointX[to][i] = npcWaypointX[from][i];
            npcWaypointY[to][i] = npcWaypointY[from][i];
        }
        npcScheduleCount[to] = npcScheduleCount[from];
        npcCurrentSchedule[to] = npcCurrentSchedule[from];
        for (int i = 0; i < MAX_SCHEDULE_SLOTS; i++) {
            npcSchedulePeriod[to][i] = npcSchedulePeriod[from][i];
            npcScheduleX[to][i] = npcScheduleX[from][i];
            npcScheduleY[to][i] = npcScheduleY[from][i];
        }
    }

    public int findNPCAt(int x, int y) {
        for (int i = 0; i < npcCount; i++) {
            if (npcActive[i] && npcX[i] == x && npcY[i] == y) {
                return i;
            }
        }
        return -1;
    }

    public int findNPCAt(int x, int y, int mapId) {
        for (int i = 0; i < npcCount; i++) {
            if (npcActive[i] && npcX[i] == x && npcY[i] == y && npcMapId[i] == mapId) {
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------
    //  WAYPOINTS & SCHEDULE
    // -------------------------------------------------
    public void setWaypoints(int id, int[] wx, int[] wy, int len) {
        if (id < 0 || id >= npcCount) return;
        int count = Math.min(len, MAX_WAYPOINTS);
        npcWaypointCount[id] = count;
        for (int i = 0; i < count; i++) {
            npcWaypointX[id][i] = wx[i];
            npcWaypointY[id][i] = wy[i];
        }
    }

    public void addScheduleEntry(int id, int dayPeriod, int destX, int destY) {
        if (id < 0 || id >= npcCount) return;
        int s = npcScheduleCount[id];
        if (s >= MAX_SCHEDULE_SLOTS) return;
        npcSchedulePeriod[id][s] = dayPeriod;
        npcScheduleX[id][s] = destX;
        npcScheduleY[id][s] = destY;
        npcScheduleCount[id]++;
        npcMoveBehavior[id] = MOVE_SCHEDULE;
    }

    // -------------------------------------------------
    //  UPDATE (call every game frame)
    // -------------------------------------------------
    public void update(int[][][] mapData, int mapCols, int mapRows, 
                       int dayPeriod, int playerX, int playerY) {
        for (int i = 0; i < npcCount; i++) {
            if (!npcActive[i]) continue;

            // Animate
            if (npcIsMovingAnim[i]) {
                npcAnimTimer[i]++;
                if (npcAnimTimer[i] >= 8) {
                    npcAnimFrame[i] = (npcAnimFrame[i] + 1) % 4;
                    npcAnimTimer[i] = 0;
                }
            }

            // Mood timer
            if (npcMoodTimer[i] > 0) {
                npcMoodTimer[i]--;
                if (npcMoodTimer[i] == 0) npcMood[i] = MOOD_NEUTRAL;
            }

            // Move timer
            if (npcMoveTimer[i] > 0) {
                npcMoveTimer[i]--;
                continue;
            }

            // Schedule override
            if (npcMoveBehavior[i] == MOVE_SCHEDULE) {
                updateScheduleMove(i, dayPeriod, mapData, mapCols, mapRows);
                continue;
            }

            switch (npcMoveBehavior[i]) {
                case MOVE_WANDER:
                    updateWander(i, mapData, mapCols, mapRows);
                    break;
                case MOVE_PATROL:
                    updatePatrol(i, mapData, mapCols, mapRows);
                    break;
                case MOVE_FOLLOW:
                    updateFollow(i, playerX, playerY, mapData, mapCols, mapRows);
                    break;
                default:
                    npcIsMovingAnim[i] = false;
                    break;
            }
        }
    }

    // -------------------------------------------------
    //  WANDER AI
    // -------------------------------------------------
    private void updateWander(int id, int[][][] mapData, int mapCols, int mapRows) {
        npcRandomSeed[id] = nextRand(npcRandomSeed[id]);
        int action = npcRandomSeed[id] % 10;

        if (action < 4) {
            // Stand still this tick
            npcIsMovingAnim[id] = false;
            npcMoveTimer[id] = npcMoveSpeed[id] + (npcRandomSeed[id] % 20);
            return;
        }

        // Change direction randomly
        npcRandomSeed[id] = nextRand(npcRandomSeed[id]);
        int dir = npcRandomSeed[id] % 4;
        int nx = npcX[id], ny = npcY[id];
        switch (dir) {
            case 0: ny++; break;
            case 1: ny--; break;
            case 2: nx--; break;
            case 3: nx++; break;
        }

        // Stay within wander radius of home
        int dxHome = nx - npcHomeX[id];
        int dyHome = ny - npcHomeY[id];
        int distHome = Math.abs(dxHome) + Math.abs(dyHome);

        if (distHome <= npcWanderRadius[id] && canMoveTo(nx, ny, mapData, mapCols, mapRows, id)) {
            npcX[id] = nx;
            npcY[id] = ny;
            npcDir[id] = dir;
            npcIsMovingAnim[id] = true;
        } else {
            // Try to drift back home
            int hx = npcX[id] + (npcHomeX[id] > npcX[id] ? 1 : npcHomeX[id] < npcX[id] ? -1 : 0);
            int hy = npcY[id] + (npcHomeY[id] > npcY[id] ? 1 : npcHomeY[id] < npcY[id] ? -1 : 0);
            if (hx != npcX[id] && canMoveTo(hx, npcY[id], mapData, mapCols, mapRows, id)) {
                npcDir[id] = (hx > npcX[id]) ? 3 : 2;
                npcX[id] = hx;
            } else if (hy != npcY[id] && canMoveTo(npcX[id], hy, mapData, mapCols, mapRows, id)) {
                npcDir[id] = (hy > npcY[id]) ? 0 : 1;
                npcY[id] = hy;
            }
            npcIsMovingAnim[id] = false;
        }
        npcMoveTimer[id] = npcMoveSpeed[id];
    }

    // -------------------------------------------------
    //  PATROL AI
    // -------------------------------------------------
    private void updatePatrol(int id, int[][][] mapData, int mapCols, int mapRows) {
        if (npcWaypointCount[id] < 2) {
            updateWander(id, mapData, mapCols, mapRows);
            return;
        }

        int wpIdx = npcWaypointIndex[id];
        int wpX = npcWaypointX[id][wpIdx];
        int wpY = npcWaypointY[id][wpIdx];

        if (npcX[id] == wpX && npcY[id] == wpY) {
            // Reached waypoint — advance
            if (npcWaypointReverse[id]) {
                npcWaypointIndex[id]--;
                if (npcWaypointIndex[id] < 0) {
                    npcWaypointIndex[id] = 1;
                    npcWaypointReverse[id] = false;
                }
            } else {
                npcWaypointIndex[id]++;
                if (npcWaypointIndex[id] >= npcWaypointCount[id]) {
                    npcWaypointIndex[id] = npcWaypointCount[id] - 2;
                    npcWaypointReverse[id] = true;
                }
            }
            npcMoveTimer[id] = npcMoveSpeed[id] * 2; // Brief pause at waypoint
            npcIsMovingAnim[id] = false;
            return;
        }

        stepTowards(id, wpX, wpY, mapData, mapCols, mapRows);
        npcMoveTimer[id] = npcMoveSpeed[id];
    }

    // -------------------------------------------------
    //  FOLLOW AI
    // -------------------------------------------------
    private void updateFollow(int id, int playerX, int playerY,
                              int[][][] mapData, int mapCols, int mapRows) {
        int dist = Math.abs(npcX[id] - playerX) + Math.abs(npcY[id] - playerY);
        if (dist > 2) {
            stepTowards(id, playerX, playerY, mapData, mapCols, mapRows);
            npcMoveTimer[id] = npcMoveSpeed[id];
            npcIsMovingAnim[id] = true;
        } else {
            npcIsMovingAnim[id] = false;
            // Face player
            int dx = playerX - npcX[id], dy = playerY - npcY[id];
            if (Math.abs(dx) > Math.abs(dy)) npcDir[id] = (dx > 0) ? 3 : 2;
            else if (dy != 0) npcDir[id] = (dy > 0) ? 0 : 1;
        }
    }

    // -------------------------------------------------
    //  SCHEDULE AI
    // -------------------------------------------------
    private void updateScheduleMove(int id, int dayPeriod, 
                                    int[][][] mapData, int mapCols, int mapRows) {
        // Find active schedule slot
        int slot = -1;
        for (int s = npcScheduleCount[id] - 1; s >= 0; s--) {
            if (dayPeriod >= npcSchedulePeriod[id][s]) {
                slot = s;
                break;
            }
        }

        if (slot < 0) slot = 0;

        if (slot != npcCurrentSchedule[id]) {
            // Period changed — start moving to new destination
            npcCurrentSchedule[id] = slot;
        }

        int destX = npcScheduleX[id][slot];
        int destY = npcScheduleY[id][slot];

        if (npcX[id] == destX && npcY[id] == destY) {
            npcIsMovingAnim[id] = false;
            return;
        }

        stepTowards(id, destX, destY, mapData, mapCols, mapRows);
        npcMoveTimer[id] = npcMoveSpeed[id];
    }

    // -------------------------------------------------
    //  MOVEMENT UTILITY
    // -------------------------------------------------
    private void stepTowards(int id, int targetX, int targetY,
                             int[][][] mapData, int mapCols, int mapRows) {
        int dx = targetX - npcX[id];
        int dy = targetY - npcY[id];

        boolean tryX = Math.abs(dx) >= Math.abs(dy);

        for (int attempt = 0; attempt < 2; attempt++) {
            int nx = npcX[id], ny = npcY[id], dir = npcDir[id];

            if (tryX && dx != 0) {
                nx = npcX[id] + (dx > 0 ? 1 : -1);
                dir = dx > 0 ? 3 : 2;
            } else if (dy != 0) {
                ny = npcY[id] + (dy > 0 ? 1 : -1);
                dir = dy > 0 ? 0 : 1;
            }

            if (canMoveTo(nx, ny, mapData, mapCols, mapRows, id)) {
                npcX[id] = nx;
                npcY[id] = ny;
                npcDir[id] = dir;
                npcIsMovingAnim[id] = true;
                return;
            }
            tryX = !tryX;
        }
        npcIsMovingAnim[id] = false;
    }

    private boolean canMoveTo(int x, int y, int[][][] mapData, 
                              int mapCols, int mapRows, int excludeId) {
        if (x < 0 || x >= mapCols || y < 0 || y >= mapRows) return false;
        if (TileData.isSolid(mapData[1][y][x])) return false;
        for (int i = 0; i < npcCount; i++) {
            if (i != excludeId && npcActive[i] && npcX[i] == x && npcY[i] == y) return false;
        }
        return true;
    }

    // -------------------------------------------------
    //  RELATION & MOOD
    // -------------------------------------------------
    public void changeRelation(int npcId, int delta) {
        if (npcId < 0 || npcId >= npcCount) return;
        npcRelation[npcId] = Math.max(-100, Math.min(100, npcRelation[npcId] + delta));

        // Update mood based on relation
        if (npcRelation[npcId] >= 50) {
            setMood(npcId, MOOD_HAPPY, 300);
        } else if (npcRelation[npcId] <= -50) {
            setMood(npcId, MOOD_ANGRY, 300);
        }
    }

    public void setMood(int id, int mood, int duration) {
        if (id < 0 || id >= npcCount) return;
        npcMood[id] = mood;
        npcMoodTimer[id] = duration;
    }

    public String getMoodIcon(int id) {
        if (id < 0 || id >= npcCount) return "";
        switch (npcMood[id]) {
            case MOOD_HAPPY:   return ":)";
            case MOOD_ANGRY:   return ">:(";
            case MOOD_AFRAID:  return "D:";
            case MOOD_EXCITED: return "!!";
            case MOOD_SAD:     return ":(";
            default:           return "";
        }
    }

    // Get dialogue index based on relation + talked-to state
    public int getEffectiveDialogue(int npcId) {
        if (npcId < 0 || npcId >= npcCount) return -1;
        int baseId = npcDialogueId[npcId];
        if (baseId < 0) return -1;

        // If high relation and there is a bonus dialogue (baseId+1 exists), use it
        if (npcRelation[npcId] >= 50 && baseId + 1 < dialogues.size()) {
            return baseId + 1;
        }
        // If talked to before, use follow-up (baseId+2 if exists)
        if (npcTalkedTo[npcId] && baseId + 2 < dialogues.size()) {
            return baseId + 2;
        }
        return baseId;
    }

    // -------------------------------------------------
    //  DIALOGUE MANAGEMENT
    // -------------------------------------------------
    public int addDialogue(String[] lines) {
        int id = dialogues.size();
        dialogues.addElement(lines);
        return id;
    }

    public String[] getDialogue(int id) {
        if (id < 0 || id >= dialogues.size()) return null;
        return (String[]) dialogues.elementAt(id);
    }

    public void setDialogue(int id, String[] lines) {
        if (id < 0 || id >= dialogues.size()) return;
        dialogues.setElementAt(lines, id);
    }

    public int getDialogueCount() { return dialogues.size(); }

    public void clearDialogues() { dialogues.removeAllElements(); }

    // -------------------------------------------------
    //  NPC SETTERS
    // -------------------------------------------------
    public void moveNPC(int id, int x, int y) {
        if (id < 0 || id >= npcCount) return;
        npcX[id] = x;
        npcY[id] = y;
    }

    public void setNPCType(int id, int type) {
        if (id < 0 || id >= npcCount) return;
        npcType[id] = type;
    }

    public void setNPCDialogue(int id, int dialogueId) {
        if (id < 0 || id >= npcCount) return;
        npcDialogueId[id] = dialogueId;
    }

    public void setNPCMovement(int id, int behavior, int speed) {
        if (id < 0 || id >= npcCount) return;
        npcMoveBehavior[id] = behavior;
        npcMoveSpeed[id] = speed;
    }

    // -------------------------------------------------
    //  DRAW HELPER
    // -------------------------------------------------
    public void drawNPC(javax.microedition.lcdui.Graphics g, int id,
                        int screenX, int screenY, int tileSize) {
        if (id < 0 || id >= npcCount || !npcActive[id]) return;

        // Color by type
        int[] typeColors = {
            0x4488FF, 0xFFAA00, 0xFF8800, 0xFF3333, 0xFF0000,
            0xAA44FF, 0x00CC88, 0x8888FF, 0xCC8844
        };
        int t = Math.min(npcType[id], typeColors.length - 1);
        g.setColor(typeColors[t]);
        g.fillRect(screenX + 2, screenY + 2, tileSize - 4, tileSize - 4);

        // Direction dot
        g.setColor(0xFFFFFF);
        int cx = screenX + tileSize / 2;
        int cy = screenY + tileSize / 2;
        switch (npcDir[id]) {
            case 0: g.fillRect(cx - 2, cy + 3, 4, 3); break;
            case 1: g.fillRect(cx - 2, cy - 6, 4, 3); break;
            case 2: g.fillRect(cx - 6, cy - 1, 3, 3); break;
            case 3: g.fillRect(cx + 3, cy - 1, 3, 3); break;
        }

        // Mood icon above head
        String icon = getMoodIcon(id);
        if (icon.length() > 0) {
            g.setColor(0xFFFF00);
            javax.microedition.lcdui.Font f =
                javax.microedition.lcdui.Font.getFont(
                    javax.microedition.lcdui.Font.FACE_SYSTEM,
                    javax.microedition.lcdui.Font.STYLE_BOLD,
                    javax.microedition.lcdui.Font.SIZE_SMALL);
            g.setFont(f);
            g.drawString(icon, screenX + tileSize / 2, screenY - 10,
                         javax.microedition.lcdui.Graphics.TOP |
                         javax.microedition.lcdui.Graphics.HCENTER);
        }

        // Shopkeeper bag icon
        if (npcType[id] == NPC_SHOPKEEP) {
            g.setColor(0xFFD700);
            g.fillRect(screenX + tileSize - 7, screenY + 1, 6, 6);
        }
    }

    // -------------------------------------------------
    //  RANDOM HELPER
    // -------------------------------------------------
    private int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    // -------------------------------------------------
    //  SAVE / LOAD
    // -------------------------------------------------
    public byte[] saveToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(npcCount);
        for (int i = 0; i < npcCount; i++) {
            dos.writeInt(npcX[i]);
            dos.writeInt(npcY[i]);
            dos.writeInt(npcType[i]);
            dos.writeInt(npcDir[i]);
            dos.writeInt(npcSpriteId[i]);
            dos.writeInt(npcDialogueId[i]);
            dos.writeBoolean(npcActive[i]);
            dos.writeInt(npcMapId[i]);
            dos.writeInt(npcMoveBehavior[i]);
            dos.writeInt(npcHomeX[i]);
            dos.writeInt(npcHomeY[i]);
            dos.writeInt(npcWanderRadius[i]);
            dos.writeInt(npcMoveSpeed[i]);
            dos.writeInt(npcMood[i]);
            dos.writeInt(npcRelation[i]);
            dos.writeInt(npcShopId[i]);
            dos.writeBoolean(npcTalkedTo[i]);
            dos.writeInt(npcInteractScript[i]);

            dos.writeInt(npcWaypointCount[i]);
            for (int j = 0; j < npcWaypointCount[i]; j++) {
                dos.writeInt(npcWaypointX[i][j]);
                dos.writeInt(npcWaypointY[i][j]);
            }

            dos.writeInt(npcScheduleCount[i]);
            for (int j = 0; j < npcScheduleCount[i]; j++) {
                dos.writeInt(npcSchedulePeriod[i][j]);
                dos.writeInt(npcScheduleX[i][j]);
                dos.writeInt(npcScheduleY[i][j]);
            }
        }

        dos.writeInt(dialogues.size());
        for (int i = 0; i < dialogues.size(); i++) {
            String[] lines = (String[]) dialogues.elementAt(i);
            dos.writeInt(lines.length);
            for (int j = 0; j < lines.length; j++) {
                dos.writeUTF(lines[j]);
            }
        }

        dos.flush();
        return baos.toByteArray();
    }

    public void loadFromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        npcCount = dis.readInt();
        if (npcCount > MAX_NPCS) npcCount = MAX_NPCS;

        for (int i = 0; i < npcCount; i++) {
            npcX[i] = dis.readInt();
            npcY[i] = dis.readInt();
            npcType[i] = dis.readInt();
            npcDir[i] = dis.readInt();
            npcSpriteId[i] = dis.readInt();
            npcDialogueId[i] = dis.readInt();
            npcActive[i] = dis.readBoolean();
            try {
                npcMapId[i] = dis.readInt();
                npcMoveBehavior[i] = dis.readInt();
                npcHomeX[i] = dis.readInt();
                npcHomeY[i] = dis.readInt();
                npcWanderRadius[i] = dis.readInt();
                npcMoveSpeed[i] = dis.readInt();
                npcMood[i] = dis.readInt();
                npcRelation[i] = dis.readInt();
                npcShopId[i] = dis.readInt();
                npcTalkedTo[i] = dis.readBoolean();
                npcInteractScript[i] = dis.readInt();

                int wpc = dis.readInt();
                npcWaypointCount[i] = wpc;
                for (int j = 0; j < wpc; j++) {
                    npcWaypointX[i][j] = dis.readInt();
                    npcWaypointY[i][j] = dis.readInt();
                }

                int sc = dis.readInt();
                npcScheduleCount[i] = sc;
                for (int j = 0; j < sc; j++) {
                    npcSchedulePeriod[i][j] = dis.readInt();
                    npcScheduleX[i][j] = dis.readInt();
                    npcScheduleY[i][j] = dis.readInt();
                }
            } catch (IOException e) {
                // Older save — fill defaults
                npcMoveBehavior[i] = MOVE_STATIC;
                npcHomeX[i] = npcX[i];
                npcHomeY[i] = npcY[i];
                npcWanderRadius[i] = GameConfig.NPC_WANDER_RANGE;
                npcMoveSpeed[i] = 20;
                npcShopId[i] = -1;
                npcInteractScript[i] = -1;
            }
        }

        dialogues.removeAllElements();
        int dCount = dis.readInt();
        for (int i = 0; i < dCount; i++) {
            int lineCount = dis.readInt();
            String[] lines = new String[lineCount];
            for (int j = 0; j < lineCount; j++) {
                lines[j] = dis.readUTF();
            }
            dialogues.addElement(lines);
        }
    }

    // -------------------------------------------------
    //  TEXT EXPORT / IMPORT
    // -------------------------------------------------
    public String exportDialoguesToText() {
        StringBuffer sb = new StringBuffer();
        sb.append("# NPC Dialogues\n# [DIALOGUE_ID]\n# Line1\n# ...\n\n");
        for (int i = 0; i < dialogues.size(); i++) {
            sb.append("[DIALOGUE_" + i + "]\n");
            String[] lines = (String[]) dialogues.elementAt(i);
            for (int j = 0; j < lines.length; j++) {
                sb.append(lines[j] + "\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public void importDialoguesFromText(String text) {
        dialogues.removeAllElements();
        Vector currentLines = new Vector();
        int start = 0, end;
        while ((end = text.indexOf('\n', start)) != -1 || start < text.length()) {
            if (end == -1) end = text.length();
            String line = text.substring(start, end).trim();
            start = end + 1;
            if (line.startsWith("#") || line.length() == 0) continue;
            if (line.startsWith("[DIALOGUE_")) {
                if (currentLines.size() > 0) {
                    String[] arr = new String[currentLines.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = (String) currentLines.elementAt(i);
                    dialogues.addElement(arr);
                    currentLines.removeAllElements();
                }
            } else {
                currentLines.addElement(line);
            }
            if (start >= text.length()) break;
        }
        if (currentLines.size() > 0) {
            String[] arr = new String[currentLines.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = (String) currentLines.elementAt(i);
            dialogues.addElement(arr);
        }
    }

    public void clearAll() {
        npcCount = 0;
        dialogues.removeAllElements();
    }
}
