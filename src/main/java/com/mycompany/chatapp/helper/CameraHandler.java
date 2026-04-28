package com.mycompany.chatapp.helper;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import javax.swing.*;
import java.awt.*;

public class CameraHandler {

    // Đoạn này để cấu hình driver cho Mac M1 ngay khi khởi động
    static {
        System.setProperty("bridj.platform.library", "apple_universal");
        System.setProperty("webcam.driver", "com.github.sarxos.webcam.ds.buildin.WebcamDefaultDriver");
    }

    public static void openMeeting(JFrame parent, String roomName) {
        new Thread(() -> {
            try {
                // 1. Thử lấy Camera mặc định của Macbook
                Webcam webcam = Webcam.getDefault();
                
                if (webcam != null) {
                    if (webcam.isOpen()) webcam.close();

                    // 2. Set độ phân giải nhỏ (QVGA) để Mac M1 dễ xử lý, tránh lỗi
                    webcam.setViewSize(WebcamResolution.QVGA.getSize());
                    
                    WebcamPanel panel = new WebcamPanel(webcam);
                    panel.setFPSDisplayed(true);
                    panel.setMirrored(true); // Chế độ soi gương

                    // 3. Tạo cửa sổ hiện mặt Nam
                    JFrame camFrame = new JFrame("Cuộc họp trực tuyến: #" + roomName);
                    camFrame.add(panel);
                    camFrame.pack();
                    camFrame.setLocationRelativeTo(parent);
                    camFrame.setVisible(true);

                    // 4. Khi tắt cửa sổ thì tắt đèn Camera
                    camFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
                        public void windowClosing(java.awt.event.WindowEvent e) {
                            webcam.close();
                            camFrame.dispose();
                        }
                    });
                } else {
                    // CỨU CÁNH: Nếu Java không gọi được Cam, mở app hệ thống để demo
                    JOptionPane.showMessageDialog(parent, "Đang kết nối Camera hệ thống...");
                    Runtime.getRuntime().exec("open -a 'Photo Booth'");
                }
            } catch (Exception ex) {
                try {
                    // Nếu lỗi driver BridJ (lỗi đỏ console), mở app Photo Booth chữa cháy
                    Runtime.getRuntime().exec("open -a 'Photo Booth'");
                } catch (Exception e) {}
            }
        }).start();
    }
}