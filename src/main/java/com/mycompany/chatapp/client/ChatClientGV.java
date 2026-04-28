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
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

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
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatDarkLaf;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.ds.buildin.WebcamDefaultDriver;
import com.mycompany.chatapp.helper.ImageHandler;
import com.mycompany.chatapp.helper.VoiceMessageHelper;
import com.mycompany.chatapp.service.DBHelper;

public class ChatClientGV {

    static {
        System.setProperty("bridj.platform.library", "apple_universal");
        Webcam.setDriver(new WebcamDefaultDriver());
    }

    // ── Palette ──
    private static final Color BG_DARKEST  = new Color(10, 12, 18);
    private static final Color BG_DARK     = new Color(15, 18, 28);
    private static final Color BG_PANEL    = new Color(20, 24, 38);
    private static final Color BG_SIDEBAR  = new Color(13, 16, 26);
    private static final Color BG_INPUT    = new Color(25, 30, 48);
    private static final Color BG_CARD     = new Color(28, 34, 52);
    private static final Color NEON_GREEN  = new Color(0, 255, 136);
    private static final Color NEON_CYAN   = new Color(0, 212, 255);
    private static final Color NEON_PURPLE = new Color(130, 80, 255);
    private static final Color NEON_ORANGE = new Color(255, 160, 0);
    private static final Color TEXT_PRIMARY= new Color(220, 230, 255);
    private static final Color TEXT_MUTED  = new Color(100, 120, 160);
    private static final Color DANGER      = new Color(255, 60, 80);

    // ── State ──
    private Socket socket;
    private DataInputStream  dataIn;
    private DataOutputStream dataOut;
    private PrintWriter      out;
    private String myName    = "";
    private String currentRoom = "general";

    // Điểm danh
    private String  attendanceCode    = "";   // mã 6 số hiện tại
    private long    codeExpireTime    = 0;    // thời điểm hết hạn (ms)
    private final Set<String>       checkedIn     = new LinkedHashSet<>(); // SV đã điểm danh
    private final List<String>      classList     = new ArrayList<>();     // danh sách lớp
    private boolean roomLocked = false;

    private final VoiceMessageHelper voiceHelper = new VoiceMessageHelper();

    // ── UI ──
    private final JFrame         loginFrame  = new JFrame("GV Login");
    private final JTextField     userField   = new JTextField(16);
    private final JPasswordField passField   = new JPasswordField(16);
    private final JLabel         loginStatus = new JLabel(" ", SwingConstants.CENTER);

    private final JFrame     chatFrame  = new JFrame("Chat GV - Discord Clone");
    private final JTextPane  chatPane   = new JTextPane();
    private final JTextField inputField = new JTextField();
    private final JLabel     statusBar  = new JLabel(" ");
    private final JLabel     roomHeader = new JLabel("# general");
    private final DefaultListModel<String> memberModel = new DefaultListModel<>();
    private final JList<String>           memberList  = new JList<>(memberModel);

    // Điểm danh UI
    private JLabel     codeLabel;      // hiển thị mã 6 số lớn
    private JLabel     timerLabel;     // đếm ngược
    private JLabel     countLabel;     // số SV đã điểm danh
    private DefaultTableModel attendTable; // bảng danh sách
    private Timer      countdownTimer;
    private JPanel     qrPanel;        // vẽ QR giả

    public ChatClientGV() {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception e) {}
        buildLoginUI();
    }

    // ══════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════
    private JButton neonBtn(String text, Color neon) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? neon.darker() : BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(neon); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(neon); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleField(JTextField f) {
        f.setBackground(BG_INPUT); f.setForeground(TEXT_PRIMARY); f.setCaretColor(NEON_GREEN);
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(new Color(40,55,90), 8), new EmptyBorder(10,12,10,12)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    }

    private JLabel avatarLabel(String name, int size, Color color) {
        return new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color); g2.fillOval(0, 0, size, size);
                g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.BOLD, size/2));
                String lt = name.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(name.charAt(0)));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(lt, (size-fm.stringWidth(lt))/2, (size+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
        };
    }

    private JButton iconBtn(String icon, String tip) {
        JButton b = new JButton(icon);
        b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(36, 36));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(NEON_PURPLE); }
            public void mouseExited(MouseEvent e)  { b.setForeground(null); }
        });
        return b;
    }

    // ══════════════════════════════════════════
    // LOGIN UI
    // ══════════════════════════════════════════
    void buildLoginUI() {
        loginFrame.setSize(440, 560);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.getContentPane().setBackground(BG_DARKEST);
        loginFrame.setLayout(new GridBagLayout());

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_PANEL); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                GradientPaint gp = new GradientPaint(0, 0, NEON_PURPLE, getWidth(), 0, NEON_CYAN);
                g2.setPaint(gp); g2.setStroke(new BasicStroke(2.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 20, 20);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(40, 40, 40, 40));
        card.setPreferredSize(new Dimension(360, 480));

        JLabel logo  = new JLabel("👨‍🏫");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 44));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Cổng Giảng Viên");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY); title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Đăng nhập tài khoản giảng viên");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(TEXT_MUTED); sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        styleField(userField); styleField(passField);
        userField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblU = new JLabel("TÊN ĐĂNG NHẬP");
        lblU.setFont(new Font("SansSerif", Font.BOLD, 10)); lblU.setForeground(TEXT_MUTED);
        lblU.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblP = new JLabel("MẬT KHẨU");
        lblP.setFont(new Font("SansSerif", Font.BOLD, 10)); lblP.setForeground(TEXT_MUTED);
        lblP.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginStatus.setForeground(DANGER); loginStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton loginBtn = neonBtn("Đăng nhập", NEON_PURPLE);
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(logo); card.add(Box.createVerticalStrut(8));
        card.add(title); card.add(Box.createVerticalStrut(4));
        card.add(sub); card.add(Box.createVerticalStrut(28));
        card.add(lblU); card.add(Box.createVerticalStrut(6));
        card.add(userField); card.add(Box.createVerticalStrut(14));
        card.add(lblP); card.add(Box.createVerticalStrut(6));
        card.add(passField); card.add(Box.createVerticalStrut(22));
        card.add(loginBtn); card.add(Box.createVerticalStrut(10));
        card.add(loginStatus);

        loginFrame.add(card);
        loginFrame.setVisible(true);

        Runnable doLogin = () -> doAuth();
        loginBtn.addActionListener(e -> doLogin.run());
        passField.addActionListener(e -> doLogin.run());
    }

    // ══════════════════════════════════════════
    // AUTH (kết nối server, đăng nhập với prefix GV_)
    // ══════════════════════════════════════════
    void doAuth() {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            loginStatus.setText("Vui lòng nhập đủ thông tin!"); return;
        }

        new Thread(() -> {
            try {
                socket  = new Socket("127.0.0.1", 9999);
                dataIn  = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream()));
                dataOut = new DataOutputStream(new java.io.BufferedOutputStream(socket.getOutputStream()));
                out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String header = dataIn.readUTF();
                if (!header.contains("AUTH_START")) {
                    SwingUtilities.invokeLater(() -> loginStatus.setText("Server lỗi!"));
                    return;
                }

                // Prefix GV_ để phân biệt với sinh viên
                // Server expect: TEXT:LOGIN:username:password
                String gvUsername = "GV_" + user;
                dataOut.writeUTF("TEXT:LOGIN:" + gvUsername + ":" + pass);
                dataOut.flush();

                String resp = dataIn.readUTF();
                if (resp.startsWith("TEXT:")) resp = resp.substring(5).trim();

                if (resp.startsWith("AUTH_OK:")) {
                    myName = gvUsername;
                    dataIn.readUTF(); // CHOOSE_ROOM

                    String inputRoom = JOptionPane.showInputDialog(loginFrame,
                        "Nhập tên lớp / phòng dạy (vd: 23DTH1B):",
                        "Chọn lớp", JOptionPane.QUESTION_MESSAGE);
                    currentRoom = (inputRoom == null || inputRoom.trim().isEmpty()) ? "general" : inputRoom.trim();

                    dataOut.writeUTF("TEXT:" + currentRoom);
                    dataOut.flush();
                    dataIn.readUTF(); // APPROVED

                    // Load danh sách SV của lớp
                    loadClassList(currentRoom);

                    SwingUtilities.invokeLater(() -> {
                        loginFrame.setVisible(false);
                        buildChatUI();
                    });
                } else {
                    SwingUtilities.invokeLater(() ->
                        loginStatus.setText("Sai tài khoản hoặc mật khẩu!"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() ->
                    loginStatus.setText("Không thể kết nối Server!"));
            }
        }).start();
    }

    // Load danh sách SV từ DATABASE
    private void loadClassList(String room) {
        classList.clear();
        String className = room.startsWith("#") ? room.substring(1) : room;
        java.util.List<String[]> members = DBHelper.getMembersOfClass(className);
        for (String[] m : members) {
            classList.add(m[0] + " - " + m[1]); // MSSV - TenSV
        }
    }

    // ══════════════════════════════════════════
    // CHAT UI
    // ══════════════════════════════════════════
    void buildChatUI() {
        chatFrame.setSize(1200, 750);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setLocationRelativeTo(null);
        chatFrame.getContentPane().setBackground(BG_DARKEST);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARKEST);

        root.add(buildSidebar(),       BorderLayout.WEST);
        root.add(buildCenterPanel(),   BorderLayout.CENTER);
        root.add(buildMemberSidebar(), BorderLayout.EAST);

        chatFrame.add(root);
        chatFrame.setVisible(true);
        setupNetworking();
    }

    // ── Left Sidebar ──
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));

        // Server name
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG_SIDEBAR);
        top.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0, new Color(130,80,255,60)),
            new EmptyBorder(14,16,14,16)));
        JLabel sLbl = new JLabel("◈  Cổng Giảng Viên");
        sLbl.setFont(new Font("SansSerif", Font.BOLD, 14)); sLbl.setForeground(NEON_PURPLE);
        top.add(sLbl, BorderLayout.WEST);

        // Room
        JPanel roomInfo = new JPanel();
        roomInfo.setLayout(new BoxLayout(roomInfo, BoxLayout.Y_AXIS));
        roomInfo.setBackground(BG_SIDEBAR); roomInfo.setBorder(new EmptyBorder(12,10,10,10));

        JLabel secLbl = new JLabel("  LỚP ĐANG DẠY");
        secLbl.setFont(new Font("SansSerif", Font.BOLD, 10)); secLbl.setForeground(TEXT_MUTED);
        roomInfo.add(secLbl); roomInfo.add(Box.createVerticalStrut(6));

        JPanel roomItem = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        roomItem.setBackground(new Color(130,80,255,20));
        roomItem.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        roomItem.setBorder(new RoundedBorder(NEON_PURPLE.darker(), 6));
        JLabel rIcon = new JLabel("📚"); rIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        roomHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        roomHeader.setForeground(TEXT_PRIMARY); roomHeader.setText(currentRoom);
        roomItem.add(rIcon); roomItem.add(roomHeader);
        roomInfo.add(roomItem);

        sidebar.add(top, BorderLayout.NORTH);
        sidebar.add(roomInfo, BorderLayout.CENTER);

        // User info bottom
        JPanel userInfo = new JPanel(new BorderLayout(10, 0));
        userInfo.setBackground(new Color(10,13,22)); userInfo.setBorder(new EmptyBorder(10,12,10,12));
        JLabel ava = avatarLabel(myName, 36, NEON_PURPLE);
        JPanel nameCol = new JPanel(); nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS)); nameCol.setOpaque(false);
        JLabel nLbl = new JLabel(myName); nLbl.setFont(new Font("SansSerif", Font.BOLD, 13)); nLbl.setForeground(TEXT_PRIMARY);
        JLabel sLbl2 = new JLabel("● Giảng viên"); sLbl2.setFont(new Font("SansSerif", Font.PLAIN, 11)); sLbl2.setForeground(NEON_PURPLE);
        nameCol.add(nLbl); nameCol.add(sLbl2);
        JButton logoutBtn = new JButton("⏏");
        logoutBtn.setFont(new Font("SansSerif", Font.BOLD, 16)); logoutBtn.setForeground(DANGER);
        logoutBtn.setContentAreaFilled(false); logoutBtn.setBorderPainted(false);
        logoutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> {
            try { if (socket != null) socket.close(); } catch (IOException ex) {}
            chatFrame.dispose(); loginFrame.setVisible(true);
        });
        userInfo.add(ava, BorderLayout.WEST);
        userInfo.add(nameCol, BorderLayout.CENTER);
        userInfo.add(logoutBtn, BorderLayout.EAST);
        sidebar.add(userInfo, BorderLayout.SOUTH);
        return sidebar;
    }

    // ── Center Panel: Header + GV Toolbar + Chat + Input ──
    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(BG_DARK);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0, new Color(30,40,70)),
            new EmptyBorder(12,20,12,20)));
        JPanel hLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); hLeft.setOpaque(false);
        JLabel hHash = new JLabel("#"); hHash.setFont(new Font("SansSerif", Font.BOLD, 18)); hHash.setForeground(NEON_PURPLE);
        JLabel hRoom = new JLabel(currentRoom); hRoom.setFont(new Font("SansSerif", Font.BOLD, 16)); hRoom.setForeground(TEXT_PRIMARY);
        hLeft.add(hHash); hLeft.add(hRoom);
        statusBar.setText("  " + myName + "  ●  Giảng viên - Trực tuyến");
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 12)); statusBar.setForeground(NEON_PURPLE);
        header.add(hLeft, BorderLayout.WEST); header.add(statusBar, BorderLayout.EAST);

        // GV Toolbar - thanh công cụ riêng cho GV
        JPanel gvToolbar = buildGVToolbar();

        // Chat pane
        chatPane.setBackground(BG_DARK); chatPane.setForeground(TEXT_PRIMARY);
        chatPane.setEditable(false); chatPane.setFont(new Font("SansSerif", Font.PLAIN, 14));
        chatPane.setBorder(new EmptyBorder(10,10,10,10));
        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(null); scroll.getViewport().setBackground(BG_DARK);

        // Input bar
        JPanel inputBar = buildInputBar();

        // Ghép lại: header + toolbar + chat + input
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.setOpaque(false);
        topArea.add(header, BorderLayout.NORTH);
        topArea.add(gvToolbar, BorderLayout.SOUTH);

        center.add(topArea,   BorderLayout.NORTH);
        center.add(scroll,    BorderLayout.CENTER);
        center.add(inputBar,  BorderLayout.SOUTH);
        return center;
    }

    // ── GV Toolbar (điểm danh + thông báo + khóa chat) ──
    private JPanel buildGVToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bar.setBackground(new Color(20, 24, 38));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(130, 80, 255, 60)),
            new EmptyBorder(2, 10, 2, 10)));

        JLabel gvLbl = new JLabel("🛠 CÔNG CỤ GV:");
        gvLbl.setFont(new Font("SansSerif", Font.BOLD, 10)); gvLbl.setForeground(TEXT_MUTED);

        JButton btnDiemDanh  = neonBtn("📊 Điểm danh", NEON_PURPLE);
        JButton btnThongBao  = neonBtn("📢 Thông báo", NEON_ORANGE);
        JButton btnKhoaChat  = neonBtn("🔒 Khóa Chat", DANGER);
        JButton btnXuatFile  = neonBtn("💾 Xuất danh sách", NEON_GREEN);

        for (JButton b : new JButton[]{btnDiemDanh, btnThongBao, btnKhoaChat, btnXuatFile})
            b.setPreferredSize(new Dimension(150, 30));

        // Điểm danh → mở dialog
        btnDiemDanh.addActionListener(e -> showAttendanceDialog());

        // Thông báo toàn phòng
        btnThongBao.addActionListener(e -> {
            String msg = JOptionPane.showInputDialog(chatFrame,
                "Nhập thông báo gửi toàn phòng:", "Gửi thông báo", JOptionPane.QUESTION_MESSAGE);
            if (msg != null && !msg.trim().isEmpty()) {
                String fullMsg = "TEXT:📢 [THÔNG BÁO - " + myName + "]: " + msg.trim();
                try { dataOut.writeUTF(fullMsg); dataOut.flush(); } catch (IOException ex) {}
                appendChat("\n📢 [Tôi - Thông báo]: " + msg.trim() + "\n", NEON_ORANGE, true);
            }
        });

        // Khóa / Mở chat
        btnKhoaChat.addActionListener(e -> {
            roomLocked = !roomLocked;
            String cmd = roomLocked ? "TEXT:🔒 [GV] Phòng chat đã bị khóa bởi giảng viên."
                                    : "TEXT:🔓 [GV] Phòng chat đã được mở lại.";
            try { dataOut.writeUTF(cmd); dataOut.flush(); } catch (IOException ex) {}
            btnKhoaChat.setText(roomLocked ? "🔓 Mở Chat" : "🔒 Khóa Chat");
            appendChat(roomLocked ? "\n🔒 Bạn đã khóa phòng chat.\n" : "\n🔓 Bạn đã mở phòng chat.\n",
                roomLocked ? DANGER : NEON_GREEN, true);
        });

        // Xuất danh sách điểm danh
        btnXuatFile.addActionListener(e -> exportAttendance());

        bar.add(gvLbl); bar.add(btnDiemDanh); bar.add(btnThongBao);
        bar.add(btnKhoaChat); bar.add(btnXuatFile);
        return bar;
    }

    // ══════════════════════════════════════════
    // DIALOG ĐIỂM DANH - Trái tim của tính năng
    // ══════════════════════════════════════════
    void showAttendanceDialog() {
        JDialog dialog = new JDialog(chatFrame, "📊 Điểm danh lớp " + currentRoom, false);
        dialog.setSize(820, 620);
        dialog.setLocationRelativeTo(chatFrame);
        dialog.getContentPane().setBackground(BG_DARK);
        dialog.setLayout(new BorderLayout(10, 10));

        // ── Phần trên: QR + Mã số ──
        JPanel topPanel = new JPanel(new BorderLayout(20, 0));
        topPanel.setBackground(BG_PANEL);
        topPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // QR Panel (vẽ giả QR pattern bằng đồ họa)
        qrPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (attendanceCode.isEmpty()) {
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                    g2.drawString("Bấm TẠO MÃ để bắt đầu", 20, getHeight()/2);
                    g2.dispose(); return;
                }

                // Vẽ QR giả từ mã code (pattern đơn giản dựa trên ký tự)
                int cellSize = 8;
                int cols = (getWidth()-20) / cellSize;
                int rows = (getHeight()-20) / cellSize;
                g2.setColor(Color.WHITE);
                g2.fillRect(8, 8, getWidth()-16, getHeight()-16);

                // Tạo pattern từ hash của mã
                Random rand = new Random(attendanceCode.hashCode());
                g2.setColor(BG_DARKEST);
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (rand.nextBoolean()) {
                            g2.fillRect(10 + c*cellSize, 10 + r*cellSize, cellSize-1, cellSize-1);
                        }
                    }
                }
                // 3 góc định vị QR
                for (int[] corner : new int[][]{{0,0},{cols-7,0},{0,rows-7}}) {
                    int cx = 10 + corner[0]*cellSize, cy = 10 + corner[1]*cellSize;
                    g2.setColor(BG_DARKEST); g2.fillRect(cx, cy, 7*cellSize, 7*cellSize);
                    g2.setColor(Color.WHITE); g2.fillRect(cx+cellSize, cy+cellSize, 5*cellSize, 5*cellSize);
                    g2.setColor(BG_DARKEST); g2.fillRect(cx+2*cellSize, cy+2*cellSize, 3*cellSize, 3*cellSize);
                }
                // Viền neon
                g2.setColor(NEON_PURPLE); g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(4, 4, getWidth()-8, getHeight()-8, 10, 10);
                g2.dispose();
            }
        };
        qrPanel.setBackground(Color.WHITE);
        qrPanel.setPreferredSize(new Dimension(220, 220));
        qrPanel.setBorder(BorderFactory.createLineBorder(NEON_PURPLE, 2));

        // Phần mã số + timer
        JPanel codePanel = new JPanel();
        codePanel.setLayout(new BoxLayout(codePanel, BoxLayout.Y_AXIS));
        codePanel.setBackground(BG_PANEL);

        JLabel infoLbl = new JLabel("Mã điểm danh:");
        infoLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        infoLbl.setForeground(TEXT_MUTED); infoLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Mã 6 số TO, BẮT MẮT
        codeLabel = new JLabel("------") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Vẽ nền card cho mã
                g2.setColor(BG_CARD); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(NEON_PURPLE); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 12, 12);
                // Vẽ từng chữ số
                String code = getText();
                g2.setFont(new Font("Monospaced", Font.BOLD, 42));
                FontMetrics fm = g2.getFontMetrics();
                int totalW = fm.stringWidth(code) + (code.length()-1)*8;
                int startX = (getWidth() - totalW) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                for (int i = 0; i < code.length(); i++) {
                    Color charColor = (i < 3) ? NEON_CYAN : NEON_GREEN;
                    g2.setColor(charColor);
                    String ch = String.valueOf(code.charAt(i));
                    g2.drawString(ch, startX, y);
                    startX += fm.stringWidth(ch) + 8;
                }
                g2.dispose();
            }
        };
        codeLabel.setFont(new Font("Monospaced", Font.BOLD, 42));
        codeLabel.setForeground(NEON_GREEN);
        codeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        codeLabel.setPreferredSize(new Dimension(280, 80));
        codeLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // Timer countdown
        timerLabel = new JLabel("⏱ Chờ tạo mã...");
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        timerLabel.setForeground(NEON_ORANGE); timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Số người đã điểm danh
        countLabel = new JLabel("✅ 0 / " + classList.size() + " sinh viên");
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        countLabel.setForeground(NEON_GREEN); countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Nút tạo mã mới
        JButton btnGenCode = neonBtn("🔄 Tạo mã mới (5 phút)", NEON_PURPLE);
        btnGenCode.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnGenCode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnGenCode.addActionListener(e -> generateNewCode(dialog));

        // Nút SV nhập mã (GV cũng có thể điểm danh thủ công)
        JButton btnManual = neonBtn("✍ Điểm danh thủ công", NEON_CYAN);
        btnManual.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnManual.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnManual.addActionListener(e -> manualCheckIn());

        codePanel.add(Box.createVerticalStrut(10));
        codePanel.add(infoLbl); codePanel.add(Box.createVerticalStrut(10));
        codePanel.add(codeLabel); codePanel.add(Box.createVerticalStrut(12));
        codePanel.add(timerLabel); codePanel.add(Box.createVerticalStrut(8));
        codePanel.add(countLabel); codePanel.add(Box.createVerticalStrut(16));
        codePanel.add(btnGenCode); codePanel.add(Box.createVerticalStrut(8));
        codePanel.add(btnManual);

        topPanel.add(qrPanel, BorderLayout.WEST);
        topPanel.add(codePanel, BorderLayout.CENTER);

        // ── Bảng danh sách điểm danh ──
        String[] cols = {"MSSV / Tên SV", "Thời gian điểm danh", "Trạng thái"};
        attendTable = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        // Thêm tất cả SV vào bảng (ban đầu là "Vắng")
        for (String sv : classList) {
            attendTable.addRow(new Object[]{sv, "--:--:--", "❌ Vắng"});
        }

        JTable table = new JTable(attendTable);
        table.setBackground(BG_PANEL); table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(new Color(130,80,255,40));
        table.setGridColor(new Color(30,45,80)); table.setRowHeight(30);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setBackground(BG_DARKEST);
        table.getTableHeader().setForeground(NEON_PURPLE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        // Custom render cho cột Trạng thái
        table.getColumnModel().getColumn(2).setCellRenderer((tbl, val, sel, focus, row, col) -> {
            JLabel lbl = new JLabel(val.toString());
            lbl.setOpaque(true);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            lbl.setBackground(sel ? new Color(130,80,255,40) : BG_PANEL);
            lbl.setForeground(val.toString().contains("✅") ? NEON_GREEN : DANGER);
            lbl.setBorder(new EmptyBorder(0, 8, 0, 0));
            return lbl;
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder(
            new RoundedBorder(NEON_PURPLE, 8), "  Danh sách lớp " + currentRoom,
            0, 0, new Font("SansSerif", Font.BOLD, 11), NEON_PURPLE));
        tableScroll.getViewport().setBackground(BG_PANEL);

        // Bottom buttons
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        bottomBar.setBackground(BG_DARK);
        JButton btnClose = neonBtn("Đóng", DANGER);
        btnClose.setPreferredSize(new Dimension(100, 36));
        btnClose.addActionListener(e -> { stopCountdown(); dialog.dispose(); });
        bottomBar.add(btnClose);

        dialog.add(topPanel,    BorderLayout.NORTH);
        dialog.add(tableScroll, BorderLayout.CENTER);
        dialog.add(bottomBar,   BorderLayout.SOUTH);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { stopCountdown(); }
        });
        dialog.setVisible(true);
    }

    // Tạo mã điểm danh mới
    private void generateNewCode(JDialog dialog) {
        stopCountdown();

        // Tạo mã 6 số ngẫu nhiên
        Random rand = new Random();
        attendanceCode = String.format("%06d", rand.nextInt(1000000));
        codeExpireTime = System.currentTimeMillis() + 5 * 60 * 1000; // 5 phút

        // Cập nhật UI
        codeLabel.setText(attendanceCode);
        qrPanel.repaint();

        // Phát mã lên phòng chat để SV nhìn thấy (SV dùng lệnh /attend XXXXXX)
        try {
            dataOut.writeUTF("TEXT:📊 [ĐIỂM DANH] GV đã mở điểm danh! Mã: " + attendanceCode
                + " (hết hạn sau 5 phút). Nhập /attend " + attendanceCode + " để điểm danh.");
            dataOut.flush();
        } catch (IOException ex) {}

        appendChat("\n📊 Đã tạo mã điểm danh: " + attendanceCode + "\n", NEON_PURPLE, true);

        // Countdown timer
        final int[] secondsLeft = {300}; // 5 phút
        countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                secondsLeft[0]--;
                int m = secondsLeft[0] / 60, s = secondsLeft[0] % 60;
                String timeStr = String.format("⏱ Còn: %02d:%02d", m, s);
                Color tColor = secondsLeft[0] > 60 ? NEON_GREEN :
                               secondsLeft[0] > 30 ? NEON_ORANGE : DANGER;
                SwingUtilities.invokeLater(() -> {
                    if (timerLabel != null) {
                        timerLabel.setText(timeStr);
                        timerLabel.setForeground(tColor);
                    }
                });
                if (secondsLeft[0] <= 0) {
                    attendanceCode = "";
                    SwingUtilities.invokeLater(() -> {
                        if (timerLabel != null) {
                            timerLabel.setText("❌ Mã đã hết hạn!");
                            timerLabel.setForeground(DANGER);
                        }
                        if (codeLabel != null) codeLabel.setText("------");
                        if (qrPanel != null) qrPanel.repaint();
                    });
                    countdownTimer.cancel();
                }
            }
        }, 1000, 1000);
    }

    private void stopCountdown() {
        if (countdownTimer != null) { countdownTimer.cancel(); countdownTimer = null; }
    }

    // Điểm danh thủ công (GV gọi tên)
    private void manualCheckIn() {
        if (attendTable == null) return;
        String input = JOptionPane.showInputDialog(chatFrame,
            "Nhập MSSV hoặc tên SV cần điểm danh:", "Điểm danh thủ công", JOptionPane.QUESTION_MESSAGE);
        if (input == null || input.trim().isEmpty()) return;
        String sv = input.trim();
        processCheckIn(sv + " (thủ công)");
    }

    // Xử lý khi SV điểm danh thành công
    private void processCheckIn(String svName) {
        if (checkedIn.contains(svName)) return;
        checkedIn.add(svName);
        String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date());

        SwingUtilities.invokeLater(() -> {
            // Cập nhật bảng
            if (attendTable != null) {
                // Tìm SV trong bảng và cập nhật
                boolean found = false;
                for (int i = 0; i < attendTable.getRowCount(); i++) {
                    String rowVal = attendTable.getValueAt(i, 0).toString();
                    if (rowVal.toLowerCase().contains(svName.toLowerCase().replace(" (thủ công)", ""))) {
                        attendTable.setValueAt(timeStr, i, 1);
                        attendTable.setValueAt("✅ Có mặt", i, 2);
                        found = true; break;
                    }
                }
                // Nếu không tìm thấy trong danh sách, thêm dòng mới
                if (!found) {
                    attendTable.addRow(new Object[]{svName, timeStr, "✅ Có mặt"});
                }
            }
            // Cập nhật count
            if (countLabel != null)
                countLabel.setText("✅ " + checkedIn.size() + " / " + Math.max(classList.size(), checkedIn.size()) + " sinh viên");

            appendChat("✅ " + svName + " đã điểm danh lúc " + timeStr + "\n", NEON_GREEN, false);
        });
    }

    // Xuất danh sách điểm danh ra file
    private void exportAttendance() {
        JFileChooser saver = new JFileChooser();
        saver.setSelectedFile(new File("diemdanh_" + currentRoom + "_"
            + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".txt"));
        if (saver.showSaveDialog(chatFrame) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(saver.getSelectedFile()))) {
            pw.println("=== DANH SÁCH ĐIỂM DANH ===");
            pw.println("Lớp: " + currentRoom);
            pw.println("GV: " + myName);
            pw.println("Thời gian: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
            pw.println("---------------------------");
            pw.printf("%-30s %-12s %-15s%n", "HỌ TÊN / MSSV", "GIỜ VÀO", "TRẠNG THÁI");
            pw.println("---------------------------");
            if (attendTable != null) {
                for (int i = 0; i < attendTable.getRowCount(); i++) {
                    pw.printf("%-30s %-12s %-15s%n",
                        attendTable.getValueAt(i, 0),
                        attendTable.getValueAt(i, 1),
                        attendTable.getValueAt(i, 2));
                }
            }
            pw.println("---------------------------");
            pw.println("Tổng có mặt: " + checkedIn.size());
            JOptionPane.showMessageDialog(chatFrame, "✅ Đã xuất file thành công!\n" + saver.getSelectedFile().getPath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(chatFrame, "Lỗi xuất file: " + e.getMessage());
        }
    }

    // ── Input Bar ──
    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 8));
        bar.setBackground(BG_DARK); bar.setBorder(new EmptyBorder(10,16,16,16));

        JPanel inner = new JPanel(new BorderLayout(8, 0));
        inner.setBackground(BG_INPUT);
        inner.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(new Color(130,80,255,80), 12), new EmptyBorder(4,8,4,8)));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 4));
        tools.setOpaque(false);
        JButton btnFile  = iconBtn("📁", "Gửi file / bài giảng");
        JButton btnImage = iconBtn("🖼️", "Gửi ảnh");
        JButton btnVoice = iconBtn("🎤", "Voice");

        btnFile.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(chatFrame) == JFileChooser.APPROVE_OPTION) {
                File sel = fc.getSelectedFile();
                new Thread(() -> {
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(sel.toPath());
                        dataOut.writeUTF("FILE_MSG:" + myName + ":" + sel.getName());
                        dataOut.writeInt(bytes.length); dataOut.write(bytes); dataOut.flush();
                        appendChat("[Tôi] 📁 Đã gửi: " + sel.getName() + "\n", TEXT_MUTED, false);
                    } catch (IOException ex) { ex.printStackTrace(); }
                }).start();
            }
        });
        btnImage.addActionListener(e -> {
            byte[] imgBytes = ImageHandler.pickImageBytes(chatFrame);
            if (imgBytes != null) {
                new Thread(() -> {
                    try {
                        dataOut.writeUTF("IMAGE:" + myName);
                        dataOut.writeInt(imgBytes.length); dataOut.write(imgBytes); dataOut.flush();
                        appendChat("[Tôi] 🖼️ Đã gửi ảnh\n", NEON_CYAN, false);
                    } catch (IOException ex) {}
                }).start();
            }
        });
        final boolean[] rec = {false};
        btnVoice.addActionListener(e -> {
            if (!rec[0]) {
                voiceHelper.startRecording(); rec[0] = true;
                btnVoice.setText("⏹️"); statusBar.setText("  🎤 Đang ghi âm...");
            } else {
                byte[] audio = voiceHelper.stopRecording(); rec[0] = false;
                btnVoice.setText("🎤"); statusBar.setText("  " + myName + "  ●  Giảng viên - Trực tuyến");
                if (audio != null && audio.length > 100) {
                    try {
                        dataOut.writeUTF("VOICE:" + myName);
                        dataOut.writeInt(audio.length); dataOut.write(audio); dataOut.flush();
                        appendChat("[Tôi] 🎤 Đã gửi voice\n", NEON_GREEN, false);
                    } catch (IOException ex) {}
                }
            }
        });
        tools.add(btnFile); tools.add(btnImage); tools.add(btnVoice);

        inputField.setBackground(BG_INPUT); inputField.setForeground(TEXT_PRIMARY);
        inputField.setCaretColor(NEON_PURPLE);
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputField.setBorder(new EmptyBorder(8,4,8,4));
        inputField.putClientProperty("JTextField.placeholderText", "Gửi tin nhắn tới #" + currentRoom);

        JButton sendBtn = new JButton("➤");
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 16)); sendBtn.setForeground(NEON_PURPLE);
        sendBtn.setContentAreaFilled(false); sendBtn.setBorderPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> sendMsg());
        inputField.addActionListener(e -> sendMsg());

        inner.add(tools, BorderLayout.WEST);
        inner.add(inputField, BorderLayout.CENTER);
        inner.add(sendBtn, BorderLayout.EAST);
        bar.add(inner, BorderLayout.CENTER);
        return bar;
    }

    // ── Right Sidebar (Members) ──
    private JPanel buildMemberSidebar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_SIDEBAR);
        panel.setPreferredSize(new Dimension(210, 0));
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(30, 40, 70)));


        JLabel title = new JLabel("  THÀNH VIÊN");
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        title.setForeground(TEXT_MUTED);
        title.setBorder(new EmptyBorder(14, 10, 8, 10));


        memberList.setBackground(BG_SIDEBAR); 
        memberList.setForeground(TEXT_PRIMARY);
        memberList.setFixedCellHeight(52);
        memberList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        memberList.setCellRenderer((list, val, idx, sel, focus) -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            row.setBackground(sel ? new Color(30,40,70) : BG_SIDEBAR);
            String name = val.toString();
            boolean isGV = name.startsWith("GV_");
            Color ac = isGV ? NEON_PURPLE : new Color((name.hashCode() & 0x7F7F7F) | 0x404040);
            JLabel av = avatarLabel(name.replace("GV_",""), 26, ac);
            JPanel info = new JPanel(); info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS)); info.setOpaque(false);
            JLabel nLbl = new JLabel(isGV ? "👨‍🏫 " + name.replace("GV_","") : name);
            nLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            nLbl.setForeground(isGV ? NEON_PURPLE : TEXT_PRIMARY);
            JLabel dLbl = new JLabel(isGV ? "● Giảng viên" : "● online");
            dLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            dLbl.setForeground(isGV ? NEON_PURPLE : NEON_GREEN);
            info.add(nLbl); info.add(dLbl);
            row.add(av); row.add(info);
            return row;
        });

        JScrollPane scroll = new JScrollPane(memberList);
        scroll.setBorder(null); 
        scroll.getViewport().setBackground(BG_SIDEBAR);
        panel.add(title, BorderLayout.NORTH); 
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ══════════════════════════════════════════
    // NETWORKING
    // ══════════════════════════════════════════
    private void sendMsg() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;
        try {
            dataOut.writeUTF("TEXT:" + msg);
            dataOut.flush(); inputField.setText("");
        } catch (IOException ex) {}
    }

    void setupNetworking() {
        new Thread(() -> {
            try {
                while (socket != null && !socket.isClosed()) {
                    String header = dataIn.readUTF();

                    if (header.startsWith("FILE_MSG:")) {
                        String[] parts = header.split(":");
                        String sender = parts.length > 1 ? parts[1] : "?";
                        String fn = parts.length > 2 ? parts[2] : "file";
                        int sz = dataIn.readInt();
                        byte[] data = new byte[sz]; dataIn.readFully(data);
                        final String s=sender; final String f=fn; final byte[]d=data;
                        SwingUtilities.invokeLater(() -> showDownloadButton(s, f, d));
                    }
                    else if (header.startsWith("IMAGE:")) {
                        String sender = header.substring(6);
                        int sz = dataIn.readInt(); byte[] img = new byte[sz]; dataIn.readFully(img);
                        final String s=sender; final byte[]im=img;
                        SwingUtilities.invokeLater(() -> {
                            appendChat(s + ":  \n", NEON_CYAN, true);
                            Image scaled = new ImageIcon(im).getImage().getScaledInstance(260,-1,Image.SCALE_SMOOTH);
                            chatPane.setCaretPosition(chatPane.getDocument().getLength());
                            chatPane.insertIcon(new ImageIcon(scaled));
                            appendChat("\n", TEXT_PRIMARY, false);
                        });
                    }
                    else if (header.startsWith("VOICE:")) {
                        String sender = header.substring(6);
                        int sz = dataIn.readInt(); byte[] audio = new byte[sz]; dataIn.readFully(audio);
                        final String s=sender; final byte[]au=audio;
                        SwingUtilities.invokeLater(() -> showVoiceButton(s, au));
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
                SwingUtilities.invokeLater(() -> appendChat("⚠ Mất kết nối!\n", DANGER, false));
            }
        }).start();
    }

    void handleMsg(String line) {
        // Kiểm tra SV điểm danh: "/attend XXXXXX"
        if (line.contains("/attend ")) {
            int idx = line.indexOf("/attend ");
            String code = line.substring(idx + 8).trim().split("\\s+")[0];
            // Lấy tên người gửi từ MSG:[room] sender: /attend CODE
            String sender = "?";
            if (line.startsWith("MSG:")) {
                int bEnd = line.indexOf("] "); int col = line.indexOf(": ", bEnd);
                if (bEnd >= 0 && col >= 0) sender = line.substring(bEnd+2, col);
            }
            final String sv = sender, cd = code;

            // ✅ FIX 1: Chặn GV tự điểm danh
            if (sv.startsWith("GV_") || sv.toUpperCase().startsWith("GV")) {
                try {
                    dataOut.writeUTF("TEXT:⚠ Giảng viên không thể tự điểm danh!");
                    dataOut.flush();
                } catch (IOException ex) {}
                return;
            }

            // ✅ FIX 2: Chặn điểm danh trùng
            if (checkedIn.contains(sv)) {
                try {
                    dataOut.writeUTF("TEXT:⚠ @" + sv + " đã điểm danh rồi, không thể điểm danh lại!");
                    dataOut.flush();
                } catch (IOException ex) {}
                return;
            }

            // Kiểm tra mã
            if (!attendanceCode.isEmpty() && cd.equals(attendanceCode)
                    && System.currentTimeMillis() < codeExpireTime) {
                processCheckIn(sv);
                // Gửi xác nhận cho SV
                try {
                    dataOut.writeUTF("TEXT:✅ @" + sv + " đã điểm danh thành công!");
                    dataOut.flush();
                } catch (IOException ex) {}
            } else if (!attendanceCode.isEmpty()) {
                // Sai mã hoặc hết hạn
                try {
                    dataOut.writeUTF("TEXT:❌ @" + sv + " Mã điểm danh không hợp lệ hoặc đã hết hạn!");
                    dataOut.flush();
                } catch (IOException ex) {}
            } else {
                // Chưa mở điểm danh
                try {
                    dataOut.writeUTF("TEXT:⚠ @" + sv + " Chưa có phiên điểm danh nào được mở!");
                    dataOut.flush();
                } catch (IOException ex) {}
            }
            return;
        }

        if (line.startsWith("MSG:")) {
            String content = line.substring(4);
            int bEnd = content.indexOf("] ");
            if (bEnd >= 0) {
                String rest = content.substring(bEnd+2);
                int colon = rest.indexOf(": ");
                if (colon >= 0) {
                    String sender = rest.substring(0, colon);
                    String msg    = rest.substring(colon+2);
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

    void appendBubble(String sender, String msg, boolean isMe) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            Style ns = chatPane.addStyle("n" + Math.random(), null);
            StyleConstants.setBold(ns, true); StyleConstants.setFontSize(ns, 12);
            if (isMe) {
                StyleConstants.setForeground(ns, NEON_PURPLE);
                doc.insertString(doc.getLength(), "  Tôi (GV)\n", ns);
            } else {
                StyleConstants.setForeground(ns, NEON_CYAN);
                doc.insertString(doc.getLength(), "  " + sender + "\n", ns);
            }
            Style ms = chatPane.addStyle("m" + Math.random(), null);
            StyleConstants.setForeground(ms, TEXT_PRIMARY); StyleConstants.setFontSize(ms, 14);
            doc.insertString(doc.getLength(), "  " + msg + "\n\n", ms);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {}
    }

    void appendChat(String msg, Color c, boolean bold) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            Style s = chatPane.addStyle("s", null);
            StyleConstants.setForeground(s, c); StyleConstants.setBold(s, bold);
            doc.insertString(doc.getLength(), msg, s);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {}
    }

    void showDownloadButton(String sender, String fn, byte[] data) {
        appendChat("  " + sender + " 📁 " + fn + "  ", NEON_CYAN, true);
        JButton btn = new JButton("⬇ Tải về");
        btn.setForeground(NEON_GREEN); btn.setBackground(BG_CARD);
        btn.setBorder(new RoundedBorder(NEON_GREEN, 6));
        btn.addActionListener(e -> {
            JFileChooser sv = new JFileChooser(); sv.setSelectedFile(new File(fn));
            if (sv.showSaveDialog(chatFrame) == JFileChooser.APPROVE_OPTION) {
                try (FileOutputStream fos = new FileOutputStream(sv.getSelectedFile())) {
                    fos.write(data);
                    JOptionPane.showMessageDialog(chatFrame, "✅ Đã lưu!");
                } catch (IOException ex) {}
            }
        });
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
        chatPane.insertComponent(btn);
        appendChat("\n", TEXT_PRIMARY, false);
    }

    void showVoiceButton(String sender, byte[] audio) {
        appendChat("  " + sender + " 🎤  ", NEON_GREEN, true);
        JButton btn = new JButton("▶ Nghe");
        btn.setForeground(NEON_GREEN); btn.setBackground(BG_CARD);
        btn.setBorder(new RoundedBorder(NEON_GREEN, 6));
        btn.addActionListener(ev -> VoiceMessageHelper.playAudio(audio));
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
        chatPane.insertComponent(btn);
        appendChat("\n", TEXT_PRIMARY, false);
    }
    
    class OnlineMemberRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            String name = value.toString();
            boolean isGV = name.toUpperCase().startsWith("GV_") || name.toUpperCase().startsWith("GV");

            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(true);
            row.setBackground(isSelected ? new Color(30, 40, 70) : BG_SIDEBAR);
            row.setBorder(new EmptyBorder(6, 10, 6, 10));

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
                    String dn = name.replaceAll("(?i)GV_", "");
                    String lt = dn.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(dn.charAt(0)));
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

            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setOpaque(false);

            String displayName = name.replaceAll("(?i)GV_", "");
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

    // ══════════════════════════════════════════
    // ROUNDED BORDER
    // ══════════════════════════════════════════
    static class RoundedBorder extends AbstractBorder {
        private final Color color; private final int radius;
        RoundedBorder(Color c, int r) { color=c; radius=r; }
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.drawRoundRect(x,y,w-1,h-1,radius,radius); g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c){return new Insets(radius/2,radius/2,radius/2,radius/2);}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGV::new);
    }
}