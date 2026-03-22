import javax.microedition.lcdui.*;

public class DialogueEditor implements CommandListener {

    private LevelEditorMIDlet midlet;
    private NPCManager npcMgr;
    private int npcId;
    private int dialogueId;

    private Form form;
    private TextField[] lineFields;
    private int lineCount;
    private static final int MAX_LINES = 8;

    private Command cmdSave;
    private Command cmdAddLine;
    private Command cmdCancel;

    public DialogueEditor(LevelEditorMIDlet midlet, NPCManager npcMgr) {
        this.midlet = midlet;
        this.npcMgr = npcMgr;

        cmdSave = new Command("Save", Command.OK, 1);
        cmdAddLine = new Command("Add Line", Command.SCREEN, 2);
        cmdCancel = new Command("Cancel", Command.BACK, 1);
    }

    public void editNPCDialogue(int npcId) {
        this.npcId = npcId;
        this.dialogueId = npcMgr.npcDialogueId[npcId];

        form = new Form("Edit Dialogue");
        form.addCommand(cmdSave);
        form.addCommand(cmdAddLine);
        form.addCommand(cmdCancel);
        form.setCommandListener(this);

        lineFields = new TextField[MAX_LINES];
        lineCount = 0;

        // Load existing dialogue
        if (dialogueId >= 0) {
            String[] lines = npcMgr.getDialogue(dialogueId);
            if (lines != null) {
                for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
                    addLineField(lines[i]);
                }
            }
        }

        // At least one empty line
        if (lineCount == 0) {
            addLineField("");
        }

        midlet.showScreen(form);
    }

    private void addLineField(String text) {
        if (lineCount >= MAX_LINES) return;

        TextField tf = new TextField("Line " + (lineCount + 1),
                                     text, 128, TextField.ANY);
        lineFields[lineCount] = tf;
        form.append(tf);
        lineCount++;
    }

    private void saveDialogue() {
        // Collect non-empty lines
        int count = 0;
        for (int i = 0; i < lineCount; i++) {
            if (lineFields[i].getString().trim().length() > 0) {
                count++;
            }
        }

        if (count == 0) {
            // Remove dialogue
            npcMgr.npcDialogueId[npcId] = -1;
        } else {
            String[] lines = new String[count];
            int idx = 0;
            for (int i = 0; i < lineCount; i++) {
                String line = lineFields[i].getString().trim();
                if (line.length() > 0) {
                    lines[idx++] = line;
                }
            }

            if (dialogueId >= 0) {
                npcMgr.setDialogue(dialogueId, lines);
            } else {
                dialogueId = npcMgr.addDialogue(lines);
                npcMgr.npcDialogueId[npcId] = dialogueId;
            }
        }

        midlet.showEditor();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdSave) {
            saveDialogue();
        } else if (c == cmdAddLine) {
            addLineField("");
        } else if (c == cmdCancel) {
            midlet.showEditor();
        }
    }
}