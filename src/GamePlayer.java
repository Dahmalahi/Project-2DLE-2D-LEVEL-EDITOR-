import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import java.io.*;
import java.util.Vector;

/**
 * GamePlayer.java  v1.0
 *
 * Loads a .2dip package and runs it as a fully playable game.
 * This is the public-facing runtime — end users never need the editor.
 *
 * The player can be launched two ways:
 *   1. From GamePlayerMIDlet (standalone JAR with a .2dip bundled as resource)
 *   2. From within the editor (Menu → Play Game → pick .2dip)
 *
 * Features:
 *  - Loads all levels from the package into memory on demand
 *  - Full playtest loop identical to EditorCanvas.updatePlaytest()
 *    but without any editor UI overhead
 *  - Script execution via EventSystem
 *  - Map transitions via MapLinkSystem
 *  - NPC dialogue and battle triggers
 *  - Saves game state (player position, switches, variables)
 *    to RecordStore (no file system required for saves)
 *  - Title screen with game name and start/load options
 *  - Game Over / Victory screens
 */
public class GamePlayer extends GameCanvas implements Runnable {

    // =========================================================
    //  STATE MACHINE
    // =========================================================
    private static final int STATE_TITLE    = 0;
    private static final int STATE_PLAYING  = 1;
    private static final int STATE_DIALOGUE = 2;
    private static final int STATE_GAMEOVER = 3;
    private static final int STATE_VICTORY  = 4;
    private static final int STATE_PAUSED   = 5;
    private static final int STATE_MENU     = 6;

    // =========================================================
    //  PACKAGE DATA
    // =========================================================
    private byte[]   packageData;   // raw .2dip bytes
    private Vector   sections;      // parsed sections
    private String   gameTitle;
    private String   gameAuthor;
    private String   gameVersion;
    private String   startLevel;

    // =========================================================
    //  RUNTIME SYSTEMS
    // =========================================================
    private PlayEngine    playEngine;
    private EventSystem   eventSys;
    private NPCManager    npcMgr;
    private MapLinkSystem linkSys;
    private ScriptManager scriptMgr;
    private PlayerData    playerData;
    private InventorySystem inventory;
    private SoundManager  soundMgr;

    // =========================================================
    //  MAP STATE
    // =========================================================
    private static final int MAP_COLS = 32;
    private static final int MAP_ROWS = 24;
    private static final int NUM_LAYERS = 3;
    private int[][][] mapData;
    private String currentLevel;

    // =========================================================
    //  RENDERING
    // =========================================================
    private int screenW, screenH;
    private int tileSize;
    private int viewCols, viewRows;
    private int offsetX, offsetY;
    private int hudH;
    private int scrollX, scrollY;
    private Image[] tileImages;      // generated tile sprites
    private java.util.Hashtable assetCache; // assetName -> Image (from ASSET sections)
    private java.util.Hashtable rawCache;   // filename  -> byte[] (scripts, raw files)

    // =========================================================
    //  GAME STATE
    // =========================================================
    private int    gameState;
    private long   lastInputTime;
    private int    animFrame;
    private long   animTimer;
    private Thread thread;
    private volatile boolean running;
    private int    titleCursor;  // 0=New Game 1=Load Game

    // =========================================================
    //  DIALOGUE
    // =========================================================
    private String dialogueText;
    private int    dialogueCharPos;
    private long   dialogueLastTime;
    private int    dialogueSpeed = GameConfig.TEXT_SPEED_MED;

    // =========================================================
    //  TRANSITION EFFECT
    // =========================================================
    private int  fadeLevel;   // 0=clear, 255=black
    private boolean fadingOut;

    // =========================================================
    //  EXIT CALLBACK — decouples GamePlayer from specific MIDlet
    // =========================================================
    public interface ExitCallback {
        void onGameExit();
    }

    private ExitCallback     exitCallback;
    private GamePlayerMIDlet midlet; // used by standalone GamePlayerMIDlet only

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    /** Used by GamePlayerMIDlet (standalone player). */
    public GamePlayer(GamePlayerMIDlet midlet, byte[] packageData) {
        super(true);
        this.midlet      = midlet;
        this.exitCallback = null;
        this.packageData = packageData;
        setFullScreenMode(true);
    }

    /** Used by LevelEditorMIDlet (launch from editor). */
    public GamePlayer(ExitCallback cb, byte[] packageData) {
        super(true);
        this.exitCallback = cb;
        this.midlet       = null;
        this.packageData  = packageData;
        setFullScreenMode(true);
    }

    // =========================================================
    //  INITIALISE
    // =========================================================
    public boolean init() {
        try {
            sections = GameCompiler.readSections(packageData);
        } catch (IOException e) {
            return false;
        }

        String[] hdr = GameCompiler.readHeader(packageData);
        if (hdr == null) return false;
        gameTitle   = hdr[0];
        gameAuthor  = hdr[1];
        gameVersion = hdr[2];
        startLevel  = hdr[3];

        // Init runtime systems
        playEngine  = new PlayEngine();
        eventSys    = new EventSystem();
        npcMgr      = new NPCManager();
        linkSys     = new MapLinkSystem();
        scriptMgr   = new ScriptManager();
        playerData  = new PlayerData();
        inventory   = new InventorySystem();
        soundMgr    = new SoundManager();

        // Load scripts
        byte[] sregData = GameCompiler.findSection(sections, GameCompiler.SEC_SCRIPTS, "all");
        if (sregData != null) {
            try { scriptMgr.fromBytes(sregData); } catch (Exception e) {}
        }

        // Load NPC dialogues
        byte[] npcData = GameCompiler.findSection(sections, GameCompiler.SEC_NPC, "all");
        if (npcData != null) {
            try {
                String text = new String(npcData, "UTF-8");
                npcMgr.importDialoguesFromText(text);
            } catch (Exception e) {}
        }

        // Pre-load all packed assets into memory image cache
        assetCache = new java.util.Hashtable();
        rawCache   = new java.util.Hashtable();

        // ASSET sections → image cache (PNG sprites, icons, etc.)
        Vector assetNames = GameCompiler.listAssetsInPackage(sections);
        for (int ai = 0; ai < assetNames.size(); ai++) {
            String name = (String) assetNames.elementAt(ai);
            byte[] data = GameCompiler.getAsset(sections, name);
            if (data == null) continue;
            try {
                Image img = Image.createImage(data, 0, data.length);
                assetCache.put(name, img);
            } catch (Exception e) {
                // Not an image — store as raw bytes for other uses
                rawCache.put(name, data);
            }
        }

        // SCRIPT SOURCE sections (.2dls) → raw cache
        for (int i = 0; i < sections.size(); i++) {
            Object[] sec = (Object[]) sections.elementAt(i);
            String secType = (String) sec[0];
            String secName = (String) sec[1];
            byte[] secData = (byte[]) sec[2];
            if (GameCompiler.SEC_SCRIPT_SRC.equals(secType) ||
                GameCompiler.SEC_SCRIPT_BIN.equals(secType) ||
                GameCompiler.SEC_RAW.equals(secType)) {
                rawCache.put(secName, secData);
            }
        }

        mapData = new int[NUM_LAYERS][MAP_ROWS][MAP_COLS];
        gameState = STATE_TITLE;
        titleCursor = 0;
        fadeLevel   = 0;

        screenW = getWidth();
        screenH = getHeight();
        recalcLayout();

        return true;
    }

    // =========================================================
    //  LAYOUT
    // =========================================================
    private void recalcLayout() {
        screenW = getWidth();
        screenH = getHeight();
        hudH    = Math.max(12, screenH / 12);
        int avH = screenH - hudH;
        int avW = screenW;
        int maxC = Math.min(MAP_COLS, 16);
        int maxR = Math.min(MAP_ROWS, 12);
        tileSize = Math.min(avW / maxC, avH / maxR);
        if (tileSize < 6)  tileSize = 6;
        if (tileSize > 20) tileSize = 20;
        viewCols = Math.min(MAP_COLS, avW / tileSize);
        viewRows = Math.min(MAP_ROWS, avH / tileSize);
        if (viewCols < 4) viewCols = 4;
        if (viewRows < 4) viewRows = 4;
        offsetX = (avW - viewCols * tileSize) / 2;
        offsetY = hudH + (avH - viewRows * tileSize) / 2;
        buildTileImages();
    }

    private void buildTileImages() {
        tileImages = new Image[TileData.MAX_TILES];
        for (int i = 0; i < TileData.MAX_TILES; i++) {
            tileImages[i] = generateTile(i, tileSize);
        }
    }

    private Image generateTile(int type, int size) {
        int color = TileData.getColor(type);
        int[] px = new int[size * size];
        for (int i = 0; i < px.length; i++) px[i] = color;
        // Simple shaded tile
        int light = lighten(color, 40);
        int dark  = darken(color,  40);
        for (int i = 0; i < size; i++) {
            px[i]                    = light;
            px[i * size]             = light;
            px[(size-1)*size + i]    = dark;
            px[i*size + size-1]      = dark;
        }
        return Image.createRGBImage(px, size, size, true);
    }

    // =========================================================
    //  LOAD LEVEL FROM PACKAGE
    // =========================================================
    private boolean loadLevel(String levelName) {
        byte[] data = GameCompiler.findSection(sections, GameCompiler.SEC_LEVEL, levelName);
        if (data == null) return false;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream      dis  = new DataInputStream(bais);

            String magic = dis.readUTF();
            if (!magic.equals("2DLE")) return false;
            int version = dis.readInt();
            int cols    = dis.readInt();
            int rows    = dis.readInt();
            int layers  = dis.readInt();

            // Clear map
            for (int l = 0; l < NUM_LAYERS; l++)
                for (int r = 0; r < MAP_ROWS; r++)
                    for (int c = 0; c < MAP_COLS; c++)
                        mapData[l][r][c] = 0;

            for (int l = 0; l < layers && l < NUM_LAYERS; l++) {
                for (int r = 0; r < rows && r < MAP_ROWS; r++) {
                    for (int c = 0; c < cols && c < MAP_COLS; c++) {
                        mapData[l][r][c] = dis.readByte() & 0xFF;
                    }
                    for (int c = MAP_COLS; c < cols; c++) dis.readByte();
                }
                for (int r = MAP_ROWS; r < rows; r++)
                    for (int c = 0; c < cols; c++) dis.readByte();
            }

            // Load NPC data for this level
            int npcLen = dis.readInt();
            byte[] npcBytes = new byte[npcLen];
            dis.read(npcBytes);
            npcMgr.clearAll();
            npcMgr.loadFromBytes(npcBytes);

        } catch (Exception e) { return false; }

        // Load links for this level
        byte[] linkData = GameCompiler.findSection(sections, GameCompiler.SEC_LINKS, levelName);
        linkSys.clearAll();
        if (linkData != null) {
            try { linkSys.fromBytes(linkData); } catch (Exception e) {}
        }

        currentLevel = levelName;

        // Rebuild tile images in case tileSize changed
        if (tileImages == null || tileImages.length < TileData.MAX_TILES) {
            buildTileImages();
        }
        return true;
    }

    // =========================================================
    //  START GAME
    // =========================================================
    private void startNewGame() {
        playerData = new PlayerData();
        inventory  = new InventorySystem();
        playEngine.reset();

        if (!loadLevel(startLevel)) {
            gameState = STATE_GAMEOVER;
            return;
        }

        // Find player start tile
        outer:
        for (int y = 0; y < MAP_ROWS; y++) {
            for (int x = 0; x < MAP_COLS; x++) {
                int t = mapData[2][y][x];
                if (t == TileData.TILE_STAIRS_UP || t == TileData.TILE_SPECIAL) {
                    playEngine.playerX = x;
                    playEngine.playerY = y;
                    break outer;
                }
            }
        }
        scrollX = playEngine.playerX - viewCols / 2;
        scrollY = playEngine.playerY - viewRows / 2;
        clampScroll();
        gameState = STATE_PLAYING;
        soundMgr.playMusic(SoundManager.MUS_FIELD);
    }

    private void executeTransition(MapLinkSystem.MapLink link) {
        if (link == null || link.targetLevel == null) return;
        fadingOut = true;
        fadeLevel = 0;
        // Load synchronously after a brief fade
        if (!loadLevel(link.targetLevel)) return;
        playEngine.playerX  = link.spawnX;
        playEngine.playerY  = link.spawnY;
        playEngine.playerDir = link.spawnDir;
        scrollX = playEngine.playerX - viewCols / 2;
        scrollY = playEngine.playerY - viewRows / 2;
        clampScroll();
        fadingOut = false;
        fadeLevel = 0;
    }

    // =========================================================
    //  SAVE / LOAD PLAYER STATE (RecordStore)
    // =========================================================
    private static final String SAVE_STORE = "2dip_save";

    private void saveGame() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream      dos  = new DataOutputStream(baos);
            dos.writeUTF(currentLevel != null ? currentLevel : startLevel);
            dos.writeInt(playEngine.playerX);
            dos.writeInt(playEngine.playerY);
            dos.write(playerData.toBytes());
            dos.write(inventory.toBytes());
            dos.flush();
            byte[] data = baos.toByteArray();
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore(SAVE_STORE, true);
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else rs.setRecord(1, data, 0, data.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private boolean loadGame() {
        try {
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore(SAVE_STORE, false);
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            String lvl = dis.readUTF();
            int px = dis.readInt(), py = dis.readInt();
            if (!loadLevel(lvl)) return false;
            playEngine.playerX = px;
            playEngine.playerY = py;
            scrollX = px - viewCols / 2;
            scrollY = py - viewRows / 2;
            clampScroll();
            gameState = STATE_PLAYING;
            return true;
        } catch (Exception e) { return false; }
    }

    // =========================================================
    //  GAME LOOP
    // =========================================================
    public void start() {
        running = true;
        thread  = new Thread(this);
        thread.start();
    }

    public void stop() {
        running = false;
        soundMgr.cleanup();
    }

    public void run() {
        recalcLayout();
        while (running) {
            long t0 = System.currentTimeMillis();
            animTimer++;
            if (animTimer % 10 == 0) animFrame = (animFrame + 1) % 4;

            update();
            render();

            long elapsed = System.currentTimeMillis() - t0;
            long sleep   = 33 - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (Exception e) { break; }
            }
        }
    }

    private void update() {
        eventSys.update();
        if (fadeLevel > 0 && !fadingOut) fadeLevel = Math.max(0, fadeLevel - 16);
        if (fadingOut)                   fadeLevel = Math.min(255, fadeLevel + 16);

        switch (gameState) {
            case STATE_PLAYING:  updatePlaying();  break;
            case STATE_PAUSED:   updatePaused();   break;
        }
    }

    private void updatePlaying() {
        if (eventSys.isRunning()) return;

        long now = System.currentTimeMillis();
        int keys = getKeyStates();
        int dx = 0, dy = 0;
        if ((keys & UP_PRESSED)    != 0) dy = -1;
        else if ((keys & DOWN_PRESSED)  != 0) dy =  1;
        else if ((keys & LEFT_PRESSED)  != 0) dx = -1;
        else if ((keys & RIGHT_PRESSED) != 0) dx =  1;

        if ((dx != 0 || dy != 0) && now - lastInputTime > 100) {
            playEngine.move(mapData, dx, dy, MAP_COLS, MAP_ROWS, tileSize);
            lastInputTime = now;

            int px = playEngine.playerX, py = playEngine.playerY;
            MapLinkSystem.MapLink link = linkSys.checkTransition(
                px, py, MAP_COLS, MAP_ROWS, mapData);
            if (link != null && link.trigger == MapLinkSystem.TRIGGER_STEP) {
                executeTransition(link);
                return;
            }
            if (px >= 0 && px < MAP_COLS && py >= 0 && py < MAP_ROWS) {
                scriptMgr.triggerTile(mapData[0][py][px], eventSys);
            }
        }

        playEngine.handleSliding(mapData, MAP_COLS, MAP_ROWS, tileSize);
        playEngine.updateThrow(mapData, MAP_COLS, MAP_ROWS, tileSize);
        playEngine.updateParticles();
        playEngine.updateDialogue();
        playEngine.updateDamage();

        scrollX = playEngine.playerX - viewCols / 2;
        scrollY = playEngine.playerY - viewRows / 2;
        clampScroll();

        // Check defeat
        if (!playerData.isAlive) gameState = STATE_GAMEOVER;
    }

    private void updatePaused() {}

    // =========================================================
    //  INPUT
    // =========================================================
    protected void keyPressed(int keyCode) {
        switch (gameState) {
            case STATE_TITLE:    handleTitleKey(keyCode);   break;
            case STATE_PLAYING:  handlePlayKey(keyCode);    break;
            case STATE_GAMEOVER: startNewGame();             break;
            case STATE_VICTORY:  gameState = STATE_TITLE;   break;
            case STATE_PAUSED:   gameState = STATE_PLAYING; break;
        }
    }

    private void handleTitleKey(int keyCode) {
        int action = -1;
        try { action = getGameAction(keyCode); } catch (Exception e) {}

        if (action == UP || keyCode == Canvas.KEY_NUM2) {
            titleCursor--;
            if (titleCursor < 0) titleCursor = 2;
        } else if (action == DOWN || keyCode == Canvas.KEY_NUM8) {
            titleCursor++;
            if (titleCursor > 2) titleCursor = 0;
        } else if (action == FIRE || keyCode == Canvas.KEY_NUM5) {
            if (titleCursor == 0) {
                startNewGame();
            } else if (titleCursor == 1) {
                if (!loadGame()) startNewGame();
            } else {
                // Exit — return to editor or standalone picker
                stop();
                if (exitCallback != null) {
                    exitCallback.onGameExit();
                } else if (midlet != null) {
                    midlet.returnToPicker();
                }
            }
        }
    }

    private void handlePlayKey(int keyCode) {
        if (eventSys.isRunning() && eventSys.isWaiting()) {
            eventSys.onConfirm();
            return;
        }
        int action = -1;
        try { action = getGameAction(keyCode); } catch (Exception e) {}
        if (action == FIRE || keyCode == Canvas.KEY_NUM5) {
            // Interact
            int fx = playEngine.playerX, fy = playEngine.playerY;
            switch (playEngine.playerDir) {
                case 0: fy++; break;
                case 1: fy--; break;
                case 2: fx--; break;
                case 3: fx++; break;
            }
            MapLinkSystem.MapLink link = linkSys.checkInteract(fx, fy);
            if (link != null) { executeTransition(link); return; }
            int npcId = npcMgr.findNPCAt(fx, fy);
            if (npcId >= 0) {
                if (!scriptMgr.triggerNPC(npcId, eventSys)) {
                    playEngine.interact(mapData, npcMgr, MAP_COLS, MAP_ROWS);
                }
            }
        } else if (keyCode == Canvas.KEY_STAR) {
            if (gameState == STATE_PAUSED) {
                // Second * press — exit game and return to caller
                stop();
                if (exitCallback != null) {
                    exitCallback.onGameExit();
                } else if (midlet != null) {
                    midlet.returnToPicker();
                }
            } else {
                // First * press — pause
                gameState = STATE_PAUSED;
            }
        } else if (keyCode == Canvas.KEY_NUM0) {
            saveGame();
        }
    }

    // =========================================================
    //  RENDERING
    // =========================================================
    private void render() {
        Graphics g = getGraphics();
        g.setColor(0x1A1A2E);
        g.fillRect(0, 0, screenW, screenH);

        switch (gameState) {
            case STATE_TITLE:   renderTitle(g);   break;
            case STATE_PLAYING:
            case STATE_PAUSED:  renderGame(g);    break;
            case STATE_GAMEOVER:renderGameOver(g); break;
            case STATE_VICTORY: renderVictory(g);  break;
        }

        if (fadeLevel > 0) {
            // Dark overlay for fades
            for (int y = 0; y < screenH; y += 2) {
                int alpha = fadeLevel;
                g.setColor((alpha << 16) | (alpha << 8) | alpha);
                if (y % 4 == 0) g.drawLine(0, y, screenW, y);
            }
        }

        flushGraphics();
    }

    // ── Title screen ─────────────────────────────────────────
    private void renderTitle(Graphics g) {
        Font big  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,  Font.SIZE_LARGE);
        Font med  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        Font sm   = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        // Animated star field
        for (int i = 0; i < 30; i++) {
            int sx = (i * 47 + animFrame * 2) % screenW;
            int sy = (i * 31 + animFrame)     % (screenH - 40);
            g.setColor((animFrame + i) % 3 == 0 ? 0xFFFFFF : 0x4466AA);
            g.fillRect(sx, sy, 2, 2);
        }

        // Game icon from packed assets (icon.png if present)
        Image icon = getPackedAsset("icon.png");
        if (icon != null) {
            g.drawImage(icon, screenW / 2, screenH / 8,
                        Graphics.TOP | Graphics.HCENTER);
        }

        // Game title
        g.setFont(big);
        g.setColor(0xFFDD44);
        int titleY = icon != null ? screenH / 8 + icon.getHeight() + 4 : screenH / 5;
        g.drawString(gameTitle, screenW / 2, titleY,
                     Graphics.TOP | Graphics.HCENTER);

        // Author / version
        g.setFont(sm);
        g.setColor(0xAABBCC);
        g.drawString("by " + gameAuthor + "  v" + gameVersion,
                     screenW / 2, titleY + big.getHeight() + 2,
                     Graphics.TOP | Graphics.HCENTER);

        // Menu options — 3 items: New Game / Continue / Exit
        String[] opts = { "New Game", "Continue", "Exit" };
        int[] colors  = { 0xFFFFFF,   0xFFFFFF,   0xFF6666 };
        int menuY = screenH / 2;
        for (int i = 0; i < opts.length; i++) {
            boolean sel = (i == titleCursor);
            g.setFont(sel ? big : med);
            g.setColor(sel ? colors[i] : 0x7788AA);
            String label = sel ? "> " + opts[i] + " <" : opts[i];
            g.drawString(label, screenW / 2, menuY + i * 28,
                         Graphics.TOP | Graphics.HCENTER);
        }

        // Controls hint
        g.setFont(sm);
        g.setColor(0x445566);
        g.drawString("2/8=select  5=confirm",
                     screenW / 2, screenH - 14,
                     Graphics.TOP | Graphics.HCENTER);
    }

    // ── Game screen ──────────────────────────────────────────
    private void renderGame(Graphics g) {
        // Map layers
        for (int layer = 0; layer < NUM_LAYERS; layer++) {
            for (int vy = 0; vy < viewRows; vy++) {
                for (int vx = 0; vx < viewCols; vx++) {
                    int mx = scrollX + vx, my = scrollY + vy;
                    if (mx >= MAP_COLS || my >= MAP_ROWS) continue;
                    int tt = mapData[layer][my][mx];
                    if (layer > 0 && tt == 0) continue;
                    int dx = offsetX + vx * tileSize;
                    int dy = offsetY + vy * tileSize;
                    if (tt >= 0 && tt < TileData.MAX_TILES && tileImages[tt] != null) {
                        g.drawImage(tileImages[tt], dx, dy, Graphics.TOP | Graphics.LEFT);
                    }
                }
            }
        }

        // NPCs
        for (int i = 0; i < npcMgr.npcCount; i++) {
            if (!npcMgr.npcActive[i]) continue;
            int vx = npcMgr.npcX[i] - scrollX, vy = npcMgr.npcY[i] - scrollY;
            if (vx < 0 || vx >= viewCols || vy < 0 || vy >= viewRows) continue;
            int dx = offsetX + vx * tileSize, dy = offsetY + vy * tileSize;
            int[] npcColors = { 0xFF4488FF, 0xFFFFAA00, 0xFF00FF88,
                                0xFFFF2222, 0xFFFF00FF };
            int col = npcColors[Math.min(npcMgr.npcType[i], npcColors.length - 1)];
            g.setColor(col);
            g.fillRect(dx + 2, dy + 2, tileSize - 4, tileSize - 4);
            g.setColor(0x000000);
            int ey = dy + tileSize / 3;
            g.fillRect(dx + tileSize / 3 - 1, ey, 2, 2);
            g.fillRect(dx + tileSize * 2 / 3 - 1, ey, 2, 2);
        }

        // Player
        int pvx = playEngine.playerX - scrollX, pvy = playEngine.playerY - scrollY;
        if (pvx >= 0 && pvx < viewCols && pvy >= 0 && pvy < viewRows) {
            int pdx = offsetX + pvx * tileSize, pdy = offsetY + pvy * tileSize;
            g.setColor(0xFFFFFF);
            g.fillRect(pdx + 2, pdy + 2, tileSize - 4, tileSize - 4);
            g.setColor(0x222266);
            int ey = pdy + tileSize / 3;
            g.fillRect(pdx + tileSize / 3 - 1, ey, 2, 2);
            g.fillRect(pdx + tileSize * 2 / 3 - 1, ey, 2, 2);
        }

        // Script text box
        if (eventSys.isRunning() && eventSys.isWaiting()) {
            String txt = eventSys.getCurrentText();
            if (txt != null && txt.length() > 0) {
                int bh = screenH / 4;
                int by = screenH - bh - 2;
                g.setColor(0x001122);
                g.fillRect(2, by, screenW - 4, bh);
                g.setColor(0x3388BB);
                g.drawRect(2, by, screenW - 5, bh - 1);
                Font tf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
                g.setFont(tf);
                g.setColor(0xFFFFFF);
                // Word-wrap simple (split at screenW/charW chars)
                int maxChars = (screenW - 10) / tf.charWidth('A');
                int ty = by + 4;
                while (txt.length() > 0 && ty < by + bh - tf.getHeight()) {
                    int end = Math.min(txt.length(), maxChars);
                    g.drawString(txt.substring(0, end), 6, ty, Graphics.TOP | Graphics.LEFT);
                    txt = txt.substring(end);
                    ty += tf.getHeight() + 2;
                }
                if (eventSys.isTextComplete()) {
                    g.setColor(0xFFDD44);
                    g.drawString("5", screenW - 8, by + bh - tf.getHeight() - 2,
                                 Graphics.TOP | Graphics.LEFT);
                }
            }
        }

        // HUD
        renderHUD(g);

        if (gameState == STATE_PAUSED) {
            g.setColor(0x000000);
            g.fillRect(screenW/4, screenH/3, screenW/2, screenH/4);
            g.setColor(0xFFFFFF);
            Font pf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            g.setFont(pf);
            g.drawString("PAUSED", screenW/2, screenH/3 + 8,
                         Graphics.TOP | Graphics.HCENTER);
            Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(sf);
            g.setColor(0xAAAAFF);
            g.drawString("* again = exit   0 = save", screenW/2, screenH/3 + 28,
                         Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void renderHUD(Graphics g) {
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(0x1A1A3A);
        g.fillRect(0, 0, screenW, hudH);
        g.setColor(0x3A3A6A);
        g.drawLine(0, hudH - 1, screenW, hudH - 1);
        // HP bar
        int hp = playerData.hp, mhp = playerData.maxHp;
        int bw = screenW / 3;
        g.setColor(0x440000);
        g.fillRect(4, 3, bw, 7);
        int hpColor = hp > mhp / 4 ? 0xFF2222 : 0xFF6600;
        g.setColor(hpColor);
        g.fillRect(4, 3, bw * hp / Math.max(1, mhp), 7);
        g.setColor(0xFFFFFF);
        g.drawRect(4, 3, bw, 7);
        // Level
        g.setFont(sf);
        g.setColor(0xFFDD44);
        g.drawString("Lv" + playerData.level, 4 + bw + 4, 2,
                     Graphics.TOP | Graphics.LEFT);
        // Map name
        g.setColor(0xAABBCC);
        g.drawString(currentLevel != null ? currentLevel : "", screenW / 2, 2,
                     Graphics.TOP | Graphics.HCENTER);
        // Controls hint
        g.setColor(0x445566);
        g.drawString("5=act *=pause 0=save", screenW - 2, 2,
                     Graphics.TOP | Graphics.RIGHT);
    }

    // ── Game Over ────────────────────────────────────────────
    private void renderGameOver(Graphics g) {
        Font bf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(0xDD2222);
        g.setFont(bf);
        g.drawString("GAME OVER", screenW / 2, screenH / 3,
                     Graphics.TOP | Graphics.HCENTER);
        g.setColor(0xAAAAAA);
        g.setFont(sf);
        g.drawString("Press any key to try again", screenW / 2, screenH / 2,
                     Graphics.TOP | Graphics.HCENTER);
    }

    // ── Victory ──────────────────────────────────────────────
    private void renderVictory(Graphics g) {
        Font bf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(0xFFDD44);
        g.setFont(bf);
        g.drawString("YOU WIN!", screenW / 2, screenH / 3,
                     Graphics.TOP | Graphics.HCENTER);
        g.setColor(0xAAAAAA);
        g.setFont(sf);
        g.drawString(gameTitle + " - " + gameAuthor, screenW / 2, screenH / 2,
                     Graphics.TOP | Graphics.HCENTER);
        g.drawString("Press any key", screenW / 2, screenH / 2 + 20,
                     Graphics.TOP | Graphics.HCENTER);
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    /**
     * Returns a packed asset Image by filename, or null if not found.
     * Use for hero.png, npc sprites, icon.png, etc.
     */
    private Image getPackedAsset(String name) {
        if (assetCache == null) return null;
        return (Image) assetCache.get(name);
    }

    /**
     * Returns the raw bytes of any packed file by filename, or null if not found.
     * Use for .2dls script sources, .2dlb bytecodes, and other non-image files.
     */
    private byte[] getRawFile(String name) {
        if (rawCache == null) return null;
        return (byte[]) rawCache.get(name);
    }

    private void clampScroll() {
        if (scrollX < 0) scrollX = 0;
        if (scrollY < 0) scrollY = 0;
        if (scrollX > MAP_COLS - viewCols) scrollX = MAP_COLS - viewCols;
        if (scrollY > MAP_ROWS - viewRows) scrollY = MAP_ROWS - viewRows;
    }

    private int darken(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >>  8) & 0xFF) - amount);
        int b = Math.max(0, ( color        & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int lighten(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >>  8) & 0xFF) + amount);
        int b = Math.min(255, ( color        & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
