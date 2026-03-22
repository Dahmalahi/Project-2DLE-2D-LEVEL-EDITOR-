import javax.microedition.lcdui.Graphics;

public class WeatherSystem {

    // -------------------------------------------------
    //  WEATHER TYPES
    // -------------------------------------------------
    public static final int WEATHER_NONE      = 0;
    public static final int WEATHER_RAIN      = 1;
    public static final int WEATHER_SNOW      = 2;
    public static final int WEATHER_FOG       = 3;
    public static final int WEATHER_STORM     = 4;
    public static final int WEATHER_SANDSTORM = 5;
    public static final int WEATHER_LEAVES    = 6;
    public static final int WEATHER_SPARKLE   = 7;
    public static final int WEATHER_BLIZZARD  = 8;   // NEW: heavy snow + wind
    public static final int WEATHER_AURORA    = 9;   // NEW: northern lights (indoor overlay)

    // -------------------------------------------------
    //  OVERLAY TYPES
    // -------------------------------------------------
    public static final int OVERLAY_NONE      = 0;
    public static final int OVERLAY_NIGHT     = 1;
    public static final int OVERLAY_SEPIA     = 2;
    public static final int OVERLAY_DARK      = 3;
    public static final int OVERLAY_SUNSET    = 4;
    public static final int OVERLAY_UNDERWATER= 5;
    public static final int OVERLAY_DAWN      = 6;   // NEW: pinkish
    public static final int OVERLAY_NOON      = 7;   // NEW: bright warm
    public static final int OVERLAY_OVERCAST  = 8;   // NEW: grey

    // -------------------------------------------------
    //  PARTICLE DATA
    // -------------------------------------------------
    private static final int MAX_PARTICLES = 120;

    private int[] particleX;
    private int[] particleY;
    private int[] particleSpeedX;
    private int[] particleSpeedY;
    private int[] particleLife;
    private int[] particleType;
    private int[] particleSize;    // NEW: variable size per particle
    private int particleCount;

    // -------------------------------------------------
    //  STATE
    // -------------------------------------------------
    private int currentWeather;
    private int targetWeather;     // NEW: for smooth transitions
    private int weatherIntensity;
    private int targetIntensity;   // NEW: ramp intensity over time
    private int intensityRampTimer;

    private int currentOverlay;
    private int overlayAlpha;
    private int targetOverlay;
    private int transitionTimer;

    // Lightning (storm / blizzard)
    private int lightningTimer;
    private int lightningFlash;

    // Fog / sandstorm scroll
    private int fogOffset;
    private int fogDensity;

    // Aurora bands
    private int[] auroraBandY;
    private int[] auroraBandWidth;
    private int[] auroraBandColor;
    private int auroraTimer;

    // Wind (affects particle X velocity)
    private int windX;             // NEW: wind direction * strength (pixel/frame)

    // Screen size
    private int screenW;
    private int screenH;

    // Random seed
    private int randomSeed;

    // -------------------------------------------------
    //  CONSTRUCTOR
    // -------------------------------------------------
    public WeatherSystem(int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;

        particleX     = new int[MAX_PARTICLES];
        particleY     = new int[MAX_PARTICLES];
        particleSpeedX = new int[MAX_PARTICLES];
        particleSpeedY = new int[MAX_PARTICLES];
        particleLife  = new int[MAX_PARTICLES];
        particleType  = new int[MAX_PARTICLES];
        particleSize  = new int[MAX_PARTICLES];
        particleCount = 0;

        currentWeather = WEATHER_NONE;
        targetWeather  = WEATHER_NONE;
        weatherIntensity = 50;
        targetIntensity  = 50;
        intensityRampTimer = 0;

        currentOverlay = OVERLAY_NONE;
        overlayAlpha   = 0;
        targetOverlay  = OVERLAY_NONE;
        transitionTimer = 0;

        auroraBandY     = new int[4];
        auroraBandWidth = new int[4];
        auroraBandColor = new int[]{0x00FF88, 0x0088FF, 0xFF44FF, 0x44FFCC};
        for (int i = 0; i < 4; i++) auroraBandY[i] = i * (screenHeight / 4);

        randomSeed = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
    }

    // -------------------------------------------------
    //  WEATHER CONTROL
    // -------------------------------------------------
    public void setWeather(int type, int intensity) {
        if (type != currentWeather) {
            targetWeather = type;
            // Crossfade: drain current particles first, then swap
            targetIntensity = 0;
            intensityRampTimer = 30;
        }
        weatherIntensity = Math.max(0, Math.min(100, intensity));
        targetIntensity  = weatherIntensity;

        // If same type, just update intensity
        if (type == currentWeather) {
            initializeParticles();
        }

        // Wind defaults
        switch (type) {
            case WEATHER_STORM:     windX = -3; break;
            case WEATHER_SANDSTORM: windX = -5; break;
            case WEATHER_BLIZZARD:  windX = -4; break;
            case WEATHER_RAIN:      windX = -1; break;
            default:                windX =  0; break;
        }
    }

    public void setOverlay(int type) {
        if (type != currentOverlay) { targetOverlay = type; transitionTimer = 60; }
    }

    /** Set overlay automatically based on day period */
    public void setDayOverlay(int dayPeriod) {
        switch (dayPeriod) {
            case 0: setOverlay(OVERLAY_DAWN);      break;  // Dawn
            case 1: setOverlay(OVERLAY_NONE);      break;  // Morning — clear
            case 2: setOverlay(OVERLAY_NOON);      break;  // Noon
            case 3: setOverlay(OVERLAY_NONE);      break;  // Afternoon
            case 4: setOverlay(OVERLAY_SUNSET);    break;  // Evening
            case 5: setOverlay(OVERLAY_SUNSET);    break;  // Dusk
            default: setOverlay(OVERLAY_NIGHT);    break;  // Night
        }
    }

    public void clearWeather() { setWeather(WEATHER_NONE, 0); }
    public void clearOverlay() { setOverlay(OVERLAY_NONE); }

    // -------------------------------------------------
    //  PARTICLES
    // -------------------------------------------------
    private void initializeParticles() {
        particleCount = 0;
        int count = (weatherIntensity * MAX_PARTICLES) / 100;
        for (int i = 0; i < count; i++) spawnParticle();
    }

    private void spawnParticle() {
        if (particleCount >= MAX_PARTICLES) return;
        int id = particleCount;
        randomSeed = nextRand(randomSeed);

        switch (currentWeather) {
            case WEATHER_RAIN:
                particleX[id] = randomSeed % screenW;
                particleY[id] = -(randomSeed % 60);
                particleSpeedX[id] = windX;
                particleSpeedY[id] = 8 + (randomSeed % 4);
                particleLife[id] = 100;
                particleType[id] = 0;
                particleSize[id] = 1;
                break;

            case WEATHER_SNOW:
                particleX[id] = randomSeed % screenW;
                particleY[id] = -(randomSeed % 40);
                randomSeed = nextRand(randomSeed);
                particleSpeedX[id] = (randomSeed % 3) - 1;
                particleSpeedY[id] = 1 + (randomSeed % 2);
                particleLife[id] = 200;
                particleType[id] = randomSeed % 3;
                particleSize[id] = 1 + (randomSeed % 3);
                break;

            case WEATHER_BLIZZARD:
                particleX[id] = randomSeed % screenW;
                particleY[id] = -(randomSeed % 30);
                randomSeed = nextRand(randomSeed);
                particleSpeedX[id] = windX - (randomSeed % 3);
                particleSpeedY[id] = 3 + (randomSeed % 4);
                particleLife[id] = 100;
                particleType[id] = 0;
                particleSize[id] = 1 + (randomSeed % 2);
                break;

            case WEATHER_STORM:
                particleX[id] = randomSeed % screenW;
                particleY[id] = -(randomSeed % 50);
                particleSpeedX[id] = windX;
                particleSpeedY[id] = 12 + (randomSeed % 4);
                particleLife[id] = 80;
                particleType[id] = 0;
                particleSize[id] = 1;
                break;

            case WEATHER_SANDSTORM:
                particleX[id] = screenW + (randomSeed % 60);
                particleY[id] = randomSeed % screenH;
                randomSeed = nextRand(randomSeed);
                particleSpeedX[id] = windX - (randomSeed % 3);
                particleSpeedY[id] = (randomSeed % 5) - 2;
                particleLife[id] = 120;
                particleType[id] = randomSeed % 2;
                particleSize[id] = 1 + (randomSeed % 3);
                break;

            case WEATHER_LEAVES:
                particleX[id] = randomSeed % screenW;
                particleY[id] = -(randomSeed % 30);
                randomSeed = nextRand(randomSeed);
                particleSpeedX[id] = (randomSeed % 5) - 2;
                particleSpeedY[id] = 2 + (randomSeed % 2);
                particleLife[id] = 150;
                particleType[id] = randomSeed % 4;
                particleSize[id] = 3;
                break;

            case WEATHER_SPARKLE:
                particleX[id] = randomSeed % screenW;
                randomSeed = nextRand(randomSeed);
                particleY[id] = randomSeed % screenH;
                particleSpeedX[id] = 0; particleSpeedY[id] = -1;
                particleLife[id] = 30 + (randomSeed % 30);
                particleType[id] = randomSeed % 3;
                particleSize[id] = 1;
                break;

            case WEATHER_AURORA:
                // Aurora uses bands, not particles — skip
                return;
        }
        particleCount++;
    }

    // -------------------------------------------------
    //  UPDATE
    // -------------------------------------------------
    public void update() {
        // Intensity ramp during weather transition
        if (intensityRampTimer > 0) {
            intensityRampTimer--;
            if (intensityRampTimer == 0) {
                // Swap weather type
                currentWeather = targetWeather;
                weatherIntensity = 0;
                targetIntensity = 50;
                particleCount = 0;
                initializeParticles();
            }
        } else if (weatherIntensity < targetIntensity) {
            weatherIntensity++;
        } else if (weatherIntensity > targetIntensity) {
            weatherIntensity--;
        }

        // Overlay transition
        if (transitionTimer > 0) {
            transitionTimer--;
            if (transitionTimer == 30) currentOverlay = targetOverlay;
            overlayAlpha = (targetOverlay == OVERLAY_NONE)
                ? Math.max(0, overlayAlpha - 6)
                : Math.min(120, overlayAlpha + 4);
        }

        // Particles
        updateParticles();

        // Lightning
        if (currentWeather == WEATHER_STORM || currentWeather == WEATHER_BLIZZARD) {
            updateLightning();
        }

        // Fog scroll
        if (currentWeather == WEATHER_FOG || currentWeather == WEATHER_SANDSTORM
                || currentWeather == WEATHER_BLIZZARD) {
            fogOffset = (fogOffset + 1) % screenW;
        }

        // Aurora animation
        if (currentWeather == WEATHER_AURORA || currentOverlay == OVERLAY_NIGHT) {
            auroraTimer++;
            for (int i = 0; i < 4; i++) {
                auroraBandY[i] = (auroraBandY[i] + 1) % screenH;
                randomSeed = nextRand(randomSeed);
                auroraBandWidth[i] = 8 + (randomSeed % 12);
            }
        }
    }

    private void updateParticles() {
        for (int i = particleCount - 1; i >= 0; i--) {
            particleX[i] += particleSpeedX[i];
            particleY[i] += particleSpeedY[i];
            particleLife[i]--;

            // Snow wobble
            if (currentWeather == WEATHER_SNOW || currentWeather == WEATHER_LEAVES) {
                randomSeed = nextRand(randomSeed);
                if (randomSeed % 12 == 0) particleSpeedX[i] = (randomSeed % 3) - 1;
            }

            // Blizzard gusts
            if (currentWeather == WEATHER_BLIZZARD) {
                randomSeed = nextRand(randomSeed);
                if (randomSeed % 20 == 0) particleSpeedX[i] = windX - (randomSeed % 3);
            }

            // Remove dead
            if (particleLife[i] <= 0 || particleY[i] > screenH + 10
                    || particleX[i] < -30 || particleX[i] > screenW + 30) {
                particleX[i] = particleX[particleCount - 1];
                particleY[i] = particleY[particleCount - 1];
                particleSpeedX[i] = particleSpeedX[particleCount - 1];
                particleSpeedY[i] = particleSpeedY[particleCount - 1];
                particleLife[i] = particleLife[particleCount - 1];
                particleType[i] = particleType[particleCount - 1];
                particleSize[i] = particleSize[particleCount - 1];
                particleCount--;
            }
        }

        // Spawn replacements
        int wanted = (weatherIntensity * MAX_PARTICLES) / 100;
        while (particleCount < wanted) spawnParticle();
    }

    private void updateLightning() {
        if (lightningFlash > 0) { lightningFlash--; return; }
        lightningTimer++;
        randomSeed = nextRand(randomSeed);
        if (lightningTimer > 60 && randomSeed % 100 < weatherIntensity / 10) {
            lightningFlash = 4 + (randomSeed % 4);
            lightningTimer = 0;
        }
    }

    // -------------------------------------------------
    //  RENDER
    // -------------------------------------------------
    public void render(Graphics g) {
        switch (currentWeather) {
            case WEATHER_RAIN:      renderRain(g, false); break;
            case WEATHER_SNOW:      renderSnow(g); break;
            case WEATHER_FOG:       renderFog(g); break;
            case WEATHER_STORM:     renderStorm(g); break;
            case WEATHER_SANDSTORM: renderSandstorm(g); break;
            case WEATHER_LEAVES:    renderLeaves(g); break;
            case WEATHER_SPARKLE:   renderSparkle(g); break;
            case WEATHER_BLIZZARD:  renderBlizzard(g); break;
            case WEATHER_AURORA:    renderAurora(g); break;
        }
        renderOverlay(g);
    }

    private void renderRain(Graphics g, boolean heavy) {
        g.setColor(heavy ? 0x4466AA : 0x6688CC);
        for (int i = 0; i < particleCount; i++) {
            int x = particleX[i], y = particleY[i];
            g.drawLine(x, y, x + (heavy ? 2 : 1), y + (heavy ? 10 : 6));
        }
    }

    private void renderSnow(Graphics g) {
        for (int i = 0; i < particleCount; i++) {
            int x = particleX[i], y = particleY[i];
            int sz = particleSize[i];
            switch (particleType[i]) {
                case 0: g.setColor(0xFFFFFF); break;
                case 1: g.setColor(0xEEEEFF); break;
                default: g.setColor(0xDDDDFF); break;
            }
            g.fillRect(x, y, sz, sz);
        }
    }

    private void renderBlizzard(Graphics g) {
        // Heavy snow + horizontal streaks
        g.setColor(0xCCDDFF);
        for (int i = 0; i < particleCount; i++) {
            int x = particleX[i], y = particleY[i];
            int sz = particleSize[i];
            g.fillRect(x, y, sz + 1, sz);
            g.drawLine(x - 3, y, x, y);  // horizontal tail
        }

        // Fog layer
        g.setColor(0xAABBCC);
        for (int y = 0; y < screenH; y += 6) {
            for (int x = ((y + fogOffset) / 6) % 4; x < screenW; x += 4) {
                g.drawLine(x, y, x, y + 5);
            }
        }
    }

    private void renderFog(Graphics g) {
        g.setColor(0xCCCCCC);
        for (int y = 0; y < screenH; y += 8) {
            for (int x = (y / 8) % 3; x < screenW; x += 3 + (fogDensity / 50)) {
                g.drawLine(x, y, x, y + 7);
            }
        }
    }

    private void renderStorm(Graphics g) {
        if (lightningFlash > 0) {
            int f = lightningFlash * 50;
            g.setColor((Math.min(255, f) << 16) | (Math.min(255, f) << 8) | Math.min(255, f));
            for (int y = 0; y < screenH; y += 2) {
                for (int x = y % 2; x < screenW; x += 2) g.drawLine(x, y, x, y);
            }
        }
        renderRain(g, false);
    }

    private void renderSandstorm(Graphics g) {
        for (int i = 0; i < particleCount; i++) {
            int x = particleX[i], y = particleY[i];
            g.setColor(particleType[i] == 0 ? 0xD4A574 : 0xC4956A);
            g.fillRect(x, y, 2 + particleSize[i], 1);
        }
        g.setColor(0xD4A574);
        for (int y = 0; y < screenH; y += 4) {
            for (int x = ((y + fogOffset) / 4) % 4; x < screenW; x += 4) g.drawLine(x, y, x, y);
        }
    }

    private void renderLeaves(Graphics g) {
        int[] colors = {0x884400, 0xCC6600, 0xFFAA00, 0x668800};
        for (int i = 0; i < particleCount; i++) {
            int x = particleX[i], y = particleY[i];
            g.setColor(colors[particleType[i] % 4]);
            int frame = (particleLife[i] / 10) % 2;
            g.fillRect(x, y, frame == 0 ? 4 : 3, frame == 0 ? 2 : 3);
        }
    }

    private void renderSparkle(Graphics g) {
        for (int i = 0; i < particleCount; i++) {
            int x = particleX[i], y = particleY[i];
            int b = Math.min(255, particleLife[i] * 10);
            g.setColor((b << 16) | (b << 8) | b);
            int frame = (particleLife[i] / 5) % 2;
            if (frame == 0) { g.drawLine(x - 2, y, x + 2, y); g.drawLine(x, y - 2, x, y + 2); }
            else { g.drawLine(x - 1, y - 1, x + 1, y + 1); g.drawLine(x + 1, y - 1, x - 1, y + 1); }
        }
    }

    private void renderAurora(Graphics g) {
        for (int i = 0; i < 4; i++) {
            g.setColor(auroraBandColor[i]);
            int bW = auroraBandWidth[i];
            int bY = auroraBandY[i];
            // Dithered horizontal aurora band
            for (int y = bY; y < Math.min(screenH, bY + bW); y++) {
                int density = (y - bY) * 3 / bW;
                for (int x = density; x < screenW; x += density + 1) {
                    g.drawLine(x, y, x, y);
                }
            }
        }
    }

    private void renderOverlay(Graphics g) {
        if (currentOverlay == OVERLAY_NONE && overlayAlpha == 0) return;

        int color;
        switch (currentOverlay) {
            case OVERLAY_NIGHT:     color = 0x000033; break;
            case OVERLAY_SEPIA:     color = 0x704214; break;
            case OVERLAY_DARK:      color = 0x000000; break;
            case OVERLAY_SUNSET:    color = 0xFF6600; break;
            case OVERLAY_UNDERWATER:color = 0x002266; break;
            case OVERLAY_DAWN:      color = 0xCC6688; break;
            case OVERLAY_NOON:      color = 0xFFFF88; break;
            case OVERLAY_OVERCAST:  color = 0x888899; break;
            default: return;
        }

        g.setColor(color);
        int skip = Math.max(1, 4 - (overlayAlpha / 40));
        for (int y = 0; y < screenH; y += skip) {
            for (int x = (y / skip) % skip; x < screenW; x += skip) {
                g.drawLine(x, y, x, y);
            }
        }
    }

    // -------------------------------------------------
    //  HELPERS
    // -------------------------------------------------
    private int nextRand(int seed) {
        return (seed * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    public void setScreenSize(int w, int h) { screenW = w; screenH = h; }
    public void setFogDensity(int d)        { fogDensity = Math.max(0, Math.min(100, d)); }
    public void setWind(int wx)             { windX = wx; }

    public int getCurrentWeather()   { return currentWeather; }
    public int getCurrentOverlay()   { return currentOverlay; }
    public int getWeatherIntensity() { return weatherIntensity; }
    public boolean isLightningFlashing() { return lightningFlash > 0; }
}
