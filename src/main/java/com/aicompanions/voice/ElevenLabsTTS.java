package com.aicompanions.voice;

import com.aicompanions.util.HttpUtil;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Calls ElevenLabs' text-to-speech endpoint and asks for RAW PCM audio
 * (output_format=pcm_24000) instead of MP3. That means VoicePlayer can play
 * the response straight through javax.sound.sampled with no MP3 decoding
 * library on the classpath at all.
 * <p>
 * Endpoint: POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}
 * Auth: "xi-api-key" header (not Bearer).
 */
public class ElevenLabsTTS {

    private static final String ENDPOINT_TEMPLATE =
            "https://api.elevenlabs.io/v1/text-to-speech/%s?output_format=%s";

    private final String apiKey;
    private final String modelId;
    private final String outputFormat;

    public ElevenLabsTTS(String apiKey, String modelId, String outputFormat) {
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.outputFormat = outputFormat;
    }

    /** @return raw PCM bytes (16-bit signed little-endian mono) ready for VoicePlayer. */
    public byte[] speak(String voiceId, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ElevenLabs API key is not set. Fill it into "
                    + "config/aicompanions.json or set the ELEVENLABS_API_KEY environment variable.");
        }
        if (text == null || text.isBlank()) {
            return new byte[0];
        }

        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("model_id", modelId);

        String url = String.format(ENDPOINT_TEMPLATE, voiceId, outputFormat);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/pcm")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<byte[]> response = HttpUtil.CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("ElevenLabs TTS error " + response.statusCode() + ": "
                        + new String(response.body(), StandardCharsets.UTF_8));
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call ElevenLabs TTS", e);
        }
    }

    /** Sample rate matching ttsOutputFormat, e.g. "pcm_24000" -> 24000. Keep
     *  these in sync if you change the config value. */
    public static int sampleRateFromFormat(String outputFormat) {
        try {
            return Integer.parseInt(outputFormat.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 24000;
        }
    }
}
