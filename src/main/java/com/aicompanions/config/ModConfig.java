package com.aicompanions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads/saves config/aicompanions.json in the Minecraft config directory.
 * <p>
 * API keys are read with this priority, so you are never forced to put a
 * secret in a plain-text file if you don't want to:
 *   1. Environment variable (OPENAI_API_KEY / ELEVENLABS_API_KEY)
 *   2. The matching field in aicompanions.json
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "aicompanions.json";

    public String openaiApiKey = "";
    public String openaiModel = "gpt-4o-mini";

    public String elevenLabsApiKey = "";
    /** scribe_v2 is ElevenLabs' current speech-to-text model. */
    public String sttModel = "scribe_v2";
    /** eleven_multilingual_v2 handles Arabic + English in one model. */
    public String ttsModel = "eleven_multilingual_v2";
    /** Raw 16-bit PCM mono @ 24kHz - no MP3 decoder needed on our end. */
    public String ttsOutputFormat = "pcm_24000";

    public List<BotProfile> bots = new ArrayList<>();

    public static ModConfig load() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                writeDefault(path);
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                return cfg != null ? cfg : defaultConfig();
            }
        } catch (IOException e) {
            throw new RuntimeException("[AI Companions] Could not load " + FILE_NAME, e);
        }
    }

    public void save() {
        Path path = configPath();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            throw new RuntimeException("[AI Companions] Could not save " + FILE_NAME, e);
        }
    }

    public String resolvedOpenAiKey() {
        String env = System.getenv("OPENAI_API_KEY");
        return (env != null && !env.isBlank()) ? env : openaiApiKey;
    }

    public String resolvedElevenLabsKey() {
        String env = System.getenv("ELEVENLABS_API_KEY");
        return (env != null && !env.isBlank()) ? env : elevenLabsApiKey;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** First run: copy the bundled template (with the two voice IDs you
     *  gave already filled in) out to the real config file. */
    private static void writeDefault(Path path) throws IOException {
        try (InputStream in = ModConfig.class.getClassLoader()
                .getResourceAsStream("aicompanions-default-config.json")) {
            if (in != null) {
                Files.copy(in, path);
                return;
            }
        }
        // Fallback in case the bundled resource ever goes missing.
        ModConfig fallback = defaultConfig();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(fallback, writer);
        }
    }

    private static ModConfig defaultConfig() {
        ModConfig cfg = new ModConfig();

        BotProfile polite = new BotProfile();
        polite.carpetName = "Rafiq";
        polite.displayName = "رفيق";
        polite.personality = "POLITE";
        polite.voiceId = "7GuGTyc18I5wWanLbbNq";
        polite.keybindId = "talk_polite";
        polite.cursingEnabled = false;
        polite.pranksEnabled = false;
        cfg.bots.add(polite);

        BotProfile rude = new BotProfile();
        rude.carpetName = "Waqah";
        rude.displayName = "الوقح";
        rude.personality = "RUDE";
        rude.voiceId = "DhfzvPf0OIN8xdGwcHOk";
        rude.keybindId = "talk_rude";
        rude.cursingEnabled = true;
        rude.pranksEnabled = true;
        cfg.bots.add(rude);

        return cfg;
    }
}
