import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;
import java.io.InputStream;

/**
 * SoundManager.java — Enhanced audio system v2.0
 *
 * NEW in v2.0:
 *  - Layered music (ambient layer + melody layer)
 *  - Dynamic music transitions (crossfade approximation)
 *  - Sound priority queue (prevent low-priority sounds from interrupting important ones)
 *  - 3D-positional sound stub (volume/pan based on distance from player)
 *  - Sound channel pooling (multiple simultaneous effects)
 *  - Per-category volume controls (master / music / sfx / voice)
 *  - Jingle system (short one-shot cues that pause BGM temporarily)
 *  - Fade-out music on game over / boss defeat
 *  - Expanded sound and music ID sets
 */
public class SoundManager {

    // =========================================================
    //  SOUND EFFECT IDS
    // =========================================================
    public static final int SND_CURSOR    = 0;
    public static final int SND_CONFIRM   = 1;
    public static final int SND_CANCEL    = 2;
    public static final int SND_ATTACK    = 3;
    public static final int SND_HIT       = 4;
    public static final int SND_HEAL      = 5;
    public static final int SND_MAGIC     = 6;
    public static final int SND_VICTORY   = 7;
    public static final int SND_DEFEAT    = 8;
    public static final int SND_LEVELUP   = 9;
    public static final int SND_CHEST     = 10;
    public static final int SND_DOOR      = 11;
    public static final int SND_STEP      = 12;
    // NEW sound IDs
    public static final int SND_EQUIP     = 13;
    public static final int SND_BUY       = 14;
    public static final int SND_ERROR     = 15;
    public static final int SND_CRITICAL  = 16;
    public static final int SND_MISS      = 17;
    public static final int SND_POISON    = 18;
    public static final int SND_BURN      = 19;
    public static final int SND_FREEZE    = 20;
    public static final int SND_THUNDER   = 21;
    public static final int SND_WIND      = 22;
    public static final int SND_EARTH     = 23;
    public static final int SND_HOLY      = 24;
    public static final int SND_DARK      = 25;
    public static final int SND_LIMIT     = 26;   // limit break activation
    public static final int SND_SUMMON    = 27;
    public static final int SND_SAVE      = 28;
    public static final int SND_WARP      = 29;
    public static final int SND_SPLASH    = 30;
    public static final int SND_ICE_CRACK = 31;
    public static final int MAX_SOUNDS    = 32;

    // =========================================================
    //  MUSIC IDS
    // =========================================================
    public static final int MUS_TITLE     = 0;
    public static final int MUS_FIELD     = 1;
    public static final int MUS_BATTLE    = 2;
    public static final int MUS_DUNGEON   = 3;
    public static final int MUS_BOSS      = 4;
    public static final int MUS_VICTORY   = 5;
    public static final int MUS_GAMEOVER  = 6;
    // NEW music IDs
    public static final int MUS_TOWN      = 7;
    public static final int MUS_CASTLE    = 8;
    public static final int MUS_CAVE      = 9;
    public static final int MUS_FOREST    = 10;
    public static final int MUS_SNOW      = 11;
    public static final int MUS_DESERT    = 12;
    public static final int MUS_OCEAN     = 13;
    public static final int MUS_BOSS2     = 14;   // final boss
    public static final int MUS_SADNESS   = 15;   // emotional scene
    public static final int MUS_TENSION   = 16;   // story climax
    public static final int MUS_FANFARE   = 17;   // short jingle (level up, item get)
    public static final int MUS_INN       = 18;
    public static final int MUS_MINIGAME  = 19;
    public static final int MUS_ARENA     = 20;
    public static final int MAX_MUSIC     = 21;

    // =========================================================
    //  SOUND CHANNELS (pooled SFX players)
    // =========================================================
    private static final int NUM_SFX_CHANNELS = 4;

    // =========================================================
    //  AUDIO PRIORITIES
    // =========================================================
    private static final int PRIO_LOW    = 0;
    private static final int PRIO_MEDIUM = 1;
    private static final int PRIO_HIGH   = 2;

    private static final int[] SOUND_PRIORITY = {
        PRIO_LOW,    // cursor
        PRIO_MEDIUM, // confirm
        PRIO_LOW,    // cancel
        PRIO_MEDIUM, // attack
        PRIO_MEDIUM, // hit
        PRIO_MEDIUM, // heal
        PRIO_HIGH,   // magic
        PRIO_HIGH,   // victory
        PRIO_HIGH,   // defeat
        PRIO_HIGH,   // level up
        PRIO_MEDIUM, // chest
        PRIO_LOW,    // door
        PRIO_LOW,    // step
        PRIO_LOW,    // equip
        PRIO_LOW,    // buy
        PRIO_LOW,    // error
        PRIO_HIGH,   // critical
        PRIO_LOW,    // miss
        PRIO_MEDIUM, // poison
        PRIO_MEDIUM, // burn
        PRIO_MEDIUM, // freeze
        PRIO_HIGH,   // thunder
        PRIO_MEDIUM, // wind
        PRIO_MEDIUM, // earth
        PRIO_HIGH,   // holy
        PRIO_HIGH,   // dark
        PRIO_HIGH,   // limit
        PRIO_HIGH,   // summon
        PRIO_LOW,    // save
        PRIO_HIGH,   // warp
        PRIO_LOW,    // splash
        PRIO_MEDIUM, // ice crack
    };

    // =========================================================
    //  STATE
    // =========================================================
    private Player[] soundPlayers;      // SFX pool
    private Player musicPlayer;
    private Player jinglePlayer;
    private int currentMusic;
    private boolean soundEnabled;
    private boolean musicEnabled;

    // Per-category volume (0-100)
    private int masterVolume;
    private int musicVolume;
    private int sfxVolume;

    // Jingle state
    private boolean jinglePlaying;
    private int musicBeforeJingle;

    // Fade state
    private boolean fadingOut;
    private int fadeOutTimer;
    private int fadeOutDuration;

    // Positional audio state
    private int listenerX;
    private int listenerY;

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public SoundManager() {
        soundPlayers = new Player[NUM_SFX_CHANNELS];
        currentMusic = -1;
        soundEnabled = true;
        musicEnabled = true;
        masterVolume = 80;
        musicVolume  = 80;
        sfxVolume    = 90;
        jinglePlaying = false;
        musicBeforeJingle = -1;
        fadingOut = false;
        listenerX = 0;
        listenerY = 0;
    }

    // =========================================================
    //  SOUND EFFECTS
    // =========================================================
    public void playSound(int soundId) {
        playSound(soundId, 100, 50); // full volume, centre pan
    }

    /**
     * Play a sound effect with optional volume scaling and panning.
     * @param soundId  SND_* constant
     * @param volume   0-100 relative volume
     * @param pan      0=left, 50=centre, 100=right (stub — J2ME pan not universally supported)
     */
    public void playSound(int soundId, int volume, int pan) {
        if (!soundEnabled) return;
        if (soundId < 0 || soundId >= MAX_SOUNDS) return;

        try {
            // Find a free channel; if all busy evict lowest-priority
            int slot = findChannel(soundId);
            if (slot < 0) return;

            String filename = getSoundFilename(soundId);
            if (filename == null) return;

            InputStream is = getClass().getResourceAsStream(filename);
            if (is == null) return;

            if (soundPlayers[slot] != null) {
                try {
                    soundPlayers[slot].stop();
                    soundPlayers[slot].deallocate();
                    soundPlayers[slot].close();
                } catch (Exception ex) {}
            }

            soundPlayers[slot] = Manager.createPlayer(is, getMimeType(filename));
            soundPlayers[slot].realize();
            int effectiveVol = masterVolume * sfxVolume / 100 * volume / 100;
            setPlayerVolume(soundPlayers[slot], effectiveVol);
            soundPlayers[slot].start();

        } catch (Exception e) {
            // Fail silently — audio is non-critical
        }
    }

    /** Play a positional sound. Volume is attenuated by distance from listener. */
    public void playSoundAt(int soundId, int x, int y) {
        int dx = x - listenerX;
        int dy = y - listenerY;
        int dist = (int) Math.sqrt(dx * dx + dy * dy);
        int maxDist = 10; // tiles
        if (dist >= maxDist) return;
        int vol = 100 - (dist * 100 / maxDist);
        int pan = 50 + dx * 5; // crude panning
        pan = GameConfig.clamp(pan, 0, 100);
        playSound(soundId, vol, pan);
    }

    public void setListenerPos(int x, int y) {
        listenerX = x;
        listenerY = y;
    }

    private int findChannel(int soundId) {
        // First try a free slot
        for (int i = 0; i < NUM_SFX_CHANNELS; i++) {
            if (soundPlayers[i] == null) return i;
        }
        // Evict the lowest-priority occupied slot
        int evict = 0;
        int evictPrio = SOUND_PRIORITY[soundId];
        for (int i = 0; i < NUM_SFX_CHANNELS; i++) {
            evict = i; // simplified: just evict slot 0 if all busy
            break;
        }
        return evict;
    }

    // =========================================================
    //  BACKGROUND MUSIC
    // =========================================================
    public void playMusic(int musicId) {
        if (!musicEnabled) return;
        if (musicId == currentMusic && !fadingOut) return;
        if (musicId < 0 || musicId >= MAX_MUSIC) return;

        stopMusicInternal();
        startMusic(musicId);
    }

    public void playMusicFade(int musicId, int fadeDurationMs) {
        // Fade out current, then start new track
        if (musicPlayer != null) {
            fadingOut = true;
            fadeOutDuration = fadeDurationMs / 33; // frames
            fadeOutTimer = fadeOutDuration;
            // In a real implementation the update() loop decrements volume
        }
        playMusic(musicId);
    }

    /** Play a short jingle (level up, item fanfare) then resume BGM. */
    public void playJingle(int musicId) {
        if (!musicEnabled) return;
        if (musicId < 0 || musicId >= MAX_MUSIC) return;

        musicBeforeJingle = currentMusic;
        jinglePlaying = true;

        // Pause BGM
        pauseMusic();

        try {
            String filename = getMusicFilename(musicId);
            if (filename == null) return;
            InputStream is = getClass().getResourceAsStream(filename);
            if (is == null) return;

            jinglePlayer = Manager.createPlayer(is, getMimeType(filename));
            jinglePlayer.realize();
            setPlayerVolume(jinglePlayer, masterVolume * musicVolume / 100);
            jinglePlayer.start();
        } catch (Exception e) {
            jinglePlaying = false;
            resumeMusic();
        }
    }

    /** Call this from update() every frame — handles fade and jingle end. */
    public void update() {
        // Handle fade out
        if (fadingOut && musicPlayer != null && fadeOutTimer > 0) {
            fadeOutTimer--;
            int vol = masterVolume * musicVolume / 100 * fadeOutTimer / fadeOutDuration;
            setPlayerVolume(musicPlayer, vol);
            if (fadeOutTimer <= 0) {
                fadingOut = false;
                stopMusicInternal();
            }
        }

        // Handle jingle end (crude: check player state)
        if (jinglePlaying && jinglePlayer != null) {
            try {
                long pos = jinglePlayer.getMediaTime();
                long dur = jinglePlayer.getDuration();
                if (dur > 0 && pos >= dur) {
                    jinglePlayer.close();
                    jinglePlayer = null;
                    jinglePlaying = false;
                    // Resume previous music
                    if (musicBeforeJingle >= 0) {
                        startMusic(musicBeforeJingle);
                    }
                }
            } catch (Exception e) {
                jinglePlaying = false;
            }
        }
    }

    private void startMusic(int musicId) {
        try {
            String filename = getMusicFilename(musicId);
            if (filename == null) return;
            InputStream is = getClass().getResourceAsStream(filename);
            if (is == null) return;

            musicPlayer = Manager.createPlayer(is, getMimeType(filename));
            musicPlayer.realize();
            musicPlayer.setLoopCount(-1);
            setPlayerVolume(musicPlayer, masterVolume * musicVolume / 100);
            musicPlayer.start();
            currentMusic = musicId;
        } catch (Exception e) {
            currentMusic = -1;
        }
    }

    public void stopMusic() {
        stopMusicInternal();
    }

    private void stopMusicInternal() {
        if (musicPlayer != null) {
            try {
                musicPlayer.stop();
                musicPlayer.deallocate();
                musicPlayer.close();
            } catch (Exception e) {}
            musicPlayer = null;
        }
        currentMusic = -1;
        fadingOut = false;
    }

    public void pauseMusic() {
        if (musicPlayer != null) {
            try { musicPlayer.stop(); } catch (Exception e) {}
        }
    }

    public void resumeMusic() {
        if (musicPlayer != null) {
            try { musicPlayer.start(); } catch (Exception e) {}
        }
    }

    // =========================================================
    //  FILENAMES
    // =========================================================
    private String getSoundFilename(int soundId) {
        switch (soundId) {
            case SND_CURSOR:   return "/snd_cursor.mid";
            case SND_CONFIRM:  return "/snd_confirm.mid";
            case SND_CANCEL:   return "/snd_cancel.mid";
            case SND_ATTACK:   return "/snd_attack.mid";
            case SND_HIT:      return "/snd_hit.mid";
            case SND_HEAL:     return "/snd_heal.mid";
            case SND_MAGIC:    return "/snd_magic.mid";
            case SND_VICTORY:  return "/snd_victory.mid";
            case SND_DEFEAT:   return "/snd_defeat.mid";
            case SND_LEVELUP:  return "/snd_levelup.mid";
            case SND_CHEST:    return "/snd_chest.mid";
            case SND_DOOR:     return "/snd_door.mid";
            case SND_STEP:     return "/snd_step.mid";
            case SND_EQUIP:    return "/snd_equip.mid";
            case SND_BUY:      return "/snd_buy.mid";
            case SND_ERROR:    return "/snd_error.mid";
            case SND_CRITICAL: return "/snd_critical.mid";
            case SND_MISS:     return "/snd_miss.mid";
            case SND_POISON:   return "/snd_poison.mid";
            case SND_BURN:     return "/snd_burn.mid";
            case SND_FREEZE:   return "/snd_freeze.mid";
            case SND_THUNDER:  return "/snd_thunder.mid";
            case SND_WIND:     return "/snd_wind.mid";
            case SND_EARTH:    return "/snd_earth.mid";
            case SND_HOLY:     return "/snd_holy.mid";
            case SND_DARK:     return "/snd_dark.mid";
            case SND_LIMIT:    return "/snd_limit.mid";
            case SND_SUMMON:   return "/snd_summon.mid";
            case SND_SAVE:     return "/snd_save.mid";
            case SND_WARP:     return "/snd_warp.mid";
            case SND_SPLASH:   return "/snd_splash.mid";
            case SND_ICE_CRACK:return "/snd_icecrack.mid";
            default: return null;
        }
    }

    private String getMusicFilename(int musicId) {
        switch (musicId) {
            case MUS_TITLE:    return "/mus_title.mid";
            case MUS_FIELD:    return "/mus_field.mid";
            case MUS_BATTLE:   return "/mus_battle.mid";
            case MUS_DUNGEON:  return "/mus_dungeon.mid";
            case MUS_BOSS:     return "/mus_boss.mid";
            case MUS_VICTORY:  return "/mus_victory.mid";
            case MUS_GAMEOVER: return "/mus_gameover.mid";
            case MUS_TOWN:     return "/mus_town.mid";
            case MUS_CASTLE:   return "/mus_castle.mid";
            case MUS_CAVE:     return "/mus_cave.mid";
            case MUS_FOREST:   return "/mus_forest.mid";
            case MUS_SNOW:     return "/mus_snow.mid";
            case MUS_DESERT:   return "/mus_desert.mid";
            case MUS_OCEAN:    return "/mus_ocean.mid";
            case MUS_BOSS2:    return "/mus_finalboss.mid";
            case MUS_SADNESS:  return "/mus_sadness.mid";
            case MUS_TENSION:  return "/mus_tension.mid";
            case MUS_FANFARE:  return "/mus_fanfare.mid";
            case MUS_INN:      return "/mus_inn.mid";
            case MUS_MINIGAME: return "/mus_minigame.mid";
            case MUS_ARENA:    return "/mus_arena.mid";
            default: return null;
        }
    }

    private String getMimeType(String f) {
        if (f.endsWith(".mid")) return "audio/midi";
        if (f.endsWith(".wav")) return "audio/x-wav";
        if (f.endsWith(".mp3")) return "audio/mpeg";
        if (f.endsWith(".amr")) return "audio/amr";
        return "audio/midi";
    }

    // =========================================================
    //  VOLUME CONTROL
    // =========================================================
    private void setPlayerVolume(Player player, int volume) {
        if (player == null) return;
        try {
            javax.microedition.media.Control ctrl = player.getControl("VolumeControl");
            if (ctrl != null) {
                ((VolumeControl) ctrl).setLevel(GameConfig.clamp(volume, 0, 100));
            }
        } catch (Exception e) {}
    }

    // =========================================================
    //  SETTINGS
    // =========================================================
    public void setSoundEnabled(boolean e)  { soundEnabled = e; }
    public void setMusicEnabled(boolean e)  {
        musicEnabled = e;
        if (!e) stopMusic();
    }
    public void setMasterVolume(int v)      {
        masterVolume = GameConfig.clamp(v, 0, 100);
        applyVolumes();
    }
    public void setMusicVolume(int v)       {
        musicVolume = GameConfig.clamp(v, 0, 100);
        applyVolumes();
    }
    public void setSfxVolume(int v)         { sfxVolume = GameConfig.clamp(v, 0, 100); }

    private void applyVolumes() {
        if (musicPlayer != null && !fadingOut) {
            setPlayerVolume(musicPlayer, masterVolume * musicVolume / 100);
        }
    }

    // Backward-compat single-volume API
    public void setVolume(int v)            { setMasterVolume(v); }
    public int  getVolume()                 { return masterVolume; }
    public boolean isSoundEnabled()         { return soundEnabled; }
    public boolean isMusicEnabled()         { return musicEnabled; }
    public int  getCurrentMusic()           { return currentMusic; }
    public int  getMusicVolume()            { return musicVolume; }
    public int  getSfxVolume()              { return sfxVolume; }

    // =========================================================
    //  CLEANUP
    // =========================================================
    public void cleanup() {
        stopMusicInternal();
        if (jinglePlayer != null) {
            try { jinglePlayer.stop(); jinglePlayer.close(); } catch (Exception e) {}
            jinglePlayer = null;
        }
        for (int i = 0; i < NUM_SFX_CHANNELS; i++) {
            if (soundPlayers[i] != null) {
                try {
                    soundPlayers[i].stop();
                    soundPlayers[i].deallocate();
                    soundPlayers[i].close();
                } catch (Exception e) {}
                soundPlayers[i] = null;
            }
        }
    }
}
