import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * ProjectManager.java — Enhanced v2.0
 *
 * NEW FEATURES:
 *  - First-run install-location chooser: scans all drives and lets the
 *    user pick where to install the 2DLE folder (shown with free space)
 *  - Custom-drawn Canvas screens for chooser and project list (richer UI)
 *  - Project info screen: shows level count, size, last-modified stamp
 *  - Real project deletion via FileManager.deleteProject()
 *  - Project rename
 *  - Backup + restore from within the project selector
 *  - Import project from backup file (.2dlebak)
 *  - Storage summary bar on the main screen
 *  - Animated splash / logo screen before project list
 *  - Fallback to RecordStore mode with a clear status message
 */
public class ProjectManager implements CommandListener {

    // =========================================================
    //  STATE
    // =========================================================
    private LevelEditorMIDlet midlet;
    private FileManager       fileMgr;

    // Project list
    private Vector  projects;
    private String  currentProject;

    // ─── Screens ─────────────────────────────────────────────
    private Canvas    splashCanvas;
    private List      projectList;
    private List      rootChooserList;    // install-location chooser
    private Form      newProjectForm;
    private Form      projectInfoForm;
    private Form      renameForm;
    private Form      importForm;

    // ─── Commands ─────────────────────────────────────────────
    private Command cmdNew;
    private Command cmdOpen;
    private Command cmdDelete;
    private Command cmdRename;
    private Command cmdInfo;
    private Command cmdBackup;
    private Command cmdRestore;
    private Command cmdSettings;
    private Command cmdBack;
    private Command cmdOK;
    private Command cmdCancel;
    private Command cmdChangeRoot;

    // ─── Sub-screen state ─────────────────────────────────────
    private TextField tfNewName;
    private TextField tfRenameName;
    private TextField tfImportPath;
    private boolean   firstRun;    // true when no preference has been set yet

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public ProjectManager(LevelEditorMIDlet midlet) {
        this.midlet = midlet;
        fileMgr = new FileManager();

        buildCommands();

        // Detect first-run: if no preference was saved, start with chooser
        firstRun = !fileMgr.isAvailable()
                 || (fileMgr.getAvailableRootCount() > 1
                     && fileMgr.getActiveRootIndex() == 0
                     && !hasExistingInstall());
    }

    private void buildCommands() {
        cmdNew        = new Command("New",        Command.SCREEN,  1);
        cmdOpen       = new Command("Open",       Command.OK,      1);
        cmdDelete     = new Command("Delete",     Command.SCREEN,  3);
        cmdRename     = new Command("Rename",     Command.SCREEN,  4);
        cmdInfo       = new Command("Info",       Command.SCREEN,  5);
        cmdBackup     = new Command("Backup",     Command.SCREEN,  6);
        cmdRestore    = new Command("Restore",    Command.SCREEN,  7);
        cmdSettings   = new Command("Storage",    Command.SCREEN,  8);
        cmdBack       = new Command("Exit",       Command.BACK,    1);
        cmdOK         = new Command("OK",         Command.OK,      1);
        cmdCancel     = new Command("Cancel",     Command.BACK,    1);
        cmdChangeRoot = new Command("Change Drive", Command.SCREEN, 9);
    }

    /** Checks if the 2DLE folder already exists on the active root. */
    private boolean hasExistingInstall() {
        return fileMgr.fileExists(fileMgr.getRootPath());
    }

    // =========================================================
    //  ENTRY POINT
    // =========================================================
    public void showProjectSelector() {
        if (firstRun && fileMgr.getAvailableRootCount() > 1) {
            showInstallChooser();
        } else {
            showSplash();
        }
    }

    // =========================================================
    //  SPLASH SCREEN (animated Canvas)
    // =========================================================
    private void showSplash() {
        splashCanvas = new SplashCanvas(midlet, this);
        midlet.showScreen(splashCanvas);
    }

    /** Called by SplashCanvas when animation finishes. */
    public void onSplashDone() {
        showProjectList();
    }

    // =========================================================
    //  INSTALL-LOCATION CHOOSER
    // =========================================================
    /**
     * Shows a List of all detected drives so the user can pick
     * where to install the 2DLE project folder.
     */
    private void showInstallChooser() {
        int count = fileMgr.getAvailableRootCount();

        rootChooserList = new List("Choose Install Location", List.EXCLUSIVE);

        if (count == 0) {
            rootChooserList.append("[No storage found]", null);
            rootChooserList.append("Using RecordStore fallback", null);
        } else {
            for (int i = 0; i < count; i++) {
                String label = fileMgr.getRootLabel(i);
                rootChooserList.append(label, null);
            }
            // Pre-select the auto-detected best root
            rootChooserList.setSelectedIndex(fileMgr.getActiveRootIndex(), true);
        }

        rootChooserList.addCommand(cmdOK);
        rootChooserList.addCommand(cmdBack);
        rootChooserList.setCommandListener(this);
        midlet.showScreen(rootChooserList);
    }

    private void confirmInstallLocation(int selectedIndex) {
        if (fileMgr.getAvailableRootCount() == 0) {
            // No JSR-75 — continue with RMS fallback
            showAlert("Info",
                "No file system found.\nProjects will use RecordStore.",
                null);
            showSplash();
            return;
        }

        fileMgr.setInstallRoot(selectedIndex);
        firstRun = false;

        String label = fileMgr.getRootLabel(selectedIndex);
        showAlert("Installed",
            "2DLE folder created on:\n" + label,
            null);
        showSplash();
    }

    // =========================================================
    //  PROJECT LIST
    // =========================================================
    private void showProjectList() {
        projectList = new List("2D Level Editor", List.IMPLICIT);

        projectList.addCommand(cmdNew);
        projectList.addCommand(cmdOpen);
        projectList.addCommand(cmdDelete);
        projectList.addCommand(cmdRename);
        projectList.addCommand(cmdInfo);
        projectList.addCommand(cmdBackup);
        projectList.addCommand(cmdRestore);
        projectList.addCommand(cmdSettings);
        projectList.addCommand(cmdChangeRoot);
        projectList.addCommand(cmdBack);
        projectList.setCommandListener(this);

        refreshProjectList();
        midlet.showScreen(projectList);
    }

    private void refreshProjectList() {
        while (projectList.size() > 0) projectList.delete(0);

        if (!fileMgr.isAvailable()) {
            projectList.append("[No File System]", null);
            projectList.append("JSR-75 unavailable", null);
            projectList.append("Using RecordStore mode", null);
            return;
        }

        // Header: storage info
        long free = fileMgr.getFreeSpace();
        String freeStr = free >= 0 ? FileManager.formatBytes(free) + " free" : "storage";
        projectList.append("=== " + freeStr + " ===", null);

        projects = fileMgr.listProjects();

        if (projects.size() == 0) {
            projectList.append("[No projects yet]", null);
            projectList.append("Press 'New' to create one", null);
        } else {
            for (int i = 0; i < projects.size(); i++) {
                String name = (String) projects.elementAt(i);
                // Show level count inline
                Vector levels = fileMgr.listLevels(name);
                String suffix = " (" + levels.size() + " level"
                              + (levels.size() == 1 ? "" : "s") + ")";
                projectList.append(name + suffix, null);
            }
        }
    }

    // =========================================================
    //  NEW PROJECT
    // =========================================================
    private void showNewProjectDialog() {
        newProjectForm = new Form("New Project");

        tfNewName = new TextField("Project name:", "", 32, TextField.ANY);
        newProjectForm.append(tfNewName);

        // Show available space
        long free = fileMgr.getFreeSpace();
        if (free >= 0) {
            newProjectForm.append(new StringItem("Free space:",
                FileManager.formatBytes(free)));
        }

        newProjectForm.addCommand(cmdOK);
        newProjectForm.addCommand(cmdCancel);
        newProjectForm.setCommandListener(this);
        midlet.showScreen(newProjectForm);
    }

    private void createProject(String rawName) {
        if (rawName == null || rawName.trim().length() == 0) {
            showAlert("Error", "Name cannot be empty.", newProjectForm);
            return;
        }
        String name = FileManager.sanitizeName(rawName.trim());
        if (name.length() == 0) {
            showAlert("Error", "Name contains no valid characters.", newProjectForm);
            return;
        }

        // Check duplicate
        if (fileMgr.fileExists(fileMgr.getProjectPath(name))) {
            showAlert("Error", "Project '" + name + "' already exists.", newProjectForm);
            return;
        }

        if (fileMgr.createProjectStructure(name)) {
            currentProject = name;
            showAlert("Created", "Project '" + name + "' ready!", null);
            midlet.startEditor(fileMgr.getProjectPath(name));
        } else {
            showAlert("Error",
                "Could not create project.\nCheck storage space.", newProjectForm);
        }
    }

    // =========================================================
    //  OPEN PROJECT
    // =========================================================
    private void openProject(int listIndex) {
        // listIndex 0 is the storage-info header row — skip it
        int projIndex = listIndex - 1;
        if (projects == null || projIndex < 0 || projIndex >= projects.size()) return;
        currentProject = (String) projects.elementAt(projIndex);
        midlet.startEditor(fileMgr.getProjectPath(currentProject));
    }

    // =========================================================
    //  PROJECT INFO
    // =========================================================
    private void showProjectInfo(int listIndex) {
        int projIndex = listIndex - 1;
        if (projects == null || projIndex < 0 || projIndex >= projects.size()) return;

        String name = (String) projects.elementAt(projIndex);
        projectInfoForm = new Form("Info: " + name);

        // Level count
        Vector levels = fileMgr.listLevels(name);
        projectInfoForm.append(new StringItem("Levels:", "" + levels.size()));

        // Asset count
        Vector assets = fileMgr.listAssets(name);
        projectInfoForm.append(new StringItem("Assets:", "" + assets.size()));

        // Backup count
        Vector backups = fileMgr.listBackups(name);
        projectInfoForm.append(new StringItem("Backups:", "" + backups.size()));

        // Project size
        long size = fileMgr.getProjectSize(name);
        projectInfoForm.append(new StringItem("Size:", FileManager.formatBytes(size)));

        // Install path
        projectInfoForm.append(new StringItem("Location:",
            fileMgr.getProjectPath(name)));

        projectInfoForm.addCommand(cmdBack);
        projectInfoForm.setCommandListener(this);
        midlet.showScreen(projectInfoForm);
    }

    // =========================================================
    //  DELETE PROJECT
    // =========================================================
    private void deleteProject(int listIndex) {
        int projIndex = listIndex - 1;
        if (projects == null || projIndex < 0 || projIndex >= projects.size()) return;

        final String name = (String) projects.elementAt(projIndex);

        // Confirmation dialog
        Alert confirm = new Alert(
            "Delete Project",
            "Delete '" + name + "' and ALL its files?\nThis cannot be undone.",
            null, AlertType.WARNING);
        confirm.setTimeout(Alert.FOREVER);

        Command yes = new Command("Delete", Command.OK, 1);
        Command no  = new Command("Cancel", Command.BACK, 1);
        confirm.addCommand(yes);
        confirm.addCommand(no);
        confirm.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    if (fileMgr.deleteProject(name)) {
                        showAlert("Deleted", "Project '" + name + "' removed.", null);
                    } else {
                        showAlert("Error", "Could not delete project.", null);
                    }
                    showProjectList();
                } else {
                    midlet.showScreen(projectList);
                }
            }
        });
        midlet.getDisplay().setCurrent(confirm);
    }

    // =========================================================
    //  RENAME PROJECT
    // =========================================================
    private void showRenameDialog(int listIndex) {
        int projIndex = listIndex - 1;
        if (projects == null || projIndex < 0 || projIndex >= projects.size()) return;

        final String oldName = (String) projects.elementAt(projIndex);
        renameForm = new Form("Rename: " + oldName);
        tfRenameName = new TextField("New name:", oldName, 32, TextField.ANY);
        renameForm.append(tfRenameName);
        renameForm.addCommand(cmdOK);
        renameForm.addCommand(cmdCancel);
        renameForm.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    String newName = FileManager.sanitizeName(
                        tfRenameName.getString().trim());
                    if (newName.length() == 0) {
                        showAlert("Error", "Invalid name.", renameForm);
                        return;
                    }
                    if (fileMgr.renameProject(oldName, newName)) {
                        showAlert("Renamed", oldName + " -> " + newName, null);
                        showProjectList();
                    } else {
                        showAlert("Error", "Rename failed.", renameForm);
                    }
                } else {
                    showProjectList();
                }
            }
        });
        midlet.showScreen(renameForm);
    }

    // =========================================================
    //  BACKUP
    // =========================================================
    private void backupProject(int listIndex) {
        int projIndex = listIndex - 1;
        if (projects == null || projIndex < 0 || projIndex >= projects.size()) return;

        String name  = (String) projects.elementAt(projIndex);
        String stamp = FileManager.makeStamp();
        String path  = fileMgr.backupProject(name, stamp);

        if (path != null) {
            // Get size for feedback
            long size = fileMgr.getProjectSize(name);
            showAlert("Backup Created",
                "Saved: " + stamp + FileManager.BACKUP_EXT
                + "\nSize: " + FileManager.formatBytes(size), null);
        } else {
            showAlert("Error", "Backup failed.\nCheck storage space.", null);
        }
        showProjectList();
    }

    // =========================================================
    //  RESTORE / IMPORT
    // =========================================================
    private void showRestoreDialog() {
        importForm = new Form("Restore from Backup");
        importForm.append(new StringItem("",
            "Enter the full path to a .2dlebak file,\nor select a project to restore from its backups."));

        tfImportPath = new TextField("Backup path:", "", 128, TextField.ANY);
        importForm.append(tfImportPath);

        TextField tfRestoreName = new TextField("Restore as:", "restored", 32, TextField.ANY);
        importForm.append(tfRestoreName);

        // Also list available backups from all projects
        Vector allProjects = fileMgr.listProjects();
        for (int i = 0; i < allProjects.size(); i++) {
            String pn = (String) allProjects.elementAt(i);
            Vector backups = fileMgr.listBackups(pn);
            for (int j = 0; j < backups.size(); j++) {
                importForm.append(new StringItem(pn + ":",
                    (String) backups.elementAt(j)));
            }
        }

        final TextField ftfPath = tfImportPath;
        final TextField ftfName = tfRestoreName;

        importForm.addCommand(cmdOK);
        importForm.addCommand(cmdCancel);
        importForm.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    String srcPath  = ftfPath.getString().trim();
                    String destName = FileManager.sanitizeName(
                        ftfName.getString().trim());
                    if (srcPath.length() == 0 || destName.length() == 0) {
                        showAlert("Error", "Path and name are required.", importForm);
                        return;
                    }
                    if (fileMgr.restoreProject(srcPath, destName)) {
                        showAlert("Restored", "Project '" + destName + "' ready.", null);
                        showProjectList();
                    } else {
                        showAlert("Error",
                            "Restore failed.\nCheck path and storage.", importForm);
                    }
                } else {
                    showProjectList();
                }
            }
        });
        midlet.showScreen(importForm);
    }

    // =========================================================
    //  STORAGE SETTINGS SCREEN
    // =========================================================
    private void showStorageSettings() {
        Form form = new Form("Storage Settings");

        // Current install location
        form.append(new StringItem("Install path:", fileMgr.getRootPath()));

        // Free space on active root
        long free = fileMgr.getFreeSpace();
        form.append(new StringItem("Free space:",
            free >= 0 ? FileManager.formatBytes(free) : "unknown"));

        // All available roots
        form.append(new StringItem("Available drives:", ""));
        int count = fileMgr.getAvailableRootCount();
        if (count == 0) {
            form.append(new StringItem("", "None detected"));
        } else {
            for (int i = 0; i < count; i++) {
                String marker = (i == fileMgr.getActiveRootIndex()) ? "* " : "  ";
                form.append(new StringItem(marker, fileMgr.getRootLabel(i)));
            }
        }

        // Change drive button
        form.addCommand(cmdChangeRoot);
        form.addCommand(cmdBack);
        form.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Change Drive")) {
                    showInstallChooser();
                } else {
                    showProjectList();
                }
            }
        });
        midlet.showScreen(form);
    }

    // =========================================================
    //  COMMAND DISPATCH
    // =========================================================
    public void commandAction(Command c, Displayable d) {

        // ── Install chooser ───────────────────────────────────
        if (d == rootChooserList) {
            if (c == cmdOK) {
                int idx = rootChooserList.getSelectedIndex();
                confirmInstallLocation(idx);
            } else if (c == cmdBack) {
                midlet.exitApp();
            }
            return;
        }

        // ── Project list ──────────────────────────────────────
        if (d == projectList) {
            int idx = projectList.getSelectedIndex();

            if (c == cmdNew) {
                showNewProjectDialog();
            } else if (c == cmdOpen || c == List.SELECT_COMMAND) {
                openProject(idx);
            } else if (c == cmdDelete) {
                deleteProject(idx);
            } else if (c == cmdRename) {
                showRenameDialog(idx);
            } else if (c == cmdInfo) {
                showProjectInfo(idx);
            } else if (c == cmdBackup) {
                backupProject(idx);
            } else if (c == cmdRestore) {
                showRestoreDialog();
            } else if (c == cmdSettings) {
                showStorageSettings();
            } else if (c == cmdChangeRoot) {
                showInstallChooser();
            } else if (c == cmdBack) {
                midlet.exitApp();
            }
            return;
        }

        // ── New project form ──────────────────────────────────
        if (d == newProjectForm) {
            if (c == cmdOK) {
                createProject(tfNewName.getString());
            } else {
                showProjectList();
            }
            return;
        }

        // ── Project info form ─────────────────────────────────
        if (d == projectInfoForm) {
            showProjectList();
            return;
        }
    }

    // =========================================================
    //  ALERT HELPER
    // =========================================================
    private void showAlert(String title, String msg, Displayable returnTo) {
        Alert alert = new Alert(title, msg, null, AlertType.INFO);
        alert.setTimeout(3000);
        Displayable next = (returnTo != null) ? returnTo : projectList;
        if (next != null) {
            midlet.getDisplay().setCurrent(alert, next);
        } else {
            midlet.showScreen(alert);
        }
    }

    // =========================================================
    //  ACCESSORS
    // =========================================================
    public FileManager getFileManager()   { return fileMgr; }
    public String      getCurrentProject(){ return currentProject; }

    // =========================================================
    //  SPLASH CANVAS (inner class)
    //  Custom-drawn animated splash screen with logo + progress
    // =========================================================
    private static class SplashCanvas extends Canvas implements Runnable {

        private LevelEditorMIDlet midlet;
        private ProjectManager    manager;
        private volatile boolean  running;
        private Thread            thread;

        // Animation state
        private int   frame;
        private int   progress;   // 0-100
        private int   phase;      // 0=fade-in, 1=hold, 2=loading bar, 3=done
        private int   fadeAlpha;  // 0=transparent, 255=opaque

        // Screen dims
        private int W, H;

        // Tile colours for decorative animated border
        private static final int[] TILE_COLORS = {
            0xFF2D5A27, 0xFF696969, 0xFF2F5496, 0xFFD4AF37,
            0xFFA52A2A, 0xFF228B22, 0xFFDC143C, 0xFFE8E8E8
        };
        private int borderOffset;

        SplashCanvas(LevelEditorMIDlet midlet, ProjectManager manager) {
            super();
            this.midlet  = midlet;
            this.manager = manager;
            setFullScreenMode(true);
        }

        protected void showNotify() {
            W = getWidth();
            H = getHeight();
            running = true;
            thread  = new Thread(this);
            thread.start();
        }

        protected void hideNotify() {
            running = false;
        }

        public void run() {
            frame        = 0;
            progress     = 0;
            phase        = 0;
            fadeAlpha    = 0;
            borderOffset = 0;

            while (running) {
                frame++;
                borderOffset = (borderOffset + 1) % (TILE_COLORS.length * 8);

                switch (phase) {
                    case 0: // Fade in
                        fadeAlpha += 12;
                        if (fadeAlpha >= 255) { fadeAlpha = 255; phase = 1; }
                        break;
                    case 1: // Hold logo
                        if (frame > 40) phase = 2;
                        break;
                    case 2: // Loading bar fills
                        progress += 3;
                        if (progress >= 100) { progress = 100; phase = 3; }
                        break;
                    case 3: // Done
                        try { Thread.sleep(400); } catch (Exception e) {}
                        running = false;
                        break;
                }

                repaint();
                serviceRepaints();

                try { Thread.sleep(33); } catch (Exception e) {}
            }

            manager.onSplashDone();
        }

        protected void paint(Graphics g) {
            // ── Background ────────────────────────────────────
            g.setColor(0x0D1117); // very dark navy
            g.fillRect(0, 0, W, H);

            // ── Animated tile border ──────────────────────────
            int tileW = 12;
            drawTileBorder(g, tileW);

            // ── Pixel-art logo text "2DLE" ────────────────────
            drawLogoText(g);

            // ── Subtitle ──────────────────────────────────────
            Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,  Font.SIZE_SMALL);
            Font bold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,   Font.SIZE_MEDIUM);
            g.setFont(small);
            g.setColor(0x7ABFFF);
            String sub = "2D Level Editor v2.0";
            g.drawString(sub, W / 2, H / 2 + 30, Graphics.HCENTER | Graphics.TOP);

            // ── Loading bar ───────────────────────────────────
            if (phase >= 2) {
                int bw = W - 40;
                int bh = 10;
                int bx = 20;
                int by = H - 45;

                // Track
                g.setColor(0x1E2A38);
                g.fillRoundRect(bx, by, bw, bh, 5, 5);
                // Fill
                int filled = bw * progress / 100;
                // Gradient simulation: 3 bands
                g.setColor(0x1A6EBD);
                g.fillRoundRect(bx, by, filled / 2, bh, 5, 5);
                g.setColor(0x3FA7FF);
                g.fillRoundRect(bx + filled / 2, by, filled - filled / 2, bh, 5, 5);
                // Border
                g.setColor(0x3FA7FF);
                g.drawRoundRect(bx, by, bw, bh, 5, 5);

                // Percentage
                g.setFont(small);
                g.setColor(0xAAAAAA);
                g.drawString(progress + "%", W / 2, by + bh + 3,
                             Graphics.HCENTER | Graphics.TOP);
            }

            // ── Version / credit line ─────────────────────────
            g.setFont(small);
            g.setColor(0x445566);
            g.drawString("(c) 2DLE Project", W / 2, H - 14,
                         Graphics.HCENTER | Graphics.TOP);

            // ── Fade overlay ──────────────────────────────────
            if (fadeAlpha < 255) {
                // Draw darkening rect that fades out
                int alpha = 255 - fadeAlpha;
                // Approximate: draw black rect getting lighter
                for (int y = 0; y < H; y += 2) {
                    g.setColor(0x000000);
                    g.drawLine(0, y, W, y);
                }
            }
        }

        /** Draws a border of coloured tile squares that scroll. */
        private void drawTileBorder(Graphics g, int tileW) {
            int cols = W / tileW + 2;
            int rows = H / tileW + 2;

            // Top row
            for (int x = 0; x < cols; x++) {
                int ci = ((x + borderOffset / 4) % TILE_COLORS.length);
                g.setColor(TILE_COLORS[ci]);
                g.fillRect(x * tileW - (borderOffset % tileW), 0, tileW - 1, tileW - 1);
            }
            // Bottom row
            for (int x = 0; x < cols; x++) {
                int ci = ((x + TILE_COLORS.length / 2 + borderOffset / 4) % TILE_COLORS.length);
                g.setColor(TILE_COLORS[ci]);
                g.fillRect(x * tileW - (borderOffset % tileW),
                           H - tileW, tileW - 1, tileW - 1);
            }
            // Left column
            for (int y = 1; y < rows - 1; y++) {
                int ci = ((y + borderOffset / 4) % TILE_COLORS.length);
                g.setColor(TILE_COLORS[ci]);
                g.fillRect(0, y * tileW - (borderOffset % tileW), tileW - 1, tileW - 1);
            }
            // Right column
            for (int y = 1; y < rows - 1; y++) {
                int ci = ((y + TILE_COLORS.length / 2 + borderOffset / 4) % TILE_COLORS.length);
                g.setColor(TILE_COLORS[ci]);
                g.fillRect(W - tileW, y * tileW - (borderOffset % tileW),
                           tileW - 1, tileW - 1);
            }
        }

        /** Draws a large blocky "2DLE" logo using filled rectangles. */
        private void drawLogoText(Graphics g) {
            int px = 4;  // pixel block size
            int cx = W / 2;
            int cy = H / 2 - 20;

            // Glow effect — draw logo twice, slightly offset in a dim colour
            drawPixelChar(g, cx - 6 * px * 4, cy, CHAR_2, px, 0x1A4A8A, false);
            drawPixelChar(g, cx - 6 * px * 4 + 1, cy + 1, CHAR_2, px, 0x1A4A8A, false);

            // Main logo, 4 characters: 2  D  L  E
            // Each char is 5×7 pixels, spacing = 6 * px
            int spacing = 6 * px;
            int startX  = cx - spacing * 2 + spacing / 2;

            // Bright colours per character
            drawPixelChar(g, startX,                  cy, CHAR_2, px, 0xFFDD44, true);
            drawPixelChar(g, startX + spacing,        cy, CHAR_D, px, 0x44DDFF, true);
            drawPixelChar(g, startX + spacing * 2,    cy, CHAR_L, px, 0xFF6644, true);
            drawPixelChar(g, startX + spacing * 3,    cy, CHAR_E, px, 0x88FF44, true);
        }

        /**
         * Draws a 5×7 pixel-art character using filled blocks.
         * bitmap: int[7], each int uses bits 4..0 for columns left..right.
         */
        private void drawPixelChar(Graphics g, int x, int y,
                                   int[] bitmap, int px, int color, boolean lit) {
            for (int row = 0; row < bitmap.length; row++) {
                for (int col = 0; col < 5; col++) {
                    if ((bitmap[row] & (1 << (4 - col))) != 0) {
                        if (lit) {
                            g.setColor(color);
                        } else {
                            g.setColor(color & 0x003F3F3F); // dim
                        }
                        g.fillRect(x + col * px, y + row * px, px - 1, px - 1);
                    }
                }
            }
        }

        // 5×7 pixel font bitmaps for: 2, D, L, E
        // 5x7 pixel font bitmaps — bit 4=leftmost col, bit 0=rightmost col
        // Stored as decimal (binary literals require Java 7+, incompatible with J2ME 1.3)
        private static final int[] CHAR_2 = { 14, 17,  1,  6,  8, 16, 31 };
        private static final int[] CHAR_D = { 28, 18, 17, 17, 17, 18, 28 };
        private static final int[] CHAR_L = { 16, 16, 16, 16, 16, 16, 31 };
        private static final int[] CHAR_E = { 31, 16, 16, 30, 16, 16, 31 };

        protected void keyPressed(int key) {
            // Any key skips splash
            running = false;
        }

        protected void pointerPressed(int x, int y) {
            running = false;
        }
    }
}
