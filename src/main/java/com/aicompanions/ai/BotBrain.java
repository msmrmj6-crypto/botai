package com.aicompanions.ai;

import com.aicompanions.config.BotProfile;
import com.aicompanions.config.ModConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Glue between config, OpenAIClient and the rest of the mod. Call
 * {@link #decide(BotProfile, String)} with whatever just happened (a chat
 * line directed at the bot, or "[no one is talking to you right now, do
 * something on your own]" for an autonomous tick) and get back parsed
 * speech + actions.
 * <p>
 * Every call here does blocking network I/O - always invoke this from a
 * background thread (see BotManager), never from the server/render thread.
 */
public class BotBrain {

    private static final Logger LOGGER = Logger.getLogger("aicompanions");

    private final OpenAIClient client;

    public BotBrain(ModConfig config) {
        this.client = new OpenAIClient(config.resolvedOpenAiKey(), config.openaiModel);
    }

    public Decision decide(BotProfile bot, String incomingMessage) {
        String systemPrompt = PersonalityPrompts.build(bot);
        List<String> history = new ArrayList<>(bot.conversationHistory);

        String raw;
        try {
            raw = client.requestDecision(systemPrompt, history, incomingMessage);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[AI Companions] OpenAI call failed for " + bot.displayName, e);
            return new Decision("...", new JsonArray());
        }

        Decision decision = parse(raw);

        bot.remember("Player: " + incomingMessage);
        bot.remember("You: " + decision.speech());

        return decision;
    }

    private Decision parse(String raw) {
        try {
            JsonObject obj = JsonParser.parseString(stripCodeFence(raw)).getAsJsonObject();
            String speech = obj.has("speech") ? obj.get("speech").getAsString() : "";
            JsonArray actions = obj.has("actions") && obj.get("actions").isJsonArray()
                    ? obj.getAsJsonArray("actions")
                    : new JsonArray();
            return new Decision(speech, actions);
        } catch (Exception e) {
            LOGGER.warning("[AI Companions] Could not parse model output as JSON, treating it as plain speech: " + raw);
            return new Decision(raw, new JsonArray());
        }
    }

    /** Some models wrap JSON in ```json fences even when told not to - strip it defensively. */
    private String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline != -1) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }
}
