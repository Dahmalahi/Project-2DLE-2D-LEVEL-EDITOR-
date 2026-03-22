import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * LinkEditor.java  v1.0
 *
 * In-editor UI for creating and managing map transition links.
 * Opens when user presses # while cursor is on a DOOR/STAIRS/TELEPAD tile.
 *
 * Flow:
 *   1. Shows link type (auto-detected from tile under cursor)
 *   2. User picks target level from a list of existing levels in the project
 *   3. User types spawn X, Y position in target map
 *   4. Optionally sets trigger mode (step=auto / action=press 5)
 *   5. Saves link to MapLinkSystem
 */
public class LinkEditor implements CommandListener {

    public interface LinkSavedCallback {
        void onLinkSaved(MapLinkSystem.MapLink link);
        void onLinkDeleted(int srcX, int srcY);
        void onLinkCancelled();
    }

    // =========================================================
    //  STATE
    // =========================================================
    private LevelEditorMIDlet  midlet;
    private FileManager        fileMgr;
    private String             projectName;
    private MapLinkSystem      linkSys;
    private LinkSavedCallback  callback;

    private int  srcTileX, srcTileY;
    private int  detectedLinkType;

    // ── Screens ──────────────────────────────────────────────
    private Form   editorForm;
    private List   levelPickerList;

    // ── Form items ───────────────────────────────────────────
    private ChoiceGroup cgLinkType;
    private ChoiceGroup cgTrigger;
    private TextField   tfTargetLevel;
    private TextField   tfSpawnX;
    private TextField   tfSpawnY;
    private ChoiceGroup cgSpawnDir;

    // ── Commands ─────────────────────────────────────────────
    private Command cmdSave;
    private Command cmdDelete;
    private Command cmdPickLevel;
    private Command cmdCancel;
    private Command cmdOK;
    private Command cmdBack;

    // =========================================================
    //  FACTORY
    // =========================================================
    public static void show(LevelEditorMIDlet midlet,
                            FileManager fileMgr,
                            String projectName,
                            MapLinkSystem linkSys,
                            int srcX, int srcY,
                            int tileType,
                            LinkSavedCallback callback) {
        LinkEditor ed = new LinkEditor(midlet, fileMgr, projectName,
                                       linkSys, srcX, srcY, tileType, callback);
        ed.showForm();
    }

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    private LinkEditor(LevelEditorMIDlet midlet,
                       FileManager fileMgr,
                       String projectName,
                       MapLinkSystem linkSys,
                       int srcX, int srcY,
                       int tileType,
                       LinkSavedCallback callback) {
        this.midlet      = midlet;
        this.fileMgr     = fileMgr;
        this.projectName = projectName;
        this.linkSys     = linkSys;
        this.srcTileX    = srcX;
        this.srcTileY    = srcY;
        this.callback    = callback;
        this.detectedLinkType = detectLinkType(tileType);

        cmdSave      = new Command("Save Link", Command.OK,     1);
        cmdDelete    = new Command("Delete",    Command.SCREEN, 2);
        cmdPickLevel = new Command("Browse...", Command.SCREEN, 3);
        cmdCancel    = new Command("Cancel",    Command.BACK,   1);
        cmdOK        = new Command("OK",        Command.OK,     1);
        cmdBack      = new Command("Back",      Command.BACK,   1);
    }

    // =========================================================
    //  DETECT LINK TYPE FROM TILE
    // =========================================================
    private int detectLinkType(int tileType) {
        switch (tileType) {
            case TileData.TILE_DOOR:        return MapLinkSystem.LINK_DOOR;
            case TileData.TILE_STAIRS_UP:   return MapLinkSystem.LINK_STAIRS_U;
            case TileData.TILE_STAIRS_DN:   return MapLinkSystem.LINK_STAIRS_D;
            case TileData.TILE_TELEPAD:     return MapLinkSystem.LINK_TELEPAD;
            case TileData.TILE_WARP_STONE:  return MapLinkSystem.LINK_WARPSTONE;
            default:                        return MapLinkSystem.LINK_DOOR;
        }
    }

    // =========================================================
    //  MAIN FORM
    // =========================================================
    private void showForm() {
        String title = "Link [" + srcTileX + "," + srcTileY + "]";
        editorForm = new Form(title);

        // Show existing link info if any
        MapLinkSystem.MapLink existing = linkSys.findLink(srcTileX, srcTileY);

        // Link type chooser
        cgLinkType = new ChoiceGroup("Type:", ChoiceGroup.EXCLUSIVE);
        for (int i = 0; i < MapLinkSystem.LINK_NAMES.length; i++) {
            cgLinkType.append(MapLinkSystem.LINK_NAMES[i], null);
        }
        int selType = (existing != null) ? existing.linkType : detectedLinkType;
        cgLinkType.setSelectedIndex(selType, true);
        editorForm.append(cgLinkType);

        // Target level
        String existTarget = (existing != null && existing.targetLevel != null)
                           ? existing.targetLevel : "";
        tfTargetLevel = new TextField("Target level:", existTarget, 64, TextField.ANY);
        editorForm.append(tfTargetLevel);

        // Spawn position
        String existX = (existing != null) ? String.valueOf(existing.spawnX) : "1";
        String existY = (existing != null) ? String.valueOf(existing.spawnY) : "1";
        tfSpawnX = new TextField("Spawn X:", existX, 4, TextField.NUMERIC);
        tfSpawnY = new TextField("Spawn Y:", existY, 4, TextField.NUMERIC);
        editorForm.append(tfSpawnX);
        editorForm.append(tfSpawnY);

        // Spawn direction
        cgSpawnDir = new ChoiceGroup("Face:", ChoiceGroup.EXCLUSIVE);
        cgSpawnDir.append("Down",  null);
        cgSpawnDir.append("Up",    null);
        cgSpawnDir.append("Left",  null);
        cgSpawnDir.append("Right", null);
        int selDir = (existing != null) ? existing.spawnDir : 0;
        cgSpawnDir.setSelectedIndex(selDir, true);
        editorForm.append(cgSpawnDir);

        // Trigger mode
        cgTrigger = new ChoiceGroup("Trigger:", ChoiceGroup.EXCLUSIVE);
        cgTrigger.append("Auto (step on)",    null);
        cgTrigger.append("Manual (press 5)",  null);
        int selTrig = (existing != null) ? existing.trigger : MapLinkSystem.TRIGGER_STEP;
        cgTrigger.setSelectedIndex(selTrig, true);
        editorForm.append(cgTrigger);

        // Helper info
        editorForm.append(new StringItem("Tip:",
            "Browse to pick a level,\nor type name manually.\n"
          + "Spawn = player start position\nin the target level."));

        editorForm.addCommand(cmdSave);
        editorForm.addCommand(cmdPickLevel);
        if (existing != null) editorForm.addCommand(cmdDelete);
        editorForm.addCommand(cmdCancel);
        editorForm.setCommandListener(this);
        midlet.showScreen(editorForm);
    }

    // =========================================================
    //  LEVEL PICKER
    // =========================================================
    private void showLevelPicker() {
        Vector levels = new Vector();
        if (fileMgr != null && fileMgr.isAvailable()) {
            levels = fileMgr.listLevels(projectName);
        }
        if (levels.size() == 0) {
            Alert a = new Alert("No Levels",
                "No saved levels found.\nType the name manually.", null, AlertType.INFO);
            a.setTimeout(2000);
            midlet.getDisplay().setCurrent(a, editorForm);
            return;
        }

        levelPickerList = new List("Pick Target Level", List.IMPLICIT);
        for (int i = 0; i < levels.size(); i++) {
            levelPickerList.append((String) levels.elementAt(i), null);
        }
        levelPickerList.addCommand(cmdOK);
        levelPickerList.addCommand(cmdBack);
        levelPickerList.setCommandListener(this);
        midlet.showScreen(levelPickerList);
    }

    // =========================================================
    //  SAVE LINK
    // =========================================================
    private void saveLink() {
        String target = tfTargetLevel.getString().trim();
        if (target.length() == 0) {
            Alert a = new Alert("Error", "Target level name is required.", null, AlertType.WARNING);
            a.setTimeout(2000);
            midlet.getDisplay().setCurrent(a, editorForm);
            return;
        }

        int sx = parseIntSafe(tfSpawnX.getString(), 1);
        int sy = parseIntSafe(tfSpawnY.getString(), 1);
        int dir   = cgSpawnDir.getSelectedIndex();
        int type  = cgLinkType.getSelectedIndex();
        int trig  = cgTrigger.getSelectedIndex();

        // Remove existing link at this tile first
        linkSys.removeLinkAt(srcTileX, srcTileY);

        // Create new link
        MapLinkSystem.MapLink ml = new MapLinkSystem.MapLink(
            type, srcTileX, srcTileY, target, sx, sy, dir);
        ml.trigger = trig;
        linkSys.addLink(ml);

        if (callback != null) callback.onLinkSaved(ml);
    }

    // =========================================================
    //  COMMAND DISPATCH
    // =========================================================
    public void commandAction(Command c, Displayable d) {
        if (d == editorForm) {
            if (c == cmdSave) {
                saveLink();
            } else if (c == cmdPickLevel) {
                showLevelPicker();
            } else if (c == cmdDelete) {
                linkSys.removeLinkAt(srcTileX, srcTileY);
                if (callback != null) callback.onLinkDeleted(srcTileX, srcTileY);
            } else if (c == cmdCancel) {
                if (callback != null) callback.onLinkCancelled();
            }
        } else if (d == levelPickerList) {
            if (c == cmdOK || c == List.SELECT_COMMAND) {
                int idx = levelPickerList.getSelectedIndex();
                if (idx >= 0) {
                    tfTargetLevel.setString(levelPickerList.getString(idx));
                }
                midlet.showScreen(editorForm);
            } else {
                midlet.showScreen(editorForm);
            }
        }
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
