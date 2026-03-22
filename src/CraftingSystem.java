import java.io.*;

/**
 * CraftingSystem.java - NEW v2.0
 * Full crafting system: recipes with multiple ingredients,
 * quality tiers, failure chance, crafting level gating,
 * discovered vs. undiscovered recipes, and equipment forging.
 */
public class CraftingSystem {

    // -------------------------------------------------
    //  RECIPE TYPES
    // -------------------------------------------------
    public static final int RTYPE_CONSUMABLE = 0;
    public static final int RTYPE_WEAPON     = 1;
    public static final int RTYPE_ARMOR      = 2;
    public static final int RTYPE_ACCESSORY  = 3;
    public static final int RTYPE_SPECIAL    = 4;   // event-specific

    // -------------------------------------------------
    //  QUALITY TIERS
    // -------------------------------------------------
    public static final int QUALITY_FAIL    = -1;
    public static final int QUALITY_NORMAL  = 0;
    public static final int QUALITY_FINE    = 1;   // +10% stat bonus
    public static final int QUALITY_RARE    = 2;   // +25% stat bonus
    public static final int QUALITY_MASTER  = 3;   // +50% stat bonus

    public static final String[] QUALITY_NAMES = { "Normal","Fine","Rare","Masterwork" };
    public static final int[]    QUALITY_BONUS  = { 0, 10, 25, 50 };  // % stat bonus

    // -------------------------------------------------
    //  RECIPE DEFINITIONS
    //  Each recipe: [resultItemId, resultCount, levelReq, rtype,
    //               ingr1Id, ingr1Qty, ingr2Id, ingr2Qty,
    //               ingr3Id, ingr3Qty, ingr4Id, ingr4Qty]
    //  Ingredient -1 = unused
    // -------------------------------------------------
    // Using InventorySystem item IDs
    private static final int[][] RECIPE_DATA = {
        // [resultId, qty, lvlReq, type, i1Id, i1Qty, i2Id, i2Qty, i3Id, i3Qty, i4Id, i4Qty]
        { 0,  2, 1, RTYPE_CONSUMABLE,  3,2, -1,0, -1,0, -1,0 },  // 2x Potion from 2x Antidote herb
        { 1,  1, 3, RTYPE_CONSUMABLE,  0,3, -1,0, -1,0, -1,0 },  // Hi-Potion from 3x Potion
        { 2,  1, 2, RTYPE_CONSUMABLE,  3,1,  0,1, -1,0, -1,0 },  // Ether from herbs
        { 4,  1, 5, RTYPE_CONSUMABLE,  0,5,  1,2, -1,0, -1,0 },  // Phoenix from 5 Potion+2 Hi-Potion
        {10,  1, 3, RTYPE_WEAPON,     -1,0, -1,0, -1,0, -1,0 },  // Sword (forge, needs smithy)
        {11,  1, 5, RTYPE_WEAPON,     10,1, -1,0, -1,0, -1,0 },  // Axe from Sword upgrade
        {12,  1, 2, RTYPE_WEAPON,     -1,0, -1,0, -1,0, -1,0 },  // Staff
        {20,  1, 2, RTYPE_ARMOR,      -1,0, -1,0, -1,0, -1,0 },  // Leather Armor
        {21,  1, 4, RTYPE_ARMOR,      20,1, -1,0, -1,0, -1,0 },  // Chainmail from Leather
        {22,  1, 7, RTYPE_ARMOR,      21,1, -1,0, -1,0, -1,0 },  // Plate from Chain
        {40,  1, 4, RTYPE_ACCESSORY,  -1,0, -1,0, -1,0, -1,0 },  // Ring+ATK
        {43,  1, 8, RTYPE_ACCESSORY,  40,1, 41,1, 42,1, -1,0 },  // Amulet from all 3 rings
        { 5,  1, 3, RTYPE_CONSUMABLE,  3,3,  0,2, -1,0, -1,0 },  // Tent from herbs+potions
    };

    private static final String[] RECIPE_NAMES = {
        "Brew Potion","Brew Hi-Potion","Brew Ether","Craft Phoenix",
        "Forge Sword","Upgrade Axe","Carve Staff",
        "Tan Leather","Weave Chainmail","Smith Plate Armor",
        "Craft ATK Ring","Craft Amulet","Stitch Tent"
    };

    public static final int MAX_RECIPES = 13;

    // -------------------------------------------------
    //  DISCOVERED STATE
    // -------------------------------------------------
    private boolean[] discovered;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public CraftingSystem() {
        discovered = new boolean[MAX_RECIPES];
        // First few recipes are known from the start
        discovered[0] = true;
        discovered[1] = true;
        discovered[2] = true;
        discovered[7] = true;
    }

    // -------------------------------------------------
    //  RECIPE QUERIES
    // -------------------------------------------------
    public int getRecipeCount() { return MAX_RECIPES; }

    public boolean isDiscovered(int recipeId) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return false;
        return discovered[recipeId];
    }

    public void discoverRecipe(int recipeId) {
        if (recipeId >= 0 && recipeId < MAX_RECIPES) discovered[recipeId] = true;
    }

    public String getRecipeName(int recipeId) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return "???";
        if (!discovered[recipeId]) return "???";
        return RECIPE_NAMES[recipeId];
    }

    public int getResultItemId(int recipeId) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return -1;
        return RECIPE_DATA[recipeId][0];
    }

    public int getResultCount(int recipeId) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return 0;
        return RECIPE_DATA[recipeId][1];
    }

    public int getLevelReq(int recipeId) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return 1;
        return RECIPE_DATA[recipeId][2];
    }

    // -------------------------------------------------
    //  CAN CRAFT CHECK
    // -------------------------------------------------
    public boolean canCraft(int recipeId, InventorySystem inv, PlayerData player) {
        if (!isDiscovered(recipeId)) return false;
        if (player.craftingLevel < getLevelReq(recipeId)) return false;
        return hasIngredients(recipeId, inv);
    }

    public boolean hasIngredients(int recipeId, InventorySystem inv) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return false;
        int[] rd = RECIPE_DATA[recipeId];
        // Check ingredient pairs at offsets 4,6,8,10
        for (int i = 0; i < GameConfig.MAX_RECIPE_INGR; i++) {
            int iId  = rd[4 + i * 2];
            int iQty = rd[5 + i * 2];
            if (iId < 0) continue;
            if (inv.getItemCount(iId) < iQty) return false;
        }
        return true;
    }

    public String[] getIngredientList(int recipeId) {
        if (recipeId < 0 || recipeId >= MAX_RECIPES) return new String[0];
        int[] rd = RECIPE_DATA[recipeId];
        String[] lines = new String[GameConfig.MAX_RECIPE_INGR];
        int count = 0;
        for (int i = 0; i < GameConfig.MAX_RECIPE_INGR; i++) {
            int iId  = rd[4 + i * 2];
            int iQty = rd[5 + i * 2];
            if (iId >= 0) {
                lines[count++] = InventorySystem.getItemName(iId) + " x" + iQty;
            }
        }
        String[] result = new String[count];
        for (int i = 0; i < count; i++) result[i] = lines[i];
        return result;
    }

    // -------------------------------------------------
    //  CRAFT
    // -------------------------------------------------
    public int craft(int recipeId, InventorySystem inv,
                     PlayerData player, int randomSeed) {
        if (!canCraft(recipeId, inv, player)) return QUALITY_FAIL;

        // Consume ingredients
        int[] rd = RECIPE_DATA[recipeId];
        for (int i = 0; i < GameConfig.MAX_RECIPE_INGR; i++) {
            int iId  = rd[4 + i * 2];
            int iQty = rd[5 + i * 2];
            if (iId >= 0) inv.removeItem(iId, iQty);
        }

        // Determine quality
        int quality = rollQuality(player.craftingLevel, randomSeed);

        if (quality == QUALITY_FAIL) {
            // May still get a lesser item
            int fallback = inv.addItem(rd[0], 1) ? 0 : -1;
            player.addCraftingExp(1);
            return QUALITY_FAIL;
        }

        int resultCount = rd[1] + (quality == QUALITY_MASTER ? 1 : 0);
        inv.addItem(rd[0], resultCount);

        // Grant crafting EXP
        int craftExp = 5 + getLevelReq(recipeId) * 3 + quality * 5;
        player.addCraftingExp(craftExp);
        player.craftCount++;

        return quality;
    }

    private int rollQuality(int craftLvl, int seed) {
        // Fail rate decreases with level
        int failChance = Math.max(0, GameConfig.CRAFT_FAIL_RATE - craftLvl);
        int r = Math.abs(seed) % 100;

        if (r < failChance) return QUALITY_FAIL;
        r -= failChance;
        int remaining = 100 - failChance;

        // Master: rare (5% base + 2% per craft level above 5)
        int masterChance = Math.max(0, craftLvl - 5) * 2 + 5;
        int rareChance   = 15 + craftLvl * 2;
        int fineChance   = 30 + craftLvl * 2;

        if (r < (masterChance * remaining / 100)) return QUALITY_MASTER;
        if (r < ((masterChance + rareChance) * remaining / 100)) return QUALITY_RARE;
        if (r < ((masterChance + rareChance + fineChance) * remaining / 100)) return QUALITY_FINE;
        return QUALITY_NORMAL;
    }

    // -------------------------------------------------
    //  SAVE / LOAD
    // -------------------------------------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(MAX_RECIPES);
        for (int i = 0; i < MAX_RECIPES; i++) dos.writeBoolean(discovered[i]);
        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        int count = dis.readInt();
        for (int i = 0; i < count && i < MAX_RECIPES; i++) discovered[i] = dis.readBoolean();
    }
}
