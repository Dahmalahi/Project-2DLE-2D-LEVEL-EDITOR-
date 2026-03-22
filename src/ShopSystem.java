import java.io.*;
import javax.microedition.lcdui.*;

/**
 * ShopSystem.java - NEW v2.0
 * Full shop with buy/sell, haggling, NPC-level reputation discounts,
 * stock limits, shop categories, INN rest functionality, and a
 * full-screen shop UI renderer.
 */
public class ShopSystem {

    // -------------------------------------------------
    //  SHOP TYPES
    // -------------------------------------------------
    public static final int SHOP_GENERAL  = 0;
    public static final int SHOP_WEAPON   = 1;
    public static final int SHOP_ARMOR    = 2;
    public static final int SHOP_MAGIC    = 3;
    public static final int SHOP_INN      = 4;

    // -------------------------------------------------
    //  PREDEFINED SHOPS
    // -------------------------------------------------
    // Starter Town General Store
    private static final int[] SHOP_GENERAL_ITEMS = {
        InventorySystem.ITEM_POTION, InventorySystem.ITEM_ANTIDOTE,
        InventorySystem.ITEM_ETHER,  InventorySystem.ITEM_TENT
    };
    // Weapon Shop
    private static final int[] SHOP_WEAPON_ITEMS = {
        InventorySystem.ITEM_SWORD, InventorySystem.ITEM_AXE,
        InventorySystem.ITEM_STAFF, InventorySystem.ITEM_BOW,
        InventorySystem.ITEM_DAGGER
    };
    // Armor Shop
    private static final int[] SHOP_ARMOR_ITEMS = {
        InventorySystem.ITEM_LEATHER,  InventorySystem.ITEM_CHAINMAIL,
        InventorySystem.ITEM_PLATE,    InventorySystem.ITEM_ROBE,
        InventorySystem.ITEM_CAP,      InventorySystem.ITEM_HELM_IRON
    };
    // Magic Shop
    private static final int[] SHOP_MAGIC_ITEMS = {
        InventorySystem.ITEM_ETHER, InventorySystem.ITEM_HI_POTION,
        InventorySystem.ITEM_PHOENIX, InventorySystem.ITEM_RING_ATK,
        InventorySystem.ITEM_RING_DEF, InventorySystem.ITEM_AMULET
    };

    private static final int[][] ALL_SHOP_ITEMS = {
        SHOP_GENERAL_ITEMS, SHOP_WEAPON_ITEMS,
        SHOP_ARMOR_ITEMS, SHOP_MAGIC_ITEMS
    };

    // -------------------------------------------------
    //  CURRENT SHOP STATE
    // -------------------------------------------------
    private int currentShopType;
    private int[] currentItems;
    private int[] stockRemaining;  // -1 = unlimited
    private int currentReputation; // affects prices

    // UI state
    private boolean isOpen;
    private boolean isSelling;
    private int selection;
    private int haggleAttempts;
    private boolean haggleActive;
    private int haggleDiscount;
    private String statusMsg;
    private long statusTimer;

    // INN cost
    private static final int INN_COST_BASE = 50;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public ShopSystem() {
        stockRemaining = new int[GameConfig.MAX_SHOP_ITEMS];
        isOpen = false; isSelling = false; selection = 0;
        haggleAttempts = 0; haggleActive = false; haggleDiscount = 0;
        statusMsg = ""; statusTimer = 0;
    }

    // -------------------------------------------------
    //  OPEN / CLOSE
    // -------------------------------------------------
    public void openShop(int shopType, int reputation) {
        currentShopType = shopType;
        currentReputation = Math.max(-100, Math.min(100, reputation));
        isOpen = true; isSelling = false; selection = 0;
        haggleAttempts = 0; haggleActive = false; haggleDiscount = 0;

        if (shopType >= 0 && shopType < ALL_SHOP_ITEMS.length) {
            currentItems = ALL_SHOP_ITEMS[shopType];
        } else {
            currentItems = new int[0];
        }
        // Reset stock (unlimited for general items)
        for (int i = 0; i < stockRemaining.length; i++) stockRemaining[i] = -1;
    }

    public boolean isOpen() { return isOpen; }

    public void close() { isOpen = false; }

    // -------------------------------------------------
    //  PRICE CALCULATION
    // -------------------------------------------------
    public int getBuyPrice(int itemId) {
        int basePrice = InventorySystem.getItemPrice(itemId);
        // Reputation discount: up to 20% off at +100 rep
        int discount = (currentReputation > 0)
                     ? currentReputation * GameConfig.HAGGLE_MAX / 100 / 5
                     : 0;
        // Haggle discount
        discount += haggleDiscount;
        discount = Math.min(discount, GameConfig.HAGGLE_MAX);
        return Math.max(1, basePrice * (100 - discount) / 100);
    }

    public int getSellPrice(int itemId) {
        if (InventorySystem.isKeyItem(itemId)) return 0;
        return Math.max(1, InventorySystem.getItemPrice(itemId)
                        * GameConfig.SELL_RATE / 100);
    }

    // -------------------------------------------------
    //  BUY / SELL
    // -------------------------------------------------
    public boolean buy(int itemId, InventorySystem inv,
                       PlayerData player, int quantity) {
        int price = getBuyPrice(itemId) * quantity;
        if (!inv.spendGold(price)) {
            setStatus("Not enough gold!");
            return false;
        }
        if (!inv.addItem(itemId, quantity)) {
            inv.addGold(price); // refund
            setStatus("Inventory full!");
            return false;
        }
        setStatus("Purchased " + InventorySystem.getItemName(itemId) + " x" + quantity);
        haggleAttempts = 0; haggleDiscount = 0;
        return true;
    }

    public boolean sell(int itemId, InventorySystem inv, int quantity) {
        if (InventorySystem.isKeyItem(itemId)) {
            setStatus("Can't sell key items!");
            return false;
        }
        if (!inv.hasItem(itemId)) { setStatus("You don't have that!"); return false; }
        int price = getSellPrice(itemId) * quantity;
        if (!inv.removeItem(itemId, quantity)) { setStatus("Error!"); return false; }
        inv.addGold(price);
        setStatus("Sold for " + price + " gold!");
        return true;
    }

    // -------------------------------------------------
    //  HAGGLE (NEW)
    // -------------------------------------------------
    public boolean haggle(int randomSeed, PlayerData player) {
        if (haggleAttempts >= 3) { setStatus("They're done haggling!"); return false; }
        haggleAttempts++;
        // Chance based on LUK and relation
        int chance = 40 + player.luk / 5 + currentReputation / 5;
        if (Math.abs(randomSeed) % 100 < chance) {
            int discount = 3 + Math.abs(randomSeed) % 8;
            haggleDiscount = Math.min(haggleDiscount + discount, GameConfig.HAGGLE_MAX);
            setStatus("Haggled " + discount + "% off! (Total: -" + haggleDiscount + "%)");
            return true;
        } else {
            setStatus("They didn't budge!");
            return false;
        }
    }

    // -------------------------------------------------
    //  INN REST
    // -------------------------------------------------
    public boolean restAtInn(PlayerData player, InventorySystem inv, int floorDepth) {
        int cost = INN_COST_BASE + floorDepth * 20;
        if (!inv.spendGold(cost)) {
            setStatus("Need " + cost + " gold to rest!");
            return false;
        }
        player.fullRestore();
        setStatus("Rested fully! -" + cost + "G");
        return true;
    }

    // -------------------------------------------------
    //  UI INPUT
    // -------------------------------------------------
    public void handleInput(int key, InventorySystem inv,
                            PlayerData player, int randomSeed) {
        if (!isOpen) return;
        int itemCount = isSelling ? inv.getSlotCount() : (currentItems != null ? currentItems.length : 0);

        final int KEY_UP    = 1;
        final int KEY_DOWN  = 2;
        final int KEY_LEFT  = 3;
        final int KEY_RIGHT = 4;
        final int KEY_FIRE  = 5;
        final int KEY_BACK  = 6;
        final int KEY_AUX   = 7;  // haggle / switch mode

        if (key == KEY_UP)   selection = Math.max(0, selection - 1);
        if (key == KEY_DOWN) selection = Math.min(Math.max(0, itemCount - 1), selection + 1);

        if (key == KEY_LEFT || key == KEY_RIGHT) {
            isSelling = !isSelling;
            selection = 0;
            setStatus(isSelling ? "Sell mode" : "Buy mode");
        }

        if (key == KEY_FIRE) {
            if (isSelling) {
                if (selection < inv.getSlotCount()) {
                    sell(inv.getItemAt(selection), inv, 1);
                }
            } else {
                if (currentItems != null && selection < currentItems.length) {
                    buy(currentItems[selection], inv, player, 1);
                }
            }
        }

        if (key == KEY_AUX && !isSelling) {
            haggle(randomSeed, player);
        }

        if (key == KEY_BACK) close();
    }

    // -------------------------------------------------
    //  RENDER
    // -------------------------------------------------
    public void draw(Graphics g, int screenW, int screenH,
                     InventorySystem inv, PlayerData player) {
        if (!isOpen) return;

        // Background dim
        g.setColor(0, 0, 0);
        for (int y = 0; y < screenH; y += 2) g.drawLine(0, y, screenW, y);

        int boxW = screenW - 8;
        int boxH = screenH - 8;
        int bx = 4, by = 4;

        // Main panel
        g.setColor(0x0A1A0A);
        g.fillRect(bx, by, boxW, boxH);
        g.setColor(0x44AA44);
        g.drawRect(bx, by, boxW - 1, boxH - 1);
        g.drawRect(bx + 1, by + 1, boxW - 3, boxH - 3);

        // Title
        Font bf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        g.setFont(bf);
        g.setColor(0x88FF88);
        String[] shopTitles = {"General Store","Weapon Shop","Armor Shop","Magic Shop","Inn"};
        String title = currentShopType < shopTitles.length ? shopTitles[currentShopType] : "Shop";
        g.drawString(title, bx + boxW / 2, by + 4, Graphics.TOP | Graphics.HCENTER);

        // Mode tabs
        g.setFont(sf);
        int tabY = by + 20;
        g.setColor(isSelling ? 0x444444 : 0x226622);
        g.fillRect(bx + 5, tabY, boxW / 2 - 8, 13);
        g.setColor(isSelling ? 0x226622 : 0x444444);
        g.fillRect(bx + boxW / 2, tabY, boxW / 2 - 8, 13);

        g.setColor(!isSelling ? 0xFFFFFF : 0xAAAAAA);
        g.drawString("BUY", bx + boxW / 4, tabY + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(isSelling ? 0xFFFFFF : 0xAAAAAA);
        g.drawString("SELL", bx + boxW * 3 / 4, tabY + 1, Graphics.TOP | Graphics.HCENTER);

        // Item list
        int listY = tabY + 16;
        int[] items = isSelling ? null : currentItems;
        int count = isSelling ? inv.getSlotCount() : (items != null ? items.length : 0);
        int visRows = (boxH - 80) / 16;
        int startIdx = Math.max(0, selection - visRows + 1);

        for (int i = startIdx; i < count && i < startIdx + visRows; i++) {
            int itemId = isSelling ? inv.getItemAt(i) : items[i];
            boolean sel = i == selection;
            int ry = listY + (i - startIdx) * 16;

            if (sel) {
                g.setColor(0x1A4A1A);
                g.fillRect(bx + 4, ry, boxW - 8, 15);
                g.setColor(0x44FF44);
                g.drawRect(bx + 4, ry, boxW - 9, 14);
            }

            g.setColor(sel ? 0xFFFFFF : 0xAADDAA);
            String name = InventorySystem.getItemName(itemId);
            int price = isSelling ? getSellPrice(itemId) : getBuyPrice(itemId);
            String qty = isSelling ? " x" + inv.getItemCount(itemId) : "";

            g.drawString(name + qty, bx + 10, ry + 1, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xFFDD44);
            g.drawString(price + "G", bx + boxW - 10, ry + 1, Graphics.TOP | Graphics.RIGHT);
        }

        // Footer: gold + discount + status
        int footY = by + boxH - 44;
        g.setColor(0x112211);
        g.fillRect(bx + 2, footY, boxW - 4, 40);
        g.setColor(0x44AA44);
        g.drawLine(bx + 2, footY, bx + boxW - 3, footY);

        g.setFont(sf);
        g.setColor(0xFFDD44);
        g.drawString("Gold: " + inv.getGold(), bx + 8, footY + 2, Graphics.TOP | Graphics.LEFT);

        if (haggleDiscount > 0) {
            g.setColor(0xFF8844);
            g.drawString("-" + haggleDiscount + "% discount", bx + boxW / 2, footY + 2,
                         Graphics.TOP | Graphics.HCENTER);
        }

        g.setColor(0x88FF88);
        g.drawString("[</> mode] [OK=buy/sell] [AUX=haggle]", bx + boxW / 2, footY + 14,
                     Graphics.TOP | Graphics.HCENTER);

        // Status msg
        if (statusMsg.length() > 0 && System.currentTimeMillis() - statusTimer < 2500) {
            g.setColor(0xFFFFAA);
            g.drawString(statusMsg, bx + boxW / 2, footY + 26, Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusTimer = System.currentTimeMillis();
    }

    // -------------------------------------------------
    //  SAVE / LOAD (minimal - just reputation & haggle)
    // -------------------------------------------------
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(currentReputation);
        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        currentReputation = dis.readInt();
    }
}
