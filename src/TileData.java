/**
 * TileData.java — Enhanced tile definitions v2.0
 *
 * NEW in v2.0:
 *  - Expanded to 64 tile types (was 32)
 *  - Added 9th element: WIND, EARTH, WATER tiles
 *  - New tiles: Swamp, Quicksand, Crystal, Volcano, Telepad, Pressure plate
 *  - Each tile now has a light emission value for the day/night system
 *  - Destructible tile flag added
 *  - Z-height property for pseudo-3D perspective maps
 *  - Tag system for map-region detection (indoor/outdoor/underwater)
 */
public class TileData {

    // =========================================================
    //  TILE TYPE CONSTANTS (64 total)
    // =========================================================
    public static final int TILE_VOID        = 0;
    public static final int TILE_GRASS       = 1;
    public static final int TILE_GRASS_LIGHT = 2;
    public static final int TILE_DIRT        = 3;
    public static final int TILE_STONE       = 4;
    public static final int TILE_WOOD        = 5;
    public static final int TILE_WATER       = 6;
    public static final int TILE_SAND        = 7;
    public static final int TILE_BRICK       = 8;
    public static final int TILE_TREE        = 9;
    public static final int TILE_CHEST       = 10;
    public static final int TILE_LAVA        = 11;
    public static final int TILE_SNOW        = 12;
    public static final int TILE_CAVE        = 13;
    public static final int TILE_NIGHT       = 14;
    public static final int TILE_SPECIAL     = 15;
    public static final int TILE_BRIDGE      = 16;
    public static final int TILE_PATH        = 17;
    public static final int TILE_FLOWER      = 18;
    public static final int TILE_ROCK        = 19;
    public static final int TILE_BUSH        = 20;
    public static final int TILE_DOOR        = 21;
    public static final int TILE_STAIRS_UP   = 22;
    public static final int TILE_STAIRS_DN   = 23;
    public static final int TILE_SIGN        = 24;
    public static final int TILE_FENCE       = 25;
    public static final int TILE_ROOF        = 26;
    public static final int TILE_WALL_TOP    = 27;
    public static final int TILE_FLOOR_WOOD  = 28;
    public static final int TILE_FLOOR_TILE  = 29;
    public static final int TILE_CARPET      = 30;
    public static final int TILE_ICE         = 31;
    // NEW tiles 32-63
    public static final int TILE_SWAMP       = 32;   // slows + poison chance
    public static final int TILE_QUICKSAND   = 33;   // sinking trap
    public static final int TILE_CRYSTAL     = 34;   // reflective solid, magical
    public static final int TILE_VOLCANO     = 35;   // lava variant with eruption
    public static final int TILE_TELEPAD     = 36;   // instant teleport tile
    public static final int TILE_PRESSURE    = 37;   // pressure plate trigger
    public static final int TILE_MUD         = 38;   // slows movement
    public static final int TILE_TALL_GRASS  = 39;   // hides player + random enc
    public static final int TILE_CACTUS      = 40;   // damage + solid
    public static final int TILE_CORAL       = 41;   // underwater solid
    public static final int TILE_SEABED      = 42;   // underwater walkable
    public static final int TILE_CLOUD       = 43;   // sky world walkable
    public static final int TILE_MIRROR      = 44;   // reflects projectiles
    public static final int TILE_SPIKE       = 45;   // heavy damage trap
    public static final int TILE_BARREL      = 46;   // pushable + destructible
    public static final int TILE_BOOKSHELF   = 47;   // interact for hints/lore
    public static final int TILE_FIREPLACE   = 48;   // interact to camp/rest
    public static final int TILE_ALTAR       = 49;   // save + heal point
    public static final int TILE_WARP_STONE  = 50;   // fast-travel anchor
    public static final int TILE_CRACKED     = 51;   // break with bomb
    public static final int TILE_DEEP_WATER  = 52;   // needs boat
    public static final int TILE_CLIMBABLE   = 53;   // wall that can be climbed
    public static final int TILE_LADDER      = 53;   // alias for TILE_CLIMBABLE (used by PlayEngine)
    public static final int TILE_CONVEY_R    = 54;   // conveyor belt east
    public static final int TILE_CONVEY_L    = 55;   // conveyor belt west
    public static final int TILE_CONVEY_U    = 56;   // conveyor belt north
    public static final int TILE_CONVEY_D    = 57;   // conveyor belt south
    public static final int TILE_BOMB_WALL   = 58;   // wall destroyed by bombs
    public static final int TILE_TORCH       = 59;   // emits light, decorative
    public static final int TILE_CRYSTAL_BALL= 60;   // event / lore trigger
    public static final int TILE_TRAP_FLOOR  = 61;   // hidden pit / arrow trap
    public static final int TILE_VINE        = 62;   // climbable + slow
    public static final int TILE_GLOWING_ORB = 63;   // permanent light source
    // ── NEW tiles for map-link system ──────────────────────────────────────
    public static final int TILE_HOUSE_DOOR  = 64;   // house entrance (auto-trigger link)
    public static final int TILE_HOUSE_EXIT  = 65;   // house exit back to outside
    // ── BUILDINGS ──────────────────────────────────────────────────────────
    public static final int TILE_SHOP        = 66;   // shop building front
    public static final int TILE_INN_FRONT   = 67;   // inn building front
    public static final int TILE_CASTLE_WALL = 68;   // castle stone wall
    public static final int TILE_CASTLE_GATE = 69;   // castle gate / portcullis
    public static final int TILE_TOWER       = 70;   // tower / turret top
    public static final int TILE_RUIN        = 71;   // broken wall ruin
    public static final int TILE_TEMPLE      = 72;   // temple front
    public static final int TILE_HOUSE_WALL  = 73;   // house side wall
    // ── TRANSPORT ──────────────────────────────────────────────────────────
    public static final int TILE_BOAT        = 74;   // boat (4-frame sail animation)
    public static final int TILE_CART        = 75;   // wooden cart (4-frame wheel)
    public static final int TILE_HORSE       = 76;   // horse mount (4-frame trot)
    public static final int TILE_AIRSHIP     = 77;   // airship / blimp (4-frame)
    public static final int TILE_RAFT        = 78;   // wooden raft on water
    // ── ANIMALS ────────────────────────────────────────────────────────────
    public static final int TILE_BIRD        = 79;   // bird (4-frame flap)
    public static final int TILE_FISH        = 80;   // fish in water (4-frame swim)
    public static final int TILE_CAT         = 81;   // cat (4-frame idle)
    public static final int TILE_DOG         = 82;   // dog (4-frame wag)
    public static final int TILE_RABBIT      = 83;   // rabbit (4-frame hop)
    public static final int TILE_DEER        = 84;   // deer (4-frame walk)
    public static final int TILE_WOLF_TILE   = 85;   // wolf (4-frame prowl)
    public static final int TILE_BEAR        = 86;   // bear (4-frame stomp)

    public static final int MAX_TILES        = 87;  // expanded from 66

    // =========================================================
    //  COLLISION FLAGS
    // =========================================================
    public static final int COL_NONE        = 0;
    public static final int COL_SOLID       = 1;
    public static final int COL_WATER       = 2;
    public static final int COL_DAMAGE      = 4;
    public static final int COL_PICKUP      = 8;
    public static final int COL_THROWABLE   = 16;
    public static final int COL_PUSHABLE    = 32;
    public static final int COL_INTERACT    = 64;
    public static final int COL_SLIPPERY    = 128;
    // NEW flags
    public static final int COL_SLOW        = 256;   // slows movement
    public static final int COL_SINK        = 512;   // quicksand — gradual trap
    public static final int COL_CLIMB       = 1024;  // can be climbed (ladder/vine)
    public static final int COL_HIDDEN_TRAP = 2048;  // invisible until triggered
    public static final int COL_DESTRUCTIBLE= 4096;  // can be destroyed
    public static final int COL_CONVEYOR    = 8192;  // moves player automatically
    public static final int COL_BOAT_REQ    = 16384; // requires boat to traverse
    public static final int COL_SAVE_POINT  = 32768; // triggers save/heal

    // =========================================================
    //  ANIMATION FLAGS
    // =========================================================
    public static final int ANIM_NONE        = 0;
    public static final int ANIM_WALK_DUST   = 1;
    public static final int ANIM_WALK_SPLASH = 2;
    public static final int ANIM_SWIM        = 4;
    public static final int ANIM_BURN        = 8;
    public static final int ANIM_FREEZE      = 16;
    public static final int ANIM_SPARKLE     = 32;
    public static final int ANIM_FOOTPRINT   = 64;
    // NEW animation flags
    public static final int ANIM_BUBBLE      = 128;  // underwater bubbles
    public static final int ANIM_LIGHTNING   = 256;  // electric crackle
    public static final int ANIM_GLOW        = 512;  // pulsing glow
    public static final int ANIM_WAVE        = 1024; // ripple/wave
    public static final int ANIM_SHAKE       = 2048; // earthquake/rumble
    public static final int ANIM_DRIP        = 4096; // swamp/cave drip

    // =========================================================
    //  TILE COLORS (ARGB) — all 64 tiles
    // =========================================================
    public static final int[] TILE_COLORS = {
        0xFF1A1A2E, 0xFF2D5A27, 0xFF4A7023, 0xFF8B7355,  // 0-3
        0xFF696969, 0xFF8B4513, 0xFF2F5496, 0xFFD4AF37,  // 4-7
        0xFFA52A2A, 0xFF228B22, 0xFFFFD700, 0xFFDC143C,  // 8-11
        0xFFE8E8E8, 0xFF4B0082, 0xFF2E2E5E, 0xFFFF69B4,  // 12-15
        0xFF8B6914, 0xFFBDB76B, 0xFFFF6699, 0xFF808080,  // 16-19
        0xFF006400, 0xFF654321, 0xFFDEB887, 0xFFA0522D,  // 20-23
        0xFFCD853F, 0xFF8B4513, 0xFF800000, 0xFF696969,  // 24-27
        0xFFDEB887, 0xFFD2B48C, 0xFF8B0000, 0xFFADD8E6,  // 28-31
        // NEW 32-63
        0xFF3D6B2A, 0xFFB8A040, 0xFF88DDFF, 0xFFFF3300,  // 32-35 swamp/quicksand/crystal/volcano
        0xFF00FFEE, 0xFF996633, 0xFF554422, 0xFF3A5C1A,  // 36-39 telepad/pressure/mud/tallgrass
        0xFF8BC34A, 0xFF00AACC, 0xFF1A4A6A, 0xFFEEEEFF,  // 40-43 cactus/coral/seabed/cloud
        0xFFCCEEFF, 0xFFCC2200, 0xFF774411, 0xFF553322,  // 44-47 mirror/spike/barrel/bookshelf
        0xFFFF6622, 0xFF886644, 0xFF9966AA, 0xFF777777,  // 48-51 fireplace/altar/warpstone/cracked
        0xFF1A3A7A, 0xFF5A8A4A, 0xFF997700, 0xFF997700,  // 52-55 deepwater/climbable/convey-r/l
        0xFF997700, 0xFF997700, 0xFF885500, 0xFFFFAA22,  // 56-59 convey-u/d/bombwall/torch
        0xFF8866BB, 0xFF443322, 0xFF447733, 0xFFAAFFBB,  // 60-63 crystalball/trapfloor/vine/glowing
        0xFF8B4513, 0xFF228B22,  // 64-65 house-door/house-exit
        // Buildings 66-73
        0xFFDEB887, 0xFFFFAACC, 0xFF888888, 0xFF666644, 0xFF8B7355, 0xFF885533, 0xFFDDCC88, 0xFFCC8844,
        // Transport 74-78
        0xFF8B6914, 0xFF8B4513, 0xFFCC9933, 0xFF8888CC, 0xFF887744,
        // Animals 79-86
        0xFF4488FF, 0xFF2266CC, 0xFFFF8844, 0xFFBB8844, 0xFFEEEEEE, 0xFF885522, 0xFF666666, 0xFF774422,
    };

    // =========================================================
    //  TILE NAMES — all 64
    // =========================================================
    public static final String[] TILE_NAMES = {
        "Void","Grass","GrassLt","Dirt","Stone","Wood","Water","Sand",
        "Brick","Tree","Chest","Lava","Snow","Cave","Night","Special",
        "Bridge","Path","Flower","Rock","Bush","Door","StairUp","StairDn",
        "Sign","Fence","Roof","WallTop","FloorW","FloorT","Carpet","Ice",
        // NEW
        "Swamp","QuickSand","Crystal","Volcano","Telepad","Pressure","Mud","TallGrass",
        "Cactus","Coral","Seabed","Cloud","Mirror","Spike","Barrel","Bookshelf",
        "Fireplace","Altar","WarpStone","Cracked","DeepWater","Climbable",
        "ConveyR","ConveyL","ConveyU","ConveyD","BombWall","Torch",
        "CrystalBall","TrapFloor","Vine","GlowOrb",
        "HouseDoor","HouseExit",
        "Shop","Inn","CastleWall","CastleGate","Tower","Ruin","Temple","HouseWall",
        "Boat","Cart","Horse","Airship","Raft",
        "Bird","Fish","Cat","Dog","Rabbit","Deer","Wolf","Bear"
    };

    // =========================================================
    //  COLLISION TABLE — all 64
    // =========================================================
    public static final int[] TILE_COLLISION = {
        COL_NONE,                                    // 0  void
        COL_NONE,                                    // 1  grass
        COL_NONE,                                    // 2  grass light
        COL_NONE,                                    // 3  dirt
        COL_SOLID|COL_PICKUP|COL_THROWABLE,          // 4  stone
        COL_SOLID|COL_PICKUP|COL_THROWABLE,          // 5  wood
        COL_WATER,                                   // 6  water
        COL_NONE,                                    // 7  sand
        COL_SOLID,                                   // 8  brick
        COL_SOLID,                                   // 9  tree
        COL_INTERACT|COL_PICKUP,                     // 10 chest
        COL_DAMAGE,                                  // 11 lava
        COL_NONE,                                    // 12 snow
        COL_NONE,                                    // 13 cave
        COL_NONE,                                    // 14 night
        COL_INTERACT,                                // 15 special
        COL_NONE,                                    // 16 bridge
        COL_NONE,                                    // 17 path
        COL_NONE,                                    // 18 flower
        COL_SOLID|COL_PUSHABLE,                      // 19 rock
        COL_SOLID,                                   // 20 bush
        COL_INTERACT,                                // 21 door
        COL_INTERACT,                                // 22 stairs up
        COL_INTERACT,                                // 23 stairs down
        COL_INTERACT,                                // 24 sign
        COL_SOLID,                                   // 25 fence
        COL_SOLID,                                   // 26 roof
        COL_SOLID,                                   // 27 wall top
        COL_NONE,                                    // 28 wood floor
        COL_NONE,                                    // 29 tile floor
        COL_NONE,                                    // 30 carpet
        COL_SLIPPERY,                                // 31 ice
        // NEW 32-63
        COL_SLOW|COL_DAMAGE,                         // 32 swamp
        COL_SINK,                                    // 33 quicksand
        COL_SOLID,                                   // 34 crystal
        COL_DAMAGE,                                  // 35 volcano
        COL_INTERACT,                                // 36 telepad
        COL_INTERACT,                                // 37 pressure plate
        COL_SLOW,                                    // 38 mud
        COL_NONE,                                    // 39 tall grass (encounter+)
        COL_SOLID|COL_DAMAGE,                        // 40 cactus
        COL_SOLID,                                   // 41 coral
        COL_NONE,                                    // 42 seabed
        COL_NONE,                                    // 43 cloud
        COL_SOLID,                                   // 44 mirror
        COL_DAMAGE,                                  // 45 spike trap
        COL_PUSHABLE|COL_DESTRUCTIBLE,               // 46 barrel
        COL_INTERACT|COL_SOLID,                      // 47 bookshelf
        COL_INTERACT,                                // 48 fireplace
        COL_SAVE_POINT|COL_INTERACT,                 // 49 altar (save point)
        COL_INTERACT,                                // 50 warp stone
        COL_SOLID|COL_DESTRUCTIBLE,                  // 51 cracked wall
        COL_WATER|COL_BOAT_REQ,                      // 52 deep water
        COL_CLIMB,                                   // 53 climbable
        COL_CONVEYOR,                                // 54 conveyor right
        COL_CONVEYOR,                                // 55 conveyor left
        COL_CONVEYOR,                                // 56 conveyor up
        COL_CONVEYOR,                                // 57 conveyor down
        COL_SOLID|COL_DESTRUCTIBLE,                  // 58 bomb wall
        COL_SOLID,                                   // 59 torch (wall decoration)
        COL_INTERACT,                                // 60 crystal ball
        COL_HIDDEN_TRAP,                             // 61 trap floor
        COL_CLIMB|COL_SLOW,                          // 62 vine
        COL_NONE,                                    // 63 glowing orb
        COL_INTERACT,                                // 64 house door
        COL_INTERACT,                                // 65 house exit
        // Buildings 66-73
        COL_INTERACT,                                // 66 shop
        COL_INTERACT,                                // 67 inn
        COL_SOLID,                                   // 68 castle wall
        COL_INTERACT,                                // 69 castle gate
        COL_SOLID,                                   // 70 tower
        COL_SOLID,                                   // 71 ruin
        COL_INTERACT,                                // 72 temple
        COL_SOLID,                                   // 73 house wall
        // Transport 74-78
        COL_INTERACT,                                // 74 boat
        COL_INTERACT | COL_PUSHABLE,                 // 75 cart
        COL_INTERACT,                                // 76 horse
        COL_INTERACT,                                // 77 airship
        COL_INTERACT,                                // 78 raft
        // Animals 79-86
        COL_NONE,                                    // 79 bird
        COL_NONE,                                    // 80 fish
        COL_INTERACT,                                // 81 cat
        COL_INTERACT,                                // 82 dog
        COL_NONE,                                    // 83 rabbit
        COL_NONE,                                    // 84 deer
        COL_DAMAGE,                                  // 85 wolf
        COL_DAMAGE,                                  // 86 bear
    };

    // =========================================================
    //  ANIMATION TABLE — all 64
    // =========================================================
    public static final int[] TILE_ANIMATION = {
        ANIM_NONE,
        ANIM_WALK_DUST, ANIM_WALK_DUST,
        ANIM_WALK_DUST|ANIM_FOOTPRINT,
        ANIM_NONE, ANIM_NONE,
        ANIM_SWIM|ANIM_WALK_SPLASH|ANIM_WAVE,
        ANIM_FOOTPRINT,
        ANIM_NONE, ANIM_NONE,
        ANIM_SPARKLE,
        ANIM_BURN,
        ANIM_FOOTPRINT|ANIM_FREEZE,
        ANIM_DRIP,
        ANIM_NONE, ANIM_SPARKLE,
        ANIM_NONE, ANIM_WALK_DUST,
        ANIM_WALK_DUST, ANIM_NONE,
        ANIM_NONE, ANIM_NONE,
        ANIM_NONE, ANIM_NONE,
        ANIM_NONE, ANIM_NONE,
        ANIM_NONE, ANIM_NONE,
        ANIM_NONE, ANIM_NONE,
        ANIM_NONE, ANIM_FREEZE,
        // NEW 32-63
        ANIM_WALK_SPLASH|ANIM_DRIP,  // 32 swamp
        ANIM_FOOTPRINT,               // 33 quicksand
        ANIM_SPARKLE|ANIM_GLOW,       // 34 crystal
        ANIM_BURN|ANIM_SHAKE,         // 35 volcano
        ANIM_GLOW,                    // 36 telepad
        ANIM_NONE,                    // 37 pressure
        ANIM_FOOTPRINT,               // 38 mud
        ANIM_WALK_DUST,               // 39 tall grass
        ANIM_NONE,                    // 40 cactus
        ANIM_BUBBLE,                  // 41 coral
        ANIM_BUBBLE,                  // 42 seabed
        ANIM_NONE,                    // 43 cloud
        ANIM_SPARKLE,                 // 44 mirror
        ANIM_NONE,                    // 45 spike
        ANIM_NONE,                    // 46 barrel
        ANIM_NONE,                    // 47 bookshelf
        ANIM_BURN,                    // 48 fireplace
        ANIM_GLOW|ANIM_SPARKLE,       // 49 altar
        ANIM_GLOW,                    // 50 warp stone
        ANIM_NONE,                    // 51 cracked
        ANIM_WAVE,                    // 52 deep water
        ANIM_NONE,                    // 53 climbable
        ANIM_NONE,                    // 54 convey R
        ANIM_NONE,                    // 55 convey L
        ANIM_NONE,                    // 56 convey U
        ANIM_NONE,                    // 57 convey D
        ANIM_NONE,                    // 58 bomb wall
        ANIM_BURN|ANIM_GLOW,          // 59 torch
        ANIM_GLOW,                    // 60 crystal ball
        ANIM_NONE,                    // 61 trap floor
        ANIM_NONE,                    // 62 vine
        ANIM_GLOW|ANIM_SPARKLE,       // 63 glowing orb
        ANIM_SPARKLE,                  // 64 house door
        ANIM_NONE,                     // 65 house exit
        // Buildings 66-73
        ANIM_SPARKLE,                  // 66 shop
        ANIM_NONE,                     // 67 inn
        ANIM_NONE,                     // 68 castle wall
        ANIM_NONE,                     // 69 castle gate
        ANIM_NONE,                     // 70 tower
        ANIM_NONE,                     // 71 ruin
        ANIM_GLOW,                     // 72 temple
        ANIM_NONE,                     // 73 house wall
        // Transport 74-78 — all animated
        ANIM_WAVE,                     // 74 boat (sail)
        ANIM_WALK_DUST,                // 75 cart (wheel dust)
        ANIM_WALK_DUST,                // 76 horse (trot)
        ANIM_NONE,                     // 77 airship (propeller)
        ANIM_WAVE,                     // 78 raft
        // Animals 79-86 — all animated
        ANIM_NONE,                     // 79 bird (flap)
        ANIM_SWIM,                     // 80 fish (swim)
        ANIM_NONE,                     // 81 cat
        ANIM_WALK_DUST,                // 82 dog
        ANIM_WALK_DUST,                // 83 rabbit
        ANIM_WALK_DUST,                // 84 deer
        ANIM_WALK_DUST,                // 85 wolf
        ANIM_WALK_DUST,                // 86 bear
    };

    // =========================================================
    //  LIGHT EMISSION (0=none, 255=full bright) — NEW
    // =========================================================
    public static final int[] TILE_LIGHT_EMIT = {
        0,0,0,0,0,0,0,0,0,0,
        0,  // chest — no light
        40, // lava — warm glow
        0,0,0,
        20, // special — glow
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
        0,  // 31 ice
        0,0,0, // 32-34
        80, // 35 volcano
        100,// 36 telepad
        0,0,0,0,0,0,0,0,0,0,0,0,
        30, // 48 fireplace
        60, // 49 altar
        50, // 50 warp stone
        0,0,0,0,0,0,0,
        120,// 59 torch
        40, // 60 crystal ball
        0,0,
        200,// 63 glowing orb
        10, // 64 house door
        5,  // 65 house exit
        20, // 66 shop
        10, // 67 inn
        0,  // 68 castle wall
        0,  // 69 castle gate
        5,  // 70 tower
        0,  // 71 ruin
        40, // 72 temple
        0,  // 73 house wall
        0,0,0,0,0,  // 74-78 transport
        0,0,0,0,0,0,0,0  // 79-86 animals
    };

    // =========================================================
    //  Z-HEIGHT for pseudo-3D (0=flat, 1=half, 2=full) — NEW
    // =========================================================
    public static final int[] TILE_HEIGHT = {
        0,0,0,0,2,2,0,0,2,2,
        0,0,0,0,0,0,0,0,0,2,
        2,2,0,0,1,2,2,2,0,0,
        0,0,0,0,2,0,0,0,0,0,
        2,2,0,0,2,0,1,2,1,1,
        0,2,0,1,0,0,0,0,2,1,
        1,0,1,0,
        2,2   // 64 house-door, 65 house-exit
        ,2,2,2,2,2,1,2,2  // 66-73 buildings
        ,1,1,1,2,0         // 74-78 transport
        ,0,0,0,0,0,0,0,0   // 79-86 animals
    };

    public static final int[] TILE_ENCOUNTER_MOD = {
        0,0,0,0,0,0,0,0,0,0,
        0,0,0,20,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,0,
        0,-10,0,0,0,0,0,0,0,
        30, // 39 tall grass
        0,0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,
        0,0  // 64 house-door, 65 house-exit
        ,0,0,0,0,0,0,0,0   // 66-73 buildings
        ,0,0,0,0,0           // 74-78 transport
        ,0,0,0,0,0,20,30,40  // 79-86 animals (wolf+30, bear+40)
    };

    // =========================================================
    //  HELPER METHODS
    // =========================================================
    public static boolean isSolid(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_SOLID) != 0;
    }
    public static boolean isWater(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_WATER) != 0;
    }
    public static boolean isDamage(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_DAMAGE) != 0;
    }
    public static boolean isPickup(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_PICKUP) != 0;
    }
    public static boolean isThrowable(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_THROWABLE) != 0;
    }
    public static boolean isPushable(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_PUSHABLE) != 0;
    }
    public static boolean isInteract(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_INTERACT) != 0;
    }
    public static boolean isSlippery(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_SLIPPERY) != 0;
    }
    public static boolean isSlow(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_SLOW) != 0;
    }
    public static boolean isClimbable(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_CLIMB) != 0;
    }
    public static boolean isDestructible(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_DESTRUCTIBLE) != 0;
    }
    public static boolean isSavePoint(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_SAVE_POINT) != 0;
    }
    public static boolean isConveyor(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_CONVEYOR) != 0;
    }
    public static boolean requiresBoat(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        return (TILE_COLLISION[tile] & COL_BOAT_REQ) != 0;
    }
    /** Returns conveyor direction: 0=R 1=L 2=U 3=D, or -1 if not conveyor. */
    public static int getConveyorDir(int tile) {
        switch (tile) {
            case TILE_CONVEY_R: return 0;
            case TILE_CONVEY_L: return 1;
            case TILE_CONVEY_U: return 2;
            case TILE_CONVEY_D: return 3;
            default: return -1;
        }
    }
    public static boolean hasWalkAnim(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return false;
        int a = TILE_ANIMATION[tile];
        return (a & (ANIM_WALK_DUST|ANIM_FOOTPRINT|ANIM_WALK_SPLASH)) != 0;
    }
    public static int getColor(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return 0xFFFF00FF;
        return TILE_COLORS[tile];
    }
    public static String getName(int tile) {
        if (tile < 0 || tile >= MAX_TILES) return "?";
        return TILE_NAMES[tile];
    }
    public static int getLightEmit(int tile) {
        if (tile < 0 || tile >= TILE_LIGHT_EMIT.length) return 0;
        return TILE_LIGHT_EMIT[tile];
    }
    public static int getEncounterMod(int tile) {
        if (tile < 0 || tile >= TILE_ENCOUNTER_MOD.length) return 0;
        return TILE_ENCOUNTER_MOD[tile];
    }
}
