package com.aicompanions.ai;

import com.aicompanions.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal client for OpenAI's Chat Completions endpoint. We force JSON-mode
 * output so {@link BotBrain} can parse a structured {speech, actions[]}
 * reply instead of scraping free text.
 */
public class OpenAIClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;

    public OpenAIClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * @param systemPrompt the personality + action-grammar instructions
     * @param historyLines prior turns, oldest first, already formatted like
     *                     "Player: ..." / "You: ..." - kept as plain user
     *                     messages for simplicity rather than strict roles
     * @param userMessage  the newest thing the player said or did
     * @return raw assistant JSON text (NOT yet parsed into BotAction)
     */
    public String requestDecision(String systemPrompt, List<String> historyLines, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not set. Fill it into "
                    + "config/aicompanions.json or set the OPENAI_API_KEY environment variable.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.9);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        body.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        for (String line : historyLines) {
            messages.add(message("user", line));
        }
        messages.add(message("user", userMessage));
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HttpUtil.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("OpenAI API error " + response.statusCode() + ": " + response.body());
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OpenAI", e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
