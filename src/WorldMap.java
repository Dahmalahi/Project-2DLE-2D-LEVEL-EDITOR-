import java.io.*;

public class WorldMap {

    // -------------------------------------------------
    //  CONSTANTS
    // -------------------------------------------------
    public static final int MAX_MAPS        = 50;
    public static final int MAX_CONNECTIONS = 4;      // N,S,E,W
    public static final int MAX_TELEPORTS   = 10;
    public static final int MAX_SAVEPOINTS  = 5;
    public static final int MAX_NOTES       = 8;      // NEW: player map notes per map
    public static final int MAX_FAST_TRAVEL = 20;     // NEW: global fast-travel nodes

    // Region types
    public static final int REGION_FIELD    = 0;
    public static final int REGION_FOREST   = 1;
    public static final int REGION_MOUNTAIN = 2;
    public static final int REGION_DESERT   = 3;
    public static final int REGION_SNOW     = 4;
    public static final int REGION_DUNGEON  = 5;
    public static final int REGION_TOWN     = 6;
    public static final int REGION_CASTLE   = 7;
    public static final int REGION_CAVE     = 8;
    public static final int REGION_WATER    = 9;
    public static final int REGION_SWAMP    = 10;     // NEW
    public static final int REGION_VOLCANO  = 11;     // NEW

    // Connection directions
    public static final int DIR_NORTH = 0;
    public static final int DIR_SOUTH = 1;
    public static final int DIR_EAST  = 2;
    public static final int DIR_WEST  = 3;

    // -------------------------------------------------
    //  MAP METADATA
    // -------------------------------------------------
    public String[] mapName;
    public int[] mapWidth;
    public int[] mapHeight;
    public int[] mapRegion;
    public int[] mapLevel;
    public int[] mapEncounterRate;
    public int[] mapBGM;
    public int[] mapWeather;
    public int[] mapOverlay;
    public boolean[] mapDiscovered;      // NEW: fog on world map
    public boolean[] mapVisited;         // NEW: player has been here

    // Connections [mapId][direction] = targetMapId
    public int[][] mapConnections;
    public int[][] connectionX;
    public int[][] connectionY;

    // Teleports
    public int[][] teleportX;
    public int[][] teleportY;
    public int[][] teleportTargetMap;
    public int[][] teleportTargetX;
    public int[][] teleportTargetY;
    public int[] teleportCount;

    // Save points
    public int[][] savePointX;
    public int[][] savePointY;
    public int[] savePointCount;

    // Map notes (player-placed markers)
    public int[][] noteX;
    public int[][] noteY;
    public String[][] noteText;
    public int[] noteCount;

    // Fast-travel nodes
    public int[] fastTravelMapId;
    public int[] fastTravelX;
    public int[] fastTravelY;
    public String[] fastTravelName;
    public boolean[] fastTravelUnlocked;
    public int fastTravelCount;

    // Map count
    public int mapCount;

    // Current map
    public int currentMapId;
    public int[][][] currentMapData;
    public int currentWidth;
    public int currentHeight;

    // Scroll offset (for camera)
    public int scrollX;
    public int scrollY;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public WorldMap() {
        mapName        = new String[MAX_MAPS];
        mapWidth       = new int[MAX_MAPS];
        mapHeight      = new int[MAX_MAPS];
        mapRegion      = new int[MAX_MAPS];
        mapLevel       = new int[MAX_MAPS];
        mapEncounterRate = new int[MAX_MAPS];
        mapBGM         = new int[MAX_MAPS];
        mapWeather     = new int[MAX_MAPS];
        mapOverlay     = new int[MAX_MAPS];
        mapDiscovered  = new boolean[MAX_MAPS];
        mapVisited     = new boolean[MAX_MAPS];

        mapConnections = new int[MAX_MAPS][4];
        connectionX    = new int[MAX_MAPS][4];
        connectionY    = new int[MAX_MAPS][4];

        teleportX        = new int[MAX_MAPS][MAX_TELEPORTS];
        teleportY        = new int[MAX_MAPS][MAX_TELEPORTS];
        teleportTargetMap= new int[MAX_MAPS][MAX_TELEPORTS];
        teleportTargetX  = new int[MAX_MAPS][MAX_TELEPORTS];
        teleportTargetY  = new int[MAX_MAPS][MAX_TELEPORTS];
        teleportCount    = new int[MAX_MAPS];

        savePointX     = new int[MAX_MAPS][MAX_SAVEPOINTS];
        savePointY     = new int[MAX_MAPS][MAX_SAVEPOINTS];
        savePointCount = new int[MAX_MAPS];

        noteX    = new int[MAX_MAPS][MAX_NOTES];
        noteY    = new int[MAX_MAPS][MAX_NOTES];
        noteText = new String[MAX_MAPS][MAX_NOTES];
        noteCount = new int[MAX_MAPS];

        fastTravelMapId  = new int[MAX_FAST_TRAVEL];
        fastTravelX      = new int[MAX_FAST_TRAVEL];
        fastTravelY      = new int[MAX_FAST_TRAVEL];
        fastTravelName   = new String[MAX_FAST_TRAVEL];
        fastTravelUnlocked = new boolean[MAX_FAST_TRAVEL];
        fastTravelCount  = 0;

        mapCount      = 0;
        currentMapId  = -1;

        for (int i = 0; i < MAX_MAPS; i++) {
            mapName[i] = "";
            for (int j = 0; j < 4; j++) mapConnections[i][j] = -1;
            for (int j = 0; j < MAX_NOTES; j++) noteText[i][j] = "";
        }
        for (int i = 0; i < MAX_FAST_TRAVEL; i++) fastTravelName[i] = "";
    }

    // -------------------------------------------------
    //  MAP REGISTRATION
    // -------------------------------------------------
    public int registerMap(String name, int width, int height, int region) {
        if (mapCount >= MAX_MAPS) return -1;
        int id = mapCount;
        mapName[id] = name;
        mapWidth[id] = width;
        mapHeight[id] = height;
        mapRegion[id] = region;
        mapLevel[id] = 1;
        mapEncounterRate[id] = getDefaultEncounterRate(region);
        mapBGM[id] = getDefaultBGM(region);
        mapWeather[id] = getDefaultWeather(region);
        mapOverlay[id] = WeatherSystem.OVERLAY_NONE;
        mapDiscovered[id] = false;
        mapVisited[id] = false;
        mapCount++;
        return id;
    }

    private int getDefaultEncounterRate(int region) {
        switch (region) {
            case REGION_DUNGEON: case REGION_CAVE: return 20;
            case REGION_FOREST:  case REGION_SWAMP: return 15;
            case REGION_MOUNTAIN: case REGION_DESERT: return 12;
            case REGION_VOLCANO: return 25;
            case REGION_TOWN: case REGION_CASTLE: return 0;
            default: return 10;
        }
    }

    private int getDefaultBGM(int region) {
        switch (region) {
            case REGION_DUNGEON: case REGION_CAVE: return SoundManager.MUS_DUNGEON;
            default: return SoundManager.MUS_FIELD;
        }
    }

    private int getDefaultWeather(int region) {
        switch (region) {
            case REGION_SNOW: return WeatherSystem.WEATHER_SNOW;
            case REGION_DESERT: return WeatherSystem.WEATHER_SANDSTORM;
            case REGION_SWAMP: return WeatherSystem.WEATHER_FOG;
            case REGION_VOLCANO: return WeatherSystem.WEATHER_SPARKLE;
            default: return WeatherSystem.WEATHER_NONE;
        }
    }

    public void setMapProperties(int mapId, int level, int encounterRate,
                                  int bgm, int weather, int overlay) {
        if (mapId < 0 || mapId >= mapCount) return;
        mapLevel[mapId] = level;
        mapEncounterRate[mapId] = encounterRate;
        mapBGM[mapId] = bgm;
        mapWeather[mapId] = weather;
        mapOverlay[mapId] = overlay;
    }

    // -------------------------------------------------
    //  CONNECTIONS
    // -------------------------------------------------
    public void connectMaps(int mapId1, int direction, int mapId2,
                            int spawnX, int spawnY) {
        if (mapId1 < 0 || mapId1 >= mapCount || mapId2 < 0 || mapId2 >= mapCount) return;
        mapConnections[mapId1][direction] = mapId2;
        connectionX[mapId1][direction] = spawnX;
        connectionY[mapId1][direction] = spawnY;
        int rev = reverseDir(direction);
        if (rev >= 0) mapConnections[mapId2][rev] = mapId1;
    }

    private int reverseDir(int dir) {
        switch (dir) {
            case DIR_NORTH: return DIR_SOUTH;
            case DIR_SOUTH: return DIR_NORTH;
            case DIR_EAST:  return DIR_WEST;
            case DIR_WEST:  return DIR_EAST;
        }
        return -1;
    }

    // -------------------------------------------------
    //  TELEPORTS
    // -------------------------------------------------
    public void addTeleport(int mapId, int x, int y, int targetMap, int tx, int ty) {
        if (mapId < 0 || mapId >= mapCount || teleportCount[mapId] >= MAX_TELEPORTS) return;
        int idx = teleportCount[mapId];
        teleportX[mapId][idx] = x;
        teleportY[mapId][idx] = y;
        teleportTargetMap[mapId][idx] = targetMap;
        teleportTargetX[mapId][idx] = tx;
        teleportTargetY[mapId][idx] = ty;
        teleportCount[mapId]++;
    }

    public int checkTeleport(int mapId, int x, int y) {
        if (mapId < 0 || mapId >= mapCount) return -1;
        for (int i = 0; i < teleportCount[mapId]; i++) {
            if (teleportX[mapId][i] == x && teleportY[mapId][i] == y) return i;
        }
        return -1;
    }

    // -------------------------------------------------
    //  SAVE POINTS
    // -------------------------------------------------
    public void addSavePoint(int mapId, int x, int y) {
        if (mapId < 0 || mapId >= mapCount || savePointCount[mapId] >= MAX_SAVEPOINTS) return;
        int idx = savePointCount[mapId];
        savePointX[mapId][idx] = x;
        savePointY[mapId][idx] = y;
        savePointCount[mapId]++;
    }

    public boolean isSavePoint(int mapId, int x, int y) {
        if (mapId < 0 || mapId >= mapCount) return false;
        for (int i = 0; i < savePointCount[mapId]; i++) {
            if (savePointX[mapId][i] == x && savePointY[mapId][i] == y) return true;
        }
        return false;
    }

    // -------------------------------------------------
    //  MAP NOTES (NEW)
    // -------------------------------------------------
    public void addNote(int mapId, int x, int y, String text) {
        if (mapId < 0 || mapId >= mapCount || noteCount[mapId] >= MAX_NOTES) return;
        int idx = noteCount[mapId];
        noteX[mapId][idx] = x;
        noteY[mapId][idx] = y;
        noteText[mapId][idx] = (text != null) ? text : "";
        noteCount[mapId]++;
    }

    public void removeNote(int mapId, int index) {
        if (mapId < 0 || mapId >= mapCount || index < 0 || index >= noteCount[mapId]) return;
        for (int i = index; i < noteCount[mapId] - 1; i++) {
            noteX[mapId][i] = noteX[mapId][i + 1];
            noteY[mapId][i] = noteY[mapId][i + 1];
            noteText[mapId][i] = noteText[mapId][i + 1];
        }
        noteCount[mapId]--;
    }

    // -------------------------------------------------
    //  FAST TRAVEL (NEW)
    // -------------------------------------------------
    public int addFastTravelNode(int mapId, int x, int y, String name) {
        if (fastTravelCount >= MAX_FAST_TRAVEL) return -1;
        int id = fastTravelCount;
        fastTravelMapId[id] = mapId;
        fastTravelX[id] = x;
        fastTravelY[id] = y;
        fastTravelName[id] = (name != null) ? name : "Unknown";
        fastTravelUnlocked[id] = false;
        fastTravelCount++;
        return id;
    }

    public void unlockFastTravel(int nodeId) {
        if (nodeId >= 0 && nodeId < fastTravelCount) {
            fastTravelUnlocked[nodeId] = true;
            // Also discover the map
            mapDiscovered[fastTravelMapId[nodeId]] = true;
        }
    }

    public int checkFastTravelNode(int mapId, int x, int y) {
        for (int i = 0; i < fastTravelCount; i++) {
            if (fastTravelMapId[i] == mapId && fastTravelX[i] == x && fastTravelY[i] == y) return i;
        }
        return -1;
    }

    // -------------------------------------------------
    //  MAP LOADING
    // -------------------------------------------------
    public void loadMap(int mapId) {
        if (mapId < 0 || mapId >= mapCount) return;
        currentMapId = mapId;
        currentWidth = mapWidth[mapId];
        currentHeight = mapHeight[mapId];
        currentMapData = new int[4][currentHeight][currentWidth];  // 4 layers
        generateMapData(mapId);
        mapDiscovered[mapId] = true;
        mapVisited[mapId] = true;
    }

    public void loadMap(int mapId, int[][][] existingData) {
        if (mapId < 0 || mapId >= mapCount) return;
        currentMapId = mapId;
        currentWidth = mapWidth[mapId];
        currentHeight = mapHeight[mapId];
        currentMapData = existingData;
        mapDiscovered[mapId] = true;
        mapVisited[mapId] = true;
    }

    private void generateMapData(int mapId) {
        int region = mapRegion[mapId];
        int seed = mapId * 12345;

        int baseTile, wallTile;
        switch (region) {
            case REGION_FOREST:
                baseTile = TileData.TILE_GRASS; wallTile = TileData.TILE_TREE; break;
            case REGION_DESERT:
                baseTile = TileData.TILE_SAND; wallTile = TileData.TILE_ROCK; break;
            case REGION_SNOW:
                baseTile = TileData.TILE_SNOW; wallTile = TileData.TILE_ROCK; break;
            case REGION_DUNGEON: case REGION_CAVE:
                baseTile = TileData.TILE_STONE; wallTile = TileData.TILE_BRICK; break;
            case REGION_TOWN: case REGION_CASTLE:
                baseTile = TileData.TILE_PATH; wallTile = TileData.TILE_WALL_TOP; break;
            case REGION_WATER:
                baseTile = TileData.TILE_WATER; wallTile = TileData.TILE_ROCK; break;
            case REGION_SWAMP:
                baseTile = TileData.TILE_DIRT; wallTile = TileData.TILE_TREE; break;
            case REGION_VOLCANO:
                baseTile = TileData.TILE_STONE; wallTile = TileData.TILE_LAVA; break;
            default:
                baseTile = TileData.TILE_GRASS; wallTile = TileData.TILE_TREE; break;
        }

        // Fill base layer
        for (int y = 0; y < currentHeight; y++) {
            for (int x = 0; x < currentWidth; x++) {
                currentMapData[0][y][x] = baseTile;
                currentMapData[1][y][x] = 0;
                currentMapData[2][y][x] = 0;
                currentMapData[3][y][x] = 0;
            }
        }

        // Border walls
        for (int x = 0; x < currentWidth; x++) {
            currentMapData[1][0][x] = wallTile;
            currentMapData[1][currentHeight - 1][x] = wallTile;
        }
        for (int y = 0; y < currentHeight; y++) {
            currentMapData[1][y][0] = wallTile;
            currentMapData[1][y][currentWidth - 1] = wallTile;
        }

        // Scatter obstacles
        for (int i = 0; i < (currentWidth * currentHeight) / 18; i++) {
            seed = nextRand(seed);
            int ox = 2 + (seed % (currentWidth - 4));
            seed = nextRand(seed);
            int oy = 2 + (seed % (currentHeight - 4));
            currentMapData[1][oy][ox] = wallTile;
        }

        // Connection openings
        for (int dir = 0; dir < 4; dir++) {
            if (mapConnections[mapId][dir] < 0) continue;
            int cx, cy;
            switch (dir) {
                case DIR_NORTH:
                    cx = currentWidth / 2; cy = 0;
                    for (int d = -1; d <= 1; d++) currentMapData[1][cy][cx + d] = 0;
                    break;
                case DIR_SOUTH:
                    cx = currentWidth / 2; cy = currentHeight - 1;
                    for (int d = -1; d <= 1; d++) currentMapData[1][cy][cx + d] = 0;
                    break;
                case DIR_EAST:
                    cx = currentWidth - 1; cy = currentHeight / 2;
                    for (int d = -1; d <= 1; d++) currentMapData[1][cy + d][cx] = 0;
                    break;
                case DIR_WEST:
                    cx = 0; cy = currentHeight / 2;
                    for (int d = -1; d <= 1; d++) currentMapData[1][cy + d][cx] = 0;
                    break;
            }
        }
    }

    // -------------------------------------------------
    //  MAP TRANSITIONS
    // -------------------------------------------------
    public int checkMapTransition(int x, int y) {
        if (y <= 0 && mapConnections[currentMapId][DIR_NORTH] >= 0) return DIR_NORTH;
        if (y >= currentHeight - 1 && mapConnections[currentMapId][DIR_SOUTH] >= 0) return DIR_SOUTH;
        if (x >= currentWidth - 1 && mapConnections[currentMapId][DIR_EAST] >= 0) return DIR_EAST;
        if (x <= 0 && mapConnections[currentMapId][DIR_WEST] >= 0) return DIR_WEST;
        return -1;
    }

    public void transitionMap(int direction, PlayerData player) {
        int targetMap = mapConnections[currentMapId][direction];
        if (targetMap < 0) return;

        int spawnX = connectionX[currentMapId][direction];
        int spawnY = connectionY[currentMapId][direction];

        if (spawnX == 0 && spawnY == 0) {
            switch (direction) {
                case DIR_NORTH: spawnX = mapWidth[targetMap] / 2; spawnY = mapHeight[targetMap] - 2; break;
                case DIR_SOUTH: spawnX = mapWidth[targetMap] / 2; spawnY = 1; break;
                case DIR_EAST:  spawnX = 1; spawnY = mapHeight[targetMap] / 2; break;
                case DIR_WEST:  spawnX = mapWidth[targetMap] - 2; spawnY = mapHeight[targetMap] / 2; break;
            }
        }

        loadMap(targetMap);
        player.mapId = targetMap;
        player.x = spawnX;
        player.y = spawnY;
    }

    public void teleportTo(int mapId, int teleportIdx, PlayerData player) {
        if (teleportIdx < 0 || teleportIdx >= teleportCount[mapId]) return;
        int tMap = teleportTargetMap[mapId][teleportIdx];
        if (tMap != currentMapId) loadMap(tMap);
        player.mapId = tMap;
        player.x = teleportTargetX[mapId][teleportIdx];
        player.y = teleportTargetY[mapId][teleportIdx];
    }

    public void fastTravelTo(int nodeId, PlayerData player) {
        if (nodeId < 0 || nodeId >= fastTravelCount) return;
        if (!fastTravelUnlocked[nodeId]) return;
        int tMap = fastTravelMapId[nodeId];
        if (tMap != currentMapId) loadMap(tMap);
        player.mapId = tMap;
        player.x = fastTravelX[nodeId];
        player.y = fastTravelY[nodeId];
    }

    // -------------------------------------------------
    //  RANDOM ENCOUNTERS (level-scaled)
    // -------------------------------------------------
    public boolean checkRandomEncounter(int steps, int seed, int playerLevel) {
        if (currentMapId < 0) return false;
        int rate = mapEncounterRate[currentMapId];
        if (rate <= 0) return false;
        if (steps < GameConfig.ENCOUNTER_MIN_STEPS) return false;
        seed = nextRand(seed);
        // Higher player level vs map level = lower encounter chance (explored)
        int levelDiff = playerLevel - mapLevel[currentMapId];
        int threshold = rate + (steps - GameConfig.ENCOUNTER_MIN_STEPS) * 2
                        - Math.max(0, levelDiff * 2);
        return (seed % 100) < Math.max(5, threshold);
    }

    public int[] getEncounterEnemies(int seed, int playerLevel) {
        if (currentMapId < 0) return new int[]{0};
        int region = mapRegion[currentMapId];
        int level  = mapLevel[currentMapId];
        seed = nextRand(seed);
        int count = 1 + (seed % 3);
        int[] enemies = new int[count];
        for (int i = 0; i < count; i++) {
            seed = nextRand(seed);
            switch (region) {
                case REGION_FOREST:
                    enemies[i] = (seed % 3 == 0) ? EnemyData.ENEMY_WOLF :
                                 (seed % 3 == 1) ? EnemyData.ENEMY_SLIME : EnemyData.ENEMY_BAT;
                    break;
                case REGION_DUNGEON: case REGION_CAVE:
                    enemies[i] = (seed % 3 == 0) ? EnemyData.ENEMY_SKELETON :
                                 (seed % 3 == 1) ? EnemyData.ENEMY_BAT : EnemyData.ENEMY_SPIDER;
                    break;
                case REGION_DESERT:
                    enemies[i] = (seed % 2 == 0) ? EnemyData.ENEMY_SPIDER : EnemyData.ENEMY_ORC;
                    break;
                case REGION_SNOW:
                    enemies[i] = (seed % 2 == 0) ? EnemyData.ENEMY_WOLF : EnemyData.ENEMY_GOLEM;
                    break;
                case REGION_VOLCANO:
                    enemies[i] = (seed % 2 == 0) ? EnemyData.ENEMY_DJINN : EnemyData.ENEMY_TITAN;
                    break;
                default:
                    enemies[i] = seed % Math.max(1, Math.min(level / 5 + 2, EnemyData.MAX_ENEMY_TYPES));
            }
        }
        return enemies;
    }

    // -------------------------------------------------
    //  SCROLL / CAMERA
    // -------------------------------------------------
    public void updateScroll(int playerX, int playerY,
                             int viewCols, int viewRows) {
        scrollX = playerX - viewCols / 2;
        scrollY = playerY - viewRows / 2;
        if (scrollX < 0) scrollX = 0;
        if (scrollY < 0) scrollY = 0;
        if (scrollX > currentWidth - viewCols) scrollX = Math.max(0, currentWidth - viewCols);
        if (scrollY > currentHeight - viewRows) scrollY = Math.max(0, currentHeight - viewRows);
    }

    // -------------------------------------------------
    //  HELPERS
    // -------------------------------------------------
    private int nextRand(int seed) { return (seed * 1103515245 + 12345) & 0x7FFFFFFF; }

    public String getMapName(int mapId) {
        if (mapId < 0 || mapId >= mapCount) return "Unknown";
        return mapName[mapId];
    }

    public String getCurrentMapName() { return getMapName(currentMapId); }

    public int getCurrentRegion() {
        return currentMapId >= 0 ? mapRegion[currentMapId] : REGION_FIELD;
    }

    public int getCurrentBGM() {
        return currentMapId >= 0 ? mapBGM[currentMapId] : SoundManager.MUS_FIELD;
    }

    public int getCurrentWeather() {
        return currentMapId >= 0 ? mapWeather[currentMapId] : WeatherSystem.WEATHER_NONE;
    }

    public int getCurrentOverlay() {
        return currentMapId >= 0 ? mapOverlay[currentMapId] : WeatherSystem.OVERLAY_NONE;
    }

    public int getCurrentLevel() {
        return currentMapId >= 0 ? mapLevel[currentMapId] : 1;
    }

    // -------------------------------------------------
    //  SAVE / LOAD
    // -------------------------------------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(mapCount);
        for (int i = 0; i < mapCount; i++) {
            dos.writeUTF(mapName[i]);
            dos.writeInt(mapWidth[i]);
            dos.writeInt(mapHeight[i]);
            dos.writeInt(mapRegion[i]);
            dos.writeInt(mapLevel[i]);
            dos.writeInt(mapEncounterRate[i]);
            dos.writeInt(mapBGM[i]);
            dos.writeInt(mapWeather[i]);
            dos.writeInt(mapOverlay[i]);
            dos.writeBoolean(mapDiscovered[i]);
            dos.writeBoolean(mapVisited[i]);

            for (int j = 0; j < 4; j++) {
                dos.writeInt(mapConnections[i][j]);
                dos.writeInt(connectionX[i][j]);
                dos.writeInt(connectionY[i][j]);
            }

            dos.writeInt(teleportCount[i]);
            for (int j = 0; j < teleportCount[i]; j++) {
                dos.writeInt(teleportX[i][j]);
                dos.writeInt(teleportY[i][j]);
                dos.writeInt(teleportTargetMap[i][j]);
                dos.writeInt(teleportTargetX[i][j]);
                dos.writeInt(teleportTargetY[i][j]);
            }

            dos.writeInt(savePointCount[i]);
            for (int j = 0; j < savePointCount[i]; j++) {
                dos.writeInt(savePointX[i][j]);
                dos.writeInt(savePointY[i][j]);
            }

            dos.writeInt(noteCount[i]);
            for (int j = 0; j < noteCount[i]; j++) {
                dos.writeInt(noteX[i][j]);
                dos.writeInt(noteY[i][j]);
                dos.writeUTF(noteText[i][j]);
            }
        }

        dos.writeInt(fastTravelCount);
        for (int i = 0; i < fastTravelCount; i++) {
            dos.writeInt(fastTravelMapId[i]);
            dos.writeInt(fastTravelX[i]);
            dos.writeInt(fastTravelY[i]);
            dos.writeUTF(fastTravelName[i]);
            dos.writeBoolean(fastTravelUnlocked[i]);
        }

        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        mapCount = dis.readInt();
        for (int i = 0; i < mapCount; i++) {
            mapName[i] = dis.readUTF();
            mapWidth[i] = dis.readInt();
            mapHeight[i] = dis.readInt();
            mapRegion[i] = dis.readInt();
            mapLevel[i] = dis.readInt();
            mapEncounterRate[i] = dis.readInt();
            mapBGM[i] = dis.readInt();
            mapWeather[i] = dis.readInt();
            mapOverlay[i] = dis.readInt();
            try {
                mapDiscovered[i] = dis.readBoolean();
                mapVisited[i] = dis.readBoolean();
            } catch (IOException e) {
                mapDiscovered[i] = true;
                mapVisited[i] = true;
            }

            for (int j = 0; j < 4; j++) {
                mapConnections[i][j] = dis.readInt();
                connectionX[i][j] = dis.readInt();
                connectionY[i][j] = dis.readInt();
            }

            teleportCount[i] = dis.readInt();
            for (int j = 0; j < teleportCount[i]; j++) {
                teleportX[i][j] = dis.readInt();
                teleportY[i][j] = dis.readInt();
                teleportTargetMap[i][j] = dis.readInt();
                teleportTargetX[i][j] = dis.readInt();
                teleportTargetY[i][j] = dis.readInt();
            }

            savePointCount[i] = dis.readInt();
            for (int j = 0; j < savePointCount[i]; j++) {
                savePointX[i][j] = dis.readInt();
                savePointY[i][j] = dis.readInt();
            }

            try {
                noteCount[i] = dis.readInt();
                for (int j = 0; j < noteCount[i]; j++) {
                    noteX[i][j] = dis.readInt();
                    noteY[i][j] = dis.readInt();
                    noteText[i][j] = dis.readUTF();
                }
            } catch (IOException e) {
                noteCount[i] = 0;
            }
        }

        try {
            fastTravelCount = dis.readInt();
            for (int i = 0; i < fastTravelCount; i++) {
                fastTravelMapId[i] = dis.readInt();
                fastTravelX[i] = dis.readInt();
                fastTravelY[i] = dis.readInt();
                fastTravelName[i] = dis.readUTF();
                fastTravelUnlocked[i] = dis.readBoolean();
            }
        } catch (IOException e) {
            fastTravelCount = 0;
        }
    }
}
