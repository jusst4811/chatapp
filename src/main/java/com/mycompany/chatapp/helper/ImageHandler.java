package com.mycompany.chatapp.helper;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import java.awt.Component; // Thêm import này
import java.awt.FileDialog; 

public class ImageHandler {

    // --- HÀM MỚI: Dùng để lấy mảng byte (Binary) - Sửa lỗi đỏ ở ChatClientUI ---
    public static byte[] pickImageBytes(JFrame parent) {
        FileDialog dialog = new FileDialog(parent, "Chọn ảnh để gửi", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String low = name.toLowerCase();
            return low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".gif");
        });
        dialog.setVisible(true);

        if (dialog.getFile() == null) return null;

        File file = new File(dialog.getDirectory(), dialog.getFile());
        
        try {
            // Đọc trực tiếp ra mảng byte, không cần Base64 nên không lo giới hạn dung lượng
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Lỗi đọc file: " + e.getMessage());
            return null;
        }
    }

    // --- HÀM CŨ: (Giữ nguyên cho bạn nếu cần dùng Base64) ---
    public static String pickAndEncode(JFrame parent, String room, String username) {
        FileDialog dialog = new FileDialog(parent, "Chọn ảnh để gửi", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String low = name.toLowerCase();
            return low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".gif");
        });
        dialog.setVisible(true);

        if (dialog.getFile() == null) return null;

        File file = new File(dialog.getDirectory(), dialog.getFile());

        if (file.length() > 2 * 1024 * 1024) {
            JOptionPane.showMessageDialog(parent, "Ảnh quá nặng ( > 2MB), vui lòng chọn ảnh nhẹ hơn!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return "IMG:[" + room + "] " + username + ": " + b64;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Lỗi đọc file: " + e.getMessage());
            return null;
        }
    }
}