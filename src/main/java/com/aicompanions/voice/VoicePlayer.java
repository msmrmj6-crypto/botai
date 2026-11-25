package com.aicompanions.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays raw 16-bit PCM mono audio (as returned by ElevenLabsTTS when asked
 * for output_format=pcm_24000) through the default system speakers using
 * plain javax.sound.sampled - no extra libraries needed.
 */
public class VoicePlayer {

    /** Fire-and-forget: plays on its own daemon thread so it never blocks
     *  the caller (the client tick thread, or a background brain thread). */
    public static void playAsync(byte[] pcmBytes, int sampleRateHz) {
        if (pcmBytes == null || pcmBytes.length == 0) {
            return;
        }
        Thread t = new Thread(() -> play(pcmBytes, sampleRateHz), "aicompanions-voice-playback");
        t.setDaemon(true);
        t.start();
    }

    private static void play(byte[] pcmBytes, int sampleRateHz) {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRateHz,
                16,     // bits per sample
                1,      // mono
                2,      // frame size in bytes (16-bit * 1 channel)
                sampleRateHz,
                false   // little-endian
        );
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(format);
            line.start();
            line.write(pcmBytes, 0, pcmBytes.length);
            line.drain();
        } catch (Exception e) {
            System.err.println("[AI Companions] Could not play voice audio: " + e.getMessage());
        }
    }
}
