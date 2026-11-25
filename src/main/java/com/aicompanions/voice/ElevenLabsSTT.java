package com.aicompanions.voice;

import com.aicompanions.util.HttpUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Sends a recorded WAV clip to ElevenLabs Scribe for transcription.
 * Endpoint: POST https://api.elevenlabs.io/v1/speech-to-text
 * (multipart/form-data, fields: model_id, file)
 */
public class ElevenLabsSTT {

    private static final String ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text";

    private final String apiKey;
    private final String modelId;

    public ElevenLabsSTT(String apiKey, String modelId) {
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    /** @return the transcribed text, or "" if the clip was empty/unintelligible. */
    public String transcribe(byte[] wavBytes) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ElevenLabs API key is not set. Fill it into "
                    + "config/aicompanions.json or set the ELEVENLABS_API_KEY environment variable.");
        }
        if (wavBytes == null || wavBytes.length == 0) {
            return "";
        }

        HttpUtil.MultipartBody multipart = HttpUtil.buildMultipart(
                Map.of("model_id", modelId),
                "file", "speech.wav", wavBytes, "audio/wav");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("xi-api-key", apiKey)
                .header("Content-Type", multipart.contentType())
                .POST(multipart.body())
                .build();

        try {
            HttpResponse<String> response = HttpUtil.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("ElevenLabs STT error " + response.statusCode() + ": " + response.body());
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.has("text") ? json.get("text").getAsString() : "";
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call ElevenLabs STT", e);
        }
    }
}
