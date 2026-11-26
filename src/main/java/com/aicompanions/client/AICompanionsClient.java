package com.aicompanions.client;

import com.aicompanions.AICompanionsMod;
import com.aicompanions.bot.BotManager;
import com.aicompanions.config.BotProfile;
import com.aicompanions.config.ModConfig;
import com.aicompanions.voice.ElevenLabsSTT;
import com.aicompanions.voice.MicRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Push-to-talk: hold a bot's key, talk, release it, and the clip gets sent
 * to ElevenLabs Scribe -> OpenAI -> back out as the bot's voice + actions.
 * <p>
 * IMPORTANT SCOPE NOTE: this reaches the server-side BotManager directly
 * (AICompanionsMod.getBotManager()), which only works because in
 * singleplayer the client and the "integrated server" run in the same JVM.
 * If you ever play on a separate dedicated server, this client-side class
 * can no longer call into that other process directly - you'd need to add
 * a custom network packet (ClientPlayNetworking/ServerPlayNetworking from
 * Fabric API) carrying the transcribed text instead. Out of scope for the
 * singleplayer setup this project was built for, but flagged here so it's
 * not a surprise later.
 */
public class AICompanionsClient implements ClientModInitializer {

    private static final ExecutorService STT_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "aicompanions-stt");
        t.setDaemon(true);
        return t;
    });

    // 1.21.11 changed KeyBinding's category parameter from a plain
    // translation-key String to a KeyBinding.Category object built from an
    // Identifier - create() derives the lang key from the id itself
    // (key.categories.aicompanions.general), which is what's in the lang file.
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("aicompanions", "general"));

    private final Map<String, KeyBinding> keyBindings = new HashMap<>();
    private final Map<String, MicRecorder> recorders = new HashMap<>();

    @Override
    public void onInitializeClient() {
        if (AICompanionsMod.CONFIG == null) {
            AICompanionsMod.CONFIG = ModConfig.load();
        }

        int key = GLFW.GLFW_KEY_G;
        for (BotProfile bot : AICompanionsMod.CONFIG.bots) {
            KeyBinding kb = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.aicompanions." + bot.keybindId,
                    InputUtil.Type.KEYSYM,
                    key++,
                    CATEGORY));
            keyBindings.put(bot.carpetName, kb);
            recorders.put(bot.carpetName, new MicRecorder());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (BotProfile bot : AICompanionsMod.CONFIG.bots) {
                KeyBinding kb = keyBindings.get(bot.carpetName);
                MicRecorder recorder = recorders.get(bot.carpetName);
                if (kb == null || recorder == null) {
                    continue;
                }
                if (kb.isPressed() && !recorder.isRecording()) {
                    recorder.start();
                } else if (!kb.isPressed() && recorder.isRecording()) {
                    byte[] wav = recorder.stopAndGetWav();
                    if (wav.length > 0) {
                        STT_EXECUTOR.submit(() -> handleRecording(bot, wav));
                    }
                }
            }
        });
    }

    private void handleRecording(BotProfile bot, byte[] wav) {
        try {
            ElevenLabsSTT stt = new ElevenLabsSTT(
                    AICompanionsMod.CONFIG.resolvedElevenLabsKey(),
                    AICompanionsMod.CONFIG.sttModel);
            String text = stt.transcribe(wav);
            if (text == null || text.isBlank()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return;
            }
            client.execute(() -> client.player.sendMessage(
                    Text.literal("[" + bot.displayName + " heard] " + text), false));

            if (client.getServer() == null) {
                // Not singleplayer / not hosting - see the class javadoc.
                client.execute(() -> client.player.sendMessage(
                        Text.literal("[AI Companions] Voice chat only works in singleplayer right now."), false));
                return;
            }

            BotManager manager = AICompanionsMod.getBotManager();
            if (manager == null) {
                return;
            }
            ServerPlayerEntity serverPlayer =
                    client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            if (serverPlayer != null) {
                manager.handleSpeech(bot, serverPlayer, text);
            }
        } catch (Exception e) {
            System.err.println("[AI Companions] Push-to-talk failed: " + e.getMessage());
        }
    }
}
