package com.aicompanions.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One AI companion: a Carpet fake-player "body" + a personality + a voice.
 * <p>
 * Instances of this are loaded straight out of the config JSON by Gson, so
 * keep the field names matching the JSON keys and keep a no-arg constructor
 * available (Gson uses reflection and doesn't need one, but we provide
 * sensible defaults below in case a field is missing from an older config
 * file).
 */
public class BotProfile {

    /** Must be a legal Minecraft player name: 3-16 chars, [A-Za-z0-9_]. */
    public String carpetName = "Rafiq";

    /** What the bot is called in chat/subtitles; can contain Arabic/spaces. */
    public String displayName = "رفيق";

    /** "POLITE" or "RUDE" - selects which system prompt BotBrain builds. */
    public String personality = "POLITE";

    /** ElevenLabs voice_id used for this bot's TTS. */
    public String voiceId = "7GuGTyc18I5wWanLbbNq";

    /** Translation key suffix for this bot's push-to-talk keybind. */
    public String keybindId = "talk_polite";

    /** Only meaningful when personality = RUDE. */
    public boolean cursingEnabled = true;
    public boolean pranksEnabled = true;

    /** Minimum seconds between two pranks from this bot. */
    public int prankCooldownSeconds = 300;

    /**
     * If > 0, the mod will automatically place water on the lava it started
     * after this many seconds, so a prank is a scare + a mess to clean up
     * rather than a permanent base fire. Set to 0 to disable the cleanup
     * and let the fire burn (full chaos mode).
     */
    public int prankAutoExtinguishSeconds = 20;

    /** How often (seconds) the bot makes an autonomous decision on its own,
     *  even if nobody is talking to it. 0 disables autonomous behaviour. */
    public int autonomyIntervalSeconds = 45;

    // ------------------------------------------------------------------
    // Runtime-only state (not persisted, not touched by Gson on load as
    // long as it's never written back out through saveConfig()).
    // ------------------------------------------------------------------
    public transient long lastPrankTimeMillis = 0L;
    public transient long lastAutonomyTickMillis = 0L;
    public transient boolean spawned = false;

    /** Rolling conversation memory sent to OpenAI as context. Capped so the
     *  prompt doesn't grow forever. */
    public transient final Deque<String> conversationHistory = new ArrayDeque<>();

    public static final int MAX_HISTORY_LINES = 20;

    public void remember(String line) {
        conversationHistory.addLast(line);
        while (conversationHistory.size() > MAX_HISTORY_LINES) {
            conversationHistory.removeFirst();
        }
    }

    public boolean isRude() {
        return "RUDE".equalsIgnoreCase(personality);
    }
}
