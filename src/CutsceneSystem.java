import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

public class CutsceneSystem {

    // -------------------------------------------------
    //  COMMAND TYPES
    // -------------------------------------------------
    public static final int CMD_END           = 0;
    public static final int CMD_WAIT          = 1;
    public static final int CMD_FADE_IN       = 2;
    public static final int CMD_FADE_OUT      = 3;
    public static final int CMD_SHOW_TEXT     = 4;
    public static final int CMD_HIDE_TEXT     = 5;
    public static final int CMD_MOVE_CAMERA   = 6;
    public static final int CMD_MOVE_ACTOR    = 7;
    public static final int CMD_FACE_ACTOR    = 8;
    public static final int CMD_SHOW_ACTOR    = 9;
    public static final int CMD_HIDE_ACTOR    = 10;
    public static final int CMD_PLAY_SOUND    = 11;
    public static final int CMD_PLAY_MUSIC    = 12;
    public static final int CMD_STOP_MUSIC    = 13;
    public static final int CMD_SHAKE_SCREEN  = 14;
    public static final int CMD_FLASH_SCREEN  = 15;
    public static final int CMD_SET_WEATHER   = 16;
    public static final int CMD_WAIT_INPUT    = 17;
    public static final int CMD_JUMP          = 18;
    public static final int CMD_SET_SWITCH    = 19;
    public static final int CMD_IF_SWITCH     = 20;
    public static final int CMD_SHOW_PICTURE  = 21;
    public static final int CMD_HIDE_PICTURE  = 22;
    public static final int CMD_TINT_SCREEN   = 23;
    public static final int CMD_MOVE_PICTURE  = 24;
    public static final int CMD_CHOICE        = 25;

    // -------------------------------------------------
    //  ACTOR DATA (for cutscene characters)
    // -------------------------------------------------
    private static final int MAX_ACTORS = 8;
    
    private int[] actorX;
    private int[] actorY;
    private int[] actorTargetX;
    private int[] actorTargetY;
    private int[] actorDir;
    private int[] actorSprite;
    private int[] actorFrame;
    private boolean[] actorVisible;
    private boolean[] actorMoving;
    private int[] actorMoveSpeed;

    // -------------------------------------------------
    //  CAMERA
    // -------------------------------------------------
    private int cameraX;
    private int cameraY;
    private int cameraTargetX;
    private int cameraTargetY;
    private int cameraMoveSpeed;
    private boolean cameraMoving;

    // -------------------------------------------------
    //  TEXT BOX
    // -------------------------------------------------
    private String[] textLines;
    private int textLineCount;
    private String currentText;
    private int textCharIndex;
    private int textSpeed;
    private long lastTextTime;
    private boolean textVisible;
    private String speakerName;

    // -------------------------------------------------
    //  EFFECTS
    // -------------------------------------------------
    private int fadeLevel;        // 0-255
    private int fadeTarget;
    private int fadeSpeed;
    
    private int shakeIntensity;
    private int shakeDuration;
    private int shakeOffsetX;
    private int shakeOffsetY;
    
    private int flashColor;
    private int flashDuration;
    
    private int tintColor;
    private int tintAlpha;

    // -------------------------------------------------
    //  PICTURES
    // -------------------------------------------------
    private static final int MAX_PICTURES = 4;
    private int[] pictureId;
    private int[] pictureX;
    private int[] pictureY;
    private int[] pictureTargetX;
    private int[] pictureTargetY;
    private boolean[] pictureVisible;
    private boolean[] pictureMoving;

    // -------------------------------------------------
    //  SCRIPT EXECUTION
    // -------------------------------------------------
    private int[] script;
    private int scriptIndex;
    private boolean isRunning;
    private boolean waitingForInput;
    private int waitTimer;

    // Choice
    private boolean inChoice;
    private String[] choiceOptions;
    private int choiceCount;
    private int choiceSelection;

    // Screen size
    private int screenW;
    private int screenH;

    // Random seed
    private int randomSeed;

    // Callback
    private CutsceneCallback callback;

    // -------------------------------------------------
    //  INTERFACE
    // -------------------------------------------------
    public interface CutsceneCallback {
        void onCutsceneEnd();
        void onPlaySound(int soundId);
        void onPlayMusic(int musicId);
        void onStopMusic();
        void onSetSwitch(int switchId, boolean value);
        boolean onGetSwitch(int switchId);
        void onSetWeather(int weatherType, int intensity);
    }

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public CutsceneSystem(int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        
        actorX = new int[MAX_ACTORS];
        actorY = new int[MAX_ACTORS];
        actorTargetX = new int[MAX_ACTORS];
        actorTargetY = new int[MAX_ACTORS];
        actorDir = new int[MAX_ACTORS];
        actorSprite = new int[MAX_ACTORS];
        actorFrame = new int[MAX_ACTORS];
        actorVisible = new boolean[MAX_ACTORS];
        actorMoving = new boolean[MAX_ACTORS];
        actorMoveSpeed = new int[MAX_ACTORS];
        
        pictureId = new int[MAX_PICTURES];
        pictureX = new int[MAX_PICTURES];
        pictureY = new int[MAX_PICTURES];
        pictureTargetX = new int[MAX_PICTURES];
        pictureTargetY = new int[MAX_PICTURES];
        pictureVisible = new boolean[MAX_PICTURES];
        pictureMoving = new boolean[MAX_PICTURES];
        
        textLines = new String[4];
        
        randomSeed = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
        
        reset();
    }

    public void reset() {
        scriptIndex = 0;
        isRunning = false;
        waitingForInput = false;
        waitTimer = 0;
        
        fadeLevel = 0;
        fadeTarget = 0;
        shakeIntensity = 0;
        shakeDuration = 0;
        flashDuration = 0;
        tintAlpha = 0;
        
        textVisible = false;
        textLineCount = 0;
        currentText = "";
        textCharIndex = 0;
        textSpeed = GameConfig.TEXT_SPEED_MED;
        speakerName = "";
        
        inChoice = false;
        choiceCount = 0;
        choiceSelection = 0;
        
        cameraX = 0;
        cameraY = 0;
        cameraMoving = false;
        
        for (int i = 0; i < MAX_ACTORS; i++) {
            actorVisible[i] = false;
            actorMoving[i] = false;
            actorMoveSpeed[i] = 2;
        }
        
        for (int i = 0; i < MAX_PICTURES; i++) {
            pictureVisible[i] = false;
            pictureMoving[i] = false;
        }
    }

    public void setCallback(CutsceneCallback cb) {
        this.callback = cb;
    }

    // -------------------------------------------------
    //  PLAY SCENE
    // -------------------------------------------------
    public void playScene(int[] sceneScript) {
        reset();
        this.script = sceneScript;
        this.scriptIndex = 0;
        this.isRunning = true;
        
        // Start with fade from black (optional)
        fadeLevel = 255;
    }

    public void playScene(int sceneId) {
        // In real implementation, load script from database
        // For demo, create a simple test scene
        int[] demoScript = createDemoScene(sceneId);
        playScene(demoScript);
    }

    private int[] createDemoScene(int sceneId) {
        // Demo scene format
        switch (sceneId) {
            case 0:  // Intro scene
                return new int[]{
                    CMD_FADE_IN, 60,
                    CMD_WAIT, 30,
                    CMD_SHOW_TEXT, 0, // "Welcome to the world!"
                    CMD_WAIT_INPUT,
                    CMD_SHOW_TEXT, 1, // "Your adventure begins..."
                    CMD_WAIT_INPUT,
                    CMD_HIDE_TEXT,
                    CMD_FADE_OUT, 60,
                    CMD_END
                };
            case 1:  // Battle intro
                return new int[]{
                    CMD_FLASH_SCREEN, 0xFFFFFF, 10,
                    CMD_SHAKE_SCREEN, 5, 30,
                    CMD_SHOW_TEXT, 2, // "Enemy appears!"
                    CMD_WAIT, 60,
                    CMD_HIDE_TEXT,
                    CMD_END
                };
            default:
                return new int[]{CMD_END};
        }
    }

    // -------------------------------------------------
    //  UPDATE
    // -------------------------------------------------
    public void update() {
        if (!isRunning) return;
        
        // Update effects
        updateFade();
        updateShake();
        updateFlash();
        updateActors();
        updateCamera();
        updatePictures();
        updateText();
        
        // Update wait timer
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }
        
        // Check if waiting for input
        if (waitingForInput) {
            return;
        }
        
        // Continue script execution
        executeNext();
    }

    private void executeNext() {
        if (script == null || scriptIndex >= script.length) {
            endCutscene();
            return;
        }
        
        int cmd = script[scriptIndex++];
        
        switch (cmd) {
            case CMD_END:
                endCutscene();
                break;
                
            case CMD_WAIT:
                waitTimer = script[scriptIndex++];
                break;
                
            case CMD_FADE_IN:
                fadeTarget = 0;
                fadeSpeed = 255 / Math.max(1, script[scriptIndex++] / 2);
                break;
                
            case CMD_FADE_OUT:
                fadeTarget = 255;
                fadeSpeed = 255 / Math.max(1, script[scriptIndex++] / 2);
                break;
                
            case CMD_SHOW_TEXT:
                int textId = script[scriptIndex++];
                showText(getTextById(textId));
                break;
                
            case CMD_HIDE_TEXT:
                textVisible = false;
                break;
                
            case CMD_MOVE_CAMERA:
                cameraTargetX = script[scriptIndex++];
                cameraTargetY = script[scriptIndex++];
                cameraMoveSpeed = script[scriptIndex++];
                cameraMoving = true;
                break;
                
            case CMD_MOVE_ACTOR:
                int actorId = script[scriptIndex++];
                if (actorId >= 0 && actorId < MAX_ACTORS) {
                    actorTargetX[actorId] = script[scriptIndex++];
                    actorTargetY[actorId] = script[scriptIndex++];
                    actorMoveSpeed[actorId] = script[scriptIndex++];
                    actorMoving[actorId] = true;
                } else {
                    scriptIndex += 3;
                }
                break;
                
            case CMD_FACE_ACTOR:
                int faceId = script[scriptIndex++];
                if (faceId >= 0 && faceId < MAX_ACTORS) {
                    actorDir[faceId] = script[scriptIndex++];
                } else {
                    scriptIndex++;
                }
                break;
                
            case CMD_SHOW_ACTOR:
                int showId = script[scriptIndex++];
                if (showId >= 0 && showId < MAX_ACTORS) {
                    actorX[showId] = script[scriptIndex++];
                    actorY[showId] = script[scriptIndex++];
                    actorSprite[showId] = script[scriptIndex++];
                    actorVisible[showId] = true;
                } else {
                    scriptIndex += 3;
                }
                break;
                
            case CMD_HIDE_ACTOR:
                int hideId = script[scriptIndex++];
                if (hideId >= 0 && hideId < MAX_ACTORS) {
                    actorVisible[hideId] = false;
                }
                break;
                
            case CMD_PLAY_SOUND:
                if (callback != null) {
                    callback.onPlaySound(script[scriptIndex++]);
                } else {
                    scriptIndex++;
                }
                break;
                
            case CMD_PLAY_MUSIC:
                if (callback != null) {
                    callback.onPlayMusic(script[scriptIndex++]);
                } else {
                    scriptIndex++;
                }
                break;
                
            case CMD_STOP_MUSIC:
                if (callback != null) {
                    callback.onStopMusic();
                }
                break;
                
            case CMD_SHAKE_SCREEN:
                shakeIntensity = script[scriptIndex++];
                shakeDuration = script[scriptIndex++];
                break;
                
            case CMD_FLASH_SCREEN:
                flashColor = script[scriptIndex++];
                flashDuration = script[scriptIndex++];
                break;
                
            case CMD_SET_WEATHER:
                if (callback != null) {
                    int weatherType = script[scriptIndex++];
                    int intensity = script[scriptIndex++];
                    callback.onSetWeather(weatherType, intensity);
                } else {
                    scriptIndex += 2;
                }
                break;
                
            case CMD_WAIT_INPUT:
                waitingForInput = true;
                break;
                
            case CMD_JUMP:
                scriptIndex = script[scriptIndex];
                break;
                
            case CMD_SET_SWITCH:
                if (callback != null) {
                    int swId = script[scriptIndex++];
                    boolean swVal = script[scriptIndex++] != 0;
                    callback.onSetSwitch(swId, swVal);
                } else {
                    scriptIndex += 2;
                }
                break;
                
            case CMD_IF_SWITCH:
                if (callback != null) {
                    int checkId = script[scriptIndex++];
                    boolean checkVal = script[scriptIndex++] != 0;
                    int jumpAddr = script[scriptIndex++];
                    if (callback.onGetSwitch(checkId) != checkVal) {
                        scriptIndex = jumpAddr;
                    }
                } else {
                    scriptIndex += 3;
                }
                break;
                
            case CMD_SHOW_PICTURE:
                int picSlot = script[scriptIndex++];
                if (picSlot >= 0 && picSlot < MAX_PICTURES) {
                    pictureId[picSlot] = script[scriptIndex++];
                    pictureX[picSlot] = script[scriptIndex++];
                    pictureY[picSlot] = script[scriptIndex++];
                    pictureVisible[picSlot] = true;
                } else {
                    scriptIndex += 3;
                }
                break;
                
            case CMD_HIDE_PICTURE:
                int hidePic = script[scriptIndex++];
                if (hidePic >= 0 && hidePic < MAX_PICTURES) {
                    pictureVisible[hidePic] = false;
                }
                break;
                
            case CMD_TINT_SCREEN:
                tintColor = script[scriptIndex++];
                tintAlpha = script[scriptIndex++];
                break;
                
            case CMD_CHOICE:
                choiceCount = script[scriptIndex++];
                choiceOptions = new String[choiceCount];
                for (int i = 0; i < choiceCount; i++) {
                    int optId = script[scriptIndex++];
                    choiceOptions[i] = getTextById(optId);
                }
                inChoice = true;
                waitingForInput = true;
                choiceSelection = 0;
                break;
                
            default:
                // Unknown command
                break;
        }
    }

    // -------------------------------------------------
    //  EFFECT UPDATES
    // -------------------------------------------------
    private void updateFade() {
        if (fadeLevel < fadeTarget) {
            fadeLevel = Math.min(fadeTarget, fadeLevel + fadeSpeed);
        } else if (fadeLevel > fadeTarget) {
            fadeLevel = Math.max(fadeTarget, fadeLevel - fadeSpeed);
        }
    }

    private void updateShake() {
        if (shakeDuration > 0) {
            shakeDuration--;
            randomSeed = nextRand(randomSeed);
            shakeOffsetX = (randomSeed % (shakeIntensity * 2 + 1)) - shakeIntensity;
            randomSeed = nextRand(randomSeed);
            shakeOffsetY = (randomSeed % (shakeIntensity * 2 + 1)) - shakeIntensity;
        } else {
            shakeOffsetX = 0;
            shakeOffsetY = 0;
        }
    }

    private void updateFlash() {
        if (flashDuration > 0) {
            flashDuration--;
        }
    }

    private void updateActors() {
        for (int i = 0; i < MAX_ACTORS; i++) {
            if (!actorMoving[i]) continue;
            
            int dx = actorTargetX[i] - actorX[i];
            int dy = actorTargetY[i] - actorY[i];
            
            if (dx == 0 && dy == 0) {
                actorMoving[i] = false;
                continue;
            }
            
            // Move towards target
            int speed = actorMoveSpeed[i];
            if (Math.abs(dx) > speed) {
                actorX[i] += (dx > 0) ? speed : -speed;
            } else {
                actorX[i] = actorTargetX[i];
            }
            if (Math.abs(dy) > speed) {
                actorY[i] += (dy > 0) ? speed : -speed;
            } else {
                actorY[i] = actorTargetY[i];
            }
            
            // Update direction
            if (Math.abs(dx) > Math.abs(dy)) {
                actorDir[i] = (dx > 0) ? 3 : 2;
            } else {
                actorDir[i] = (dy > 0) ? 0 : 1;
            }
            
            // Animate
            actorFrame[i] = (actorFrame[i] + 1) % 4;
        }
    }

    private void updateCamera() {
        if (!cameraMoving) return;
        
        int dx = cameraTargetX - cameraX;
        int dy = cameraTargetY - cameraY;
        
        if (dx == 0 && dy == 0) {
            cameraMoving = false;
            return;
        }
        
        if (Math.abs(dx) > cameraMoveSpeed) {
            cameraX += (dx > 0) ? cameraMoveSpeed : -cameraMoveSpeed;
        } else {
            cameraX = cameraTargetX;
        }
        if (Math.abs(dy) > cameraMoveSpeed) {
            cameraY += (dy > 0) ? cameraMoveSpeed : -cameraMoveSpeed;
        } else {
            cameraY = cameraTargetY;
        }
    }

    private void updatePictures() {
        for (int i = 0; i < MAX_PICTURES; i++) {
            if (!pictureMoving[i]) continue;
            
            int dx = pictureTargetX[i] - pictureX[i];
            int dy = pictureTargetY[i] - pictureY[i];
            
            if (dx == 0 && dy == 0) {
                pictureMoving[i] = false;
                continue;
            }
            
            int speed = 2;
            if (Math.abs(dx) > speed) {
                pictureX[i] += (dx > 0) ? speed : -speed;
            } else {
                pictureX[i] = pictureTargetX[i];
            }
            if (Math.abs(dy) > speed) {
                pictureY[i] += (dy > 0) ? speed : -speed;
            } else {
                pictureY[i] = pictureTargetY[i];
            }
        }
    }

    private void updateText() {
        if (!textVisible || currentText == null) return;
        
        long now = System.currentTimeMillis();
        if (now - lastTextTime >= textSpeed) {
            if (textCharIndex < currentText.length()) {
                textCharIndex++;
            }
            lastTextTime = now;
        }
    }

    // -------------------------------------------------
    //  TEXT HANDLING
    // -------------------------------------------------
    private void showText(String text) {
        currentText = text;
        textCharIndex = 0;
        textVisible = true;
        lastTextTime = System.currentTimeMillis();
        
        // Split into lines
        splitTextIntoLines(text);
    }

    private void splitTextIntoLines(String text) {
        textLineCount = 0;
        int maxWidth = screenW - 20;
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        
        StringBuffer line = new StringBuffer();
        int start = 0;
        
        for (int i = 0; i <= text.length() && textLineCount < 4; i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                if (line.length() > 0) {
                    textLines[textLineCount++] = line.toString();
                }
                line = new StringBuffer();
            } else {
                line.append(text.charAt(i));
                if (font.stringWidth(line.toString()) > maxWidth) {
                    // Word wrap
                    int lastSpace = line.toString().lastIndexOf(' ');
                    if (lastSpace > 0) {
                        textLines[textLineCount++] = line.toString().substring(0, lastSpace);
                        line = new StringBuffer(line.toString().substring(lastSpace + 1));
                    }
                }
            }
        }
    }

    private String getTextById(int textId) {
        // In real implementation, load from database
        switch (textId) {
            case 0: return "Welcome to the world of adventure!";
            case 1: return "Your journey begins now...";
            case 2: return "A monster appears!";
            case 3: return "You found a treasure chest!";
            case 4: return "The door is locked.";
            default: return "...";
        }
    }

    // -------------------------------------------------
    //  INPUT
    // -------------------------------------------------
    public void handleInput(int keyCode) {
        if (!isRunning) return;
        
        if (inChoice) {
            switch (keyCode) {
                case -1: // UP
                    choiceSelection--;
                    if (choiceSelection < 0) choiceSelection = choiceCount - 1;
                    break;
                case -2: // DOWN
                    choiceSelection++;
                    if (choiceSelection >= choiceCount) choiceSelection = 0;
                    break;
                case -5: // FIRE
                    inChoice = false;
                    waitingForInput = false;
                    // Choice result stored in choiceSelection
                    break;
            }
            return;
        }
        
        if (waitingForInput) {
            if (keyCode == -5) {  // FIRE
                if (textVisible && textCharIndex < currentText.length()) {
                    // Show all text instantly
                    textCharIndex = currentText.length();
                } else {
                    waitingForInput = false;
                }
            }
        }
    }

    // -------------------------------------------------
    //  RENDER
    // -------------------------------------------------
    public void render(Graphics g) {
        // Apply shake offset
        g.translate(shakeOffsetX, shakeOffsetY);
        
        // Draw actors
        for (int i = 0; i < MAX_ACTORS; i++) {
            if (actorVisible[i]) {
                drawActor(g, i);
            }
        }
        
        // Draw pictures
        for (int i = 0; i < MAX_PICTURES; i++) {
            if (pictureVisible[i]) {
                drawPicture(g, i);
            }
        }
        
        // Draw text box
        if (textVisible) {
            drawTextBox(g);
        }
        
        // Draw choice menu
        if (inChoice) {
            drawChoiceMenu(g);
        }
        
        // Draw fade overlay
        if (fadeLevel > 0) {
            drawFade(g);
        }
        
        // Draw flash
        if (flashDuration > 0) {
            drawFlash(g);
        }
        
        // Draw tint
        if (tintAlpha > 0) {
            drawTint(g);
        }
        
        // Reset translation
        g.translate(-shakeOffsetX, -shakeOffsetY);
    }

    private void drawActor(Graphics g, int id) {
        int x = actorX[id] - cameraX;
        int y = actorY[id] - cameraY;
        
        // Simple colored rectangle for actor
        int[] colors = {0x00FF00, 0x0000FF, 0xFF0000, 0xFFFF00, 
                        0xFF00FF, 0x00FFFF, 0xFFAA00, 0xAA00FF};
        g.setColor(colors[actorSprite[id] % colors.length]);
        g.fillRect(x - 8, y - 16, 16, 24);
        
        // Direction indicator
        g.setColor(0xFFFFFF);
        int dx = 0, dy = 0;
        switch (actorDir[id]) {
            case 0: dy = 4; break;
            case 1: dy = -4; break;
            case 2: dx = -4; break;
            case 3: dx = 4; break;
        }
        g.fillRect(x + dx - 2, y + dy - 2, 4, 4);
    }

    private void drawPicture(Graphics g, int id) {
        int x = pictureX[id];
        int y = pictureY[id];
        
        // Placeholder rectangle
        g.setColor(0x444488);
        g.fillRect(x, y, 64, 48);
        g.setColor(0x8888FF);
        g.drawRect(x, y, 63, 47);
        
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);
        g.setColor(0xFFFFFF);
        g.drawString("PIC " + pictureId[id], x + 32, y + 20, 
                     Graphics.TOP | Graphics.HCENTER);
    }

    private void drawTextBox(Graphics g) {
        int boxH = 60;
        int boxY = screenH - boxH - 5;
        
        // Box background
        g.setColor(0x000033);
        g.fillRect(5, boxY, screenW - 10, boxH);
        g.setColor(0x4466AA);
        g.drawRect(5, boxY, screenW - 11, boxH - 1);
        g.drawRect(6, boxY + 1, screenW - 13, boxH - 3);
        
        // Speaker name
        if (speakerName.length() > 0) {
            g.setColor(0xFFFF00);
            Font fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setFont(fontBold);
            g.drawString(speakerName, 12, boxY + 4, Graphics.TOP | Graphics.LEFT);
        }
        
        // Text
        g.setColor(0xFFFFFF);
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);
        
        int textY = boxY + (speakerName.length() > 0 ? 18 : 8);
        int charCount = 0;
        
        for (int i = 0; i < textLineCount; i++) {
            String line = textLines[i];
            int lineLen = line.length();
            
            if (charCount + lineLen <= textCharIndex) {
                g.drawString(line, 12, textY, Graphics.TOP | Graphics.LEFT);
            } else if (charCount < textCharIndex) {
                int showChars = textCharIndex - charCount;
                g.drawString(line.substring(0, showChars), 12, textY, 
                             Graphics.TOP | Graphics.LEFT);
            }
            
            charCount += lineLen;
            textY += font.getHeight() + 2;
        }
        
        // Continue indicator
        if (textCharIndex >= currentText.length()) {
            int blinkX = screenW - 20;
            int blinkY = boxY + boxH - 12;
            if ((System.currentTimeMillis() / 300) % 2 == 0) {
                g.setColor(0xFFFF00);
                g.fillRect(blinkX, blinkY, 8, 8);
            }
        }
    }

    private void drawChoiceMenu(Graphics g) {
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int itemH = font.getHeight() + 4;
        int menuH = choiceCount * itemH + 10;
        int menuW = 100;
        int menuX = screenW - menuW - 10;
        int menuY = screenH - 70 - menuH;
        
        // Background
        g.setColor(0x002244);
        g.fillRect(menuX, menuY, menuW, menuH);
        g.setColor(0x4488AA);
        g.drawRect(menuX, menuY, menuW - 1, menuH - 1);
        
        g.setFont(font);
        
        int y = menuY + 5;
        for (int i = 0; i < choiceCount; i++) {
            if (i == choiceSelection) {
                g.setColor(0x446688);
                g.fillRect(menuX + 2, y - 1, menuW - 4, itemH);
                g.setColor(0xFFFF00);
            } else {
                g.setColor(0xCCCCCC);
            }
            
            g.drawString(choiceOptions[i], menuX + 10, y, 
                         Graphics.TOP | Graphics.LEFT);
            y += itemH;
        }
    }

    private void drawFade(Graphics g) {
        g.setColor(0x000000);
        // Dithered fade (J2ME has no alpha)
        int skip = 8 - (fadeLevel / 32);
        if (skip < 1) skip = 1;
        
        for (int y = 0; y < screenH; y += skip) {
            for (int x = (y / skip) % skip; x < screenW; x += skip) {
                g.drawLine(x, y, x, y);
            }
        }
    }

    private void drawFlash(Graphics g) {
        g.setColor(flashColor);
        int alpha = flashDuration * 30;
        if (alpha > 255) alpha = 255;
        
        int skip = 4 - (alpha / 80);
        if (skip < 1) skip = 1;
        
        for (int y = 0; y < screenH; y += skip) {
            for (int x = (y / skip) % skip; x < screenW; x += skip) {
                g.drawLine(x, y, x, y);
            }
        }
    }

    private void drawTint(Graphics g) {
        g.setColor(tintColor);
        int skip = 4 - (tintAlpha / 40);
        if (skip < 1) skip = 1;
        
        for (int y = 0; y < screenH; y += skip) {
            for (int x = (y / skip) % skip; x < screenW; x += skip) {
                g.drawLine(x, y, x, y);
            }
        }
    }

    // -------------------------------------------------
    //  END CUTSCENE
    // -------------------------------------------------
    private void endCutscene() {
        isRunning = false;
        script = null;
        if (callback != null) {
            callback.onCutsceneEnd();
        }
    }

    // -------------------------------------------------
    //  HELPERS
    // -------------------------------------------------
    private int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getCameraX() {
        return cameraX;
    }

    public int getCameraY() {
        return cameraY;
    }

    public int getShakeOffsetX() {
        return shakeOffsetX;
    }

    public int getShakeOffsetY() {
        return shakeOffsetY;
    }

    public int getChoiceResult() {
        return choiceSelection;
    }

    public void setScreenSize(int w, int h) {
        this.screenW = w;
        this.screenH = h;
    }

    public void setSpeaker(String name) {
        this.speakerName = name;
    }

    public void setTextSpeed(int speed) {
        this.textSpeed = speed;
    }
}