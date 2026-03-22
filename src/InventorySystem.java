import java.io.*;

/**
 * InventorySystem.java - ENHANCED v2.0
 * Added: sorting, category filter, new items (Mega Potion, Elixir, etc.),
 * equipment quality tracking, item descriptions, stack sorting,
 * and quick-access check by category.
 */
public class InventorySystem {

    // -------------------------------------------------
    //  ITEM TYPES
    // -------------------------------------------------
    public static final int ITYPE_CONSUMABLE = 0;
    public static final int ITYPE_WEAPON     = 1;
    public static final int ITYPE_ARMOR      = 2;
    public static final int ITYPE_HELM       = 3;
    public static final int ITYPE_ACCESSORY  = 4;
    public static final int ITYPE_KEY        = 5;
    public static final int ITYPE_MATERIAL   = 6;
    public static final int ITYPE_BOOTS      = 7;  // NEW
    public static final int ITYPE_OFFHAND    = 8;  // NEW

    // -------------------------------------------------
    //  ITEM IDs
    // -------------------------------------------------
    public static final int ITEM_POTION      = 0;
    public static final int ITEM_HI_POTION   = 1;
    public static final int ITEM_ETHER       = 2;
    public static final int ITEM_ANTIDOTE    = 3;
    public static final int ITEM_PHOENIX     = 4;
    public static final int ITEM_TENT        = 5;
    public static final int ITEM_MEGA_POTION = 6;   // NEW
    public static final int ITEM_ELIXIR      = 7;   // NEW: full restore
    public static final int ITEM_SMOKE_BOMB  = 8;   // NEW: escape battle
    public static final int ITEM_GRENADE     = 9;   // NEW: AoE damage item

    public static final int ITEM_SWORD       = 10;
    public static final int ITEM_AXE         = 11;
    public static final int ITEM_STAFF       = 12;
    public static final int ITEM_BOW         = 13;
    public static final int ITEM_DAGGER      = 14;
    public static final int ITEM_SPEAR       = 15;  // NEW
    public static final int ITEM_WAND        = 16;  // NEW: boosts MAG

    public static final int ITEM_LEATHER     = 20;
    public static final int ITEM_CHAINMAIL   = 21;
    public static final int ITEM_PLATE       = 22;
    public static final int ITEM_ROBE        = 23;
    public static final int ITEM_MITHRIL     = 24;  // NEW

    public static final int ITEM_CAP         = 30;
    public static final int ITEM_HELM_IRON   = 31;
    public static final int ITEM_CROWN       = 32;
    public static final int ITEM_HOOD        = 33;  // NEW

    public static final int ITEM_RING_ATK    = 40;
    public static final int ITEM_RING_DEF    = 41;
    public static final int ITEM_RING_SPD    = 42;
    public static final int ITEM_AMULET      = 43;
    public static final int ITEM_TALISMAN    = 44;  // NEW: +luk

    public static final int ITEM_KEY_DOOR    = 50;
    public static final int ITEM_KEY_CHEST   = 51;
    public static final int ITEM_KEY_BOSS    = 52;
    public static final int ITEM_KEY_CITY    = 53;  // NEW

    public static final int ITEM_HERB        = 54;  // NEW: crafting material
    public static final int ITEM_ORE         = 55;  // NEW
    public static final int ITEM_SILK        = 56;  // NEW

    public static final int ITEM_BOOTS_IRON  = 57;  // NEW
    public static final int ITEM_BOOTS_SPEED = 58;  // NEW: +spd

    public static final int ITEM_SHIELD_WOOD = 59;  // NEW
    public static final int ITEM_SHIELD_IRON = 60;  // NEW

    public static final int MAX_ITEM_TYPES   = 64;

    // Item data: [type, value, price, maxStack]
    public static final int[][] ITEM_DATA = {
        {ITYPE_CONSUMABLE, 50,   50,  99},  // 0:Potion
        {ITYPE_CONSUMABLE, 150, 150,  99},  // 1:Hi-Potion
        {ITYPE_CONSUMABLE, 30,  100,  99},  // 2:Ether
        {ITYPE_CONSUMABLE, 0,    30,  99},  // 3:Antidote
        {ITYPE_CONSUMABLE, 0,   500,  99},  // 4:Phoenix
        {ITYPE_CONSUMABLE, 0,   200,  99},  // 5:Tent
        {ITYPE_CONSUMABLE, 400, 400,  99},  // 6:Mega Potion (NEW)
        {ITYPE_CONSUMABLE, 0,  2000,  10},  // 7:Elixir (NEW)
        {ITYPE_CONSUMABLE, 0,   300,  99},  // 8:Smoke Bomb (NEW)
        {ITYPE_CONSUMABLE, 80,  150,  99},  // 9:Grenade (NEW, AoE fire)

        {ITYPE_WEAPON,   5,   100, 1},  // 10:Sword
        {ITYPE_WEAPON,   8,   200, 1},  // 11:Axe
        {ITYPE_WEAPON,   3,   150, 1},  // 12:Staff
        {ITYPE_WEAPON,   6,   180, 1},  // 13:Bow
        {ITYPE_WEAPON,   4,    80, 1},  // 14:Dagger
        {ITYPE_WEAPON,   7,   220, 1},  // 15:Spear (NEW)
        {ITYPE_WEAPON,   2,   160, 1},  // 16:Wand (NEW, +mag)
        {0,0,0,0},{0,0,0,0},{0,0,0,0}, // 17-19 unused

        {ITYPE_ARMOR,  3,   80, 1},  // 20:Leather
        {ITYPE_ARMOR,  6,  200, 1},  // 21:Chainmail
        {ITYPE_ARMOR, 10,  400, 1},  // 22:Plate
        {ITYPE_ARMOR,  2,  150, 1},  // 23:Robe
        {ITYPE_ARMOR, 15,  800, 1},  // 24:Mithril Armor (NEW)
        {0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},

        {ITYPE_HELM,  1,  40, 1},  // 30:Cap
        {ITYPE_HELM,  3, 120, 1},  // 31:Iron Helm
        {ITYPE_HELM,  5, 300, 1},  // 32:Crown
        {ITYPE_HELM,  2, 100, 1},  // 33:Hood (NEW)
        {0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},

        {ITYPE_ACCESSORY, 5, 200, 1},  // 40:Ring ATK
        {ITYPE_ACCESSORY, 5, 200, 1},  // 41:Ring DEF
        {ITYPE_ACCESSORY, 5, 200, 1},  // 42:Ring SPD
        {ITYPE_ACCESSORY, 3, 500, 1},  // 43:Amulet
        {ITYPE_ACCESSORY, 5, 300, 1},  // 44:Talisman (NEW, +luk)
        {0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},

        {ITYPE_KEY, 0, 0, 1},  // 50:Door Key
        {ITYPE_KEY, 0, 0, 1},  // 51:Chest Key
        {ITYPE_KEY, 0, 0, 1},  // 52:Boss Key
        {ITYPE_KEY, 0, 0, 1},  // 53:City Key (NEW)
        {ITYPE_MATERIAL, 0, 10, 99},  // 54:Herb
        {ITYPE_MATERIAL, 0, 20, 99},  // 55:Ore
        {ITYPE_MATERIAL, 0, 15, 99},  // 56:Silk
        {ITYPE_BOOTS,  2,  80, 1},   // 57:Iron Boots
        {ITYPE_BOOTS,  5, 200, 1},   // 58:Speed Boots
        {ITYPE_OFFHAND, 3,  90, 1},  // 59:Wood Shield
        {ITYPE_OFFHAND, 7, 250, 1},  // 60:Iron Shield
        {0,0,0,0},{0,0,0,0},{0,0,0,0}
    };

    public static final String[] ITEM_NAMES = {
        "Potion","Hi-Potion","Ether","Antidote","Phoenix","Tent",
        "Mega Potion","Elixir","Smoke Bomb","Grenade",
        "Sword","Axe","Staff","Bow","Dagger","Spear","Wand",
        "","","",
        "Leather","Chainmail","Plate","Robe","Mithril Armor",
        "","","","","",
        "Cap","Iron Helm","Crown","Hood",
        "","","","","","",
        "Ring+ATK","Ring+DEF","Ring+SPD","Amulet","Talisman",
        "","","","","",
        "Door Key","Chest Key","Boss Key","City Key",
        "Herb","Ore","Silk",
        "Iron Boots","Speed Boots","Wood Shield","Iron Shield",
        "","",""
    };

    public static final String[] ITEM_DESC = {
        "Restores 50 HP","Restores 150 HP","Restores 30 MP","Cures poison",
        "Revives fallen ally","Rests anywhere",
        "Restores 400 HP","Fully restores HP/MP","Escape battle",
        "Deals 80 fire AoE damage",
        "ATK +5","ATK +8","ATK +3 MAG+5","ATK +6 ranged","ATK +4 fast","ATK +7 reach","MAG +8",
        "","","",
        "DEF +3","DEF +6","DEF +10","DEF +2 MAG+3","DEF +15",
        "","","","","",
        "DEF +1","DEF +3","DEF +5 LUK+3","DEF +2 MAG+2",
        "","","","","","",
        "ATK +5","DEF +5","SPD +5","All stats +3","LUK +5",
        "","","","","",
        "Opens doors","Opens chests","Opens boss gates","Opens city gates",
        "Crafting material","Crafting material","Crafting material",
        "DEF +2","SPD +5","DEF +3 block","DEF +7 block"
    };

    // -------------------------------------------------
    //  INVENTORY DATA
    // -------------------------------------------------
    private int[] itemIds;
    private int[] itemCounts;
    private int[] itemQuality;   // NEW: 0=normal,1=fine,2=rare,3=master
    private int itemSlots;
    private int gold;

    // Sort modes
    public static final int SORT_NONE  = 0;
    public static final int SORT_TYPE  = 1;
    public static final int SORT_NAME  = 2;
    public static final int SORT_VALUE = 3;

    // Filter
    private int filterType = -1;  // -1 = show all

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public InventorySystem() {
        itemIds     = new int[GameConfig.MAX_INVENTORY];
        itemCounts  = new int[GameConfig.MAX_INVENTORY];
        itemQuality = new int[GameConfig.MAX_INVENTORY];
        itemSlots = 0; gold = 0;
        for (int i = 0; i < GameConfig.MAX_INVENTORY; i++) {
            itemIds[i] = -1; itemCounts[i] = 0; itemQuality[i] = 0;
        }
    }

    // -------------------------------------------------
    //  ITEM MANAGEMENT
    // -------------------------------------------------
    public boolean addItem(int itemId, int count) {
        return addItem(itemId, count, 0);
    }

    public boolean addItem(int itemId, int count, int quality) {
        if (itemId < 0 || itemId >= MAX_ITEM_TYPES || count <= 0) return false;
        int maxStack = ITEM_DATA[itemId][3];

        for (int i = 0; i < itemSlots; i++) {
            if (itemIds[i] == itemId && itemQuality[i] == quality) {
                int newCount = Math.min(itemCounts[i] + count, maxStack);
                itemCounts[i] = newCount;
                return true;
            }
        }

        if (itemSlots >= GameConfig.MAX_INVENTORY) return false;
        itemIds[itemSlots]     = itemId;
        itemCounts[itemSlots]  = Math.min(count, maxStack);
        itemQuality[itemSlots] = quality;
        itemSlots++;
        return true;
    }

    public boolean removeItem(int itemId, int count) {
        for (int i = 0; i < itemSlots; i++) {
            if (itemIds[i] == itemId) {
                itemCounts[i] -= count;
                if (itemCounts[i] <= 0) {
                    for (int j = i; j < itemSlots - 1; j++) {
                        itemIds[j]     = itemIds[j+1];
                        itemCounts[j]  = itemCounts[j+1];
                        itemQuality[j] = itemQuality[j+1];
                    }
                    itemSlots--;
                    itemIds[itemSlots] = -1; itemCounts[itemSlots] = 0;
                }
                return true;
            }
        }
        return false;
    }

    public boolean hasItem(int itemId) { return getItemCount(itemId) > 0; }

    public int getItemCount(int itemId) {
        for (int i = 0; i < itemSlots; i++) {
            if (itemIds[i] == itemId) return itemCounts[i];
        }
        return 0;
    }

    public int getSlotCount() { return itemSlots; }

    public int getItemAt(int slot) {
        if (slot < 0 || slot >= itemSlots) return -1;
        return itemIds[slot];
    }
    public int getCountAt(int slot) {
        if (slot < 0 || slot >= itemSlots) return 0;
        return itemCounts[slot];
    }
    public int getQualityAt(int slot) {
        if (slot < 0 || slot >= itemSlots) return 0;
        return itemQuality[slot];
    }

    // -------------------------------------------------
    //  USE ITEM
    // -------------------------------------------------
    public boolean useItem(int itemId, PlayerData player) {
        if (!hasItem(itemId)) return false;
        int type = getItemType(itemId);
        int value = getItemValue(itemId);

        if (type == ITYPE_CONSUMABLE) {
            switch (itemId) {
                case ITEM_POTION:
                case ITEM_HI_POTION:
                case ITEM_MEGA_POTION:
                    player.heal(value); break;
                case ITEM_ETHER:
                    player.restoreMp(value); break;
                case ITEM_ANTIDOTE:
                    player.removeStatus(PlayerData.STATUS_POISON); break;
                case ITEM_PHOENIX:
                    if (!player.isAlive) { player.isAlive = true; player.hp = player.maxHp / 2; } break;
                case ITEM_TENT:
                    player.fullRestore(); break;
                case ITEM_ELIXIR:
                    player.fullRestore(); break;
                case ITEM_SMOKE_BOMB:
                    // Handled by battle system (set escape flag)
                    break;
                default: return false;
            }
            removeItem(itemId, 1);
            return true;
        }
        return false;
    }

    // -------------------------------------------------
    //  SORT (NEW)
    // -------------------------------------------------
    public void sort(int mode) {
        // Simple bubble sort for J2ME compatibility
        for (int i = 0; i < itemSlots - 1; i++) {
            for (int j = 0; j < itemSlots - 1 - i; j++) {
                boolean swap = false;
                switch (mode) {
                    case SORT_TYPE:
                        swap = getItemType(itemIds[j]) > getItemType(itemIds[j+1]);
                        break;
                    case SORT_VALUE:
                        swap = getItemValue(itemIds[j]) < getItemValue(itemIds[j+1]);
                        break;
                    case SORT_NAME:
                        String a = getItemName(itemIds[j]);
                        String b = getItemName(itemIds[j+1]);
                        swap = a.compareTo(b) > 0;
                        break;
                }
                if (swap) {
                    int ti = itemIds[j]; itemIds[j] = itemIds[j+1]; itemIds[j+1] = ti;
                    int tc = itemCounts[j]; itemCounts[j] = itemCounts[j+1]; itemCounts[j+1] = tc;
                    int tq = itemQuality[j]; itemQuality[j] = itemQuality[j+1]; itemQuality[j+1] = tq;
                }
            }
        }
    }

    // -------------------------------------------------
    //  FILTER (NEW)
    // -------------------------------------------------
    public void setFilter(int type) { filterType = type; }

    public int getFilteredCount() {
        if (filterType < 0) return itemSlots;
        int count = 0;
        for (int i = 0; i < itemSlots; i++) {
            if (getItemType(itemIds[i]) == filterType) count++;
        }
        return count;
    }

    public int getFilteredItem(int filteredIndex) {
        if (filterType < 0) return getItemAt(filteredIndex);
        int count = 0;
        for (int i = 0; i < itemSlots; i++) {
            if (getItemType(itemIds[i]) == filterType) {
                if (count == filteredIndex) return itemIds[i];
                count++;
            }
        }
        return -1;
    }

    // -------------------------------------------------
    //  EQUIPMENT
    // -------------------------------------------------
    public boolean canEquip(int itemId) {
        int type = getItemType(itemId);
        return type >= ITYPE_WEAPON && type <= ITYPE_OFFHAND;
    }

    public void equip(int itemId, PlayerData player) {
        if (!hasItem(itemId) || !canEquip(itemId)) return;
        int type = getItemType(itemId);
        int slot = -1;
        switch (type) {
            case ITYPE_WEAPON:    slot = PlayerData.EQUIP_WEAPON;  break;
            case ITYPE_OFFHAND:   slot = PlayerData.EQUIP_OFFHAND; break;
            case ITYPE_ARMOR:     slot = PlayerData.EQUIP_ARMOR;   break;
            case ITYPE_HELM:      slot = PlayerData.EQUIP_HELM;    break;
            case ITYPE_ACCESSORY: slot = PlayerData.EQUIP_ACCESS1; break;
            case ITYPE_BOOTS:     slot = PlayerData.EQUIP_BOOTS;   break;
        }
        if (slot < 0) return;
        int oldItem = player.equipment[slot];
        if (oldItem >= 0) addItem(oldItem, 1);
        removeItem(itemId, 1);
        player.equipment[slot] = itemId;
    }

    public void unequip(int slot, PlayerData player) {
        if (slot < 0 || slot >= GameConfig.MAX_EQUIPMENT) return;
        int itemId = player.equipment[slot];
        if (itemId >= 0) { addItem(itemId, 1); player.equipment[slot] = -1; }
    }

    public int getEquipmentBonus(PlayerData player, int statType) {
        int bonus = 0;
        for (int i = 0; i < GameConfig.MAX_EQUIPMENT; i++) {
            int itemId = player.equipment[i];
            if (itemId < 0) continue;
            int type = getItemType(itemId);
            int value = getItemValue(itemId);

            if (statType == 0) { // ATK
                if (type == ITYPE_WEAPON) bonus += value;
                if (itemId == ITEM_RING_ATK) bonus += value;
            } else if (statType == 1) { // DEF
                if (type == ITYPE_ARMOR || type == ITYPE_HELM || type == ITYPE_OFFHAND) bonus += value;
                if (itemId == ITEM_RING_DEF) bonus += value;
            } else if (statType == 2) { // SPD
                if (type == ITYPE_BOOTS) bonus += value;
                if (itemId == ITEM_RING_SPD) bonus += value;
                if (itemId == ITEM_BOOTS_SPEED) bonus += value;
            } else if (statType == 3) { // MAG
                if (itemId == ITEM_WAND)   bonus += 8;
                if (itemId == ITEM_STAFF)  bonus += 5;
                if (itemId == ITEM_ROBE)   bonus += 3;
                if (itemId == ITEM_AMULET) bonus += value;
            } else if (statType == 4) { // LUK
                if (itemId == ITEM_TALISMAN) bonus += value;
                if (itemId == ITEM_CROWN)    bonus += 3;
            }
        }
        return bonus;
    }

    // -------------------------------------------------
    //  GOLD
    // -------------------------------------------------
    public int getGold()              { return gold; }
    public void addGold(int amount)   { gold = Math.min(9999999, gold + amount); }
    public boolean spendGold(int amt) {
        if (gold >= amt) { gold -= amt; return true; }
        return false;
    }

    // -------------------------------------------------
    //  STATIC HELPERS
    // -------------------------------------------------
    public static int getItemType(int id) {
        if (id < 0 || id >= MAX_ITEM_TYPES) return -1;
        return ITEM_DATA[id][0];
    }
    public static int getItemValue(int id) {
        if (id < 0 || id >= MAX_ITEM_TYPES) return 0;
        return ITEM_DATA[id][1];
    }
    public static int getItemPrice(int id) {
        if (id < 0 || id >= MAX_ITEM_TYPES) return 0;
        return ITEM_DATA[id][2];
    }
    public static String getItemName(int id) {
        if (id < 0 || id >= ITEM_NAMES.length) return "???";
        return ITEM_NAMES[id];
    }
    public static String getItemDesc(int id) {
        if (id < 0 || id >= ITEM_DESC.length) return "";
        return ITEM_DESC[id];
    }
    public static boolean isKeyItem(int id) { return getItemType(id) == ITYPE_KEY; }
    public static boolean isMaterial(int id){ return getItemType(id) == ITYPE_MATERIAL; }

    // -------------------------------------------------
    //  SAVE / LOAD
    // -------------------------------------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(gold);
        dos.writeInt(itemSlots);
        for (int i = 0; i < itemSlots; i++) {
            dos.writeInt(itemIds[i]);
            dos.writeInt(itemCounts[i]);
            dos.writeInt(itemQuality[i]);
        }
        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        gold = dis.readInt();
        itemSlots = dis.readInt();
        for (int i = 0; i < itemSlots; i++) {
            itemIds[i]     = dis.readInt();
            itemCounts[i]  = dis.readInt();
            // Quality may not be in older saves
            try { itemQuality[i] = dis.readInt(); } catch (Exception e) { itemQuality[i] = 0; }
        }
    }
}
