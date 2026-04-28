package com.mycompany.chatapp.helper;

import javax.swing.*;
import java.awt.Desktop;
import java.net.URI;
import java.io.PrintWriter;

public class openMeeting {
    // Thêm out và myName vào đây cho khớp
    public static void start(JFrame parent, String roomName, PrintWriter out, String myName) {
        new Thread(() -> {
            try {
                // Tạo link phòng họp
                String roomUrl = "https://meet.jit.si/ChatApp_NamBui_" + roomName;

                // Gửi tin nhắn vào khung chat để người kia cũng thấy link mà bấm vào
                if (out != null) {
                    out.println("MSG:[Hệ thống] " + myName + " đã mở phòng họp trực tuyến tại: " + roomUrl);
                }
                
                // Mở trình duyệt
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI(roomUrl));
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }
}