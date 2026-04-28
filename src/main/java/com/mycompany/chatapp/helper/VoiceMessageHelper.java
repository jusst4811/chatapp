package com.mycompany.chatapp.helper;

import javax.sound.sampled.*;
import java.io.*;

public class VoiceMessageHelper {
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
    private TargetDataLine mic;
    private ByteArrayOutputStream recordStream;
    private volatile boolean isRecording = false;

    public void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(FORMAT);
            mic.start();
            recordStream = new ByteArrayOutputStream();
            isRecording = true;
            new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording) {
                    int count = mic.read(buffer, 0, buffer.length);
                    if (count > 0) recordStream.write(buffer, 0, count);
                }
            }).start();
            System.out.println("DEBUG: Bắt đầu ghi âm");
        } catch (Exception e) {
            System.err.println("Lỗi mở Mic: " + e.getMessage());
        }
    }

    public byte[] stopRecording() {
        isRecording = false;
        if (mic != null) { mic.stop(); mic.close(); }
        byte[] data = (recordStream != null) ? recordStream.toByteArray() : null;
        System.out.println("DEBUG: Dừng ghi âm, size=" + (data != null ? data.length : 0));
        return data;
    }

    public static void playAudio(byte[] audioData) {
        if (audioData == null || audioData.length < 100) {
            System.out.println("DEBUG: Audio quá ngắn: " + (audioData != null ? audioData.length : 0));
            return;
        }
        System.out.println("DEBUG: Phát audio size=" + audioData.length);
        new Thread(() -> {
            SourceDataLine speaker = null;
            try {
                AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                speaker = (SourceDataLine) AudioSystem.getLine(info);
                speaker.open(format);
                speaker.start();
                speaker.write(audioData, 0, audioData.length);
                speaker.drain();
            } catch (Exception e) {
                System.err.println("Lỗi phát audio: " + e.getMessage());
            } finally {
                if (speaker != null) speaker.close();
            }
        }).start();
    }
}