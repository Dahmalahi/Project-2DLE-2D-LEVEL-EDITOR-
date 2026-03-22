public class EventSystem {

    // -------------------------------------------------
    //  EVENT COMMANDS
    // -------------------------------------------------
    public static final int CMD_END          = 0;
    public static final int CMD_TEXT         = 1;
    public static final int CMD_CHOICE       = 2;
    public static final int CMD_SET_SWITCH   = 3;
    public static final int CMD_SET_VAR      = 4;
    public static final int CMD_IF_SWITCH    = 5;
    public static final int CMD_IF_VAR       = 6;
    public static final int CMD_GOTO         = 7;
    public static final int CMD_GIVE_ITEM    = 8;
    public static final int CMD_TAKE_ITEM    = 9;
    public static final int CMD_GIVE_GOLD    = 10;
    public static final int CMD_TAKE_GOLD    = 11;
    public static final int CMD_GIVE_EXP     = 12;
    public static final int CMD_HEAL         = 13;
    public static final int CMD_TELEPORT     = 14;
    public static final int CMD_PLAY_SOUND   = 15;
    public static final int CMD_WAIT         = 16;
    public static final int CMD_MOVE_NPC     = 17;
    public static final int CMD_FACE_NPC     = 18;
    public static final int CMD_SHOW_PIC     = 19;
    public static final int CMD_HIDE_PIC     = 20;
    public static final int CMD_BATTLE       = 21;
    public static final int CMD_SHOP         = 22;
    public static final int CMD_INN          = 23;
    public static final int CMD_QUEST_START  = 24;
    public static final int CMD_QUEST_CHECK  = 25;
    public static final int CMD_LABEL        = 26;
    public static final int CMD_LOOP_START   = 27;
    public static final int CMD_LOOP_END     = 28;
    public static final int CMD_BREAK        = 29;
    // NEW commands
    public static final int CMD_SET_WEATHER  = 30;  // [weatherType, intensity]
    public static final int CMD_CRAFT_OPEN   = 31;  // open crafting menu
    public static final int CMD_SHOP_OPEN    = 32;  // [shopId]
    public static final int CMD_VAR_MATH     = 33;  // [varId, op, val]  op: 0=set,1=add,2=sub,3=mul,4=div,5=mod,6=rand
    public static final int CMD_CHECK_ITEM   = 34;  // [itemId, count, jumpIfNotEnough]
    public static final int CMD_CHECK_GOLD   = 35;  // [amount, jumpIfNotEnough]
    public static final int CMD_NPC_MOOD     = 36;  // [npcId, mood, duration]
    public static final int CMD_NPC_RELATION = 37;  // [npcId, delta]
    public static final int CMD_PLAY_CUTSCENE= 38;  // [sceneId]
    public static final int CMD_CHANGE_CLASS = 39;  // [classId]
    public static final int CMD_LEARN_SKILL  = 40;  // [skillId]
    public static final int CMD_QUEST_UPDATE = 41;  // [questId, progress]
    public static final int CMD_GIVE_SKILL_PT= 42;  // give skill points
    public static final int CMD_SAVE_POINT   = 43;  // trigger save prompt
    public static final int CMD_DAMAGE_PLAYER= 44;  // [amount]
    public static final int CMD_STATUS_EFFECT= 45;  // [statusId]
    public static final int CMD_CAMERA_SHAKE = 46;  // [intensity, duration]
    public static final int CMD_FADE_OUT     = 47;  // [speed]
    public static final int CMD_FADE_IN      = 48;  // [speed]

    // VAR_MATH operations
    public static final int MATH_SET  = 0;
    public static final int MATH_ADD  = 1;
    public static final int MATH_SUB  = 2;
    public static final int MATH_MUL  = 3;
    public static final int MATH_DIV  = 4;
    public static final int MATH_MOD  = 5;
    public static final int MATH_RAND = 6;  // var = random(0, val-1)

    // -------------------------------------------------
    //  GLOBAL SWITCHES & VARIABLES
    // -------------------------------------------------
    private boolean[] switches;
    private int[] variables;

    // Loop stack (up to 8 nested loops)
    private static final int MAX_LOOP_DEPTH = 8;
    private int[] loopStart;
    private int[] loopCount;
    private int[] loopMax;
    private int loopDepth;

    // -------------------------------------------------
    //  EVENT EXECUTION STATE
    // -------------------------------------------------
    private int[] currentScript;
    private int scriptIndex;
    private boolean isRunning;
    private boolean waitingForInput;
    private int waitTimer;

    // Choice state
    private String[] choiceOptions;
    private int choiceCount;
    private int choiceResult;
    private boolean inChoice;
    private int choiceSelection;    // for arrow-key navigation

    // Text state
    private String currentText;
    private int textCharIndex;
    private int textSpeed;
    private long lastTextTime;
    private String speakerName;     // NEW: NPC name shown in box

    // Screen effect state (consumed by renderer)
    public int pendingFadeSpeed;
    public boolean pendingFadeOut;
    public boolean pendingFadeIn;
    public int pendingShakeIntensity;
    public int pendingShakeDuration;

    // Callback
    private EventCallback callback;

    // Random seed (for MATH_RAND)
    private int randomSeed;

    // -------------------------------------------------
    //  INTERFACE
    // -------------------------------------------------
    public interface EventCallback {
        void onShowText(String speaker, String text);
        void onShowChoice(String[] options);
        void onGiveItem(int itemId, int count);
        void onTakeItem(int itemId, int count);
        boolean onCheckItem(int itemId, int count);
        boolean onCheckGold(int amount);
        void onTeleport(int mapId, int x, int y);
        void onStartBattle(int[] enemyTypes);
        void onPlaySound(int soundId);
        void onSetWeather(int weatherType, int intensity);
        void onOpenShop(int shopId);
        void onOpenCraft();
        void onOpenInn();
        void onPlayCutscene(int sceneId);
        void onChangeClass(int classId);
        void onLearnSkill(int skillId);
        void onUpdateQuest(int questId, int progress);
        void onGiveSkillPoints(int amount);
        void onSavePoint();
        void onDamagePlayer(int amount);
        void onApplyStatus(int statusId);
        void onMoveNPC(int npcId, int x, int y);
        void onFaceNPC(int npcId, int dir);
        void onSetNPCMood(int npcId, int mood, int duration);
        void onSetNPCRelation(int npcId, int delta);
        void onEventEnd();
    }

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public EventSystem() {
        switches = new boolean[GameConfig.MAX_SWITCHES];
        variables = new int[GameConfig.MAX_VARIABLES];
        loopStart = new int[MAX_LOOP_DEPTH];
        loopCount = new int[MAX_LOOP_DEPTH];
        loopMax   = new int[MAX_LOOP_DEPTH];
        loopDepth = 0;
        randomSeed = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
        reset();
    }

    public void reset() {
        scriptIndex = 0;
        isRunning = false;
        waitingForInput = false;
        waitTimer = 0;
        inChoice = false;
        choiceSelection = 0;
        currentText = "";
        textCharIndex = 0;
        speakerName = "";
        textSpeed = GameConfig.TEXT_SPEED_MED;
        loopDepth = 0;
        pendingFadeSpeed = 0;
        pendingFadeOut = false;
        pendingFadeIn = false;
        pendingShakeIntensity = 0;
        pendingShakeDuration = 0;
    }

    public void setCallback(EventCallback cb) { this.callback = cb; }

    // -------------------------------------------------
    //  SWITCH / VARIABLE ACCESS
    // -------------------------------------------------
    public boolean getSwitch(int id) {
        if (id < 0 || id >= GameConfig.MAX_SWITCHES) return false;
        return switches[id];
    }

    public void setSwitch(int id, boolean value) {
        if (id < 0 || id >= GameConfig.MAX_SWITCHES) return;
        switches[id] = value;
    }

    public int getVariable(int id) {
        if (id < 0 || id >= GameConfig.MAX_VARIABLES) return 0;
        return variables[id];
    }

    public void setVariable(int id, int value) {
        if (id < 0 || id >= GameConfig.MAX_VARIABLES) return;
        variables[id] = value;
    }

    public void addVariable(int id, int value) {
        if (id < 0 || id >= GameConfig.MAX_VARIABLES) return;
        variables[id] += value;
    }

    private void applyVarMath(int id, int op, int val) {
        if (id < 0 || id >= GameConfig.MAX_VARIABLES) return;
        switch (op) {
            case MATH_SET:  variables[id] = val; break;
            case MATH_ADD:  variables[id] += val; break;
            case MATH_SUB:  variables[id] -= val; break;
            case MATH_MUL:  variables[id] *= val; break;
            case MATH_DIV:  if (val != 0) variables[id] /= val; break;
            case MATH_MOD:  if (val != 0) variables[id] %= val; break;
            case MATH_RAND:
                randomSeed = nextRand(randomSeed);
                variables[id] = (val > 0) ? (randomSeed % val) : 0;
                break;
        }
    }

    // -------------------------------------------------
    //  SCRIPT EXECUTION
    // -------------------------------------------------
    public void runScript(int[] script) {
        currentScript = script;
        scriptIndex = 0;
        isRunning = true;
        waitingForInput = false;
        loopDepth = 0;
        speakerName = "";
        executeNext();
    }

    public void update() {
        if (!isRunning) return;

        // Wait timer
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        // Text animation
        if (currentText.length() > 0 && textCharIndex < currentText.length()) {
            long now = System.currentTimeMillis();
            if (now - lastTextTime >= textSpeed) {
                textCharIndex++;
                lastTextTime = now;
            }
            return;
        }

        if (waitingForInput) return;

        executeNext();
    }

    private void executeNext() {
        if (currentScript == null || scriptIndex >= currentScript.length) {
            endScript();
            return;
        }

        int cmd = currentScript[scriptIndex++];

        switch (cmd) {

            case CMD_END:
                endScript();
                break;

            case CMD_TEXT:
                int textId = currentScript[scriptIndex++];
                currentText = resolveText(textId);
                textCharIndex = 0;
                lastTextTime = System.currentTimeMillis();
                waitingForInput = true;
                if (callback != null) callback.onShowText(speakerName, currentText);
                break;

            case CMD_CHOICE:
                choiceCount = currentScript[scriptIndex++];
                choiceOptions = new String[choiceCount];
                for (int i = 0; i < choiceCount; i++) {
                    choiceOptions[i] = resolveText(currentScript[scriptIndex++]);
                }
                inChoice = true;
                waitingForInput = true;
                choiceSelection = 0;
                if (callback != null) callback.onShowChoice(choiceOptions);
                break;

            case CMD_SET_SWITCH:
                setSwitch(currentScript[scriptIndex++], currentScript[scriptIndex++] != 0);
                break;

            case CMD_SET_VAR:
                setVariable(currentScript[scriptIndex++], currentScript[scriptIndex++]);
                break;

            case CMD_VAR_MATH:
                applyVarMath(currentScript[scriptIndex++],
                             currentScript[scriptIndex++],
                             currentScript[scriptIndex++]);
                break;

            case CMD_IF_SWITCH: {
                int sid = currentScript[scriptIndex++];
                boolean expected = currentScript[scriptIndex++] != 0;
                int jump = currentScript[scriptIndex++];
                if (getSwitch(sid) != expected) scriptIndex = jump;
                break;
            }

            case CMD_IF_VAR: {
                int vid = currentScript[scriptIndex++];
                int op  = currentScript[scriptIndex++];
                int cmp = currentScript[scriptIndex++];
                int jmp = currentScript[scriptIndex++];
                int v = getVariable(vid);
                boolean met = false;
                switch (op) {
                    case 0: met = v == cmp; break;
                    case 1: met = v != cmp; break;
                    case 2: met = v >  cmp; break;
                    case 3: met = v <  cmp; break;
                    case 4: met = v >= cmp; break;
                    case 5: met = v <= cmp; break;
                }
                if (!met) scriptIndex = jmp;
                break;
            }

            case CMD_CHECK_ITEM: {
                int iid   = currentScript[scriptIndex++];
                int cnt   = currentScript[scriptIndex++];
                int jmpFail = currentScript[scriptIndex++];
                if (callback != null && !callback.onCheckItem(iid, cnt)) scriptIndex = jmpFail;
                break;
            }

            case CMD_CHECK_GOLD: {
                int amount = currentScript[scriptIndex++];
                int jmpFail = currentScript[scriptIndex++];
                if (callback != null && !callback.onCheckGold(amount)) scriptIndex = jmpFail;
                break;
            }

            case CMD_GOTO:
                scriptIndex = currentScript[scriptIndex];
                break;

            case CMD_LOOP_START:
                if (loopDepth < MAX_LOOP_DEPTH) {
                    loopMax[loopDepth]   = currentScript[scriptIndex++];
                    loopCount[loopDepth] = 0;
                    loopStart[loopDepth] = scriptIndex;
                    loopDepth++;
                } else {
                    scriptIndex++; // skip count, ignore
                }
                break;

            case CMD_LOOP_END:
                if (loopDepth > 0) {
                    int d = loopDepth - 1;
                    loopCount[d]++;
                    if (loopMax[d] == 0 || loopCount[d] < loopMax[d]) {
                        scriptIndex = loopStart[d]; // repeat
                    } else {
                        loopDepth--; // exit loop
                    }
                }
                break;

            case CMD_BREAK:
                if (loopDepth > 0) {
                    loopDepth--;
                    // Skip to matching CMD_LOOP_END
                    int depth = 1;
                    while (scriptIndex < currentScript.length && depth > 0) {
                        int nc = currentScript[scriptIndex++];
                        if (nc == CMD_LOOP_START) { depth++; scriptIndex++; }
                        else if (nc == CMD_LOOP_END) depth--;
                        else skipArgs(nc);
                    }
                }
                break;

            case CMD_GIVE_ITEM:
                if (callback != null) callback.onGiveItem(currentScript[scriptIndex++], currentScript[scriptIndex++]);
                break;

            case CMD_TAKE_ITEM:
                if (callback != null) callback.onTakeItem(currentScript[scriptIndex++], currentScript[scriptIndex++]);
                break;

            case CMD_GIVE_GOLD:
                addVariable(GameConfig.VAR_GOLD, currentScript[scriptIndex++]);
                break;

            case CMD_TAKE_GOLD:
                addVariable(GameConfig.VAR_GOLD, -currentScript[scriptIndex++]);
                break;

            case CMD_GIVE_EXP:
                addVariable(GameConfig.VAR_EXP, currentScript[scriptIndex++]);
                break;

            case CMD_HEAL:
                // Heal player to full; engine should check VAR_EXP etc.
                addVariable(GameConfig.VAR_HP, currentScript[scriptIndex++]);
                break;

            case CMD_DAMAGE_PLAYER:
                if (callback != null) callback.onDamagePlayer(currentScript[scriptIndex++]);
                break;

            case CMD_STATUS_EFFECT:
                if (callback != null) callback.onApplyStatus(currentScript[scriptIndex++]);
                break;

            case CMD_TELEPORT:
                if (callback != null) callback.onTeleport(
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++]);
                break;

            case CMD_WAIT:
                waitTimer = currentScript[scriptIndex++] / 33;
                break;

            case CMD_PLAY_SOUND:
                if (callback != null) callback.onPlaySound(currentScript[scriptIndex++]);
                break;

            case CMD_SET_WEATHER:
                if (callback != null) callback.onSetWeather(
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++]);
                break;

            case CMD_SHOP_OPEN:
                if (callback != null) callback.onOpenShop(currentScript[scriptIndex++]);
                waitingForInput = true;
                break;

            case CMD_INN:
                if (callback != null) callback.onOpenInn();
                waitingForInput = true;
                break;

            case CMD_CRAFT_OPEN:
                if (callback != null) callback.onOpenCraft();
                waitingForInput = true;
                break;

            case CMD_BATTLE:
                int ecount = currentScript[scriptIndex++];
                int[] etypes = new int[ecount];
                for (int i = 0; i < ecount; i++) etypes[i] = currentScript[scriptIndex++];
                if (callback != null) callback.onStartBattle(etypes);
                waitingForInput = true;
                break;

            case CMD_QUEST_START:
                if (callback != null) callback.onUpdateQuest(currentScript[scriptIndex++], 0);
                break;

            case CMD_QUEST_CHECK:
                // Just a marker; caller checks QuestSystem themselves
                scriptIndex++;
                break;

            case CMD_QUEST_UPDATE:
                if (callback != null) callback.onUpdateQuest(
                    currentScript[scriptIndex++], currentScript[scriptIndex++]);
                break;

            case CMD_MOVE_NPC:
                if (callback != null) callback.onMoveNPC(
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++]);
                break;

            case CMD_FACE_NPC:
                if (callback != null) callback.onFaceNPC(
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++]);
                break;

            case CMD_NPC_MOOD:
                if (callback != null) callback.onSetNPCMood(
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++]);
                break;

            case CMD_NPC_RELATION:
                if (callback != null) callback.onSetNPCRelation(
                    currentScript[scriptIndex++],
                    currentScript[scriptIndex++]);
                break;

            case CMD_PLAY_CUTSCENE:
                if (callback != null) callback.onPlayCutscene(currentScript[scriptIndex++]);
                waitingForInput = true;
                break;

            case CMD_CHANGE_CLASS:
                if (callback != null) callback.onChangeClass(currentScript[scriptIndex++]);
                break;

            case CMD_LEARN_SKILL:
                if (callback != null) callback.onLearnSkill(currentScript[scriptIndex++]);
                break;

            case CMD_GIVE_SKILL_PT:
                if (callback != null) callback.onGiveSkillPoints(currentScript[scriptIndex++]);
                break;

            case CMD_SAVE_POINT:
                if (callback != null) callback.onSavePoint();
                waitingForInput = true;
                break;

            case CMD_CAMERA_SHAKE:
                pendingShakeIntensity = currentScript[scriptIndex++];
                pendingShakeDuration  = currentScript[scriptIndex++];
                break;

            case CMD_FADE_OUT:
                pendingFadeSpeed = currentScript[scriptIndex++];
                pendingFadeOut = true;
                break;

            case CMD_FADE_IN:
                pendingFadeSpeed = currentScript[scriptIndex++];
                pendingFadeIn = true;
                break;

            case CMD_LABEL:
                // No-op marker; just used for GOTO targets
                scriptIndex++;
                break;

            default:
                // Unknown — skip 1 arg conservatively
                if (scriptIndex < currentScript.length) scriptIndex++;
                break;
        }
    }

    /** Skip argument words for unknown commands during loop scanning */
    private void skipArgs(int cmd) {
        switch (cmd) {
            case CMD_TEXT: case CMD_PLAY_SOUND: case CMD_SHOP_OPEN:
            case CMD_GIVE_GOLD: case CMD_TAKE_GOLD: case CMD_GIVE_EXP:
            case CMD_HEAL: case CMD_SAVE_POINT: case CMD_CRAFT_OPEN:
            case CMD_INN: case CMD_PLAY_CUTSCENE: case CMD_CHANGE_CLASS:
            case CMD_LEARN_SKILL: case CMD_GIVE_SKILL_PT: case CMD_DAMAGE_PLAYER:
            case CMD_STATUS_EFFECT: case CMD_LABEL: case CMD_QUEST_START:
            case CMD_FADE_OUT: case CMD_FADE_IN:
                scriptIndex += 1; break;
            case CMD_SET_SWITCH: case CMD_SET_VAR: case CMD_GIVE_ITEM:
            case CMD_TAKE_ITEM: case CMD_FACE_NPC: case CMD_NPC_RELATION:
            case CMD_QUEST_CHECK: case CMD_QUEST_UPDATE: case CMD_SET_WEATHER:
            case CMD_CAMERA_SHAKE:
                scriptIndex += 2; break;
            case CMD_IF_SWITCH: case CMD_MOVE_NPC: case CMD_NPC_MOOD:
            case CMD_VAR_MATH: case CMD_CHECK_ITEM: case CMD_TELEPORT:
                scriptIndex += 3; break;
            case CMD_IF_VAR: case CMD_CHECK_GOLD:
                scriptIndex += 4; break;
        }
    }

    private String resolveText(int textId) {
        // Placeholder; real impl loads from string database
        return "Text #" + textId;
    }

    // -------------------------------------------------
    //  INPUT HANDLING
    // -------------------------------------------------
    public void onConfirm() {
        if (!waitingForInput) return;
        if (inChoice) {
            setVariable(0, choiceSelection);
            choiceResult = choiceSelection;
            inChoice = false;
            waitingForInput = false;
        } else if (currentText.length() > 0) {
            if (textCharIndex < currentText.length()) {
                textCharIndex = currentText.length();
            } else {
                currentText = "";
                speakerName = "";
                waitingForInput = false;
            }
        } else {
            waitingForInput = false;
        }
    }

    public void onChoiceUp() {
        if (inChoice) {
            choiceSelection = (choiceSelection - 1 + choiceCount) % choiceCount;
        }
    }

    public void onChoiceDown() {
        if (inChoice) {
            choiceSelection = (choiceSelection + 1) % choiceCount;
        }
    }

    public void selectChoice(int index) {
        if (!inChoice) return;
        choiceResult = index;
        setVariable(0, index);
    }

    public void onBattleEnd()    { waitingForInput = false; }
    public void onShopClosed()   { waitingForInput = false; }
    public void onCraftClosed()  { waitingForInput = false; }
    public void onCutsceneEnd()  { waitingForInput = false; }
    public void onSaveComplete() { waitingForInput = false; }
    public void onInnClosed()    { waitingForInput = false; }

    private void endScript() {
        isRunning = false;
        currentScript = null;
        if (callback != null) callback.onEventEnd();
    }

    // -------------------------------------------------
    //  SAVE / LOAD SWITCHES & VARIABLES
    // -------------------------------------------------
    public byte[] saveSwitchesVars() throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        dos.writeInt(GameConfig.MAX_SWITCHES);
        for (int i = 0; i < GameConfig.MAX_SWITCHES; i++) dos.writeBoolean(switches[i]);
        dos.writeInt(GameConfig.MAX_VARIABLES);
        for (int i = 0; i < GameConfig.MAX_VARIABLES; i++) dos.writeInt(variables[i]);
        dos.flush();
        return baos.toByteArray();
    }

    public void loadSwitchesVars(byte[] data) throws java.io.IOException {
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
        java.io.DataInputStream dis = new java.io.DataInputStream(bais);
        int sc = dis.readInt();
        for (int i = 0; i < sc && i < GameConfig.MAX_SWITCHES; i++) switches[i] = dis.readBoolean();
        int vc = dis.readInt();
        for (int i = 0; i < vc && i < GameConfig.MAX_VARIABLES; i++) variables[i] = dis.readInt();
    }

    // -------------------------------------------------
    //  HELPERS & RANDOM
    // -------------------------------------------------
    private int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    // -------------------------------------------------
    //  GETTERS
    // -------------------------------------------------
    public boolean isRunning()       { return isRunning; }
    public boolean isWaiting()       { return waitingForInput; }
    public boolean isInChoice()      { return inChoice; }
    public String[] getChoiceOptions() { return choiceOptions; }
    public int getChoiceCount()      { return choiceCount; }
    public int getChoiceSelection()  { return choiceSelection; }
    public int getChoiceResult()     { return choiceResult; }

    public String getCurrentText() {
        if (currentText.length() == 0) return "";
        if (textCharIndex >= currentText.length()) return currentText;
        return currentText.substring(0, textCharIndex);
    }

    public String getSpeakerName()   { return speakerName; }
    public void setSpeakerName(String name) { this.speakerName = name; }
    public boolean isTextComplete()  { return textCharIndex >= currentText.length(); }
    public void setTextSpeed(int s)  { textSpeed = s; }
}
