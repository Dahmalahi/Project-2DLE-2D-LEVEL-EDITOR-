import javax.microedition.lcdui.Graphics;

/**
 * DayNightSystem.java — NEW in v2.0
 *
 * Manages in-game time and the visual day/night overlay.
 *
 * Features:
 *  - 24 in-game hours per day, speed configurable
 *  - Lighting overlay tints screen dark at night
 *  - Per-tile light sources brighten local areas
 *  - Named time-of-day periods (dawn, morning, noon, dusk, night)
 *  - Affects NPC schedules, encounter rates, shop hours
 *  - Moon phase system (28-day cycle) for optional werewolf / undead mechanics
 *  - Calendar: day count, weekday, season (Spring/Summer/Autumn/Winter)
 */
public class DayNightSystem {

    // =========================================================
    //  TIME OF DAY PERIODS
    // =========================================================
    public static final int PERIOD_MIDNIGHT  = 0;
    public static final int PERIOD_LATE_NIGHT= 1;
    public static final int PERIOD_DAWN      = 2;
    public static final int PERIOD_MORNING   = 3;
    public static final int PERIOD_NOON      = 4;
    public static final int PERIOD_AFTERNOON = 5;
    public static final int PERIOD_DUSK      = 6;
    public static final int PERIOD_EVENING   = 7;
    public static final int PERIOD_NIGHT     = 8;

    public static final String[] PERIOD_NAMES = {
        "Midnight","Late Night","Dawn","Morning",
        "Noon","Afternoon","Dusk","Evening","Night"
    };

    // =========================================================
    //  MOON PHASES
    // =========================================================
    public static final int MOON_NEW        = 0;
    public static final int MOON_WAXING_CR  = 1;
    public static final int MOON_FIRST_QTR  = 2;
    public static final int MOON_WAXING_GB  = 3;
    public static final int MOON_FULL       = 4;
    public static final int MOON_WANING_GB  = 5;
    public static final int MOON_LAST_QTR   = 6;
    public static final int MOON_WANING_CR  = 7;
    public static final int MOON_CYCLE_DAYS = 28;

    public static final String[] MOON_NAMES = {
        "New Moon","Waxing Crescent","First Quarter","Waxing Gibbous",
        "Full Moon","Waning Gibbous","Last Quarter","Waning Crescent"
    };

    // =========================================================
    //  SEASONS (90 days each, 360-day year)
    // =========================================================
    public static final int SEASON_SPRING = 0;
    public static final int SEASON_SUMMER = 1;
    public static final int SEASON_AUTUMN = 2;
    public static final int SEASON_WINTER = 3;
    public static final String[] SEASON_NAMES = { "Spring","Summer","Autumn","Winter" };
    public static final int DAYS_PER_SEASON = 90;
    public static final int DAYS_PER_YEAR   = 360;

    // =========================================================
    //  WEEKDAY NAMES
    // =========================================================
    public static final String[] WEEKDAY_NAMES = {
        "Moonday","Fireday","Waterday","Earthday",
        "Windday","Holyday","Shadowday"
    };

    // =========================================================
    //  STATE
    // =========================================================
    private int hour;           // 0-23
    private int minute;         // 0-59
    private int tickCounter;    // counts up to DAY_TICKS_PER_HOUR
    private long totalDays;     // day count since game start
    private boolean paused;
    private int timeMultiplier; // 1=real time, 2=2× speed, etc. (game usually uses 10-60×)

    // Screen dimensions for overlay
    private int screenW;
    private int screenH;

    // Light source accumulation buffer (simplified: per-region brightness)
    private int ambientLight;   // 0-255

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public DayNightSystem(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        hour = 8;           // Start at 8 AM
        minute = 0;
        tickCounter = 0;
        totalDays = 0;
        paused = false;
        timeMultiplier = 20; // 20 game-ticks per real minute ≈ 24h day in ~8 real minutes
        updateAmbient();
    }

    // =========================================================
    //  UPDATE (call once per game tick / frame)
    // =========================================================
    public void update() {
        if (paused) return;

        tickCounter += timeMultiplier;
        if (tickCounter >= GameConfig.DAY_TICKS_PER_HOUR * 60) {
            tickCounter -= GameConfig.DAY_TICKS_PER_HOUR * 60;
            advanceMinute();
        }
    }

    private void advanceMinute() {
        minute++;
        if (minute >= 60) {
            minute = 0;
            hour++;
            if (hour >= 24) {
                hour = 0;
                totalDays++;
            }
            updateAmbient();
        }
    }

    private void updateAmbient() {
        int darkness = GameConfig.getDarknessAlpha(hour);
        ambientLight = 255 - darkness;
    }

    // =========================================================
    //  DRAW OVERLAY
    // =========================================================
    /**
     * Draw the day/night darkness overlay onto the screen.
     * Call AFTER rendering the map/sprites, BEFORE HUD.
     * Tile light sources can partially offset the overlay.
     */
    public void drawOverlay(Graphics g) {
        int darkness = 255 - ambientLight;
        if (darkness <= 0) return;

        g.setColor(0, 0, 20); // very dark blue-black
        // J2ME doesn't have native alpha — approximate with stipple or
        // semi-transparent fill using Graphics.fillRect with a clip trick.
        // For devices supporting alpha compositing we can use drawRGB.
        // Here we use a layered opacity approximation:
        int[] rgbRow = new int[screenW];
        for (int x = 0; x < screenW; x++) {
            // Premultiplied alpha blend with black: color = src * (1-alpha)
            // Source colour is the frame buffer (not accessible in MIDP 1),
            // so we just draw a dark overlay.
            int alpha = darkness;
            int r = 0 * alpha / 255;
            int gv = 0 * alpha / 255;
            int b = 20 * alpha / 255;
            rgbRow[x] = (alpha << 24) | (r << 16) | (gv << 8) | b;
        }
        try {
            // drawRGB is available in MIDP 2.0 (most J2ME phones)
            for (int y = 0; y < screenH; y++) {
                g.drawRGB(rgbRow, 0, screenW, 0, y, screenW, 1, true);
            }
        } catch (Exception e) {
            // Fallback: solid rectangle at reduced opacity (not ideal but safe)
            g.setColor(0x00000014);
            g.fillRect(0, 0, screenW, screenH);
        }
    }

    /**
     * Simple overlay using fill rectangle — lower quality but always works.
     * Use when drawRGB is not available.
     */
    public void drawOverlaySimple(Graphics g) {
        int darkness = 255 - ambientLight;
        if (darkness < 30) return;
        // Draw multiple semi-transparent rects (layered approximation)
        int layers = darkness / 85; // 0-3 layers
        for (int i = 0; i < layers; i++) {
            // Each layer uses a slightly different colour for atmosphere
            if (hour >= GameConfig.HOUR_DUSK || hour < GameConfig.HOUR_DAWN) {
                g.setColor(0x00001A); // night — dark blue
            } else {
                g.setColor(0x1A1400); // dusk — warm amber tint
            }
            g.fillRect(0, 0, screenW, screenH);
        }
    }

    // =========================================================
    //  QUERIES
    // =========================================================
    public int getHour()       { return hour; }
    public int getMinute()     { return minute; }
    public long getTotalDays() { return totalDays; }
    public int getAmbientLight() { return ambientLight; }
    public boolean isNight()   { return hour >= GameConfig.HOUR_NIGHT || hour < GameConfig.HOUR_DAWN; }
    public boolean isDawn()    { return hour == GameConfig.HOUR_DAWN; }
    public boolean isDay()     { return hour >= GameConfig.HOUR_MORNING && hour < GameConfig.HOUR_DUSK; }
    public boolean isDusk()    { return hour == GameConfig.HOUR_DUSK; }

    public int getPeriod() {
        if (hour == 0)                                       return PERIOD_MIDNIGHT;
        if (hour < GameConfig.HOUR_DAWN)                     return PERIOD_LATE_NIGHT;
        if (hour < GameConfig.HOUR_MORNING)                  return PERIOD_DAWN;
        if (hour < GameConfig.HOUR_NOON)                     return PERIOD_MORNING;
        if (hour == GameConfig.HOUR_NOON)                    return PERIOD_NOON;
        if (hour < GameConfig.HOUR_DUSK)                     return PERIOD_AFTERNOON;
        if (hour < GameConfig.HOUR_EVENING)                  return PERIOD_DUSK;
        if (hour < GameConfig.HOUR_NIGHT)                    return PERIOD_EVENING;
        return PERIOD_NIGHT;
    }

    public String getPeriodName() {
        return PERIOD_NAMES[getPeriod()];
    }

    public int getMoonPhase() {
        int dayInCycle = (int)(totalDays % MOON_CYCLE_DAYS);
        return (dayInCycle * 8) / MOON_CYCLE_DAYS;
    }

    public String getMoonPhaseName() {
        return MOON_NAMES[getMoonPhase()];
    }

    public boolean isFullMoon() {
        return getMoonPhase() == MOON_FULL;
    }

    public int getSeason() {
        int dayOfYear = (int)(totalDays % DAYS_PER_YEAR);
        return dayOfYear / DAYS_PER_SEASON;
    }

    public String getSeasonName() {
        return SEASON_NAMES[getSeason()];
    }

    public int getWeekday() {
        return (int)(totalDays % WEEKDAY_NAMES.length);
    }

    public String getWeekdayName() {
        return WEEKDAY_NAMES[getWeekday()];
    }

    /** Format time as "HH:MM". */
    public String getTimeString() {
        String h = hour < 10 ? "0" + hour : "" + hour;
        String m = minute < 10 ? "0" + minute : "" + minute;
        return h + ":" + m;
    }

    /** Format full date string. */
    public String getDateString() {
        int dayOfSeason = (int)(totalDays % DAYS_PER_SEASON) + 1;
        return getWeekdayName() + ", Day " + dayOfSeason + " of " + getSeasonName();
    }

    // =========================================================
    //  ENCOUNTER RATE MODIFIER
    // =========================================================
    /** Encounter rate multiplier * 100 — at night it's higher. */
    public int getEncounterMod() {
        if (isNight())  return 150; // 1.5x
        if (isDusk())   return 120;
        if (isDawn())   return 110;
        return 100;
    }

    // =========================================================
    //  CONTROLS
    // =========================================================
    public void pause()                     { paused = true; }
    public void resume()                    { paused = false; }
    public void setTimeMultiplier(int mult) { timeMultiplier = GameConfig.clamp(mult, 1, 300); }

    /** Jump to a specific hour (e.g. after inn rest). */
    public void setTime(int h, int m) {
        hour = GameConfig.clamp(h, 0, 23);
        minute = GameConfig.clamp(m, 0, 59);
        updateAmbient();
    }

    /** Advance time by a number of hours (e.g. teleport, sleep). */
    public void advanceHours(int hours) {
        int newHour = hour + hours;
        totalDays += newHour / 24;
        hour = newHour % 24;
        updateAmbient();
    }
}
