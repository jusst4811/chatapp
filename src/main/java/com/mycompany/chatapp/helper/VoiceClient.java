package com.mycompany.chatapp.helper;

import javax.sound.sampled.*;
import java.io.*;
import java.net.*;

public class VoiceClient {

    private static final int    UDP_PORT    = 8081;
    private static final int    BUFFER_SIZE = 1024;
    // Thống nhất dùng một định dạng Audio
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);

    private final String serverHost;
    private final String username;
    private final String room;

    private DatagramSocket udpSocket;
    private TargetDataLine mic;
    private SourceDataLine speaker;

    private volatile boolean running = false;
    private volatile boolean muted   = false;

    public VoiceClient(String serverHost, String username, String room) {
        this.serverHost = serverHost;
        this.username   = username;
        this.room       = room;
    }

    public void start() {
        if (running) return;
        running = true;
        try {
            udpSocket = new DatagramSocket();
            openMic();
            openSpeaker();
            
            // Khởi chạy 2 luồng riêng biệt cho Thu và Phát
            new Thread(this::captureLoop, "voice-capture").start();
            new Thread(this::playbackLoop, "voice-playback").start();
            
            System.out.println("Voice Client started for room: " + room);
        } catch (Exception e) {
            e.printStackTrace();
            stop();
        }
    }

    public void stop() {
        running = false;
        if (mic     != null) { mic.stop();     mic.close(); }
        if (speaker != null) { speaker.stop(); speaker.close(); }
        if (udpSocket != null) udpSocket.close();
    }

    public void toggleMute() { muted = !muted; }
    public boolean isMuted()  { return muted; }

    private void openMic() throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        mic = (TargetDataLine) AudioSystem.getLine(info);
        mic.open(FORMAT);
        mic.start();
    }

    private void openSpeaker() throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        speaker = (SourceDataLine) AudioSystem.getLine(info);
        speaker.open(FORMAT);
        speaker.start();
    }

    // Luồng: Đọc dữ liệu từ Mic -> Gửi lên Server qua UDP
    private void captureLoop() {
        byte[] buf = new byte[BUFFER_SIZE];
        try {
            InetAddress addr = InetAddress.getByName(serverHost);
            while (running) {
                int read = mic.read(buf, 0, buf.length);
                if (read > 0 && !muted) {
                    byte[] roomBytes = room.getBytes("UTF-8");
                    byte[] packetData = new byte[1 + roomBytes.length + read];
                    
                    packetData[0] = (byte) roomBytes.length;
                    System.arraycopy(roomBytes, 0, packetData, 1, roomBytes.length);
                    System.arraycopy(buf, 0, packetData, 1 + roomBytes.length, read);

                    DatagramPacket dp = new DatagramPacket(packetData, packetData.length, addr, UDP_PORT);
                    udpSocket.send(dp); // QUAN TRỌNG: Phải có dòng này để gửi đi
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    // Luồng: Nhận dữ liệu UDP từ Server -> Phát ra Loa
    private void playbackLoop() {
        byte[] receiveBuf = new byte[BUFFER_SIZE + 64]; // Buffer lớn hơn một chút để chứa prefix
        try {
            while (running) {
                DatagramPacket dp = new DatagramPacket(receiveBuf, receiveBuf.length);
                udpSocket.receive(dp);

                // Phân tích gói tin nhận được
                byte[] data = dp.getData();
                int roomLen = data[0];
                // Bạn có thể kiểm tra room ở đây nếu muốn lọc dữ liệu đúng phòng
                
                int audioOffset = 1 + roomLen;
                int audioLen = dp.getLength() - audioOffset;

                if (audioLen > 0) {
                    speaker.write(data, audioOffset, audioLen);
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
}