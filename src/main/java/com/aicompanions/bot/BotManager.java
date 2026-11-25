package com.aicompanions.bot;

import com.aicompanions.ai.BotBrain;
import com.aicompanions.ai.Decision;
import com.aicompanions.config.BotProfile;
import com.aicompanions.config.ModConfig;
import com.aicompanions.voice.ElevenLabsTTS;
import com.aicompanions.voice.VoicePlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Top-level orchestrator: owns one BotBrain / BotController / PrankManager
 * set per running server, and is the single place that turns "the player
 * said/typed X to bot Y" into (network calls on a background thread) ->
 * (game actions + chat + voice back on the main thread).
 */
public class BotManager {

    private static final Logger LOGGER = Logger.getLogger("aicompanions");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "aicompanions-brain");
                t.setDaemon(true);
                return t;
            });

    private final MinecraftServer server;
    private final ModConfig config;
    private final BotBrain brain;
    private final BotController controller;
    private final PrankManager prankManager;
    private final ElevenLabsTTS tts;

    public BotManager(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.brain = new BotBrain(config);
        this.controller = new BotController(server);
        this.prankManager = new PrankManager(server, controller);
        this.tts = new ElevenLabsTTS(config.resolvedElevenLabsKey(), config.ttsModel, config.ttsOutputFormat);
    }

    public List<BotProfile> bots() {
        return config.bots;
    }

    public Optional<BotProfile> findBot(String carpetOrDisplayName) {
        return config.bots.stream()
                .filter(b -> b.carpetName.equalsIgnoreCase(carpetOrDisplayName)
                        || b.displayName.equals(carpetOrDisplayName))
                .findFirst();
    }

    public void ensureSpawned(BotProfile bot, ServerPlayerEntity nearPlayer) {
        controller.spawnIfNeeded(bot, nearPlayer);
    }

    public void despawn(BotProfile bot) {
        controller.kill(bot);
    }

    /**
     * Main entry point: something was said to (or near) this bot. Runs the
     * OpenAI + ElevenLabs calls on a background thread, then hops back onto
     * the server thread (via MinecraftServer#execute) to touch the world.
     */
    public void handleSpeech(BotProfile bot, ServerPlayerEntity from, String message) {
        ensureSpawned(bot, from);
        EXECUTOR.submit(() -> runBrainCycle(bot, from, message));
    }

    /** Called periodically per bot (see AICompanionsMod's tick loop) so the
     *  bots act on their own instead of only reacting to speech. */
    public void autonomousTick(BotProfile bot, ServerPlayerEntity anyOnlinePlayer) {
        if (bot.autonomyIntervalSeconds <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - bot.lastAutonomyTickMillis < bot.autonomyIntervalSeconds * 1000L) {
            return;
        }
        bot.lastAutonomyTickMillis = now;
        ensureSpawned(bot, anyOnlinePlayer);
        EXECUTOR.submit(() -> runBrainCycle(bot, anyOnlinePlayer,
                "[No one is talking to you right now. Decide what to do on your own for a moment.]"));
    }

    private void runBrainCycle(BotProfile bot, ServerPlayerEntity relevantPlayer, String incoming) {
        try {
            Decision decision = brain.decide(bot, incoming);

            byte[] pcm = new byte[0];
            try {
                pcm = tts.speak(bot.voiceId, decision.speech());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[AI Companions] TTS failed for " + bot.displayName, e);
            }
            byte[] finalPcm = pcm;

            server.execute(() -> {
                broadcastBotChat(bot, decision.speech());
                applyActions(bot, relevantPlayer, decision.actions());
            });

            VoicePlayer.playAsync(finalPcm, ElevenLabsTTS.sampleRateFromFormat(config.ttsOutputFormat));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[AI Companions] Brain cycle failed for " + bot.displayName, e);
        }
    }

    // ------------------------------------------------------------------
    // KNOWN RISK AREA: broadcasting a chat line. net.minecraft.text.Text /
    // PlayerManager#broadcast has a stable shape across recent versions,
    // but double check the exact overload in your IDE if it doesn't
    // compile - the fix is always a one-line signature change here.
    // ------------------------------------------------------------------
    private void broadcastBotChat(BotProfile bot, String speech) {
        if (speech == null || speech.isBlank()) {
            return;
        }
        Text message = Text.literal("<" + bot.displayName + "> " + speech);
        server.getPlayerManager().broadcast(message, false);
    }

    private void applyActions(BotProfile bot, ServerPlayerEntity relevantPlayer, JsonArray actions) {
        if (actions == null) {
            return;
        }
        for (JsonElement el : actions) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject action = el.getAsJsonObject();
            String type = action.has("type") ? action.get("type").getAsString() : "none";
            try {
                applyOne(bot, relevantPlayer, type, action);
            } catch (Exception e) {
                LOGGER.warning("[AI Companions] Could not apply action '" + type + "' for "
                        + bot.displayName + ": " + e.getMessage());
            }
        }
    }

    private void applyOne(BotProfile bot, ServerPlayerEntity relevantPlayer, String type, JsonObject action) {
        switch (type) {
            case "move" -> controller.move(bot,
                    str(action, "direction", "forward"),
                    (int) num(action, "seconds", 1));
            case "turn" -> controller.turn(bot, str(action, "direction", "left"));
            case "look_at_player" -> controller.lookAtPlayer(bot, relevantPlayer);
            case "look_direction" -> controller.lookDirection(bot, str(action, "direction", "north"));
            case "approach_player" -> controller.approachPlayer(bot, relevantPlayer);
            case "follow_player" -> controller.followPlayer(bot, relevantPlayer, (int) num(action, "seconds", 5));
            case "jump" -> controller.jump(bot);
            case "sneak" -> controller.sneak(bot, bool(action, "on", true));
            case "sprint" -> controller.sprint(bot, bool(action, "on", true));
            case "attack" -> controller.attack(bot, str(action, "mode", "once"), (int) num(action, "seconds", 1));
            case "use_item" -> controller.useItem(bot, str(action, "mode", "once"), (int) num(action, "seconds", 1));
            case "stop" -> controller.stop(bot);
            case "prank_lava" -> {
                if (bot.isRude() && bot.pranksEnabled) {
                    prankManager.lavaTrap(bot, relevantPlayer);
                }
            }
            case "none" -> { /* intentional no-op */ }
            default -> LOGGER.warning("[AI Companions] Unknown action type from model: " + type);
        }
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    private static double num(JsonObject o, String key, double def) {
        return o.has(key) ? o.get(key).getAsDouble() : def;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
