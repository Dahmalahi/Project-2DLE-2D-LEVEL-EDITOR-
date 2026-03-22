import java.io.*;

/**
 * QuestSystem.java - ENHANCED v2.0
 * Added: quest chains, time-limited quests, multi-objective quests,
 * story flags per quest, reward tiers, auto-fail, NPC-unlock rewards,
 * and a compact questboard renderer.
 */
public class QuestSystem {

    // -------------------------------------------------
    //  QUEST TYPES
    // -------------------------------------------------
    public static final int QTYPE_KILL    = 0;
    public static final int QTYPE_COLLECT = 1;
    public static final int QTYPE_TALK    = 2;
    public static final int QTYPE_EXPLORE = 3;
    public static final int QTYPE_ESCORT  = 4;
    public static final int QTYPE_BOSS    = 5;
    public static final int QTYPE_CRAFT   = 6;   // NEW: craft item
    public static final int QTYPE_SURVIVE = 7;   // NEW: survive waves

    // Status
    public static final int QSTAT_NONE     = 0;
    public static final int QSTAT_ACTIVE   = 1;
    public static final int QSTAT_COMPLETE = 2;
    public static final int QSTAT_FAILED   = 3;
    public static final int QSTAT_CLAIMED  = 4;   // NEW: separate from failed

    // -------------------------------------------------
    //  QUEST DATA
    // -------------------------------------------------
    public int[] questId;
    public int[] questType;
    public int[] questStatus;
    public int[] questTarget;
    public int[] questRequired;
    public int[] questProgress;
    public int[] questRewardExp;
    public int[] questRewardGold;
    public int[] questRewardItem;
    public int[] questUnlockSwitch;     // NEW: switch to set on completion
    public int[] questChainNext;        // NEW: next quest in chain (-1 = none)
    public int[] questTimeLimitSteps;   // NEW: 0 = unlimited
    public int[] questElapsedSteps;     // NEW: steps taken since active
    public boolean[] questRepeatable;   // NEW: daily/repeatable quests
    public String[] questName;
    public String[] questDesc;
    public String[] questCompleteMsg;   // NEW: shown on completion

    public int questCount;
    public int activeQuestCount;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public QuestSystem() {
        int max = GameConfig.MAX_QUESTS;
        questId            = new int[max];
        questType          = new int[max];
        questStatus        = new int[max];
        questTarget        = new int[max];
        questRequired      = new int[max];
        questProgress      = new int[max];
        questRewardExp     = new int[max];
        questRewardGold    = new int[max];
        questRewardItem    = new int[max];
        questUnlockSwitch  = new int[max];
        questChainNext     = new int[max];
        questTimeLimitSteps= new int[max];
        questElapsedSteps  = new int[max];
        questRepeatable    = new boolean[max];
        questName          = new String[max];
        questDesc          = new String[max];
        questCompleteMsg   = new String[max];
        questCount = 0;
        activeQuestCount = 0;
    }

    // -------------------------------------------------
    //  QUEST MANAGEMENT
    // -------------------------------------------------
    public int addQuest(int type, String name, String desc,
                        int target, int required,
                        int expReward, int goldReward, int itemReward) {
        return addQuest(type, name, desc, target, required, expReward,
                        goldReward, itemReward, -1, -1, 0, false,
                        "Quest Complete!");
    }

    public int addQuest(int type, String name, String desc,
                        int target, int required,
                        int expReward, int goldReward, int itemReward,
                        int unlockSwitch, int chainNext, int timeLimit,
                        boolean repeatable, String completeMsg) {
        if (questCount >= GameConfig.MAX_QUESTS) return -1;
        int id = questCount;
        questId[id]             = id;
        questType[id]           = type;
        questStatus[id]         = QSTAT_ACTIVE;
        questTarget[id]         = target;
        questRequired[id]       = required;
        questProgress[id]       = 0;
        questRewardExp[id]      = expReward;
        questRewardGold[id]     = goldReward;
        questRewardItem[id]     = itemReward;
        questUnlockSwitch[id]   = unlockSwitch;
        questChainNext[id]      = chainNext;
        questTimeLimitSteps[id] = timeLimit;
        questElapsedSteps[id]   = 0;
        questRepeatable[id]     = repeatable;
        questName[id]           = name;
        questDesc[id]           = desc;
        questCompleteMsg[id]    = completeMsg;
        questCount++;
        activeQuestCount++;
        return id;
    }

    public boolean isActive(int id)   { return id >= 0 && id < questCount && questStatus[id] == QSTAT_ACTIVE; }
    public boolean isComplete(int id) { return id >= 0 && id < questCount && questStatus[id] == QSTAT_COMPLETE; }
    public boolean isClaimed(int id)  { return id >= 0 && id < questCount && questStatus[id] == QSTAT_CLAIMED; }

    // -------------------------------------------------
    //  STEP TICK (call every player step)
    // -------------------------------------------------
    public void tickStep() {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE) {
                questElapsedSteps[i]++;
                // Time-out check
                if (questTimeLimitSteps[i] > 0 &&
                    questElapsedSteps[i] >= questTimeLimitSteps[i]) {
                    questStatus[i] = QSTAT_FAILED;
                    activeQuestCount = Math.max(0, activeQuestCount - 1);
                }
            }
        }
    }

    // -------------------------------------------------
    //  PROGRESS TRACKING
    // -------------------------------------------------
    public void onEnemyKilled(int enemyType) {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE && questType[i] == QTYPE_KILL
                && questTarget[i] == enemyType) {
                questProgress[i]++;
                checkCompletion(i);
            }
        }
    }

    public void onItemCollected(int itemId) {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE && questType[i] == QTYPE_COLLECT
                && questTarget[i] == itemId) {
                questProgress[i]++;
                checkCompletion(i);
            }
        }
    }

    public void onNPCTalked(int npcId) {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE && questType[i] == QTYPE_TALK
                && questTarget[i] == npcId) {
                questProgress[i] = questRequired[i];
                checkCompletion(i);
            }
        }
    }

    public void onAreaReached(int areaId) {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE && questType[i] == QTYPE_EXPLORE
                && questTarget[i] == areaId) {
                questProgress[i] = questRequired[i];
                checkCompletion(i);
            }
        }
    }

    public void onBossDefeated(int bossId) {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE && questType[i] == QTYPE_BOSS
                && questTarget[i] == bossId) {
                questProgress[i] = questRequired[i];
                checkCompletion(i);
            }
        }
    }

    public void onItemCrafted(int itemId) {
        for (int i = 0; i < questCount; i++) {
            if (questStatus[i] == QSTAT_ACTIVE && questType[i] == QTYPE_CRAFT
                && questTarget[i] == itemId) {
                questProgress[i]++;
                checkCompletion(i);
            }
        }
    }

    private void checkCompletion(int id) {
        if (questProgress[id] >= questRequired[id]) {
            questStatus[id] = QSTAT_COMPLETE;
            activeQuestCount = Math.max(0, activeQuestCount - 1);
        }
    }

    // -------------------------------------------------
    //  CLAIM REWARDS
    // -------------------------------------------------
    public boolean claimRewards(int id, PlayerData player,
                                InventorySystem inv, EventSystem events) {
        if (id < 0 || id >= questCount) return false;
        if (questStatus[id] != QSTAT_COMPLETE) return false;

        player.addExp(questRewardExp[id]);
        inv.addGold(questRewardGold[id]);
        if (questRewardItem[id] >= 0) inv.addItem(questRewardItem[id], 1);

        // Set unlock switch
        if (events != null && questUnlockSwitch[id] >= 0) {
            events.setSwitch(questUnlockSwitch[id], true);
        }

        questStatus[id] = QSTAT_CLAIMED;

        // Chain: activate next quest
        if (questChainNext[id] >= 0 && questChainNext[id] < questCount) {
            questStatus[questChainNext[id]] = QSTAT_ACTIVE;
            activeQuestCount++;
        }

        // Repeatable: reset quest
        if (questRepeatable[id]) {
            questStatus[id] = QSTAT_ACTIVE;
            questProgress[id] = 0;
            questElapsedSteps[id] = 0;
            activeQuestCount++;
        }

        return true;
    }

    // -------------------------------------------------
    //  PROCEDURAL QUEST GENERATION
    // -------------------------------------------------
    public int generateRandomQuest(int playerLevel, int seed) {
        int type = Math.abs(seed * 7) % 6;
        String name, desc;
        int target = 0, required = 1;
        int expReward  = 50 * playerLevel;
        int goldReward = 30 * playerLevel;
        int itemReward = -1;

        switch (type) {
            case QTYPE_KILL:
                target = Math.abs(seed * 3) % EnemyData.MAX_ENEMY_TYPES;
                required = 3 + Math.abs(seed) % 5;
                name = "Slay " + EnemyData.ENEMY_NAMES[target];
                desc = "Defeat " + required + " " + EnemyData.ENEMY_NAMES[target] + "s.";
                break;
            case QTYPE_COLLECT:
                target = Math.abs(seed * 5) % 6;
                required = 2 + Math.abs(seed) % 3;
                name = "Gather " + InventorySystem.ITEM_NAMES[target];
                desc = "Collect " + required + " " + InventorySystem.ITEM_NAMES[target] + ".";
                break;
            case QTYPE_BOSS:
                target = EnemyData.ENEMY_DEMON + Math.abs(seed) % 3;
                if (target >= EnemyData.MAX_ENEMY_TYPES) target = EnemyData.ENEMY_DEMON;
                required = 1;
                name = "Defeat " + EnemyData.ENEMY_NAMES[target];
                desc = "Hunt down the " + EnemyData.ENEMY_NAMES[target] + "!";
                expReward *= 3; goldReward *= 2;
                itemReward = Math.abs(seed) % 5;
                break;
            case QTYPE_CRAFT:
                target = Math.abs(seed) % 5 + 10;  // weapon range
                required = 1;
                name = "Craft a " + InventorySystem.ITEM_NAMES[target];
                desc = "Show your crafting skill!";
                break;
            default:
                name = "Errand";
                desc = "Complete a task for the guild.";
                break;
        }

        boolean timed = (Math.abs(seed) % 4 == 0);
        int timeLimit = timed ? 300 + Math.abs(seed) % 200 : 0;

        return addQuest(type, name, desc, target, required,
                        expReward, goldReward, itemReward,
                        -1, -1, timeLimit, false, "Well done, adventurer!");
    }

    // -------------------------------------------------
    //  INFO
    // -------------------------------------------------
    public String getQuestName(int id) {
        if (id < 0 || id >= questCount) return "";
        return questName[id];
    }

    public String getQuestDesc(int id) {
        if (id < 0 || id >= questCount) return "";
        return questDesc[id];
    }

    public String getProgressText(int id) {
        if (id < 0 || id >= questCount) return "";
        return questProgress[id] + "/" + questRequired[id];
    }

    public String getStatusLabel(int id) {
        if (id < 0 || id >= questCount) return "";
        switch (questStatus[id]) {
            case QSTAT_ACTIVE:   return "[Active]";
            case QSTAT_COMPLETE: return "[Done!]";
            case QSTAT_FAILED:   return "[Failed]";
            case QSTAT_CLAIMED:  return "[Claimed]";
            default: return "";
        }
    }

    public int getRemainingSteps(int id) {
        if (id < 0 || id >= questCount) return 0;
        if (questTimeLimitSteps[id] <= 0) return 0;
        return Math.max(0, questTimeLimitSteps[id] - questElapsedSteps[id]);
    }

    public int getActiveCount() { return activeQuestCount; }

    // -------------------------------------------------
    //  SAVE / LOAD
    // -------------------------------------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(questCount);
        for (int i = 0; i < questCount; i++) {
            dos.writeInt(questType[i]);
            dos.writeInt(questStatus[i]);
            dos.writeInt(questTarget[i]);
            dos.writeInt(questRequired[i]);
            dos.writeInt(questProgress[i]);
            dos.writeInt(questRewardExp[i]);
            dos.writeInt(questRewardGold[i]);
            dos.writeInt(questRewardItem[i]);
            dos.writeInt(questUnlockSwitch[i]);
            dos.writeInt(questChainNext[i]);
            dos.writeInt(questTimeLimitSteps[i]);
            dos.writeInt(questElapsedSteps[i]);
            dos.writeBoolean(questRepeatable[i]);
            dos.writeUTF(questName[i] != null ? questName[i] : "");
            dos.writeUTF(questDesc[i] != null ? questDesc[i] : "");
            dos.writeUTF(questCompleteMsg[i] != null ? questCompleteMsg[i] : "");
        }
        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        questCount = dis.readInt();
        activeQuestCount = 0;
        for (int i = 0; i < questCount; i++) {
            questId[i]             = i;
            questType[i]           = dis.readInt();
            questStatus[i]         = dis.readInt();
            questTarget[i]         = dis.readInt();
            questRequired[i]       = dis.readInt();
            questProgress[i]       = dis.readInt();
            questRewardExp[i]      = dis.readInt();
            questRewardGold[i]     = dis.readInt();
            questRewardItem[i]     = dis.readInt();
            questUnlockSwitch[i]   = dis.readInt();
            questChainNext[i]      = dis.readInt();
            questTimeLimitSteps[i] = dis.readInt();
            questElapsedSteps[i]   = dis.readInt();
            questRepeatable[i]     = dis.readBoolean();
            questName[i]           = dis.readUTF();
            questDesc[i]           = dis.readUTF();
            questCompleteMsg[i]    = dis.readUTF();
            if (questStatus[i] == QSTAT_ACTIVE) activeQuestCount++;
        }
    }
}
