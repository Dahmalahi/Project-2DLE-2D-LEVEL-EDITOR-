import javax.microedition.lcdui.*;
import java.util.Vector;

/**
 * ScriptEditor.java — In-editor script authoring UI  v1.0
 *
 * Provides a full text-based script editor inside the level editor.
 * The user can:
 *  - Write / edit .2dls script source text
 *  - Compile to bytecode with error feedback
 *  - Attach scripts to tiles, NPCs, or map events
 *  - Browse, load and save script files per project
 *  - Insert template scripts (chest, boss, NPC, shop)
 *  - Preview the decompiled bytecode
 *  - Run a quick syntax check without saving
 *
 * Integration:
 *   Call ScriptEditor.show(midlet, fileMgr, projectName, targetId, callback)
 *   from EditorCanvas when the user presses "Script" on a tile/NPC.
 */
public class ScriptEditor implements CommandListener {

    // =========================================================
    //  CALLBACK INTERFACE
    // =========================================================
    public interface ScriptSavedCallback {
        /**
         * Called when the user saves a script.
         * @param targetType  TARGET_TILE / TARGET_NPC / TARGET_MAP
         * @param targetId    tile index, NPC id, or event id
         * @param bytecode    compiled bytecode (ready for EventSystem.runScript)
         * @param source      source text (for re-editing)
         */
        void onScriptSaved(int targetType, int targetId, int[] bytecode, String source);
    }

    // Target types
    public static final int TARGET_TILE = 0;
    public static final int TARGET_NPC  = 1;
    public static final int TARGET_MAP  = 2;

    // =========================================================
    //  STATE
    // =========================================================
    private LevelEditorMIDlet  midlet;
    private FileManager        fileMgr;
    private String             projectName;
    private int                targetType;
    private int                targetId;
    private ScriptSavedCallback callback;

    // Current script source being edited
    private String currentSource;
    private String scriptFileName; // e.g. "event_npc5.2dls"

    // ── Screens ──────────────────────────────────────────────
    private Form   editorForm;       // main editor
    private Form   templateForm;     // template picker
    private Form   filePickerForm;   // open existing script
    private Form   statusForm;       // compile result / error

    // ── Fields ───────────────────────────────────────────────
    private TextField tfSource;      // multi-line script text field
    private TextField tfFileName;    // filename for save
    private ChoiceGroup cgTemplates; // template list

    // ── Commands ─────────────────────────────────────────────
    private Command cmdCompileSave;
    private Command cmdCheck;
    private Command cmdTemplate;
    private Command cmdOpen;
    private Command cmdDecompile;
    private Command cmdCancel;
    private Command cmdOK;
    private Command cmdBack;

    // =========================================================
    //  FACTORY / ENTRY POINT
    // =========================================================
    /**
     * Shows the script editor for the given target.
     *
     * @param midlet       the MIDlet
     * @param fileMgr      project file manager
     * @param projectName  current project name
     * @param targetType   TARGET_TILE / TARGET_NPC / TARGET_MAP
     * @param targetId     tile index, NPC id, or event id
     * @param existingSrc  existing source to load (null = empty)
     * @param callback     called when the user saves
     */
    public static void show(LevelEditorMIDlet midlet,
                            FileManager fileMgr,
                            String projectName,
                            int targetType,
                            int targetId,
                            String existingSrc,
                            ScriptSavedCallback callback) {
        ScriptEditor ed = new ScriptEditor(midlet, fileMgr, projectName,
                                           targetType, targetId, callback);
        ed.currentSource  = (existingSrc != null) ? existingSrc : defaultSource(targetType, targetId);
        ed.scriptFileName = buildFileName(targetType, targetId);
        ed.showEditorForm();
    }

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    private ScriptEditor(LevelEditorMIDlet midlet,
                         FileManager fileMgr,
                         String projectName,
                         int targetType, int targetId,
                         ScriptSavedCallback callback) {
        this.midlet      = midlet;
        this.fileMgr     = fileMgr;
        this.projectName = projectName;
        this.targetType  = targetType;
        this.targetId    = targetId;
        this.callback    = callback;

        cmdCompileSave = new Command("Save+Run", Command.OK,     1);
        cmdCheck       = new Command("Check",    Command.SCREEN, 2);
        cmdTemplate    = new Command("Template", Command.SCREEN, 3);
        cmdOpen        = new Command("Open",     Command.SCREEN, 4);
        cmdDecompile   = new Command("Decompile",Command.SCREEN, 5);
        cmdCancel      = new Command("Cancel",   Command.BACK,   1);
        cmdOK          = new Command("OK",       Command.OK,     1);
        cmdBack        = new Command("Back",     Command.BACK,   1);
    }

    // =========================================================
    //  MAIN EDITOR SCREEN
    // =========================================================
    private void showEditorForm() {
        String title = targetLabel() + " Script";
        editorForm = new Form(title);

        // Script source text area
        tfSource = new TextField("Source:", currentSource, 2048, TextField.ANY);
        editorForm.append(tfSource);

        // Filename field
        tfFileName = new TextField("File:", scriptFileName, 64, TextField.ANY);
        editorForm.append(tfFileName);

        // Info label
        editorForm.append(new StringItem("Keys:",
            "Save+Run = compile & attach\n"
          + "Check = syntax only\n"
          + "Template = insert template\n"
          + "Open = load .2dls file"));

        editorForm.addCommand(cmdCompileSave);
        editorForm.addCommand(cmdCheck);
        editorForm.addCommand(cmdTemplate);
        editorForm.addCommand(cmdOpen);
        editorForm.addCommand(cmdDecompile);
        editorForm.addCommand(cmdCancel);
        editorForm.setCommandListener(this);

        midlet.showScreen(editorForm);
    }

    // =========================================================
    //  TEMPLATE PICKER
    // =========================================================
    private void showTemplatePicker() {
        templateForm = new Form("Insert Template");
        cgTemplates  = new ChoiceGroup("Choose template:", ChoiceGroup.EXCLUSIVE);
        cgTemplates.append("Empty script",        null);
        cgTemplates.append("NPC greeting",        null);
        cgTemplates.append("Shop NPC",            null);
        cgTemplates.append("Chest (give item)",   null);
        cgTemplates.append("Boss battle intro",   null);
        cgTemplates.append("Conditional branch",  null);
        cgTemplates.append("Loop counter",        null);
        cgTemplates.append("Warp / teleport",     null);
        cgTemplates.append("Weather change",      null);
        cgTemplates.append("Quest trigger",       null);
        cgTemplates.setSelectedIndex(0, true);
        templateForm.append(cgTemplates);
        templateForm.addCommand(cmdOK);
        templateForm.addCommand(cmdBack);
        templateForm.setCommandListener(this);
        midlet.showScreen(templateForm);
    }

    private void applyTemplate(int idx) {
        String src;
        switch (idx) {
            case 0:  src = "// Empty script\nend\n"; break;
            case 1:  src = ScriptLang.templateNPCGreeting("NPC", targetId); break;
            case 2:  src = ScriptLang.templateShopNPC(0); break;
            case 3:  src = ScriptLang.templateChest(targetId, 0, 1); break;
            case 4:  src = ScriptLang.templateBossBattle(9, targetId); break;
            case 5:  src = templateConditional(); break;
            case 6:  src = templateLoop(); break;
            case 7:  src = templateWarp(); break;
            case 8:  src = templateWeather(); break;
            case 9:  src = templateQuest(); break;
            default: src = "end\n"; break;
        }
        currentSource = src;
        showEditorForm();
    }

    // ── Extra templates ───────────────────────────────────────
    private String templateConditional() {
        return
            "// Conditional branch example\n"
          + "if switch 0 on goto already_done\n"
          + "text \"This is the first time!\"\n"
          + "set switch 0 on\n"
          + "give exp 20\n"
          + "goto done\n\n"
          + "label already_done\n"
          + "text \"You've been here before.\"\n\n"
          + "label done\n"
          + "end\n";
    }

    private String templateLoop() {
        return
            "// Loop counter: repeat 3 times\n"
          + "set var 0 0\n\n"
          + "label loop_start\n"
          + "if var 0 >= 3 goto loop_end\n"
          + "text \"Tick!\"\n"
          + "add var 0 1\n"
          + "goto loop_start\n\n"
          + "label loop_end\n"
          + "text \"Done!\"\n"
          + "end\n";
    }

    private String templateWarp() {
        return
            "// Warp to another map\n"
          + "text \"Entering the dungeon...\"\n"
          + "fadeout 4\n"
          + "wait 30\n"
          + "teleport 1 5 5\n"
          + "music 3\n"
          + "fadein 4\n"
          + "end\n";
    }

    private String templateWeather() {
        return
            "// Dynamic weather change\n"
          + "text \"The sky grows dark...\"\n"
          + "weather 4 90\n"
          + "shake 3 20\n"
          + "sound 21\n"
          + "wait 60\n"
          + "end\n";
    }

    private String templateQuest() {
        return
            "// Quest trigger NPC\n"
          + "if switch 10 on goto quest_done\n"
          + "text \"I need your help!\"\n"
          + "choice 2 \"I'll help!\" \"Not now.\"\n"
          + "if var 0 == 1 goto declined\n"
          + "startQuest 0\n"
          + "set switch 10 on\n"
          + "text \"Thank you!\"\n"
          + "goto done\n\n"
          + "label declined\n"
          + "text \"I understand...\"\n"
          + "goto done\n\n"
          + "label quest_done\n"
          + "checkQuest 0 quest_complete\n"
          + "text \"Still working on it?\"\n"
          + "goto done\n\n"
          + "label quest_complete\n"
          + "text \"You did it! Here's your reward.\"\n"
          + "give item 0 3\n"
          + "give exp 100\n"
          + "give gold 50\n\n"
          + "label done\n"
          + "end\n";
    }

    // =========================================================
    //  FILE PICKER
    // =========================================================
    private void showFilePicker() {
        if (fileMgr == null || !fileMgr.isAvailable()) {
            showAlert("Error", "File system not available.", editorForm);
            return;
        }

        Vector scripts = listScriptFiles();
        if (scripts.size() == 0) {
            showAlert("Info", "No .2dls scripts found in project.", editorForm);
            return;
        }

        filePickerForm = new Form("Open Script");
        final ChoiceGroup cg = new ChoiceGroup("Scripts:", ChoiceGroup.EXCLUSIVE);
        for (int i = 0; i < scripts.size(); i++) {
            cg.append((String) scripts.elementAt(i), null);
        }
        cg.setSelectedIndex(0, true);
        filePickerForm.append(cg);
        filePickerForm.addCommand(cmdOK);
        filePickerForm.addCommand(cmdBack);
        filePickerForm.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    int idx = cg.getSelectedIndex();
                    String name = cg.getString(idx);
                    loadScriptFile(name);
                } else {
                    midlet.showScreen(editorForm);
                }
            }
        });
        midlet.showScreen(filePickerForm);
    }

    private Vector listScriptFiles() {
        Vector files = new Vector();
        if (fileMgr == null) return files;
        String dir = fileMgr.getProjectPath(projectName);
        Vector all = new Vector();
        fileMgr.listAllFiles(dir, all);
        for (int i = 0; i < all.size(); i++) {
            String path = (String) all.elementAt(i);
            if (path.endsWith(".2dls")) {
                int slash = path.lastIndexOf('/');
                files.addElement(slash >= 0 ? path.substring(slash + 1) : path);
            }
        }
        return files;
    }

    private void loadScriptFile(String name) {
        String path = fileMgr.getProjectPath(projectName) + name;
        String src  = fileMgr.readTextFile(path);
        if (src != null) {
            currentSource  = src;
            scriptFileName = name;
            showEditorForm();
        } else {
            showAlert("Error", "Could not load: " + name, editorForm);
        }
    }

    // =========================================================
    //  COMPILE & SAVE
    // =========================================================
    private void doCompileSave() {
        String src  = tfSource.getString();
        String fname = tfFileName.getString().trim();
        if (fname.length() == 0) fname = scriptFileName;

        ScriptLang.CompileResult res = ScriptLang.compile(src);

        if (res.bytecode == null) {
            // Compile error
            showCompileError(res.error, res.errorLine, src);
            return;
        }

        // Save .2dls source
        if (fileMgr != null && fileMgr.isAvailable()) {
            String srcPath = fileMgr.getProjectPath(projectName) + fname;
            if (!fname.endsWith(".2dls")) srcPath += ".2dls";
            fileMgr.writeTextFile(srcPath, src);

            // Save compiled bytecode — replace .2dls extension with .2dlb
            // (String.replace(String,String) requires Java 1.5+, not available in J2ME 1.3)
            String bcPath;
            if (srcPath.endsWith(".2dls")) {
                bcPath = srcPath.substring(0, srcPath.length() - 5) + ".2dlb";
            } else {
                bcPath = srcPath + ".2dlb";
            }
            fileMgr.writeFile(bcPath, ScriptLang.serialize(res.bytecode));
        }

        // Notify editor
        if (callback != null) {
            callback.onScriptSaved(targetType, targetId, res.bytecode, src);
        }

        showStatus("OK! " + res.opCount + " ops compiled.", src, true);
    }

    private void doCheckOnly() {
        String src = tfSource.getString();
        ScriptLang.CompileResult res = ScriptLang.compile(src);
        if (res.bytecode == null) {
            showCompileError(res.error, res.errorLine, src);
        } else {
            showStatus("Syntax OK — " + res.opCount + " ops.", src, false);
        }
    }

    private void doDecompile() {
        String src = tfSource.getString();
        ScriptLang.CompileResult res = ScriptLang.compile(src);
        if (res.bytecode == null) {
            showCompileError(res.error, res.errorLine, src);
            return;
        }
        String dec = ScriptLang.decompile(res.bytecode);
        showStatus("Bytecode view:\n" + dec, src, false);
    }

    // =========================================================
    //  STATUS / ERROR SCREENS
    // =========================================================
    private void showCompileError(String msg, int line, final String src) {
        statusForm = new Form("Compile Error");
        String lineStr = line > 0 ? " (line " + line + ")" : "";
        statusForm.append(new StringItem("Error" + lineStr + ":", msg));
        statusForm.append(new StringItem("Tip:",
            "Check keyword spelling,\nmissing arguments,\nor undefined labels."));

        // Show the line in question if possible
        if (line > 0) {
            String[] lines = splitLines(src);
            if (line - 1 < lines.length) {
                statusForm.append(new StringItem("Near:", "\"" + lines[line-1].trim() + "\""));
            }
        }

        final String savedSrc = src;
        Command backToEdit = new Command("Edit", Command.BACK, 1);
        statusForm.addCommand(backToEdit);
        statusForm.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                currentSource = savedSrc;
                showEditorForm();
            }
        });
        midlet.showScreen(statusForm);
    }

    private void showStatus(String msg, final String src, final boolean saved) {
        statusForm = new Form(saved ? "Saved!" : "Check Result");
        statusForm.append(new StringItem("", msg));
        if (saved) {
            statusForm.append(new StringItem("Script attached to:",
                targetLabel() + " id=" + targetId));
        }
        Command backToEdit = new Command("Edit", Command.SCREEN, 2);
        Command done       = new Command(saved ? "Done" : "Back", Command.BACK, 1);
        statusForm.addCommand(done);
        statusForm.addCommand(backToEdit);
        statusForm.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getLabel().equals("Edit")) {
                    currentSource = src;
                    showEditorForm();
                } else {
                    // Return to editor canvas
                    midlet.showEditor();
                }
            }
        });
        midlet.showScreen(statusForm);
    }

    // =========================================================
    //  COMMAND DISPATCH
    // =========================================================
    public void commandAction(Command c, Displayable d) {
        if (d == editorForm) {
            if (c == cmdCompileSave) {
                doCompileSave();
            } else if (c == cmdCheck) {
                doCheckOnly();
            } else if (c == cmdTemplate) {
                showTemplatePicker();
            } else if (c == cmdOpen) {
                showFilePicker();
            } else if (c == cmdDecompile) {
                doDecompile();
            } else if (c == cmdCancel) {
                midlet.showEditor();
            }
        } else if (d == templateForm) {
            if (c == cmdOK) {
                int idx = cgTemplates.getSelectedIndex();
                applyTemplate(idx);
            } else {
                showEditorForm();
            }
        }
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private String targetLabel() {
        switch (targetType) {
            case TARGET_TILE: return "Tile";
            case TARGET_NPC:  return "NPC";
            case TARGET_MAP:  return "Map";
            default:          return "Event";
        }
    }

    private static String buildFileName(int type, int id) {
        switch (type) {
            case TARGET_TILE: return "tile_" + id + ".2dls";
            case TARGET_NPC:  return "npc_"  + id + ".2dls";
            case TARGET_MAP:  return "map_"  + id + ".2dls";
            default:          return "event_" + id + ".2dls";
        }
    }

    private static String defaultSource(int type, int id) {
        return "// Script for " + buildFileName(type, id) + "\n"
             + "text \"Hello from script!\"\n"
             + "end\n";
    }

    private void showAlert(String title, String msg, Displayable next) {
        Alert a = new Alert(title, msg, null, AlertType.INFO);
        a.setTimeout(3000);
        midlet.getDisplay().setCurrent(a, next);
    }

    private static String[] splitLines(String src) {
        Vector v = new Vector();
        int start = 0;
        for (int i = 0; i <= src.length(); i++) {
            if (i == src.length() || src.charAt(i) == '\n') {
                v.addElement(src.substring(start, i));
                start = i + 1;
            }
        }
        String[] arr = new String[v.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = (String) v.elementAt(i);
        return arr;
    }
}
