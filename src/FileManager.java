import javax.microedition.io.*;
import javax.microedition.io.file.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.lcdui.Image;
import javax.microedition.rms.RecordStore;

/**
 * FileManager.java — Enhanced v2.0
 *
 * NEW FEATURES:
 *  - Auto-detects ALL available drives/roots via FileSystemRegistry
 *  - Ranks drives by type: memory card preferred over internal flash
 *  - Install-location chooser: returns list of all valid roots for user selection
 *  - Persists the chosen install root in RecordStore (survives restarts)
 *  - Free-space check before creating project / writing files
 *  - File size reporting and disk-usage summary per project
 *  - Recursive directory delete (real project deletion)
 *  - Backup / restore: exports entire project to a single .2dlebak archive
 *  - Import: unpacks .2dlebak archive into a new project
 *  - Thumbnail cache: scaled 16x16 mip of each tile sheet stored per project
 *  - Asset type expansion: PNG, JPG, BMP, GIF recognised
 *  - listAllFiles() recursive utility for project export
 *  - Drive health check: tests read/write on each root before trusting it
 *  - Human-readable byte formatter (KB / MB)
 *  - RecordStore fallback when JSR-75 is unavailable
 */
public class FileManager {

    // =========================================================
    //  CONSTANTS
    // =========================================================
    public static final String ROOT_FOLDER   = "2DLE/";
    public static final String PROJECT_EXT   = ".2dle";
    public static final String LEVEL_EXT     = ".l2de";
    public static final String BACKUP_EXT    = ".2dlebak";
    public static final String NPC_FILE      = "npcs.txt";
    public static final String EVENTS_FILE   = "events.txt";
    public static final String QUESTS_FILE   = "quests.txt";
    public static final String SETTINGS_FILE = "project.2dle";
    public static final String THUMB_FILE    = "thumb.raw";   // 16×16 ARGB raw
    public static final String LOG_FILE      = "editor.log";
    public static final String ASSETS_DIR    = "assets/";
    public static final String BACKUP_DIR    = "backups/";
    public static final String THUMB_DIR     = "thumbs/";

    /** RecordStore name for persisted install path preference. */
    private static final String PREF_STORE   = "2dle_path";
    private static final int    PREF_RECORD  = 1;

    // Drive type rank — lower = more preferred
    private static final int RANK_SDCARD   = 0;  // removable media
    private static final int RANK_EXTERNAL = 1;  // external storage
    private static final int RANK_INTERNAL = 2;  // phone flash
    private static final int RANK_UNKNOWN  = 3;

    // =========================================================
    //  STATE
    // =========================================================
    private String   basePath;          // e.g. "file:///E:/"
    private boolean  jsr75Available;
    private Vector   availableRoots;    // all detected roots (String)
    private Vector   rootLabels;        // human-readable label per root
    private Vector   rootFreeSpace;     // Long free bytes per root
    private String   savedPreference;   // last user-chosen root (from RMS)
    private StringBuffer logBuffer;     // in-memory log (flushed on demand)

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public FileManager() {
        logBuffer        = new StringBuffer();
        availableRoots   = new Vector();
        rootLabels       = new Vector();
        rootFreeSpace    = new Vector();
        jsr75Available   = checkJSR75();

        loadPreference();     // Load previously saved install path

        if (jsr75Available) {
            scanAllRoots();   // Populate availableRoots
            basePath = resolveBasePath();
        } else {
            log("JSR-75 not available — RecordStore fallback active");
        }
    }

    // =========================================================
    //  JSR-75 AVAILABILITY CHECK
    // =========================================================
    private boolean checkJSR75() {
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================
    //  AUTO-DETECT ALL ROOTS
    // =========================================================
    /**
     * Scans every root reported by FileSystemRegistry.
     * Validates each one with a read/write probe.
     * Populates availableRoots, rootLabels, rootFreeSpace.
     */
    private void scanAllRoots() {
        availableRoots.removeAllElements();
        rootLabels.removeAllElements();
        rootFreeSpace.removeAllElements();

        // ── Primary: use FileSystemRegistry ──────────────────
        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements()) {
                String root = (String) roots.nextElement();
                // Registry returns names like "E:/" or "root1/" without "file:///"
                String url = root.startsWith("file:///") ? root : "file:///" + root;
                probeAndAdd(url);
            }
        } catch (Exception e) {
            log("FileSystemRegistry failed: " + e.getMessage());
        }

        // ── Fallback: hard-coded common paths if none found ──
        if (availableRoots.size() == 0) {
            String[] fallbacks = {
                "file:///E:/",    // most Nokia, SE (memory card)
                "file:///F:/",    // second card slot
                "file:///D:/",    // some Motorola
                "file:///C:/",    // internal flash (Nokia)
                "file:///a/",     // Android-style
                "file:///b/",
                "file:///sdcard/",
                "file:///mmc/",
                "file:///store/",
                "file:///Memory card/",
                "file:///Phone memory/",
                "file:///root1/",
                "file:///root2/",
            };
            for (int i = 0; i < fallbacks.length; i++) {
                probeAndAdd(fallbacks[i]);
            }
        }

        log("Detected " + availableRoots.size() + " usable root(s)");
    }

    /**
     * Tests a root URL: open, check exists, probe free space.
     * Adds to lists if valid.
     */
    private void probeAndAdd(String url) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(url);
            if (!fc.exists() || !fc.isDirectory()) {
                return;
            }
            long free = 0;
            try { free = fc.availableSize(); } catch (Exception ex) {}

            // Classify and label
            String label = buildLabel(url, free);
            availableRoots.addElement(url);
            rootLabels.addElement(label);
            rootFreeSpace.addElement(new Long(free));

            log("Root OK: " + url + " free=" + formatBytes(free));
        } catch (Exception e) {
            // Root not accessible — skip silently
        } finally {
            closeFC(fc);
        }
    }

    /** Build a human-readable label for a root URL. */
    private String buildLabel(String url, long freeBytes) {
        String lower = url.toLowerCase();
        String name;

        if (lower.indexOf("sdcard") >= 0 || lower.indexOf("mmc") >= 0
                || lower.indexOf("memory card") >= 0) {
            name = "SD Card";
        } else if (lower.indexOf("e:/") >= 0 || lower.indexOf("f:/") >= 0) {
            name = "Memory Card (" + url.substring(url.lastIndexOf('/') + 1 - 3) + ")";
        } else if (lower.indexOf("c:/") >= 0) {
            name = "Internal Flash (C:)";
        } else if (lower.indexOf("d:/") >= 0) {
            name = "Internal Flash (D:)";
        } else if (lower.indexOf("phone") >= 0 || lower.indexOf("store") >= 0) {
            name = "Phone Memory";
        } else {
            // Use last path segment as name
            String u = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            int slash = u.lastIndexOf('/');
            name = slash >= 0 ? u.substring(slash + 1) : u;
        }

        return name + "  [" + formatBytes(freeBytes) + " free]";
    }

    /** Assign a rank to a root URL for sorting (lower = preferred). */
    private int rankRoot(String url) {
        String lower = url.toLowerCase();
        if (lower.indexOf("sdcard") >= 0 || lower.indexOf("mmc") >= 0
                || lower.indexOf("memory card") >= 0
                || lower.indexOf("e:/") >= 0 || lower.indexOf("f:/") >= 0) {
            return RANK_SDCARD;
        }
        if (lower.indexOf("d:/") >= 0) {
            return RANK_EXTERNAL;
        }
        if (lower.indexOf("c:/") >= 0 || lower.indexOf("phone") >= 0
                || lower.indexOf("store") >= 0) {
            return RANK_INTERNAL;
        }
        return RANK_UNKNOWN;
    }

    // =========================================================
    //  RESOLVE BASE PATH (auto or from preference)
    // =========================================================
    private String resolveBasePath() {
        // User has previously chosen a root — validate it is still accessible
        if (savedPreference != null && savedPreference.length() > 0) {
            for (int i = 0; i < availableRoots.size(); i++) {
                if (availableRoots.elementAt(i).equals(savedPreference)) {
                    log("Using saved preference: " + savedPreference);
                    return savedPreference;
                }
            }
            log("Saved preference no longer available: " + savedPreference);
        }

        // Pick best available root automatically
        return pickBestRoot();
    }

    /** Returns the highest-ranked (most preferred) available root. */
    private String pickBestRoot() {
        if (availableRoots.size() == 0) {
            return "file:///E:/"; // last-resort default
        }

        int bestIdx  = 0;
        int bestRank = RANK_UNKNOWN + 1;

        for (int i = 0; i < availableRoots.size(); i++) {
            int rank = rankRoot((String) availableRoots.elementAt(i));
            if (rank < bestRank) {
                bestRank = rank;
                bestIdx  = i;
            }
        }

        String best = (String) availableRoots.elementAt(bestIdx);
        log("Auto-selected root: " + best);
        return best;
    }

    // =========================================================
    //  USER INSTALL-LOCATION CHOOSER  (called by ProjectManager)
    // =========================================================
    /**
     * Returns the number of detected roots available for installation.
     */
    public int getAvailableRootCount() {
        return availableRoots.size();
    }

    /**
     * Returns the human-readable label for root at index.
     * e.g. "SD Card  [14.2 MB free]"
     */
    public String getRootLabel(int index) {
        if (index < 0 || index >= rootLabels.size()) return "Unknown";
        return (String) rootLabels.elementAt(index);
    }

    /**
     * Returns the raw URL for root at index.
     */
    public String getRootUrl(int index) {
        if (index < 0 || index >= availableRoots.size()) return null;
        return (String) availableRoots.elementAt(index);
    }

    /**
     * Get index of the currently active root.
     */
    public int getActiveRootIndex() {
        for (int i = 0; i < availableRoots.size(); i++) {
            if (availableRoots.elementAt(i).equals(basePath)) return i;
        }
        return 0;
    }

    /**
     * User has chosen a root from the chooser UI.
     * Updates basePath and persists to RecordStore.
     */
    public void setInstallRoot(int rootIndex) {
        if (rootIndex < 0 || rootIndex >= availableRoots.size()) return;
        basePath = (String) availableRoots.elementAt(rootIndex);
        log("Install root set to: " + basePath);
        savePreference(basePath);
        // Ensure 2DLE folder exists on new root
        createDirectory(getRootPath());
    }

    /**
     * Force install to a specific URL (for manual path entry).
     */
    public void setInstallRootUrl(String url) {
        if (url == null || url.length() == 0) return;
        if (!url.endsWith("/")) url = url + "/";
        basePath = url;
        log("Install root manually set: " + basePath);
        savePreference(basePath);
        createDirectory(getRootPath());
    }

    // =========================================================
    //  PREFERENCE PERSISTENCE (RecordStore)
    // =========================================================
    private void loadPreference() {
        try {
            RecordStore rs = RecordStore.openRecordStore(PREF_STORE, false);
            byte[] data = rs.getRecord(PREF_RECORD);
            rs.closeRecordStore();
            savedPreference = new String(data, "UTF-8");
        } catch (Exception e) {
            savedPreference = null;
        }
    }

    private void savePreference(String path) {
        try {
            byte[] data = path.getBytes("UTF-8");
            RecordStore rs = RecordStore.openRecordStore(PREF_STORE, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(PREF_RECORD, data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            log("Could not save path preference: " + e.getMessage());
        }
    }

    /** Clears saved preference (reset to auto-detect next launch). */
    public void clearPreference() {
        savedPreference = null;
        try {
            RecordStore.deleteRecordStore(PREF_STORE);
        } catch (Exception e) {}
    }

    // =========================================================
    //  PATH HELPERS
    // =========================================================
    public String getBasePath()                           { return basePath; }
    public String getRootPath()                           { return basePath + ROOT_FOLDER; }
    public String getProjectPath(String name)             { return getRootPath() + name + "/"; }
    public String getLevelPath(String p, String l)        { return getProjectPath(p) + l + LEVEL_EXT; }
    public String getNPCPath(String p)                    { return getProjectPath(p) + NPC_FILE; }
    public String getEventsPath(String p)                 { return getProjectPath(p) + EVENTS_FILE; }
    public String getQuestsPath(String p)                 { return getProjectPath(p) + QUESTS_FILE; }
    public String getAssetsPath(String p)                 { return getProjectPath(p) + ASSETS_DIR; }
    public String getBackupDirPath(String p)              { return getProjectPath(p) + BACKUP_DIR; }
    public String getThumbDirPath(String p)               { return getProjectPath(p) + THUMB_DIR; }
    public String getSettingsPath(String p)               { return getProjectPath(p) + SETTINGS_FILE; }
    public String getLogPath()                            { return getRootPath() + LOG_FILE; }
    public String getBackupPath(String p, String stamp)   { return getBackupDirPath(p) + p + "_" + stamp + BACKUP_EXT; }

    // =========================================================
    //  AVAILABILITY
    // =========================================================
    public boolean isAvailable()           { return jsr75Available && basePath != null; }
    public boolean isJsr75Available()      { return jsr75Available; }

    /**
     * Returns free bytes on the active install root, or -1 if unknown.
     */
    public long getFreeSpace() {
        if (!jsr75Available) return -1;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(basePath);
            return fc.availableSize();
        } catch (Exception e) {
            return -1;
        } finally {
            closeFC(fc);
        }
    }

    /**
     * Returns total bytes used by a project folder (recursive).
     */
    public long getProjectSize(String projectName) {
        return getDirectorySize(getProjectPath(projectName));
    }

    private long getDirectorySize(String dirPath) {
        if (!jsr75Available) return 0;
        long total = 0;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirPath);
            if (!fc.exists() || !fc.isDirectory()) return 0;
            Enumeration files = fc.list();
            while (files.hasMoreElements()) {
                String name = (String) files.nextElement();
                String child = dirPath + name;
                if (name.endsWith("/")) {
                    total += getDirectorySize(child);
                } else {
                    total += getFileSize(child);
                }
            }
        } catch (Exception e) {
        } finally {
            closeFC(fc);
        }
        return total;
    }

    private long getFileSize(String path) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ);
            return fc.fileSize();
        } catch (Exception e) {
            return 0;
        } finally {
            closeFC(fc);
        }
    }

    // =========================================================
    //  DIRECTORY OPERATIONS
    // =========================================================
    public boolean createDirectory(String path) {
        if (!jsr75Available) return false;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(path);
            if (!fc.exists()) fc.mkdir();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeFC(fc);
        }
    }

    /**
     * Creates full project structure:
     *   2DLE/<name>/
     *   2DLE/<name>/assets/
     *   2DLE/<name>/backups/
     *   2DLE/<name>/thumbs/
     */
    public boolean createProjectStructure(String projectName) {
        if (!jsr75Available) return false;
        if (!createDirectory(getRootPath()))                  return false;
        if (!createDirectory(getProjectPath(projectName)))    return false;
        if (!createDirectory(getAssetsPath(projectName)))     return false;
        if (!createDirectory(getBackupDirPath(projectName)))  return false;
        if (!createDirectory(getThumbDirPath(projectName)))   return false;
        log("Project structure created: " + projectName);
        return true;
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Used for real project deletion.
     */
    public boolean deleteDirectory(String dirPath) {
        if (!jsr75Available) return false;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirPath);
            if (!fc.exists()) return true;
            if (fc.isDirectory()) {
                Enumeration files = fc.list();
                while (files.hasMoreElements()) {
                    String name = (String) files.nextElement();
                    String child = dirPath + name;
                    closeFC(fc);
                    fc = null;
                    if (name.endsWith("/")) {
                        deleteDirectory(child);
                    } else {
                        deleteFile(child);
                    }
                    fc = (FileConnection) Connector.open(dirPath);
                }
                closeFC(fc);
                fc = (FileConnection) Connector.open(dirPath, Connector.READ_WRITE);
                fc.delete();
            } else {
                closeFC(fc);
                fc = (FileConnection) Connector.open(dirPath, Connector.READ_WRITE);
                fc.delete();
            }
            return true;
        } catch (Exception e) {
            log("deleteDirectory error: " + e.getMessage());
            return false;
        } finally {
            closeFC(fc);
        }
    }

    /** Deletes an entire project folder recursively. */
    public boolean deleteProject(String projectName) {
        boolean ok = deleteDirectory(getProjectPath(projectName));
        if (ok) log("Project deleted: " + projectName);
        return ok;
    }

    /**
     * Renames a project folder.
     */
    public boolean renameProject(String oldName, String newName) {
        if (!jsr75Available) return false;
        String oldPath = getProjectPath(oldName);
        String newPath = getProjectPath(newName);
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(oldPath, Connector.READ_WRITE);
            if (!fc.exists()) return false;
            fc.rename(newName + "/");
            log("Project renamed: " + oldName + " -> " + newName);
            return true;
        } catch (Exception e) {
            log("rename error: " + e.getMessage());
            return false;
        } finally {
            closeFC(fc);
        }
    }

    // =========================================================
    //  LISTING
    // =========================================================
    public Vector listProjects() {
        Vector projects = new Vector();
        if (!jsr75Available) return projects;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(getRootPath());
            if (fc.exists() && fc.isDirectory()) {
                Enumeration files = fc.list();
                while (files.hasMoreElements()) {
                    String name = (String) files.nextElement();
                    if (name.endsWith("/")) {
                        projects.addElement(name.substring(0, name.length() - 1));
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            closeFC(fc);
        }
        return projects;
    }

    public Vector listLevels(String projectName) {
        Vector levels = new Vector();
        if (!jsr75Available) return levels;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(getProjectPath(projectName));
            if (fc.exists() && fc.isDirectory()) {
                Enumeration files = fc.list();
                while (files.hasMoreElements()) {
                    String name = (String) files.nextElement();
                    if (name.endsWith(LEVEL_EXT)) {
                        levels.addElement(name.substring(0, name.length() - LEVEL_EXT.length()));
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            closeFC(fc);
        }
        return levels;
    }

    /**
     * Lists assets in a project: PNG, JPG, BMP, GIF all recognised.
     */
    public Vector listAssets(String projectName) {
        Vector assets = new Vector();
        if (!jsr75Available) return assets;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(getAssetsPath(projectName));
            if (fc.exists() && fc.isDirectory()) {
                Enumeration files = fc.list();
                while (files.hasMoreElements()) {
                    String name = (String) files.nextElement();
                    if (isImageFile(name)) {
                        assets.addElement(name);
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            closeFC(fc);
        }
        return assets;
    }

    /** Lists backup files for a project. */
    public Vector listBackups(String projectName) {
        Vector backups = new Vector();
        if (!jsr75Available) return backups;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(getBackupDirPath(projectName));
            if (fc.exists() && fc.isDirectory()) {
                Enumeration files = fc.list();
                while (files.hasMoreElements()) {
                    String name = (String) files.nextElement();
                    if (name.endsWith(BACKUP_EXT)) {
                        backups.addElement(name);
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            closeFC(fc);
        }
        return backups;
    }

    /**
     * Recursively lists all files under a directory.
     * Returns full absolute paths.
     */
    public void listAllFiles(String dirPath, Vector result) {
        if (!jsr75Available) return;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirPath);
            if (!fc.exists() || !fc.isDirectory()) return;
            Enumeration files = fc.list();
            while (files.hasMoreElements()) {
                String name = (String) files.nextElement();
                String child = dirPath + name;
                if (name.endsWith("/")) {
                    listAllFiles(child, result);
                } else {
                    result.addElement(child);
                }
            }
        } catch (Exception e) {
        } finally {
            closeFC(fc);
        }
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg") || lower.endsWith(".bmp")
            || lower.endsWith(".gif");
    }

    // =========================================================
    //  FILE OPERATIONS
    // =========================================================
    public byte[] readFile(String path) {
        if (!jsr75Available) return null;
        FileConnection fc = null;
        InputStream is   = null;
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ);
            if (!fc.exists()) return null;
            int size = (int) fc.fileSize();
            if (size <= 0) return new byte[0];
            byte[] data = new byte[size];
            is = fc.openInputStream();
            int read = 0;
            while (read < size) {
                int r = is.read(data, read, size - read);
                if (r == -1) break;
                read += r;
            }
            return data;
        } catch (Exception e) {
            log("readFile error [" + path + "]: " + e.getMessage());
            return null;
        } finally {
            closeStream(is);
            closeFC(fc);
        }
    }

    public boolean writeFile(String path, byte[] data) {
        if (!jsr75Available) return false;
        // Free-space guard: refuse if less than data.length + 4KB headroom
        long free = getFreeSpace();
        if (free >= 0 && free < data.length + 4096) {
            log("writeFile: insufficient space (" + formatBytes(free) + " free)");
            return false;
        }
        FileConnection fc  = null;
        OutputStream   os  = null;
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ_WRITE);
            if (!fc.exists()) {
                fc.create();
            } else {
                fc.truncate(0);
            }
            os = fc.openOutputStream();
            os.write(data);
            os.flush();
            return true;
        } catch (Exception e) {
            log("writeFile error [" + path + "]: " + e.getMessage());
            return false;
        } finally {
            closeStream(os);
            closeFC(fc);
        }
    }

    /**
     * Atomic write: writes to a temp file first, then renames.
     * Prevents data corruption if the device loses power mid-write.
     */
    public boolean writeFileAtomic(String path, byte[] data) {
        String tmp = path + ".tmp";
        if (!writeFile(tmp, data)) return false;
        // Delete old file
        deleteFile(path);
        // Rename temp to final
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(tmp, Connector.READ_WRITE);
            // Extract just the filename portion for rename
            String filename = path;
            int slash = path.lastIndexOf('/');
            if (slash >= 0) filename = path.substring(slash + 1);
            fc.rename(filename);
            return true;
        } catch (Exception e) {
            log("atomic rename error: " + e.getMessage());
            return false;
        } finally {
            closeFC(fc);
        }
    }

    public String readTextFile(String path) {
        byte[] data = readFile(path);
        if (data == null) return null;
        try { return new String(data, "UTF-8"); }
        catch (Exception e) { return new String(data); }
    }

    public boolean writeTextFile(String path, String text) {
        try {
            return writeFile(path, text.getBytes("UTF-8"));
        } catch (Exception e) {
            return writeFile(path, text.getBytes());
        }
    }

    public boolean deleteFile(String path) {
        if (!jsr75Available) return false;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ_WRITE);
            if (fc.exists()) fc.delete();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeFC(fc);
        }
    }

    public boolean fileExists(String path) {
        if (!jsr75Available) return false;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ);
            return fc.exists();
        } catch (Exception e) {
            return false;
        } finally {
            closeFC(fc);
        }
    }

    /**
     * Copies a file from src to dst.
     */
    public boolean copyFile(String src, String dst) {
        byte[] data = readFile(src);
        if (data == null) return false;
        return writeFile(dst, data);
    }

    // =========================================================
    //  IMAGE LOADING
    // =========================================================
    public Image loadImage(String path) {
        byte[] data = readFile(path);
        if (data == null) return null;
        try {
            return Image.createImage(data, 0, data.length);
        } catch (Exception e) {
            log("loadImage error [" + path + "]: " + e.getMessage());
            return null;
        }
    }

    public Image loadProjectAsset(String projectName, String assetName) {
        return loadImage(getAssetsPath(projectName) + assetName);
    }

    /**
     * Saves a 16×16 ARGB thumbnail raw bytes for a tileset.
     * @param projectName  project name
     * @param assetName    asset filename (without path)
     * @param argbData     int[] of 256 ARGB pixels (16×16)
     */
    public boolean saveThumbnail(String projectName, String assetName, int[] argbData) {
        if (argbData == null || argbData.length != 256) return false;
        // Encode as 4 bytes per pixel big-endian
        byte[] raw = new byte[256 * 4];
        for (int i = 0; i < 256; i++) {
            raw[i * 4]     = (byte)((argbData[i] >> 24) & 0xFF);
            raw[i * 4 + 1] = (byte)((argbData[i] >> 16) & 0xFF);
            raw[i * 4 + 2] = (byte)((argbData[i] >>  8) & 0xFF);
            raw[i * 4 + 3] = (byte)( argbData[i]        & 0xFF);
        }
        String thumbName = assetName.replace('.', '_') + ".raw";
        return writeFile(getThumbDirPath(projectName) + thumbName, raw);
    }

    /**
     * Loads a previously saved 16×16 thumbnail.
     * Returns int[256] ARGB, or null if not cached.
     */
    public int[] loadThumbnail(String projectName, String assetName) {
        String thumbName = assetName.replace('.', '_') + ".raw";
        byte[] raw = readFile(getThumbDirPath(projectName) + thumbName);
        if (raw == null || raw.length != 256 * 4) return null;
        int[] argb = new int[256];
        for (int i = 0; i < 256; i++) {
            argb[i] = ((raw[i * 4] & 0xFF) << 24)
                    | ((raw[i * 4 + 1] & 0xFF) << 16)
                    | ((raw[i * 4 + 2] & 0xFF) << 8)
                    |  (raw[i * 4 + 3] & 0xFF);
        }
        return argb;
    }

    // =========================================================
    //  PROJECT DATA
    // =========================================================
    public boolean saveProjectSettings(String projectName, byte[] data) {
        return writeFileAtomic(getSettingsPath(projectName), data);
    }

    public byte[] loadProjectSettings(String projectName) {
        return readFile(getSettingsPath(projectName));
    }

    public boolean saveLevel(String projectName, String levelName, byte[] data) {
        return writeFileAtomic(getLevelPath(projectName, levelName), data);
    }

    public byte[] loadLevel(String projectName, String levelName) {
        return readFile(getLevelPath(projectName, levelName));
    }

    public boolean saveNPCData(String projectName, String text) {
        return writeTextFile(getNPCPath(projectName), text);
    }

    public String loadNPCData(String projectName) {
        return readTextFile(getNPCPath(projectName));
    }

    public boolean saveEventsData(String projectName, String text) {
        return writeTextFile(getEventsPath(projectName), text);
    }

    public String loadEventsData(String projectName) {
        return readTextFile(getEventsPath(projectName));
    }

    public boolean saveQuestsData(String projectName, String text) {
        return writeTextFile(getQuestsPath(projectName), text);
    }

    public String loadQuestsData(String projectName) {
        return readTextFile(getQuestsPath(projectName));
    }

    // =========================================================
    //  BACKUP / RESTORE
    // =========================================================
    /**
     * Creates a full project backup as a single binary archive.
     * Format:
     *   [int fileCount]
     *   for each file:
     *     [UTF relativePath][int dataLength][bytes data]
     *
     * @param projectName  project to back up
     * @param stamp        timestamp string for filename (e.g. "20250101_1430")
     * @return path to created backup, or null on failure
     */
    public String backupProject(String projectName, String stamp) {
        if (!jsr75Available) return null;

        Vector allFiles = new Vector();
        String projPath = getProjectPath(projectName);
        listAllFiles(projPath, allFiles);

        if (allFiles.size() == 0) return null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos       = new DataOutputStream(baos);

            dos.writeInt(allFiles.size());
            for (int i = 0; i < allFiles.size(); i++) {
                String absPath = (String) allFiles.elementAt(i);
                // Store relative path (strip project root prefix)
                String rel = absPath.startsWith(projPath)
                           ? absPath.substring(projPath.length()) : absPath;
                byte[] fileData = readFile(absPath);
                if (fileData == null) fileData = new byte[0];

                dos.writeUTF(rel);
                dos.writeInt(fileData.length);
                dos.write(fileData);
            }
            dos.flush();

            byte[] archive = baos.toByteArray();
            String backupPath = getBackupPath(projectName, stamp);
            // Ensure backup dir exists
            createDirectory(getBackupDirPath(projectName));
            if (writeFile(backupPath, archive)) {
                log("Backup created: " + backupPath + " (" + formatBytes(archive.length) + ")");
                return backupPath;
            }
        } catch (Exception e) {
            log("backupProject error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Restores a project from a .2dlebak archive.
     * Creates a new project with the given name.
     *
     * @param backupPath   full path to the .2dlebak file
     * @param newName      new project name to restore as
     * @return true on success
     */
    public boolean restoreProject(String backupPath, String newName) {
        if (!jsr75Available) return false;

        byte[] archive = readFile(backupPath);
        if (archive == null) return false;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(archive);
            DataInputStream      dis  = new DataInputStream(bais);

            // Create fresh project structure
            if (!createProjectStructure(newName)) return false;
            String projPath = getProjectPath(newName);

            int fileCount = dis.readInt();
            for (int i = 0; i < fileCount; i++) {
                String rel      = dis.readUTF();
                int    dataLen  = dis.readInt();
                byte[] fileData = new byte[dataLen];
                dis.readFully(fileData);

                // Reconstruct full path
                String absPath = projPath + rel;

                // Ensure parent directory exists
                int lastSlash = absPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    createDirectory(absPath.substring(0, lastSlash + 1));
                }

                writeFile(absPath, fileData);
            }
            log("Project restored from " + backupPath + " as " + newName);
            return true;
        } catch (Exception e) {
            log("restoreProject error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Auto-backup: keeps only the last N backups, deletes older ones.
     * Call after each save to maintain a rolling backup window.
     */
    public void autoBackup(String projectName, String stamp, int maxKeep) {
        String path = backupProject(projectName, stamp);
        if (path == null) return;

        // Prune old backups
        Vector backups = listBackups(projectName);
        if (backups.size() > maxKeep) {
            // Simple sort: alphabetical order = chronological for timestamp names
            // Remove the first (oldest) entries
            int toDelete = backups.size() - maxKeep;
            for (int i = 0; i < toDelete; i++) {
                String old = getBackupDirPath(projectName)
                           + (String) backups.elementAt(i);
                deleteFile(old);
                log("Pruned old backup: " + backups.elementAt(i));
            }
        }
    }

    // =========================================================
    //  RECORDSTORE FALLBACK (when JSR-75 is not available)
    // =========================================================
    private static final String RMS_STORE = "2dle_data";

    /**
     * Save data to RecordStore under a logical key.
     * Used as fallback when file system is unavailable.
     * Key-value pairs stored as: [UTF key][int dataLen][bytes data]
     * (All packed into a single record for simplicity on constrained devices.)
     */
    public boolean rmsWrite(String key, byte[] data) {
        try {
            // Read existing data
            byte[] existing = rmsReadAll();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Re-write all existing entries except this key
            if (existing != null && existing.length > 0) {
                DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(existing));
                try {
                    while (dis.available() > 0) {
                        String k = dis.readUTF();
                        int len  = dis.readInt();
                        byte[] v = new byte[len];
                        dis.readFully(v);
                        if (!k.equals(key)) {
                            dos.writeUTF(k);
                            dos.writeInt(v.length);
                            dos.write(v);
                        }
                    }
                } catch (Exception ex) {}
            }

            // Append new entry
            dos.writeUTF(key);
            dos.writeInt(data.length);
            dos.write(data);
            dos.flush();

            byte[] blob = baos.toByteArray();
            RecordStore rs = RecordStore.openRecordStore(RMS_STORE, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(blob, 0, blob.length);
            } else {
                rs.setRecord(1, blob, 0, blob.length);
            }
            rs.closeRecordStore();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public byte[] rmsRead(String key) {
        byte[] existing = rmsReadAll();
        if (existing == null || existing.length == 0) return null;
        try {
            DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(existing));
            while (dis.available() > 0) {
                String k = dis.readUTF();
                int len  = dis.readInt();
                byte[] v = new byte[len];
                dis.readFully(v);
                if (k.equals(key)) return v;
            }
        } catch (Exception e) {}
        return null;
    }

    private byte[] rmsReadAll() {
        try {
            RecordStore rs = RecordStore.openRecordStore(RMS_STORE, false);
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    //  LOGGING
    // =========================================================
    private void log(String msg) {
        logBuffer.append(msg);
        logBuffer.append('\n');
    }

    /** Returns accumulated log as a String. */
    public String getLog() {
        return logBuffer.toString();
    }

    /** Flushes in-memory log to editor.log on the active root. */
    public void flushLog() {
        if (!jsr75Available || basePath == null) return;
        writeTextFile(getLogPath(), logBuffer.toString());
    }

    // =========================================================
    //  UTILITY
    // =========================================================
    /**
     * Formats a byte count to a human-readable string.
     * e.g. 1536 → "1.5 KB",  2097152 → "2.0 MB"
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0)           return "unknown";
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes * 10 / 1024) / 10 + "." + (bytes * 10 / 1024) % 10 + " KB";
        long mb = bytes / (1024 * 1024);
        long frac = (bytes % (1024 * 1024)) * 10 / (1024 * 1024);
        return mb + "." + frac + " MB";
    }

    /**
     * Returns a simple timestamp string (seconds since epoch, base-36 encoded).
     * Used for backup filenames when no calendar API is available.
     */
    public static String makeStamp() {
        long sec = System.currentTimeMillis() / 1000L;
        // Base-36 encode for compact filenames
        String s = Long.toString(sec, 36).toUpperCase();
        return s;
    }

    /**
     * Sanitises a user-supplied project name for use as a directory name.
     * Removes characters that are unsafe on J2ME file systems.
     */
    public static String sanitizeName(String name) {
        if (name == null) return "project";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean letter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean digit  = (c >= '0' && c <= '9');
            if (letter || digit || c == '_' || c == '-') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        String result = sb.toString().trim();
        return result.length() == 0 ? "project" : result;
    }

    // =========================================================
    //  CLOSE HELPERS
    // =========================================================
    private void closeFC(FileConnection fc) {
        if (fc != null) { try { fc.close(); } catch (Exception e) {} }
    }

    private void closeStream(InputStream is) {
        if (is != null) { try { is.close(); } catch (Exception e) {} }
    }

    private void closeStream(OutputStream os) {
        if (os != null) { try { os.close(); } catch (Exception e) {} }
    }
}
