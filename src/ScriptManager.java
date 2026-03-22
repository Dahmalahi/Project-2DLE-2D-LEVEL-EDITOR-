import java.io.*;
import java.util.Vector;

/**
 * ScriptManager.java — Per-project script registry  v1.0
 *
 * Maintains the mapping from (targetType, targetId) → int[] bytecode.
 * Integrates with FileManager for persistence and EventSystem for execution.
 *
 * FEATURES:
 *  - Attach scripts to tiles (by tile type ID), NPCs (by NPC slot), or
 *    map event zones (by event ID)
 *  - Bulk save/load entire script registry to a single binary file
 *  - Export all scripts as a human-readable .2dls directory listing
 *  - Hot-reload: re-compile from source without restarting
 *  - Script listing / browser API for the editor
 *  - Trigger helper: call the right script when the player interacts
 *    with a tile or NPC
 *  - Common-event library: scripts not bound to a specific target,
 *    callable from any other script or game event
 */
public class ScriptManager {

    // =========================================================
    //  CONSTANTS
    // =========================================================
    public static final int TYPE_TILE   = 0;
    public static final int TYPE_NPC    = 1;
    public static final int TYPE_MAP    = 2;
    public static final int TYPE_COMMON = 3;  // common events (callable by id)

    private static final int MAX_TILE_SCRIPTS   = 64;   // one per tile type
    private static final int MAX_NPC_SCRIPTS    = 128;  // one per NPC slot
    private static final int MAX_MAP_SCRIPTS    = 50;   // map-global triggers
    private static final int MAX_COMMON_SCRIPTS = 50;   // shared event library

    private static final String REGISTRY_FILE = "scripts.sreg";
    private static final int    FILE_MAGIC    = 0x32444C53; // "2DLS"
    private static final int    FILE_VERSION  = 1;

    // =========================================================
    //  BYTECODE STORAGE
    // =========================================================
    private int[][] tileScripts;   // [tileTypeId]    → bytecode
    private int[][] npcScripts;    // [npcSlotId]     → bytecode
    private int[][] mapScripts;    // [eventId]       → bytecode
    private int[][] commonScripts; // [commonEventId] → bytecode

    // Source text (kept for re-editing; null if compiled externally)
    private String[] tileSources;
    private String[] npcSources;
    private String[] mapSources;
    private String[] commonSources;

    // Script names (human-readable labels shown in editor)
    private String[] tileNames;
    private String[] npcNames;
    private String[] mapNames;
    private String[] commonNames;

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public ScriptManager() {
        tileScripts    = new int[MAX_TILE_SCRIPTS][];
        npcScripts     = new int[MAX_NPC_SCRIPTS][];
        mapScripts     = new int[MAX_MAP_SCRIPTS][];
        commonScripts  = new int[MAX_COMMON_SCRIPTS][];

        tileSources    = new String[MAX_TILE_SCRIPTS];
        npcSources     = new String[MAX_NPC_SCRIPTS];
        mapSources     = new String[MAX_MAP_SCRIPTS];
        commonSources  = new String[MAX_COMMON_SCRIPTS];

        tileNames      = new String[MAX_TILE_SCRIPTS];
        npcNames       = new String[MAX_NPC_SCRIPTS];
        mapNames       = new String[MAX_MAP_SCRIPTS];
        commonNames    = new String[MAX_COMMON_SCRIPTS];
    }

    // =========================================================
    //  ATTACH SCRIPT
    // =========================================================
    /**
     * Attaches compiled bytecode (and optionally source) to a target.
     *
     * @param type     TYPE_TILE / TYPE_NPC / TYPE_MAP / TYPE_COMMON
     * @param id       slot index
     * @param bytecode compiled int[] bytecode (from ScriptLang.compile)
     * @param source   editable source text (may be null)
     * @param name     human label (may be null → auto-generated)
     */
    public void attach(int type, int id, int[] bytecode, String source, String name) {
        switch (type) {
            case TYPE_TILE:
                if (id >= 0 && id < MAX_TILE_SCRIPTS) {
                    tileScripts[id]  = bytecode;
                    tileSources[id]  = source;
                    tileNames[id]    = name != null ? name : "Tile " + id;
                }
                break;
            case TYPE_NPC:
                if (id >= 0 && id < MAX_NPC_SCRIPTS) {
                    npcScripts[id]   = bytecode;
                    npcSources[id]   = source;
                    npcNames[id]     = name != null ? name : "NPC " + id;
                }
                break;
            case TYPE_MAP:
                if (id >= 0 && id < MAX_MAP_SCRIPTS) {
                    mapScripts[id]   = bytecode;
                    mapSources[id]   = source;
                    mapNames[id]     = name != null ? name : "Event " + id;
                }
                break;
            case TYPE_COMMON:
                if (id >= 0 && id < MAX_COMMON_SCRIPTS) {
                    commonScripts[id] = bytecode;
                    commonSources[id] = source;
                    commonNames[id]   = name != null ? name : "Common " + id;
                }
                break;
        }
    }

    /** Removes a script from a target. */
    public void detach(int type, int id) {
        switch (type) {
            case TYPE_TILE:   if (id>=0&&id<MAX_TILE_SCRIPTS)   { tileScripts[id]=null;   tileSources[id]=null;   tileNames[id]=null;   } break;
            case TYPE_NPC:    if (id>=0&&id<MAX_NPC_SCRIPTS)    { npcScripts[id]=null;    npcSources[id]=null;    npcNames[id]=null;    } break;
            case TYPE_MAP:    if (id>=0&&id<MAX_MAP_SCRIPTS)    { mapScripts[id]=null;    mapSources[id]=null;    mapNames[id]=null;    } break;
            case TYPE_COMMON: if (id>=0&&id<MAX_COMMON_SCRIPTS) { commonScripts[id]=null; commonSources[id]=null; commonNames[id]=null; } break;
        }
    }

    // =========================================================
    //  QUERY
    // =========================================================
    public boolean hasScript(int type, int id) {
        return getScript(type, id) != null;
    }

    public int[] getScript(int type, int id) {
        switch (type) {
            case TYPE_TILE:   return (id>=0&&id<MAX_TILE_SCRIPTS)   ? tileScripts[id]   : null;
            case TYPE_NPC:    return (id>=0&&id<MAX_NPC_SCRIPTS)    ? npcScripts[id]    : null;
            case TYPE_MAP:    return (id>=0&&id<MAX_MAP_SCRIPTS)    ? mapScripts[id]    : null;
            case TYPE_COMMON: return (id>=0&&id<MAX_COMMON_SCRIPTS) ? commonScripts[id] : null;
        }
        return null;
    }

    public String getSource(int type, int id) {
        switch (type) {
            case TYPE_TILE:   return (id>=0&&id<MAX_TILE_SCRIPTS)   ? tileSources[id]   : null;
            case TYPE_NPC:    return (id>=0&&id<MAX_NPC_SCRIPTS)    ? npcSources[id]    : null;
            case TYPE_MAP:    return (id>=0&&id<MAX_MAP_SCRIPTS)    ? mapSources[id]    : null;
            case TYPE_COMMON: return (id>=0&&id<MAX_COMMON_SCRIPTS) ? commonSources[id] : null;
        }
        return null;
    }

    public String getName(int type, int id) {
        switch (type) {
            case TYPE_TILE:   return (id>=0&&id<MAX_TILE_SCRIPTS)   ? tileNames[id]   : null;
            case TYPE_NPC:    return (id>=0&&id<MAX_NPC_SCRIPTS)    ? npcNames[id]    : null;
            case TYPE_MAP:    return (id>=0&&id<MAX_MAP_SCRIPTS)    ? mapNames[id]    : null;
            case TYPE_COMMON: return (id>=0&&id<MAX_COMMON_SCRIPTS) ? commonNames[id] : null;
        }
        return null;
    }

    // =========================================================
    //  TRIGGER  (called by PlayEngine / EditorCanvas)
    // =========================================================
    /**
     * Triggers the script for a tile interact event.
     * @param tileType  TileData.TILE_* constant
     * @param eventSys  the active EventSystem
     * @return true if a script was started
     */
    public boolean triggerTile(int tileType, EventSystem eventSys) {
        int[] bc = getScript(TYPE_TILE, tileType);
        if (bc == null) return false;
        eventSys.runScript(bc);
        return true;
    }

    /**
     * Triggers the script for an NPC interaction.
     * @param npcSlot   slot index in NPCManager
     * @param eventSys  the active EventSystem
     * @return true if a script was started
     */
    public boolean triggerNPC(int npcSlot, EventSystem eventSys) {
        int[] bc = getScript(TYPE_NPC, npcSlot);
        if (bc == null) return false;
        eventSys.runScript(bc);
        return true;
    }

    /**
     * Triggers a map event (zone enter / switch activate).
     * @param eventId  map event slot
     * @param eventSys the active EventSystem
     * @return true if a script was started
     */
    public boolean triggerMap(int eventId, EventSystem eventSys) {
        int[] bc = getScript(TYPE_MAP, eventId);
        if (bc == null) return false;
        eventSys.runScript(bc);
        return true;
    }

    /**
     * Calls a common event by id (used from within other scripts
     * or game code like BattleSystem.onVictory).
     */
    public boolean callCommon(int commonId, EventSystem eventSys) {
        int[] bc = getScript(TYPE_COMMON, commonId);
        if (bc == null) return false;
        eventSys.runScript(bc);
        return true;
    }

    // =========================================================
    //  LISTING (for editor browser)
    // =========================================================
    /**
     * Returns a list of script info strings for the given type.
     * Format: "id: name  [N ops]"
     */
    public Vector listScripts(int type) {
        Vector v = new Vector();
        int[] counts; int[][] scripts; String[] names;
        switch (type) {
            case TYPE_TILE:   scripts=tileScripts;   names=tileNames;   counts=null; break;
            case TYPE_NPC:    scripts=npcScripts;    names=npcNames;    counts=null; break;
            case TYPE_MAP:    scripts=mapScripts;    names=mapNames;    counts=null; break;
            case TYPE_COMMON: scripts=commonScripts; names=commonNames; counts=null; break;
            default: return v;
        }
        for (int i = 0; i < scripts.length; i++) {
            if (scripts[i] != null) {
                String nm = (names[i] != null) ? names[i] : ("id=" + i);
                v.addElement(i + ": " + nm + "  [" + scripts[i].length + " ops]");
            }
        }
        return v;
    }

    /** Total number of attached scripts across all types. */
    public int totalScriptCount() {
        int n = 0;
        for (int i = 0; i < MAX_TILE_SCRIPTS;   i++) if (tileScripts[i]   != null) n++;
        for (int i = 0; i < MAX_NPC_SCRIPTS;    i++) if (npcScripts[i]    != null) n++;
        for (int i = 0; i < MAX_MAP_SCRIPTS;    i++) if (mapScripts[i]    != null) n++;
        for (int i = 0; i < MAX_COMMON_SCRIPTS; i++) if (commonScripts[i] != null) n++;
        return n;
    }

    // =========================================================
    //  HOT-RELOAD  (re-compile from source)
    // =========================================================
    /**
     * Re-compiles the stored source for a target.
     * Returns the error string, or null on success.
     */
    public String hotReload(int type, int id) {
        String src = getSource(type, id);
        if (src == null) return "No source available for " + type + "/" + id;
        ScriptLang.CompileResult res = ScriptLang.compile(src);
        if (res.bytecode == null) {
            return res.error + (res.errorLine > 0 ? " (line " + res.errorLine + ")" : "");
        }
        attach(type, id, res.bytecode, src, getName(type, id));
        return null;
    }

    /** Re-compiles ALL scripts that have stored source. Returns error count. */
    public int hotReloadAll() {
        int errors = 0;
        for (int i = 0; i < MAX_TILE_SCRIPTS;   i++) if (tileSources[i]   != null && hotReload(TYPE_TILE,   i) != null) errors++;
        for (int i = 0; i < MAX_NPC_SCRIPTS;    i++) if (npcSources[i]    != null && hotReload(TYPE_NPC,    i) != null) errors++;
        for (int i = 0; i < MAX_MAP_SCRIPTS;    i++) if (mapSources[i]    != null && hotReload(TYPE_MAP,    i) != null) errors++;
        for (int i = 0; i < MAX_COMMON_SCRIPTS; i++) if (commonSources[i] != null && hotReload(TYPE_COMMON, i) != null) errors++;
        return errors;
    }

    // =========================================================
    //  EXPORT TEXT LISTING
    // =========================================================
    /**
     * Exports all scripts with source as a readable text document.
     * Suitable for writeTextFile() for review / backup.
     */
    public String exportTextListing() {
        StringBuffer sb = new StringBuffer();
        sb.append("# 2DLE Script Registry Export\n\n");
        exportSection(sb, TYPE_TILE,   "TILE SCRIPTS",   tileScripts,   tileSources,   tileNames);
        exportSection(sb, TYPE_NPC,    "NPC SCRIPTS",    npcScripts,    npcSources,    npcNames);
        exportSection(sb, TYPE_MAP,    "MAP SCRIPTS",    mapScripts,    mapSources,    mapNames);
        exportSection(sb, TYPE_COMMON, "COMMON EVENTS",  commonScripts, commonSources, commonNames);
        return sb.toString();
    }

    private void exportSection(StringBuffer sb, int type, String header,
                                int[][] scripts, String[] sources, String[] names) {
        boolean anyFound = false;
        for (int i = 0; i < scripts.length; i++) {
            if (scripts[i] != null) { anyFound = true; break; }
        }
        if (!anyFound) return;

        sb.append("## ").append(header).append("\n\n");
        for (int i = 0; i < scripts.length; i++) {
            if (scripts[i] == null) continue;
            String nm = (names[i] != null) ? names[i] : ("id=" + i);
            sb.append("### [").append(i).append("] ").append(nm).append('\n');
            if (sources[i] != null) {
                sb.append(sources[i]).append('\n');
            } else {
                sb.append(ScriptLang.decompile(scripts[i])).append('\n');
            }
        }
    }

    // =========================================================
    //  SAVE / LOAD  (binary registry)
    // =========================================================
    /**
     * Saves the full script registry to a binary file.
     * Format:
     *   [int MAGIC] [int VERSION]
     *   [int tileCount]  { [int id][int srcLen][bytes src][int bcLen][int[] bc] }
     *   [int npcCount]   { ... }
     *   [int mapCount]   { ... }
     *   [int commonCount]{ ... }
     */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(FILE_MAGIC);
        dos.writeInt(FILE_VERSION);

        writeSection(dos, tileScripts,   tileSources,   tileNames,   MAX_TILE_SCRIPTS);
        writeSection(dos, npcScripts,    npcSources,    npcNames,    MAX_NPC_SCRIPTS);
        writeSection(dos, mapScripts,    mapSources,    mapNames,    MAX_MAP_SCRIPTS);
        writeSection(dos, commonScripts, commonSources, commonNames, MAX_COMMON_SCRIPTS);

        dos.flush();
        return baos.toByteArray();
    }

    private void writeSection(DataOutputStream dos, int[][] scripts,
                               String[] sources, String[] names, int max)
            throws IOException {
        // Count non-null
        int count = 0;
        for (int i = 0; i < max; i++) if (scripts[i] != null) count++;
        dos.writeInt(count);

        for (int i = 0; i < max; i++) {
            if (scripts[i] == null) continue;
            dos.writeInt(i); // id

            // Source (UTF-encoded, or empty if null)
            String src = (sources[i] != null) ? sources[i] : "";
            dos.writeUTF(src.length() > 0 ? src : "");

            // Name
            dos.writeUTF(names[i] != null ? names[i] : "");

            // Bytecode
            dos.writeInt(scripts[i].length);
            for (int j = 0; j < scripts[i].length; j++) {
                dos.writeInt(scripts[i][j]);
            }
        }
    }

    public void fromBytes(byte[] data) throws IOException {
        if (data == null || data.length < 8) return;
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        int magic = dis.readInt();
        if (magic != FILE_MAGIC) throw new IOException("Invalid script registry file");
        int version = dis.readInt();
        if (version != FILE_VERSION) throw new IOException("Unsupported version: " + version);

        readSection(dis, tileScripts,   tileSources,   tileNames,   MAX_TILE_SCRIPTS);
        readSection(dis, npcScripts,    npcSources,    npcNames,    MAX_NPC_SCRIPTS);
        readSection(dis, mapScripts,    mapSources,    mapNames,    MAX_MAP_SCRIPTS);
        readSection(dis, commonScripts, commonSources, commonNames, MAX_COMMON_SCRIPTS);
    }

    private void readSection(DataInputStream dis, int[][] scripts,
                              String[] sources, String[] names, int max)
            throws IOException {
        int count = dis.readInt();
        for (int c = 0; c < count; c++) {
            int id = dis.readInt();
            if (id < 0 || id >= max) {
                // Skip this entry
                dis.readUTF(); dis.readUTF();
                int bcLen = dis.readInt();
                for (int j = 0; j < bcLen; j++) dis.readInt();
                continue;
            }

            String src  = dis.readUTF();
            String name = dis.readUTF();
            int bcLen   = dis.readInt();
            int[] bc    = new int[bcLen];
            for (int j = 0; j < bcLen; j++) bc[j] = dis.readInt();

            scripts[id] = bc;
            sources[id] = src.length() > 0 ? src : null;
            names[id]   = name.length() > 0 ? name : null;
        }
    }

    /** Saves registry to project using FileManager. */
    public boolean saveToProject(FileManager fileMgr, String projectName) {
        try {
            byte[] data = toBytes();
            String path = fileMgr.getProjectPath(projectName) + REGISTRY_FILE;
            return fileMgr.writeFile(path, data);
        } catch (IOException e) {
            return false;
        }
    }

    /** Loads registry from project using FileManager. */
    public boolean loadFromProject(FileManager fileMgr, String projectName) {
        String path = fileMgr.getProjectPath(projectName) + REGISTRY_FILE;
        byte[] data = fileMgr.readFile(path);
        if (data == null) return false;
        try {
            fromBytes(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // =========================================================
    //  CLEAR
    // =========================================================
    public void clearAll() {
        for (int i = 0; i < MAX_TILE_SCRIPTS;   i++) { tileScripts[i]=null;   tileSources[i]=null;   tileNames[i]=null;   }
        for (int i = 0; i < MAX_NPC_SCRIPTS;    i++) { npcScripts[i]=null;    npcSources[i]=null;    npcNames[i]=null;    }
        for (int i = 0; i < MAX_MAP_SCRIPTS;    i++) { mapScripts[i]=null;    mapSources[i]=null;    mapNames[i]=null;    }
        for (int i = 0; i < MAX_COMMON_SCRIPTS; i++) { commonScripts[i]=null; commonSources[i]=null; commonNames[i]=null; }
    }
}
