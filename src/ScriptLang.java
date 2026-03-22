import java.util.Vector;

/**
 * ScriptLang.java  — 2DLE Script Language Compiler  v1.0
 *
 * Compiles human-readable .2dls text scripts into int[] bytecode
 * that the EventSystem can execute directly.
 *
 * ── LANGUAGE REFERENCE ──────────────────────────────────────────────────────
 *
 * COMMENTS        // single-line comment
 *
 * TEXT            text "Hello World!"          show dialogue box
 * CHOICE          choice 2 "Yes" "No"          show 2-option menu
 * WAIT            wait 60                      wait N frames (~60 = 1 sec)
 *
 * VARIABLES
 *   set var 5 10                               var[5] = 10
 *   add var 5 3                                var[5] += 3
 *   sub var 5 1                                var[5] -= 1
 *   mul var 5 2                                var[5] *= 2
 *   div var 5 2                                var[5] /= 2
 *   mod var 5 3                                var[5] %= 3
 *   rnd var 5 100                              var[5] = random(0..99)
 *
 * SWITCHES
 *   set switch 3 on                            switch[3] = true
 *   set switch 3 off                           switch[3] = false
 *
 * CONDITIONALS
 *   if switch 3 on  goto label_name            branch if switch[3] == true
 *   if switch 3 off goto label_name
 *   if var 5 == 10  goto label_name            == != > < >= <=
 *   if var 5 > 0    goto label_name
 *   if hasitem 10 2 goto label_name            branch if item[10] count >= 2
 *   if gold >= 500  goto label_name
 *
 * LABELS & GOTO
 *   label loop_start                           define a jump target
 *   goto  loop_start                           unconditional jump
 *
 * LOOPS
 *   loop                                       loop start
 *     ...
 *   break                                      exit innermost loop
 *   endloop                                    loop end (jumps back to loop)
 *
 * ITEMS / GOLD / EXP
 *   give item 0 3                              give 3× item[0] (Potion)
 *   take item 0 1                              remove 1× item[0]
 *   give gold 500
 *   take gold 200
 *   give exp 100
 *
 * PLAYER
 *   heal                                       full restore
 *   damage 25                                  deal 25 HP
 *   status 1                                   apply status effect id 1
 *   learnSkill 4                               learn skill id 4
 *   giveSkillPt                                give 1 skill point
 *   changeClass 2                              change player class to id 2
 *
 * WORLD
 *   teleport 2 10 5                            warp to map 2, x10 y5
 *   weather 1 80                               weather type 1, intensity 80
 *   shake 3 30                                 screen shake intensity 3, 30 frames
 *   fadeout 4                                  fade out at speed 4
 *   fadein  4                                  fade in at speed 4
 *
 * NPC
 *   movenpc 0 3 2                              move npc[0] to x3,y2
 *   facenpc 0 1                                face npc[0] direction 1
 *   moodNpc 0 2 60                             npc[0] mood 2 for 60 frames
 *   repNpc  0 5                                npc[0] reputation +5
 *
 * AUDIO
 *   sound 3                                    play sound id 3
 *   music 2                                    play music id 2
 *
 * GAME FLOW
 *   battle 2 0 3                               start battle with 2 enemies: type 0, type 3
 *   shop 1                                     open shop id 1
 *   inn                                        open inn screen
 *   craft                                      open crafting screen
 *   savepoint                                  trigger save prompt
 *   cutscene 2                                 play cutscene id 2
 *
 * QUESTS
 *   startQuest 5                               start quest id 5
 *   checkQuest 5 10                            jump to label if quest 5 done
 *   updateQuest 5 1                            add 1 progress to quest 5
 *
 * end                                          terminate script
 *
 * ── EXAMPLE SCRIPT ──────────────────────────────────────────────────────────
 *
 *   // Guard at the castle gate
 *   if switch 0 on goto already_met
 *   text "Halt! State your business."
 *   choice 2 "I seek the king." "Just passing."
 *   if var 0 == 0 goto seek_king
 *   text "Move along then."
 *   goto done
 *
 *   label seek_king
 *   text "Very well. The king awaits."
 *   set switch 0 on
 *   give exp 50
 *   teleport 1 5 3
 *   goto done
 *
 *   label already_met
 *   text "Welcome back, traveller."
 *
 *   label done
 *   end
 *
 * ────────────────────────────────────────────────────────────────────────────
 */
public class ScriptLang {

    // =========================================================
    //  COMPILE RESULT
    // =========================================================
    public static class CompileResult {
        public int[]   bytecode;    // null on error
        public String  error;       // null on success
        public int     errorLine;   // 1-based, -1 if unknown
        public int     opCount;     // number of opcodes generated

        CompileResult(int[] bc, int ops) {
            bytecode  = bc;
            opCount   = ops;
            errorLine = -1;
        }
        CompileResult(String err, int line) {
            error     = err;
            errorLine = line;
            bytecode  = null;
        }
    }

    // =========================================================
    //  COMPILE
    // =========================================================
    /**
     * Compiles a script source string to int[] bytecode.
     * Returns a CompileResult with either bytecode or an error message.
     */
    public static CompileResult compile(String source) {
        if (source == null || source.length() == 0) {
            int[] bc = new int[]{ EventSystem.CMD_END };
            return new CompileResult(bc, 1);
        }

        // ── Pass 1: tokenise lines ──────────────────────────
        String[] rawLines = splitLines(source);
        Vector tokens = new Vector();  // Vector of String[]
        Vector lineNums = new Vector(); // line number per token-group

        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i].trim();
            // Strip comment
            int ci = line.indexOf("//");
            if (ci >= 0) line = line.substring(0, ci).trim();
            if (line.length() == 0) continue;
            String[] parts = tokeniseLine(line);
            if (parts.length == 0) continue;
            tokens.addElement(parts);
            lineNums.addElement(new Integer(i + 1));
        }

        // ── Pass 2: collect label positions ─────────────────
        // We need two passes: first to know label byte positions.
        // Strategy: emit bytecode with placeholder addresses,
        // then back-patch on second pass.

        // Label name → placeholder index in output array
        Vector labelNames   = new Vector();  // String
        Vector labelPatches = new Vector();  // Integer (index in out where address goes)
        Vector labelDefs    = new Vector();  // Integer (output index at definition point)

        // Loop stack: each entry = Integer index in output where loop-start address lives
        Vector loopStack = new Vector();

        // Output buffer
        int[] out = new int[4096];
        int   pos = 0;

        for (int t = 0; t < tokens.size(); t++) {
            String[] p = (String[]) tokens.elementAt(t);
            int lineNum = ((Integer) lineNums.elementAt(t)).intValue();
            String kw = p[0].toLowerCase();

            try {
                // ── end ──────────────────────────────────────
                if (kw.equals("end")) {
                    out[pos++] = EventSystem.CMD_END;

                // ── text ─────────────────────────────────────
                } else if (kw.equals("text")) {
                    requireArgs(p, 1, "text", lineNum);
                    out[pos++] = EventSystem.CMD_TEXT;
                    out[pos++] = internString(p[1], labelNames);

                // ── choice ───────────────────────────────────
                } else if (kw.equals("choice")) {
                    requireArgs(p, 2, "choice", lineNum);
                    int n = parseInt(p[1], lineNum);
                    out[pos++] = EventSystem.CMD_CHOICE;
                    out[pos++] = n;
                    for (int i = 0; i < n; i++) {
                        int idx = 2 + i;
                        if (idx >= p.length) {
                            return new CompileResult(
                                "choice: expected " + n + " option strings", lineNum);
                        }
                        out[pos++] = internString(p[idx], labelNames);
                    }

                // ── wait ─────────────────────────────────────
                } else if (kw.equals("wait")) {
                    requireArgs(p, 1, "wait", lineNum);
                    out[pos++] = EventSystem.CMD_WAIT;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── set ──────────────────────────────────────
                } else if (kw.equals("set")) {
                    requireArgs(p, 3, "set", lineNum);
                    String target = p[1].toLowerCase();
                    if (target.equals("var")) {
                        out[pos++] = EventSystem.CMD_VAR_MATH;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = 0; // op=SET
                        out[pos++] = parseInt(p[3], lineNum);
                    } else if (target.equals("switch")) {
                        out[pos++] = EventSystem.CMD_SET_SWITCH;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = p[3].equalsIgnoreCase("on") ? 1 : 0;
                    } else {
                        return new CompileResult("set: unknown target '" + target + "'", lineNum);
                    }

                // ── add / sub / mul / div / mod / rnd ────────
                } else if (kw.equals("add") || kw.equals("sub") || kw.equals("mul")
                        || kw.equals("div") || kw.equals("mod") || kw.equals("rnd")) {
                    requireArgs(p, 3, kw, lineNum);
                    String target = p[1].toLowerCase();
                    if (!target.equals("var")) {
                        return new CompileResult(kw + ": expected 'var'", lineNum);
                    }
                    int op = kw.equals("add") ? 1
                           : kw.equals("sub") ? 2
                           : kw.equals("mul") ? 3
                           : kw.equals("div") ? 4
                           : kw.equals("mod") ? 5 : 6;
                    out[pos++] = EventSystem.CMD_VAR_MATH;
                    out[pos++] = parseInt(p[2], lineNum);
                    out[pos++] = op;
                    out[pos++] = parseInt(p[3], lineNum);

                // ── if ───────────────────────────────────────
                } else if (kw.equals("if")) {
                    requireArgs(p, 4, "if", lineNum);
                    String subject = p[1].toLowerCase();

                    if (subject.equals("switch")) {
                        // if switch <id> on|off goto <label>
                        requireArgs(p, 4, "if switch", lineNum);
                        out[pos++] = EventSystem.CMD_IF_SWITCH;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = p[3].equalsIgnoreCase("on") ? 1 : 0;
                        // jump address (placeholder)
                        String lbl = p.length > 4 && p[4].equalsIgnoreCase("goto")
                                   ? (p.length > 5 ? p[5] : p[4]) : p[4];
                        registerPatch(labelNames, labelPatches, pos, lbl);
                        out[pos++] = 0; // placeholder

                    } else if (subject.equals("var")) {
                        // if var <id> <op> <val> goto <label>
                        requireArgs(p, 6, "if var", lineNum);
                        out[pos++] = EventSystem.CMD_IF_VAR;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = parseOp(p[3], lineNum);
                        out[pos++] = parseInt(p[4], lineNum);
                        String lbl = p[5].equalsIgnoreCase("goto")
                                   ? (p.length > 6 ? p[6] : p[5]) : p[5];
                        registerPatch(labelNames, labelPatches, pos, lbl);
                        out[pos++] = 0; // placeholder

                    } else if (subject.equals("hasitem")) {
                        // if hasitem <itemId> <count> goto <label>
                        requireArgs(p, 5, "if hasitem", lineNum);
                        out[pos++] = EventSystem.CMD_CHECK_ITEM;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = parseInt(p[3], lineNum);
                        String lbl = p[4].equalsIgnoreCase("goto")
                                   ? (p.length > 5 ? p[5] : p[4]) : p[4];
                        registerPatch(labelNames, labelPatches, pos, lbl);
                        out[pos++] = 0;

                    } else if (subject.equals("gold")) {
                        // if gold >= <amount> goto <label>
                        requireArgs(p, 5, "if gold", lineNum);
                        out[pos++] = EventSystem.CMD_CHECK_GOLD;
                        out[pos++] = parseOp(p[2], lineNum);
                        out[pos++] = parseInt(p[3], lineNum);
                        String lbl = p[4].equalsIgnoreCase("goto")
                                   ? (p.length > 5 ? p[5] : p[4]) : p[4];
                        registerPatch(labelNames, labelPatches, pos, lbl);
                        out[pos++] = 0;

                    } else {
                        return new CompileResult("if: unknown subject '" + subject + "'", lineNum);
                    }

                // ── label ────────────────────────────────────
                } else if (kw.equals("label")) {
                    requireArgs(p, 1, "label", lineNum);
                    String name = p[1];
                    // Record definition: name → current pos
                    // Check duplicate
                    for (int i = 0; i < labelNames.size(); i++) {
                        if (labelNames.elementAt(i) instanceof String[]) continue;
                        // labelNames stores patch entries as String[] {name, isString}
                        // and label defs as just String. Use parallel structure:
                    }
                    // Store label def: name+":def" → pos
                    labelNames.addElement(name + ":def");
                    labelDefs.addElement(new Integer(pos));

                // ── goto ─────────────────────────────────────
                } else if (kw.equals("goto")) {
                    requireArgs(p, 1, "goto", lineNum);
                    out[pos++] = EventSystem.CMD_GOTO;
                    registerPatch(labelNames, labelPatches, pos, p[1]);
                    out[pos++] = 0; // placeholder

                // ── loop ─────────────────────────────────────
                } else if (kw.equals("loop")) {
                    loopStack.addElement(new Integer(pos));
                    out[pos++] = EventSystem.CMD_LOOP_START;

                // ── endloop ──────────────────────────────────
                } else if (kw.equals("endloop")) {
                    if (loopStack.size() == 0) {
                        return new CompileResult("endloop without loop", lineNum);
                    }
                    int loopStart = ((Integer) loopStack.elementAt(loopStack.size() - 1)).intValue();
                    loopStack.removeElementAt(loopStack.size() - 1);
                    out[pos++] = EventSystem.CMD_LOOP_END;
                    out[pos++] = loopStart; // jump back address

                // ── break ────────────────────────────────────
                } else if (kw.equals("break")) {
                    out[pos++] = EventSystem.CMD_BREAK;

                // ── give ─────────────────────────────────────
                } else if (kw.equals("give")) {
                    requireArgs(p, 2, "give", lineNum);
                    String what = p[1].toLowerCase();
                    if (what.equals("item")) {
                        requireArgs(p, 3, "give item", lineNum);
                        out[pos++] = EventSystem.CMD_GIVE_ITEM;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = parseInt(p[3], lineNum);
                    } else if (what.equals("gold")) {
                        out[pos++] = EventSystem.CMD_GIVE_GOLD;
                        out[pos++] = parseInt(p[2], lineNum);
                    } else if (what.equals("exp")) {
                        out[pos++] = EventSystem.CMD_GIVE_EXP;
                        out[pos++] = parseInt(p[2], lineNum);
                    } else if (what.equals("skillpt")) {
                        out[pos++] = EventSystem.CMD_GIVE_SKILL_PT;
                        out[pos++] = 1;
                    } else {
                        return new CompileResult("give: unknown '" + what + "'", lineNum);
                    }

                // ── take ─────────────────────────────────────
                } else if (kw.equals("take")) {
                    requireArgs(p, 2, "take", lineNum);
                    String what = p[1].toLowerCase();
                    if (what.equals("item")) {
                        requireArgs(p, 3, "take item", lineNum);
                        out[pos++] = EventSystem.CMD_TAKE_ITEM;
                        out[pos++] = parseInt(p[2], lineNum);
                        out[pos++] = parseInt(p[3], lineNum);
                    } else if (what.equals("gold")) {
                        out[pos++] = EventSystem.CMD_TAKE_GOLD;
                        out[pos++] = parseInt(p[2], lineNum);
                    } else {
                        return new CompileResult("take: unknown '" + what + "'", lineNum);
                    }

                // ── heal ─────────────────────────────────────
                } else if (kw.equals("heal")) {
                    out[pos++] = EventSystem.CMD_HEAL;
                    out[pos++] = 9999; // full heal

                // ── damage ───────────────────────────────────
                } else if (kw.equals("damage")) {
                    requireArgs(p, 1, "damage", lineNum);
                    out[pos++] = EventSystem.CMD_DAMAGE_PLAYER;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── status ───────────────────────────────────
                } else if (kw.equals("status")) {
                    requireArgs(p, 1, "status", lineNum);
                    out[pos++] = EventSystem.CMD_STATUS_EFFECT;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── learnskill ───────────────────────────────
                } else if (kw.equals("learnskill")) {
                    requireArgs(p, 1, "learnSkill", lineNum);
                    out[pos++] = EventSystem.CMD_LEARN_SKILL;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── giveskillpt ──────────────────────────────
                } else if (kw.equals("giveskillpt")) {
                    out[pos++] = EventSystem.CMD_GIVE_SKILL_PT;
                    out[pos++] = 1;

                // ── changeclass ──────────────────────────────
                } else if (kw.equals("changeclass")) {
                    requireArgs(p, 1, "changeClass", lineNum);
                    out[pos++] = EventSystem.CMD_CHANGE_CLASS;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── teleport ─────────────────────────────────
                } else if (kw.equals("teleport")) {
                    requireArgs(p, 3, "teleport", lineNum);
                    out[pos++] = EventSystem.CMD_TELEPORT;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);
                    out[pos++] = parseInt(p[3], lineNum);

                // ── weather ──────────────────────────────────
                } else if (kw.equals("weather")) {
                    requireArgs(p, 2, "weather", lineNum);
                    out[pos++] = EventSystem.CMD_SET_WEATHER;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);

                // ── shake ────────────────────────────────────
                } else if (kw.equals("shake")) {
                    requireArgs(p, 2, "shake", lineNum);
                    out[pos++] = EventSystem.CMD_CAMERA_SHAKE;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);

                // ── fadeout / fadein ──────────────────────────
                } else if (kw.equals("fadeout")) {
                    requireArgs(p, 1, "fadeout", lineNum);
                    out[pos++] = EventSystem.CMD_FADE_OUT;
                    out[pos++] = parseInt(p[1], lineNum);

                } else if (kw.equals("fadein")) {
                    requireArgs(p, 1, "fadein", lineNum);
                    out[pos++] = EventSystem.CMD_FADE_IN;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── movenpc ──────────────────────────────────
                } else if (kw.equals("movenpc")) {
                    requireArgs(p, 3, "movenpc", lineNum);
                    out[pos++] = EventSystem.CMD_MOVE_NPC;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);
                    out[pos++] = parseInt(p[3], lineNum);

                // ── facenpc ──────────────────────────────────
                } else if (kw.equals("facenpc")) {
                    requireArgs(p, 2, "facenpc", lineNum);
                    out[pos++] = EventSystem.CMD_FACE_NPC;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);

                // ── moodnpc ──────────────────────────────────
                } else if (kw.equals("moodnpc")) {
                    requireArgs(p, 3, "moodNpc", lineNum);
                    out[pos++] = EventSystem.CMD_NPC_MOOD;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);
                    out[pos++] = parseInt(p[3], lineNum);

                // ── repnpc ───────────────────────────────────
                } else if (kw.equals("repnpc")) {
                    requireArgs(p, 2, "repNpc", lineNum);
                    out[pos++] = EventSystem.CMD_NPC_RELATION;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);

                // ── sound ────────────────────────────────────
                } else if (kw.equals("sound")) {
                    requireArgs(p, 1, "sound", lineNum);
                    out[pos++] = EventSystem.CMD_PLAY_SOUND;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── music ────────────────────────────────────
                } else if (kw.equals("music")) {
                    requireArgs(p, 1, "music", lineNum);
                    out[pos++] = EventSystem.CMD_PLAY_SOUND; // reuse sound cmd for music id
                    out[pos++] = parseInt(p[1], lineNum);

                // ── battle ───────────────────────────────────
                } else if (kw.equals("battle")) {
                    requireArgs(p, 1, "battle", lineNum);
                    int n = parseInt(p[1], lineNum);
                    out[pos++] = EventSystem.CMD_BATTLE;
                    out[pos++] = n;
                    for (int i = 0; i < n; i++) {
                        int idx = 2 + i;
                        if (idx >= p.length) {
                            return new CompileResult(
                                "battle: need " + n + " enemy type ids", lineNum);
                        }
                        out[pos++] = parseInt(p[idx], lineNum);
                    }

                // ── shop ─────────────────────────────────────
                } else if (kw.equals("shop")) {
                    requireArgs(p, 1, "shop", lineNum);
                    out[pos++] = EventSystem.CMD_SHOP_OPEN;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── inn ──────────────────────────────────────
                } else if (kw.equals("inn")) {
                    out[pos++] = EventSystem.CMD_INN;
                    out[pos++] = 0;

                // ── craft ────────────────────────────────────
                } else if (kw.equals("craft")) {
                    out[pos++] = EventSystem.CMD_CRAFT_OPEN;
                    out[pos++] = 0;

                // ── savepoint ────────────────────────────────
                } else if (kw.equals("savepoint")) {
                    out[pos++] = EventSystem.CMD_SAVE_POINT;
                    out[pos++] = 0;

                // ── cutscene ─────────────────────────────────
                } else if (kw.equals("cutscene")) {
                    requireArgs(p, 1, "cutscene", lineNum);
                    out[pos++] = EventSystem.CMD_PLAY_CUTSCENE;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── startquest ───────────────────────────────
                } else if (kw.equals("startquest")) {
                    requireArgs(p, 1, "startQuest", lineNum);
                    out[pos++] = EventSystem.CMD_QUEST_START;
                    out[pos++] = parseInt(p[1], lineNum);

                // ── checkquest ───────────────────────────────
                } else if (kw.equals("checkquest")) {
                    requireArgs(p, 2, "checkQuest", lineNum);
                    out[pos++] = EventSystem.CMD_QUEST_CHECK;
                    out[pos++] = parseInt(p[1], lineNum);
                    registerPatch(labelNames, labelPatches, pos, p[2]);
                    out[pos++] = 0;

                // ── updatequest ──────────────────────────────
                } else if (kw.equals("updatequest")) {
                    requireArgs(p, 2, "updateQuest", lineNum);
                    out[pos++] = EventSystem.CMD_QUEST_UPDATE;
                    out[pos++] = parseInt(p[1], lineNum);
                    out[pos++] = parseInt(p[2], lineNum);

                } else {
                    return new CompileResult("Unknown keyword: '" + kw + "'", lineNum);
                }

            } catch (CompileError e) {
                return new CompileResult(e.getMessage(), lineNum);
            }

            if (pos >= out.length - 16) {
                return new CompileResult("Script too long (max 4096 ints)", lineNum);
            }
        }

        // Ensure script ends with CMD_END
        if (pos == 0 || out[pos - 1] != EventSystem.CMD_END) {
            out[pos++] = EventSystem.CMD_END;
        }

        // ── Pass 3: back-patch label addresses ───────────────
        for (int i = 0; i < labelPatches.size(); i++) {
            Object[] patch = (Object[]) labelPatches.elementAt(i);
            int  patchPos  = ((Integer) patch[0]).intValue();
            String lblName = (String)   patch[1];

            // Find definition
            int defPos = -1;
            for (int j = 0; j < labelNames.size(); j++) {
                Object entry = labelNames.elementAt(j);
                if (entry instanceof String) {
                    String defKey = (String) entry;
                    if (defKey.equals(lblName + ":def")) {
                        defPos = ((Integer) labelDefs.elementAt(
                            labelDefIndex(labelNames, lblName))).intValue();
                        break;
                    }
                }
            }
            if (defPos < 0) {
                return new CompileResult("Undefined label: '" + lblName + "'", -1);
            }
            out[patchPos] = defPos;
        }

        // Trim to used size
        int[] result = new int[pos];
        System.arraycopy(out, 0, result, 0, pos);
        return new CompileResult(result, pos);
    }

    // =========================================================
    //  DECOMPILE (bytecode → readable source)
    // =========================================================
    /**
     * Converts an int[] bytecode array back to a human-readable
     * script string. Useful for viewing compiled event scripts.
     */
    public static String decompile(int[] bytecode) {
        if (bytecode == null || bytecode.length == 0) return "end\n";
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (i < bytecode.length) {
            int cmd = bytecode[i++];
            switch (cmd) {
                case EventSystem.CMD_END:          sb.append("end\n"); break;
                case EventSystem.CMD_TEXT:
                    sb.append("text \"[").append(safeGet(bytecode,i++)).append("]\"\n"); break;
                case EventSystem.CMD_CHOICE:
                    int nc = safeGet(bytecode,i++);
                    sb.append("choice ").append(nc);
                    for (int j=0;j<nc;j++) sb.append(" \"[").append(safeGet(bytecode,i++)).append("]\"");
                    sb.append('\n'); break;
                case EventSystem.CMD_WAIT:
                    sb.append("wait ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_SET_SWITCH:
                    sb.append("set switch ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)==1?"on":"off").append('\n'); break;
                case EventSystem.CMD_VAR_MATH:
                    int vi=safeGet(bytecode,i++); int op=safeGet(bytecode,i++); int vv=safeGet(bytecode,i++);
                    String[] opNames={"set","add","sub","mul","div","mod","rnd"};
                    sb.append(op<opNames.length?opNames[op]:"op"+op)
                      .append(" var ").append(vi).append(' ').append(vv).append('\n'); break;
                case EventSystem.CMD_IF_SWITCH:
                    sb.append("if switch ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)==1?"on":"off")
                      .append(" goto [").append(safeGet(bytecode,i++)).append("]\n"); break;
                case EventSystem.CMD_IF_VAR:
                    sb.append("if var ").append(safeGet(bytecode,i++));
                    int dop=safeGet(bytecode,i++);
                    String[] dops={"==","!=",">","<",">=","<="};
                    sb.append(' ').append(dop<dops.length?dops[dop]:"?")
                      .append(' ').append(safeGet(bytecode,i++))
                      .append(" goto [").append(safeGet(bytecode,i++)).append("]\n"); break;
                case EventSystem.CMD_GOTO:
                    sb.append("goto [").append(safeGet(bytecode,i++)).append("]\n"); break;
                case EventSystem.CMD_GIVE_ITEM:
                    sb.append("give item ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_TAKE_ITEM:
                    sb.append("take item ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_GIVE_GOLD:
                    sb.append("give gold ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_TAKE_GOLD:
                    sb.append("take gold ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_GIVE_EXP:
                    sb.append("give exp ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_HEAL:
                    i++; sb.append("heal\n"); break;
                case EventSystem.CMD_DAMAGE_PLAYER:
                    sb.append("damage ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_STATUS_EFFECT:
                    sb.append("status ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_TELEPORT:
                    sb.append("teleport ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_PLAY_SOUND:
                    sb.append("sound ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_BATTLE:
                    int bn=safeGet(bytecode,i++); sb.append("battle ").append(bn);
                    for(int j=0;j<bn;j++) sb.append(' ').append(safeGet(bytecode,i++));
                    sb.append('\n'); break;
                case EventSystem.CMD_SHOP_OPEN:
                    sb.append("shop ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_INN:        i++; sb.append("inn\n"); break;
                case EventSystem.CMD_CRAFT_OPEN: i++; sb.append("craft\n"); break;
                case EventSystem.CMD_SAVE_POINT: i++; sb.append("savepoint\n"); break;
                case EventSystem.CMD_PLAY_CUTSCENE:
                    sb.append("cutscene ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_QUEST_START:
                    sb.append("startQuest ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_QUEST_CHECK:
                    sb.append("checkQuest ").append(safeGet(bytecode,i++))
                      .append(" [").append(safeGet(bytecode,i++)).append("]\n"); break;
                case EventSystem.CMD_QUEST_UPDATE:
                    sb.append("updateQuest ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_MOVE_NPC:
                    sb.append("movenpc ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_FACE_NPC:
                    sb.append("facenpc ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_NPC_MOOD:
                    sb.append("moodNpc ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_NPC_RELATION:
                    sb.append("repNpc ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_SET_WEATHER:
                    sb.append("weather ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_CAMERA_SHAKE:
                    sb.append("shake ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_FADE_OUT:
                    sb.append("fadeout ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_FADE_IN:
                    sb.append("fadein ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_LEARN_SKILL:
                    sb.append("learnSkill ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_GIVE_SKILL_PT:
                    i++; sb.append("giveSkillPt\n"); break;
                case EventSystem.CMD_CHANGE_CLASS:
                    sb.append("changeClass ").append(safeGet(bytecode,i++)).append('\n'); break;
                case EventSystem.CMD_LOOP_START:
                    sb.append("loop\n"); break;
                case EventSystem.CMD_LOOP_END:
                    i++; sb.append("endloop\n"); break;
                case EventSystem.CMD_BREAK:
                    sb.append("break\n"); break;
                case EventSystem.CMD_CHECK_ITEM:
                    sb.append("if hasitem ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++))
                      .append(" goto [").append(safeGet(bytecode,i++)).append("]\n"); break;
                case EventSystem.CMD_CHECK_GOLD:
                    sb.append("if gold ").append(safeGet(bytecode,i++))
                      .append(' ').append(safeGet(bytecode,i++))
                      .append(" goto [").append(safeGet(bytecode,i++)).append("]\n"); break;
                default:
                    sb.append("// unknown opcode ").append(cmd).append('\n');
                    break;
            }
        }
        return sb.toString();
    }

    // =========================================================
    //  SERIALIZE / DESERIALIZE bytecode ↔ byte[]
    // =========================================================
    /**
     * Serializes int[] bytecode to a compact byte array (4 bytes/int, big-endian).
     * Suitable for storage via FileManager.writeFile().
     */
    public static byte[] serialize(int[] bytecode) {
        if (bytecode == null) return new byte[0];
        byte[] data = new byte[bytecode.length * 4];
        for (int i = 0; i < bytecode.length; i++) {
            int v = bytecode[i];
            data[i*4]   = (byte)((v >> 24) & 0xFF);
            data[i*4+1] = (byte)((v >> 16) & 0xFF);
            data[i*4+2] = (byte)((v >>  8) & 0xFF);
            data[i*4+3] = (byte)( v        & 0xFF);
        }
        return data;
    }

    /** Deserializes a byte[] (from FileManager.readFile()) back to int[] bytecode. */
    public static int[] deserialize(byte[] data) {
        if (data == null || data.length < 4) return new int[]{ EventSystem.CMD_END };
        int len = data.length / 4;
        int[] bc = new int[len];
        for (int i = 0; i < len; i++) {
            bc[i] = ((data[i*4]   & 0xFF) << 24)
                  | ((data[i*4+1] & 0xFF) << 16)
                  | ((data[i*4+2] & 0xFF) <<  8)
                  |  (data[i*4+3] & 0xFF);
        }
        return bc;
    }

    // =========================================================
    //  BUILT-IN SCRIPT LIBRARY  (common reusable scripts)
    // =========================================================
    /** Returns source for a basic NPC greeting script. */
    public static String templateNPCGreeting(String name, int questId) {
        return
            "// " + name + " greeting\n"
          + "if switch " + questId + " on goto met_before\n"
          + "text \"Hello, I'm " + name + "!\"\n"
          + "set switch " + questId + " on\n"
          + "give exp 10\n"
          + "goto done\n"
          + "label met_before\n"
          + "text \"Good to see you again!\"\n"
          + "label done\n"
          + "end\n";
    }

    /** Returns source for a shop NPC. */
    public static String templateShopNPC(int shopId) {
        return
            "text \"Welcome to my shop!\"\n"
          + "choice 2 \"Buy\" \"Leave\"\n"
          + "if var 0 == 1 goto done\n"
          + "shop " + shopId + "\n"
          + "label done\n"
          + "end\n";
    }

    /** Returns source for a chest event (open once, give item). */
    public static String templateChest(int switchId, int itemId, int itemCount) {
        return
            "if switch " + switchId + " on goto already_open\n"
          + "set switch " + switchId + " on\n"
          + "sound 10\n"
          + "text \"You found something!\"\n"
          + "give item " + itemId + " " + itemCount + "\n"
          + "goto done\n"
          + "label already_open\n"
          + "text \"(The chest is empty.)\"\n"
          + "label done\n"
          + "end\n";
    }

    /** Returns source for a boss intro + battle. */
    public static String templateBossBattle(int bossEnemyType, int defeatSwitchId) {
        return
            "if switch " + defeatSwitchId + " on goto boss_dead\n"
          + "text \"So, you've come this far...\"\n"
          + "fadeout 3\n"
          + "music 4\n"
          + "fadein 3\n"
          + "battle 1 " + bossEnemyType + "\n"
          + "set switch " + defeatSwitchId + " on\n"
          + "music 5\n"
          + "text \"You defeated the boss!\"\n"
          + "give exp 500\n"
          + "give gold 200\n"
          + "goto done\n"
          + "label boss_dead\n"
          + "text \"...I won't forget this.\"\n"
          + "label done\n"
          + "end\n";
    }

    // =========================================================
    //  INTERNAL HELPERS
    // =========================================================
    private static String[] splitLines(String src) {
        Vector lines = new Vector();
        int start = 0;
        for (int i = 0; i <= src.length(); i++) {
            if (i == src.length() || src.charAt(i) == '\n') {
                lines.addElement(src.substring(start, i));
                start = i + 1;
            }
        }
        String[] arr = new String[lines.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = (String) lines.elementAt(i);
        return arr;
    }

    private static String[] tokeniseLine(String line) {
        Vector parts = new Vector();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r') { i++; continue; }
            if (c == '"') {
                // Quoted string
                int j = i + 1;
                while (j < line.length() && line.charAt(j) != '"') j++;
                parts.addElement(line.substring(i + 1, j));
                i = j + 1;
            } else {
                // Plain token
                int j = i;
                while (j < line.length() && line.charAt(j) != ' '
                        && line.charAt(j) != '\t') j++;
                parts.addElement(line.substring(i, j));
                i = j;
            }
        }
        String[] arr = new String[parts.size()];
        for (int k = 0; k < arr.length; k++) arr[k] = (String) parts.elementAt(k);
        return arr;
    }

    private static int parseInt(String s, int lineNum) throws CompileError {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) {
            throw new CompileError("Expected number, got '" + s + "'");
        }
    }

    private static int parseOp(String s, int lineNum) throws CompileError {
        if (s.equals("==")) return 0;
        if (s.equals("!=")) return 1;
        if (s.equals(">"))  return 2;
        if (s.equals("<"))  return 3;
        if (s.equals(">=")) return 4;
        if (s.equals("<=")) return 5;
        throw new CompileError("Unknown operator '" + s + "'");
    }

    private static void requireArgs(String[] p, int n, String kw, int ln) throws CompileError {
        if (p.length <= n) {
            throw new CompileError(kw + " requires " + n + " argument(s)");
        }
    }

    /** Stores a label name in the string table and returns its index. */
    private static int internString(String s, Vector labelNames) {
        for (int i = 0; i < labelNames.size(); i++) {
            Object e = labelNames.elementAt(i);
            if (e instanceof String && e.equals(s)) return i;
        }
        int idx = labelNames.size();
        labelNames.addElement(s);
        return idx;
    }

    /** Records that position [patchPos] in output needs to be filled with the address of [labelName]. */
    private static void registerPatch(Vector labelNames, Vector labelPatches,
                                       int patchPos, String labelName) {
        Object[] entry = new Object[]{ new Integer(patchPos), labelName };
        labelPatches.addElement(entry);
    }

    private static int labelDefIndex(Vector labelNames, String name) {
        String key = name + ":def";
        int defCount = 0;
        for (int i = 0; i < labelNames.size(); i++) {
            Object e = labelNames.elementAt(i);
            if (e instanceof String && ((String)e).startsWith(":def")) {
                if (((String)e).equals(key)) return defCount;
                defCount++;
            }
        }
        return 0;
    }

    private static int safeGet(int[] bc, int i) {
        return (i < bc.length) ? bc[i] : 0;
    }

    // ── CompileError (internal checked exception workaround) ──
    private static class CompileError extends Exception {
        CompileError(String msg) { super(msg); }
    }
}
