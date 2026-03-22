import java.io.*;
import java.util.Vector;

/**
 * MapLinkSystem.java  v1.0
 *
 * Manages all map transitions in the engine:
 *  - Door links (tile position → target map + spawn position)
 *  - House enter/exit (auto-trigger when player steps on DOOR tile)
 *  - Stair links (up/down dungeon floors)
 *  - Warp stones (fast-travel between anchors)
 *  - Map connection edges (walk off edge → next map)
 *
 * Each link is a MapLink record:
 *   sourceMap, sourceTileX, sourceTileY  →  targetMap, spawnX, spawnY, direction
 *
 * INTEGRATION with EditorCanvas:
 *   1. Editor places a TILE_DOOR, TILE_STAIRS_UP/DN, TILE_TELEPAD tile
 *   2. User presses 0 to open ScriptEditor OR presses # to open LinkEditor
 *   3. LinkEditor lets user pick target level + spawn position
 *   4. On playtest: updatePlaytest() calls checkTransition() each step
 *   5. If a link fires, EditorCanvas loads the target level
 */
public class MapLinkSystem {

    // =========================================================
    //  LINK TYPES
    // =========================================================
    public static final int LINK_DOOR     = 0;  // house door / room door
    public static final int LINK_STAIRS_U = 1;  // stairs going up
    public static final int LINK_STAIRS_D = 2;  // stairs going down
    public static final int LINK_TELEPAD  = 3;  // instant warp pad
    public static final int LINK_WARPSTONE= 4;  // fast-travel stone
    public static final int LINK_EDGE_N   = 5;  // walk off north edge
    public static final int LINK_EDGE_S   = 6;
    public static final int LINK_EDGE_E   = 7;
    public static final int LINK_EDGE_W   = 8;

    public static final String[] LINK_NAMES = {
        "Door", "StairsUp", "StairsDn",
        "Telepad", "WarpStone",
        "EdgeN", "EdgeS", "EdgeE", "EdgeW"
    };

    // Trigger modes
    public static final int TRIGGER_STEP   = 0;  // auto when player steps on tile
    public static final int TRIGGER_ACTION = 1;  // only on interact (5 key)

    // =========================================================
    //  MAP LINK RECORD
    // =========================================================
    public static class MapLink {
        public int  linkType;
        public int  srcTileX, srcTileY;   // -1,-1 for edge links
        public String targetLevel;        // level filename (without ext)
        public int  spawnX, spawnY;       // where player appears in target
        public int  spawnDir;             // player facing direction (0-3)
        public int  trigger;              // TRIGGER_STEP or TRIGGER_ACTION
        public boolean enabled;

        public MapLink() { enabled = true; trigger = TRIGGER_STEP; }

        public MapLink(int type, int sx, int sy,
                       String target, int tx, int ty, int dir) {
            this.linkType    = type;
            this.srcTileX    = sx;
            this.srcTileY    = sy;
            this.targetLevel = target;
            this.spawnX      = tx;
            this.spawnY      = ty;
            this.spawnDir    = dir;
            this.enabled     = true;
            this.trigger     = TRIGGER_STEP;
        }
    }

    // =========================================================
    //  STORAGE
    // =========================================================
    private static final int MAX_LINKS   = 128;
    private Vector links;   // Vector of MapLink

    // File magic
    private static final String LINKS_FILE = "maplinks.mls";
    private static final int    MAGIC      = 0x4D4C4E4B; // "MLNK"

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public MapLinkSystem() {
        links = new Vector();
    }

    // =========================================================
    //  ADD / REMOVE LINKS
    // =========================================================
    public int addLink(MapLink link) {
        if (links.size() >= MAX_LINKS) return -1;
        links.addElement(link);
        return links.size() - 1;
    }

    /** Quick helper: add a door link. */
    public int addDoor(int sx, int sy, String target, int tx, int ty, int dir) {
        return addLink(new MapLink(LINK_DOOR, sx, sy, target, tx, ty, dir));
    }

    /** Add a stair link. */
    public int addStairs(boolean goingUp, int sx, int sy,
                         String target, int tx, int ty) {
        int type = goingUp ? LINK_STAIRS_U : LINK_STAIRS_D;
        return addLink(new MapLink(type, sx, sy, target, tx, ty, 0));
    }

    /** Add a telepad link. */
    public int addTelepad(int sx, int sy, String target, int tx, int ty) {
        return addLink(new MapLink(LINK_TELEPAD, sx, sy, target, tx, ty, 0));
    }

    /** Add a map-edge connection. */
    public int addEdge(int direction, String target, int tx, int ty) {
        // direction: 0=N 1=S 2=E 3=W → LINK_EDGE_N..W
        int type = LINK_EDGE_N + direction;
        return addLink(new MapLink(type, -1, -1, target, tx, ty, 0));
    }

    public void removeLink(int index) {
        if (index >= 0 && index < links.size()) {
            links.removeElementAt(index);
        }
    }

    public void removeLinkAt(int tileX, int tileY) {
        for (int i = links.size() - 1; i >= 0; i--) {
            MapLink ml = (MapLink) links.elementAt(i);
            if (ml.srcTileX == tileX && ml.srcTileY == tileY) {
                links.removeElementAt(i);
                return;
            }
        }
    }

    public void clearAll() {
        links.removeAllElements();
    }

    // =========================================================
    //  QUERY
    // =========================================================
    public int getLinkCount() {
        return links.size();
    }

    public MapLink getLinkAt(int index) {
        if (index < 0 || index >= links.size()) return null;
        return (MapLink) links.elementAt(index);
    }

    /** Returns first link whose source tile matches (x,y), or null. */
    public MapLink findLink(int tileX, int tileY) {
        for (int i = 0; i < links.size(); i++) {
            MapLink ml = (MapLink) links.elementAt(i);
            if (ml.enabled && ml.srcTileX == tileX && ml.srcTileY == tileY) {
                return ml;
            }
        }
        return null;
    }

    public boolean hasLinkAt(int tileX, int tileY) {
        return findLink(tileX, tileY) != null;
    }

    /**
     * Returns the edge link for a given direction, or null.
     * direction: 0=N 1=S 2=E 3=W
     */
    public MapLink findEdgeLink(int direction) {
        int type = LINK_EDGE_N + direction;
        for (int i = 0; i < links.size(); i++) {
            MapLink ml = (MapLink) links.elementAt(i);
            if (ml.enabled && ml.linkType == type) return ml;
        }
        return null;
    }

    // =========================================================
    //  TRIGGER CHECK  (called from EditorCanvas.updatePlaytest)
    // =========================================================
    /**
     * Checks if the player at (px, py) triggers any link.
     * Also checks map edges if player would walk off-map.
     *
     * @param px         player tile X
     * @param py         player tile Y
     * @param mapCols    map width in tiles
     * @param mapRows    map height in tiles
     * @param mapData    tile data [layer][row][col]
     * @return  The triggered MapLink, or null
     */
    public MapLink checkTransition(int px, int py, int mapCols, int mapRows,
                                   int[][][] mapData) {
        // Edge checks
        if (py < 0)        return findEdgeLink(0); // north
        if (py >= mapRows) return findEdgeLink(1); // south
        if (px >= mapCols) return findEdgeLink(2); // east
        if (px < 0)        return findEdgeLink(3); // west

        // Tile-based link
        return findLink(px, py);
    }

    /**
     * Checks tiles that are interact-triggered (doors requiring action key).
     * Returns link if (facedX, facedY) has an ACTION-triggered link.
     */
    public MapLink checkInteract(int facedX, int facedY) {
        MapLink ml = findLink(facedX, facedY);
        if (ml != null && ml.trigger == TRIGGER_ACTION) return ml;
        return null;
    }

    // =========================================================
    //  LISTING (for editor UI)
    // =========================================================
    public Vector listLinks() {
        Vector v = new Vector();
        for (int i = 0; i < links.size(); i++) {
            MapLink ml = (MapLink) links.elementAt(i);
            String s;
            if (ml.linkType >= LINK_EDGE_N) {
                s = i + ": " + LINK_NAMES[ml.linkType]
                  + " → " + ml.targetLevel
                  + " (" + ml.spawnX + "," + ml.spawnY + ")";
            } else {
                s = i + ": " + LINK_NAMES[ml.linkType]
                  + " [" + ml.srcTileX + "," + ml.srcTileY + "]"
                  + " → " + ml.targetLevel
                  + " (" + ml.spawnX + "," + ml.spawnY + ")";
            }
            v.addElement(s);
        }
        return v;
    }

    // =========================================================
    //  SAVE / LOAD
    // =========================================================
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(MAGIC);
        dos.writeInt(1); // version
        dos.writeInt(links.size());
        for (int i = 0; i < links.size(); i++) {
            MapLink ml = (MapLink) links.elementAt(i);
            dos.writeInt(ml.linkType);
            dos.writeInt(ml.srcTileX);
            dos.writeInt(ml.srcTileY);
            dos.writeUTF(ml.targetLevel != null ? ml.targetLevel : "");
            dos.writeInt(ml.spawnX);
            dos.writeInt(ml.spawnY);
            dos.writeInt(ml.spawnDir);
            dos.writeInt(ml.trigger);
            dos.writeBoolean(ml.enabled);
        }
        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        if (data == null || data.length < 8) return;
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        int magic = dis.readInt();
        if (magic != MAGIC) throw new IOException("Invalid map links file");
        dis.readInt(); // version
        int count = dis.readInt();
        links.removeAllElements();
        for (int i = 0; i < count; i++) {
            MapLink ml = new MapLink();
            ml.linkType    = dis.readInt();
            ml.srcTileX    = dis.readInt();
            ml.srcTileY    = dis.readInt();
            ml.targetLevel = dis.readUTF();
            ml.spawnX      = dis.readInt();
            ml.spawnY      = dis.readInt();
            ml.spawnDir    = dis.readInt();
            ml.trigger     = dis.readInt();
            ml.enabled     = dis.readBoolean();
            links.addElement(ml);
        }
    }

    public boolean saveToProject(FileManager fm, String project, String levelName) {
        try {
            byte[] data = toBytes();
            // Store per-level: links_level01.mls
            String path = fm.getProjectPath(project) + "links_" + levelName + ".mls";
            return fm.writeFile(path, data);
        } catch (IOException e) { return false; }
    }

    public boolean loadFromProject(FileManager fm, String project, String levelName) {
        String path = fm.getProjectPath(project) + "links_" + levelName + ".mls";
        byte[] data = fm.readFile(path);
        if (data == null) return false;
        try { fromBytes(data); return true; }
        catch (IOException e) { return false; }
    }
}
