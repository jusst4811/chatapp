package com.mycompany.chatapp.client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.Base64;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatDarkLaf;
import com.mycompany.chatapp.helper.EmojiPicker;
import com.mycompany.chatapp.helper.ImageHandler;
import com.mycompany.chatapp.helper.VoiceMessageHelper;
import com.mycompany.chatapp.helper.WebcamHelper;
import com.mycompany.chatapp.helper.openMeeting;
import com.mycompany.chatapp.model.ScheduleSystem;
import com.mycompany.chatapp.service.DBHelper;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

public class ChatClientUI {

    // ── Animation state cho login border ──
    private float ledAlpha = 1.0f;
    private boolean isBrightening = false;
    private Timer ledTimer;
    private float hue = 0.0f;

    static {
        System.setProperty("bridj.platform.library", "apple_universal");
    }

    // ── Color palette ──
    private static final Color BG_DARKEST   = new Color(10, 12, 18);
    private static final Color BG_DARK      = new Color(15, 18, 28);
    private static final Color BG_PANEL     = new Color(20, 24, 38);
    private static final Color BG_SIDEBAR   = new Color(13, 16, 26);
    private static final Color BG_INPUT     = new Color(25, 30, 48);
    private static final Color BG_CARD      = new Color(28, 34, 52);
    private static final Color NEON_GREEN   = new Color(0, 255, 136);
    private static final Color NEON_CYAN    = new Color(0, 212, 255);
    private static final Color NEON_PURPLE  = new Color(130, 80, 255);
    private static final Color TEXT_PRIMARY = new Color(220, 230, 255);
    private static final Color TEXT_MUTED   = new Color(100, 120, 160);
    private static final Color DANGER       = new Color(255, 60, 80);

    // ── Fields ──
    private JButton sidebarAva = null;
    private Socket socket;
    private DataInputStream  dataIn;
    private DataOutputStream dataOut;
    private PrintWriter out;
    private String myName    = "";
    private String currentRoom = "general";
    private String myRole    = "STUDENT";
    private ImageIcon savedAvatarIcon = null;

    private final VoiceMessageHelper voiceHelper = new VoiceMessageHelper();

    // ── Gemini AI ──
    private Object geminiModel; // placeholder, không dùng
    private TeachingAssistant aiAssistant;

    interface TeachingAssistant {
        String chat(String userMessage);
    }

    // ── Login UI components ──
    private final JFrame         loginFrame  = new JFrame("Discord Login");
    private final JTextField     userField   = new JTextField(16);
    private final JPasswordField passField   = new JPasswordField(16);
    private final JLabel         loginStatus = new JLabel(" ", SwingConstants.CENTER);

    // ── Chat UI components ──
    private final JFrame     chatFrame  = new JFrame("Discord Clone");
    private final JTextPane  chatPane   = new JTextPane();
    private final JTextField inputField = new JTextField();
    private final JLabel     statusBar  = new JLabel(" ");
    private final JLabel     roomHeader = new JLabel("# general");

    private final DefaultListModel<String> memberModel = new DefaultListModel<>();
    private final JList<String>            memberList  = new JList<>(memberModel);

    // ================================================================
    // CONSTRUCTOR
    // ================================================================
    public ChatClientUI() {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception e) { e.printStackTrace(); }

        ledTimer = new Timer(50, e -> {
            hue += 0.01f;
            if (hue > 1.0f) hue = 0.0f;
            if (isBrightening) {
                ledAlpha += 0.05f;
                if (ledAlpha >= 1.0f) { ledAlpha = 1.0f; isBrightening = false; }
            } else {
                ledAlpha -= 0.05f;
                if (ledAlpha <= 0.3f) { ledAlpha = 0.3f; isBrightening = true; }
            }
            loginFrame.repaint();
        });
        ledTimer.start();
        buildLoginUI();
    }

    // ================================================================
    // GEMINI AI
    // ================================================================
    private void initGeminiAI() {
        try {
            String apiKey = "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";

            geminiModel = null; // không dùng nữa

            OpenAiChatModel groqModel = OpenAiChatModel.builder()
                    .baseUrl("https://api.groq.com/openai/v1")
                    .apiKey(apiKey)
                    .modelName("llama-3.3-70b-versatile")
                    .temperature(0.7)
                    .build();

            aiAssistant = AiServices.builder(TeachingAssistant.class)
                    .chatLanguageModel(groqModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(12))
                    .build();

            System.out.println("✅ Groq AI đã kết nối thành công!");
        } catch (Exception e) {
            System.err.println("❌ Lỗi: " + e.getMessage());
        }
    }

    private void askGemini() {
        if (aiAssistant == null) {
            JOptionPane.showMessageDialog(chatFrame, "AI chưa sẵn sàng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Tạo Dialog ──
        JDialog dialog = new JDialog(chatFrame, "🤖 Định Không Đi Lố ", false);
        dialog.setSize(600, 700);
        dialog.setLocationRelativeTo(chatFrame);
        dialog.getContentPane().setBackground(BG_DARKEST);
        dialog.setLayout(new BorderLayout());

        // ── Header ──
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 24, 38));
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerLeft.setOpaque(false);

        JLabel robotIcon = new JLabel("🤖");
        robotIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));

        JPanel titleCol = new JPanel();
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleCol.setOpaque(false);
        JLabel titleLbl = new JLabel("Đẳng Cấp AI Người Tày");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLbl.setForeground(NEON_GREEN);
        JLabel subLbl = new JLabel("TàyMini 3.3 · Powered by Bã Mía ");
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        subLbl.setForeground(TEXT_MUTED);
        titleCol.add(titleLbl);
        titleCol.add(subLbl);

        headerLeft.add(robotIcon);
        headerLeft.add(titleCol);

        JButton closeBtn = new JButton("✕");
        closeBtn.setForeground(TEXT_MUTED);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dialog.dispose());
        closeBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { closeBtn.setForeground(DANGER); }
            public void mouseExited(MouseEvent e)  { closeBtn.setForeground(TEXT_MUTED); }
        });

        header.add(headerLeft, BorderLayout.WEST);
        header.add(closeBtn,   BorderLayout.EAST);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 40, 70)),
            new EmptyBorder(14, 20, 14, 20)));

        // ── Chat Area ──
        JTextPane aiPane = new JTextPane();
        aiPane.setBackground(BG_DARK);
        aiPane.setForeground(TEXT_PRIMARY);
        aiPane.setEditable(false);
        aiPane.setFont(new Font("SansSerif", Font.PLAIN, 14));
        aiPane.setBorder(new EmptyBorder(16, 16, 16, 16));

        JScrollPane aiScroll = new JScrollPane(aiPane);
        aiScroll.setBorder(null);
        aiScroll.getViewport().setBackground(BG_DARK);

        // Welcome message
        appendToPane(aiPane, "🤖 Tày KIKI ", NEON_GREEN, true, 13);
        appendToPane(aiPane, "Xin chào! Tôi có thể giúp bạn về:\n", TEXT_MUTED, false, 13);
        appendToPane(aiPane, "  📚 Bài giảng & học thuật\n", TEXT_PRIMARY, false, 13);
        appendToPane(aiPane, "  💻 Lập trình & debug code\n", TEXT_PRIMARY, false, 13);
        appendToPane(aiPane, "  🧮 Toán học & tính toán\n", TEXT_PRIMARY, false, 13);
        appendToPane(aiPane, "  💬 Câu hỏi bất kỳ\n\n", TEXT_PRIMARY, false, 13);

        // ── Suggested Questions ──
        JPanel suggestPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        suggestPanel.setBackground(BG_DARK);
        suggestPanel.setBorder(new EmptyBorder(0, 10, 6, 10));

        String[] suggests = {"💻 Giải thích OOP", "🧮 Công thức toán", "🐛 Debug code", "📖 Tóm tắt bài"};
        for (String s : suggests) {
            JButton btn = new JButton(s);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            btn.setForeground(NEON_CYAN);
            btn.setBackground(new Color(0, 212, 255, 20));
            btn.setBorder(new RoundedBorder(NEON_CYAN.darker().darker(), 12));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setFocusPainted(false);
            suggestPanel.add(btn);
            // sẽ gán action sau khi có inputField
            btn.putClientProperty("suggest", s);
        }

        // ── Input Area ──
        JPanel inputArea = new JPanel(new BorderLayout(8, 0));
        inputArea.setBackground(new Color(20, 24, 38));
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 40, 70)),
            new EmptyBorder(12, 16, 12, 16)));

        JTextField aiInput = new JTextField();
        aiInput.setBackground(BG_INPUT);
        aiInput.setForeground(TEXT_PRIMARY);
        aiInput.setCaretColor(NEON_GREEN);
        aiInput.setFont(new Font("SansSerif", Font.PLAIN, 14));
        aiInput.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(new Color(40, 55, 90), 10),
            new EmptyBorder(10, 14, 10, 14)));
        aiInput.putClientProperty("JTextField.placeholderText", "Nhập câu hỏi của bạn...");

        JButton sendAiBtn = new JButton("➤") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? NEON_GREEN.darker() : new Color(0, 255, 136, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(NEON_GREEN);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        sendAiBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        sendAiBtn.setForeground(NEON_GREEN);
        sendAiBtn.setContentAreaFilled(false);
        sendAiBtn.setBorderPainted(false);
        sendAiBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendAiBtn.setPreferredSize(new Dimension(46, 46));

        inputArea.add(aiInput,   BorderLayout.CENTER);
        inputArea.add(sendAiBtn, BorderLayout.EAST);

        // ── Gán suggest buttons ──
        for (Component c : suggestPanel.getComponents()) {
            if (c instanceof JButton) {
                JButton sb = (JButton) c; // ép kiểu thủ công
                String hint = (String) sb.getClientProperty("suggest");
                sb.addActionListener(e -> aiInput.setText(hint + ": "));
                aiInput.requestFocusInWindow();
            }
        }

        // ── Logic gửi câu hỏi ──
        JLabel typingLbl = new JLabel(" ");
        typingLbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
        typingLbl.setForeground(NEON_GREEN);
        typingLbl.setBorder(new EmptyBorder(0, 18, 4, 0));
        typingLbl.setBackground(BG_DARK);
        typingLbl.setOpaque(true);

        Runnable doAsk = () -> {
            String question = aiInput.getText().trim();
            if (question.isEmpty()) return;
            aiInput.setText("");
            aiInput.setEnabled(false);
            sendAiBtn.setEnabled(false);

            appendToPane(aiPane, "\n👤  Bạn\n", NEON_CYAN, true, 12);
            appendToPane(aiPane, "  " + question + "\n\n", TEXT_PRIMARY, false, 14);
            typingLbl.setText("🤖 Đang suy nghĩ...");

            new Thread(() -> {
                try {
                    String response = aiAssistant.chat(question);
                    SwingUtilities.invokeLater(() -> {
                        appendToPane(aiPane, "🤖  Groq AI\n", NEON_GREEN, true, 12);
                        appendToPane(aiPane, "  " + response.replace("\n", "\n  ") + "\n\n", TEXT_PRIMARY, false, 14);
                        typingLbl.setText(" ");
                        aiInput.setEnabled(true);
                        sendAiBtn.setEnabled(true);
                        aiInput.requestFocusInWindow();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        appendToPane(aiPane, "🤖  Groq AI\n", DANGER, true, 12);
                        appendToPane(aiPane, "  ❌ Lỗi: " + ex.getMessage() + "\n\n", DANGER, false, 13);
                        typingLbl.setText(" ");
                        aiInput.setEnabled(true);
                        sendAiBtn.setEnabled(true);
                    });
                }
            }).start();
        };

        sendAiBtn.addActionListener(e -> doAsk.run());
        aiInput.addActionListener(e -> doAsk.run());

        // ── Bottom panel ──
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(new Color(20, 24, 38));
        bottom.add(suggestPanel, BorderLayout.NORTH);
        bottom.add(typingLbl,    BorderLayout.CENTER);
        bottom.add(inputArea,    BorderLayout.SOUTH);

        dialog.add(header,   BorderLayout.NORTH);
        dialog.add(aiScroll, BorderLayout.CENTER);
        dialog.add(bottom,   BorderLayout.SOUTH);

        dialog.setVisible(true);
        aiInput.requestFocusInWindow();
    }

    // ── Helper append cho AI pane ──
    private void appendToPane(JTextPane pane, String text, Color color, boolean bold, int size) {
        try {
            StyledDocument doc = pane.getStyledDocument();
            Style s = pane.addStyle("s" + Math.random(), null);
            StyleConstants.setForeground(s, color);
            StyleConstants.setBold(s, bold);
            StyleConstants.setFontSize(s, size);
            doc.insertString(doc.getLength(), text, s);
            pane.setCaretPosition(doc.getLength());
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ================================================================
    // UI HELPERS
    // ================================================================
    private JButton neonButton(String text, Color neon) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed())       g2.setColor(neon.darker());
                else if (getModel().isRollover()) g2.setColor(neon.darker().darker());
                else                              g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(neon);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(neon);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 40));
        return btn;
    }

    private JButton iconBtn(String icon, String tip) {
        JButton b = new JButton(icon);
        b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(36, 36));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(NEON_GREEN); }
            public void mouseExited(MouseEvent e)  { b.setForeground(null); }
        });
        return b;
    }

    private JButton avatarButton(String name, int size, Color color) {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Ellipse2D.Double circle = new Ellipse2D.Double(0, 0, size, size);
                g2.setClip(circle);
                if (getIcon() != null) {
                    getIcon().paintIcon(this, g2, 0, 0);
                } else {
                    g2.setColor(color);
                    g2.fill(circle);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, size / 2));
                    String letter = name.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(name.charAt(0)));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(letter,
                        (size - fm.stringWidth(letter)) / 2,
                        (size + fm.getAscent() - fm.getDescent()) / 2);
                }
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // ================================================================
    // LOGIN UI
    // ================================================================
    void buildLoginUI() {
        loginFrame.getContentPane().removeAll();
        loginFrame.setSize(440, 580);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.getContentPane().setBackground(BG_DARKEST);
        loginFrame.setLayout(new GridBagLayout());

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                Color rainbowColor = Color.getHSBColor(hue, 0.8f, 1.0f);
                Color finalColor = new Color(rainbowColor.getRed(), rainbowColor.getGreen(),
                    rainbowColor.getBlue(), (int)(ledAlpha * 255));
                g2.setPaint(finalColor);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 20, 20);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(40, 40, 40, 40));
        card.setPreferredSize(new Dimension(360, 500));

        JLabel logo  = new JLabel("◈");
        logo.setFont(new Font("SansSerif", Font.BOLD, 40));
        logo.setForeground(NEON_GREEN);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Chào mừng trở lại!");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Đăng nhập để tiếp tục");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        userField.setBackground(BG_INPUT); userField.setForeground(TEXT_PRIMARY); userField.setCaretColor(NEON_GREEN);
        userField.setBorder(new RoundedBorder(NEON_GREEN.darker().darker(), 8));
        passField.setBackground(BG_INPUT); passField.setForeground(TEXT_PRIMARY); passField.setCaretColor(NEON_GREEN);
        passField.setBorder(new RoundedBorder(NEON_GREEN.darker().darker(), 8));
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        userField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblUser = new JLabel("TÊN ĐĂNG NHẬP");
        lblUser.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblUser.setForeground(TEXT_MUTED);
        lblUser.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblPass = new JLabel("MẬT KHẨU");
        lblPass.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblPass.setForeground(TEXT_MUTED);
        lblPass.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton loginBtn = neonButton("Đăng nhập", NEON_GREEN);
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton regBtn = new JButton("Chưa có tài khoản? Đăng ký ngay");
        regBtn.setForeground(NEON_GREEN); regBtn.setContentAreaFilled(false); regBtn.setBorderPainted(false);
        regBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        regBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        regBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(logo);        card.add(Box.createVerticalStrut(10));
        card.add(title);       card.add(Box.createVerticalStrut(4));
        card.add(sub);         card.add(Box.createVerticalStrut(30));
        card.add(lblUser);     card.add(Box.createVerticalStrut(6));
        card.add(userField);   card.add(Box.createVerticalStrut(14));
        card.add(lblPass);     card.add(Box.createVerticalStrut(6));
        card.add(passField);   card.add(Box.createVerticalStrut(24));
        card.add(loginBtn);    card.add(Box.createVerticalStrut(10));
        card.add(loginStatus); card.add(Box.createVerticalStrut(6));
        card.add(regBtn);

        loginFrame.add(card);
        loginFrame.setVisible(true);

        loginBtn.addActionListener(e -> doAuth("LOGIN"));
        passField.addActionListener(e -> doAuth("LOGIN"));
        regBtn.addActionListener(e -> doAuth("REGISTER"));
    }

    // ================================================================
    // AUTH
    // ================================================================
    void doAuth(String action) {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty() || pass.isEmpty()) { showError("Vui lòng nhập đủ thông tin!"); return; }

        new Thread(() -> {
            try {
                socket  = new Socket("127.0.0.1", 9999);
                dataIn  = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream()));
                dataOut = new DataOutputStream(new java.io.BufferedOutputStream(socket.getOutputStream()));
                out     = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String header = dataIn.readUTF();
                if (!header.contains("AUTH_START")) { showError("Server lỗi giao thức!"); return; }

                dataOut.writeUTF("TEXT:" + action + ":" + user + ":" + pass);
                dataOut.flush();

                String resp = dataIn.readUTF();
                if (resp.startsWith("TEXT:")) resp = resp.substring(5).trim();

                if (resp.startsWith("AUTH_OK:")) {
                    myName = user;
                    ScheduleSystem.GiaoVien gv = ScheduleSystem.findGiaoVien(user);
                    String role;
                    if (gv != null) {
                        role   = "LECTURER";
                        myRole = "TEACHER";
                    } else {
                        role   = "STUDENT";
                        myRole = "STUDENT";
                        ScheduleSystem.initSinhVienClasses(user);
                    }

                    dataIn.readUTF(); // CHOOSE_ROOM signal

                    String inputRoom = JOptionPane.showInputDialog(loginFrame,
                        "Nhập tên phòng muốn tham gia:", "Chọn phòng", JOptionPane.QUESTION_MESSAGE);
                    currentRoom = (inputRoom == null || inputRoom.trim().isEmpty()) ? "general" : inputRoom.trim();

                    dataOut.writeUTF("TEXT:" + currentRoom);
                    dataOut.flush();

                    dataIn.readUTF(); // APPROVED

                    final String finalRole = role;
                    SwingUtilities.invokeLater(() -> {
                        loginFrame.setVisible(false);
                        buildChatUI(finalRole);
                    });

                } else if (resp.startsWith("AUTH_ERR:")) {
                    showError(resp.substring(9));
                } else {
                    showError("Sai tài khoản hoặc mật khẩu!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showError("Không thể kết nối Server! (" + e.getMessage() + ")");
            }
        }).start();
    }

    void showError(String msg) { SwingUtilities.invokeLater(() -> loginStatus.setText(msg)); }

    // ================================================================
    // CHAT UI  (định nghĩa DUY NHẤT)
    // ================================================================
    void buildChatUI(String role) {
        chatFrame.setSize(1150, 750);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setLocationRelativeTo(null);
        chatFrame.getContentPane().setBackground(BG_DARKEST);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARKEST);

        JPanel sidebar  = buildSidebar();
        JPanel center   = new JPanel(new BorderLayout(0, 0));
        center.setBackground(BG_DARK);

        JPanel header = buildHeader();

        chatPane.setBackground(BG_DARK);
        chatPane.setForeground(TEXT_PRIMARY);
        chatPane.setEditable(false);
        chatPane.setFont(new Font("SansSerif", Font.PLAIN, 14));
        chatPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);

        JPanel inputBar     = buildInputBar();
        JPanel rightSidebar = buildMemberSidebar();

        center.add(header,   BorderLayout.NORTH);
        center.add(scroll,   BorderLayout.CENTER);
        center.add(inputBar, BorderLayout.SOUTH);

        root.add(sidebar,       BorderLayout.WEST);
        root.add(center,        BorderLayout.CENTER);
        root.add(rightSidebar,  BorderLayout.EAST);

        // Thanh công cụ giảng viên
        if ("LECTURER".equals(role)) {
            JPanel lecturerBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
            lecturerBar.setBackground(BG_SIDEBAR);
            lecturerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, NEON_PURPLE));

            JButton btnDiemDanh = neonButton("📊 Điểm danh", NEON_PURPLE);
            btnDiemDanh.setPreferredSize(new Dimension(140, 30));
            btnDiemDanh.addActionListener(e ->
                appendChat("\n[HỆ THỐNG] Thầy/Cô đang thực hiện điểm danh lớp...\n", NEON_PURPLE, true));

            JButton btnKhoaChat = neonButton("🔒 Khóa Chat", DANGER);
            btnKhoaChat.setPreferredSize(new Dimension(140, 30));

            JLabel gvLbl = new JLabel("🛠 GIẢNG VIÊN: ");
            gvLbl.setForeground(TEXT_MUTED);
            gvLbl.setFont(new Font("SansSerif", Font.BOLD, 10));

            lecturerBar.add(gvLbl);
            lecturerBar.add(btnDiemDanh);
            lecturerBar.add(btnKhoaChat);
            center.add(lecturerBar, BorderLayout.NORTH);
        }

        chatFrame.add(root);
        chatFrame.setVisible(true);

        setupNetworkingLogic("");
        initGeminiAI();
    }

    // ================================================================
    // SIDEBAR (trái)
    // ================================================================
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));

        JPanel serverName = new JPanel(new BorderLayout());
        serverName.setBackground(BG_SIDEBAR);
        serverName.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 40, 70)),
            new EmptyBorder(14, 16, 14, 16)));
        JLabel serverLbl = new JLabel("◈  Chat Server");
        serverLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        serverLbl.setForeground(NEON_GREEN);
        serverName.add(serverLbl, BorderLayout.WEST);

        JPanel roomInfo = new JPanel();
        roomInfo.setLayout(new BoxLayout(roomInfo, BoxLayout.Y_AXIS));
        roomInfo.setBackground(BG_SIDEBAR);
        roomInfo.setBorder(new EmptyBorder(12, 10, 10, 10));

        JLabel sectionLbl = new JLabel("  PHÒNG CHAT");
        sectionLbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        sectionLbl.setForeground(TEXT_MUTED);
        roomInfo.add(sectionLbl);
        roomInfo.add(Box.createVerticalStrut(6));

        JPanel roomItem = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        roomItem.setBackground(new Color(NEON_GREEN.getRed(), NEON_GREEN.getGreen(), NEON_GREEN.getBlue(), 20));
        roomItem.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        roomItem.setBorder(new RoundedBorder(NEON_GREEN.darker().darker(), 6));
        JLabel roomIcon = new JLabel("#");
        roomIcon.setFont(new Font("SansSerif", Font.BOLD, 14));
        roomIcon.setForeground(NEON_GREEN);
        roomHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        roomHeader.setForeground(TEXT_PRIMARY);
        roomHeader.setText(currentRoom);
        roomItem.add(roomIcon);
        roomItem.add(roomHeader);
        roomInfo.add(roomItem);

        sidebar.add(serverName, BorderLayout.NORTH);
        sidebar.add(roomInfo,   BorderLayout.CENTER);

        JPanel userInfo = new JPanel(new BorderLayout(10, 0));
        userInfo.setBackground(new Color(10, 13, 22));
        userInfo.setBorder(new EmptyBorder(10, 12, 10, 12));

        JButton ava = avatarButton(myName, 36, NEON_PURPLE);
        if (savedAvatarIcon != null) {
            Image scaled = savedAvatarIcon.getImage().getScaledInstance(36, 36, Image.SCALE_SMOOTH);
            ava.setIcon(new ImageIcon(scaled));
        }
        ava.addActionListener(e -> {
            showStudentProfile();
            if (savedAvatarIcon != null) {
                Image scaled = savedAvatarIcon.getImage().getScaledInstance(36, 36, Image.SCALE_SMOOTH);
                ava.setIcon(new ImageIcon(scaled));
                ava.repaint();
            }
        });

        JPanel nameCol = new JPanel();
        nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));
        nameCol.setOpaque(false);
        JLabel nameLbl = new JLabel(myName);
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        nameLbl.setForeground(TEXT_PRIMARY);
        JLabel statusLbl2 = new JLabel("● Trực tuyến");
        statusLbl2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLbl2.setForeground(NEON_GREEN);
        nameCol.add(nameLbl);
        nameCol.add(statusLbl2);

        JButton logoutBtn = new JButton("⏏");
        logoutBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        logoutBtn.setForeground(DANGER);
        logoutBtn.setContentAreaFilled(false); logoutBtn.setBorderPainted(false);
        logoutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoutBtn.setToolTipText("Đăng xuất");
        logoutBtn.addActionListener(e -> {
            try { if (socket != null) socket.close(); } catch (IOException ex) {}
            chatFrame.dispose();
            loginFrame.setVisible(true);
            loginStatus.setText("Đã đăng xuất.");
        });

        userInfo.add(ava,       BorderLayout.WEST);
        userInfo.add(nameCol,   BorderLayout.CENTER);
        userInfo.add(logoutBtn, BorderLayout.EAST);
        sidebar.add(userInfo,   BorderLayout.SOUTH);
        return sidebar;
    }

    // ================================================================
    // HEADER
    // ================================================================
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 40, 70)),
            new EmptyBorder(12, 20, 12, 20)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel hashLbl = new JLabel("#");
        hashLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        hashLbl.setForeground(NEON_GREEN);
        JLabel rLbl = new JLabel(currentRoom);
        rLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        rLbl.setForeground(TEXT_PRIMARY);
        left.add(hashLbl); left.add(rLbl);

        statusBar.setText("  " + myName + "  ●  Trực tuyến");
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusBar.setForeground(NEON_GREEN);

        header.add(left,      BorderLayout.WEST);
        header.add(statusBar, BorderLayout.EAST);
        return header;
    }

    // ================================================================
    // INPUT BAR
    // ================================================================
    private JPanel buildInputBar() {
        JPanel inputBar = new JPanel(new BorderLayout(0, 8));
        inputBar.setBackground(BG_DARK);
        inputBar.setBorder(new EmptyBorder(10, 16, 16, 16));

        JPanel inner = new JPanel(new BorderLayout(8, 0));
        inner.setBackground(BG_INPUT);
        inner.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(new Color(40, 55, 90), 12),
            new EmptyBorder(4, 8, 4, 8)));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 4));
        tools.setOpaque(false);

        JButton btnFile     = iconBtn("📁",  "Gửi file");
        JButton btnEmoji    = iconBtn("😀",  "Emoji");
        JButton btnImage    = iconBtn("🖼️", "Gửi ảnh");
        JButton btnVoice    = iconBtn("🎤",  "Voice message");
        JButton btnCam      = iconBtn("📹",  "Video call");
        JButton btnSchedule = iconBtn("📅",  "Lịch học");
        JButton btnAttend   = iconBtn("✅",  "Điểm danh");
        JButton btnGif      = iconBtn("🎞",  "Gửi GIF");
        JButton btnGemini   = iconBtn("🤖",  "Hỏi Gemini AI");
        JButton btnFaceReg   = iconBtn("🧑", "Đăng ký khuôn mặt");
        JButton btnFaceAttend = iconBtn("🤖", "Điểm danh khuôn mặt");
        btnFaceReg.addActionListener(e -> showFaceRegister());
        btnFaceAttend.addActionListener(e -> showFaceAttend());
        tools.add(btnFaceReg);
        tools.add(btnFaceAttend);

        // File
        btnFile.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(chatFrame) == JFileChooser.APPROVE_OPTION) {
                File sel = fc.getSelectedFile();
                long fileSizeInMb = sel.length() / (1024 * 1024);
                if (fileSizeInMb > 40) {
                    JOptionPane.showMessageDialog(chatFrame,
                        "⚠️ File quá lớn (" + fileSizeInMb + "MB)!\nVui lòng gửi file dưới 40MB.",
                        "Giới hạn dung lượng", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                new Thread(() -> {
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(sel.toPath());
                        dataOut.writeUTF("FILE_MSG:" + myName + ":" + sel.getName());
                        dataOut.writeInt(bytes.length);
                        dataOut.write(bytes);
                        dataOut.flush();
                        appendChat("[Tôi] 📁 Đã gửi: " + sel.getName() + "\n", TEXT_MUTED, false);
                    } catch (IOException ex) { ex.printStackTrace(); }
                }).start();
            }
        });

        // Emoji
        EmojiPicker picker = new EmojiPicker(chatFrame, inputField);
        btnEmoji.addActionListener(e -> picker.toggle());

        // Image
        btnImage.addActionListener(e -> {
            byte[] imgBytes = ImageHandler.pickImageBytes(chatFrame);
            if (imgBytes != null) {
                new Thread(() -> {
                    try {
                        dataOut.writeUTF("IMAGE:" + myName);
                        dataOut.writeInt(imgBytes.length);
                        dataOut.write(imgBytes);
                        dataOut.flush();
                        appendChat("[Tôi] 🖼️ Đã gửi ảnh\n", NEON_CYAN, false);
                    } catch (IOException ex) { ex.printStackTrace(); }
                }).start();
            }
        });

        // Voice
        final boolean[] recording = {false};
        btnVoice.addActionListener(e -> {
            if (!recording[0]) {
                voiceHelper.startRecording();
                recording[0] = true;
                btnVoice.setText("⏹️");
                statusBar.setText("  🎤 Đang ghi âm...");
                statusBar.setForeground(DANGER);
            } else {
                byte[] audio = voiceHelper.stopRecording();
                recording[0] = false;
                btnVoice.setText("🎤");
                statusBar.setText("  " + myName + "  ●  Trực tuyến");
                statusBar.setForeground(NEON_GREEN);
                if (audio != null && audio.length > 100) {
                    try {
                        dataOut.writeUTF("VOICE:" + myName);
                        dataOut.writeInt(audio.length);
                        dataOut.write(audio);
                        dataOut.flush();
                        appendChat("[Tôi] 🎤 Đã gửi voice\n", NEON_GREEN, false);
                    } catch (IOException ex) {}
                }
            }
        });

        // Camera
        btnCam.addActionListener(e -> openMeeting.start(chatFrame, currentRoom, out, myName));

        // Schedule
        btnSchedule.addActionListener(e -> showMySchedule());

        // Điểm danh
        btnAttend.addActionListener(e -> showAttendDialog());

        // GIF
        btnGif.addActionListener(e -> showGifPicker());

        // Gemini AI
        btnGemini.addActionListener(e -> askGemini());

        tools.add(btnFile);
        tools.add(btnEmoji);
        tools.add(btnImage);
        tools.add(btnVoice);
        tools.add(btnCam);
        tools.add(btnSchedule);
        tools.add(btnAttend);
        tools.add(btnGif);
        tools.add(btnGemini);
        
     // ── Nút ML Analysis ──
        JButton btnML = iconBtn("📊", "Phan tich ML Sinh vien");
        btnML.addActionListener(e -> showMLAnalysis());
        tools.add(btnML);

        inputField.setBackground(BG_INPUT);
        inputField.setForeground(TEXT_PRIMARY);
        inputField.setCaretColor(NEON_GREEN);
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputField.setBorder(new EmptyBorder(8, 4, 8, 4));
        inputField.putClientProperty("JTextField.placeholderText", "Gửi tin nhắn tới #" + currentRoom);

        JButton sendBtn = new JButton("➤");
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        sendBtn.setForeground(NEON_GREEN);
        sendBtn.setContentAreaFilled(false); sendBtn.setBorderPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        inner.add(tools,      BorderLayout.WEST);
        inner.add(inputField, BorderLayout.CENTER);
        inner.add(sendBtn,    BorderLayout.EAST);
        inputBar.add(inner,   BorderLayout.CENTER);
        return inputBar;
    }

    // ================================================================
    // MEMBER SIDEBAR (phải)
    // ================================================================
    private JPanel buildMemberSidebar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_SIDEBAR);
        panel.setPreferredSize(new Dimension(210, 0));
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(30, 40, 70)));

        JLabel title = new JLabel(" THÀNH VIÊN LỚP");
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        title.setForeground(TEXT_MUTED);
        title.setBorder(new EmptyBorder(14, 12, 8, 12));

        memberList.setBackground(BG_SIDEBAR);
        memberList.setForeground(TEXT_PRIMARY);
        memberList.setFixedCellHeight(52);
        memberList.setCellRenderer(new OnlineMemberCellRenderer());

        JScrollPane scroll = new JScrollPane(memberList);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_SIDEBAR);

        panel.add(title,  BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }
    
    class OnlineMemberCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            String name = value.toString();
            boolean isGV = name.toUpperCase().startsWith("GV_") || name.toUpperCase().startsWith("GV");

            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(true);
            row.setBackground(isSelected ? new Color(30, 40, 70) : BG_SIDEBAR);
            row.setBorder(new EmptyBorder(6, 10, 6, 10));

            // Avatar circle
            Color avatarColor = isGV ? NEON_PURPLE
                : new Color((name.hashCode() & 0x7F7F7F) | 0x404040);
            JLabel av = new JLabel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(avatarColor);
                    g2.fillOval(0, 0, 36, 36);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 15));
                    String displayName = name.replaceAll("(?i)GV_", "");
                    String lt = displayName.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(displayName.charAt(0)));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(lt, (36 - fm.stringWidth(lt)) / 2, (36 + fm.getAscent() - fm.getDescent()) / 2);
                    // Online dot
                    g2.setColor(NEON_GREEN);
                    g2.fillOval(24, 24, 11, 11);
                    g2.setColor(BG_SIDEBAR);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(24, 24, 11, 11);
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(36, 36); }
            };

            // Info panel
            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setOpaque(false);

            String displayName = isGV ? name.replaceAll("(?i)GV_", "") : name;
            JLabel nameLbl = new JLabel(isGV ? "👨‍🏫 " + displayName : displayName);
            nameLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLbl.setForeground(isGV ? NEON_PURPLE : TEXT_PRIMARY);

            JLabel statusLbl = new JLabel(isGV ? "● Giảng viên" : "● online");
            statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
            statusLbl.setForeground(isGV ? NEON_PURPLE : NEON_GREEN);

            info.add(nameLbl);
            info.add(statusLbl);

            row.add(av,   BorderLayout.WEST);
            row.add(info, BorderLayout.CENTER);
            return row;
        }
    }

    // ================================================================
    // SEND MESSAGE  (hỗ trợ /gif [url])
    // ================================================================
    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;
        try {
            if (msg.toLowerCase().startsWith("/gif ")) {
                String url = msg.substring(5).trim();
                if (!url.isEmpty()) {
                    sendGifUrl(url);
                    inputField.setText("");
                    return;
                }
            }
            dataOut.writeUTF("TEXT:" + msg);
            dataOut.flush();
            inputField.setText("");
        } catch (IOException ex) {}
    }

    // ================================================================
    // NETWORKING
    // ================================================================
    void setupNetworkingLogic(String welcome) {
        if (!welcome.isEmpty()) appendChat("🔗 " + welcome + "\n", NEON_GREEN, false);

        new Thread(() -> {
            try {
                while (socket != null && !socket.isClosed()) {
                    String header = dataIn.readUTF();

                    if (header.startsWith("FILE_MSG:")) {
                        String[] parts = header.split(":");
                        String sender   = parts.length > 1 ? parts[1] : "?";
                        String fileName = parts.length > 2 ? parts[2] : "file";
                        int size = dataIn.readInt();
                        byte[] data = new byte[size];
                        dataIn.readFully(data);
                        SwingUtilities.invokeLater(() -> showDownloadButton(sender, fileName, data));
                    }
                    else if (header.startsWith("IMAGE:")) {
                        String sender = header.substring(6);
                        int size = dataIn.readInt();
                        byte[] img = new byte[size];
                        dataIn.readFully(img);
                        SwingUtilities.invokeLater(() -> {
                            appendChat(sender + ":  \n", NEON_CYAN, true);
                            Image scaled = new ImageIcon(img).getImage().getScaledInstance(260, -1, Image.SCALE_SMOOTH);
                            chatPane.setCaretPosition(chatPane.getDocument().getLength());
                            chatPane.insertIcon(new ImageIcon(scaled));
                            appendChat("\n", TEXT_PRIMARY, false);
                        });
                    }
                    else if (header.startsWith("VOICE:")) {
                        String sender = header.substring(6);
                        int size = dataIn.readInt();
                        byte[] audio = new byte[size];
                        dataIn.readFully(audio);
                        SwingUtilities.invokeLater(() -> showVoiceButton(sender, audio));
                    }
                    else if (header.startsWith("TEXT:")) {
                        String line = header.substring(5);
                        SwingUtilities.invokeLater(() -> handleMsg(line));
                    }
                    else if (header.startsWith("MSG:")) {
                        SwingUtilities.invokeLater(() -> handleMsg(header));
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    appendChat("⚠ Mất kết nối server!\n", DANGER, false));
            }
        }).start();
    }

    // ================================================================
    // HANDLE INCOMING MESSAGE
    // ================================================================
    void handleMsg(String line) {
        // GIF
        if (line.startsWith("GIF:")) {
            String[] parts = line.split(":", 3);
            String sender = parts.length > 1 ? parts[1] : "?";
            String url    = parts.length > 2 ? parts[2] : "";
            if (!url.isEmpty()) {
                appendChat("  " + sender + " 🎞 GIF:\n", NEON_CYAN, true);
                loadAndShowGif(url);
            }
            return;
        }

        // Base64 image (legacy)
        if (line.startsWith("IMG:")) {
            try {
                int idx = line.indexOf(": ");
                String info  = line.substring(0, idx);
                byte[] bytes = Base64.getDecoder().decode(line.substring(idx + 2));
                appendChat(info + ": ", NEON_CYAN, true);
                Image img = new ImageIcon(bytes).getImage().getScaledInstance(260, -1, Image.SCALE_SMOOTH);
                chatPane.setCaretPosition(chatPane.getDocument().getLength());
                chatPane.insertIcon(new ImageIcon(img));
                appendChat("\n", TEXT_PRIMARY, false);
            } catch (Exception e) {}
            return;
        }

        // Chat message
        if (line.startsWith("MSG:")) {
            String content = line.substring(4);
            int bracketEnd = content.indexOf("] ");
            if (bracketEnd >= 0) {
                String rest  = content.substring(bracketEnd + 2);
                int colon    = rest.indexOf(": ");
                if (colon >= 0) {
                    String sender = rest.substring(0, colon);
                    String msg    = rest.substring(colon + 2);
                    boolean isMe  = sender.equalsIgnoreCase(myName);
                    if (!memberModel.contains(sender)) memberModel.addElement(sender);
                    appendBubble(sender, msg, isMe);
                    return;
                }
            }
            appendChat(content + "\n", TEXT_PRIMARY, false);
        } else {
            appendChat(line + "\n", TEXT_MUTED, false);
        }
    }

    // ================================================================
    // APPEND HELPERS
    // ================================================================
    void appendBubble(String sender, String msg, boolean isMe) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            Style nameStyle = chatPane.addStyle("n" + Math.random(), null);
            StyleConstants.setBold(nameStyle, true);
            StyleConstants.setFontSize(nameStyle, 12);
            if (isMe) {
                StyleConstants.setForeground(nameStyle, NEON_GREEN);
                doc.insertString(doc.getLength(), "  Tôi\n", nameStyle);
            } else if (sender.toUpperCase().startsWith("GV")) {
                StyleConstants.setForeground(nameStyle, NEON_PURPLE);
                doc.insertString(doc.getLength(), "  [GV] " + sender + "\n", nameStyle);
            } else {
                StyleConstants.setForeground(nameStyle, NEON_CYAN);
                doc.insertString(doc.getLength(), "  " + sender + "\n", nameStyle);
            }
            Style msgStyle = chatPane.addStyle("m" + Math.random(), null);
            StyleConstants.setForeground(msgStyle, TEXT_PRIMARY);
            StyleConstants.setFontSize(msgStyle, 14);
            doc.insertString(doc.getLength(), "  " + msg + "\n\n", msgStyle);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) { e.printStackTrace(); }
    }

    void appendChat(String msg, Color c, boolean bold) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            Style s = chatPane.addStyle("s", null);
            StyleConstants.setForeground(s, c);
            StyleConstants.setBold(s, bold);
            doc.insertString(doc.getLength(), msg, s);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {}
    }

    // ================================================================
    // DOWNLOAD / VOICE BUTTONS
    // ================================================================
    void showDownloadButton(String sender, String fileName, byte[] data) {
        appendChat("  " + sender + " 📁 " + fileName + "  ", NEON_CYAN, true);
        JButton btn = new JButton("⬇ Tải về");
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(BG_CARD); btn.setForeground(NEON_GREEN);
        btn.setBorder(new RoundedBorder(NEON_GREEN, 6));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            JFileChooser saver = new JFileChooser();
            saver.setSelectedFile(new File(fileName));
            if (saver.showSaveDialog(chatFrame) == JFileChooser.APPROVE_OPTION) {
                try (FileOutputStream fos = new FileOutputStream(saver.getSelectedFile())) {
                    fos.write(data);
                    JOptionPane.showMessageDialog(chatFrame, "✅ Đã lưu!");
                } catch (IOException ex) { ex.printStackTrace(); }
            }
        });
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
        chatPane.insertComponent(btn);
        appendChat("\n", TEXT_PRIMARY, false);
    }

    void showVoiceButton(String sender, byte[] audio) {
        appendChat("  " + sender + " 🎤  ", NEON_GREEN, true);
        JButton btn = new JButton("▶ Nghe");
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(BG_CARD); btn.setForeground(NEON_GREEN);
        btn.setBorder(new RoundedBorder(NEON_GREEN, 6));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(ev -> VoiceMessageHelper.playAudio(audio));
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
        chatPane.insertComponent(btn);
        appendChat("\n", TEXT_PRIMARY, false);
    }

    // ================================================================
    // SCHEDULE
    // ================================================================
    void showMySchedule() {
        if ("TEACHER".equals(myRole)) {
            ScheduleSystem.showTeacherSchedule(chatFrame, myName);
        } else {
            String mssv = ScheduleSystem.extractMSSV(myName);
            ScheduleSystem.showWeeklySchedule(chatFrame, mssv);
        }
    }

    // ================================================================
    // MEMBER CELL RENDERER
    // ================================================================
    class MemberCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            String fullInfo = value.toString();
            String[] parts  = fullInfo.split("\\|");
            String mssv     = parts.length > 0 ? parts[0] : "";
            String name     = parts.length > 1 ? parts[1] : mssv;
            boolean isOnline = fullInfo.toLowerCase().contains("online");

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            row.setOpaque(true);
            row.setBackground(isSelected ? new Color(30, 40, 70) : BG_SIDEBAR);
            row.setBorder(new EmptyBorder(2, 8, 2, 8));

            Color avatarColor = new Color((name.hashCode() & 0x7F7F7F) | 0x404040);
            JButton ava = avatarButton(name, 28, avatarColor);

            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setOpaque(false);

            JLabel nameLbl = new JLabel(name);
            nameLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLbl.setForeground(TEXT_PRIMARY);

            JLabel statusLbl = new JLabel(isOnline ? "● Trực tuyến" : "○ Ngoại tuyến");
            statusLbl.setForeground(isOnline ? NEON_GREEN : TEXT_MUTED);
            statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));

            info.add(nameLbl);
            info.add(statusLbl);
            row.add(ava);
            row.add(info);
            return row;
        }
    }

    // ================================================================
    // ĐIỂM DANH
    // ================================================================
    void showAttendDialog() {
        JDialog dialog = new JDialog(chatFrame, "✅ Điểm danh", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(chatFrame);
        dialog.getContentPane().setBackground(BG_DARK);
        dialog.setLayout(new BorderLayout());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_PANEL);
        card.setBorder(new EmptyBorder(28, 35, 28, 35));

        JLabel iconLbl = new JLabel("✅");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
        iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel("Nhập mã điểm danh");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLbl.setForeground(TEXT_PRIMARY);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLbl = new JLabel("Mã 6 số do giảng viên cung cấp");
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(TEXT_MUTED);
        subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField codeField = new JTextField();
        codeField.setBackground(BG_INPUT);
        codeField.setForeground(NEON_GREEN);
        codeField.setCaretColor(NEON_GREEN);
        codeField.setFont(new Font("Monospaced", Font.BOLD, 34));
        codeField.setHorizontalAlignment(JTextField.CENTER);
        codeField.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(NEON_GREEN.darker(), 10),
            new EmptyBorder(8, 15, 8, 15)));
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        codeField.setAlignmentX(Component.CENTER_ALIGNMENT);
        codeField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                if (!Character.isDigit(e.getKeyChar()) || codeField.getText().length() >= 6)
                    e.consume();
            }
        });

        JLabel statusLbl = new JLabel(" ");
        statusLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLbl.setForeground(NEON_GREEN);
        statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnOK = new JButton("Xác nhận điểm danh") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? NEON_GREEN.darker() : BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(NEON_GREEN); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btnOK.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnOK.setForeground(NEON_GREEN);
        btnOK.setContentAreaFilled(false); btnOK.setBorderPainted(false);
        btnOK.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnOK.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnOK.setAlignmentX(Component.CENTER_ALIGNMENT);

        Runnable doAttend = () -> {
            String code = codeField.getText().trim();
            if (code.length() != 6) {
                statusLbl.setForeground(DANGER);
                statusLbl.setText("⚠ Mã phải đúng 6 số!");
                return;
            }
            try {
                dataOut.writeUTF("TEXT:/attend " + code);
                dataOut.flush();
                statusLbl.setForeground(NEON_GREEN);
                statusLbl.setText("✅ Đã gửi! Chờ xác nhận từ GV...");
                javax.swing.Timer t = new javax.swing.Timer(1500, ev -> dialog.dispose());
                t.setRepeats(false); t.start();
            } catch (IOException ex) {
                statusLbl.setForeground(DANGER);
                statusLbl.setText("❌ Lỗi kết nối, thử lại!");
            }
        };

        btnOK.addActionListener(e -> doAttend.run());
        codeField.addActionListener(e -> doAttend.run());

        card.add(iconLbl);  card.add(Box.createVerticalStrut(6));
        card.add(titleLbl); card.add(Box.createVerticalStrut(3));
        card.add(subLbl);   card.add(Box.createVerticalStrut(18));
        card.add(codeField);card.add(Box.createVerticalStrut(10));
        card.add(statusLbl);card.add(Box.createVerticalStrut(12));
        card.add(btnOK);

        dialog.add(card, BorderLayout.CENTER);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) { codeField.requestFocusInWindow(); }
        });
        dialog.setVisible(true);
    }

    // ================================================================
    // STUDENT PROFILE
    // ================================================================
    void showStudentProfile() {
        JDialog dialog = new JDialog(chatFrame, "HỆ THỐNG SINH VIÊN NTTU", true);
        dialog.setSize(580, 430);
        dialog.setLocationRelativeTo(chatFrame);
        dialog.getContentPane().setBackground(new Color(10, 12, 18));
        dialog.setLayout(null);

        String mssv     = ScheduleSystem.extractMSSV(myName);
        String savedTen = myName;
        String savedLop = "N/A";
        // Lấy thông tin từ DATABASE thay vì students.txt
        try {
            java.util.Map<String, String> allStudents = DBHelper.getAllStudents();
            String info = allStudents.get(mssv.toLowerCase());
            if (info == null) info = allStudents.get(myName.toLowerCase());
            if (info != null) {
                String[] parts = info.split("\\|");
                if (parts.length >= 1) savedTen = parts[0].trim();
                if (parts.length >= 2) savedLop = parts[1].trim();
            }
            java.util.List<String> classes = DBHelper.getUserClasses(mssv);
            if (!classes.isEmpty()) savedLop = classes.get(0);
        } catch (Exception e) {}

        final String[] currentData = { savedTen, mssv, savedLop, myName };

        JPanel card = new JPanel();
        card.setBounds(15, 15, 545, 350);
        card.setBackground(new Color(20, 24, 38));
        card.setBorder(new RoundedBorder(NEON_CYAN, 15));
        card.setLayout(null);

        JButton photo = avatarButton(currentData[0], 130, NEON_PURPLE);
        photo.setBounds(30, 40, 130, 130);
        if (savedAvatarIcon != null) photo.setIcon(savedAvatarIcon);
        card.add(photo);

        JLabel hintLbl = new JLabel("Click to change Avatar", SwingConstants.CENTER);
        hintLbl.setForeground(NEON_CYAN);
        hintLbl.setFont(new Font("SansSerif", Font.ITALIC, 10));
        hintLbl.setBounds(30, 175, 130, 20);
        card.add(hintLbl);

        photo.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Chọn ảnh Avatar");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Ảnh (jpg, png, gif)", "jpg", "jpeg", "png", "gif"));
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    Image scaledImg = new ImageIcon(fc.getSelectedFile().getAbsolutePath())
                        .getImage().getScaledInstance(130, 130, Image.SCALE_SMOOTH);
                    ImageIcon newIcon = new ImageIcon(scaledImg);
                    photo.setIcon(newIcon);
                    savedAvatarIcon = newIcon;
                    photo.revalidate(); photo.repaint();
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        JLabel titleLbl = new JLabel("STUDENT PROFILE");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLbl.setForeground(NEON_GREEN);
        titleLbl.setBounds(185, 25, 250, 30);
        card.add(titleLbl);

        final boolean[] isEditing = {false};
        String[]     labels     = {"FULL NAME:", "STUDENT ID:", "CLASS:", "EMAIL:"};
        JLabel[]     valLabels  = new JLabel[4];
        JTextField[] editFields = new JTextField[4];

        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(TEXT_MUTED);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
            lbl.setBounds(185, 70 + (i * 60), 120, 20);
            card.add(lbl);

            valLabels[i] = new JLabel(currentData[i]);
            valLabels[i].setForeground(Color.WHITE);
            valLabels[i].setFont(new Font("SansSerif", Font.BOLD, 14));
            valLabels[i].setBounds(185, 88 + (i * 60), 310, 25);
            card.add(valLabels[i]);

            editFields[i] = new JTextField(currentData[i]);
            editFields[i].setBackground(new Color(25, 30, 48));
            editFields[i].setForeground(Color.WHITE);
            editFields[i].setCaretColor(NEON_GREEN);
            editFields[i].setFont(new Font("SansSerif", Font.BOLD, 13));
            editFields[i].setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(NEON_GREEN.darker(), 6),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            editFields[i].setBounds(185, 85 + (i * 60), 310, 30);
            editFields[i].setVisible(false);
            editFields[i].setEditable(i == 0 || i == 2);
            if (!(i == 0 || i == 2)) editFields[i].setForeground(TEXT_MUTED);
            card.add(editFields[i]);
        }

        JButton btnEditSave = new JButton("✏ Chỉnh sửa") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? NEON_CYAN.darker() : new Color(28, 34, 52));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(NEON_CYAN); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 8, 8);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btnEditSave.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnEditSave.setForeground(NEON_CYAN);
        btnEditSave.setContentAreaFilled(false); btnEditSave.setBorderPainted(false);
        btnEditSave.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEditSave.setBounds(185, 318, 140, 28);
        card.add(btnEditSave);

        JLabel statusLbl = new JLabel(" ");
        statusLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        statusLbl.setForeground(NEON_GREEN);
        statusLbl.setBounds(335, 318, 200, 28);
        card.add(statusLbl);

        btnEditSave.addActionListener(e -> {
            if (!isEditing[0]) {
                isEditing[0] = true;
                btnEditSave.setText("💾 Lưu thay đổi");
                for (int i = 0; i < 4; i++) {
                    editFields[i].setText(currentData[i]);
                    valLabels[i].setVisible(false);
                    editFields[i].setVisible(true);
                }
                statusLbl.setForeground(NEON_CYAN);
                statusLbl.setText("Đang chỉnh sửa...");
            } else {
                String newTen = editFields[0].getText().trim();
                String newLop = editFields[2].getText().trim();
                if (newTen.isEmpty()) {
                    statusLbl.setForeground(DANGER);
                    statusLbl.setText("⚠ Họ tên không được trống!");
                    return;
                }
                currentData[0] = newTen;
                currentData[2] = newLop;
                for (int i = 0; i < 4; i++) {
                    valLabels[i].setText(currentData[i]);
                    valLabels[i].setVisible(true);
                    editFields[i].setVisible(false);
                }
                isEditing[0] = false;
                btnEditSave.setText("✏ Chỉnh sửa");
                try {
                    dataOut.writeUTF("TEXT:/update_profile " + mssv + "|" + newTen + "|" + newLop);
                    dataOut.flush();
                    statusLbl.setForeground(NEON_GREEN);
                    statusLbl.setText("✅ Đã lưu!");
                } catch (IOException ex) {
                    statusLbl.setForeground(DANGER);
                    statusLbl.setText("❌ Lỗi kết nối!");
                }
            }
        });

        dialog.add(card);
        JButton closeBtn = neonButton("CLOSE", DANGER);
        closeBtn.setBounds(440, 370, 100, 30);
        closeBtn.addActionListener(ev -> dialog.dispose());
        dialog.add(closeBtn);
        dialog.setVisible(true);
    }

    private ImageIcon pickAvatar(int size) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn ảnh thẻ Cyber");
        if (fc.showOpenDialog(chatFrame) == JFileChooser.APPROVE_OPTION) {
            try {
                Image img = new ImageIcon(fc.getSelectedFile().getAbsolutePath())
                    .getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(img);
            } catch (Exception e) { e.printStackTrace(); }
        }
        return null;
    }

    // ================================================================
    // ROUNDED BORDER
    // ================================================================
    static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int   radius;
        RoundedBorder(Color c, int r) { color = c; radius = r; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w-1, h-1, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(radius/2, radius/2, radius/2, radius/2); }
    }

    // ================================================================
    // GIF FEATURE
    // ================================================================

    /** Mở dialog nhập URL GIF với preview trước khi gửi */
    void showGifPicker() {
        JDialog dialog = new JDialog(chatFrame, "🎞 Gửi GIF", true);
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(chatFrame);
        dialog.getContentPane().setBackground(new Color(15, 18, 28));
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(new Color(20, 24, 38));
        top.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JLabel titleLbl = new JLabel("🎞  Gửi GIF vào Chat");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLbl.setForeground(NEON_CYAN);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLbl = new JLabel("Paste link GIF từ GIPHY, Tenor hoặc bất kỳ URL .gif nào");
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        subLbl.setForeground(TEXT_MUTED);
        subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField urlField = new JTextField();
        urlField.setBackground(BG_INPUT);
        urlField.setForeground(TEXT_PRIMARY);
        urlField.setCaretColor(NEON_GREEN);
        urlField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        urlField.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(NEON_CYAN.darker(), 8),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        urlField.setAlignmentX(Component.CENTER_ALIGNMENT);
        urlField.putClientProperty("JTextField.placeholderText",
            "https://media.giphy.com/media/xxx/giphy.gif");

        JLabel previewLbl = new JLabel("Preview sẽ hiển thị sau khi nhấn Xem thử", SwingConstants.CENTER);
        previewLbl.setForeground(TEXT_MUTED);
        previewLbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
        previewLbl.setPreferredSize(new Dimension(460, 120));
        previewLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLbl = new JLabel(" ");
        statusLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        statusLbl.setForeground(NEON_GREEN);
        statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        top.add(titleLbl);
        top.add(Box.createVerticalStrut(6));
        top.add(subLbl);
        top.add(Box.createVerticalStrut(14));
        top.add(urlField);
        top.add(Box.createVerticalStrut(10));
        top.add(previewLbl);
        top.add(Box.createVerticalStrut(6));
        top.add(statusLbl);

        JButton btnPreview = makeDialogBtn("🔍 Xem thử", NEON_CYAN);
        JButton btnSend    = makeDialogBtn("🎞 Gửi GIF", NEON_GREEN);
        JButton btnCancel  = makeDialogBtn("Hủy",        DANGER);

        btnCancel.addActionListener(e -> dialog.dispose());

        btnPreview.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.isEmpty()) { statusLbl.setForeground(DANGER); statusLbl.setText("⚠ Nhập URL trước!"); return; }
            statusLbl.setForeground(NEON_CYAN); statusLbl.setText("⏳ Đang tải preview...");
            previewLbl.setIcon(null); previewLbl.setText("");
            new Thread(() -> {
                try {
                    Image scaled = new ImageIcon(new URL(url)).getImage().getScaledInstance(240, -1, Image.SCALE_DEFAULT);
                    SwingUtilities.invokeLater(() -> {
                        previewLbl.setIcon(new ImageIcon(scaled));
                        previewLbl.setText("");
                        statusLbl.setForeground(NEON_GREEN);
                        statusLbl.setText("✅ Preview OK! Nhấn Gửi GIF.");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setForeground(DANGER);
                        statusLbl.setText("❌ Không tải được! Kiểm tra URL.");
                        previewLbl.setIcon(null);
                        previewLbl.setText("Không tải được ảnh");
                    });
                }
            }).start();
        });

        btnSend.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.isEmpty()) { statusLbl.setForeground(DANGER); statusLbl.setText("⚠ Nhập URL trước!"); return; }
            sendGifUrl(url);
            dialog.dispose();
        });

        urlField.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (!url.isEmpty()) { sendGifUrl(url); dialog.dispose(); }
        });

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 8, 0));
        btnRow.setBackground(new Color(20, 24, 38));
        btnRow.setBorder(BorderFactory.createEmptyBorder(0, 20, 16, 20));
        btnRow.add(btnPreview);
        btnRow.add(btnSend);
        btnRow.add(btnCancel);

        dialog.add(top,    BorderLayout.CENTER);
        dialog.add(btnRow, BorderLayout.SOUTH);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) { urlField.requestFocusInWindow(); }
        });
        dialog.setVisible(true);
    }

    /** Helper tạo nút neon cho dialog (tránh lặp code) */
    private JButton makeDialogBtn(String text, Color neon) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? neon.darker() : BG_CARD);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(neon); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1,1,getWidth()-2,getHeight()-2,8,8);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(neon);
        b.setContentAreaFilled(false); b.setBorderPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Gửi GIF URL lên server và hiển thị ngay cho mình */
    void sendGifUrl(String url) {
        try {
            dataOut.writeUTF("TEXT:GIF:" + myName + ":" + url);
            dataOut.flush();
            appendChat("  Tôi 🎞 GIF:\n", NEON_GREEN, true);
            loadAndShowGif(url);
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    /** Tải và hiển thị GIF animated trong chatPane */
    void loadAndShowGif(String url) {
        new Thread(() -> {
            try {
                Image scaled = new ImageIcon(new URL(url)).getImage().getScaledInstance(260, -1, Image.SCALE_DEFAULT);
                SwingUtilities.invokeLater(() -> {
                    chatPane.setCaretPosition(chatPane.getDocument().getLength());
                    chatPane.insertComponent(new JLabel(new ImageIcon(scaled)));
                    appendChat("\n", TEXT_PRIMARY, false);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    appendChat("  ❌ Không tải được GIF: " + url + "\n", DANGER, false));
            }
        }).start();
    }

    // ================================================================
    // MAIN
    // ================================================================
    
 // ================================================================
 // ML ANALYSIS
 // ================================================================

 private JPanel makeStatCard(String title, String value, Color color) {
     JPanel card = new JPanel();
     card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
     card.setBackground(new Color(15, 18, 28));
     card.setBorder(BorderFactory.createCompoundBorder(
         new ChatClientUI.RoundedBorder(color.darker(), 8),
         new EmptyBorder(12, 12, 12, 12)));

     JLabel valLbl = new JLabel(value, SwingConstants.CENTER);
     valLbl.setFont(new Font("SansSerif", Font.BOLD, 22));
     valLbl.setForeground(color);
     valLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

     JLabel titLbl = new JLabel(title, SwingConstants.CENTER);
     titLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
     titLbl.setForeground(TEXT_MUTED);
     titLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

     card.add(Box.createVerticalStrut(4));
     card.add(valLbl);
     card.add(Box.createVerticalStrut(6));
     card.add(titLbl);
     return card;
 }
 
//================================================================
//ML ANALYSIS — Load CSV + Ket noi Docker
//================================================================
 
 private void filterTable(
	        javax.swing.table.DefaultTableModel[] models,
	        java.util.List<Object[]> allData,
	        String keyword, int activeTab) {

	    String[] xepFilter = {null, "Xuat sac", "Gioi", "Kha", "Trung binh", "Yeu"};
	    String filter = xepFilter[activeTab];

	    for (int i = 0; i < models.length; i++) {
	        final int ti = i;
	        String tf = (i == 0) ? null : xepFilter[i];
	        models[i].setRowCount(0);
	        for (Object[] row : allData) {
	            String mssv  = row[0].toString().toLowerCase();
	            String hoten = row[1].toString().toLowerCase();
	            String xep   = row[5].toString();
	            String lop = row[2].toString().toLowerCase();
	            boolean matchKw = keyword.isEmpty()
	                || mssv.contains(keyword) || hoten.contains(keyword) || lop.contains(keyword);
	            boolean matchTab = (tf == null) || xep.equals(tf);
	            if (matchKw && matchTab) models[ti].addRow(row);
	        }
	    }
	}
 
//================================================================
//ML ANALYSIS — Full Dashboard
//================================================================
void showMLAnalysis() {
  JDialog dialog = new JDialog(chatFrame, "📊 ML Analysis", false);
  dialog.setSize(1000, 750);
  dialog.setLocationRelativeTo(chatFrame);
  dialog.getContentPane().setBackground(BG_DARKEST);
  dialog.setLayout(new BorderLayout(0, 0));

  // ── Header ──
  JPanel header = new JPanel(new BorderLayout());
  header.setBackground(new Color(20, 24, 38));
  header.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, 1, 0, NEON_PURPLE),
      new EmptyBorder(14, 20, 14, 20)));
  JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
  headerLeft.setOpaque(false);
  JLabel iconLbl = new JLabel("📊");
  iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
  JPanel titleCol = new JPanel();
  titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
  titleCol.setOpaque(false);
  JLabel titleLbl = new JLabel("ML Analysis Dashboard");
  titleLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
  titleLbl.setForeground(NEON_PURPLE);
  JLabel subLbl = new JLabel("Phong: " + currentRoom + "  |  Du lieu: sinhvien_2000.csv");
  subLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
  subLbl.setForeground(TEXT_MUTED);
  titleCol.add(titleLbl);
  titleCol.add(subLbl);
  headerLeft.add(iconLbl);
  headerLeft.add(titleCol);
  JButton closeBtn = new JButton("✕");
  closeBtn.setForeground(TEXT_MUTED);
  closeBtn.setContentAreaFilled(false);
  closeBtn.setBorderPainted(false);
  closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
  closeBtn.addActionListener(e -> dialog.dispose());
  header.add(headerLeft, BorderLayout.WEST);
  header.add(closeBtn,   BorderLayout.EAST);

  // ── Stat cards ──
  JLabel[] statValues = new JLabel[4];
  JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
  statsPanel.setBackground(BG_DARKEST);
  statsPanel.setBorder(new EmptyBorder(14, 16, 10, 16));
  String[] stitles = {"🔴 Nguy co truot", "📈 Diem TB", "📅 Vang TB", "👥 Tong SV"};
  Color[]  scolors = {
      new Color(248,113,113), new Color(52,211,153),
      new Color(251,191,36),  new Color(96,165,250)
  };
  for (int i = 0; i < 4; i++) {
      JPanel card = new JPanel();
      card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
      card.setBackground(new Color(15, 18, 28));
      card.setBorder(BorderFactory.createCompoundBorder(
          new RoundedBorder(scolors[i].darker(), 10),
          new EmptyBorder(14, 10, 14, 10)));
      statValues[i] = new JLabel("...", SwingConstants.CENTER);
      statValues[i].setFont(new Font("SansSerif", Font.BOLD, 26));
      statValues[i].setForeground(scolors[i]);
      statValues[i].setAlignmentX(Component.CENTER_ALIGNMENT);
      JLabel tl = new JLabel(stitles[i], SwingConstants.CENTER);
      tl.setFont(new Font("SansSerif", Font.PLAIN, 11));
      tl.setForeground(TEXT_MUTED);
      tl.setAlignmentX(Component.CENTER_ALIGNMENT);
      card.add(Box.createVerticalStrut(4));
      card.add(statValues[i]);
      card.add(Box.createVerticalStrut(6));
      card.add(tl);
      statsPanel.add(card);
  }

  // ── Chart ──
  int[] chartData = {0, 0, 0, 0, 0};
//── Chart nâng cấp — Histogram + Density curve ──
JPanel chartPanel = new JPanel() {
   @Override protected void paintComponent(Graphics g) {
       super.paintComponent(g);
       Graphics2D g2 = (Graphics2D) g.create();
       g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
       g2.setColor(new Color(15, 18, 28));
       g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

       int W = getWidth(), H = getHeight();
       int padL = 55, padR = 20, padT = 35, padB = 45;
       int chartW = W - padL - padR;
       int chartH = H - padT - padB;

       String[] labels = {"Xuat sac","Gioi","Kha","Trung binh","Yeu"};
       Color[] bc = {
           new Color(52,211,153), new Color(96,165,250),
           new Color(167,139,250), new Color(251,191,36),
           new Color(248,113,113)
       };

       int total = 0;
       for (int v : chartData) total += v;
       if (total == 0) { g2.dispose(); return; }

       int maxVal = 0;
       for (int v : chartData) maxVal = Math.max(maxVal, v);

       // ── Tieu de ──
       g2.setColor(TEXT_MUTED);
       g2.setFont(new Font("SansSerif", Font.BOLD, 12));
       g2.drawString("Phan bo Xep loai Sinh vien", padL, 22);

       // ── Ve luoi ngang ──
       g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
       for (int i = 0; i <= 4; i++) {
           int y = padT + chartH - i * chartH / 4;
           g2.setColor(new Color(30, 40, 60));
           g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT,
               BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0));
           g2.drawLine(padL, y, padL + chartW, y);
           g2.setColor(TEXT_MUTED);
           int gridVal = maxVal * i / 4;
           g2.drawString(String.valueOf(gridVal), 5, y + 4);
       }

       // ── Ve cot Histogram ──
       g2.setStroke(new BasicStroke(1.5f));
       int barW = chartW / labels.length - 12;
       int[] barXs = new int[labels.length];
       int[] barTops = new int[labels.length];

       for (int i = 0; i < labels.length; i++) {
           int h = maxVal > 0 ? chartData[i] * chartH / maxVal : 0;
           int x = padL + i * (chartW / labels.length) + 6;
           int y = padT + chartH - h;
           barXs[i]  = x + barW / 2;
           barTops[i] = y;

           // Shadow
           g2.setColor(new Color(bc[i].getRed(), bc[i].getGreen(), bc[i].getBlue(), 30));
           g2.fillRoundRect(x + 4, y + 4, barW, h, 8, 8);

           // Gradient bar
           GradientPaint gp = new GradientPaint(
               x, y, bc[i].brighter(),
               x, y + h, bc[i].darker());
           g2.setPaint(gp);
           g2.fillRoundRect(x, y, barW, h, 8, 8);

           // Vien bar
           g2.setColor(bc[i]);
           g2.setStroke(new BasicStroke(1.2f));
           g2.drawRoundRect(x, y, barW, h, 8, 8);

           // % phia tren
           g2.setColor(Color.WHITE);
           g2.setFont(new Font("SansSerif", Font.BOLD, 11));
           String pct = (chartData[i] * 100 / total) + "%";
           FontMetrics fm = g2.getFontMetrics();
           g2.drawString(pct, x + (barW - fm.stringWidth(pct)) / 2, y - 16);

           // So luong
           g2.setColor(bc[i]);
           g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
           String cnt = String.valueOf(chartData[i]);
           g2.drawString(cnt, x + (barW - fm.stringWidth(cnt)) / 2, y - 4);

           // Label duoi
           g2.setColor(TEXT_MUTED);
           g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
           FontMetrics fm2 = g2.getFontMetrics();
           g2.drawString(labels[i],
               x + (barW - fm2.stringWidth(labels[i])) / 2,
               padT + chartH + 16);

           // Diem tren truc X
           g2.setColor(bc[i]);
           g2.fillOval(x + barW/2 - 3, padT + chartH - 3, 6, 6);
       }

       // ── Ve duong cong mat do (Density curve) ──
       g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

       // Tinh diem control cho bezier
       int n = labels.length;
       int[] cx = new int[n];
       int[] cy = new int[n];
       for (int i = 0; i < n; i++) {
           cx[i] = barXs[i];
           cy[i] = barTops[i];
       }

       // Ve duong cong qua cac dinh bar
       java.awt.geom.GeneralPath path = new java.awt.geom.GeneralPath();
       path.moveTo(cx[0], cy[0]);
       for (int i = 0; i < n - 1; i++) {
           int mx = (cx[i] + cx[i+1]) / 2;
           path.curveTo(mx, cy[i], mx, cy[i+1], cx[i+1], cy[i+1]);
       }

       // Ve vung to mau ben duoi duong cong
       java.awt.geom.GeneralPath fillPath = new java.awt.geom.GeneralPath(path);
       fillPath.lineTo(cx[n-1], padT + chartH);
       fillPath.lineTo(cx[0],   padT + chartH);
       fillPath.closePath();
       g2.setColor(new Color(167, 139, 250, 35));
       g2.fill(fillPath);

       // Ve duong cong chinh
       g2.setColor(new Color(167, 139, 250, 200));
       g2.draw(path);

       // Ve cac diem tren duong cong
       for (int i = 0; i < n; i++) {
           g2.setColor(new Color(167, 139, 250));
           g2.fillOval(cx[i] - 4, cy[i] - 4, 8, 8);
           g2.setColor(Color.WHITE);
           g2.setStroke(new BasicStroke(1.5f));
           g2.drawOval(cx[i] - 4, cy[i] - 4, 8, 8);
       }

       // ── Legend ──
       g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
       int lx = padL + chartW - 140;
       int ly = padT + 10;

       // Histogram legend
       g2.setColor(new Color(96, 165, 250, 150));
       g2.fillRect(lx, ly, 14, 10);
       g2.setColor(TEXT_MUTED);
       g2.drawString("So luong SV", lx + 18, ly + 9);

       // Density legend
       g2.setColor(new Color(167, 139, 250));
       g2.setStroke(new BasicStroke(2f));
       g2.drawLine(lx, ly + 22, lx + 14, ly + 22);
       g2.fillOval(lx + 4, ly + 18, 6, 6);
       g2.setColor(TEXT_MUTED);
       g2.drawString("Duong phan bo", lx + 18, ly + 26);

       g2.dispose();
   }
};
chartPanel.setPreferredSize(new Dimension(420, 0));
chartPanel.setBorder(new EmptyBorder(10, 16, 10, 8));
chartPanel.setBackground(BG_DARKEST);
  chartPanel.setPreferredSize(new Dimension(400, 0));
  chartPanel.setBorder(new EmptyBorder(10, 16, 10, 8));
  chartPanel.setBackground(BG_DARKEST);

  // ── Bang danh sach SV nguy co cao ──
  
  
//── Bang danh sach SV — Tabs xep loai + Tim kiem ──
String[] cols = {"MSSV", "Ho Ten", "Lop", "Diem TB", "Vang%", "Xep loai"};

//Tao table cho tung tab
javax.swing.table.DefaultTableModel[] tabModels = new javax.swing.table.DefaultTableModel[6];
JTable[] tables = new JTable[6];
String[] tabNames = {"Tat ca", "⭐ Xuat sac", "🔵 Gioi", "🟢 Kha", "🟡 Trung binh", "🔴 Yeu"};
Color[] tabColors = {
   NEON_PURPLE,
   new Color(52,211,153),
   new Color(96,165,250),
   new Color(167,139,250),
   new Color(251,191,36),
   new Color(248,113,113)
};

for (int i = 0; i < 6; i++) {
   final int idx = i;
   tabModels[i] = new javax.swing.table.DefaultTableModel(cols, 0) {
       public boolean isCellEditable(int r, int c) { return false; }
   };
   tables[i] = new JTable(tabModels[i]);
   tables[i].setBackground(new Color(15, 18, 28));
   tables[i].setForeground(TEXT_PRIMARY);
   tables[i].setGridColor(new Color(30, 45, 80));
   tables[i].setRowHeight(26);
   tables[i].setFont(new Font("SansSerif", Font.PLAIN, 12));
   tables[i].setSelectionBackground(new Color(tabColors[idx].getRed(),
       tabColors[idx].getGreen(), tabColors[idx].getBlue(), 60));
   tables[i].setSelectionForeground(Color.WHITE);
   tables[i].getTableHeader().setBackground(new Color(20, 24, 38));
   tables[i].getTableHeader().setForeground(tabColors[idx]);
   tables[i].getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));

   // Renderer mau dong
   tables[i].setDefaultRenderer(Object.class, (t, val, sel, foc, row, col) -> {
       JLabel lbl = new JLabel(val != null ? val.toString() : "");
       lbl.setOpaque(true);
       lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
       lbl.setBorder(new EmptyBorder(0, 8, 0, 4));
       if (sel) {
           lbl.setBackground(new Color(tabColors[idx].getRed(),
               tabColors[idx].getGreen(), tabColors[idx].getBlue(), 80));
           lbl.setForeground(Color.WHITE);
       } else {
           lbl.setBackground(row % 2 == 0
               ? new Color(15, 18, 28)
               : new Color(20, 24, 38));
           lbl.setForeground(TEXT_PRIMARY);
       }
       return lbl;
   });
}

//── Thanh tim kiem ──
JTextField searchField = new JTextField();
searchField.setBackground(new Color(25, 30, 48));
searchField.setForeground(TEXT_PRIMARY);
searchField.setCaretColor(NEON_PURPLE);
searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
searchField.setBorder(BorderFactory.createCompoundBorder(
   new RoundedBorder(NEON_PURPLE.darker(), 8),
   new EmptyBorder(6, 10, 6, 10)));
searchField.putClientProperty("JTextField.placeholderText", "🔍 Tim kiem MSSV hoac Ten sinh vien...");

JLabel searchIcon = new JLabel("🔍");
searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
searchIcon.setBorder(new EmptyBorder(0, 8, 0, 4));

JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
searchPanel.setBackground(BG_DARKEST);
searchPanel.setBorder(new EmptyBorder(8, 8, 6, 8));
searchPanel.add(searchIcon,  BorderLayout.WEST);
searchPanel.add(searchField, BorderLayout.CENTER);

//── JTabbedPane ──
JTabbedPane tabbedPane = new JTabbedPane();
tabbedPane.setBackground(BG_DARKEST);
tabbedPane.setForeground(TEXT_PRIMARY);
tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 11));

for (int i = 0; i < 6; i++) {
   JScrollPane sp = new JScrollPane(tables[i]);
   sp.setBorder(null);
   sp.getViewport().setBackground(new Color(15, 18, 28));
   tabbedPane.addTab(tabNames[i], sp);
   tabbedPane.setForegroundAt(i, tabColors[i]);
}

//── Right panel = search + tabs ──
JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
rightPanel.setBackground(BG_DARKEST);
rightPanel.setBorder(BorderFactory.createCompoundBorder(
   new EmptyBorder(10, 8, 10, 16),
   new RoundedBorder(NEON_PURPLE, 8)));
rightPanel.add(searchPanel, BorderLayout.NORTH);
rightPanel.add(tabbedPane,  BorderLayout.CENTER);

//── Center (chart + rightPanel) ──
JPanel center = new JPanel(new GridLayout(1, 2, 0, 0));
center.setBackground(BG_DARKEST);
center.add(chartPanel);
center.add(rightPanel);

//── Luu toan bo SV de filter ──
java.util.List<Object[]> allSvData = new java.util.ArrayList<>();

//── Tim kiem realtime ──
searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
   void doFilter() {
       String keyword = searchField.getText().trim().toLowerCase();
       int activeTab = tabbedPane.getSelectedIndex();
       filterTable(tabModels, allSvData, keyword, activeTab);
   }
   public void insertUpdate(javax.swing.event.DocumentEvent e)  { doFilter(); }
   public void removeUpdate(javax.swing.event.DocumentEvent e)  { doFilter(); }
   public void changedUpdate(javax.swing.event.DocumentEvent e) { doFilter(); }
});

tabbedPane.addChangeListener(e -> {
   String keyword = searchField.getText().trim().toLowerCase();
   int activeTab = tabbedPane.getSelectedIndex();
   filterTable(tabModels, allSvData, keyword, activeTab);
});

  // ── Status bar ──
  JLabel statusLbl = new JLabel("  ⏳ Dang tai du lieu...");
  statusLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
  statusLbl.setForeground(NEON_PURPLE);
  statusLbl.setBorder(new EmptyBorder(8, 16, 8, 16));
  statusLbl.setOpaque(true);
  statusLbl.setBackground(new Color(15, 18, 28));

  JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
  mainPanel.setBackground(BG_DARKEST);
  mainPanel.add(statsPanel, BorderLayout.NORTH);
  mainPanel.add(center,     BorderLayout.CENTER);
  mainPanel.add(statusLbl,  BorderLayout.SOUTH);

  dialog.add(header,    BorderLayout.NORTH);
  dialog.add(mainPanel, BorderLayout.CENTER);
  center.setPreferredSize(new Dimension(0, 280));

  // ── Load data ──
  new Thread(() -> {
      int total = 0, truot = 0, canhBao = 0;
      int xs = 0, gioi = 0, kha = 0, tb = 0, yeu = 0;
      double tongDTB = 0, tongVang = 0;
      java.util.List<Object[]> svNguyCo = new java.util.ArrayList<>();

      String csvPath = System.getProperty("user.home") 
    		    + "/Desktop/DoAnTotNghiep_NTTU/chatapp110520042005/sinhvien_2000.csv";
    		try (java.io.BufferedReader br = new java.io.BufferedReader(
    		        new java.io.FileReader(csvPath))) {
          String line;
          boolean first = true;
          while ((line = br.readLine()) != null) {
              if (first) { first = false; continue; }
              String[] p = line.split(",");
              if (p.length < 8) continue;
              total++;
              String mssv  = p[0].trim();
              String hoten = p[1].trim();
              String lop   = p[2].trim();
              double dtb   = Double.parseDouble(p[6].trim().replace(",", "."));
              double vang  = Double.parseDouble(p[7].trim().replace(",", "."));
              String xep   = p[8].trim();
              tongDTB  += dtb;
              tongVang += vang;
              if (dtb < 4.0)  truot++;
              if (dtb < 5.5)  canhBao++;
              switch (xep) {
                  case "Xuat sac":   xs++;   break;
                  case "Gioi":       gioi++;  break;
                  case "Kha":        kha++;   break;
                  case "Trung binh": tb++;    break;
                  case "Yeu":        yeu++;   break;
              }
              // Chi lay SV co DTB < 5.5 vao bang
           // Lay tat ca SV vao bang (khong loc DTB)
              allSvData.add(new Object[]{
                  mssv, hoten, lop,
                  String.format("%.2f", dtb),
                  String.format("%.1f%%", vang),
                  xep
              });
          }
    		} catch (Exception e) {
    		    e.printStackTrace(); // <- THEM DONG NAY
    		    String errMsg = e.getMessage();
    		    SwingUtilities.invokeLater(() -> {
    		        statusLbl.setText("  ❌ Loi: " + errMsg);
    		        statusLbl.setForeground(new Color(248, 113, 113));
    		    });
    		    return;
    		}

      chartData[0] = xs; chartData[1] = gioi;
      chartData[2] = kha; chartData[3] = tb; chartData[4] = yeu;

      // Sap xep theo DTB tang dan
      allSvData.sort((a, b) -> Double.compare(
    		    Double.parseDouble(a[3].toString().replace(",", ".")),
    		    Double.parseDouble(b[3].toString().replace(",", "."))));

      final int    fTotal   = total;
      final int    fTruot   = truot;
      final double fDTB     = total > 0 ? tongDTB  / total : 0;
      final double fVang    = total > 0 ? tongVang / total : 0;
      final int    pctTruot = total > 0 ? truot * 100 / total : 0;
      final java.util.List<Object[]> fList = svNguyCo;

      SwingUtilities.invokeLater(() -> {
          statValues[0].setText(pctTruot + "%");
          statValues[1].setText(String.format("%.1f", fDTB));
          statValues[2].setText(String.format("%.1f%%", fVang));
          statValues[3].setText(String.valueOf(fTotal));
          chartPanel.repaint();

          // Do bang
          filterTable(tabModels, allSvData, "", 0);
          statusLbl.setText("  ✅ " + fTotal + " SV | " + fTruot
              + " nguy co truot | Dang ket noi Docker...");
          statusLbl.setForeground(new Color(52, 211, 153));
      });

      // Kiem tra Docker
      try {
          java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
              new java.net.URL("http://localhost:8000/").openConnection();
          conn.setConnectTimeout(2000);
          conn.connect();
          int code = conn.getResponseCode();
          SwingUtilities.invokeLater(() -> {
              String dockerStatus = code == 200 ? "Face Service ONLINE ✅" : "Face Service loi ⚠";
              statusLbl.setText("  ✅ " + fTotal + " SV | "
                  + fTruot + " nguy co truot | " + dockerStatus);
          });
      } catch (Exception e) {
          SwingUtilities.invokeLater(() ->
              statusLbl.setText("  ✅ CSV OK | ❌ Docker chua chay — bat Docker len!"));
      }
  }).start();

  dialog.setVisible(true);
}

//================================================================
//FACE ATTENDANCE - Dang ky khuon mat
//================================================================
//================================================================
//FACE REGISTER — Đăng ký 5 góc khuôn mặt
//Thay thế toàn bộ method showFaceRegister() trong ChatClientUI.java
//================================================================
//================================================================
//FACE REGISTER — Flow tự động từng bước (thay thế toàn bộ method cũ)
//Copy method này vào ChatClientUI.java, thay thế showFaceRegister()
//================================================================
void showFaceRegister() {
 final String[] angles   = {"front", "left", "right", "up", "down"};
 final String[] anglesVN = {"Nhìn thẳng", "Quay trái", "Quay phải", "Ngửa lên", "Cúi xuống"};
 final String[] emojis   = {"😐", "👈", "👉", "🙃", "🙇"};
 final String[] hints    = {
     "Giữ mặt thẳng, nhìn vào camera",
     "Từ từ quay đầu sang trái",
     "Từ từ quay đầu sang phải",
     "Ngửa đầu nhẹ lên trên",
     "Cúi đầu nhẹ xuống dưới"
 };

 // ── Dialog chính ──
 JDialog dialog = new JDialog(chatFrame, "📸 Đăng ký khuôn mặt", true);
 dialog.setSize(400, 600);
 dialog.setLocationRelativeTo(chatFrame);
 dialog.setUndecorated(true);
 dialog.getContentPane().setBackground(new Color(8, 10, 16));
 try {
     dialog.setShape(new java.awt.geom.RoundRectangle2D.Double(0, 0, 400, 600, 20, 20));
 } catch (Exception ignored) {}
 dialog.setLayout(new BorderLayout());

 // ── Top bar ──
 JPanel topBar = new JPanel(new BorderLayout());
 topBar.setOpaque(false);
 topBar.setBorder(new EmptyBorder(18, 20, 10, 20));
 JLabel titleLbl = new JLabel("Đăng ký khuôn mặt", SwingConstants.CENTER);
 titleLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
 titleLbl.setForeground(new Color(220, 230, 255));
 JButton btnX = new JButton("✕");
 btnX.setFont(new Font("SansSerif", Font.BOLD, 13));
 btnX.setForeground(new Color(80, 100, 140));
 btnX.setContentAreaFilled(false);
 btnX.setBorderPainted(false);
 btnX.setCursor(new Cursor(Cursor.HAND_CURSOR));
 topBar.add(titleLbl, BorderLayout.CENTER);
 topBar.add(btnX,     BorderLayout.EAST);

 // ── Progress dots ──
 JPanel dotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
 dotsPanel.setOpaque(false);
 JLabel[] dots = new JLabel[5];
 for (int i = 0; i < 5; i++) {
     dots[i] = new JLabel("●");
     dots[i].setFont(new Font("SansSerif", Font.PLAIN, 16));
     dots[i].setForeground(i == 0 ? new Color(0, 180, 255) : new Color(40, 50, 80));
     dotsPanel.add(dots[i]);
 }

 // ── Camera panel (hình tròn) ──
 java.util.concurrent.atomic.AtomicBoolean hasFace = new java.util.concurrent.atomic.AtomicBoolean(false);
 WebcamHelper.CamPanel camPanel = new WebcamHelper.CamPanel(280, hasFace);

 JPanel camWrap = new JPanel(new GridBagLayout());
 camWrap.setOpaque(false);
 camWrap.add(camPanel);

 // ── Countdown bar ──
 JProgressBar countdown = new JProgressBar(0, 20); // 2 giây × 10fps
 countdown.setValue(0);
 countdown.setForeground(new Color(0, 255, 136));
 countdown.setBackground(new Color(20, 26, 42));
 countdown.setBorderPainted(false);
 countdown.setPreferredSize(new Dimension(260, 6));
 countdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
 countdown.setVisible(false);

 JPanel cdWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
 cdWrap.setOpaque(false);
 cdWrap.add(countdown);

 // ── Emoji + tên góc + gợi ý ──
 JLabel emojiLbl = new JLabel(emojis[0], SwingConstants.CENTER);
 emojiLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
 emojiLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

 JLabel angleLbl = new JLabel(anglesVN[0], SwingConstants.CENTER);
 angleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
 angleLbl.setForeground(new Color(220, 230, 255));
 angleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

 JLabel hintLbl = new JLabel(hints[0], SwingConstants.CENTER);
 hintLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
 hintLbl.setForeground(new Color(80, 100, 140));
 hintLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

 // ── Status ──
 JLabel statusLbl = new JLabel("🔍 Đang tìm khuôn mặt...", SwingConstants.CENTER);
 statusLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
 statusLbl.setForeground(new Color(0, 180, 255));
 statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

 // ── Progress text ──
 JLabel progressLbl = new JLabel("0 / 5 góc hoàn thành", SwingConstants.CENTER);
 progressLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
 progressLbl.setForeground(new Color(80, 100, 140));
 progressLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

 // ── Root panel ──
 JPanel root = new JPanel(new BorderLayout()) {
     @Override protected void paintComponent(Graphics g) {
         Graphics2D g2 = (Graphics2D) g.create();
         g2.setColor(new Color(8, 10, 16));
         g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
         g2.dispose();
     }
 };
 root.setOpaque(false);
 root.setBorder(BorderFactory.createLineBorder(new Color(30, 40, 65), 1));

 JPanel center = new JPanel();
 center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
 center.setOpaque(false);
 center.setBorder(new EmptyBorder(10, 20, 10, 20));
 center.add(dotsPanel);
 center.add(Box.createVerticalStrut(12));
 center.add(camWrap);
 center.add(Box.createVerticalStrut(8));
 center.add(cdWrap);
 center.add(Box.createVerticalStrut(10));
 center.add(emojiLbl);
 center.add(Box.createVerticalStrut(4));
 center.add(angleLbl);
 center.add(Box.createVerticalStrut(4));
 center.add(hintLbl);
 center.add(Box.createVerticalStrut(10));
 center.add(statusLbl);
 center.add(Box.createVerticalStrut(6));
 center.add(progressLbl);

 root.add(topBar, BorderLayout.NORTH);
 root.add(center, BorderLayout.CENTER);
 dialog.add(root);

 // ================================================================
 // STATE
 // ================================================================
 java.util.concurrent.atomic.AtomicBoolean running     = new java.util.concurrent.atomic.AtomicBoolean(true);
 java.util.concurrent.atomic.AtomicInteger currentStep = new java.util.concurrent.atomic.AtomicInteger(0);
 java.util.concurrent.atomic.AtomicBoolean capturing   = new java.util.concurrent.atomic.AtomicBoolean(false);
 int[] holdCounter = {0};

 String mssv  = ScheduleSystem.extractMSSV(myName);
 String hoten = myName;

 // ── Đóng dialog ──
 Runnable closeDialog = () -> {
     running.set(false);
     dialog.dispose();
 };
 btnX.addActionListener(e -> closeDialog.run());

 // ================================================================
 // LIVE THREAD — preview + auto-capture từng bước
 // ================================================================
 Thread liveThread = new Thread(() -> {
     while (running.get()) {
         try {
             // 1. Lấy preview
             java.net.HttpURLConnection pc = (java.net.HttpURLConnection)
                 new java.net.URL("http://127.0.0.1:7777/snapshot").openConnection();
             pc.setConnectTimeout(3000); pc.setReadTimeout(3000);
             if (pc.getResponseCode() == 200) {
                 byte[] previewBytes = pc.getInputStream().readAllBytes();
                 java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                     new java.io.ByteArrayInputStream(previewBytes));
                 if (img != null && running.get()) {
                     final java.awt.image.BufferedImage fi = img;
                     SwingUtilities.invokeLater(() -> camPanel.setImage(fi));
                 }
             }

             // 2. Kiểm tra has-face
             java.net.HttpURLConnection hc = (java.net.HttpURLConnection)
                 new java.net.URL("http://127.0.0.1:7777/has-face").openConnection();
             hc.setConnectTimeout(2000); hc.setReadTimeout(2000);
             boolean hf = false;
             if (hc.getResponseCode() == 200) {
                 String resp = new String(hc.getInputStream().readAllBytes());
                 hf = resp.contains("\"has_face\": true") || resp.contains("\"has_face\":true");
             }
             final boolean hasFaceNow = hf;
             hasFace.set(hf);

             int step = currentStep.get();
             if (step >= 5 || capturing.get()) {
                 Thread.sleep(100);
                 continue;
             }

             if (hasFaceNow) {
                 // Tăng bộ đếm giữ mặt
                 holdCounter[0]++;
                 int prog = Math.min(holdCounter[0], 20);
                 SwingUtilities.invokeLater(() -> {
                     countdown.setVisible(true);
                     countdown.setValue(prog);
                     statusLbl.setText("Giữ yên...");
                     statusLbl.setForeground(new Color(0, 255, 136));
                     camPanel.repaint();
                 });

                 // Đủ 2 giây → chụp tự động
                 if (holdCounter[0] >= 20) {
                     capturing.set(true);
                     holdCounter[0] = 0;

                     SwingUtilities.invokeLater(() -> {
                         countdown.setValue(0);
                         countdown.setVisible(false);
                         statusLbl.setText("📸 Đang chụp...");
                         statusLbl.setForeground(new Color(255, 200, 0));
                     });

                     Thread.sleep(150);

                     // Lấy ảnh raw
                     java.net.HttpURLConnection rc = (java.net.HttpURLConnection)
                         new java.net.URL("http://127.0.0.1:7777/snapshot-raw").openConnection();
                     rc.setConnectTimeout(5000); rc.setReadTimeout(8000);

                     if (rc.getResponseCode() != 200) {
                         // Server lỗi → bắt làm lại
                         SwingUtilities.invokeLater(() -> {
                             statusLbl.setText("❌ Không lấy được ảnh, thử lại!");
                             statusLbl.setForeground(new Color(255, 80, 80));
                         });
                         capturing.set(false);
                         continue;
                     }

                     byte[] rawBytes = rc.getInputStream().readAllBytes();

                     // Kiểm tra chất lượng ảnh đơn giản (size quá nhỏ = tối/mờ)
                     if (rawBytes.length < 5000) {
                         SwingUtilities.invokeLater(() -> {
                             statusLbl.setText("⚠️ Ảnh quá tối hoặc mờ! Thử lại...");
                             statusLbl.setForeground(new Color(255, 140, 0));
                         });
                         Thread.sleep(1500);
                         capturing.set(false);
                         continue;
                     }

                     // Gửi lên API
                     final int capturedStep = step;
                     final byte[] finalBytes = rawBytes;
                     new Thread(() -> {
                         try {
                             String result = callFaceAPIAngle(
                                 "/dangky", mssv, hoten,
                                 angles[capturedStep], finalBytes);

                             // Thành công → chuyển bước tiếp
                             SwingUtilities.invokeLater(() -> {
                                 // Dot xanh
                                 dots[capturedStep].setForeground(new Color(0, 255, 136));

                                 int next = capturedStep + 1;
                                 currentStep.set(next);
                                 progressLbl.setText(next + " / 5 góc hoàn thành");

                                 if (next >= 5) {
                                     // ── HOÀN TẤT ──
                                     running.set(false);
                                     emojiLbl.setText("🎉");
                                     angleLbl.setText("Đăng ký hoàn tất!");
                                     hintLbl.setText("Tất cả 5 góc đã được lưu");
                                     statusLbl.setForeground(new Color(0, 255, 136));
                                     statusLbl.setText("✅ Thành công! Đang đóng...");
                                     javax.swing.Timer t = new javax.swing.Timer(1800,
                                         ev -> dialog.dispose());
                                     t.setRepeats(false);
                                     t.start();
                                 } else {
                                     // ── Chuyển góc tiếp ──
                                     dots[next].setForeground(new Color(0, 180, 255));
                                     emojiLbl.setText(emojis[next]);
                                     angleLbl.setText(anglesVN[next]);
                                     hintLbl.setText(hints[next]);
                                     statusLbl.setForeground(new Color(0, 180, 255));
                                     statusLbl.setText("✅ Xong! Chuẩn bị góc tiếp theo...");

                                     // Delay nhỏ rồi mới cho chụp tiếp
                                     javax.swing.Timer t = new javax.swing.Timer(1200, ev -> {
                                         statusLbl.setText("🔍 Đang tìm khuôn mặt...");
                                         capturing.set(false);
                                     });
                                     t.setRepeats(false);
                                     t.start();
                                 }
                             });

                         } catch (Exception ex) {
                             // API lỗi → bắt làm lại góc này
                             SwingUtilities.invokeLater(() -> {
                                 dots[capturedStep].setForeground(new Color(255, 80, 80));
                                 statusLbl.setForeground(new Color(255, 80, 80));
                                 statusLbl.setText("❌ Lỗi: " + ex.getMessage() + " — Thử lại!");
                             });
                             try { Thread.sleep(1500); } catch (Exception ignored) {}
                             capturing.set(false);
                         }
                     }).start();
                 }

             } else {
                 // Không có mặt → reset bộ đếm
                 holdCounter[0] = 0;
                 SwingUtilities.invokeLater(() -> {
                     countdown.setValue(0);
                     countdown.setVisible(false);
                     statusLbl.setText("🔍 Đang tìm khuôn mặt...");
                     statusLbl.setForeground(new Color(0, 180, 255));
                     camPanel.repaint();
                 });
             }

             Thread.sleep(100); // 10fps polling

         } catch (Exception ex) {
             try { Thread.sleep(500); } catch (Exception ignored) {}
         }
     }
 });
 liveThread.setDaemon(true);
 liveThread.start();

 dialog.setVisible(true);
}

//Helper gọi API với tham số angle
private String callFaceAPIAngle(String endpoint, String mssv, String hoten,
                               String angle, byte[] imgBytes) throws Exception {
 String boundary = "----Boundary" + System.currentTimeMillis();
 java.net.URL url = new java.net.URL(
     "http://localhost:8000" + endpoint
     + "?mssv=" + mssv
     + "&hoten=" + java.net.URLEncoder.encode(hoten, "UTF-8")
     + "&angle=" + angle
 );
 java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
 conn.setRequestMethod("POST");
 conn.setDoOutput(true);
 conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

 try (java.io.OutputStream os = conn.getOutputStream()) {
     String partHeader = "--" + boundary + "\r\n"
         + "Content-Disposition: form-data; name=\"file\"; filename=\"face.jpg\"\r\n"
         + "Content-Type: image/jpeg\r\n\r\n";
     os.write(partHeader.getBytes());
     os.write(imgBytes);
     os.write(("\r\n--" + boundary + "--\r\n").getBytes());
 }

 java.io.InputStream is = conn.getResponseCode() == 200
     ? conn.getInputStream() : conn.getErrorStream();
 String resp = new String(is.readAllBytes(), "UTF-8");

 if (resp.contains("\"message\"")) {
     int i = resp.indexOf("\"message\"") + 11;
     int j = resp.indexOf("\"", i + 1);
     return resp.substring(i + 1, j);
 }
 return resp;
}

//================================================================
//FACE ATTENDANCE - Diem danh bang khuon mat
//================================================================
void showFaceAttend() {
    // ── Kiểm tra đã điểm danh hôm nay chưa TRƯỚC khi mở camera ──
    java.nio.file.Path csvPath = java.nio.file.Path.of(
        System.getProperty("user.home") + "/Desktop/diemdanh.csv");
    String today = new java.text.SimpleDateFormat("yyyy-MM-dd")
        .format(new java.util.Date());

    try {
        if (java.nio.file.Files.exists(csvPath)) {
            boolean daDiemDanh = java.nio.file.Files.readAllLines(csvPath)
                .stream()
                .anyMatch(line -> line.startsWith(myName + ",") && line.contains(today));

            if (daDiemDanh) {
                // Hiện dialog thông báo, không mở camera
                JDialog warnDialog = new JDialog(chatFrame, "Thông báo", true);
                warnDialog.setSize(380, 200);
                warnDialog.setLocationRelativeTo(chatFrame);
                warnDialog.getContentPane().setBackground(BG_PANEL);
                warnDialog.setLayout(new BorderLayout());

                JPanel card = new JPanel();
                card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                card.setBackground(BG_PANEL);
                card.setBorder(new EmptyBorder(28, 30, 24, 30));

                JLabel iconLbl = new JLabel("⚠️", SwingConstants.CENTER);
                iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
                iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                JLabel msgLbl = new JLabel("Bạn đã điểm danh hôm nay rồi!", SwingConstants.CENTER);
                msgLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
                msgLbl.setForeground(DANGER);
                msgLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                JLabel subLbl = new JLabel("Mỗi ngày chỉ được điểm danh 1 lần.", SwingConstants.CENTER);
                subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
                subLbl.setForeground(TEXT_MUTED);
                subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton btnOK = neonButton("Đóng", DANGER);
                btnOK.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
                btnOK.setAlignmentX(Component.CENTER_ALIGNMENT);
                btnOK.addActionListener(e -> warnDialog.dispose());

                card.add(iconLbl);
                card.add(Box.createVerticalStrut(10));
                card.add(msgLbl);
                card.add(Box.createVerticalStrut(6));
                card.add(subLbl);
                card.add(Box.createVerticalStrut(18));
                card.add(btnOK);

                warnDialog.add(card, BorderLayout.CENTER);
                warnDialog.setVisible(true);

                // Cũng báo lên chat
                appendChat("⚠️ [Điểm danh] " + myName
                    + " đã điểm danh hôm nay rồi!\n", DANGER, true);
                return; // Dừng, không mở camera
            }
        }
    } catch (Exception ignored) {}

    // ── Chưa điểm danh → mở dialog camera ──
    JDialog dialog = new JDialog(chatFrame, "🤖 Điểm danh khuôn mặt", false);
    dialog.setSize(420, 560);
    dialog.setLocationRelativeTo(chatFrame);
    dialog.getContentPane().setBackground(BG_DARK);
    dialog.setLayout(new BorderLayout());

    JPanel card = new JPanel(new BorderLayout(0, 12));
    card.setBackground(BG_PANEL);
    card.setBorder(new EmptyBorder(20, 24, 20, 24));

    JLabel titleLbl = new JLabel("Điểm danh bằng khuôn mặt", SwingConstants.CENTER);
    titleLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
    titleLbl.setForeground(NEON_GREEN);

    java.util.concurrent.atomic.AtomicBoolean hasFace =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    WebcamHelper.CamPanel camPanel = new WebcamHelper.CamPanel(300, hasFace);

    JPanel camWrap = new JPanel(new GridBagLayout());
    camWrap.setOpaque(false);
    camWrap.add(camPanel);

    JLabel statusLbl = new JLabel("🔍 Đang tìm khuôn mặt...", SwingConstants.CENTER);
    statusLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
    statusLbl.setForeground(NEON_CYAN);

    JButton btnCapture = neonButton("📸 Chụp & Điểm danh", NEON_GREEN);
    btnCapture.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
    btnCapture.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
    bottomPanel.setOpaque(false);
    bottomPanel.add(statusLbl);
    bottomPanel.add(Box.createVerticalStrut(12));
    bottomPanel.add(btnCapture);

    card.add(titleLbl,    BorderLayout.NORTH);
    card.add(camWrap,     BorderLayout.CENTER);
    card.add(bottomPanel, BorderLayout.SOUTH);
    dialog.add(card, BorderLayout.CENTER);

    // ── Live preview thread ──
    java.util.concurrent.atomic.AtomicBoolean running =
        new java.util.concurrent.atomic.AtomicBoolean(true);

    Thread liveThread = new Thread(() -> {
        while (running.get()) {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL("http://127.0.0.1:7777/snapshot").openConnection();
                c.setConnectTimeout(2000); c.setReadTimeout(2000);
                if (c.getResponseCode() == 200) {
                    byte[] bytes = c.getInputStream().readAllBytes();
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(bytes));
                    if (img != null) {
                        final java.awt.image.BufferedImage fi = img;
                        SwingUtilities.invokeLater(() -> camPanel.setImage(fi));
                    }
                }
                java.net.HttpURLConnection hc = (java.net.HttpURLConnection)
                    new java.net.URL("http://127.0.0.1:7777/has-face").openConnection();
                hc.setConnectTimeout(1500); hc.setReadTimeout(1500);
                if (hc.getResponseCode() == 200) {
                    String resp = new String(hc.getInputStream().readAllBytes());
                    boolean hf = resp.contains("\"has_face\":true")
                              || resp.contains("\"has_face\": true");
                    hasFace.set(hf);
                    SwingUtilities.invokeLater(() -> {
                        if (hf) {
                            statusLbl.setForeground(NEON_GREEN);
                            statusLbl.setText("✅ Phát hiện khuôn mặt — nhấn Chụp!");
                        } else {
                            statusLbl.setForeground(NEON_CYAN);
                            statusLbl.setText("🔍 Đang tìm khuôn mặt...");
                        }
                        camPanel.repaint();
                    });
                }
                Thread.sleep(100);
            } catch (Exception ex) {
                try { Thread.sleep(500); } catch (Exception ignored) {}
            }
        }
    });
    liveThread.setDaemon(true);
    liveThread.start();

    // ── Nút chụp ──
    btnCapture.addActionListener(e -> {
        btnCapture.setEnabled(false);
        statusLbl.setForeground(TEXT_MUTED);
        statusLbl.setText("📸 Đang chụp ảnh...");

        new Thread(() -> {
            try {
                java.net.HttpURLConnection rc = (java.net.HttpURLConnection)
                    new java.net.URL("http://127.0.0.1:7777/snapshot-raw").openConnection();
                rc.setConnectTimeout(5000); rc.setReadTimeout(8000);
                if (rc.getResponseCode() != 200)
                    throw new Exception("Camera server lỗi!");
                byte[] imgBytes = rc.getInputStream().readAllBytes();

                SwingUtilities.invokeLater(() ->
                    statusLbl.setText("🤖 Đang nhận diện khuôn mặt..."));

                String result = callRecognizeAPI(imgBytes);

                SwingUtilities.invokeLater(() -> {
                    running.set(false);

                    // ── Lưu CSV — chỉ lưu 1 lần / ngày ──
                    try {
                        boolean daDiemDanhLai = false;
                        if (java.nio.file.Files.exists(csvPath)) {
                            daDiemDanhLai = java.nio.file.Files.readAllLines(csvPath)
                                .stream()
                                .anyMatch(line -> line.startsWith(myName + ",")
                                               && line.contains(today));
                        }

                        if (daDiemDanhLai) {
                            statusLbl.setForeground(DANGER);
                            statusLbl.setText("⚠️ Đã điểm danh hôm nay rồi!");
                            appendChat("⚠️ [Điểm danh] " + myName
                                + " đã điểm danh hôm nay rồi!\n", DANGER, true);
                        } else {
                            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(new java.util.Date());
                            java.nio.file.Files.writeString(
                                csvPath,
                                myName + "," + ts + ",Có mặt\n",
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND);

                            statusLbl.setForeground(NEON_GREEN);
                            statusLbl.setText("✅ " + result);
                            appendChat("[Điểm danh AI] " + result + "\n", NEON_GREEN, true);
                            appendChat("💾 Đã lưu điểm danh vào Desktop/diemdanh.csv\n",
                                NEON_CYAN, false);
                        }
                    } catch (Exception ex) {
                        appendChat("❌ Lỗi lưu file: " + ex.getMessage() + "\n", DANGER, false);
                    }

                    javax.swing.Timer t = new javax.swing.Timer(2000,
                        ev -> dialog.dispose());
                    t.setRepeats(false); t.start();
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(DANGER);
                    statusLbl.setText("❌ Lỗi: " + ex.getMessage());
                    btnCapture.setEnabled(true);
                });
            }
        }).start();
    });

    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {
            running.set(false);
            dialog.dispose();
        }
    });

    dialog.setVisible(true);
}

//================================================================
//HELPER - Goi API dang ky
//================================================================
private String callFaceAPI(String endpoint, String mssv, String hoten, byte[] imgBytes) throws Exception {
 String boundary = "----Boundary" + System.currentTimeMillis();
 java.net.URL url = new java.net.URL(
     "http://localhost:8000" + endpoint + "?mssv=" + mssv + "&hoten=" + java.net.URLEncoder.encode(hoten, "UTF-8"));
 java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
 conn.setRequestMethod("POST");
 conn.setDoOutput(true);
 conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

 try (java.io.OutputStream os = conn.getOutputStream()) {
     // Part: file
     String partHeader = "--" + boundary + "\r\n"
         + "Content-Disposition: form-data; name=\"file\"; filename=\"face.jpg\"\r\n"
         + "Content-Type: image/jpeg\r\n\r\n";
     os.write(partHeader.getBytes());
     os.write(imgBytes);
     os.write(("\r\n--" + boundary + "--\r\n").getBytes());
 }

 java.io.InputStream is = conn.getResponseCode() == 200
     ? conn.getInputStream() : conn.getErrorStream();
 String resp = new String(is.readAllBytes(), "UTF-8");

 // Parse message tu JSON don gian
 if (resp.contains("\"message\"")) {
     int i = resp.indexOf("\"message\"") + 11;
     int j = resp.indexOf("\"", i + 1);
     return resp.substring(i + 1, j);
 }
 return resp;
}

//================================================================
//HELPER - Goi API nhan dien
//================================================================
private String callRecognizeAPI(byte[] imgBytes) throws Exception {
 String boundary = "----Boundary" + System.currentTimeMillis();
 java.net.URL url = new java.net.URL("http://localhost:8000/nhandien");
 java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
 conn.setRequestMethod("POST");
 conn.setDoOutput(true);
 conn.setConnectTimeout(10000);
 conn.setReadTimeout(30000);
 conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

 try (java.io.OutputStream os = conn.getOutputStream()) {
     String partHeader = "--" + boundary + "\r\n"
         + "Content-Disposition: form-data; name=\"file\"; filename=\"face.jpg\"\r\n"
         + "Content-Type: image/jpeg\r\n\r\n";
     os.write(partHeader.getBytes());
     os.write(imgBytes);
     os.write(("\r\n--" + boundary + "--\r\n").getBytes());
 }

 java.io.InputStream is = conn.getResponseCode() == 200
     ? conn.getInputStream() : conn.getErrorStream();
 String resp = new String(is.readAllBytes(), "UTF-8");

 if (resp.contains("\"hoten\"")) {
     int i = resp.indexOf("\"hoten\"") + 9;
     int j = resp.indexOf("\"", i + 1);
     String hoten = resp.substring(i + 1, j);
     return "Xin chào " + hoten + " — đã điểm danh!";
 }
 return "Không nhận ra khuôn mặt";
}
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientUI::new);
    }
}