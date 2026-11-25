package com.aicompanions.voice;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Push-to-talk microphone capture. Call {@link #start()} on key-down and
 * {@link #stopAndGetWav()} on key-up; the returned bytes are a complete WAV
 * file ready to upload straight to ElevenLabs' speech-to-text endpoint.
 * <p>
 * One instance should be reused per bot's keybind rather than recreated
 * every press, so keep it as a field on the client class.
 */
public class MicRecorder {

    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread captureThread;
    private volatile boolean recording = false;

    public boolean isRecording() {
        return recording;
    }

    public void start() {
        if (recording) {
            return;
        }
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[AI Companions] No microphone line available for push-to-talk.");
                return;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
            buffer = new ByteArrayOutputStream();
            recording = true;

            captureThread = new Thread(this::captureLoop, "aicompanions-mic-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (Exception e) {
            System.err.println("[AI Companions] Could not open microphone: " + e.getMessage());
            recording = false;
        }
    }

    private void captureLoop() {
        byte[] chunk = new byte[2048];
        while (recording) {
            int read = line.read(chunk, 0, chunk.length);
            if (read > 0) {
                buffer.write(chunk, 0, read);
            }
        }
    }

    /** Stops capture and returns a ready-to-send WAV file, or an empty array
     *  if nothing was recorded / the mic failed to open. */
    public byte[] stopAndGetWav() {
        if (!recording) {
            return new byte[0];
        }
        recording = false;
        // Stop the line FIRST: a blocking line.read() in captureLoop only
        // returns once the line is stopped, so stopping before joining is
        // what lets the capture thread actually exit promptly.
        line.stop();
        try {
            captureThread.join(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        line.close();

        byte[] pcm = buffer.toByteArray();
        if (pcm.length == 0) {
            return new byte[0];
        }

        try (ByteArrayOutputStream wavOut = new ByteArrayOutputStream()) {
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize());
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        } catch (IOException e) {
            System.err.println("[AI Companions] Could not encode WAV from mic capture: " + e.getMessage());
            return new byte[0];
        }
    }
}
