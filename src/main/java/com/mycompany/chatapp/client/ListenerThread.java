package com.mycompany.chatapp.client;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.util.Base64;

public class ListenerThread implements Runnable {

    private final BufferedReader in;
    private final JTextPane chatArea;
    private final JLabel statusBar;
    private final DefaultListModel<String> userListModel;
    private final String myUsername;

    public ListenerThread(BufferedReader in,
                          JTextPane chatArea,
                          JLabel statusBar,
                          DefaultListModel<String> userListModel,
                          String myUsername) {
        this.in            = in;
        this.chatArea      = chatArea;
        this.statusBar     = statusBar;
        this.userListModel = userListModel;
        this.myUsername    = myUsername;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handle(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                appendText("[Mất kết nối với server]\n", Color.RED));
        }
    }

    private void handle(String msg) {
        // ── Ảnh ──────────────────────────────────────────────
        if (msg.startsWith("IMG:")) {
            // Format: IMG:[room] sender: <base64>
            int colonIdx = msg.lastIndexOf(": ");
            if (colonIdx != -1) {
                String header = msg.substring(0, colonIdx + 2);
                String b64    = msg.substring(colonIdx + 2);
                appendText(header + "\n", Color.CYAN);
                insertImage(b64);
            }
            return;
        }

        // ── Danh sách user online ─────────────────────────────
        if (msg.startsWith("USERLIST:")) {
            String[] users = msg.substring(9).split(",");
            userListModel.clear();
            for (String u : users) {
                if (!u.isBlank()) userListModel.addElement(u.trim());
            }
            return;
        }

        // ── Server duyệt vào phòng ────────────────────────────
        if (msg.equals("APPROVED")) {
            statusBar.setText("Đã vào phòng!");
            appendText("[Server] Bạn đã được duyệt vào phòng.\n", Color.GREEN);
            return;
        }

        // ── Đang chờ duyệt ───────────────────────────────────
        if (msg.equals("PENDING")) {
            statusBar.setText("Đang chờ admin duyệt...");
            appendText("[Server] Chờ admin duyệt nhé!\n", Color.YELLOW);
            return;
        }

        // ── Bị kick ──────────────────────────────────────────
        if (msg.equals("KICKED")) {
            appendText("[Server] Bạn đã bị kick!\n", Color.RED);
            statusBar.setText("Bị kick khỏi server.");
            return;
        }

        // ── Cảnh báo từ ngữ ──────────────────────────────────
        if (msg.startsWith("WARN:")) {
            appendText(msg.substring(5) + "\n", Color.ORANGE);
            return;
        }

        // ── Private message ───────────────────────────────────
        if (msg.startsWith("PM:")) {
            appendText(msg.substring(3) + "\n", new Color(255, 180, 255));
            return;
        }

        // ── Message thường ───────────────────────────────────
        if (msg.startsWith("MSG:")) {
            String text = msg.substring(4);
            // Highlight tin nhắn của chính mình
            if (text.contains(myUsername + ":")) {
                appendText(text + "\n", new Color(130, 220, 255));
            } else {
                appendText(text + "\n", Color.WHITE);
            }
            return;
        }

        // ── History khi vào channel ───────────────────────────
        if (msg.startsWith("HISTORY:")) {
            String[] history = msg.substring(8).split("\\|");
            appendText("── Lịch sử tin nhắn ──\n", Color.GRAY);
            for (String h : history) {
                if (!h.isBlank()) appendText(h + "\n", new Color(180, 180, 180));
            }
            appendText("──────────────────────\n", Color.GRAY);
            return;
        }

        // ── Mọi thứ khác ─────────────────────────────────────
        appendText(msg + "\n", Color.LIGHT_GRAY);
    }

    // Chèn text màu vào JTextPane
    private void appendText(String text, Color color) {
        StyledDocument doc = chatArea.getStyledDocument();
        Style style = chatArea.addStyle("s", null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, "SansSerif");
        StyleConstants.setFontSize(style, 14);
        try {
            doc.insertString(doc.getLength(), text, style);
            // Auto-scroll xuống cuối
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // Decode Base64 → ảnh → chèn vào JTextPane
    private void insertImage(String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64.trim());
            ImageIcon raw = new ImageIcon(data);
            // Scale ảnh về tối đa 300px chiều rộng
            Image scaled = raw.getImage().getScaledInstance(300, -1, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);

            StyledDocument doc = chatArea.getStyledDocument();
            Style style = chatArea.addStyle("img", null);
            StyleConstants.setIcon(style, icon);
            try {
                doc.insertString(doc.getLength(), " ", style);
                doc.insertString(doc.getLength(), "\n", null);
                chatArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            appendText("[Không load được ảnh]\n", Color.RED);
        }
    }
}