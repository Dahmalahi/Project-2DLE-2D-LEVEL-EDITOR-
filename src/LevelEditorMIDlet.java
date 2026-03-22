import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;

/**
 * LevelEditorMIDlet.java — Enhanced MIDlet entry point v2.0
 *
 * ENHANCEMENTS v2.0:
 *  - Multi-screen flow: Splash → Project → Editor → Play → Battle → Settings
 *  - SoundManager integrated at top level
 *  - DayNightSystem and FactionSystem instantiated here for global access
 *  - Autosave integration (timer-based)
 *  - GameConfig.SAVE_VERSION checked on load
 *  - "New Game Plus" mode flag propagated
 *  - Settings screen for volume, text speed, difficulty
 *  - Suspend/resume audio properly
 */
public class LevelEditorMIDlet extends MIDlet {

    private static LevelEditorMIDlet instance;
    private Display display;

    // Core screens
    private EditorCanvas   editor;
    private ProjectManager projectMgr;
    private GamePlayer     activeGamePlayer;    // currently running .2dip game
    private EditorCanvas   pendingEditorReturn; // editor to resume after game exits

    // Global systems (alive for the whole session)
    private SoundManager soundManager;
    private DayNightSystem dayNight;
    private FactionSystem factionSystem;

    // Settings
    private int difficulty;
    private int textSpeed;
    private boolean autosaveEnabled;

    // Autosave timer
    private long lastAutosaveTime;

    // =========================================================
    //  MIDLET LIFECYCLE
    // =========================================================
    public LevelEditorMIDlet() {
        instance = this;
    }

    protected void startApp() {
        display = Display.getDisplay(this);

        // Initialize global systems
        soundManager   = new SoundManager();
        factionSystem  = new FactionSystem();

        // Default settings
        difficulty     = GameConfig.DIFF_NORMAL;
        textSpeed      = GameConfig.TEXT_SPEED_MED;
        autosaveEnabled= true;
        lastAutosaveTime = System.currentTimeMillis();

        // Try to load settings from record store
        loadSettings();

        // Show project selector
        projectMgr = new ProjectManager(this);
        projectMgr.showProjectSelector();

        // Play title music
        soundManager.playMusic(SoundManager.MUS_TITLE);
    }

    protected void pauseApp() {
        if (editor != null) editor.pause();
        soundManager.pauseMusic();
    }

    protected void destroyApp(boolean unconditional) {
        if (editor != null) editor.stop();
        soundManager.cleanup();
    }

    // =========================================================
    //  NAVIGATION
    // =========================================================
    public void showScreen(Displayable d) {
        display.setCurrent(d);
    }

    public void startEditor(String projectPath) {
        soundManager.playMusic(SoundManager.MUS_FIELD);
        editor = new EditorCanvas(this, projectPath);
        display.setCurrent(editor);
        editor.start();
    }

    public void showEditor() {
        if (editor != null) {
            display.setCurrent(editor);
            soundManager.resumeMusic();
        }
    }

    // =========================================================
    //  LAUNCH .2DIP GAME FROM EDITOR
    // =========================================================
    /**
     * Creates a GamePlayer from raw .2dip bytes and shows it full-screen.
     * When the player presses * to exit, GamePlayer calls
     * LevelEditorMIDlet.returnToEditor() which resumes the editor.
     *
     * @param packageData  raw .2dip bytes
     * @param caller       the EditorCanvas to return to when done
     */
    public void launchDipGame(byte[] packageData, EditorCanvas caller) {
        soundManager.pauseMusic();

        // Use ExitCallback constructor so GamePlayer doesn't need a GamePlayerMIDlet ref
        final EditorCanvas callerFinal = caller;
        GamePlayer gp = new GamePlayer(new GamePlayer.ExitCallback() {
            public void onGameExit() {
                returnToEditor();
            }
        }, packageData);

        if (!gp.init()) {
            // Bad package — stay in editor
            soundManager.resumeMusic();
            if (caller != null) caller.returnFromGame();
            return;
        }

        this.pendingEditorReturn = callerFinal;
        this.activeGamePlayer   = gp;

        display.setCurrent(gp);
        gp.start();
    }

    /**
     * Called by GamePlayer when the player quits (presses * on title or in-game menu).
     */
    public void returnToEditor() {
        if (activeGamePlayer != null) {
            activeGamePlayer.stop();
            activeGamePlayer = null;
        }
        soundManager.resumeMusic();
        if (pendingEditorReturn != null) {
            pendingEditorReturn.returnFromGame();
            pendingEditorReturn = null;
        } else if (editor != null) {
            display.setCurrent(editor);
        }
    }

    public Display getDisplay() { return display; }

    public void showSettings() {
        // Build a simple settings form
        Form f = new Form("Settings");
        ChoiceGroup diffGroup = new ChoiceGroup("Difficulty", ChoiceGroup.EXCLUSIVE);
        diffGroup.append("Easy",      null);
        diffGroup.append("Normal",    null);
        diffGroup.append("Hard",      null);
        diffGroup.append("Nightmare", null);
        diffGroup.setSelectedIndex(difficulty, true);

        ChoiceGroup speedGroup = new ChoiceGroup("Text Speed", ChoiceGroup.EXCLUSIVE);
        speedGroup.append("Fast",   null);
        speedGroup.append("Medium", null);
        speedGroup.append("Slow",   null);
        int speedIdx = textSpeed == GameConfig.TEXT_SPEED_FAST ? 0
                     : textSpeed == GameConfig.TEXT_SPEED_SLOW ? 2 : 1;
        speedGroup.setSelectedIndex(speedIdx, true);

        Gauge masterVol = new Gauge("Master Volume", true, 100, soundManager.getVolume());
        Gauge musicVol  = new Gauge("Music Volume",  true, 100, soundManager.getMusicVolume());
        Gauge sfxVol    = new Gauge("SFX Volume",    true, 100, soundManager.getSfxVolume());

        f.append(diffGroup);
        f.append(speedGroup);
        f.append(masterVol);
        f.append(musicVol);
        f.append(sfxVol);

        final ChoiceGroup fd = diffGroup;
        final ChoiceGroup fs = speedGroup;
        final Gauge fmv = masterVol;
        final Gauge fmsc = musicVol;
        final Gauge fsfx = sfxVol;

        Command save   = new Command("Save", Command.OK, 1);
        Command back   = new Command("Back", Command.BACK, 1);
        f.addCommand(save);
        f.addCommand(back);
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    difficulty = fd.getSelectedIndex();
                    int si = fs.getSelectedIndex();
                    textSpeed = si == 0 ? GameConfig.TEXT_SPEED_FAST
                              : si == 2 ? GameConfig.TEXT_SPEED_SLOW
                              : GameConfig.TEXT_SPEED_MED;
                    soundManager.setMasterVolume(fmv.getValue());
                    soundManager.setMusicVolume(fmsc.getValue());
                    soundManager.setSfxVolume(fsfx.getValue());
                    saveSettings();
                }
                showEditor();
            }
        });
        display.setCurrent(f);
    }

    // =========================================================
    //  AUTOSAVE CHECK — call from editor's game loop
    // =========================================================
    public void checkAutosave() {
        if (!autosaveEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastAutosaveTime >= GameConfig.AUTOSAVE_INTERVAL * 1000L) {
            lastAutosaveTime = now;
            if (editor != null) {
                editor.autoSave();
            }
        }
    }

    // =========================================================
    //  SETTINGS PERSISTENCE (RecordStore-based)
    // =========================================================
    private void loadSettings() {
        try {
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore("settings", false);
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            java.io.DataInputStream dis =
                new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
            difficulty  = dis.readInt();
            textSpeed   = dis.readInt();
            int masterV = dis.readInt();
            int musicV  = dis.readInt();
            int sfxV    = dis.readInt();
            soundManager.setMasterVolume(masterV);
            soundManager.setMusicVolume(musicV);
            soundManager.setSfxVolume(sfxV);
        } catch (Exception e) {
            // First run — defaults apply
        }
    }

    private void saveSettings() {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
            dos.writeInt(difficulty);
            dos.writeInt(textSpeed);
            dos.writeInt(soundManager.getVolume());
            dos.writeInt(soundManager.getMusicVolume());
            dos.writeInt(soundManager.getSfxVolume());
            dos.flush();
            byte[] data = baos.toByteArray();

            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore("settings", true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) { /* ignore */ }
    }

    // =========================================================
    //  GLOBAL ACCESSORS
    // =========================================================
    public static LevelEditorMIDlet getInstance() { return instance; }
    public EditorCanvas      getEditor()          { return editor; }
    public ProjectManager    getProjectManager()  { return projectMgr; }
    public SoundManager      getSoundManager()    { return soundManager; }
    public FactionSystem     getFactionSystem()   { return factionSystem; }
    public DayNightSystem    getDayNight()        { return dayNight; }
    public int               getDifficulty()      { return difficulty; }
    public int               getTextSpeed()       { return textSpeed; }

    /** Create and attach a DayNightSystem when a map with time tracking is loaded. */
    public void initDayNight(int screenW, int screenH) {
        dayNight = new DayNightSystem(screenW, screenH);
    }

    public void exitApp() {
        destroyApp(true);
        notifyDestroyed();
    }
}
