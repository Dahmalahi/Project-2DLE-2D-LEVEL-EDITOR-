import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.rms.*;
import java.io.*;

public class EditorCanvas extends GameCanvas implements Runnable, CommandListener {

    // -------------------------------------------------
    //  MAP CONFIGURATION
    // -------------------------------------------------
    private static final int MAP_COLS       = 32;
    private static final int MAP_ROWS       = 24;
    private static final int NUM_LAYERS     = 3;
    private static final int MAX_TILES      = TileData.MAX_TILES;
    private static final int SRC_TILE_SIZE  = 16;
    private static final int MAX_UNDO       = 10;
    private static final int MAX_SAVE_SLOTS = 5;

    // Layer names
    private static final String[] LAYER_NAMES = {
        "BG", "Wall", "Obj"
    };

    // -------------------------------------------------
    //  SCREEN ADAPTATION
    // -------------------------------------------------
    private int screenW, screenH;
    private int tileSize;
    private int baseTileSize;
    private int zoomLevel;
    private int offsetX, offsetY;
    private int viewCols, viewRows;
    private int hudHeight;
    private int paletteHeight;

    // -------------------------------------------------
    //  MAP DATA (3 layers)
    // -------------------------------------------------
    private int[][][] mapData;
    private int cursorX, cursorY;
    private int scrollX, scrollY;
    private int currentTile;
    private int currentLayer;

    // -------------------------------------------------
    //  EDITOR STATE
    // -------------------------------------------------
    private boolean showGrid;
    private boolean painting;
    private int paletteScroll;

    // -------------------------------------------------
    //  NPC MODE
    // -------------------------------------------------
    private boolean npcMode;
    private int currentNPCType;

    // -------------------------------------------------
    //  DRAW TOOLS  (NEW v2.0)
    // -------------------------------------------------
    // Tool IDs
    private static final int TOOL_PENCIL    = 0;  // single tile paint
    private static final int TOOL_FILL      = 1;  // flood fill
    private static final int TOOL_RECT      = 2;  // filled rectangle
    private static final int TOOL_RECT_OUTLINE = 3; // hollow rectangle
    private static final int TOOL_LINE      = 4;  // straight line (Bresenham)
    private static final int TOOL_ELLIPSE   = 5;  // ellipse outline
    private static final int TOOL_ELLIPSE_FILL = 6; // filled ellipse
    private static final int TOOL_ERASER    = 7;  // erase to tile 0
    private static final int TOOL_EYEDROPPER= 8;  // pick tile under cursor
    private static final int TOOL_STAMP     = 9;  // paste clipboard region
    private static final int TOOL_SELECT    = 10; // rectangular selection / copy
    private static final int TOOL_MOVE      = 11; // move selected region
    private static final int TOOL_DIAMOND   = 12; // diamond / rotated-square shape
    private static final int TOOL_SCATTER   = 13; // random scatter paint
    private static final int NUM_TOOLS      = 14;

    private static final String[] TOOL_NAMES = {
        "Pencil", "Fill", "Rect", "RectOut",
        "Line", "Ellipse", "EllFill", "Eraser",
        "Picker", "Stamp", "Select", "Move",
        "Diamond", "Scatter"
    };

    private int  currentTool;   // active TOOL_* constant
    private int  prevTool;      // tool to restore after eyedropper

    // Shape tool anchor (where the drag started)
    private int  toolAnchorX;
    private int  toolAnchorY;
    private boolean toolDragging;  // user is dragging a shape

    // Preview buffer: tiles to draw *before* committing
    // We store the preview as a sparse list of (x,y,tile) changes
    private static final int MAX_PREVIEW = 512;
    private int[] previewX    = new int[MAX_PREVIEW];
    private int[] previewY    = new int[MAX_PREVIEW];
    private int[] previewTile = new int[MAX_PREVIEW];
    private int   previewCount;

    // Selection rectangle (for TOOL_SELECT and TOOL_STAMP)
    private int selX, selY, selW, selH;  // selection region
    private boolean hasSelection;

    // Clipboard (copied region, flat array [layer][row][col])
    private int[] clipboard;     // flat: layer * selH * selW + row * selW + col
    private int   clipW, clipH;  // dimensions
    private boolean hasClipboard;

    // Scatter tool density (1-10)
    private int scatterDensity = 4;

    // Tool panel visibility
    private boolean showToolPanel = true;

    // -------------------------------------------------
    //  UNDO / REDO SYSTEM
    // -------------------------------------------------
    private int[] undoLayer;
    private int[] undoX;
    private int[] undoY;
    private int[] undoOldTile;
    private int[] undoNewTile;
    private int undoHead;
    private int undoCount;
    private int redoCount;

    // -------------------------------------------------
    //  MANAGERS
    // -------------------------------------------------
    private NPCManager npcMgr;
    private PlayEngine playEngine;
    private DialogueEditor dialogueEditor;
    private FileManager fileMgr;
    private ScriptManager scriptMgr;      // script registry
    private EventSystem   eventSys;       // script runtime executor
    private MapLinkSystem linkSys;        // door/warp/stair transitions

    // -------------------------------------------------
    //  PROJECT INFO
    // -------------------------------------------------
    private String projectPath;
    private String projectName;
    private String currentLevelName;

    // -------------------------------------------------
    //  RESOURCES
    // -------------------------------------------------
    private Image tilesetImage;
    private Image[] scaledTiles;
    private Image heroSprite;
    private Image[] npcSprites;
    private int heroSpriteSize;

    // -------------------------------------------------
    //  THREADING
    // -------------------------------------------------
    private Thread thread;
    private volatile boolean running;
    private long lastInputTime;
    private static final int INPUT_DELAY = 80;

    // -------------------------------------------------
    //  UI STATE
    // -------------------------------------------------
    private String statusMsg;
    private long statusTimer;
    private long animTimer;
    private int animFrame;
    private boolean menuOpen;
    private int menuSelection;

    // -------------------------------------------------
    //  MENU OPTIONS
    // -------------------------------------------------
    private static final int MENU_SAVE       = 0;
    private static final int MENU_LOAD       = 1;
    private static final int MENU_UNDO       = 2;
    private static final int MENU_REDO       = 3;
    private static final int MENU_GRID       = 4;
    private static final int MENU_FILL       = 5;
    private static final int MENU_CLEAR      = 6;
    private static final int MENU_EXPORT_NPC = 7;
    private static final int MENU_PLAYTEST   = 8;
    private static final int MENU_TOOL_PANEL = 9;
    private static final int MENU_COPY       = 10;
    private static final int MENU_PASTE      = 11;
    private static final int MENU_SELECT_ALL = 12;
    private static final int MENU_EXPORT_GAME= 13;  // compile to .2dip
    private static final int MENU_PLAY_GAME  = 14;  // run a .2dip file
    private static final int MENU_EXIT       = 15;
    private static final int MENU_COUNT      = 16;

    private static final String[] MENU_LABELS = {
        "Save Map",
        "Load Map",
        "Undo",
        "Redo",
        "Toggle Grid",
        "Fill (Bucket)",
        "New Map",
        "Export NPCs",
        "Playtest",
        "Toggle Tools",
        "Copy Region",
        "Paste",
        "Select All",
        "Export .2dip",
        "Play .2dip Game",
        "Exit"
    };

    // Save slot selection
    private boolean selectingSlot;
    private boolean savingMode;
    private int slotSelection;

    // Level selection for loading
    private boolean selectingLevel;
    private String[] levelList;
    private int levelSelection;

    // -------------------------------------------------
    //  COMMANDS (LSK / RSK)
    // -------------------------------------------------
    private Command cmdMenu;
    private Command cmdZoomIn;
    private Command cmdZoomOut;
    private Command cmdBack;
    private LevelEditorMIDlet midlet;

    // -------------------------------------------------
    //  PLAYTEST MODE
    // -------------------------------------------------
    private boolean playtestMode;

    // =================================================
    //  CONSTRUCTOR
    // =================================================
    public EditorCanvas(LevelEditorMIDlet midlet, String projectPath) {
        super(true);
        this.midlet = midlet;
        this.projectPath = projectPath;
        setFullScreenMode(true);

        // Extract project name from path
        if (projectPath != null) {
            int lastSlash = projectPath.lastIndexOf('/');
            if (lastSlash > 0) {
                int prevSlash = projectPath.lastIndexOf('/', lastSlash - 1);
                if (prevSlash >= 0) {
                    projectName = projectPath.substring(prevSlash + 1, lastSlash);
                } else {
                    projectName = projectPath.substring(0, lastSlash);
                }
            } else {
                projectName = "Default";
            }
        } else {
            projectName = "Default";
        }
        currentLevelName = "level01";

        // Initialize managers
        npcMgr = new NPCManager();
        playEngine = new PlayEngine();
        dialogueEditor = new DialogueEditor(midlet, npcMgr);

        // Get file manager from project manager
        if (midlet.getProjectManager() != null) {
            fileMgr = midlet.getProjectManager().getFileManager();
        } else {
            fileMgr = new FileManager();
        }

        // Initialize script manager and load existing scripts for this project
        scriptMgr = new ScriptManager();
        if (fileMgr != null && fileMgr.isAvailable() && projectName != null) {
            scriptMgr.loadFromProject(fileMgr, projectName);
        }

        // Initialize event/script runtime
        eventSys = new EventSystem();

        // Initialize map link system
        linkSys = new MapLinkSystem();

        // Initialize map (3 layers)
        mapData = new int[NUM_LAYERS][MAP_ROWS][MAP_COLS];
        cursorX = 0;
        cursorY = 0;
        scrollX = 0;
        scrollY = 0;
        currentTile = 1;
        currentLayer = 0;
        showGrid = true;
        painting = false;
        paletteScroll = 0;
        zoomLevel = 0;

        // NPC mode
        npcMode = false;
        currentNPCType = NPCManager.NPC_FRIENDLY;

        // Draw tools
        currentTool  = TOOL_PENCIL;
        prevTool     = TOOL_PENCIL;
        toolDragging = false;
        toolAnchorX  = 0;
        toolAnchorY  = 0;
        previewCount = 0;
        hasSelection = false;
        hasClipboard = false;
        selX = selY = selW = selH = 0;
        clipW = clipH = 0;

        // Undo system
        undoLayer = new int[MAX_UNDO];
        undoX = new int[MAX_UNDO];
        undoY = new int[MAX_UNDO];
        undoOldTile = new int[MAX_UNDO];
        undoNewTile = new int[MAX_UNDO];
        undoHead = 0;
        undoCount = 0;
        redoCount = 0;

        // Menu state
        menuOpen = false;
        menuSelection = 0;
        selectingSlot = false;
        slotSelection = 0;
        selectingLevel = false;

        // Playtest
        playtestMode = false;

        // Load tileset (optional)
        try {
            tilesetImage = Image.createImage("/tiles.png");
        } catch (Exception e) {
            tilesetImage = null;
        }

        // Load custom assets
        loadCustomAssets();

        // Softkey commands
        cmdMenu    = new Command("Menu",     Command.SCREEN, 1);
        cmdZoomIn  = new Command("Zoom +",   Command.SCREEN, 2);
        cmdZoomOut = new Command("Zoom -",   Command.SCREEN, 3);
        cmdBack    = new Command("Back",     Command.BACK,   1);
        addCommand(cmdMenu);
        addCommand(cmdZoomIn);
        addCommand(cmdZoomOut);
        addCommand(cmdBack);
        setCommandListener(this);

        recalcLayout();
    }

    // Alternate constructor for RecordStore-only mode
    public EditorCanvas(LevelEditorMIDlet midlet) {
        this(midlet, null);
    }

    // -------------------------------------------------
    //  CUSTOM ASSET LOADING
    // -------------------------------------------------
    private void loadCustomAssets() {
        if (fileMgr == null || !fileMgr.isAvailable() || projectName == null) {
            return;
        }

        // Try to load hero sprite
        heroSprite = fileMgr.loadProjectAsset(projectName, "hero.png");
        if (heroSprite != null) {
            heroSpriteSize = heroSprite.getWidth() / 4;
        }

        // Try to load NPC sprites
        npcSprites = new Image[5];
        String[] npcFiles = {
            "npc_friendly.png",
            "npc_shop.png",
            "npc_quest.png",
            "enemy.png",
            "boss.png"
        };
        for (int i = 0; i < npcFiles.length; i++) {
            npcSprites[i] = fileMgr.loadProjectAsset(projectName, npcFiles[i]);
        }
    }

    // =================================================
    //  SCREEN ADAPTATION
    // =================================================
    protected void sizeChanged(int w, int h) {
        super.sizeChanged(w, h);
        recalcLayout();
    }

    private void recalcLayout() {
        screenW = getWidth();
        screenH = getHeight();

        // HUD at top, palette at bottom
        hudHeight = Math.max(12, screenH / 14);
        paletteHeight = Math.max(18, screenH / 10);

        int availH = screenH - hudHeight - paletteHeight;
        int availW = screenW;

        // Calculate base tile size
        int maxCols = Math.min(MAP_COLS, 16);
        int maxRows = Math.min(MAP_ROWS, 12);

        int tsW = availW / maxCols;
        int tsH = availH / maxRows;
        baseTileSize = Math.min(tsW, tsH);

        if (baseTileSize < 6) baseTileSize = 6;
        if (baseTileSize > 20) baseTileSize = 20;

        // Apply zoom  (0=x1  1=x2  2=x3)
        if (zoomLevel == 2) {
            tileSize = baseTileSize * 3;
            if (tileSize > 48) tileSize = 48;
        } else if (zoomLevel == 1) {
            tileSize = baseTileSize * 2;
            if (tileSize > 32) tileSize = 32;
        } else {
            tileSize = baseTileSize;
        }

        // Visible tiles
        viewCols = availW / tileSize;
        viewRows = availH / tileSize;
        if (viewCols > MAP_COLS) viewCols = MAP_COLS;
        if (viewRows > MAP_ROWS) viewRows = MAP_ROWS;
        if (viewCols < 4) viewCols = 4;
        if (viewRows < 4) viewRows = 4;

        // Centering offset
        offsetX = (availW - viewCols * tileSize) / 2;
        offsetY = hudHeight + (availH - viewRows * tileSize) / 2;
        if (offsetX < 0) offsetX = 0;
        if (offsetY < hudHeight) offsetY = hudHeight;

        // Build scaled tiles
        buildScaledTiles();
    }

    // =================================================
    //  TILE GENERATION / SCALING
    // =================================================
    private void buildScaledTiles() {
        scaledTiles = new Image[MAX_TILES];

        for (int i = 0; i < MAX_TILES; i++) {
            if (tilesetImage != null) {
                int tilesPerRow = tilesetImage.getWidth() / SRC_TILE_SIZE;
                if (tilesPerRow < 1) tilesPerRow = 1;
                int srcX = (i % tilesPerRow) * SRC_TILE_SIZE;
                int srcY = (i / tilesPerRow) * SRC_TILE_SIZE;

                if (srcX + SRC_TILE_SIZE <= tilesetImage.getWidth()
                    && srcY + SRC_TILE_SIZE <= tilesetImage.getHeight()) {

                    if (tileSize == SRC_TILE_SIZE) {
                        scaledTiles[i] = Image.createImage(
                            tilesetImage, srcX, srcY,
                            SRC_TILE_SIZE, SRC_TILE_SIZE,
                            Sprite.TRANS_NONE);
                    } else {
                        scaledTiles[i] = scaleTile(tilesetImage,
                            srcX, srcY, SRC_TILE_SIZE, SRC_TILE_SIZE,
                            tileSize, tileSize);
                    }
                    continue;
                }
            }
            // Fallback: generate tile
            scaledTiles[i] = createGeneratedTile(i, tileSize);
        }
    }

    private Image scaleTile(Image src, int sx, int sy, int sw, int sh,
                            int dw, int dh) {
        Image region = Image.createImage(src, sx, sy, sw, sh,
                                         Sprite.TRANS_NONE);
        int[] srcPx = new int[sw * sh];
        region.getRGB(srcPx, 0, sw, 0, 0, sw, sh);

        int[] dstPx = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            int sRow = (dy * sh) / dh;
            for (int dx = 0; dx < dw; dx++) {
                int sCol = (dx * sw) / dw;
                dstPx[dy * dw + dx] = srcPx[sRow * sw + sCol];
            }
        }
        return Image.createRGBImage(dstPx, dw, dh, true);
    }

    // =================================================
    //  PIXEL-ART TILE GENERATOR
    //
    //  Each tile is defined as an 8x8 palette sprite:
    //   - A char[] of 64 characters, each maps to a colour
    //   - Palette: ' '=base  'L'=light  'D'=dark  'S'=shadow
    //              '0'=black '1'-'9'=fixed colours  'T'=transparent
    //  The 8x8 sprite is nearest-neighbour scaled to tileSize.
    //  Animated tiles use animFrame to choose a sprite variant.
    // =================================================

    // ── Colour palette indices ──────────────────────────────
    private static final int PAL_BASE  = 0;
    private static final int PAL_LT    = 1;  // lighter
    private static final int PAL_LT2   = 2;  // lightest
    private static final int PAL_DK    = 3;  // darker
    private static final int PAL_DK2   = 4;  // darkest
    private static final int PAL_BLK   = 5;  // black
    private static final int PAL_WHT   = 6;  // white
    private static final int PAL_C1    = 7;  // accent 1
    private static final int PAL_C2    = 8;  // accent 2
    private static final int PAL_C3    = 9;  // accent 3
    private static final int PAL_TRAN  = 10; // transparent (use tile base)

    // ── Map char → palette index ────────────────────────────
    private static int charToPal(char c) {
        switch (c) {
            case ' ': return PAL_BASE;
            case 'L': return PAL_LT;
            case 'l': return PAL_LT2;
            case 'D': return PAL_DK;
            case 'd': return PAL_DK2;
            case '0': return PAL_BLK;
            case 'W': return PAL_WHT;
            case '1': return PAL_C1;
            case '2': return PAL_C2;
            case '3': return PAL_C3;
            default:  return PAL_BASE;
        }
    }

    // ── Build palette from base colour ──────────────────────
    private int[] buildPalette(int base, int c1, int c2, int c3) {
        int[] p = new int[11];
        p[PAL_BASE] = base;
        p[PAL_LT]   = lighten(base, 40);
        p[PAL_LT2]  = lighten(base, 70);
        p[PAL_DK]   = darken(base, 40);
        p[PAL_DK2]  = darken(base, 70);
        p[PAL_BLK]  = 0xFF111111;
        p[PAL_WHT]  = 0xFFEEEEEE;
        p[PAL_C1]   = c1;
        p[PAL_C2]   = c2;
        p[PAL_C3]   = c3;
        p[PAL_TRAN] = base;
        return p;
    }

    // ── Render an 8x8 sprite string into pixels[] ───────────
    // sprite: 64-char string, row-major, top-left first
    private void renderSprite(int[] pixels, int size, String sprite, int[] pal) {
        for (int sy = 0; sy < 8; sy++) {
            for (int sx = 0; sx < 8; sx++) {
                char c = sprite.charAt(sy * 8 + sx);
                int col = pal[charToPal(c)];
                // Scale: fill the block of pixels corresponding to this sprite pixel
                int x0 = sx * size / 8;
                int x1 = (sx + 1) * size / 8;
                int y0 = sy * size / 8;
                int y1 = (sy + 1) * size / 8;
                for (int py = y0; py < y1; py++) {
                    for (int px = x0; px < x1; px++) {
                        if (px < size && py < size) {
                            pixels[py * size + px] = col;
                        }
                    }
                }
            }
        }
    }

    // ── Sprite database ─────────────────────────────────────
    // Each sprite is an 8x8 = 64-char string.
    // Multiple variants separated for animated tiles.

    // GRASS (2 animation frames)
    private static final String[] SPR_GRASS = {
        " L  L   " + "L  L  L " + " DLD  LD" + "D  D   D" +
        " L  L   " + "L  L  L " + " DLD  LD" + "D  D   D",
        "  L  L  " + " L  L  L" + "DLD  LDL" + "  D   D " +
        "  L  L  " + " L  L  L" + "DLD  LDL" + "  D   D ",
    };
    // DIRT
    private static final String SPR_DIRT =
        "D   D   " + " D   D  " + "   D   D" + "D  D D  " +
        " D   D  " + "  D D  D" + "D   D  D" + " D D  D ";
    // STONE / ROCK
    private static final String SPR_STONE =
        "DDDDDDDD" + "DLL  LLD" + "DL LL LD" + "DL LL LD" +
        "DL    LD" + "DLL  LLD" + "DL    LD" + "DDDDDDDD";
    // WATER (2 anim frames)
    private static final String[] SPR_WATER = {
        "lll11lll" + "l1  1 1l" + "l 1  1 l" + "11 11 11" +
        "lll11lll" + "l1  1 1l" + "l 1  1 l" + "11 11 11",
        "11 11 11" + "l1  1 1l" + "l 1  1 l" + "lll11lll" +
        "11 11 11" + "l1  1 1l" + "l 1  1 l" + "lll11lll",
    };
    // SAND
    private static final String SPR_SAND =
        " L L L  " + "L L L L " + " L L L  " + "D D D D " +
        " D D D  " + "L L L L " + " L L L  " + "D D D D ";
    // WOOD / PLANKS
    private static final String SPR_WOOD =
        "LLLDDDLL" + "L  D  DL" + "L  D  DL" + "DDDDDDDD" +
        "LLLDDDLL" + "L  D  DL" + "L  D  DL" + "DDDDDDDD";
    // BRICK
    private static final String SPR_BRICK =
        "00000000" + "0LL L L0" + "0LL L L0" + "00000000" +
        "00000000" + "0L LL L0" + "0L LL L0" + "00000000";
    // TREE canopy + trunk
    private static final String SPR_TREE =
        "  1111  " + " 111111 " + "11111111" + "11111111" +
        " 111111 " + "  1122  " + "  0220  " + "  0220  ";
    // CHEST (gold box)
    private static final String SPR_CHEST =
        "D111111D" + "1WWWWWW1" + "1W1111W1" + "1WWWWWW1" +
        "1W0220W1" + "1W0220W1" + "1WWWWWW1" + "DDDDDDDD";
    // LAVA (2 frames)
    private static final String[] SPR_LAVA = {
        "1 11 1 1" + "11 1 11 " + "2111 112" + "12 111 1" +
        "1 11 1 1" + "111 1111" + "2 111 12" + "1 1 1 11",
        "11 1 11 " + "1 11 1 1" + "1 1 111 " + "2111 112" +
        "111 1111" + "1 11 1 1" + "12 111 1" + "1 1 1 1 ",
    };
    // SNOW
    private static final String SPR_SNOW =
        "lllWllll" + "lllWllll" + "WWlWWlWW" + "llWWWlll" +
        "llWlWlll" + "WWlWWlWW" + "lllWllll" + "lllWllll";
    // ICE
    private static final String SPR_ICE =
        "lllllllW" + "llllllWl" + "lllllWll" + "llllWlll" +
        "lllWllll" + "llWlllll" + "lWllllll" + "Wlllllll";
    // DOOR (wooden with window)
    private static final String SPR_DOOR =
        "D111111D" + "D1WWWW1D" + "D1W11W1D" + "D1WWWW1D" +
        "D1    1D" + "D1    1D" + "D1  2 1D" + "DDDDDDDD";
    // STAIRS UP
    private static final String SPR_STAIRS_UP =
        "DDDDDDDD" + "DDDDDDD " + "DDDDDD  " + "DDDDD   " +
        "DDDD    " + "DDD     " + "DD      " + "D       ";
    // STAIRS DOWN
    private static final String SPR_STAIRS_DN =
        "D       " + "DD      " + "DDD     " + "DDDD    " +
        "DDDDD   " + "DDDDDD  " + "DDDDDDD " + "DDDDDDDD";
    // SIGN (post + board)
    private static final String SPR_SIGN =
        "01111110" + "0D   D10" + "0D111D10" + "0DDDDDD0" +
        "   DD   " + "   DD   " + "   DD   " + "  D  D  ";
    // FENCE
    private static final String SPR_FENCE =
        "D D D D " + "DDDDDDDD" + "D D D D " + "D D D D " +
        "D D D D " + "D D D D " + "D D D D " + "D D D D ";
    // ROOF (tiles)
    private static final String SPR_ROOF =
        "1   1   " + "11  11  " + " 1D  1D " + "  DD  DD" +
        "1   1   " + "11  11  " + " 1D  1D " + "  DD  DD";
    // CARPET (ornate)
    private static final String SPR_CARPET =
        "11111111" + "1 L  L 1" + "1L 11 L1" + "1 1  1 1" +
        "1 1  1 1" + "1L 11 L1" + "1 L  L 1" + "11111111";
    // SPECIAL sparkle
    private static final String[] SPR_SPECIAL = {
        "   1    " + " 1 1 1  " + "  111   " + "1111111 " +
        "  111   " + " 1 1 1  " + "   1    " + "        ",
        "    1   " + "  1 1 1 " + "   111  " + " 1111111" +
        "   111  " + "  1 1 1 " + "    1   " + "        ",
    };
    // CAVE
    private static final String SPR_CAVE =
        "0000d000" + "0D  dD00" + "0D  dDD0" + "0D      " +
        "0D  d   " + "0D  dD  " + "0DD dDD0" + "00000000";
    // SWAMP
    private static final String[] SPR_SWAMP = {
        "D D D D " + "1D1D1D1D" + "11 1 11 " + "D1  1D1 " +
        " D1  1D " + "1 D  D 1" + "D1 D1 D1" + " D D D D",
        " D D D D" + "D1D1D1D1" + " 11 1 11" + "1D  1D1 " +
        "D 1  1 D" + " 1 D  D1" + "1D1 D1 D" + "D D D D ",
    };
    // QUICKSAND
    private static final String[] SPR_QUICKSAND = {
        "L L L L " + " LDL DL " + "L D L D " + "DL DL DL" +
        " D  D D " + "D D D  D" + " DL DLD " + "D DL LD ",
        " L L L L" + "LDL DL L" + " D L D L" + "L DL DLD" +
        "D  D D D" + " D D  DD" + "DL DLD D" + " DL LD D",
    };
    // CRYSTAL
    private static final String[] SPR_CRYSTAL = {
        "  1111  " + " 1llll1 " + "1lLLLl11" + "1lLWLl 1" +
        "1lLLLl  " + "11llll1 " + " 1111   " + "  1 1   ",
        "  1111  " + " 1llll1 " + "1lllll11" + "1lLWLl 1" +
        "1lllll  " + "11llll1 " + " 1111   " + "  1 1   ",
    };
    // VOLCANO
    private static final String[] SPR_VOLCANO = {
        "   11   " + "  1221  " + " 112221 " + " 12DDD1 " +
        "112 D211" + "12   221" + "1D   D21" + "DD   DD1",
        "   11   " + "  1111  " + " 112221 " + " 12D2D1 " +
        "112D2211" + "12   221" + "1    D21" + "DD   DD1",
    };
    // TELEPAD (glowing pad)
    private static final String[] SPR_TELEPAD = {
        "D111111D" + "1lllll 1" + "1l1 l 11" + "1ll 1l 1" +
        "1 l1ll 1" + "1 ll l 1" + "1 lllll1" + "D111111D",
        "D111111D" + "1 lllll1" + "11 l1l 1" + "1 1ll l1" +
        "1 ll1l 1" + "1 l ll 1" + "1lllll 1" + "D111111D",
    };
    // PRESSURE PLATE
    private static final String SPR_PRESSURE =
        "DDDDDDDD" + "DllllllD" + "Dl    lD" + "Dl    lD" +
        "Dl    lD" + "Dl    lD" + "DllllllD" + "DDDDDDDD";
    // MUD
    private static final String SPR_MUD =
        "D D D D " + " dD dD d" + "D dD dD " + " D d Dd " +
        "dD D dD " + "D dD D d" + " D dD D " + "D  D  D ";
    // TALL GRASS
    private static final String SPR_TALL_GRASS =
        " 1 1 1 1" + "1 1 1 1 " + " 1D1D1D1" + "1D 1 1 D" +
        "D  D D  " + " D D  D " + "D  D D  " + "DDDDDDDD";
    // CACTUS
    private static final String SPR_CACTUS =
        "   11   " + "  1  1  " + "1 1  1 1" + "1 1  1 1" +
        "1 1111 1" + "  1  1  " + "  1  1  " + "  1  1  ";
    // SPIKE TRAP
    private static final String SPR_SPIKE =
        "1 1 1 1 " + "11111111" + " WWWWWW " + "WWWWWWWW" +
        " WWWWWW " + "11111111" + "1 1 1 1 " + "DDDDDDDD";
    // BARREL
    private static final String SPR_BARREL =
        " D1111D " + "D1LLLL1D" + "D000000D" + "D1LLLL1D" +
        "D1LLLL1D" + "D000000D" + "D1LLLL1D" + " D1111D ";
    // BOOKSHELF
    private static final String SPR_BOOKSHELF =
        "DDDDDDDD" + "D1 2 3 D" + "D1 2 3 D" + "D111111D" +
        "D 3 1 2D" + "D 3 1 2D" + "D111111D" + "DDDDDDDD";
    // FIREPLACE (2 frames)
    private static final String[] SPR_FIREPLACE = {
        "D      D" + "D 1221 D" + "D112221D" + "D12  21D" +
        "D12  21D" + "DDDDDDDD" + "D1 DD 1D" + "DDDDDDDD",
        "D      D" + "D 2112 D" + "D221112D" + "D21  12D" +
        "D21  12D" + "DDDDDDDD" + "D2 DD 2D" + "DDDDDDDD",
    };
    // ALTAR (save point)
    private static final String[] SPR_ALTAR = {
        "  1WW1  " + " 1W  W1 " + "1W 11 W1" + "1W1221W1" +
        "1W1221W1" + "1W 11 W1" + " 1W  W1 " + "  DDDD  ",
        "  1lW1  " + " 1W  l1 " + "1W 11 W1" + "1W1221W1" +
        "1W1221W1" + "1W 11 W1" + " 1W  l1 " + "  DDDD  ",
    };
    // WARP STONE
    private static final String[] SPR_WARPSTONE = {
        " 011110 " + "01lll110" + "1lW  Wl1" + "1l W  l1" +
        "1l  W l1" + "1lW  Wl1" + "01lll110" + " 011110 ",
        " 011110 " + "01lll110" + "1lW  Wl1" + "1l  W l1" +
        "1l W  l1" + "1lW  Wl1" + "01lll110" + " 011110 ",
    };
    // CRACKED WALL
    private static final String SPR_CRACKED =
        "DDDDDDDD" + "D  D   D" + "D D   DD" + "DDDD DDD" +
        "D   D  D" + "DD D  DD" + "D  DD  D" + "DDDDDDDD";
    // DEEP WATER (2 frames)
    private static final String[] SPR_DEEP_WATER = {
        "ddd11ddd" + "d1  1 1d" + "d 1  1 d" + "11 11 11" +
        "d 1  1 d" + "d1  1 1d" + "ddd11ddd" + "11 11 11",
        "11 11 11" + "d1  1 1d" + "d 1  1 d" + "ddd11ddd" +
        "d 1  1 d" + "d1  1 1d" + "11 11 11" + "ddd11ddd",
    };
    // CONVEYOR RIGHT
    private static final String SPR_CONVEY_R =
        "DDDDDDDD" + "D      D" + "D  1   D" + "D 111  D" +
        "D11111 D" + "D 111  D" + "D  1   D" + "DDDDDDDD";
    // CONVEYOR LEFT
    private static final String SPR_CONVEY_L =
        "DDDDDDDD" + "D      D" + "D   1  D" + "D  111 D" +
        "D 11111D" + "D  111 D" + "D   1  D" + "DDDDDDDD";
    // CONVEYOR UP
    private static final String SPR_CONVEY_U =
        "DDDDDDDD" + "D  1   D" + "D 111  D" + "D1 1  1D" +
        "D  1   D" + "D  1   D" + "D  1   D" + "DDDDDDDD";
    // CONVEYOR DOWN
    private static final String SPR_CONVEY_D =
        "DDDDDDDD" + "D  1   D" + "D  1   D" + "D  1   D" +
        "D1 1  1D" + "D 111  D" + "D  1   D" + "DDDDDDDD";
    // BOMB WALL
    private static final String SPR_BOMB_WALL =
        "DDDDDDDD" + "D DD DD D" + "DD    DD" + "D  DD  D" +
        "D  DD  D" + "DD    DD" + "D DD DD D" + "DDDDDDDD";
    // TORCH (wall sconce)
    private static final String[] SPR_TORCH = {
        "   12   " + "  1221  " + "  1 21  " + "  DDD   " +
        "   D    " + "   D    " + "  DDD   " + "DDDDDDDD",
        "   21   " + "  2112  " + "  2 12  " + "  DDD   " +
        "   D    " + "   D    " + "  DDD   " + "DDDDDDDD",
    };
    // CRYSTAL BALL
    private static final String[] SPR_CRYSTAL_BALL = {
        " 011110 " + "01lLl110" + "1lLWLll1" + "1lLWWll1" +
        "1llLLll1" + "1llllll1" + "011llll0" + " 011110 ",
        " 011110 " + "01lll110" + "1llWlll1" + "1lLWWll1" +
        "1llWLll1" + "1llllll1" + "011llll0" + " 011110 ",
    };
    // TRAP FLOOR
    private static final String SPR_TRAP =
        "DDDDDDDD" + "D      D" + "D  DD  D" + "D D  D D" +
        "D D  D D" + "D  DD  D" + "D      D" + "DDDDDDDD";
    // VINE
    private static final String SPR_VINE =
        " 1  1 1 " + "1 11 1 1" + " 1 1 1 1" + "11 1 1 1" +
        " 1  1 1 " + "1 11 1  " + " 1 1 1 1" + "1  1 1 1";
    // GLOWING ORB
    private static final String[] SPR_GLOW_ORB = {
        "  1WW1  " + " 1WllW1 " + "1WlllLW1" + "1WlLLlW1" +
        "1WlllLW1" + "1WlllW11" + " 1WllW1 " + "  1WW1  ",
        "  1WW1  " + " 1WllW1 " + "1WllLlW1" + "1WLllLW1" +
        "1WllLlW1" + "1WlllW11" + " 1WllW1 " + "  1WW1  ",
    };
    // HOUSE DOOR
    private static final String SPR_HOUSE_DOOR =
        "1111111 " + "1DD   D1" + "1D W  D1" + "1D WW D1" +
        "1D    D1" + "1D  2 D1" + "1DDDDDDD" + "DDDDDDDD";
    // HOUSE EXIT (green floor, up arrow)
    private static final String SPR_HOUSE_EXIT =
        "DDDDDDD " + "DllllllD" + "Dl  W  D" + "Dl WWW D" +
        "Dl  W  D" + "Dl  W  D" + "DllllllD" + "DDDDDDDD";

    // ── BUILDING SPRITES ────────────────────────────────────
    // SHOP (2-frame animated sign)
    private static final String[] SPR_SHOP = {
        "11111111" + "1l1  1l1" + "1WWWWWW1" + "1W1221W1" +
        "11111111" + "1D    D1" + "1D  2 D1" + "DDDDDDDD",
        "11111111" + "1l1  1l1" + "1WWWWWW1" + "1W2112W1" +
        "11111111" + "1D    D1" + "1D  1 D1" + "DDDDDDDD",
    };
    // INN (warm lit windows)
    private static final String SPR_INN =
        "11111111" + "1LLLLLL1" + "1L1  1L1" + "1L1  1L1" +
        "11111111" + "1D    D1" + "1DDDDDD1" + "DDDDDDDD";
    // CASTLE WALL (battlements)
    private static final String SPR_CASTLE_WALL =
        "D D D D " + "DDDDDDDD" + "DL    LD" + "DL    LD" +
        "DL    LD" + "DL    LD" + "DDDDDDDD" + "DDDDDDDD";
    // CASTLE GATE (portcullis bars)
    private static final String SPR_CASTLE_GATE =
        "DD    DD" + "D0D  D0D" + "D0D  D0D" + "D0DDDD0D" +
        "D0D  D0D" + "D0D  D0D" + "D0D  D0D" + "DDDDDDDD";
    // TOWER (round top)
    private static final String SPR_TOWER =
        "  DDDD  " + " DLLLLD " + "DLLLLLL D" + "DL1  1LD" +
        "DL    LD" + "DLLLLLD " + "DDDDDDDD" + "DDDDDDDD";
    // RUIN (crumbled wall)
    private static final String SPR_RUIN =
        "D  D D  " + "DD D  DD" + "D  DD D " + "DDDD  DD" +
        "D   D  D" + "  D  DD " + "D D   D " + "DDDDDDDD";
    // TEMPLE (2-frame glow)
    private static final String[] SPR_TEMPLE = {
        "  1WW1  " + " 1WWWW1 " + "1WWWWWW1" + "D1LLLL1D" +
        "D1L  L1D" + "D1LLLL1D" + "D111111D" + "DDDDDDDD",
        "  1lW1  " + " 1WlWW1 " + "1WWlWWW1" + "D1LLLL1D" +
        "D1L  L1D" + "D1LLLL1D" + "D111111D" + "DDDDDDDD",
    };
    // HOUSE WALL (side wall with window)
    private static final String SPR_HOUSE_WALL =
        "11111111" + "1L    L1" + "1LLLLLL1" + "1L    L1" +
        "1L WW L1" + "1L WW L1" + "1LLLLLL1" + "DDDDDDDD";

    // ── TRANSPORT SPRITES (4-frame) ──────────────────────────
    // BOAT (sail sways)
    private static final String[] SPR_BOAT = {
        "   1    " + "  111   " + " 11111  " + "1111111 " +
        "DDDDDDD " + "D      D" + "DDDDDDDD" + "1111111 ",
        "   1    " + "  1 1   " + " 11 11  " + "111 111 " +
        "DDDDDDD " + "D      D" + "DDDDDDDD" + "1111111 ",
        "    1   " + "   111  " + "  11111 " + " 1111111" +
        " DDDDDDD" + "D      D" + "DDDDDDDD" + " 1111111",
        "    1   " + "   1 1  " + "  11 11 " + " 111 111" +
        " DDDDDDD" + "D      D" + "DDDDDDDD" + " 1111111",
    };
    // CART (wheel spins)
    private static final String[] SPR_CART = {
        "  DDDD  " + " D    D " + "DDDLLDD " + "D L  LD " +
        "DDDLLDD " + " D    D " + "1D1  1D1" + " 1    1 ",
        "  DDDD  " + " D    D " + "DDDllDD " + "D l  lD " +
        "DDDllDD " + " D    D " + "D1D  D1D" + "  1    1",
        "  DDDD  " + " D    D " + "DDDLLDD " + "D L  LD " +
        "DDDLLDD " + " D    D " + "1 D  D 1" + " 1    1 ",
        "  DDDD  " + " D    D " + "DDDllDD " + "D l  lD " +
        "DDDllDD " + " D    D " + " 1D  D1 " + "1  1  1 ",
    };
    // HORSE (trot cycle)
    private static final String[] SPR_HORSE = {
        "  111   " + " 1 11   " + "11111   " + " 1111   " +
        "  111   " + " 1 1 1  " + " 1   1  " + "D1   1D ",
        " 111    " + "111 1   " + "11111   " + " 1111   " +
        "  111   " + "  1  1  " + " 1    1 " + "D 1    1",
        "  111   " + " 11 1   " + "11111   " + " 1111   " +
        "  111   " + " 1   1  " + "D1   1  " + " 1   1  ",
        "   111  " + "   1 11 " + "   11111" + "    1111" +
        "    111 " + "    1  1" + "    1  1" + "   D1  D",
    };
    // AIRSHIP (propeller spins)
    private static final String[] SPR_AIRSHIP = {
        "  1111  " + " 1LLLL1 " + "1LLLLLL1" + "1LLLLLL1" +
        " 1llll1 " + "  1DD1  " + "  1221  " + "  1  1  ",
        "  1111  " + " 1LLLL1 " + "1LLLLLL1" + "1LLllLL1" +
        " 1llll1 " + "  1DD1  " + "  1 21  " + "  1  1  ",
        "  1111  " + " 1LLLL1 " + "1LLLLLL1" + "1LLLLLL1" +
        " 1llll1 " + "  1DD1  " + "  1221  " + " 1    1 ",
        "  1111  " + " 1LLLL1 " + "1LLLLLL1" + "1LLllLL1" +
        " 1llll1 " + "  1DD1  " + "  12 1  " + " 1    1 ",
    };
    // RAFT (bobs on water, 2-frame)
    private static final String[] SPR_RAFT = {
        "DDDDDDDD" + "D      D" + "D      D" + "DDDDDDDD" +
        "1 1 1 1 " + "11111111" + "1 1 1 1 " + "        ",
        "DDDDDDDD" + "D      D" + "D      D" + " DDDDDD " +
        " 1 1 1 1" + "11111111" + " 1 1 1 1" + "        ",
    };

    // ── ANIMAL SPRITES (4-frame) ─────────────────────────────
    // BIRD (flapping wings)
    private static final String[] SPR_BIRD = {
        "   111  " + "  1 1 1 " + " 11111  " + "  1 1   " +
        "  1WW1  " + "  1221  " + "   11   " + "   DD   ",
        "  1 1   " + " 11 11  " + "1111111 " + "  111   " +
        "  1WW1  " + "  1221  " + "   11   " + "   DD   ",
        "  111   " + " 1 1 1  " + "  11111 " + "   1 1  " +
        "  1WW1  " + "  1221  " + "   11   " + "   DD   ",
        " 1   1  " + " 11 11  " + "1111111 " + "  111   " +
        "  1WW1  " + "  1221  " + "   11   " + "   DD   ",
    };
    // FISH (swimming, tail wags)
    private static final String[] SPR_FISH = {
        "    111 " + "  11 11 " + " 1111111" + "111llll1" +
        " 1111111" + "  11 11 " + "    111 " + "        ",
        "   111  " + "  11 11 " + " 111111 " + "11llll1 " +
        " 111111 " + "  11 11 " + "   111  " + "        ",
        "  111   " + " 11  11 " + "1111111 " + "1llllll1" +
        "1111111 " + " 11  11 " + "  111   " + "        ",
        "   111  " + "  11 11 " + " 111111 " + " 1llll11" +
        " 111111 " + "  11 11 " + "   111  " + "        ",
    };
    // CAT (sitting, tail twitches)
    private static final String[] SPR_CAT = {
        "  1  1  " + " 11  11 " + "  1111  " + " 1WllW1 " +
        " 1W  W1 " + "  1111  " + " 1    1 " + " 1111DD ",
        "  1  1  " + " 11  11 " + "  1111  " + " 1WllW1 " +
        " 1W  W1 " + "  1111  " + " 1    1 " + " 111 DDD",
        "  1  1  " + " 11  11 " + "  1111  " + " 1lWWl1 " +
        " 1W  W1 " + "  1111  " + " 1    1 " + " 1111 DD",
        "  1  1  " + " 11  11 " + "  1111  " + " 1lWWl1 " +
        " 1W  W1 " + "  1111  " + " 1    1 " + " 11111 D",
    };
    // DOG (tail wags)
    private static final String[] SPR_DOG = {
        "  111   " + " 1 11   " + "1 111 1 " + " 11111  " +
        "  111   " + " 1 1 1  " + "D1   1D " + " 1   12 ",
        "  111   " + " 1 11   " + "1 111 1 " + " 11111  " +
        "  111   " + " 1 1 1  " + "D1   1D " + " 1    2 ",
        "  111   " + " 1 11   " + "1 111 1 " + " 11111  " +
        "  111   " + " 1 1 1  " + "D1   1D " + " 1   2  ",
        "  111   " + " 1 11   " + "1 111 1 " + " 11111  " +
        "  111   " + " 1 1 1  " + "D1   1D " + " 1  2   ",
    };
    // RABBIT (hopping)
    private static final String[] SPR_RABBIT = {
        " 1    1 " + " 1    1 " + "  1111  " + " 1WllW1 " +
        " 1    1 " + "  1111  " + " 1    1 " + "  D  D  ",
        " 1    1 " + "  1  1  " + "   11   " + "  1Wl1  " +
        " 11111  " + "  1111  " + " 1    1 " + "   D D  ",
        "        " + " 1    1 " + "  1111  " + " 1WllW1 " +
        " 1    1 " + "  1111  " + "  1  1  " + "  D  D  ",
        "1      1" + " 1    1 " + "  1111  " + " 1lWWl1 " +
        "  1111  " + "   11   " + "  D  D  " + "        ",
    };
    // DEER (walking)
    private static final String[] SPR_DEER = {
        " 1 1    " + "  111   " + "  1 1   " + " 11111  " +
        "  111   " + " 1 1 1  " + " 1   1  " + "D1   1D ",
        "  1 1   " + "  111   " + " 11 1   " + " 11111  " +
        "  111   " + " 1  11  " + "D1    1 " + " 1    1 ",
        " 1 1    " + "  111   " + "  1 1   " + " 11111  " +
        "  111   " + "  1 1 1 " + " 1   1  " + " 1   1D ",
        "   1 1  " + "   111  " + "   1 11 " + "  11111 " +
        "   111  " + "  11  1 " + " 1    1 " + " 1    1D",
    };
    // WOLF (prowling)
    private static final String[] SPR_WOLF = {
        " 11  1  " + " 111111 " + "11 1111 " + " 111111 " +
        "  1111  " + " 1 1 1  " + "D1   1D " + " 1   1  ",
        " 1 1 1  " + "  11111 " + "11 1111 " + " 111111 " +
        "  1111  " + " 1  11  " + "D1    1 " + "D1    1 ",
        " 11  1  " + " 111111 " + " 11111  " + " 111111 " +
        "  1111  " + "  1 1 1 " + " 1   1D " + " 1   1  ",
        " 1 1 1  " + "  11111 " + " 11111  " + " 111111 " +
        "  1111  " + "  11  1 " + "  1   1D" + "  1   1 ",
    };
    // BEAR (stomping)
    private static final String[] SPR_BEAR = {
        "  1111  " + " 111111 " + "11llll11" + "1lLLLl11" +
        "1ll  ll1" + " 111111 " + " 1    1 " + "D1    1D",
        "  1111  " + " 111111 " + "11llll11" + "1lLLLl11" +
        "1ll  ll1" + " 111111 " + "D1    1 " + " 1    1D",
        "  1111  " + " 111111 " + "11llll11" + "1lLLLl11" +
        "1ll  ll1" + " 111111 " + " 1    1D" + "D1    1 ",
        "  1111  " + " 111111 " + "11llll11" + "1lLLLl11" +
        "1ll  ll1" + " 111111 " + "D1    1D" + " 1    1 ",
    };

    // MIRROR
    private static final String SPR_MIRROR =
        "DllllllD" + "Dl1WW1lD" + "DlW  WlD" + "DlW  WlD" +
        "DlW  WlD" + "Dl1WW1lD" + "DllllllD" + "DDDDDDDD";
    // CLOUD
    private static final String SPR_CLOUD =
        "  lWWl  " + " lWWWWl " + "lWWWWWWl" + "WWWWWWWW" +
        "lWWWWWWl" + " lWWWWl " + "  lWWl  " + "        ";
    // SEABED (underwater)
    private static final String SPR_SEABED =
        "D D D D " + " DlDlDl " + "DlD lD D" + " D lD D " +
        "D D lD D" + " D D lD " + "DlD D D " + " D D D D";
    // CORAL
    private static final String SPR_CORAL =
        "  1 1   " + " 111111 " + "  11111 " + " 1 1 1  " +
        "  1 1 1 " + "1  1  11" + " 1    1 " + "11111111";
    // FLOOR TILE (checkerboard)
    private static final String SPR_FLOOR_TILE =
        "L L L L " + " L L L L" + "L L L L " + " L L L L" +
        "D D D D " + " D D D D" + "D D D D " + " D D D D";
    // PATH
    private static final String SPR_PATH =
        "D   D   " + " D   D  " + "   D   D" + "D  D D  " +
        " D   D  " + "  D D  D" + "D   D  D" + " D D  D ";
    // FLOWER
    private static final String SPR_FLOWER =
        " 1 2 1 2" + "1 2 1 2 " + " 1D2D1D2" + "1D 1 1 D" +
        "D  2 2  " + " 2 1  1 " + "D  D D  " + "DDDDDDDD";
    // BRIDGE (wooden planks horizontal)
    private static final String SPR_BRIDGE =
        "DD    DD" + "1LLLLLL1" + "1LLLLLL1" + "DD    DD" +
        "DD    DD" + "1LLLLLL1" + "1LLLLLL1" + "DD    DD";
    // NIGHT sky
    private static final String[] SPR_NIGHT = {
        "d dWd d " + "d  d  d " + " W d d  " + "d  dWd d" +
        "d d  d  " + " d W d d" + "d  d  d " + " d d  Wd",
        " d dWd d" + " d  d  d" + "dW d d  " + " d  dWd " +
        " d d  d " + "dd W d d" + " d  d  d" + "dd d  W ",
    };

    // ── Main tile generator using pixel art sprites ──────────
    private Image createGeneratedTile(int type, int size) {
        int[] pixels = new int[size * size];

        // Get the base colour from TileData
        int base = TileData.getColor(type);

        // Fill with base colour first
        for (int i = 0; i < pixels.length; i++) pixels[i] = base;

        // Render the pixel-art sprite for this tile
        renderTileSprite(pixels, size, type, base);

        return Image.createRGBImage(pixels, size, size, true);
    }

    private void renderTileSprite(int[] pixels, int size, int type, int base) {
        int af = animFrame & 1; // animation frame 0 or 1
        String sprite = null;
        int[] pal;

        switch (type) {
            // ── Ground tiles ─────────────────────────────────
            case TileData.TILE_GRASS:
            case TileData.TILE_GRASS_LIGHT:
                pal = buildPalette(base, 0xFF44AA22, 0xFFFFFF88, 0xFF228800);
                sprite = SPR_GRASS[af];
                break;
            case TileData.TILE_DIRT:
                pal = buildPalette(base, 0xFF996633, 0xFFBB8844, 0xFF664422);
                sprite = SPR_DIRT;
                break;
            case TileData.TILE_STONE:
            case TileData.TILE_ROCK:
                pal = buildPalette(base, 0xFF888888, 0xFFAAAAAA, 0xFF444444);
                sprite = SPR_STONE;
                break;
            case TileData.TILE_WATER:
                pal = buildPalette(base, 0xFF5588FF, 0xFFAADDFF, 0xFF2244CC);
                sprite = SPR_WATER[af];
                break;
            case TileData.TILE_SAND:
                pal = buildPalette(base, 0xFFDDCC88, 0xFFFFEEAA, 0xFFBBAA66);
                sprite = SPR_SAND;
                break;
            case TileData.TILE_WOOD:
            case TileData.TILE_FLOOR_WOOD:
                pal = buildPalette(base, 0xFF8B6914, 0xFFAA8833, 0xFF664400);
                sprite = SPR_WOOD;
                break;
            case TileData.TILE_BRICK:
            case TileData.TILE_WALL_TOP:
                pal = buildPalette(base, 0xFFCC4422, 0xFFDD6644, 0xFF883300);
                sprite = SPR_BRICK;
                break;
            case TileData.TILE_PATH:
                pal = buildPalette(base, 0xFFBDB76B, 0xFFCFC980, 0xFF9A9550);
                sprite = SPR_PATH;
                break;
            case TileData.TILE_FLOOR_TILE:
                pal = buildPalette(base, 0xFFD2B48C, 0xFFE8CCAA, 0xFFAA8866);
                sprite = SPR_FLOOR_TILE;
                break;
            case TileData.TILE_CARPET:
                pal = buildPalette(base, 0xFFFFD700, 0xFFFFEE88, 0xFFCC9900);
                sprite = SPR_CARPET;
                break;

            // ── Nature ───────────────────────────────────────
            case TileData.TILE_TREE:
            case TileData.TILE_BUSH:
                pal = buildPalette(base, 0xFF228B22, 0xFF44AA44, 0xFF8B4513);
                sprite = SPR_TREE;
                break;
            case TileData.TILE_FLOWER:
                pal = buildPalette(base, 0xFFFF6699, 0xFFFF99BB, 0xFF228B22);
                sprite = SPR_FLOWER;
                break;
            case TileData.TILE_TALL_GRASS:
                pal = buildPalette(base, 0xFF3A5C1A, 0xFF55882A, 0xFF223300);
                sprite = SPR_TALL_GRASS;
                break;
            case TileData.TILE_SNOW:
                pal = buildPalette(base, 0xFFEEEEFF, 0xFFFFFFFF, 0xFFCCCCEE);
                sprite = SPR_SNOW;
                break;
            case TileData.TILE_ICE:
                pal = buildPalette(base, 0xFFAADDFF, 0xFFDDEEFF, 0xFF88BBDD);
                sprite = SPR_ICE;
                break;
            case TileData.TILE_VINE:
                pal = buildPalette(base, 0xFF447733, 0xFF66AA55, 0xFF223322);
                sprite = SPR_VINE;
                break;
            case TileData.TILE_CACTUS:
                pal = buildPalette(base, 0xFF8BC34A, 0xFFAADD66, 0xFF557722);
                sprite = SPR_CACTUS;
                break;
            case TileData.TILE_CLOUD:
                pal = buildPalette(base, 0xFFEEEEFF, 0xFFFFFFFF, 0xFFCCCCEE);
                sprite = SPR_CLOUD;
                break;

            // ── Objects ──────────────────────────────────────
            case TileData.TILE_CHEST:
                pal = buildPalette(base, 0xFFFFD700, 0xFFFFEE88, 0xFF8B4513);
                sprite = SPR_CHEST;
                break;
            case TileData.TILE_DOOR:
                pal = buildPalette(base, 0xFF8B4513, 0xFFAA6633, 0xFF664411);
                sprite = SPR_DOOR;
                break;
            case TileData.TILE_STAIRS_UP:
                pal = buildPalette(base, 0xFFDEB887, 0xFFEECCAA, 0xFF8B7355);
                sprite = SPR_STAIRS_UP;
                break;
            case TileData.TILE_STAIRS_DN:
                pal = buildPalette(base, 0xFFA0522D, 0xFFBB7755, 0xFF663311);
                sprite = SPR_STAIRS_DN;
                break;
            case TileData.TILE_SIGN:
                pal = buildPalette(base, 0xFFDEB887, 0xFFEECCAA, 0xFF8B4513);
                sprite = SPR_SIGN;
                break;
            case TileData.TILE_FENCE:
                pal = buildPalette(base, 0xFF8B4513, 0xFFAA6633, 0xFF664411);
                sprite = SPR_FENCE;
                break;
            case TileData.TILE_ROOF:
                pal = buildPalette(base, 0xFFAA3333, 0xFFCC5555, 0xFF882222);
                sprite = SPR_ROOF;
                break;
            case TileData.TILE_BARREL:
                pal = buildPalette(base, 0xFF8B4513, 0xFFCC8844, 0xFF664400);
                sprite = SPR_BARREL;
                break;
            case TileData.TILE_BOOKSHELF:
                pal = buildPalette(base, 0xFF553322, 0xFFFF4444, 0xFF4444FF);
                pal[PAL_C2] = 0xFFFF8800;
                pal[PAL_C3] = 0xFF44FF44;
                sprite = SPR_BOOKSHELF;
                break;
            case TileData.TILE_MIRROR:
                pal = buildPalette(base, 0xFFCCEEFF, 0xFFEEFFFF, 0xFF8899AA);
                sprite = SPR_MIRROR;
                break;
            case TileData.TILE_BRIDGE:
                pal = buildPalette(base, 0xFF8B6914, 0xFFAA8833, 0xFF664400);
                sprite = SPR_BRIDGE;
                break;

            // ── Interactive ──────────────────────────────────
            case TileData.TILE_FIREPLACE:
                pal = buildPalette(base, 0xFFFF6600, 0xFFFFCC00, 0xFF882200);
                sprite = SPR_FIREPLACE[af];
                break;
            case TileData.TILE_ALTAR:
                pal = buildPalette(base, 0xFFFFD700, 0xFFFFFFFF, 0xFF886644);
                sprite = SPR_ALTAR[af];
                break;
            case TileData.TILE_WARP_STONE:
                pal = buildPalette(base, 0xFF9944FF, 0xFFCCAAFF, 0xFF442288);
                sprite = SPR_WARPSTONE[af];
                break;
            case TileData.TILE_TELEPAD:
                pal = buildPalette(base, 0xFF00FFEE, 0xFFAAFFFF, 0xFF006655);
                sprite = SPR_TELEPAD[af];
                break;
            case TileData.TILE_PRESSURE:
                pal = buildPalette(base, 0xFF996633, 0xFFBB8844, 0xFF664422);
                sprite = SPR_PRESSURE;
                break;
            case TileData.TILE_CRYSTAL_BALL:
                pal = buildPalette(base, 0xFF8866BB, 0xFFCCAAFF, 0xFF442266);
                sprite = SPR_CRYSTAL_BALL[af];
                break;
            case TileData.TILE_TORCH:
                pal = buildPalette(base, 0xFFFF8800, 0xFFFFCC44, 0xFF884400);
                sprite = SPR_TORCH[af];
                break;
            case TileData.TILE_GLOWING_ORB:
                pal = buildPalette(base, 0xFFAAFFBB, 0xFFFFFFFF, 0xFF44BB66);
                sprite = SPR_GLOW_ORB[af];
                break;

            // ── Hazards ──────────────────────────────────────
            case TileData.TILE_LAVA:
                pal = buildPalette(base, 0xFFFF4400, 0xFFFFCC00, 0xFFCC2200);
                sprite = SPR_LAVA[af];
                break;
            case TileData.TILE_SWAMP:
                pal = buildPalette(base, 0xFF3D6B2A, 0xFF55882A, 0xFF1A3311);
                sprite = SPR_SWAMP[af];
                break;
            case TileData.TILE_QUICKSAND:
                pal = buildPalette(base, 0xFFB8A040, 0xFFDDCC66, 0xFF887722);
                sprite = SPR_QUICKSAND[af];
                break;
            case TileData.TILE_MUD:
                pal = buildPalette(base, 0xFF554422, 0xFF776644, 0xFF332211);
                sprite = SPR_MUD;
                break;
            case TileData.TILE_SPIKE:
                pal = buildPalette(base, 0xFFCC2200, 0xFFFFFFFF, 0xFF880000);
                sprite = SPR_SPIKE;
                break;
            case TileData.TILE_TRAP_FLOOR:
                pal = buildPalette(base, 0xFF443322, 0xFF665544, 0xFF221100);
                sprite = SPR_TRAP;
                break;
            case TileData.TILE_BOMB_WALL:
                pal = buildPalette(base, 0xFF885500, 0xFFAA7722, 0xFF553300);
                sprite = SPR_BOMB_WALL;
                break;
            case TileData.TILE_CRACKED:
                pal = buildPalette(base, 0xFF777777, 0xFF999999, 0xFF333333);
                sprite = SPR_CRACKED;
                break;

            // ── Special environments ──────────────────────────
            case TileData.TILE_CAVE:
                pal = buildPalette(base, 0xFF4B0082, 0xFF6B20A2, 0xFF1A0033);
                sprite = SPR_CAVE;
                break;
            case TileData.TILE_NIGHT:
                pal = buildPalette(base, 0xFF2E2E5E, 0xFFFFFFBB, 0xFF111133);
                sprite = SPR_NIGHT[af];
                break;
            case TileData.TILE_SPECIAL:
                pal = buildPalette(base, 0xFFFF69B4, 0xFFFFFFFF, 0xFFCC2288);
                sprite = SPR_SPECIAL[af];
                break;

            // ── Terrain ──────────────────────────────────────
            case TileData.TILE_CRYSTAL:
                pal = buildPalette(base, 0xFF88DDFF, 0xFFFFFFFF, 0xFF4488BB);
                sprite = SPR_CRYSTAL[af];
                break;
            case TileData.TILE_VOLCANO:
                pal = buildPalette(base, 0xFFFF3300, 0xFFFFCC00, 0xFF882200);
                sprite = SPR_VOLCANO[af];
                break;
            case TileData.TILE_DEEP_WATER:
                pal = buildPalette(base, 0xFF1A3A7A, 0xFF4477CC, 0xFF0A1A44);
                sprite = SPR_DEEP_WATER[af];
                break;
            case TileData.TILE_SEABED:
                pal = buildPalette(base, 0xFF1A4A6A, 0xFF2A6A8A, 0xFF0A2A3A);
                sprite = SPR_SEABED;
                break;
            case TileData.TILE_CORAL:
                pal = buildPalette(base, 0xFF00AACC, 0xFFFF6699, 0xFF006688);
                sprite = SPR_CORAL;
                break;

            // ── Conveyors ────────────────────────────────────
            case TileData.TILE_CONVEY_R:
                pal = buildPalette(base, 0xFF997700, 0xFFFFCC00, 0xFF554400);
                sprite = SPR_CONVEY_R;
                break;
            case TileData.TILE_CONVEY_L:
                pal = buildPalette(base, 0xFF997700, 0xFFFFCC00, 0xFF554400);
                sprite = SPR_CONVEY_L;
                break;
            case TileData.TILE_CONVEY_U:
                pal = buildPalette(base, 0xFF997700, 0xFFFFCC00, 0xFF554400);
                sprite = SPR_CONVEY_U;
                break;
            case TileData.TILE_CONVEY_D:
                pal = buildPalette(base, 0xFF997700, 0xFFFFCC00, 0xFF554400);
                sprite = SPR_CONVEY_D;
                break;

            // ── Map link tiles ───────────────────────────────
            case TileData.TILE_HOUSE_DOOR:
                pal = buildPalette(base, 0xFF8B4513, 0xFF00CCCC, 0xFFFFD700);
                sprite = SPR_HOUSE_DOOR;
                break;
            case TileData.TILE_HOUSE_EXIT:
                pal = buildPalette(base, 0xFF227733, 0xFFFFFFFF, 0xFF115522);
                sprite = SPR_HOUSE_EXIT;
                break;

            // ── Buildings ────────────────────────────────────
            case TileData.TILE_SHOP:
                pal = buildPalette(base, 0xFFDEB887, 0xFFFFD700, 0xFF8B4513);
                sprite = SPR_SHOP[af];
                break;
            case TileData.TILE_INN_FRONT:
                pal = buildPalette(base, 0xFFFFAACC, 0xFFFFEEDD, 0xFF884466);
                sprite = SPR_INN;
                break;
            case TileData.TILE_CASTLE_WALL:
                pal = buildPalette(base, 0xFF888888, 0xFFAAAAAA, 0xFF444444);
                sprite = SPR_CASTLE_WALL;
                break;
            case TileData.TILE_CASTLE_GATE:
                pal = buildPalette(base, 0xFF666644, 0xFF999977, 0xFF333322);
                sprite = SPR_CASTLE_GATE;
                break;
            case TileData.TILE_TOWER:
                pal = buildPalette(base, 0xFF8B7355, 0xFFAA9977, 0xFF554433);
                sprite = SPR_TOWER;
                break;
            case TileData.TILE_RUIN:
                pal = buildPalette(base, 0xFF885533, 0xFFAA7755, 0xFF553311);
                sprite = SPR_RUIN;
                break;
            case TileData.TILE_TEMPLE:
                pal = buildPalette(base, 0xFFDDCC88, 0xFFFFEEBB, 0xFF998844);
                sprite = SPR_TEMPLE[af];
                break;
            case TileData.TILE_HOUSE_WALL:
                pal = buildPalette(base, 0xFFCC8844, 0xFFEEAA66, 0xFF884422);
                sprite = SPR_HOUSE_WALL;
                break;

            // ── Transport (4-frame) ───────────────────────────
            case TileData.TILE_BOAT:
                pal = buildPalette(base, 0xFF8B6914, 0xFFFFFFFF, 0xFF2244AA);
                sprite = SPR_BOAT[animFrame % 4];
                break;
            case TileData.TILE_CART:
                pal = buildPalette(base, 0xFF8B4513, 0xFFDEB887, 0xFF444422);
                sprite = SPR_CART[animFrame % 4];
                break;
            case TileData.TILE_HORSE:
                pal = buildPalette(base, 0xFFCC9933, 0xFFEEBB55, 0xFF885500);
                sprite = SPR_HORSE[animFrame % 4];
                break;
            case TileData.TILE_AIRSHIP:
                pal = buildPalette(base, 0xFF8888CC, 0xFFCCCCFF, 0xFF444488);
                sprite = SPR_AIRSHIP[animFrame % 4];
                break;
            case TileData.TILE_RAFT:
                pal = buildPalette(base, 0xFF887744, 0xFFAA9966, 0xFF2244AA);
                sprite = SPR_RAFT[af];
                break;

            // ── Animals (4-frame) ─────────────────────────────
            case TileData.TILE_BIRD:
                pal = buildPalette(base, 0xFF4488FF, 0xFFFFFFFF, 0xFFFF8800);
                sprite = SPR_BIRD[animFrame % 4];
                break;
            case TileData.TILE_FISH:
                pal = buildPalette(base, 0xFF2266CC, 0xFF88DDFF, 0xFFFF8800);
                sprite = SPR_FISH[animFrame % 4];
                break;
            case TileData.TILE_CAT:
                pal = buildPalette(base, 0xFFFF8844, 0xFFFFCC88, 0xFF884422);
                sprite = SPR_CAT[animFrame % 4];
                break;
            case TileData.TILE_DOG:
                pal = buildPalette(base, 0xFFBB8844, 0xFFDDAA66, 0xFF664422);
                sprite = SPR_DOG[animFrame % 4];
                break;
            case TileData.TILE_RABBIT:
                pal = buildPalette(base, 0xFFEEEEEE, 0xFFFFFFFF, 0xFFFF8888);
                sprite = SPR_RABBIT[animFrame % 4];
                break;
            case TileData.TILE_DEER:
                pal = buildPalette(base, 0xFF885522, 0xFFAA7744, 0xFFFFFFFF);
                sprite = SPR_DEER[animFrame % 4];
                break;
            case TileData.TILE_WOLF_TILE:
                pal = buildPalette(base, 0xFF666666, 0xFFAAAAAA, 0xFFFF2222);
                sprite = SPR_WOLF[animFrame % 4];
                break;
            case TileData.TILE_BEAR:
                pal = buildPalette(base, 0xFF774422, 0xFF996644, 0xFF442211);
                sprite = SPR_BEAR[animFrame % 4];
                break;

            default:
                pal = buildPalette(base, lighten(base,40), lighten(base,70), darken(base,40));
                return;
        }

        if (sprite != null) {
            renderSprite(pixels, size, sprite, pal);
        }
    }

    private void addTilePattern(int[] pixels, int size, int type, int baseColor) {
        // Legacy method kept for compatibility — now unused (renderTileSprite handles everything)
    }

    private int darken(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int lighten(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // =================================================
    //  GAME LOOP
    // =================================================
    public void start() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
    }

    public void stop() {
        running = false;
        try {
            if (thread != null) thread.join();
        } catch (InterruptedException ignored) {}
    }

    public void run() {
        while (running) {
            long frameStart = System.currentTimeMillis();

            animTimer++;
            if (animTimer % 10 == 0) {
                animFrame = (animFrame + 1) % 4;
            }

            if (!menuOpen && !selectingSlot && !selectingLevel) {
                if (playtestMode) {
                    updatePlaytest();
                } else {
                    handleEditorInput();
                }
            }

            render();

            long elapsed = System.currentTimeMillis() - frameStart;
            long sleep = 33 - elapsed;
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    // =================================================
    //  EDITOR INPUT
    // =================================================
    private void handleEditorInput() {
        long now = System.currentTimeMillis();
        if (now - lastInputTime < INPUT_DELAY) return;

        int keys = getKeyStates();
        boolean acted = false;

        // D-pad movement
        if ((keys & UP_PRESSED) != 0) {
            cursorY = Math.max(0, cursorY - 1);
            acted = true;
        }
        if ((keys & DOWN_PRESSED) != 0) {
            cursorY = Math.min(MAP_ROWS - 1, cursorY + 1);
            acted = true;
        }
        if ((keys & LEFT_PRESSED) != 0) {
            cursorX = Math.max(0, cursorX - 1);
            acted = true;
        }
        if ((keys & RIGHT_PRESSED) != 0) {
            cursorX = Math.min(MAP_COLS - 1, cursorX + 1);
            acted = true;
        }

        if (acted) {
            autoScroll();
            updateToolPreview();
        }

        // FIRE held = continuous paint for pencil/eraser/scatter
        if ((keys & FIRE_PRESSED) != 0) {
            if (!npcMode) {
                if (currentTool == TOOL_PENCIL || currentTool == TOOL_ERASER
                        || currentTool == TOOL_SCATTER) {
                    applyToolAt(cursorX, cursorY, true);
                }
            }
            painting = true;
            acted = true;
        } else {
            painting = false;
        }

        if (acted) {
            lastInputTime = now;
        }
    }

    protected void keyPressed(int keyCode) {
        if (menuOpen) {
            handleMenuKeyPress(keyCode);
            return;
        }

        if (selectingSlot) {
            handleSlotKeyPress(keyCode);
            return;
        }

        if (selectingLevel) {
            handleLevelKeyPress(keyCode);
            return;
        }

        if (playtestMode) {
            handlePlaytestKeyPress(keyCode);
            return;
        }

        // EDITOR KEY ACTIONS
        switch (keyCode) {
            case KEY_NUM0:
                // Open Script Editor for the tile or NPC under the cursor
                openScriptEditor();
                break;

            case KEY_NUM1:
                // Previous tile or NPC type
                if (npcMode) {
                    currentNPCType--;
                    if (currentNPCType < 0) currentNPCType = NPCManager.NPC_TYPE_NAMES.length - 1;
                    setStatus("NPC: " + NPCManager.NPC_TYPE_NAMES[currentNPCType]);
                } else {
                    currentTile--;
                    if (currentTile < 0) currentTile = MAX_TILES - 1;
                    setStatus("Tile: " + TileData.getName(currentTile));
                }
                break;

            case KEY_NUM3:
                // Next tile or NPC type
                if (npcMode) {
                    currentNPCType++;
                    if (currentNPCType >= NPCManager.NPC_TYPE_NAMES.length) currentNPCType = 0;
                    setStatus("NPC: " + NPCManager.NPC_TYPE_NAMES[currentNPCType]);
                } else {
                    currentTile++;
                    if (currentTile >= MAX_TILES) currentTile = 0;
                    setStatus("Tile: " + TileData.getName(currentTile));
                }
                break;

            case KEY_POUND:
                // On a linkable tile in tile mode: open Link Editor
                // Otherwise cycle layer / NPC mode
                if (!npcMode) {
                    int tileUnder = mapData[currentLayer][cursorY][cursorX];
                    boolean linkable = (tileUnder == TileData.TILE_DOOR
                        || tileUnder == TileData.TILE_STAIRS_UP
                        || tileUnder == TileData.TILE_STAIRS_DN
                        || tileUnder == TileData.TILE_TELEPAD
                        || tileUnder == TileData.TILE_WARP_STONE
                        || linkSys.hasLinkAt(cursorX, cursorY));
                    if (linkable) {
                        openLinkEditor();
                        break;
                    }
                }
                // Standard: cycle layer / toggle NPC mode
                if (npcMode) {
                    npcMode = false;
                    currentLayer = 0;
                    setStatus("Layer: " + LAYER_NAMES[currentLayer]);
                } else {
                    currentLayer++;
                    if (currentLayer >= NUM_LAYERS) {
                        currentLayer = 0;
                        npcMode = true;
                        setStatus("NPC Mode — 5=BuiltinPicker  0=Script");
                    } else {
                        setStatus("Layer: " + LAYER_NAMES[currentLayer]);
                    }
                }
                break;

            case KEY_NUM5:
                // In NPC mode: existing NPC = edit dialogue; empty = open builtin picker
                // In tile mode: apply current draw tool
                if (npcMode) {
                    int existingNPC = npcMgr.findNPCAt(cursorX, cursorY);
                    if (existingNPC >= 0) {
                        dialogueEditor.editNPCDialogue(existingNPC);
                    } else {
                        openBuiltinNPCPicker();
                    }
                } else {
                    applyToolAt(cursorX, cursorY, true);
                }
                break;

            // ── Tool selection via NUM7/NUM9 ─────────────────
            case KEY_NUM7:
                // Previous tool  (or zoom in when tool panel hidden)
                if (showToolPanel) {
                    currentTool--;
                    if (currentTool < 0) currentTool = NUM_TOOLS - 1;
                    clearPreview();
                    setStatus("Tool: " + TOOL_NAMES[currentTool]);
                } else {
                    zoomLevel++;
                    if (zoomLevel > 2) zoomLevel = 0;
                    recalcLayout();
                    updateZoomCommands();
                    setStatus("Zoom x" + (zoomLevel + 1));
                }
                break;

            case KEY_NUM9:
                // Next tool (or zoom out when tool panel hidden)
                if (showToolPanel) {
                    currentTool++;
                    if (currentTool >= NUM_TOOLS) currentTool = 0;
                    clearPreview();
                    setStatus("Tool: " + TOOL_NAMES[currentTool]);
                } else {
                    zoomLevel--;
                    if (zoomLevel < 0) zoomLevel = 2;
                    recalcLayout();
                    updateZoomCommands();
                    setStatus("Zoom x" + (zoomLevel + 1));
                }
                break;

            // ── Shape anchor: NUM2/4/6/8 update cursor + preview
            case KEY_NUM2:
                cursorY = Math.max(0, cursorY - 1);
                autoScroll();
                updateToolPreview();
                break;

            case KEY_NUM8:
                cursorY = Math.min(MAP_ROWS - 1, cursorY + 1);
                autoScroll();
                updateToolPreview();
                break;

            case KEY_NUM4:
                cursorX = Math.max(0, cursorX - 1);
                autoScroll();
                updateToolPreview();
                break;

            case KEY_NUM6:
                cursorX = Math.min(MAP_COLS - 1, cursorX + 1);
                autoScroll();
                updateToolPreview();
                break;

            case KEY_STAR:
                // Toggle grid (short press) / cancel drag (if dragging)
                if (toolDragging) {
                    toolDragging = false;
                    clearPreview();
                    setStatus("Cancelled");
                } else {
                    showGrid = !showGrid;
                    setStatus("Grid: " + (showGrid ? "ON" : "OFF"));
                }
                break;
        }
    }

    // =================================================
    //  MENU HANDLING
    // =================================================
    private void handleMenuKeyPress(int keyCode) {
        int action = -1;
        try {
            action = getGameAction(keyCode);
        } catch (Exception e) {}

        if (action == UP || keyCode == KEY_NUM2) {
            menuSelection--;
            if (menuSelection < 0) menuSelection = MENU_COUNT - 1;
        } else if (action == DOWN || keyCode == KEY_NUM8) {
            menuSelection++;
            if (menuSelection >= MENU_COUNT) menuSelection = 0;
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            executeMenuAction(menuSelection);
            menuOpen = false;
        } else if (keyCode == KEY_STAR) {
            menuOpen = false;
        }
    }

    private void handleSlotKeyPress(int keyCode) {
        int action = -1;
        try {
            action = getGameAction(keyCode);
        } catch (Exception e) {}

        if (action == UP || keyCode == KEY_NUM2) {
            slotSelection--;
            if (slotSelection < 0) slotSelection = MAX_SAVE_SLOTS - 1;
        } else if (action == DOWN || keyCode == KEY_NUM8) {
            slotSelection++;
            if (slotSelection >= MAX_SAVE_SLOTS) slotSelection = 0;
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            if (savingMode) {
                saveToSlot(slotSelection);
            } else {
                loadFromSlot(slotSelection);
            }
            selectingSlot = false;
        } else if (keyCode == KEY_STAR) {
            selectingSlot = false;
        }
    }

    private void handleLevelKeyPress(int keyCode) {
        int action = -1;
        try {
            action = getGameAction(keyCode);
        } catch (Exception e) {}

        if (levelList == null || levelList.length == 0) {
            selectingLevel = false;
            return;
        }

        if (action == UP || keyCode == KEY_NUM2) {
            levelSelection--;
            if (levelSelection < 0) levelSelection = levelList.length - 1;
        } else if (action == DOWN || keyCode == KEY_NUM8) {
            levelSelection++;
            if (levelSelection >= levelList.length) levelSelection = 0;
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            loadFromFile(levelList[levelSelection]);
            selectingLevel = false;
        } else if (keyCode == KEY_STAR) {
            selectingLevel = false;
        }
    }

    private void executeMenuAction(int action) {
        switch (action) {
            case MENU_SAVE:
                if (fileMgr != null && fileMgr.isAvailable()) {
                    saveToFile();
                } else {
                    savingMode = true;
                    selectingSlot = true;
                    slotSelection = 0;
                }
                break;

            case MENU_LOAD:
                if (fileMgr != null && fileMgr.isAvailable()) {
                    showLevelSelector();
                } else {
                    savingMode = false;
                    selectingSlot = true;
                    slotSelection = 0;
                }
                break;

            case MENU_UNDO:
                performUndo();
                break;

            case MENU_REDO:
                performRedo();
                break;

            case MENU_GRID:
                showGrid = !showGrid;
                setStatus("Grid: " + (showGrid ? "ON" : "OFF"));
                break;

            case MENU_FILL:
                if (!npcMode) {
                    int saveTool = currentTool;
                    currentTool = TOOL_FILL;
                    applyToolAt(cursorX, cursorY, true);
                    currentTool = saveTool;
                    setStatus("Area filled");
                }
                break;

            case MENU_CLEAR:
                promptNewMap();
                break;

            case MENU_EXPORT_NPC:
                exportNPCDialogues();
                break;

            case MENU_PLAYTEST:
                startPlaytest();
                break;

            case MENU_TOOL_PANEL:
                showToolPanel = !showToolPanel;
                setStatus("Tools: " + (showToolPanel ? "ON" : "OFF"));
                break;

            case MENU_COPY:
                if (hasSelection) {
                    copySelection();
                    setStatus("Copied " + clipW + "x" + clipH);
                } else {
                    setStatus("No selection");
                }
                break;

            case MENU_PASTE:
                if (hasClipboard) {
                    currentTool = TOOL_STAMP;
                    setStatus("Stamp mode: place with 5");
                } else {
                    setStatus("Clipboard empty");
                }
                break;

            case MENU_SELECT_ALL:
                selX = 0; selY = 0;
                selW = MAP_COLS; selH = MAP_ROWS;
                hasSelection = true;
                currentTool = TOOL_SELECT;
                setStatus("All selected");
                break;

            case MENU_EXPORT_GAME:
                exportGamePackage();
                break;

            case MENU_PLAY_GAME:
                playDipGame();
                break;

            case MENU_EXIT:
                if (midlet.getProjectManager() != null) {
                    midlet.getProjectManager().showProjectSelector();
                } else {
                    midlet.exitApp();
                }
                break;
        }
    }

    private void showLevelSelector() {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            return;
        }

        java.util.Vector levels = fileMgr.listLevels(projectName);
        if (levels.size() == 0) {
            setStatus("No levels found");
            return;
        }

        levelList = new String[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            levelList[i] = (String) levels.elementAt(i);
        }
        levelSelection = 0;
        selectingLevel = true;
    }

    // =================================================
    //  SCRIPT EDITOR INTEGRATION  (KEY 0)
    // =================================================
    /**
     * Opens the ScriptEditor for whatever is under the cursor.
     *  - NPC mode  → edits the script for the NPC at cursor position
     *  - Tile mode → edits the script for the current tile TYPE
     *               (so all tiles of that type share the same script)
     * Pressing 0 with nothing scripted opens a blank script.
     * The ScriptEditor calls back to onScriptSaved() when done.
     */
    private void openScriptEditor() {
        int targetType;
        int targetId;
        String existingSrc;

        if (npcMode) {
            // Script targets the specific NPC slot at cursor
            int npcId = npcMgr.findNPCAt(cursorX, cursorY);
            if (npcId < 0) {
                setStatus("No NPC here. Place one first.");
                return;
            }
            targetType  = ScriptEditor.TARGET_NPC;
            targetId    = npcId;
            existingSrc = scriptMgr.getSource(ScriptManager.TYPE_NPC, npcId);
            setStatus("Opening script for NPC #" + npcId + "...");
        } else {
            // Script targets the tile TYPE under the cursor
            int tileType = mapData[currentLayer][cursorY][cursorX];
            if (tileType == TileData.TILE_VOID) {
                // No tile here — offer to script the current selected tile type
                tileType = currentTile;
            }
            targetType  = ScriptEditor.TARGET_TILE;
            targetId    = tileType;
            existingSrc = scriptMgr.getSource(ScriptManager.TYPE_TILE, tileType);
            setStatus("Opening script for Tile: " + TileData.getName(tileType));
        }

        // Pause the game loop while editor is visible
        running = false;

        ScriptEditor.show(
            midlet, fileMgr, projectName,
            targetType, targetId, existingSrc,
            new ScriptEditor.ScriptSavedCallback() {
                public void onScriptSaved(int type, int id,
                                          int[] bytecode, String source) {
                    // Map ScriptEditor type → ScriptManager type
                    int smType = (type == ScriptEditor.TARGET_NPC)
                               ? ScriptManager.TYPE_NPC
                               : ScriptManager.TYPE_TILE;

                    // Attach to registry
                    scriptMgr.attach(smType, id, bytecode, source, null);

                    // Persist to project
                    if (fileMgr != null && fileMgr.isAvailable()) {
                        scriptMgr.saveToProject(fileMgr, projectName);
                    }

                    // Resume editor
                    running = true;
                    thread = new Thread(EditorCanvas.this);
                    thread.start();

                    String typeLabel = (smType == ScriptManager.TYPE_NPC)
                                     ? "NPC #" + id
                                     : "Tile: " + TileData.getName(id);
                    setStatus("Script saved: " + typeLabel
                            + " (" + bytecode.length + " ops)");
                }
            }
        );
    }

    // =================================================
    //  NEW MAP — safe create with save-first prompt
    // =================================================
    // =================================================
    //  EXPORT GAME (.2DIP)
    // =================================================
    /**
     * Opens a Form asking for game title, author, version, and start level,
     * then compiles the project to a .2dip file and saves it to
     * 2DLE/<project>/publish/<title>.2dip
     */
    private void exportGamePackage() {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            setStatus("No file access for export!");
            return;
        }

        // Auto-save current level first
        saveToFile();

        final Form form = new Form("Export .2dip Game");

        // Prefill with project name as default title
        final TextField tfTitle   = new TextField("Game Title:",   projectName, 40, TextField.ANY);
        final TextField tfAuthor  = new TextField("Author:",       "My Studio",  32, TextField.ANY);
        final TextField tfVersion = new TextField("Version:",      "1.0",         8, TextField.ANY);
        final TextField tfStart   = new TextField("Start Level:", currentLevelName, 32, TextField.ANY);

        form.append(tfTitle);
        form.append(tfAuthor);
        form.append(tfVersion);
        form.append(tfStart);
        form.append(new StringItem("", "All saved levels will be\npacked into the .2dip file."));

        final Command cmdBuild  = new Command("Build .2dip", Command.OK,   1);
        final Command cmdCancel = new Command("Cancel",      Command.BACK, 1);
        form.addCommand(cmdBuild);
        form.addCommand(cmdCancel);

        running = false;

        form.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == cmdBuild) {
                    String title   = tfTitle.getString().trim();
                    String author  = tfAuthor.getString().trim();
                    String version = tfVersion.getString().trim();
                    String start   = tfStart.getString().trim();

                    if (title.length()  == 0) title   = projectName;
                    if (version.length()== 0) version = "1.0";
                    if (start.length()  == 0) start   = currentLevelName;

                    // Compile
                    GameCompiler.CompileResult res = GameCompiler.compile(
                        fileMgr, projectName, title, author, version, start);

                    if (!res.success) {
                        showCompileAlert("Compile Failed", res.error);
                        return;
                    }

                    // Write output
                    String outPath = GameCompiler.compileAndSave(
                        fileMgr, projectName, title, author, version, start);

                    running = true;
                    thread  = new Thread(EditorCanvas.this);
                    thread.start();
                    midlet.showScreen(EditorCanvas.this);

                    if (outPath != null) {
                        setStatus("OK: " + res.levelCount + " lvls  "
                                + res.assetCount + " assets  "
                                + res.sectionCount + " sections  "
                                + FileManager.formatBytes((int) res.totalBytes));
                    } else {
                        setStatus("Export failed — check storage.");
                    }
                } else {
                    running = true;
                    thread  = new Thread(EditorCanvas.this);
                    thread.start();
                    midlet.showScreen(EditorCanvas.this);
                    setStatus("Export cancelled.");
                }
            }
        });
        midlet.showScreen(form);
    }

    /** Shows an alert without blocking (goes back to the export form). */
    private void showCompileAlert(String title, String msg) {
        Alert a = new Alert(title, msg, null, AlertType.ERROR);
        a.setTimeout(Alert.FOREVER);
        midlet.getDisplay().setCurrent(a);
    }

    // =================================================
    //  PLAY .2DIP GAME FROM EDITOR
    // =================================================
    /**
     * Shows a list of compiled .2dip files in the project's publish/ folder
     * (and any other .2dip found on storage). User picks one and it launches
     * in GamePlayer — a full-screen game runtime. Pressing * in the game
     * returns to the editor.
     */
    private void playDipGame() {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            setStatus("No file access!");
            return;
        }

        // Collect .2dip files: project publish/ first, then all storage
        final java.util.Vector paths = new java.util.Vector();
        final java.util.Vector labels = new java.util.Vector();

        // 1. Project publish folder
        if (projectName != null) {
            String pubDir = fileMgr.getProjectPath(projectName) + "publish/";
            java.util.Vector pub = new java.util.Vector();
            fileMgr.listAllFiles(pubDir, pub);
            for (int i = 0; i < pub.size(); i++) {
                String p = (String) pub.elementAt(i);
                if (p.endsWith(".2dip")) {
                    byte[] hdr = fileMgr.readFile(p);
                    String[] info = (hdr != null) ? GameCompiler.readHeader(hdr) : null;
                    String lbl = (info != null)
                        ? info[0] + " v" + info[2] + " [project]"
                        : p;
                    paths.addElement(p);
                    labels.addElement(lbl);
                }
            }
        }

        // 2. Scan all storage roots for any .2dip
        for (int ri = 0; ri < fileMgr.getAvailableRootCount(); ri++) {
            String root = fileMgr.getRootUrl(ri);
            if (root == null) continue;
            java.util.Vector allFiles = new java.util.Vector();
            fileMgr.listAllFiles(root, allFiles);
            for (int fi = 0; fi < allFiles.size(); fi++) {
                String p = (String) allFiles.elementAt(fi);
                if (!p.endsWith(".2dip")) continue;
                // Skip already listed
                boolean dup = false;
                for (int di = 0; di < paths.size(); di++) {
                    if (p.equals(paths.elementAt(di))) { dup = true; break; }
                }
                if (dup) continue;
                byte[] hdr = fileMgr.readFile(p);
                String[] info = (hdr != null) ? GameCompiler.readHeader(hdr) : null;
                if (info == null) continue;
                paths.addElement(p);
                labels.addElement(info[0] + " v" + info[2]);
            }
        }

        if (paths.size() == 0) {
            setStatus("No .2dip files found. Use Menu->Export .2dip first.");
            return;
        }

        // Show picker list
        final List picker = new List("Play Game", List.IMPLICIT);
        for (int i = 0; i < labels.size(); i++) {
            picker.append((String) labels.elementAt(i), null);
        }
        final Command cmdBack = new Command("Cancel", Command.BACK, 1);
        picker.addCommand(cmdBack);
        running = false;

        picker.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == List.SELECT_COMMAND) {
                    int idx = picker.getSelectedIndex();
                    if (idx >= 0 && idx < paths.size()) {
                        String path = (String) paths.elementAt(idx);
                        byte[] data = fileMgr.readFile(path);
                        if (data != null) {
                            // Launch GamePlayer — it calls back to returnFromGame()
                            midlet.launchDipGame(data, EditorCanvas.this);
                            // Don't resume the editor loop here; it resumes in returnFromGame
                            return;
                        } else {
                            setStatus("Could not read: " + path);
                        }
                    }
                }
                // Cancel or read failure — return to editor
                running = true;
                thread  = new Thread(EditorCanvas.this);
                thread.start();
                midlet.showScreen(EditorCanvas.this);
            }
        });
        midlet.showScreen(picker);
    }

    /** Called by LevelEditorMIDlet when the player exits the game (presses *). */
    public void returnFromGame() {
        running = true;
        thread  = new Thread(this);
        thread.start();
        midlet.showScreen(this);
        setStatus("Returned from game.");
    }

    private void promptNewMap() {
        // Auto-save current map first so nothing is lost
        if (fileMgr != null && fileMgr.isAvailable()) {
            saveToFile();
        }

        // Ask for a new level name
        final TextBox tb = new TextBox("New Level Name", "level02", 32, TextField.ANY);
        Command ok     = new Command("Create", Command.OK,   1);
        Command cancel = new Command("Cancel", Command.BACK, 1);
        tb.addCommand(ok);
        tb.addCommand(cancel);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    String name = FileManager.sanitizeName(tb.getString().trim());
                    if (name.length() == 0) name = "level" + System.currentTimeMillis();
                    currentLevelName = name;
                    clearMap();
                    linkSys.clearAll();
                    setStatus("New level: " + name);
                }
                // Resume editor canvas
                running = true;
                thread = new Thread(EditorCanvas.this);
                thread.start();
                midlet.showScreen(EditorCanvas.this);
            }
        });
        // Pause game loop while TextBox is visible
        running = false;
        midlet.showScreen(tb);
    }

    // =================================================
    //  LINK EDITOR — open with # key on door/stair tiles
    // =================================================
    /**
     * Opens the LinkEditor for the tile at (cursorX, cursorY).
     * Only meaningful for DOOR, STAIRS, TELEPAD, WARP_STONE tiles.
     */
    private void openLinkEditor() {
        int tileType = npcMode ? TileData.TILE_DOOR
                               : mapData[currentLayer][cursorY][cursorX];

        boolean isLinkTile = (tileType == TileData.TILE_DOOR
            || tileType == TileData.TILE_STAIRS_UP
            || tileType == TileData.TILE_STAIRS_DN
            || tileType == TileData.TILE_TELEPAD
            || tileType == TileData.TILE_WARP_STONE
            || tileType == TileData.TILE_HOUSE_DOOR
            || tileType == TileData.TILE_HOUSE_EXIT);

        if (!isLinkTile && !linkSys.hasLinkAt(cursorX, cursorY)) {
            setStatus("Not a linkable tile. Place a Door/Stairs/Telepad first.");
            return;
        }

        running = false;
        final String levelName = currentLevelName;

        LinkEditor.show(
            midlet, fileMgr, projectName, linkSys,
            cursorX, cursorY, tileType,
            new LinkEditor.LinkSavedCallback() {
                public void onLinkSaved(MapLinkSystem.MapLink link) {
                    if (fileMgr != null && fileMgr.isAvailable()) {
                        linkSys.saveToProject(fileMgr, projectName, levelName);
                    }
                    resume("Link saved: " + link.targetLevel);
                }
                public void onLinkDeleted(int x, int y) {
                    if (fileMgr != null && fileMgr.isAvailable()) {
                        linkSys.saveToProject(fileMgr, projectName, levelName);
                    }
                    resume("Link removed.");
                }
                public void onLinkCancelled() {
                    resume("Link cancelled.");
                }
                private void resume(String msg) {
                    running = true;
                    thread = new Thread(EditorCanvas.this);
                    thread.start();
                    midlet.showScreen(EditorCanvas.this);
                    setStatus(msg);
                }
            }
        );
    }

    // =================================================
    //  BUILTIN NPC PICKER
    // =================================================
    private void openBuiltinNPCPicker() {
        final List list = new List("Place NPC/Boss", List.IMPLICIT);
        for (int i = 0; i < BuiltinNPCs.BUILTIN_COUNT; i++) {
            String prefix = BuiltinNPCs.isBoss(i) ? "[BOSS] " : "";
            list.append(prefix + BuiltinNPCs.getName(i), null);
        }
        final Command back = new Command("Cancel", Command.BACK, 1);
        list.addCommand(back);
        running = false;

        list.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c != back && c != List.SELECT_COMMAND
                        || c == List.SELECT_COMMAND) {
                    if (c == List.SELECT_COMMAND) {
                        int idx = list.getSelectedIndex();
                        if (idx >= 0) placeBuiltinNPC(idx);
                    }
                }
                running = true;
                thread = new Thread(EditorCanvas.this);
                thread.start();
                midlet.showScreen(EditorCanvas.this);
            }
        });
        midlet.showScreen(list);
    }

    private void placeBuiltinNPC(int presetId) {
        // Place NPC at cursor
        int type  = BuiltinNPCs.getNPCType(presetId);
        int id    = npcMgr.addNPC(cursorX, cursorY, type);
        if (id < 0) {
            setStatus("Max NPCs reached!");
            return;
        }

        // Attach the built-in script
        String src = BuiltinNPCs.getDefaultScript(presetId);
        ScriptLang.CompileResult res = ScriptLang.compile(src);
        if (res.bytecode != null) {
            scriptMgr.attach(ScriptManager.TYPE_NPC, id, res.bytecode, src,
                             BuiltinNPCs.getName(presetId));
            if (fileMgr != null && fileMgr.isAvailable()) {
                scriptMgr.saveToProject(fileMgr, projectName);
            }
        }
        setStatus("Placed: " + BuiltinNPCs.getName(presetId));
    }

    private void exportNPCDialogues() {
        if (fileMgr != null && fileMgr.isAvailable()) {
            String dialogueText = npcMgr.exportDialoguesToText();
            if (fileMgr.saveNPCData(projectName, dialogueText)) {
                setStatus("NPCs exported to npcs.txt");
            } else {
                setStatus("Export failed!");
            }
        } else {
            setStatus("File system not available");
        }
    }

    // =================================================
    //  PLAYTEST MODE
    // =================================================
    private void startPlaytest() {
        playtestMode = true;
        playEngine.reset();

        // Find player start (chest/special tile on layer 2, or 0,0)
        playEngine.playerX = 0;
        playEngine.playerY = 0;

        outer:
        for (int y = 0; y < MAP_ROWS; y++) {
            for (int x = 0; x < MAP_COLS; x++) {
                int tile = mapData[2][y][x];
                if (tile == TileData.TILE_CHEST || tile == TileData.TILE_SPECIAL) {
                    playEngine.playerX = x;
                    playEngine.playerY = y;
                    break outer;
                }
            }
        }

        // Center scroll on player
        scrollX = playEngine.playerX - viewCols / 2;
        scrollY = playEngine.playerY - viewRows / 2;
        clampScroll();

        setStatus("PLAYTEST - * to exit");
    }

    private void updatePlaytest() {
        long now = System.currentTimeMillis();

        // Tick the event/script runtime
        eventSys.update();

        // Don't process movement while a script is running
        if (eventSys.isRunning()) {
            return;
        }

        // Handle player input
        int keys = getKeyStates();
        int dx = 0, dy = 0;

        if ((keys & UP_PRESSED) != 0) dy = -1;
        else if ((keys & DOWN_PRESSED) != 0) dy = 1;
        else if ((keys & LEFT_PRESSED) != 0) dx = -1;
        else if ((keys & RIGHT_PRESSED) != 0) dx = 1;

        if ((dx != 0 || dy != 0) && !playEngine.dialogueActive) {
            int moveDelay = playEngine.isSwimming ? 150 : 100;
            if (now - lastInputTime > moveDelay) {
                playEngine.move(mapData, dx, dy, MAP_COLS, MAP_ROWS, tileSize);
                lastInputTime = now;

                int px = playEngine.playerX;
                int py = playEngine.playerY;

                // Check map transition (door/stairs/telepad/edge)
                MapLinkSystem.MapLink link = linkSys.checkTransition(
                    px, py, MAP_COLS, MAP_ROWS, mapData);
                if (link != null && link.trigger == MapLinkSystem.TRIGGER_STEP) {
                    executeTransition(link);
                    return; // map changed — stop processing this frame
                }

                // Check tile step script (layer 0 tile under player)
                if (px >= 0 && px < MAP_COLS && py >= 0 && py < MAP_ROWS) {
                    int tileType = mapData[0][py][px];
                    scriptMgr.triggerTile(tileType, eventSys);
                }
            }
        }

        // Update game logic
        playEngine.handleSliding(mapData, MAP_COLS, MAP_ROWS, tileSize);
        playEngine.updateThrow(mapData, MAP_COLS, MAP_ROWS, tileSize);
        playEngine.updateParticles();
        playEngine.updateDialogue();
        playEngine.updateDamage();

        // Update scroll to follow player
        scrollX = playEngine.playerX - viewCols / 2;
        scrollY = playEngine.playerY - viewRows / 2;
        clampScroll();
    }

    private void handlePlaytestKeyPress(int keyCode) {
        // Script is showing a text box — any key advances it
        if (eventSys.isRunning() && eventSys.isWaiting()) {
            int action = -1;
            try { action = getGameAction(keyCode); } catch (Exception e) {}
            if (action == FIRE || keyCode == KEY_NUM5) {
                eventSys.onConfirm();
            }
            return;
        }

        if (playEngine.dialogueActive) {
            int action = -1;
            try {
                action = getGameAction(keyCode);
            } catch (Exception e) {}

            if (action == FIRE || keyCode == KEY_NUM5) {
                playEngine.advanceDialogue();
            }
            return;
        }

        switch (keyCode) {
            case KEY_STAR:
                playtestMode = false;
                setStatus("Edit Mode");
                break;

            case KEY_NUM5:
                // Interact: try NPC script first, then fallback to PlayEngine interact
                if (!eventSys.isRunning()) {
                    // Look for NPC in front of player
                    int facedX = playEngine.playerX;
                    int facedY = playEngine.playerY;
                    switch (playEngine.playerDir) {
                        case 0: facedY++; break;  // down
                        case 1: facedY--; break;  // up
                        case 2: facedX--; break;  // left
                        case 3: facedX++; break;  // right
                    }

                    // Check action-triggered map link first (doors that need a key press)
                    MapLinkSystem.MapLink actionLink = linkSys.checkInteract(facedX, facedY);
                    if (actionLink != null) {
                        executeTransition(actionLink);
                        break;
                    }

                    int npcId = npcMgr.findNPCAt(facedX, facedY);
                    boolean scriptRan = false;
                    if (npcId >= 0) {
                        scriptRan = scriptMgr.triggerNPC(npcId, eventSys);
                    }
                    if (!scriptRan) {
                        // No NPC script — check tile interact script
                        int tileType = mapData[0][facedY][facedX];
                        scriptRan = scriptMgr.triggerTile(tileType, eventSys);
                    }
                    if (!scriptRan) {
                        // Fallback to built-in PlayEngine interaction
                        playEngine.interact(mapData, npcMgr, MAP_COLS, MAP_ROWS);
                    }
                } else {
                    // Advance script dialogue
                    eventSys.onConfirm();
                }
                break;

            case KEY_NUM0:
                // Throw object
                playEngine.throwObject(mapData, MAP_COLS, MAP_ROWS, tileSize);
                break;
        }
    }

    // =================================================
    //  MAP TRANSITION EXECUTION
    // =================================================
    /**
     * Executes a map link transition: loads target level, positions player.
     * Called both from step-trigger and interact-trigger.
     */
    private void executeTransition(MapLinkSystem.MapLink link) {
        if (link == null || link.targetLevel == null || link.targetLevel.length() == 0) return;

        // Save current level first
        if (fileMgr != null && fileMgr.isAvailable()) {
            saveToFile();
        }

        // Load target level
        String target = link.targetLevel;
        loadFromFile(target);

        // Load links for new level
        if (fileMgr != null && fileMgr.isAvailable()) {
            linkSys.loadFromProject(fileMgr, projectName, target);
        }

        // Position player at spawn point
        playEngine.playerX = link.spawnX;
        playEngine.playerY = link.spawnY;
        playEngine.playerDir = link.spawnDir;

        // Centre view on player
        scrollX = playEngine.playerX - viewCols / 2;
        scrollY = playEngine.playerY - viewRows / 2;
        clampScroll();

        setStatus("Entered: " + target);
    }

    private void clampScroll() {
        if (scrollX < 0) scrollX = 0;
        if (scrollY < 0) scrollY = 0;
        if (scrollX > MAP_COLS - viewCols) scrollX = MAP_COLS - viewCols;
        if (scrollY > MAP_ROWS - viewRows) scrollY = MAP_ROWS - viewRows;
    }

    // =================================================
    //  UNDO / REDO
    // =================================================
    private void pushUndo(int layer, int x, int y, int oldTile, int newTile) {
        undoLayer[undoHead] = layer;
        undoX[undoHead] = x;
        undoY[undoHead] = y;
        undoOldTile[undoHead] = oldTile;
        undoNewTile[undoHead] = newTile;

        undoHead = (undoHead + 1) % MAX_UNDO;
        if (undoCount < MAX_UNDO) undoCount++;
        redoCount = 0;
    }

    private void performUndo() {
        if (undoCount <= 0) {
            setStatus("Nothing to undo");
            return;
        }

        undoHead = (undoHead - 1 + MAX_UNDO) % MAX_UNDO;
        int layer = undoLayer[undoHead];
        int x = undoX[undoHead];
        int y = undoY[undoHead];
        int oldTile = undoOldTile[undoHead];

        mapData[layer][y][x] = oldTile;
        undoCount--;
        redoCount++;
        setStatus("Undo (" + undoCount + " left)");
    }

    private void performRedo() {
        if (redoCount <= 0) {
            setStatus("Nothing to redo");
            return;
        }

        int layer = undoLayer[undoHead];
        int x = undoX[undoHead];
        int y = undoY[undoHead];
        int newTile = undoNewTile[undoHead];

        mapData[layer][y][x] = newTile;
        undoHead = (undoHead + 1) % MAX_UNDO;
        redoCount--;
        undoCount++;
        setStatus("Redo");
    }

    // =================================================
    //  DRAW TOOLS ENGINE  (NEW v2.0)
    // =================================================

    /**
     * Main dispatch: called when the user presses FIRE (commit=true)
     * or moves the cursor while a shape is active (commit=false for preview).
     */
    private void applyToolAt(int x, int y, boolean commit) {
        if (npcMode) return;

        switch (currentTool) {

            // ── Pencil: immediate single-tile paint ───────────
            case TOOL_PENCIL:
                paintTile(x, y, currentTile);
                break;

            // ── Eraser: paint tile 0 ──────────────────────────
            case TOOL_ERASER:
                paintTile(x, y, 0);
                break;

            // ── Flood fill ────────────────────────────────────
            case TOOL_FILL:
                if (commit) {
                    int target = mapData[currentLayer][y][x];
                    if (target != currentTile) {
                        floodFillUndo(x, y, target, currentTile);
                    }
                }
                break;

            // ── Eyedropper: pick tile under cursor ────────────
            case TOOL_EYEDROPPER:
                if (commit) {
                    currentTile = mapData[currentLayer][y][x];
                    currentTool = prevTool;   // restore previous tool
                    setStatus("Picked: " + TileData.getName(currentTile));
                }
                break;

            // ── Rectangle (filled) ────────────────────────────
            case TOOL_RECT:
                if (!toolDragging && commit) {
                    // First press: set anchor
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("Rect: drag to corner");
                } else if (toolDragging) {
                    buildRectPreview(toolAnchorX, toolAnchorY, x, y, true);
                    if (commit) {
                        commitPreview();
                        toolDragging = false;
                        setStatus("Rect drawn");
                    }
                }
                break;

            // ── Rectangle (outline) ───────────────────────────
            case TOOL_RECT_OUTLINE:
                if (!toolDragging && commit) {
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("RectOut: drag to corner");
                } else if (toolDragging) {
                    buildRectPreview(toolAnchorX, toolAnchorY, x, y, false);
                    if (commit) {
                        commitPreview();
                        toolDragging = false;
                        setStatus("Outline drawn");
                    }
                }
                break;

            // ── Line ──────────────────────────────────────────
            case TOOL_LINE:
                if (!toolDragging && commit) {
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("Line: move to end, press 5");
                } else if (toolDragging) {
                    buildLinePreview(toolAnchorX, toolAnchorY, x, y);
                    if (commit) {
                        commitPreview();
                        toolDragging = false;
                        setStatus("Line drawn");
                    }
                }
                break;

            // ── Ellipse (outline) ─────────────────────────────
            case TOOL_ELLIPSE:
                if (!toolDragging && commit) {
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("Ellipse: drag to corner");
                } else if (toolDragging) {
                    buildEllipsePreview(toolAnchorX, toolAnchorY, x, y, false);
                    if (commit) {
                        commitPreview();
                        toolDragging = false;
                        setStatus("Ellipse drawn");
                    }
                }
                break;

            // ── Ellipse (filled) ──────────────────────────────
            case TOOL_ELLIPSE_FILL:
                if (!toolDragging && commit) {
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("EllFill: drag to corner");
                } else if (toolDragging) {
                    buildEllipsePreview(toolAnchorX, toolAnchorY, x, y, true);
                    if (commit) {
                        commitPreview();
                        toolDragging = false;
                        setStatus("Ellipse filled");
                    }
                }
                break;

            // ── Diamond ───────────────────────────────────────
            case TOOL_DIAMOND:
                if (!toolDragging && commit) {
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("Diamond: drag to size");
                } else if (toolDragging) {
                    buildDiamondPreview(toolAnchorX, toolAnchorY, x, y);
                    if (commit) {
                        commitPreview();
                        toolDragging = false;
                        setStatus("Diamond drawn");
                    }
                }
                break;

            // ── Scatter: random spray ─────────────────────────
            case TOOL_SCATTER:
                if (commit) {
                    scatterPaint(x, y, scatterDensity);
                }
                break;

            // ── Selection rectangle ───────────────────────────
            case TOOL_SELECT:
                if (!toolDragging && commit) {
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    hasSelection = false;
                    setStatus("Select: drag to corner");
                } else if (toolDragging) {
                    // Update live selection rect
                    selX = Math.min(toolAnchorX, x);
                    selY = Math.min(toolAnchorY, y);
                    selW = Math.abs(x - toolAnchorX) + 1;
                    selH = Math.abs(y - toolAnchorY) + 1;
                    if (commit) {
                        hasSelection = true;
                        toolDragging = false;
                        setStatus("Sel: " + selW + "x" + selH + " (Menu->Copy)");
                    }
                }
                break;

            // ── Stamp: paste clipboard at cursor ─────────────
            case TOOL_STAMP:
                if (commit && hasClipboard) {
                    pasteClipboard(x, y);
                    setStatus("Pasted " + clipW + "x" + clipH);
                }
                break;

            // ── Move selection ────────────────────────────────
            case TOOL_MOVE:
                if (!toolDragging && commit && hasSelection) {
                    // Copy first, then we'll paste at new position
                    copySelection();
                    // Erase source
                    for (int ry = selY; ry < selY + selH; ry++) {
                        for (int rx = selX; rx < selX + selW; rx++) {
                            if (rx >= 0 && rx < MAP_COLS && ry >= 0 && ry < MAP_ROWS) {
                                pushUndo(currentLayer, rx, ry,
                                    mapData[currentLayer][ry][rx], 0);
                                mapData[currentLayer][ry][rx] = 0;
                            }
                        }
                    }
                    toolAnchorX  = x;
                    toolAnchorY  = y;
                    toolDragging = true;
                    setStatus("Move: navigate to dest, press 5");
                } else if (toolDragging && commit) {
                    pasteClipboard(x, y);
                    toolDragging = false;
                    hasSelection = false;
                    setStatus("Moved");
                }
                break;
        }
    }

    /** Called every time the cursor moves — refreshes shape preview. */
    private void updateToolPreview() {
        if (!toolDragging) return;
        switch (currentTool) {
            case TOOL_RECT:          buildRectPreview(toolAnchorX, toolAnchorY, cursorX, cursorY, true);  break;
            case TOOL_RECT_OUTLINE:  buildRectPreview(toolAnchorX, toolAnchorY, cursorX, cursorY, false); break;
            case TOOL_LINE:          buildLinePreview(toolAnchorX, toolAnchorY, cursorX, cursorY);        break;
            case TOOL_ELLIPSE:       buildEllipsePreview(toolAnchorX, toolAnchorY, cursorX, cursorY, false); break;
            case TOOL_ELLIPSE_FILL:  buildEllipsePreview(toolAnchorX, toolAnchorY, cursorX, cursorY, true);  break;
            case TOOL_DIAMOND:       buildDiamondPreview(toolAnchorX, toolAnchorY, cursorX, cursorY);     break;
            case TOOL_SELECT:
                selX = Math.min(toolAnchorX, cursorX);
                selY = Math.min(toolAnchorY, cursorY);
                selW = Math.abs(cursorX - toolAnchorX) + 1;
                selH = Math.abs(cursorY - toolAnchorY) + 1;
                break;
        }
    }

    // ─── Single tile paint ────────────────────────────────────
    private void paintTile(int x, int y, int tile) {
        if (x < 0 || x >= MAP_COLS || y < 0 || y >= MAP_ROWS) return;
        int old = mapData[currentLayer][y][x];
        if (old != tile) {
            pushUndo(currentLayer, x, y, old, tile);
            mapData[currentLayer][y][x] = tile;
        }
    }

    // ─── Rectangle preview builder ────────────────────────────
    private void buildRectPreview(int ax, int ay, int bx, int by, boolean filled) {
        clearPreview();
        int x1 = Math.min(ax, bx), x2 = Math.max(ax, bx);
        int y1 = Math.min(ay, by), y2 = Math.max(ay, by);
        for (int ry = y1; ry <= y2; ry++) {
            for (int rx = x1; rx <= x2; rx++) {
                boolean onEdge = (rx == x1 || rx == x2 || ry == y1 || ry == y2);
                if (filled || onEdge) {
                    addPreview(rx, ry, currentTile);
                }
            }
        }
    }

    // ─── Bresenham line preview ───────────────────────────────
    private void buildLinePreview(int ax, int ay, int bx, int by) {
        clearPreview();
        int dx  = Math.abs(bx - ax);
        int dy  = Math.abs(by - ay);
        int sx  = ax < bx ? 1 : -1;
        int sy  = ay < by ? 1 : -1;
        int err = dx - dy;
        int x   = ax, y = ay;
        while (true) {
            addPreview(x, y, currentTile);
            if (x == bx && y == by) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
            if (previewCount >= MAX_PREVIEW) break;
        }
    }

    // ─── Midpoint ellipse preview ─────────────────────────────
    private void buildEllipsePreview(int ax, int ay, int bx, int by, boolean fill) {
        clearPreview();
        int cx = (ax + bx) / 2;
        int cy = (ay + by) / 2;
        int rw = Math.abs(bx - ax) / 2;
        int rh = Math.abs(by - ay) / 2;
        if (rw < 1) rw = 1;
        if (rh < 1) rh = 1;

        // Midpoint algorithm
        int x = 0, y = rh;
        long rw2 = (long) rw * rw;
        long rh2 = (long) rh * rh;
        long d = rh2 - rw2 * rh + rw2 / 4;

        while (2 * rh2 * x <= 2 * rw2 * y) {
            ellipsePoints(cx, cy, x, y, rw, rh, fill);
            if (d < 0) {
                d += rh2 * (2 * x + 3);
            } else {
                d += rh2 * (2 * x + 3) + rw2 * (-2 * y + 2);
                y--;
            }
            x++;
            if (previewCount >= MAX_PREVIEW - 4) break;
        }
        d = (long)(rh2 * (x + 1) * (x + 1) / 4) + rw2 * (y - 1) * (y - 1) - (long) rw2 * rh2;
        while (y >= 0) {
            ellipsePoints(cx, cy, x, y, rw, rh, fill);
            if (d > 0) {
                d += rw2 * (-2 * y + 3);
            } else {
                d += rh2 * (2 * x + 2) + rw2 * (-2 * y + 3);
                x++;
            }
            y--;
            if (previewCount >= MAX_PREVIEW - 4) break;
        }
    }

    private void ellipsePoints(int cx, int cy, int x, int y,
                                int rw, int rh, boolean fill) {
        if (fill) {
            for (int fx = cx - x; fx <= cx + x; fx++) {
                addPreview(fx, cy + y, currentTile);
                addPreview(fx, cy - y, currentTile);
            }
        } else {
            addPreview(cx + x, cy + y, currentTile);
            addPreview(cx - x, cy + y, currentTile);
            addPreview(cx + x, cy - y, currentTile);
            addPreview(cx - x, cy - y, currentTile);
        }
    }

    // ─── Diamond (rotated square) preview ────────────────────
    private void buildDiamondPreview(int ax, int ay, int bx, int by) {
        clearPreview();
        int cx  = (ax + bx) / 2;
        int cy  = (ay + by) / 2;
        int rx  = Math.abs(bx - ax) / 2;
        int ry  = Math.abs(by - ay) / 2;
        int rad = Math.max(rx, ry);
        // Outline: |dx|/rx + |dy|/ry == 1  (Manhattan distance on axes)
        for (int dx = -rad; dx <= rad; dx++) {
            int remaining = rad - Math.abs(dx);
            addPreview(cx + dx, cy + remaining, currentTile);
            addPreview(cx + dx, cy - remaining, currentTile);
        }
    }

    // ─── Scatter / spray paint ────────────────────────────────
    private void scatterPaint(int cx, int cy, int density) {
        // density 1-10: number of radius-3 random tiles
        int radius = 3;
        long seed  = System.currentTimeMillis();
        for (int i = 0; i < density * 2; i++) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L) & 0x7FFFFFFFFFFFFFFFL;
            int ox = (int)((seed >> 16) % (radius * 2 + 1)) - radius;
            seed = (seed * 6364136223846793005L + 1442695040888963407L) & 0x7FFFFFFFFFFFFFFFL;
            int oy = (int)((seed >> 16) % (radius * 2 + 1)) - radius;
            paintTile(cx + ox, cy + oy, currentTile);
        }
    }

    // ─── Preview buffer helpers ───────────────────────────────
    private void addPreview(int x, int y, int tile) {
        if (previewCount >= MAX_PREVIEW) return;
        if (x < 0 || x >= MAP_COLS || y < 0 || y >= MAP_ROWS) return;
        // Dedup check (simple linear scan — preview is small)
        for (int i = 0; i < previewCount; i++) {
            if (previewX[i] == x && previewY[i] == y) {
                previewTile[i] = tile;
                return;
            }
        }
        previewX[previewCount]    = x;
        previewY[previewCount]    = y;
        previewTile[previewCount] = tile;
        previewCount++;
    }

    private void clearPreview() {
        previewCount = 0;
    }

    /** Writes preview tiles into the real map with undo support. */
    private void commitPreview() {
        for (int i = 0; i < previewCount; i++) {
            int x = previewX[i], y = previewY[i], t = previewTile[i];
            int old = mapData[currentLayer][y][x];
            if (old != t) {
                pushUndo(currentLayer, x, y, old, t);
                mapData[currentLayer][y][x] = t;
            }
        }
        clearPreview();
    }

    // ─── Flood fill with undo (entire fill as one block) ─────
    private void floodFillUndo(int startX, int startY, int oldTile, int newTile) {
        if (oldTile == newTile) return;
        if (startX < 0 || startX >= MAP_COLS || startY < 0 || startY >= MAP_ROWS) return;

        int[] qx = new int[MAP_COLS * MAP_ROWS];
        int[] qy = new int[MAP_COLS * MAP_ROWS];
        int head = 0, tail = 0;

        qx[tail] = startX; qy[tail] = startY; tail++;
        mapData[currentLayer][startY][startX] = newTile;
        pushUndo(currentLayer, startX, startY, oldTile, newTile);

        int[] ddx = {0, 0, -1, 1};
        int[] ddy = {-1, 1, 0, 0};
        while (head < tail && tail < qx.length - 4) {
            int cx = qx[head], cy = qy[head]; head++;
            for (int d = 0; d < 4; d++) {
                int nx = cx + ddx[d], ny = cy + ddy[d];
                if (nx >= 0 && nx < MAP_COLS && ny >= 0 && ny < MAP_ROWS) {
                    if (mapData[currentLayer][ny][nx] == oldTile) {
                        mapData[currentLayer][ny][nx] = newTile;
                        pushUndo(currentLayer, nx, ny, oldTile, newTile);
                        qx[tail] = nx; qy[tail] = ny; tail++;
                    }
                }
            }
        }
    }

    // ─── Copy selection to clipboard ─────────────────────────
    private void copySelection() {
        if (!hasSelection) return;
        clipW = selW; clipH = selH;
        clipboard = new int[NUM_LAYERS * clipH * clipW];
        for (int l = 0; l < NUM_LAYERS; l++) {
            for (int ry = 0; ry < clipH; ry++) {
                for (int rx = 0; rx < clipW; rx++) {
                    int mx = selX + rx, my = selY + ry;
                    int val = (mx >= 0 && mx < MAP_COLS && my >= 0 && my < MAP_ROWS)
                            ? mapData[l][my][mx] : 0;
                    clipboard[l * clipH * clipW + ry * clipW + rx] = val;
                }
            }
        }
        hasClipboard = true;
    }

    /** Pastes clipboard at (destX, destY) as top-left corner. */
    private void pasteClipboard(int destX, int destY) {
        if (!hasClipboard) return;
        for (int l = 0; l < NUM_LAYERS; l++) {
            for (int ry = 0; ry < clipH; ry++) {
                for (int rx = 0; rx < clipW; rx++) {
                    int mx = destX + rx, my = destY + ry;
                    if (mx < 0 || mx >= MAP_COLS || my < 0 || my >= MAP_ROWS) continue;
                    int tile = clipboard[l * clipH * clipW + ry * clipW + rx];
                    int old  = mapData[l][my][mx];
                    if (old != tile) {
                        pushUndo(l, mx, my, old, tile);
                        mapData[l][my][mx] = tile;
                    }
                }
            }
        }
    }

    // ─── Activate eyedropper (saves current tool) ────────────
    private void activateEyedropper() {
        prevTool    = currentTool;
        currentTool = TOOL_EYEDROPPER;
        setStatus("Eyedropper: press 5 to pick");
    }

    // =================================================
    //  FLOOD FILL (legacy, kept for backward compat)
    // =================================================
    private void floodFill(int x, int y, int oldTile, int newTile) {
        if (oldTile == newTile) return;
        if (x < 0 || x >= MAP_COLS || y < 0 || y >= MAP_ROWS) return;

        int[] qx = new int[MAP_COLS * MAP_ROWS];
        int[] qy = new int[MAP_COLS * MAP_ROWS];
        int head = 0, tail = 0;

        qx[tail] = x;
        qy[tail] = y;
        tail++;
        mapData[currentLayer][y][x] = newTile;

        while (head < tail && tail < qx.length - 4) {
            int cx = qx[head];
            int cy = qy[head];
            head++;

            int[] ddx = {0, 0, -1, 1};
            int[] ddy = {-1, 1, 0, 0};
            for (int d = 0; d < 4; d++) {
                int nx = cx + ddx[d];
                int ny = cy + ddy[d];
                if (nx >= 0 && nx < MAP_COLS && ny >= 0 && ny < MAP_ROWS) {
                    if (mapData[currentLayer][ny][nx] == oldTile) {
                        mapData[currentLayer][ny][nx] = newTile;
                        qx[tail] = nx;
                        qy[tail] = ny;
                        tail++;
                    }
                }
            }
        }
    }

    // =================================================
    //  AUTO SCROLL
    // =================================================
    private void autoScroll() {
        int margin = 2;
        if (cursorX < scrollX + margin) {
            scrollX = Math.max(0, cursorX - margin);
        }
        if (cursorX >= scrollX + viewCols - margin) {
            scrollX = Math.min(MAP_COLS - viewCols, cursorX - viewCols + margin + 1);
        }
        if (cursorY < scrollY + margin) {
            scrollY = Math.max(0, cursorY - margin);
        }
        if (cursorY >= scrollY + viewRows - margin) {
            scrollY = Math.min(MAP_ROWS - viewRows, cursorY - viewRows + margin + 1);
        }
        clampScroll();
    }

    // =================================================
    //  CLEAR MAP
    // =================================================
    private void clearMap() {
        for (int l = 0; l < NUM_LAYERS; l++) {
            for (int r = 0; r < MAP_ROWS; r++) {
                for (int c = 0; c < MAP_COLS; c++) {
                    mapData[l][r][c] = 0;
                }
            }
        }
        npcMgr.clearAll();
        cursorX = 0;
        cursorY = 0;
        scrollX = 0;
        scrollY = 0;
        undoHead = 0;
        undoCount = 0;
        redoCount = 0;
    }

    // =================================================
    //  RENDERING
    // =================================================
    private void render() {
        Graphics g = getGraphics();

        // Background
        g.setColor(0x1A1A2E);
        g.fillRect(0, 0, screenW, screenH);

        if (playtestMode) {
            renderPlaytest(g);
        } else {
            renderEditor(g);
        }

        // Overlay menus
        if (menuOpen) {
            renderMenu(g);
        } else if (selectingSlot) {
            renderSlotSelector(g);
        } else if (selectingLevel) {
            renderLevelSelector(g);
        }

        // Status message
        renderStatus(g);

        flushGraphics();
    }

    private void renderEditor(Graphics g) {
        renderHUD(g);

        // Draw map layers
        for (int layer = 0; layer < NUM_LAYERS; layer++) {
            renderLayer(g, layer);
        }

        // Draw NPCs in editor
        renderNPCsEditor(g);

        // Grid
        if (showGrid) {
            renderGrid(g);
        }

        // Map link overlays (arrows on door/stair tiles that have links)
        renderLinkOverlays(g);

        // Shape preview overlay
        if (previewCount > 0) {
            renderPreview(g);
        }

        // Selection rectangle
        if (hasSelection || (currentTool == TOOL_SELECT && toolDragging)) {
            renderSelection(g);
        }

        // Cursor
        renderCursor(g);

        // Palette
        renderPalette(g);

        // Tool panel
        if (showToolPanel) {
            renderToolPanel(g);
        }
    }

    private void renderHUD(Graphics g) {
        g.setColor(0x2A2A4A);
        g.fillRect(0, 0, screenW, hudHeight);
        g.setColor(0x4A4A6A);
        g.drawLine(0, hudHeight - 1, screenW, hudHeight - 1);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);
        g.setColor(0xFFFFFF);

        // Mode/Layer indicator
        String modeStr;
        if (npcMode) {
            modeStr = "NPC:" + NPCManager.NPC_TYPE_NAMES[currentNPCType];
        } else {
            modeStr = "L:" + LAYER_NAMES[currentLayer];
        }
        g.drawString(modeStr, 2, 1, Graphics.TOP | Graphics.LEFT);

        // Position
        String posStr = cursorX + "," + cursorY;
        g.drawString(posStr, screenW / 2, 1, Graphics.TOP | Graphics.HCENTER);

        // Tile info
        String tileStr;
        if (npcMode) {
            tileStr = "NPCs:" + npcMgr.npcCount;
        } else {
            tileStr = "T:" + currentTile;
            if (zoomLevel > 0) tileStr += " x" + (zoomLevel + 1);
        }
        g.drawString(tileStr, screenW - 2, 1, Graphics.TOP | Graphics.RIGHT);
    }

    private void renderLayer(Graphics g, int layer) {
        boolean isActive = (layer == currentLayer) && !npcMode;

        for (int vy = 0; vy < viewRows; vy++) {
            for (int vx = 0; vx < viewCols; vx++) {
                int mx = scrollX + vx;
                int my = scrollY + vy;
                if (mx >= MAP_COLS || my >= MAP_ROWS) continue;

                int tileType = mapData[layer][my][mx];
                if (layer > 0 && tileType == 0) continue;

                int drawX = offsetX + vx * tileSize;
                int drawY = offsetY + vy * tileSize;

                if (tileType >= 0 && tileType < MAX_TILES
                    && scaledTiles != null
                    && scaledTiles[tileType] != null) {
                    g.drawImage(scaledTiles[tileType], drawX, drawY,
                                Graphics.TOP | Graphics.LEFT);
                }

                // Dim non-active layers
                if (!isActive && !npcMode) {
                    g.setColor(0x000000);
                    for (int py = 0; py < tileSize; py += 2) {
                        for (int px = (py / 2) % 2; px < tileSize; px += 2) {
                            g.drawLine(drawX + px, drawY + py,
                                       drawX + px, drawY + py);
                        }
                    }
                }

                // Script dot: tiny green square in top-left corner of
                // any tile type that has a script attached
                if (isActive && tileType != TileData.TILE_VOID
                        && scriptMgr.hasScript(ScriptManager.TYPE_TILE, tileType)) {
                    g.setColor(0x00EE55);
                    g.fillRect(drawX + 1, drawY + 1, 3, 3);
                }
            }
        }
    }

    private void renderNPCsEditor(Graphics g) {
        for (int i = 0; i < npcMgr.npcCount; i++) {
            if (!npcMgr.npcActive[i]) continue;

            int vx = npcMgr.npcX[i] - scrollX;
            int vy = npcMgr.npcY[i] - scrollY;
            if (vx < 0 || vx >= viewCols || vy < 0 || vy >= viewRows) continue;

            int drawX = offsetX + vx * tileSize;
            int drawY = offsetY + vy * tileSize;
            int type = npcMgr.npcType[i];

            // NPC color by type
            int color;
            switch (type) {
                case NPCManager.NPC_FRIENDLY:  color = 0x4488FF; break;
                case NPCManager.NPC_SHOPKEEP:  color = 0xFFAA00; break;
                case NPCManager.NPC_QUEST:     color = 0x00FF88; break;
                case NPCManager.NPC_ENEMY:     color = 0xFF2222; break;
                case NPCManager.NPC_BOSS:      color = 0xFF00FF; break;
                default: color = 0x888888; break;
            }

            g.setColor(color);
            g.fillRect(drawX + 2, drawY + 2, tileSize - 4, tileSize - 4);

            // Eyes
            g.setColor(0x000000);
            int eyeY = drawY + tileSize / 3;
            g.fillRect(drawX + tileSize / 3 - 1, eyeY, 2, 2);
            g.fillRect(drawX + tileSize * 2 / 3 - 1, eyeY, 2, 2);

            // Type indicator letter
            g.setColor(0xFFFFFF);
            String letter;
            switch (type) {
                case NPCManager.NPC_FRIENDLY:  letter = "F"; break;
                case NPCManager.NPC_SHOPKEEP:  letter = "$"; break;
                case NPCManager.NPC_QUEST:     letter = "Q"; break;
                case NPCManager.NPC_ENEMY:     letter = "E"; break;
                case NPCManager.NPC_BOSS:      letter = "B"; break;
                default: letter = "?"; break;
            }
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setFont(font);
            g.drawString(letter, drawX + tileSize / 2, drawY + tileSize - font.getHeight() - 1,
                         Graphics.TOP | Graphics.HCENTER);

            // Blink border if NPC mode active
            if (npcMode && (animFrame % 2 == 0)) {
                g.setColor(0xFFFFFF);
                g.drawRect(drawX, drawY, tileSize - 1, tileSize - 1);
            }

            // Script dot: green square top-right if this NPC has a script
            if (scriptMgr.hasScript(ScriptManager.TYPE_NPC, i)) {
                g.setColor(0x00EE55);
                g.fillRect(drawX + tileSize - 4, drawY + 1, 3, 3);
            }
        }
    }

    private void renderGrid(Graphics g) {
        g.setColor(0x444466);
        int mapW = viewCols * tileSize;
        int mapH = viewRows * tileSize;

        for (int c = 0; c <= viewCols; c++) {
            int x = offsetX + c * tileSize;
            g.drawLine(x, offsetY, x, offsetY + mapH);
        }
        for (int r = 0; r <= viewRows; r++) {
            int y = offsetY + r * tileSize;
            g.drawLine(offsetX, y, offsetX + mapW, y);
        }
    }

    // =================================================
    //  TOOL PANEL RENDERING  (NEW v2.0)
    // =================================================
    // =================================================
    //  LINK OVERLAY RENDERING
    // =================================================
    /**
     * Draws a cyan arrow and target-level label on every tile
     * that has a map link attached. Helps map designers see
     * all transitions at a glance without moving the cursor.
     */
    private void renderLinkOverlays(Graphics g) {
        if (linkSys.getLinkCount() == 0) return;
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        for (int i = 0; i < linkSys.getLinkCount(); i++) {
            MapLinkSystem.MapLink ml = linkSys.getLinkAt(i);
            if (ml == null || !ml.enabled) continue;
            if (ml.srcTileX < 0) continue;  // edge link — no tile position

            int vx = ml.srcTileX - scrollX;
            int vy = ml.srcTileY - scrollY;
            if (vx < 0 || vx >= viewCols || vy < 0 || vy >= viewRows) continue;

            int dx = offsetX + vx * tileSize;
            int dy = offsetY + vy * tileSize;

            // Cyan border
            g.setColor(0x00EEEE);
            g.drawRect(dx, dy, tileSize - 1, tileSize - 1);

            // Arrow pointing down-right (→) to indicate "leads somewhere"
            int ax = dx + tileSize / 2;
            int ay = dy + tileSize / 2;
            g.setColor(0x00FFFF);
            g.drawLine(ax - 3, ay, ax + 3, ay);
            g.drawLine(ax + 1, ay - 2, ax + 3, ay);
            g.drawLine(ax + 1, ay + 2, ax + 3, ay);

            // Target level name (truncated to fit)
            if (ml.targetLevel != null && tileSize >= 10) {
                g.setFont(small);
                g.setColor(0x00FFFF);
                String lbl = ml.targetLevel.length() > 6
                           ? ml.targetLevel.substring(0, 6) : ml.targetLevel;
                g.drawString(lbl, dx + 1, dy + tileSize - small.getHeight(),
                             Graphics.TOP | Graphics.LEFT);
            }
        }
    }

    private void renderToolPanel(Graphics g) {
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int iconW  = Math.max(14, screenW / (NUM_TOOLS + 1));
        int iconH  = 14;
        int panelY = hudHeight + 2;
        int panelX = 0;

        // Background strip
        g.setColor(0x1A1A3A);
        g.fillRect(panelX, panelY, iconW * NUM_TOOLS, iconH + 2);
        g.setColor(0x3A3A6A);
        g.drawRect(panelX, panelY, iconW * NUM_TOOLS - 1, iconH + 1);

        for (int t = 0; t < NUM_TOOLS; t++) {
            int tx = panelX + t * iconW;
            int ty = panelY + 1;

            // Highlight active tool
            if (t == currentTool) {
                g.setColor(0x4466AA);
                g.fillRect(tx, ty, iconW - 1, iconH);
                g.setColor(0x88BBFF);
                g.drawRect(tx, ty, iconW - 2, iconH - 1);
            } else {
                g.setColor(0x2A2A4A);
                g.fillRect(tx, ty, iconW - 1, iconH);
            }

            // Draw tool icon
            drawToolIcon(g, t, tx + 1, ty + 1, iconW - 3, iconH - 2);
        }

        // Tool name label under panel
        g.setFont(small);
        g.setColor(0xAABBFF);
        g.drawString(TOOL_NAMES[currentTool], 2, panelY + iconH + 3,
                     Graphics.TOP | Graphics.LEFT);

        // Drag hint
        if (toolDragging) {
            g.setColor(0xFFDD44);
            g.drawString("Anchor:(" + toolAnchorX + "," + toolAnchorY + ")",
                         screenW / 2, panelY + iconH + 3, Graphics.TOP | Graphics.HCENTER);
        }
    }

    /**
     * Draws a recognisable icon for each tool using primitives only.
     * All icons fit in a w×h box starting at (x,y).
     */
    private void drawToolIcon(Graphics g, int tool, int x, int y, int w, int h) {
        int cx = x + w / 2, cy = y + h / 2;
        switch (tool) {
            case TOOL_PENCIL:      // diagonal line (pencil stroke)
                g.setColor(0xFFFFFF);
                g.drawLine(x, y + h - 1, x + w - 1, y);
                g.setColor(0xFFDD44);
                g.fillRect(x + w - 3, y, 3, 3);
                break;
            case TOOL_FILL:        // bucket: square with drop
                g.setColor(0x44AAFF);
                g.fillRect(x + 1, cy, w - 2, h / 2);
                g.setColor(0xFFFFFF);
                g.drawRect(x + 1, cy, w - 3, h / 2 - 1);
                g.setColor(0x44AAFF);
                g.fillRect(cx - 1, y, 3, cy - y);
                break;
            case TOOL_RECT:        // filled small rect
                g.setColor(0xFF8844);
                g.fillRect(x + 1, y + 1, w - 2, h - 2);
                break;
            case TOOL_RECT_OUTLINE:// outline rect
                g.setColor(0xFF8844);
                g.drawRect(x + 1, y + 1, w - 3, h - 3);
                break;
            case TOOL_LINE:        // diagonal line
                g.setColor(0xFFFFFF);
                g.drawLine(x, y + h - 1, x + w - 1, y);
                break;
            case TOOL_ELLIPSE:     // circle outline
                g.setColor(0x44FF88);
                g.drawArc(x + 1, y + 1, w - 3, h - 3, 0, 360);
                break;
            case TOOL_ELLIPSE_FILL:// filled arc
                g.setColor(0x44FF88);
                g.fillArc(x + 1, y + 1, w - 3, h - 3, 0, 360);
                break;
            case TOOL_ERASER:      // white square
                g.setColor(0xFFFFFF);
                g.fillRect(x + 1, cy - 1, w - 2, h / 2);
                g.setColor(0xAAAAAA);
                g.drawRect(x + 1, cy - 1, w - 3, h / 2 - 1);
                break;
            case TOOL_EYEDROPPER:  // cross / target
                g.setColor(0xFF44AA);
                g.drawLine(cx, y, cx, y + h - 1);
                g.drawLine(x, cy, x + w - 1, cy);
                g.drawArc(cx - 2, cy - 2, 4, 4, 0, 360);
                break;
            case TOOL_STAMP:       // star / asterisk
                g.setColor(0xFFDD00);
                g.drawLine(cx, y, cx, y + h - 1);
                g.drawLine(x, cy, x + w - 1, cy);
                g.drawLine(x, y, x + w - 1, y + h - 1);
                g.drawLine(x + w - 1, y, x, y + h - 1);
                break;
            case TOOL_SELECT:      // dashed rectangle
                g.setColor(0xFFFFFF);
                for (int i = x; i < x + w; i += 2) {
                    g.fillRect(i, y, 1, 1);
                    g.fillRect(i, y + h - 1, 1, 1);
                }
                for (int i = y; i < y + h; i += 2) {
                    g.fillRect(x, i, 1, 1);
                    g.fillRect(x + w - 1, i, 1, 1);
                }
                break;
            case TOOL_MOVE:        // four-way arrow
                g.setColor(0xFFAA44);
                g.drawLine(cx, y, cx, y + h - 1);
                g.drawLine(x, cy, x + w - 1, cy);
                g.fillTriangle(cx, y, cx - 2, y + 3, cx + 2, y + 3);
                g.fillTriangle(cx, y + h - 1, cx - 2, y + h - 4, cx + 2, y + h - 4);
                g.fillTriangle(x, cy, x + 3, cy - 2, x + 3, cy + 2);
                g.fillTriangle(x + w - 1, cy, x + w - 4, cy - 2, x + w - 4, cy + 2);
                break;
            case TOOL_DIAMOND:     // diamond outline
                g.setColor(0xAA44FF);
                g.drawLine(cx, y, x + w - 1, cy);
                g.drawLine(x + w - 1, cy, cx, y + h - 1);
                g.drawLine(cx, y + h - 1, x, cy);
                g.drawLine(x, cy, cx, y);
                break;
            case TOOL_SCATTER:     // dots spray
                g.setColor(0x88FFAA);
                g.fillRect(cx - 1, cy - 1, 2, 2);
                g.fillRect(x + 1, cy + 1, 2, 2);
                g.fillRect(cx + 2, y + 1, 2, 2);
                g.fillRect(x + 2, y + 2, 1, 1);
                g.fillRect(x + w - 3, cy + 1, 1, 1);
                break;
        }
    }

    // =================================================
    //  PREVIEW OVERLAY RENDERING  (NEW v2.0)
    // =================================================
    private void renderPreview(Graphics g) {
        for (int i = 0; i < previewCount; i++) {
            int px = (previewX[i] - scrollX) * tileSize + offsetX;
            int py = (previewY[i] - scrollY) * tileSize + offsetY;
            if (px < offsetX - tileSize || px > screenW) continue;
            if (py < offsetY - tileSize || py > screenH) continue;

            int color = TileData.getColor(previewTile[i]);
            // Draw semi-transparent preview tile (hatched approximation)
            g.setColor(color & 0x00FFFFFF | 0x99000000);
            g.setColor(color);
            g.fillRect(px + 1, py + 1, tileSize - 2, tileSize - 2);

            // Hatching to suggest "preview not committed"
            g.setColor(0x000000);
            for (int d = 0; d < tileSize; d += 3) {
                g.drawLine(px + d, py, px, py + d);
            }
            // Bright border
            g.setColor(0xFFFFFF);
            g.drawRect(px, py, tileSize - 1, tileSize - 1);
        }
    }

    // =================================================
    //  SELECTION RECTANGLE RENDERING  (NEW v2.0)
    // =================================================
    private void renderSelection(Graphics g) {
        int x1 = (selX - scrollX) * tileSize + offsetX;
        int y1 = (selY - scrollY) * tileSize + offsetY;
        int w  = selW * tileSize;
        int h  = selH * tileSize;

        // Animated dashed border (marching ants)
        int dash    = 4;
        int animOff = (int)(animTimer % (dash * 2));
        g.setColor(0xFFFFFF);
        for (int d = -animOff; d < w; d += dash * 2) {
            int sx = x1 + d, len = Math.min(dash, x1 + w - sx);
            if (len > 0 && sx < screenW && sx + len > 0)
                g.drawLine(Math.max(x1, sx), y1, Math.min(x1 + w - 1, sx + len - 1), y1);
            if (len > 0 && sx < screenW && sx + len > 0)
                g.drawLine(Math.max(x1, sx), y1 + h - 1, Math.min(x1 + w - 1, sx + len - 1), y1 + h - 1);
        }
        for (int d = -animOff; d < h; d += dash * 2) {
            int sy = y1 + d, len = Math.min(dash, y1 + h - sy);
            if (len > 0 && sy < screenH && sy + len > 0)
                g.drawLine(x1, Math.max(y1, sy), x1, Math.min(y1 + h - 1, sy + len - 1));
            if (len > 0 && sy < screenH && sy + len > 0)
                g.drawLine(x1 + w - 1, Math.max(y1, sy), x1 + w - 1, Math.min(y1 + h - 1, sy + len - 1));
        }

        // Corner handles
        g.setColor(0xFF4444);
        int hs = 3;
        g.fillRect(x1 - 1,     y1 - 1,     hs, hs);
        g.fillRect(x1 + w - 2, y1 - 1,     hs, hs);
        g.fillRect(x1 - 1,     y1 + h - 2, hs, hs);
        g.fillRect(x1 + w - 2, y1 + h - 2, hs, hs);

        // Size label
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(small);
        g.setColor(0xFFFF44);
        g.drawString(selW + "x" + selH, x1 + w / 2, y1 - 9,
                     Graphics.TOP | Graphics.HCENTER);
    }

    private void renderCursor(Graphics g) {
        int vx = cursorX - scrollX;
        int vy = cursorY - scrollY;
        if (vx < 0 || vx >= viewCols || vy < 0 || vy >= viewRows) return;

        int cx = offsetX + vx * tileSize;
        int cy = offsetY + vy * tileSize;

        boolean bright = (System.currentTimeMillis() % 500) < 250;

        // Different color for NPC mode
        int color1, color2;
        if (npcMode) {
            color1 = bright ? 0x00FFFF : 0x00AAAA;
            color2 = bright ? 0xFFFFFF : 0x00FFFF;
        } else {
            color1 = bright ? 0xFFFF00 : 0xFF8800;
            color2 = bright ? 0xFFFFFF : 0xFFFF00;
        }

        g.setColor(color1);
        g.drawRect(cx, cy, tileSize - 1, tileSize - 1);

        if (tileSize > 6) {
            g.setColor(color2);
            g.drawRect(cx + 1, cy + 1, tileSize - 3, tileSize - 3);
        }

        // Corner markers
        if (tileSize >= 8) {
            g.setColor(0xFFFFFF);
            int m = tileSize / 4;
            g.drawLine(cx, cy, cx + m, cy);
            g.drawLine(cx, cy, cx, cy + m);
            g.drawLine(cx + tileSize - 1, cy, cx + tileSize - 1 - m, cy);
            g.drawLine(cx + tileSize - 1, cy, cx + tileSize - 1, cy + m);
            g.drawLine(cx, cy + tileSize - 1, cx + m, cy + tileSize - 1);
            g.drawLine(cx, cy + tileSize - 1, cx, cy + tileSize - 1 - m);
            g.drawLine(cx + tileSize - 1, cy + tileSize - 1,
                       cx + tileSize - 1 - m, cy + tileSize - 1);
            g.drawLine(cx + tileSize - 1, cy + tileSize - 1,
                       cx + tileSize - 1, cy + tileSize - 1 - m);
        }

        // ── Script indicator badge ─────────────────────────
        // Shows a green "S" badge in the top-right corner of the cursor
        // when the tile or NPC under the cursor has a script attached.
        boolean hasScript = false;
        if (npcMode) {
            int npcId = npcMgr.findNPCAt(cursorX, cursorY);
            if (npcId >= 0) {
                hasScript = scriptMgr.hasScript(ScriptManager.TYPE_NPC, npcId);
            }
        } else {
            int tileType = mapData[currentLayer][cursorY][cursorX];
            hasScript = scriptMgr.hasScript(ScriptManager.TYPE_TILE, tileType);
        }

        if (hasScript && tileSize >= 6) {
            // Green badge background
            int bx = cx + tileSize - 6;
            int by = cy;
            g.setColor(0x00AA44);
            g.fillRect(bx, by, 6, 6);
            // "S" letter
            g.setColor(0xFFFFFF);
            Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setFont(sf);
            g.drawChar('S', bx + 1, by, Graphics.TOP | Graphics.LEFT);
        }

        // ── Press-0 hint in HUD when cursor on scriptable target ──
        // (rendered once per frame at the bottom of the HUD)
        if (tileSize >= 8) {
            Font hf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(hf);
            g.setColor(hasScript ? 0x44FF88 : 0xAAAAAA);
            String hint = hasScript ? "0=Edit Script" : "0=Add Script";
            g.drawString(hint, screenW - 2, hudHeight - 10,
                         Graphics.BOTTOM | Graphics.RIGHT);
        }
    }

    private void renderPalette(Graphics g) {
        int palY = screenH - paletteHeight;

        g.setColor(0x2A2A4A);
        g.fillRect(0, palY, screenW, paletteHeight);
        g.setColor(0x4A4A6A);
        g.drawLine(0, palY, screenW, palY);

        if (npcMode) {
            // Show NPC types instead of tiles
            renderNPCPalette(g, palY);
        } else {
            // Show tiles
            renderTilePalette(g, palY);
        }
    }

    private void renderTilePalette(Graphics g, int palY) {
        int previewSize = Math.min(paletteHeight - 4, 16);
        if (previewSize < 6) previewSize = 6;

        int tilesVisible = (screenW - 8) / (previewSize + 2);
        if (tilesVisible > MAX_TILES) tilesVisible = MAX_TILES;

        int startTile = 0;
        if (currentTile >= tilesVisible) {
            startTile = currentTile - tilesVisible + 1;
        }

        int totalW = tilesVisible * (previewSize + 2);
        int startX = (screenW - totalW) / 2;
        if (startX < 2) startX = 2;

        for (int i = 0; i < tilesVisible; i++) {
            int tileIdx = startTile + i;
            if (tileIdx >= MAX_TILES) break;

            int px = startX + i * (previewSize + 2);
            int py = palY + 2;

            if (scaledTiles != null && scaledTiles[tileIdx] != null) {
                g.drawImage(scaledTiles[tileIdx], px, py,
                            Graphics.TOP | Graphics.LEFT);
            }

            if (tileIdx == currentTile) {
                g.setColor(0xFF0000);
                g.drawRect(px - 1, py - 1, previewSize + 1, previewSize + 1);
                g.setColor(0xFFFFFF);
                g.drawRect(px - 2, py - 2, previewSize + 3, previewSize + 3);
            }
        }

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);
        g.setColor(0x888899);
        g.drawString("1<  >3", screenW - 2, palY + paletteHeight - font.getHeight(),
                     Graphics.TOP | Graphics.RIGHT);

        // Show tile name
        g.setColor(0xAABBCC);
        g.drawString(TileData.getName(currentTile), 2, palY + paletteHeight - font.getHeight(),
                     Graphics.TOP | Graphics.LEFT);
    }

    private void renderNPCPalette(Graphics g, int palY) {
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);

        // Show current NPC type
        g.setColor(0x00FFFF);
        g.drawString("NPC: " + NPCManager.NPC_TYPE_NAMES[currentNPCType],
                     screenW / 2, palY + 2,
                     Graphics.TOP | Graphics.HCENTER);

        g.setColor(0x888899);
        g.drawString("1<  >3", screenW - 2, palY + paletteHeight - font.getHeight(),
                     Graphics.TOP | Graphics.RIGHT);

        g.setColor(0xAABBCC);
        g.drawString("5=Place/Edit", 2, palY + paletteHeight - font.getHeight(),
                     Graphics.TOP | Graphics.LEFT);
    }

    // =================================================
    //  PLAYTEST RENDERING
    // =================================================
    private void renderPlaytest(Graphics g) {
        // Draw map layers
        for (int layer = 0; layer < NUM_LAYERS; layer++) {
            renderLayerPlaytest(g, layer);
        }

        // Draw NPCs
        renderNPCsPlaytest(g);

        // Draw particles
        playEngine.drawParticles(g, scrollX, scrollY, offsetX, offsetY, tileSize);

        // Draw thrown object
        playEngine.drawThrownObject(g, scrollX, scrollY, offsetX, offsetY, tileSize);

        // Draw player
        if (heroSprite != null) {
            drawCustomHero(g);
        } else {
            playEngine.drawPlayer(g, scrollX, scrollY, offsetX, offsetY, tileSize);
        }

        // HUD
        playEngine.drawHUD(g, screenW);

        // Dialogue
        playEngine.drawDialogue(g, screenW, screenH);

        // Playtest label
        if (!playEngine.dialogueActive) {
            g.setColor(0x004400);
            g.fillRect(0, 0, screenW, hudHeight);
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setFont(font);
            g.setColor(0x00FF00);
            g.drawString("PLAYTEST - * exit, 5 interact", screenW / 2, 1,
                         Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void renderLayerPlaytest(Graphics g, int layer) {
        for (int vy = 0; vy < viewRows; vy++) {
            for (int vx = 0; vx < viewCols; vx++) {
                int mx = scrollX + vx;
                int my = scrollY + vy;
                if (mx >= MAP_COLS || my >= MAP_ROWS) continue;

                int tileType = mapData[layer][my][mx];
                if (layer > 0 && tileType == 0) continue;

                int drawX = offsetX + vx * tileSize;
                int drawY = offsetY + vy * tileSize;

                if (tileType >= 0 && tileType < MAX_TILES
                    && scaledTiles != null
                    && scaledTiles[tileType] != null) {
                    g.drawImage(scaledTiles[tileType], drawX, drawY,
                                Graphics.TOP | Graphics.LEFT);
                }
            }
        }
    }

    private void renderNPCsPlaytest(Graphics g) {
        for (int i = 0; i < npcMgr.npcCount; i++) {
            if (!npcMgr.npcActive[i]) continue;

            int vx = npcMgr.npcX[i] - scrollX;
            int vy = npcMgr.npcY[i] - scrollY;
            if (vx < 0 || vx >= viewCols || vy < 0 || vy >= viewRows) continue;

            int drawX = offsetX + vx * tileSize;
            int drawY = offsetY + vy * tileSize;
            int type = npcMgr.npcType[i];

            // Use custom sprite if available
            if (npcSprites != null && type < npcSprites.length
                && npcSprites[type] != null) {
                g.drawImage(npcSprites[type], drawX, drawY,
                            Graphics.TOP | Graphics.LEFT);
            } else {
                // Default colored rectangle
                int color;
                switch (type) {
                    case NPCManager.NPC_FRIENDLY:  color = 0x4488FF; break;
                    case NPCManager.NPC_SHOPKEEP:  color = 0xFFAA00; break;
                    case NPCManager.NPC_QUEST:     color = 0x00FF88; break;
                    case NPCManager.NPC_ENEMY:     color = 0xFF2222; break;
                    case NPCManager.NPC_BOSS:      color = 0xFF00FF; break;
                    default: color = 0x888888; break;
                }
                g.setColor(color);
                g.fillRect(drawX + 2, drawY + 2, tileSize - 4, tileSize - 4);

                // Face
                g.setColor(0x000000);
                g.fillRect(drawX + 4, drawY + 4, 2, 2);
                g.fillRect(drawX + tileSize - 6, drawY + 4, 2, 2);
            }
        }
    }

    private void drawCustomHero(Graphics g) {
        int px = (playEngine.playerX - scrollX) * tileSize + offsetX;
        int py = (playEngine.playerY - scrollY) * tileSize + offsetY;

        if (playEngine.isHurt && (playEngine.hurtTimer % 4) < 2) {
            return;
        }

        int frame = playEngine.playerFrame % 4;
        int frameW = heroSpriteSize;
        int srcX = frame * frameW;

        g.drawRegion(heroSprite, srcX, 0, frameW, heroSprite.getHeight(),
                     Sprite.TRANS_NONE, px, py, Graphics.TOP | Graphics.LEFT);
    }

    // =================================================
    //  MENU RENDERING
    // =================================================
    private void renderMenu(Graphics g) {
        g.setColor(0x000000);
        for (int y = 0; y < screenH; y += 2) {
            g.drawLine(0, y, screenW, y);
        }

        int menuW = screenW * 3 / 4;
        int itemH = 16;
        int menuH = MENU_COUNT * itemH + 20;
        int menuX = (screenW - menuW) / 2;
        int menuY = (screenH - menuH) / 2;

        g.setColor(0x1A1A3A);
        g.fillRect(menuX, menuY, menuW, menuH);
        g.setColor(0x4444AA);
        g.drawRect(menuX, menuY, menuW - 1, menuH - 1);
        g.drawRect(menuX + 1, menuY + 1, menuW - 3, menuH - 3);

        Font fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        Font fontPlain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        g.setFont(fontBold);
        g.setColor(0xFFFF00);
        g.drawString("MENU", menuX + menuW / 2, menuY + 2,
                     Graphics.TOP | Graphics.HCENTER);

        g.setFont(fontPlain);
        int itemY = menuY + 4 + itemH;
        for (int i = 0; i < MENU_COUNT; i++) {
            boolean selected = (i == menuSelection);

            if (selected) {
                g.setColor(0x4444AA);
                g.fillRect(menuX + 4, itemY - 1, menuW - 8, itemH);
                g.setColor(0xFFFFFF);
            } else {
                g.setColor(0xAABBCC);
            }

            String label = MENU_LABELS[i];
            if (i == MENU_GRID) {
                label += showGrid ? " [ON]" : " [OFF]";
            } else if (i == MENU_UNDO) {
                label += " (" + undoCount + ")";
            } else if (i == MENU_REDO) {
                label += " (" + redoCount + ")";
            }

            g.drawString(label, menuX + 8, itemY,
                         Graphics.TOP | Graphics.LEFT);

            if (selected) {
                g.drawString(">", menuX + 4, itemY, Graphics.TOP | Graphics.LEFT);
            }

            itemY += itemH;
        }

        g.setColor(0x666688);
        g.drawString("5=Select  *=Close", menuX + menuW / 2, menuY + menuH - 12,
                     Graphics.TOP | Graphics.HCENTER);
    }

    private void renderSlotSelector(Graphics g) {
        g.setColor(0x000000);
        for (int y = 0; y < screenH; y += 2) {
            g.drawLine(0, y, screenW, y);
        }

        int boxW = screenW * 2 / 3;
        int itemH = 18;
        int boxH = MAX_SAVE_SLOTS * itemH + 30;
        int boxX = (screenW - boxW) / 2;
        int boxY = (screenH - boxH) / 2;

        g.setColor(0x1A2A1A);
        g.fillRect(boxX, boxY, boxW, boxH);
        g.setColor(0x44AA44);
        g.drawRect(boxX, boxY, boxW - 1, boxH - 1);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);
        g.setColor(0x88FF88);
        String title = savingMode ? "SAVE TO SLOT" : "LOAD FROM SLOT";
        g.drawString(title, boxX + boxW / 2, boxY + 4,
                     Graphics.TOP | Graphics.HCENTER);

        font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);

        int itemY = boxY + 24;
        for (int i = 0; i < MAX_SAVE_SLOTS; i++) {
            boolean selected = (i == slotSelection);

            if (selected) {
                g.setColor(0x227722);
                g.fillRect(boxX + 4, itemY, boxW - 8, itemH - 2);
                g.setColor(0xFFFFFF);
            } else {
                g.setColor(0xAADDAA);
            }

            String slotName = "Level " + (i + 1);
            if (slotHasData(i)) {
                slotName += " [*]";
            }

            g.drawString(slotName, boxX + 12, itemY + 2,
                         Graphics.TOP | Graphics.LEFT);
            itemY += itemH;
        }

        g.setColor(0x668866);
        g.drawString("5=Confirm  *=Cancel", boxX + boxW / 2, boxY + boxH - 12,
                     Graphics.TOP | Graphics.HCENTER);
    }

    private void renderLevelSelector(Graphics g) {
        if (levelList == null) return;

        g.setColor(0x000000);
        for (int y = 0; y < screenH; y += 2) {
            g.drawLine(0, y, screenW, y);
        }

        int boxW = screenW * 2 / 3;
        int itemH = 18;
        int maxVisible = 5;
        int boxH = Math.min(levelList.length, maxVisible) * itemH + 30;
        int boxX = (screenW - boxW) / 2;
        int boxY = (screenH - boxH) / 2;

        g.setColor(0x1A1A2A);
        g.fillRect(boxX, boxY, boxW, boxH);
        g.setColor(0x4444AA);
        g.drawRect(boxX, boxY, boxW - 1, boxH - 1);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);
        g.setColor(0x8888FF);
        g.drawString("SELECT LEVEL", boxX + boxW / 2, boxY + 4,
                     Graphics.TOP | Graphics.HCENTER);

        font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);

        int startIdx = Math.max(0, levelSelection - maxVisible + 1);
        int itemY = boxY + 24;

        for (int i = startIdx; i < levelList.length && i < startIdx + maxVisible; i++) {
            boolean selected = (i == levelSelection);

            if (selected) {
                g.setColor(0x333366);
                g.fillRect(boxX + 4, itemY, boxW - 8, itemH - 2);
                g.setColor(0xFFFFFF);
            } else {
                g.setColor(0xAABBDD);
            }

            g.drawString(levelList[i], boxX + 12, itemY + 2,
                         Graphics.TOP | Graphics.LEFT);
            itemY += itemH;
        }

        g.setColor(0x666688);
        g.drawString("5=Load  *=Cancel", boxX + boxW / 2, boxY + boxH - 12,
                     Graphics.TOP | Graphics.HCENTER);
    }

    private void renderStatus(Graphics g) {
        if (statusMsg == null) return;
        long age = System.currentTimeMillis() - statusTimer;
        if (age > 2000) {
            statusMsg = null;
            return;
        }

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);

        int msgW = font.stringWidth(statusMsg) + 12;
        int msgH = font.getHeight() + 6;
        int msgX = (screenW - msgW) / 2;
        int msgY = offsetY + viewRows * tileSize / 2 - msgH / 2;

        g.setColor(0x000044);
        g.fillRect(msgX, msgY, msgW, msgH);
        g.setColor(0x4466FF);
        g.drawRect(msgX, msgY, msgW - 1, msgH - 1);

        g.setColor(0x44FF88);
        g.drawString(statusMsg, screenW / 2, msgY + 3,
                     Graphics.TOP | Graphics.HCENTER);
    }

    // =================================================
    //  FILE SYSTEM SAVE / LOAD
    // =================================================
    /**
     * Called by LevelEditorMIDlet.checkAutosave() on a timer.
     * Saves the current level silently under the name "autosave".
     */
    public void autoSave() {
        if (fileMgr == null || !fileMgr.isAvailable()) return;
        // Temporarily swap level name, save, then restore
        String prevName = currentLevelName;
        currentLevelName = "autosave";
        saveToFile();
        currentLevelName = prevName;
        setStatus("Autosaved.");
    }

    private void saveToFile() {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            setStatus("No file access!");
            return;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Header
            dos.writeUTF("2DLE");
            dos.writeInt(1);  // version
            dos.writeInt(MAP_COLS);
            dos.writeInt(MAP_ROWS);
            dos.writeInt(NUM_LAYERS);

            // Map data
            for (int l = 0; l < NUM_LAYERS; l++) {
                for (int r = 0; r < MAP_ROWS; r++) {
                    for (int c = 0; c < MAP_COLS; c++) {
                        dos.writeByte(mapData[l][r][c]);
                    }
                }
            }

            // NPC data
            byte[] npcData = npcMgr.saveToBytes();
            dos.writeInt(npcData.length);
            dos.write(npcData);

            dos.flush();
            byte[] data = baos.toByteArray();

            if (fileMgr.saveLevel(projectName, currentLevelName, data)) {
                // Also export dialogues
                String dialogueText = npcMgr.exportDialoguesToText();
                fileMgr.saveNPCData(projectName, dialogueText);

                // Save script registry
                scriptMgr.saveToProject(fileMgr, projectName);

                // Save map links
                linkSys.saveToProject(fileMgr, projectName, currentLevelName);

                setStatus("Saved: " + currentLevelName + ".l2de");
            } else {
                setStatus("Save failed!");
            }

        } catch (Exception e) {
            setStatus("Error: " + e.getMessage());
        }
    }

    private void loadFromFile(String levelName) {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            return;
        }

        byte[] data = fileMgr.loadLevel(projectName, levelName);
        if (data == null) {
            setStatus("File not found");
            return;
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);

            String magic = dis.readUTF();
            if (!magic.equals("2DLE")) {
                setStatus("Invalid file!");
                return;
            }

            int version = dis.readInt();
            int cols = dis.readInt();
            int rows = dis.readInt();
            int layers = dis.readInt();

            clearMap();

            for (int l = 0; l < layers && l < NUM_LAYERS; l++) {
                for (int r = 0; r < rows && r < MAP_ROWS; r++) {
                    for (int c = 0; c < cols && c < MAP_COLS; c++) {
                        mapData[l][r][c] = dis.readByte() & 0xFF;
                    }
                    // Skip extra columns if file has more
                    for (int c = MAP_COLS; c < cols; c++) {
                        dis.readByte();
                    }
                }
                // Skip extra rows if file has more
                for (int r = MAP_ROWS; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        dis.readByte();
                    }
                }
            }

            // Load NPCs
            int npcDataLen = dis.readInt();
            byte[] npcData = new byte[npcDataLen];
            dis.read(npcData);
            npcMgr.loadFromBytes(npcData);

            currentLevelName = levelName;
            setStatus("Loaded: " + levelName);

        } catch (Exception e) {
            setStatus("Load error");
        }

        // Try to load dialogue text
        String dialogueText = fileMgr.loadNPCData(projectName);
        if (dialogueText != null) {
            npcMgr.importDialoguesFromText(dialogueText);
        }

        // Load script registry
        scriptMgr.loadFromProject(fileMgr, projectName);

        // Load map links for this level
        linkSys.loadFromProject(fileMgr, projectName, currentLevelName);
    }

    // =================================================
    //  RECORDSTORE SAVE / LOAD (fallback)
    // =================================================
    private boolean slotHasData(int slot) {
        String rsName = "Level" + slot;
        try {
            RecordStore rs = RecordStore.openRecordStore(rsName, false);
            boolean hasData = rs.getNumRecords() > 0;
            rs.closeRecordStore();
            return hasData;
        } catch (Exception e) {
            return false;
        }
    }

    private void saveToSlot(int slot) {
        String rsName = "Level" + slot;
        RecordStore rs = null;
        try {
            try {
                RecordStore.deleteRecordStore(rsName);
            } catch (Exception ignored) {}

            rs = RecordStore.openRecordStore(rsName, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(MAP_COLS);
            dos.writeInt(MAP_ROWS);
            dos.writeInt(NUM_LAYERS);

            for (int l = 0; l < NUM_LAYERS; l++) {
                for (int r = 0; r < MAP_ROWS; r++) {
                    for (int c = 0; c < MAP_COLS; c++) {
                        dos.writeByte(mapData[l][r][c]);
                    }
                }
            }

            // Save NPCs
            byte[] npcData = npcMgr.saveToBytes();
            dos.writeInt(npcData.length);
            dos.write(npcData);

            dos.flush();
            byte[] data = baos.toByteArray();
            rs.addRecord(data, 0, data.length);

            setStatus("Saved to slot " + (slot + 1));

        } catch (Exception e) {
            setStatus("Save failed!");
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignored) {}
            }
        }
    }

    private void loadFromSlot(int slot) {
        String rsName = "Level" + slot;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(rsName, false);
            byte[] data = rs.getRecord(1);

            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);

            int cols = dis.readInt();
            int rows = dis.readInt();
            int layers = dis.readInt();

            clearMap();

            for (int l = 0; l < layers && l < NUM_LAYERS; l++) {
                for (int r = 0; r < rows && r < MAP_ROWS; r++) {
                    for (int c = 0; c < cols && c < MAP_COLS; c++) {
                        mapData[l][r][c] = dis.readByte() & 0xFF;
                    }
                }
            }

            // Load NPCs
            int npcDataLen = dis.readInt();
            byte[] npcData = new byte[npcDataLen];
            dis.read(npcData);
            npcMgr.loadFromBytes(npcData);

            setStatus("Loaded slot " + (slot + 1));

        } catch (RecordStoreNotFoundException e) {
            setStatus("Slot empty!");
        } catch (Exception e) {
            setStatus("Load failed!");
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception ignored) {}
            }
        }
    }

    // =================================================
    //  COMMANDS (LSK / RSK)
    // =================================================
    public void commandAction(Command c, Displayable d) {
        if (c == cmdMenu) {
            if (playtestMode) {
                playtestMode = false;
                setStatus("Edit Mode");
            } else if (selectingSlot) {
                selectingSlot = false;
            } else if (selectingLevel) {
                selectingLevel = false;
            } else {
                menuOpen = !menuOpen;
                menuSelection = 0;
            }

        } else if (c == cmdZoomIn) {
            // Cycle zoom UP:  x1 → x2 → x3 → x1
            zoomLevel++;
            if (zoomLevel > 2) zoomLevel = 0;
            recalcLayout();
            updateZoomCommands();
            setStatus("Zoom x" + (zoomLevel + 1));

        } else if (c == cmdZoomOut) {
            // Cycle zoom DOWN: x1 → x3 → x2 → x1
            zoomLevel--;
            if (zoomLevel < 0) zoomLevel = 2;
            recalcLayout();
            updateZoomCommands();
            setStatus("Zoom x" + (zoomLevel + 1));

        } else if (c == cmdBack) {
            if (menuOpen) {
                menuOpen = false;
            } else if (selectingSlot) {
                selectingSlot = false;
            } else if (selectingLevel) {
                selectingLevel = false;
            } else if (playtestMode) {
                playtestMode = false;
                setStatus("Edit Mode");
            } else {
                if (midlet.getProjectManager() != null) {
                    midlet.getProjectManager().showProjectSelector();
                } else {
                    midlet.exitApp();
                }
            }
        }
    }

    /**
     * Refreshes the Zoom +/- command labels to show the current level
     * so the player always knows which zoom step they are at.
     * Re-adds the commands so the new label is visible on the softkey bar.
     */
    private void updateZoomCommands() {
        removeCommand(cmdZoomIn);
        removeCommand(cmdZoomOut);
        String zoomTag = " [x" + (zoomLevel + 1) + "]";
        cmdZoomIn  = new Command("Zoom +" + zoomTag, Command.SCREEN, 2);
        cmdZoomOut = new Command("Zoom -" + zoomTag, Command.SCREEN, 3);
        addCommand(cmdZoomIn);
        addCommand(cmdZoomOut);
    }

    // =================================================
    //  UTILITY
    // =================================================
    private void setStatus(String msg) {
        statusMsg = msg;
        statusTimer = System.currentTimeMillis();
    }

    public NPCManager getNPCManager() {
        return npcMgr;
    }

    public PlayEngine getPlayEngine() {
        return playEngine;
    }
}