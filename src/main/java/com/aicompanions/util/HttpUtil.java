package com.aicompanions.util;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Tiny shared helpers so OpenAIClient / ElevenLabsTTS / ElevenLabsSTT don't
 * each reinvent HttpClient setup and multipart/form-data encoding.
 * <p>
 * Deliberately dependency-free: java.net.http (built into Java 21) is all
 * we need, including for the multipart upload that ElevenLabs' speech-to-
 * text endpoint expects.
 */
public final class HttpUtil {

    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private HttpUtil() {
    }

    /**
     * Builds a multipart/form-data body from a mix of plain text fields and
     * a single named file field, and returns the matching Content-Type
     * header value (with the boundary) alongside the request BodyPublisher.
     */
    public static MultipartBody buildMultipart(Map<String, String> textFields,
                                                String fileFieldName,
                                                String fileName,
                                                byte[] fileBytes,
                                                String fileMimeType) {
        String boundary = "AICompanions-" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (Map.Entry<String, String> e : textFields.entrySet()) {
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write((e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fileFieldName
                    + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + fileMimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new MultipartBody("multipart/form-data; boundary=" + boundary,
                HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
    }

    public record MultipartBody(String contentType, HttpRequest.BodyPublisher body) {
    }
}
