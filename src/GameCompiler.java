import java.io.*;
import java.util.Vector;

/**
 * GameCompiler.java  v1.0
 *
 * Packages a complete 2DLE project into a single binary .2dip file
 * (2D Interactive Package) that can be distributed and played on
 * any J2ME device running the 2DLE GamePlayer engine.
 *
 * .2DIP FORMAT:
 * ─────────────
 *   [4 bytes]  Magic  = 0x32444950  ("2DIP")
 *   [4 bytes]  Version = 1
 *   [4 bytes]  Flags  (reserved)
 *   [UTF]      Game title
 *   [UTF]      Author
 *   [UTF]      Version string (e.g. "1.0")
 *   [UTF]      Start level name
 *   [4 bytes]  Section count
 *
 *   For each section:
 *     [UTF]    Section type  ("LEVEL","LINKS","SCRIPTS","NPC","ICON","META")
 *     [UTF]    Section name  (e.g. "village", "dungeon1")
 *     [4 bytes] Data length
 *     [bytes]  Data
 *
 * LEVEL section data = raw .l2de binary
 * LINKS section data = raw .mls binary (per level)
 * SCRIPTS section    = full scripts.sreg binary
 * NPC section data   = npcs.txt UTF-8 text
 * ICON section       = 16×16 ARGB raw icon pixels (1024 bytes)
 * META section       = game metadata key=value lines (UTF-8)
 *
 * Usage:
 *   GameCompiler.CompileResult r = GameCompiler.compile(fileMgr,
 *       projectName, title, author, version, startLevel);
 *   if (r.success) fileMgr.writeFile(outputPath, r.data);
 */
public class GameCompiler {

    // =========================================================
    //  CONSTANTS
    // =========================================================
    public static final int   MAGIC        = 0x32444950; // "2DIP"
    public static final int   FORMAT_VER   = 1;

    public static final String SEC_LEVEL      = "LEVEL";
    public static final String SEC_LINKS      = "LINKS";
    public static final String SEC_SCRIPTS    = "SCRIPTS";  // compiled sreg
    public static final String SEC_SCRIPT_SRC = "SCRIPSRC"; // .2dls source files
    public static final String SEC_SCRIPT_BIN = "SCRIPBIN"; // .2dlb bytecode files
    public static final String SEC_NPC        = "NPC";
    public static final String SEC_ASSET      = "ASSET";    // assets/ folder files
    public static final String SEC_ICON       = "ICON";
    public static final String SEC_META       = "META";
    public static final String SEC_RAW        = "RAW";      // any other project file

    // =========================================================
    //  COMPILE RESULT
    // =========================================================
    public static class CompileResult {
        public boolean success;
        public byte[]  data;      // the .2dip bytes, null on failure
        public String  error;     // error message, null on success
        public int     levelCount;
        public int     assetCount;
        public int     sectionCount;
        public long    totalBytes;

        CompileResult(byte[] d, int lvl, int ast, int sec) {
            success      = true;
            data         = d;
            levelCount   = lvl;
            assetCount   = ast;
            sectionCount = sec;
            totalBytes   = d.length;
        }
        CompileResult(String err) {
            success = false;
            error   = err;
        }
    }

    // =========================================================
    //  COMPILE
    // =========================================================
    /**
     * Compiles the entire project into a .2dip byte array.
     *
     * @param fileMgr      FileManager pointed at the project
     * @param projectName  project folder name
     * @param title        game title shown on launch
     * @param author       author / studio name
     * @param version      version string e.g. "1.0"
     * @param startLevel   level name to load on game start (e.g. "village")
     * @return CompileResult with .2dip bytes or error
     */
    public static CompileResult compile(FileManager fileMgr,
                                        String projectName,
                                        String title,
                                        String author,
                                        String version,
                                        String startLevel) {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            return new CompileResult("File system not available");
        }
        if (projectName == null || projectName.length() == 0) {
            return new CompileResult("No project name");
        }
        if (startLevel == null || startLevel.length() == 0) {
            return new CompileResult("Start level is required");
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream      dos  = new DataOutputStream(baos);

            // ── Header ───────────────────────────────────────
            dos.writeInt(MAGIC);
            dos.writeInt(FORMAT_VER);
            dos.writeInt(0); // flags
            dos.writeUTF(title   != null ? title   : projectName);
            dos.writeUTF(author  != null ? author  : "");
            dos.writeUTF(version != null ? version : "1.0");
            dos.writeUTF(startLevel);

            // ── Collect sections ─────────────────────────────
            Vector sections = new Vector();

            String projPath  = fileMgr.getProjectPath(projectName);
            String assetsDir = fileMgr.getAssetsPath(projectName);

            // Track which files we've already packed (full paths)
            Vector packed = new Vector();

            // ── 1. LEVEL files (.l2de) ────────────────────────
            int levelCount = 0;
            Vector levels = fileMgr.listLevels(projectName);
            for (int i = 0; i < levels.size(); i++) {
                String lvl  = (String) levels.elementAt(i);
                byte[] data = fileMgr.loadLevel(projectName, lvl);
                if (data != null) {
                    addSection(sections, SEC_LEVEL, lvl, data);
                    packed.addElement(fileMgr.getLevelPath(projectName, lvl));
                    levelCount++;
                }
            }
            if (levelCount == 0) {
                return new CompileResult("No levels found in project '" + projectName + "'");
            }

            // ── 2. MAP LINK files (links_<level>.mls) ─────────
            for (int i = 0; i < levels.size(); i++) {
                String lvl  = (String) levels.elementAt(i);
                String path = projPath + "links_" + lvl + ".mls";
                byte[] data = fileMgr.readFile(path);
                if (data != null) {
                    addSection(sections, SEC_LINKS, lvl, data);
                    packed.addElement(path);
                }
            }

            // ── 3. SCRIPT REGISTRY (scripts.sreg) ─────────────
            String sregPath = projPath + "scripts.sreg";
            byte[] sregData = fileMgr.readFile(sregPath);
            if (sregData != null) {
                addSection(sections, SEC_SCRIPTS, "scripts.sreg", sregData);
                packed.addElement(sregPath);
            }

            // ── 4. NPC DIALOGUE (npcs.txt) ────────────────────
            String npcPath = fileMgr.getNPCPath(projectName);
            String npcText = fileMgr.loadNPCData(projectName);
            if (npcText != null && npcText.length() > 0) {
                byte[] npcBytes;
                try { npcBytes = npcText.getBytes("UTF-8"); }
                catch (Exception e) { npcBytes = npcText.getBytes(); }
                addSection(sections, SEC_NPC, "npcs.txt", npcBytes);
                packed.addElement(npcPath);
            }

            // ── 5. SCRIPT SOURCE FILES (.2dls) ────────────────
            // These are individual tile/NPC script source files in project root
            Vector allProjFiles = new Vector();
            fileMgr.listAllFiles(projPath, allProjFiles);

            for (int fi = 0; fi < allProjFiles.size(); fi++) {
                String path = (String) allProjFiles.elementAt(fi);
                if (!path.endsWith(".2dls")) continue;
                if (alreadyPacked(packed, path)) continue;
                byte[] data = fileMgr.readFile(path);
                if (data == null) continue;
                String name = basename(path);
                addSection(sections, SEC_SCRIPT_SRC, name, data);
                packed.addElement(path);
            }

            // ── 6. COMPILED SCRIPT BYTECODE FILES (.2dlb) ─────
            for (int fi = 0; fi < allProjFiles.size(); fi++) {
                String path = (String) allProjFiles.elementAt(fi);
                if (!path.endsWith(".2dlb")) continue;
                if (alreadyPacked(packed, path)) continue;
                byte[] data = fileMgr.readFile(path);
                if (data == null) continue;
                String name = basename(path);
                addSection(sections, SEC_SCRIPT_BIN, name, data);
                packed.addElement(path);
            }

            // ── 7. ASSET FILES (assets/ folder — all files) ───
            int assetCount = 0;
            Vector assetFiles = new Vector();
            fileMgr.listAllFiles(assetsDir, assetFiles);
            for (int ai = 0; ai < assetFiles.size(); ai++) {
                String path = (String) assetFiles.elementAt(ai);
                if (alreadyPacked(packed, path)) continue;
                byte[] data = fileMgr.readFile(path);
                if (data == null || data.length == 0) continue;
                String name = basename(path);
                if (name.endsWith(".thumb")) continue; // skip thumbnail cache
                addSection(sections, SEC_ASSET, name, data);
                packed.addElement(path);
                assetCount++;
            }

            // ── 8. ICON (icon.raw or assets/icon.png) ─────────
            String iconPath = projPath + "icon.raw";
            byte[] iconData = fileMgr.readFile(iconPath);
            if (iconData == null) iconData = fileMgr.readFile(assetsDir + "icon.png");
            if (iconData != null && !alreadyPacked(packed, iconPath)) {
                addSection(sections, SEC_ICON, "icon", iconData);
                packed.addElement(iconPath);
            }

            // ── 9. ALL REMAINING PROJECT FILES (catch-all) ────
            // Pack anything else in the project folder not already packed:
            // .2dlb, .2dls already handled above; also catches events, quests, etc.
            int rawCount = 0;
            for (int fi = 0; fi < allProjFiles.size(); fi++) {
                String path = (String) allProjFiles.elementAt(fi);
                if (alreadyPacked(packed, path)) continue;
                // Skip publish/ folder (avoid recursive packing)
                if (path.indexOf("/publish/") >= 0) continue;
                // Skip backup archives
                if (path.endsWith(".2dlebak")) continue;
                // Skip thumbnail cache
                if (path.endsWith(".thumb")) continue;
                byte[] data = fileMgr.readFile(path);
                if (data == null || data.length == 0) continue;
                String name = basename(path);
                addSection(sections, SEC_RAW, name, data);
                packed.addElement(path);
                rawCount++;
            }

            // ── 10. META ──────────────────────────────────────
            String meta = buildMeta(title, author, version, startLevel,
                                    levelCount, assetCount, rawCount);
            byte[] metaBytes;
            try { metaBytes = meta.getBytes("UTF-8"); }
            catch (Exception e) { metaBytes = meta.getBytes(); }
            addSection(sections, SEC_META, "meta", metaBytes);

            // ── Write sections ────────────────────────────────
            dos.writeInt(sections.size());
            for (int i = 0; i < sections.size(); i++) {
                Object[] sec = (Object[]) sections.elementAt(i);
                dos.writeUTF((String) sec[0]);
                dos.writeUTF((String) sec[1]);
                byte[] d = (byte[]) sec[2];
                dos.writeInt(d.length);
                dos.write(d);
            }

            dos.flush();
            byte[] result = baos.toByteArray();
            return new CompileResult(result, levelCount, assetCount, sections.size());

        } catch (IOException e) {
            return new CompileResult("IO error: " + e.getMessage());
        }
    }

    // =========================================================
    //  INTERNAL HELPERS
    // =========================================================
    private static void addSection(Vector sections, String type,
                                   String name, byte[] data) {
        sections.addElement(new Object[]{ type, name, data });
    }

    /** Returns true if this full path was already added to the packed list. */
    private static boolean alreadyPacked(Vector packed, String path) {
        for (int i = 0; i < packed.size(); i++) {
            if (path.equals(packed.elementAt(i))) return true;
        }
        return false;
    }

    /** Returns just the filename portion of a full path. */
    private static String basename(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String buildMeta(String title, String author,
                                    String version, String startLevel,
                                    int levelCount, int assetCount,
                                    int rawCount) {
        StringBuffer sb = new StringBuffer();
        sb.append("title=").append(title   != null ? title   : "").append('\n');
        sb.append("author=").append(author != null ? author  : "").append('\n');
        sb.append("version=").append(version!= null ? version : "1.0").append('\n');
        sb.append("startLevel=").append(startLevel).append('\n');
        sb.append("levelCount=").append(levelCount).append('\n');
        sb.append("assetCount=").append(assetCount).append('\n');
        sb.append("extraFiles=").append(rawCount).append('\n');
        sb.append("engine=2DLE v2.0\n");
        sb.append("format=2DIP\n");
        return sb.toString();
    }

    /**
     * Returns a list of all asset filenames packed in the sections Vector.
     */
    public static Vector listAssetsInPackage(Vector sections) {
        Vector names = new Vector();
        for (int i = 0; i < sections.size(); i++) {
            Object[] sec = (Object[]) sections.elementAt(i);
            if (SEC_ASSET.equals(sec[0])) {
                names.addElement(sec[1]);
            }
        }
        return names;
    }

    /**
     * Retrieves a named asset's bytes from a sections Vector.
     * Returns null if not found.
     */
    public static byte[] getAsset(Vector sections, String assetName) {
        return findSection(sections, SEC_ASSET, assetName);
    }

    // =========================================================
    //  SAVE .2DIP TO FILE
    // =========================================================
    /**
     * Compiles and saves a .2dip to the project's output folder.
     * Returns the full path written, or null on failure.
     */
    public static String compileAndSave(FileManager fileMgr,
                                        String projectName,
                                        String title,
                                        String author,
                                        String version,
                                        String startLevel) {
        CompileResult res = compile(fileMgr, projectName, title, author, version, startLevel);
        if (!res.success) return null;

        // Write to 2DLE/projectName/publish/<title>.2dip
        String pubDir = fileMgr.getProjectPath(projectName) + "publish/";
        fileMgr.createDirectory(pubDir);

        String safe = FileManager.sanitizeName(
            title != null && title.length() > 0 ? title : projectName);
        String outPath = pubDir + safe + ".2dip";

        if (fileMgr.writeFile(outPath, res.data)) {
            return outPath;
        }
        return null;
    }

    // =========================================================
    //  READ .2DIP SECTIONS (used by GamePlayer)
    // =========================================================
    /**
     * Parses a .2dip byte array and returns all sections.
     * Each element of the returned Vector is Object[]{type, name, data[]}.
     */
    public static Vector readSections(byte[] dip) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(dip);
        DataInputStream      dis  = new DataInputStream(bais);

        int magic = dis.readInt();
        if (magic != MAGIC) throw new IOException("Not a .2dip file");

        int ver = dis.readInt();
        dis.readInt(); // flags
        String title      = dis.readUTF();
        String author     = dis.readUTF();
        String version    = dis.readUTF();
        String startLevel = dis.readUTF();

        int count = dis.readInt();
        Vector sections = new Vector();

        for (int i = 0; i < count; i++) {
            String type   = dis.readUTF();
            String name   = dis.readUTF();
            int    len    = dis.readInt();
            byte[] data   = new byte[len];
            dis.readFully(data);
            sections.addElement(new Object[]{ type, name, data });
        }

        return sections;
    }

    /**
     * Reads header info from a .2dip without parsing all sections.
     * Returns String[]{title, author, version, startLevel} or null.
     */
    public static String[] readHeader(byte[] dip) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(dip);
            DataInputStream      dis  = new DataInputStream(bais);
            if (dis.readInt() != MAGIC) return null;
            dis.readInt(); dis.readInt(); // ver, flags
            String title      = dis.readUTF();
            String author     = dis.readUTF();
            String version    = dis.readUTF();
            String startLevel = dis.readUTF();
            return new String[]{ title, author, version, startLevel };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds a specific section's data bytes from a sections Vector.
     * Returns first match for (type, name) or null.
     */
    public static byte[] findSection(Vector sections, String type, String name) {
        for (int i = 0; i < sections.size(); i++) {
            Object[] sec = (Object[]) sections.elementAt(i);
            if (type.equals(sec[0]) && name.equals(sec[1])) {
                return (byte[]) sec[2];
            }
        }
        return null;
    }

    /**
     * Returns a list of all level names found in the sections Vector.
     */
    public static Vector listLevelsInPackage(Vector sections) {
        Vector names = new Vector();
        for (int i = 0; i < sections.size(); i++) {
            Object[] sec = (Object[]) sections.elementAt(i);
            if (SEC_LEVEL.equals(sec[0])) {
                names.addElement(sec[1]);
            }
        }
        return names;
    }

    // =========================================================
    //  HUMAN-READABLE INFO
    // =========================================================
    public static String describePackage(byte[] dip) {
        if (dip == null) return "Invalid package";
        String[] hdr = readHeader(dip);
        if (hdr == null) return "Invalid .2dip file";
        try {
            Vector secs   = readSections(dip);
            int levels    = listLevelsInPackage(secs).size();
            int assets    = listAssetsInPackage(secs).size();
            return hdr[0] + " v" + hdr[2] + " by " + hdr[1]
                 + "\nStart: " + hdr[3]
                 + "\nLevels: " + levels + "  Assets: " + assets
                 + "\nSize: " + FileManager.formatBytes(dip.length);
        } catch (Exception e) {
            return hdr[0] + " v" + hdr[2] + " by " + hdr[1];
        }
    }
}
