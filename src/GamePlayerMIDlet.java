import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;
import java.util.Vector;

/**
 * GamePlayerMIDlet.java  v1.0
 *
 * Standalone MIDlet for playing .2dip game packages.
 *
 * USAGE MODES:
 *   1. BUNDLED: The .2dip file is packaged as a JAR resource "/game.2dip"
 *      → deployed as a self-contained game (players only need this JAR)
 *
 *   2. FILE PICKER: No bundled resource → shows a list of .2dip files
 *      found on the device storage via FileManager, lets the user pick one
 *
 *   3. FROM EDITOR: The editor's "Play" button passes the compiled .2dip
 *      bytes directly to this MIDlet via getInstance().launchGame(bytes)
 *
 * DEPLOYMENT:
 *   For publishing a game:
 *     1. In the editor: Menu → Compile → Export .2dip
 *     2. Copy GamePlayerMIDlet.jar to the device
 *     3. Copy the .2dip file to the device storage
 *     4. Launch the player — it finds and loads the .2dip automatically
 *
 *   For a fully self-contained game:
 *     1. Place the .2dip as a JAR resource named "game.2dip"
 *     2. Re-package the JAR — the player will auto-load it on startup
 */
public class GamePlayerMIDlet extends MIDlet implements CommandListener {

    private static GamePlayerMIDlet instance;
    private Display  display;
    private GamePlayer gamePlayer;

    // ── Package picker screen ─────────────────────────────
    private List    pickerList;
    private Vector  foundPackages;   // full paths to .2dip files
    private Command cmdPlay;
    private Command cmdRefresh;
    private Command cmdExit;
    private Command cmdBack;

    // =========================================================
    //  MIDLET LIFECYCLE
    // =========================================================
    public GamePlayerMIDlet() {
        instance = this;
    }

    protected void startApp() {
        display = Display.getDisplay(this);

        cmdPlay    = new Command("Play",    Command.OK,     1);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 2);
        cmdExit    = new Command("Exit",    Command.BACK,   1);
        cmdBack    = new Command("Back",    Command.BACK,   1);

        // Mode 1: try bundled resource
        byte[] bundled = loadBundledPackage();
        if (bundled != null) {
            launchGame(bundled);
            return;
        }

        // Mode 2: show file picker
        showPicker();
    }

    protected void pauseApp() {
        if (gamePlayer != null) gamePlayer.stop();
    }

    protected void destroyApp(boolean unconditional) {
        if (gamePlayer != null) gamePlayer.stop();
    }

    // =========================================================
    //  BUNDLED PACKAGE LOADER
    // =========================================================
    private byte[] loadBundledPackage() {
        try {
            InputStream is = getClass().getResourceAsStream("/game.2dip");
            if (is == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();
            byte[] data = baos.toByteArray();
            if (GameCompiler.readHeader(data) == null) return null;
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    //  FILE PICKER
    // =========================================================
    private void showPicker() {
        pickerList = new List("Select Game", List.IMPLICIT);
        pickerList.addCommand(cmdPlay);
        pickerList.addCommand(cmdRefresh);
        pickerList.addCommand(cmdExit);
        pickerList.setCommandListener(this);
        display.setCurrent(pickerList);
        scanForPackages();
    }

    private void scanForPackages() {
        while (pickerList.size() > 0) pickerList.delete(0);
        foundPackages = new Vector();

        FileManager fm = new FileManager();
        if (!fm.isAvailable()) {
            pickerList.append("[No file system]", null);
            pickerList.append("Bundle a game.2dip in the JAR", null);
            return;
        }

        // Scan all roots and the 2DLE project folders for .2dip files
        for (int ri = 0; ri < fm.getAvailableRootCount(); ri++) {
            String root = fm.getRootUrl(ri);
            if (root == null) continue;
            // Check 2DLE/<project>/publish/*.2dip
            Vector projects = fm.listProjects();
            for (int pi = 0; pi < projects.size(); pi++) {
                String proj = (String) projects.elementAt(pi);
                String pubDir = fm.getProjectPath(proj) + "publish/";
                Vector allFiles = new Vector();
                fm.listAllFiles(pubDir, allFiles);
                for (int fi = 0; fi < allFiles.size(); fi++) {
                    String path = (String) allFiles.elementAt(fi);
                    if (path.endsWith(".2dip")) {
                        byte[] hdrTest = fm.readFile(path);
                        if (hdrTest != null && GameCompiler.readHeader(hdrTest) != null) {
                            String[] hdr = GameCompiler.readHeader(hdrTest);
                            String label = hdr[0] + " v" + hdr[2];
                            pickerList.append(label, null);
                            foundPackages.addElement(path);
                        }
                    }
                }
            }
        }

        if (foundPackages.size() == 0) {
            pickerList.append("[No .2dip games found]", null);
            pickerList.append("Use editor: Menu->Compile", null);
        }
    }

    // =========================================================
    //  LAUNCH GAME
    // =========================================================
    /**
     * Launches a game from raw .2dip bytes.
     * Can be called from the editor directly.
     */
    public void launchGame(byte[] packageBytes) {
        if (packageBytes == null) return;
        if (gamePlayer != null) gamePlayer.stop();

        gamePlayer = new GamePlayer(this, packageBytes);
        if (!gamePlayer.init()) {
            showAlert("Error", "Could not load game package.");
            return;
        }
        display.setCurrent(gamePlayer);
        gamePlayer.start();
    }

    // =========================================================
    //  COMMANDS
    // =========================================================
    public void commandAction(Command c, Displayable d) {
        if (d == pickerList) {
            if (c == cmdPlay || c == List.SELECT_COMMAND) {
                int idx = pickerList.getSelectedIndex();
                if (foundPackages != null && idx >= 0 && idx < foundPackages.size()) {
                    String path = (String) foundPackages.elementAt(idx);
                    FileManager fm = new FileManager();
                    byte[] data = fm.readFile(path);
                    if (data != null) {
                        launchGame(data);
                    } else {
                        showAlert("Error", "Could not read: " + path);
                    }
                }
            } else if (c == cmdRefresh) {
                scanForPackages();
            } else if (c == cmdExit) {
                destroyApp(true);
                notifyDestroyed();
            }
        }
    }

    // =========================================================
    //  BACK TO PICKER (called from GamePlayer when user exits)
    // =========================================================
    public void returnToPicker() {
        if (gamePlayer != null) { gamePlayer.stop(); gamePlayer = null; }
        showPicker();
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private void showAlert(String title, String msg) {
        Alert a = new Alert(title, msg, null, AlertType.ERROR);
        a.setTimeout(3000);
        display.setCurrent(a, pickerList != null ? pickerList : display.getCurrent());
    }

    public static GamePlayerMIDlet getInstance() { return instance; }
    public Display getDisplay()                   { return display; }
}
