package com.mycompany.chatapp.helper;

import javax.swing.*;
import java.awt.*;

public class EmojiPicker extends JDialog {

    private static final String[] EMOJIS = {
        "😀","😂","😍","😎","😭","😡","🥳","😴",
        "👍","👎","👏","🙏","🤝","✌️","💪","🫶",
        "❤️","🔥","⭐","💯","🎉","🎮","🍕","🎵",
        "😈","🤖","👻","🐱","🐶","🦊","🐸","🤡"
    };

    private final JTextField targetField;

    public EmojiPicker(JFrame parent, JTextField targetField) {
        super(parent, "Emoji", false); // false = không block UI
        this.targetField = targetField;

        JPanel grid = new JPanel(new GridLayout(0, 8, 4, 4));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (String emoji : EMOJIS) {
            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setBackground(new Color(47, 49, 54));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                // Chèn emoji vào vị trí con trỏ
                int pos = targetField.getCaretPosition();
                String current = targetField.getText();
                targetField.setText(
                    current.substring(0, pos) + emoji + current.substring(pos));
                targetField.setCaretPosition(pos + emoji.length());
                targetField.requestFocus();
            });
            grid.add(btn);
        }

        setContentPane(grid);
        pack();
        setLocationRelativeTo(parent);
    }

    public void toggle() {
        setVisible(!isVisible());
    }
}