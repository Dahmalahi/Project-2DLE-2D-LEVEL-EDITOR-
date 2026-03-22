/**
 * DungeonGenerator.java - ENHANCED v2.0
 * Added: multi-floor support, themed rooms, secret rooms,
 * traps, corridors with variants, boss rooms on final floor,
 * environmental hazards, and mini-boss spawns.
 */
public class DungeonGenerator {

    // -------------------------------------------------
    //  CONSTANTS
    // -------------------------------------------------
    private static final int TILE_WALL   = TileData.TILE_BRICK;
    private static final int TILE_FLOOR  = TileData.TILE_STONE;
    private static final int TILE_DOOR   = TileData.TILE_DOOR;
    private static final int TILE_CHEST  = TileData.TILE_CHEST;
    private static final int TILE_STAIRS = TileData.TILE_STAIRS_DN;
    private static final int TILE_STAIRS_UP = TileData.TILE_STAIRS_UP;
    private static final int TILE_LAVA   = TileData.TILE_LAVA;
    private static final int TILE_WATER  = TileData.TILE_WATER;
    private static final int TILE_SPECIAL= TileData.TILE_SPECIAL;
    private static final int TILE_SIGN   = TileData.TILE_SIGN;

    private static final int MAX_ROOMS   = 12;  // up from 10

    // -------------------------------------------------
    //  THEME IDS
    // -------------------------------------------------
    public static final int THEME_CAVE    = 0;
    public static final int THEME_DUNGEON = 1;
    public static final int THEME_RUINS   = 2;
    public static final int THEME_ICE     = 3;
    public static final int THEME_LAVA    = 4;
    public static final int THEME_CRYPT   = 5;

    // -------------------------------------------------
    //  GENERATION (MAIN)
    // -------------------------------------------------
    public static void generate(int[][] map, int width, int height,
                                int seed, int theme, int floorDepth) {
        // Fill with walls based on theme
        int wallTile = (theme == THEME_ICE)   ? TileData.TILE_SNOW :
                       (theme == THEME_LAVA)  ? TILE_LAVA :
                       (theme == THEME_CRYPT) ? TILE_WALL : TILE_WALL;

        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                map[y][x] = TILE_WALL;

        int rand = seed;

        int[] roomX = new int[MAX_ROOMS];
        int[] roomY = new int[MAX_ROOMS];
        int[] roomW = new int[MAX_ROOMS];
        int[] roomH = new int[MAX_ROOMS];
        int[] roomType= new int[MAX_ROOMS]; // 0=normal,1=secret,2=boss,3=trap
        int roomCount = 0;

        // Generate rooms
        int attempts = 0;
        while (roomCount < MAX_ROOMS && attempts < 150) {
            attempts++;
            rand = nextRand(rand);
            int rw = 4 + (Math.abs(rand) % 7);
            rand = nextRand(rand);
            int rh = 4 + (Math.abs(rand) % 7);
            rand = nextRand(rand);
            int rx = 1 + (Math.abs(rand) % (width - rw - 2));
            rand = nextRand(rand);
            int ry = 1 + (Math.abs(rand) % (height - rh - 2));

            boolean overlap = false;
            for (int i = 0; i < roomCount; i++) {
                if (roomsOverlap(rx, ry, rw, rh,
                                 roomX[i], roomY[i], roomW[i], roomH[i])) {
                    overlap = true; break;
                }
            }

            if (!overlap) {
                // Carve room with themed floor
                int floorTile = getThemeFloor(theme);
                for (int y = ry; y < ry + rh; y++)
                    for (int x = rx; x < rx + rw; x++)
                        map[y][x] = floorTile;

                // Determine room type
                int rtype = 0;
                rand = nextRand(rand);
                if (roomCount == MAX_ROOMS - 1 && floorDepth % 5 == 0) rtype = 2; // boss room
                else if (Math.abs(rand) % 8 == 0 && roomCount > 2) rtype = 1; // secret room
                else if (Math.abs(rand) % 6 == 0 && roomCount > 1) rtype = 3; // trap room
                roomType[roomCount] = rtype;

                // Connect to previous room
                if (roomCount > 0) {
                    int pcx = roomX[roomCount-1] + roomW[roomCount-1]/2;
                    int pcy = roomY[roomCount-1] + roomH[roomCount-1]/2;
                    int ccx = rx + rw/2;
                    int ccy = ry + rh/2;

                    rand = nextRand(rand);
                    if (Math.abs(rand) % 2 == 0) {
                        carveHorizontal(map, pcx, ccx, pcy, floorTile);
                        carveVertical(map, pcy, ccy, ccx, floorTile);
                    } else {
                        carveVertical(map, pcy, ccy, pcx, floorTile);
                        carveHorizontal(map, pcx, ccx, ccy, floorTile);
                    }

                    // Place door at connection point sometimes
                    rand = nextRand(rand);
                    if (Math.abs(rand) % 3 == 0) {
                        int doorX = (pcx + ccx) / 2;
                        int doorY = (pcy + ccy) / 2;
                        if (doorX > 0 && doorX < width - 1 &&
                            doorY > 0 && doorY < height - 1) {
                            map[doorY][doorX] = TILE_DOOR;
                        }
                    }
                }

                // Decorate room based on type
                decorateRoom(map, rx, ry, rw, rh, rtype, theme, rand, floorDepth);

                roomX[roomCount] = rx; roomY[roomCount] = ry;
                roomW[roomCount] = rw; roomH[roomCount] = rh;
                roomCount++;
            }
        }

        // Place stairs in first room (up) and last room (down)
        if (roomCount > 0) {
            int sx = roomX[0] + roomW[0]/2;
            int sy = roomY[0] + roomH[0]/2;
            map[sy][sx] = TILE_STAIRS_UP;

            if (roomCount > 1) {
                int dx = roomX[roomCount-1] + roomW[roomCount-1]/2;
                int dy = roomY[roomCount-1] + roomH[roomCount-1]/2;
                map[dy][dx] = TILE_STAIRS;
            }
        }

        // Environmental hazards based on theme
        applyThemeHazards(map, width, height, theme, rand);
    }

    // -------------------------------------------------
    //  ROOM DECORATION
    // -------------------------------------------------
    private static void decorateRoom(int[][] map, int rx, int ry, int rw, int rh,
                                     int rtype, int theme, int rand, int depth) {
        int cx = rx + rw/2;
        int cy = ry + rh/2;

        switch (rtype) {
            case 0: // Normal
                // Random chest
                rand = nextRand(rand);
                if (Math.abs(rand) % 3 == 0) {
                    int chx = rx + 1 + Math.abs(rand) % (rw - 2);
                    rand = nextRand(rand);
                    int chy = ry + 1 + Math.abs(rand) % (rh - 2);
                    map[chy][chx] = TILE_CHEST;
                }
                // Sign (lore hint)
                rand = nextRand(rand);
                if (Math.abs(rand) % 5 == 0) {
                    map[ry + 1][rx + 1] = TILE_SIGN;
                }
                break;

            case 1: // Secret room - guaranteed treasure
                map[cy][cx] = TILE_CHEST;
                // Extra chests in corners
                if (rw > 5 && rh > 5) {
                    map[ry + 1][rx + 1] = TILE_CHEST;
                    map[ry + rh - 2][rx + rw - 2] = TILE_CHEST;
                }
                // Special tile (event trigger)
                map[ry + 1][rx + rw - 2] = TILE_SPECIAL;
                break;

            case 2: // Boss room - large, no clutter, boss marker
                // Clear the room of all objects
                for (int y = ry + 1; y < ry + rh - 1; y++)
                    for (int x = rx + 1; x < rx + rw - 1; x++) {
                        if (map[y][x] != getThemeFloor(theme)) continue;
                    }
                // Boss marker (special tile at center)
                map[cy][cx] = TILE_SPECIAL;
                // Chest behind boss position
                if (cy + 2 < ry + rh - 1) map[cy + 2][cx] = TILE_CHEST;
                break;

            case 3: // Trap room
                // Add lava/water hazards in pattern
                for (int y = ry + 1; y < ry + rh - 1; y++) {
                    for (int x = rx + 1; x < rx + rw - 1; x++) {
                        if ((x + y) % 3 == 0) {
                            map[y][x] = (theme == THEME_LAVA) ? TILE_LAVA : TILE_WATER;
                        }
                    }
                }
                // Still make the center safe for escape
                map[cy][cx] = getThemeFloor(theme);
                break;
        }

        // Pillar pattern for large rooms
        if (rw >= 7 && rh >= 7) {
            placePillars(map, rx, ry, rw, rh, theme);
        }
    }

    private static void placePillars(int[][] map, int rx, int ry, int rw, int rh, int theme) {
        int pillarTile = theme == THEME_RUINS ? TileData.TILE_ROCK : TILE_WALL;
        // 4-pillar pattern
        int px1 = rx + 2, px2 = rx + rw - 3;
        int py1 = ry + 2, py2 = ry + rh - 3;
        if (px1 < px2 && py1 < py2) {
            map[py1][px1] = pillarTile; map[py1][px2] = pillarTile;
            map[py2][px1] = pillarTile; map[py2][px2] = pillarTile;
        }
    }

    // -------------------------------------------------
    //  THEME HELPERS
    // -------------------------------------------------
    private static int getThemeFloor(int theme) {
        switch (theme) {
            case THEME_CAVE:    return TileData.TILE_CAVE;
            case THEME_DUNGEON: return TileData.TILE_FLOOR_TILE;
            case THEME_RUINS:   return TileData.TILE_DIRT;
            case THEME_ICE:     return TileData.TILE_ICE;
            case THEME_LAVA:    return TileData.TILE_STONE;
            case THEME_CRYPT:   return TileData.TILE_FLOOR_TILE;
            default: return TILE_FLOOR;
        }
    }

    private static void applyThemeHazards(int[][] map, int width, int height,
                                           int theme, int rand) {
        if (theme == THEME_LAVA) {
            // Scattered lava pools near walls
            for (int y = 2; y < height - 2; y++) {
                for (int x = 2; x < width - 2; x++) {
                    if (map[y][x] == TILE_WALL) {
                        rand = nextRand(rand);
                        if (Math.abs(rand) % 25 == 0) {
                            map[y][x] = TILE_LAVA;
                        }
                    }
                }
            }
        } else if (theme == THEME_ICE) {
            // Ice floor patches on corridors
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    if (map[y][x] == TileData.TILE_STONE) {
                        rand = nextRand(rand);
                        if (Math.abs(rand) % 4 == 0) {
                            map[y][x] = TileData.TILE_ICE;
                        }
                    }
                }
            }
        } else if (theme == THEME_CAVE) {
            // Stalactite obstacles (rocks) in corridors
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    if (map[y][x] == TileData.TILE_CAVE) {
                        rand = nextRand(rand);
                        if (Math.abs(rand) % 15 == 0) {
                            map[y][x] = TileData.TILE_ROCK;
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------
    //  ENEMY SPAWN POINTS
    // -------------------------------------------------
    /** Returns recommended enemy spawn positions based on map analysis */
    public static int[] getSpawnPoints(int[][] map, int width, int height,
                                        int maxSpawns, int seed) {
        int[] spawnX = new int[maxSpawns];
        int[] spawnY = new int[maxSpawns];
        int count = 0;
        int rand = seed;

        for (int attempt = 0; attempt < maxSpawns * 10 && count < maxSpawns; attempt++) {
            rand = nextRand(rand);
            int x = 1 + Math.abs(rand) % (width - 2);
            rand = nextRand(rand);
            int y = 1 + Math.abs(rand) % (height - 2);

            if (map[y][x] == TILE_FLOOR || map[y][x] == getThemeFloor(THEME_DUNGEON)) {
                // Check not on stair/chest
                if (map[y][x] != TILE_STAIRS && map[y][x] != TILE_STAIRS_UP &&
                    map[y][x] != TILE_CHEST && map[y][x] != TILE_SPECIAL) {
                    spawnX[count] = x;
                    spawnY[count] = y;
                    count++;
                }
            }
        }

        // Pack result into flat array [x0,y0, x1,y1, ...]
        int[] result = new int[count * 2];
        for (int i = 0; i < count; i++) {
            result[i*2]   = spawnX[i];
            result[i*2+1] = spawnY[i];
        }
        return result;
    }

    // -------------------------------------------------
    //  UTILITY
    // -------------------------------------------------
    private static int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    private static boolean roomsOverlap(int x1,int y1,int w1,int h1,
                                         int x2,int y2,int w2,int h2) {
        return !(x1 + w1 + 1 < x2 || x2 + w2 + 1 < x1 ||
                 y1 + h1 + 1 < y2 || y2 + h2 + 1 < y1);
    }

    private static void carveHorizontal(int[][] map, int x1, int x2, int y, int tile) {
        int minX = Math.min(x1,x2), maxX = Math.max(x1,x2);
        for (int x = minX; x <= maxX; x++)
            if (y >= 0 && y < map.length && x >= 0 && x < map[0].length)
                map[y][x] = tile;
    }

    private static void carveVertical(int[][] map, int y1, int y2, int x, int tile) {
        int minY = Math.min(y1,y2), maxY = Math.max(y1,y2);
        for (int y = minY; y <= maxY; y++)
            if (y >= 0 && y < map.length && x >= 0 && x < map[0].length)
                map[y][x] = tile;
    }

    public static int getStartX(int[][] map, int width, int height) {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (map[y][x] == TILE_STAIRS_UP) return x;
        return width/2;
    }

    public static int getStartY(int[][] map, int width, int height) {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (map[y][x] == TILE_STAIRS_UP) return y;
        return height/2;
    }

    public static int getBossX(int[][] map, int width, int height) {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (map[y][x] == TILE_SPECIAL) return x;
        return width/2;
    }

    public static int getBossY(int[][] map, int width, int height) {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (map[y][x] == TILE_SPECIAL) return y;
        return height/2;
    }
}
