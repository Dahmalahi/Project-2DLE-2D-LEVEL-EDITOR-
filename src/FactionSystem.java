import java.io.*;

/**
 * FactionSystem.java — NEW in v2.0
 *
 * Manages reputation with 10 factions. Reputation affects:
 *  - NPC dialogue options and shop prices
 *  - Whether guards/enemies are hostile
 *  - Access to faction-exclusive quests and areas
 *  - Ability to recruit faction NPCs as allies
 *
 * Factions have relationships with each other: impressing one can anger another.
 */
public class FactionSystem {

    // =========================================================
    //  FACTION RELATIONSHIP MATRIX
    //  [factionA][factionB] = reputation change when player gains rep with A
    //  Positive = friendly, negative = rivals
    // =========================================================
    private static final int[][] FACTION_RELATIONS = {
        //   VIL  GRD  MER  THI  MAG  KNT  BND  MON  UND  ELE
        {     0,   5,   3,  -3,   0,   3,  -5,  -2,  -2,   0 }, // VILLAGERS
        {     3,   0,   2,  -5,   2,   8,  -8,  -5,  -5,  -2 }, // GUARDS
        {     2,   2,   0,  -4,   2,   2,  -4,  -2,  -3,   0 }, // MERCHANTS
        {    -5,  -8,  -4,   0,   0,  -8,   5,   3,   2,   0 }, // THIEVES
        {     1,   2,   2,   0,   0,   2,  -3,  -2,  -4,   5 }, // MAGES
        {     3,   8,   2,  -8,   2,   0,  -8,  -5,  -8,  -2 }, // KNIGHTS
        {    -5,  -8,  -4,   5,  -3,  -8,   0,   5,   3,  -2 }, // BANDITS
        {    -2,  -5,  -2,   3,  -2,  -5,   5,   0,   3,   0 }, // MONSTERS
        {    -2,  -5,  -3,   2,  -4,  -8,   3,   3,   0,   2 }, // UNDEAD
        {     0,  -2,   0,   0,   5,  -2,  -2,   0,   2,   0 }, // ELEMENTALS
    };

    // Faction names
    public static final String[] FACTION_NAMES = {
        "Villagers", "Guards", "Merchants", "Thieves Guild",
        "Mages Order", "Knights", "Bandits", "Monsters",
        "Undead", "Elementals"
    };

    // =========================================================
    //  DATA
    // =========================================================
    private int[] reputation;     // -100 to +100 per faction
    private int[] rankLevel;      // cached tier 0-6

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public FactionSystem() {
        reputation = new int[GameConfig.MAX_FACTIONS];
        rankLevel  = new int[GameConfig.MAX_FACTIONS];
        // Start neutral with villagers, slightly friendly with merchants
        reputation[GameConfig.FACTION_MERCHANTS] = 10;
    }

    // =========================================================
    //  REPUTATION CHANGE
    // =========================================================
    /**
     * Change reputation with a faction, and apply ripple effects to related factions.
     * @param factionId  Which faction (FACTION_* constant)
     * @param delta      Amount to change (+ or -)
     */
    public void changeRep(int factionId, int delta) {
        if (factionId < 0 || factionId >= GameConfig.MAX_FACTIONS) return;

        reputation[factionId] = GameConfig.clamp(
            reputation[factionId] + delta,
            GameConfig.REP_HATED, GameConfig.REP_EXALTED);

        updateRank(factionId);

        // Apply ripple to allied/rival factions
        int[] relations = FACTION_RELATIONS[factionId];
        for (int i = 0; i < GameConfig.MAX_FACTIONS; i++) {
            if (i == factionId) continue;
            if (relations[i] == 0) continue;
            // Ripple is half-strength
            int ripple = delta * relations[i] / 20;
            if (ripple != 0) {
                reputation[i] = GameConfig.clamp(
                    reputation[i] + ripple,
                    GameConfig.REP_HATED, GameConfig.REP_EXALTED);
                updateRank(i);
            }
        }
    }

    public int getRep(int factionId) {
        if (factionId < 0 || factionId >= GameConfig.MAX_FACTIONS) return 0;
        return reputation[factionId];
    }

    public String getRepLabel(int factionId) {
        return GameConfig.getRepLabel(getRep(factionId));
    }

    public String getFactionName(int factionId) {
        if (factionId < 0 || factionId >= FACTION_NAMES.length) return "Unknown";
        return FACTION_NAMES[factionId];
    }

    // =========================================================
    //  RANK
    // =========================================================
    private void updateRank(int factionId) {
        int rep = reputation[factionId];
        int rank;
        if      (rep <= GameConfig.REP_HATED)      rank = 0;
        else if (rep <= GameConfig.REP_HOSTILE)     rank = 1;
        else if (rep <= GameConfig.REP_UNFRIENDLY)  rank = 2;
        else if (rep <  GameConfig.REP_FRIENDLY)    rank = 3;
        else if (rep <  GameConfig.REP_HONORED)     rank = 4;
        else if (rep <  GameConfig.REP_EXALTED)     rank = 5;
        else                                        rank = 6;
        rankLevel[factionId] = rank;
    }

    public int getRank(int factionId) {
        if (factionId < 0 || factionId >= GameConfig.MAX_FACTIONS) return 3;
        return rankLevel[factionId];
    }

    // =========================================================
    //  QUERIES
    // =========================================================
    public boolean isHostile(int factionId) {
        return getRep(factionId) <= GameConfig.REP_HOSTILE;
    }

    public boolean isFriendly(int factionId) {
        return getRep(factionId) >= GameConfig.REP_FRIENDLY;
    }

    public boolean isExalted(int factionId) {
        return getRep(factionId) >= GameConfig.REP_EXALTED;
    }

    /** Shop discount % for friendly/honored/exalted factions. */
    public int getShopDiscount(int factionId) {
        int rep = getRep(factionId);
        if (rep >= GameConfig.REP_EXALTED)  return 20;
        if (rep >= GameConfig.REP_HONORED)  return 10;
        if (rep >= GameConfig.REP_FRIENDLY) return 5;
        if (rep <= GameConfig.REP_HOSTILE)  return -20; // surcharge
        return 0;
    }

    /** Whether an NPC of this faction will talk to the player. */
    public boolean willTalk(int factionId) {
        return getRep(factionId) > GameConfig.REP_HATED;
    }

    // =========================================================
    //  SAVE / LOAD
    // =========================================================
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(GameConfig.MAX_FACTIONS);
        for (int i = 0; i < GameConfig.MAX_FACTIONS; i++) {
            dos.writeInt(reputation[i]);
        }
        dos.flush();
        return baos.toByteArray();
    }

    public void fromBytes(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        int count = dis.readInt();
        for (int i = 0; i < count && i < GameConfig.MAX_FACTIONS; i++) {
            reputation[i] = dis.readInt();
            updateRank(i);
        }
    }

    /** Build summary string for HUD or debug. */
    public String getSummary() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < GameConfig.MAX_FACTIONS; i++) {
            sb.append(FACTION_NAMES[i]);
            sb.append(": ");
            sb.append(GameConfig.getRepLabel(reputation[i]));
            sb.append(" (");
            sb.append(reputation[i]);
            sb.append(")\n");
        }
        return sb.toString();
    }
}
