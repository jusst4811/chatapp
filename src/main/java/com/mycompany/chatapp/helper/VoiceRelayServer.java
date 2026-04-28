package com.mycompany.chatapp.helper;

import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceRelayServer implements Runnable {
    private static final int BUFFER_SIZE = 65535;
    private static final long CLIENT_TIMEOUT_MS = 10000; // 10 giây không gửi → xóa
    private final int port;

    // Lưu client kèm thời gian gửi gói cuối cùng
    private final ConcurrentHashMap<InetSocketAddress, Long> voiceClients = new ConcurrentHashMap<>();

    public VoiceRelayServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (DatagramSocket udpSocket = new DatagramSocket(port)) {
            System.out.println("VoiceRelayServer đang chạy trên cổng UDP: " + port);

            // Thread dọn dẹp client timeout
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        long now = System.currentTimeMillis();
                        voiceClients.entrySet().removeIf(entry -> {
                            boolean timedOut = (now - entry.getValue()) > CLIENT_TIMEOUT_MS;
                            if (timedOut) {
                                System.out.println("Client timeout, xóa: " + entry.getKey());
                            }
                            return timedOut;
                        });
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();

            byte[] buf = new byte[BUFFER_SIZE];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                udpSocket.receive(packet);

                InetSocketAddress sender = new InetSocketAddress(
                    packet.getAddress(), packet.getPort()
                );

                // Cập nhật thời gian gửi gói cuối
                if (!voiceClients.containsKey(sender)) {
                    System.out.println("Client Voice mới: " + sender);
                }
                voiceClients.put(sender, System.currentTimeMillis());

                // Forward đến tất cả client khác
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                for (InetSocketAddress addr : voiceClients.keySet()) {
                    if (addr.equals(sender)) continue;
                    try {
                        DatagramSocket sendSocket = new DatagramSocket();
                        DatagramPacket forward = new DatagramPacket(
                            data, data.length, addr.getAddress(), addr.getPort()
                        );
                        sendSocket.send(forward);
                        sendSocket.close();
                    } catch (Exception e) {
                        voiceClients.remove(addr);
                        System.out.println("Xóa client lỗi: " + addr);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi VoiceRelayServer: " + e.getMessage());
        }
    }
}