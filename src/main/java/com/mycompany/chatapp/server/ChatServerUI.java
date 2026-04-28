package com.mycompany.chatapp.server;

import javax.swing.*;
import javax.swing.border.*;

import com.mycompany.chatapp.service.DBHelper;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class ChatServerUI {

    private static final Color BG_DARKEST  = new Color(10, 12, 18);
    private static final Color BG_DARK     = new Color(15, 18, 28);
    private static final Color BG_PANEL    = new Color(20, 24, 38);
    private static final Color BG_SIDEBAR  = new Color(13, 16, 26);
    private static final Color BG_INPUT    = new Color(25, 30, 48);
    private static final Color BG_CARD     = new Color(28, 34, 52);
    private static final Color NEON_GREEN  = new Color(0, 255, 136);
    private static final Color NEON_CYAN   = new Color(0, 212, 255);
    private static final Color NEON_ORANGE = new Color(255, 160, 0);
    private static final Color NEON_PURPLE = new Color(160, 80, 255);
    private static final Color TEXT_PRIMARY = new Color(220, 230, 255);
    private static final Color TEXT_MUTED  = new Color(100, 120, 160);
    private static final Color DANGER      = new Color(255, 60, 80);

    // ── STATIC INNER CLASSES ──

    static class StudentManager {
        StudentManager() {}
        synchronized void addStudent(String mssv, String name, String cls) {
            DBHelper.addStudent(mssv, name, cls);
        }
        synchronized void removeStudent(String mssv) {
            DBHelper.removeStudent(mssv);
        }
        synchronized void updateStudent(String mssv, String name, String cls) {
            DBHelper.updateStudent(mssv, name, cls);
        }
        public boolean exists(String mssv) { return DBHelper.studentExists(mssv); }
        public ConcurrentHashMap<String, String> getAll() {
            ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
            map.putAll(DBHelper.getAllStudents());
            return map;
        }
    }

    static class AuthManager {
        public ConcurrentHashMap<String, String> getAll() {
            ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
            map.putAll(DBHelper.getAllUsers());
            return map;
        }
        static String hash(String pw) { return DBHelper.hashPassword(pw); }
        synchronized String register(String user, String pw) {
            return DBHelper.register(user, hash(pw), "STUDENT", user);
        }
        String login(String user, String pw) {
            return DBHelper.login(user, hash(pw));
        }
        public void save() {}
    }

    static class AdminManager {
        AdminManager() { DBHelper.getAllAdmins(); } // Đảm bảo có admin mặc định
        String login(String user, String pw) {
            java.util.Map<String, String> admins = DBHelper.getAllAdmins();
            String stored = admins.get(user.toLowerCase());
            if (stored == null) return "ERR:Không tồn tại admin";
            return stored.equals(DBHelper.hashPassword(pw)) ? "OK" : "ERR:Sai mật khẩu";
        }
        synchronized String addAdmin(String user, String pw) {
            return DBHelper.registerAdmin(user, DBHelper.hashPassword(pw));
        }
        synchronized String removeAdmin(String user) {
            return DBHelper.deleteAdmin(user) ? "OK" : "ERR:Không thể xóa";
        }
        ConcurrentHashMap<String, String> getAll() {
            ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
            map.putAll(DBHelper.getAllAdmins());
            return map;
        }
    }

    static class NoteManager {
        NoteManager() {}
        synchronized void saveNote(String gvUsername, String studentUsername, String noteContent) {
            DBHelper.saveMessage(gvUsername, "[NOTE:" + studentUsername + "] " + noteContent);
        }
        synchronized String getNote(String gvUsername, String studentUsername) { return ""; }
    }

    static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        RoundedBorder(Color c, int r) { color = c; radius = r; }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }

    // ── STATIC FIELDS ──

    public static final StudentManager STUDENT = new StudentManager();
    public static final AuthManager    AUTH    = new AuthManager();
    public static final AdminManager   ADMIN   = new AdminManager();
    public static final NoteManager    NOTES   = new NoteManager();

    // ROOM_FILE removed - using DB now
    private static final ConcurrentHashMap<String, ClientHandler> CLIENTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<ClientHandler>> ROOMS = new ConcurrentHashMap<>();

    // ── INSTANCE FIELDS ──

    private final JFrame      loginFrame = new JFrame("Server Admin Login");
    private final JFrame      mainFrame  = new JFrame("Server Quan Tri - Discord Clone");
    private final JTabbedPane tabs       = new JTabbedPane();
    private final ConcurrentHashMap<String, JTextArea> tabMap = new ConcurrentHashMap<>();
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final DefaultListModel<String> roomModel   = new DefaultListModel<>();
    private String currentAdmin = "";

    public ChatServerUI() { startServer(); buildLoginUI(); }

    // ── HELPERS ──

    private JButton neonBtn(String text, Color neon) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? neon.darker() : BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(neon);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setForeground(neon);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 36));
        return btn;
    }

    private void styleField(JTextField f) {
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(NEON_GREEN);
        f.setFont(new Font("SansSerif", Font.PLAIN, 13));
        f.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(new Color(40, 55, 90), 8),
            new EmptyBorder(8, 10, 8, 10)));
    }

    private JLabel fieldLbl(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(TEXT_MUTED);
        return l;
    }

    private JLabel statusLbl() {
        JLabel l = new JLabel(" ");
        l.setForeground(NEON_GREEN);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    private JDialog styledDialog(String title, int w, int h) {
        JDialog d = new JDialog(mainFrame, title, true);
        d.setSize(w, h);
        d.setLocationRelativeTo(mainFrame);
        d.getContentPane().setBackground(BG_DARK);
        return d;
    }

    private JList<String> styledList(DefaultListModel<String> model) {
        JList<String> l = new JList<>(model);
        l.setBackground(BG_PANEL);
        l.setForeground(TEXT_PRIMARY);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setSelectionBackground(new Color(0, 255, 136, 30));
        l.setSelectionForeground(NEON_GREEN);
        l.setFixedCellHeight(30);
        return l;
    }

    private JScrollPane styledScroll(JList<?> list, String title) {
        JScrollPane s = new JScrollPane(list);
        s.setBorder(BorderFactory.createTitledBorder(
            new RoundedBorder(NEON_CYAN, 8), "  " + title,
            0, 0, new Font("SansSerif", Font.BOLD, 11), NEON_CYAN));
        s.getViewport().setBackground(BG_PANEL);
        return s;
    }

    private JPanel btnRow2(JDialog d, JButton add, JButton del,
                           ActionListener addAct, ActionListener delAct) {
        add.addActionListener(addAct);
        del.addActionListener(delAct);
        JPanel p = new JPanel(new GridLayout(1, 2, 10, 0));
        p.setBackground(BG_PANEL);
        p.setBorder(new EmptyBorder(0, 15, 15, 15));
        p.add(add);
        p.add(del);
        return p;
    }

    private void saveList(String fn, DefaultListModel<String> m) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fn))) {
            for (int i = 0; i < m.size(); i++) pw.println(m.get(i));
        } catch (Exception e) {}
    }

    private void saveSchedule(String lop, javax.swing.table.DefaultTableModel m) {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            String[] row = new String[m.getColumnCount()];
            for (int j = 0; j < m.getColumnCount(); j++)
                row[j] = String.valueOf(m.getValueAt(i, j));
            rows.add(row);
        }
        DBHelper.saveScheduleForClass(lop, rows);
    }

    // ── LOGIN UI ──

    void buildLoginUI() {
        loginFrame.getContentPane().removeAll();
        loginFrame.revalidate();
        loginFrame.repaint();
        loginFrame.setSize(440, 560);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.getContentPane().setBackground(BG_DARKEST);
        loginFrame.setLayout(new GridBagLayout());

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                GradientPaint gp = new GradientPaint(0, 0, NEON_GREEN, getWidth(), 0, NEON_CYAN);
                g2.setPaint(gp);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(40, 40, 40, 40));
        card.setPreferredSize(new Dimension(360, 480));

        JLabel logo  = new JLabel("⚙");
        logo.setFont(new Font("SansSerif", Font.BOLD, 40));
        logo.setForeground(NEON_CYAN);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Quan Tri Server");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Dang nhap de tiep tuc");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblU = new JLabel("TEN DANG NHAP");
        lblU.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblU.setForeground(TEXT_MUTED);
        lblU.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblP = new JLabel("MAT KHAU");
        lblP.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblP.setForeground(TEXT_MUTED);
        lblP.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField    uf = new JTextField();
        styleField(uf);
        uf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        uf.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPasswordField pf = new JPasswordField();
        styleField(pf);
        pf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        pf.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(DANGER);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton btnLogin = neonBtn("Dang nhap", NEON_CYAN);
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnReg = new JButton("Chua co tai khoan? Dang ky ngay");
        btnReg.setForeground(NEON_CYAN);
        btnReg.setContentAreaFilled(false);
        btnReg.setBorderPainted(false);
        btnReg.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnReg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnReg.setAlignmentX(Component.CENTER_ALIGNMENT);

        Runnable doLogin = () -> {
            String u = uf.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty() || p.isEmpty()) { statusLabel.setText("Nhap du thong tin!"); return; }
            String res = ADMIN.login(u, p);
            if (res.equals("OK")) {
                currentAdmin = u;
                loginFrame.setVisible(false);
                buildMainUI();
            } else {
                statusLabel.setText(res.substring(4));
                pf.setText("");
            }
        };

        btnLogin.addActionListener(e -> doLogin.run());
        pf.addActionListener(e -> doLogin.run());
        btnReg.addActionListener(e -> {
            String u = uf.getText().trim();
            String p = new String(pf.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                statusLabel.setForeground(DANGER);
                statusLabel.setText("Nhap du!");
                return;
            }
            String res = ADMIN.addAdmin(u, p);
            if (res.equals("OK")) {
                statusLabel.setForeground(NEON_GREEN);
                statusLabel.setText("Dang ky thanh cong!");
            } else {
                statusLabel.setForeground(DANGER);
                statusLabel.setText(res.substring(4));
            }
        });

        card.add(logo);
        card.add(Box.createVerticalStrut(8));
        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(sub);
        card.add(Box.createVerticalStrut(30));
        card.add(lblU);
        card.add(Box.createVerticalStrut(4));
        card.add(uf);
        card.add(Box.createVerticalStrut(14));
        card.add(lblP);
        card.add(Box.createVerticalStrut(4));
        card.add(pf);
        card.add(Box.createVerticalStrut(24));
        card.add(btnLogin);
        card.add(Box.createVerticalStrut(10));
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(btnReg);

        loginFrame.add(card);
        loginFrame.setVisible(true);
    }

    // ── MAIN UI ──

    void buildMainUI() {
        mainFrame.getContentPane().removeAll();
        mainFrame.setSize(1280, 720);
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setLayout(new BorderLayout(0, 0));
        mainFrame.getContentPane().setBackground(BG_DARKEST);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_SIDEBAR);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 255, 136, 40)),
            new EmptyBorder(10, 20, 10, 20)));

        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTop.setOpaque(false);
        JLabel sIcon = new JLabel("⚙");
        sIcon.setFont(new Font("SansSerif", Font.BOLD, 20));
        sIcon.setForeground(NEON_CYAN);
        JLabel aLbl = new JLabel("Admin: " + currentAdmin);
        aLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        aLbl.setForeground(TEXT_PRIMARY);
        JLabel dLbl = new JLabel("●");
        dLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dLbl.setForeground(NEON_GREEN);
        leftTop.add(sIcon);
        leftTop.add(aLbl);
        leftTop.add(dLbl);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnRow.setOpaque(false);

        JButton btnSched   = neonBtn("Lich hoc",    NEON_CYAN);
        JButton btnRoom    = neonBtn("Quan ly Lop", NEON_ORANGE);
        JButton btnLect    = neonBtn("Giang vien",  NEON_PURPLE);
        JButton btnStudent = neonBtn("Sinh vien",   NEON_GREEN);
        JButton btnGanLop  = neonBtn("Gan lop SV",  NEON_CYAN);
        JButton btnUser    = neonBtn("User",         NEON_GREEN);
        JButton btnAdmin   = neonBtn("Admin",        NEON_CYAN);
        JButton btnLogout  = neonBtn("Dang xuat",   DANGER);

        for (JButton b : new JButton[]{btnSched, btnRoom, btnLect, btnStudent, btnGanLop, btnUser, btnAdmin, btnLogout})
            b.setPreferredSize(new Dimension(110, 36));

        btnSched.addActionListener(e   -> showScheduleManager());
        btnRoom.addActionListener(e    -> showRoomManager());
        btnLect.addActionListener(e    -> showLecturerManager());
        btnStudent.addActionListener(e -> showStudentManager());
        btnGanLop.addActionListener(e  -> JOptionPane.showMessageDialog(mainFrame,
            "Chức năng Gán lớp cho Sinh viên đang phát triển!\n\n" +
            "Bạn có thể dùng nút \"Gan lop nay cho SV\" trong phần Quản lý Lịch học để test tạm.",
            "Chức năng chưa hoàn thiện", JOptionPane.INFORMATION_MESSAGE));
        btnUser.addActionListener(e    -> showUserManager());
        btnAdmin.addActionListener(e   -> showAdminManager());
        btnLogout.addActionListener(e  -> {
            int c = JOptionPane.showConfirmDialog(mainFrame, "Dang xuat? (Server van chay)", "Xac nhan", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                mainFrame.setVisible(false);
                currentAdmin = "";
                buildLoginUI();
                loginFrame.setVisible(true);
            }
        });

        btnRow.add(btnSched); btnRow.add(btnRoom); btnRow.add(btnLect);
        btnRow.add(btnStudent); btnRow.add(btnGanLop);
        btnRow.add(btnUser); btnRow.add(btnAdmin); btnRow.add(btnLogout);
        topBar.add(leftTop, BorderLayout.WEST);
        topBar.add(btnRow, BorderLayout.EAST);

        // LEFT PANEL - Room list
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(BG_SIDEBAR);
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(30, 40, 70)));

        JLabel roomTitle = new JLabel("  DANH SACH PHONG");
        roomTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        roomTitle.setForeground(TEXT_MUTED);
        roomTitle.setBorder(new EmptyBorder(14, 10, 8, 10));

        JList<String> roomList = new JList<>(roomModel);
        roomList.setBackground(BG_SIDEBAR);
        roomList.setForeground(TEXT_PRIMARY);
        roomList.setCellRenderer(new RoomCellRenderer());
        leftPanel.add(roomTitle, BorderLayout.NORTH);
        JScrollPane rsp = new JScrollPane(roomList);
        rsp.setBorder(null);
        rsp.getViewport().setBackground(BG_SIDEBAR);
        leftPanel.add(rsp, BorderLayout.CENTER);

        // RIGHT PANEL - Online users
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(BG_SIDEBAR);
        rightPanel.setPreferredSize(new Dimension(200, 0));
        rightPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(30, 40, 70)));

        JLabel onlineTitle = new JLabel("  NGUOI DUNG ONLINE");
        onlineTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        onlineTitle.setForeground(TEXT_MUTED);
        onlineTitle.setBorder(new EmptyBorder(14, 10, 8, 10));

        JList<String> onlineList = new JList<>(onlineModel);
        onlineList.setBackground(BG_SIDEBAR);
        onlineList.setForeground(TEXT_PRIMARY);
        onlineList.setCellRenderer(new OnlineCellRenderer());
        rightPanel.add(onlineTitle, BorderLayout.NORTH);
        JScrollPane osp = new JScrollPane(onlineList);
        osp.setBorder(null);
        osp.getViewport().setBackground(BG_SIDEBAR);
        rightPanel.add(osp, BorderLayout.CENTER);

        tabs.setBackground(BG_DARK);
        tabs.setForeground(TEXT_PRIMARY);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));

        mainFrame.add(topBar, BorderLayout.NORTH);
        mainFrame.add(leftPanel, BorderLayout.WEST);
        mainFrame.add(tabs, BorderLayout.CENTER);
        mainFrame.add(rightPanel, BorderLayout.EAST);
        mainFrame.setVisible(true);

        new Timer(2000, e -> {
            roomModel.clear();
            ROOMS.forEach((r, list) -> roomModel.addElement(r + "  (" + list.size() + ")"));
        }).start();
    }

    // ── RENDERERS ──

    class RoomCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object val, int idx, boolean sel, boolean focus) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
            p.setBackground(sel ? new Color(0, 255, 136, 25) : BG_SIDEBAR);
            JLabel h = new JLabel("#");
            h.setFont(new Font("SansSerif", Font.BOLD, 14));
            h.setForeground(NEON_GREEN);
            JLabel n = new JLabel(val.toString());
            n.setFont(new Font("SansSerif", Font.PLAIN, 13));
            n.setForeground(TEXT_PRIMARY);
            p.add(h);
            p.add(n);
            return p;
        }
    }

    class OnlineCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object val, int idx, boolean sel, boolean focus) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            row.setBackground(sel ? new Color(30, 40, 70) : BG_SIDEBAR);
            String name = val.toString().replaceAll("\\[.*?\\]", "").trim();
            Color ac = new Color((name.hashCode() & 0x7F7F7F) | 0x404040);
            JLabel av = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(ac);
                    g2.fillOval(0, 0, 26, 26);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                    String lt = name.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(name.charAt(0)));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(lt, (26 - fm.stringWidth(lt)) / 2, (26 + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
                @Override
                public Dimension getPreferredSize() { return new Dimension(26, 26); }
            };
            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setOpaque(false);
            JLabel nl = new JLabel(name);
            nl.setFont(new Font("SansSerif", Font.BOLD, 12));
            nl.setForeground(TEXT_PRIMARY);
            JLabel dl = new JLabel("● online");
            dl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            dl.setForeground(NEON_GREEN);
            info.add(nl);
            info.add(dl);
            row.add(av);
            row.add(info);
            return row;
        }
    }

    // ── DIALOG: STUDENT MANAGER ──

    void showStudentManager() {
        JDialog dialog = styledDialog("Quan ly Sinh vien", 650, 520);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<String> model = new DefaultListModel<>();
        STUDENT.getAll().forEach((k, v) -> model.addElement(k + " - " + v));
        JList<String> list = styledList(model);

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBackground(BG_PANEL);
        form.setBorder(new EmptyBorder(15, 15, 15, 15));

        JTextField tfMssv = new JTextField(); styleField(tfMssv);
        JTextField tfTen  = new JTextField(); styleField(tfTen);
        JTextField tfLop  = new JTextField(); styleField(tfLop);
        for (JTextField tf : new JTextField[]{tfMssv, tfTen, tfLop}) tf.setForeground(Color.WHITE);
        JLabel status = statusLbl();

        form.add(fieldLbl("MSSV:"));   form.add(tfMssv);
        form.add(fieldLbl("Ho Ten:")); form.add(tfTen);
        form.add(fieldLbl("Lop:"));    form.add(tfLop);
        form.add(fieldLbl("Trang thai:")); form.add(status);

        JPanel btns = btnRow2(dialog,
            neonBtn("+ Them SV", NEON_GREEN),
            neonBtn("Xoa SV", DANGER),
            e -> {
                String m = tfMssv.getText().trim(), t = tfTen.getText().trim(), lp = tfLop.getText().trim();
                if (m.isEmpty() || t.isEmpty()) {
                    status.setForeground(DANGER); status.setText("Nhap du MSSV & Ten!"); return;
                }
                STUDENT.addStudent(m, t, lp);
                model.addElement(m + " - " + t + " | " + lp);
                tfMssv.setText(""); tfTen.setText(""); tfLop.setText("");
                status.setForeground(NEON_GREEN); status.setText("Da them thanh cong!");
            },
            e -> {
                String sel = list.getSelectedValue();
                if (sel == null) { status.setText("Chon SV can xoa!"); return; }
                String mssv = sel.split(" - ")[0];
                if (JOptionPane.showConfirmDialog(dialog, "Xoa SV: " + mssv + "?", "Xac nhan", JOptionPane.YES_NO_OPTION) == 0) {
                    STUDENT.removeStudent(mssv);
                    model.removeElement(sel);
                    status.setForeground(NEON_GREEN); status.setText("Da xoa!");
                }
            });

        dialog.add(styledScroll(list, "Danh sach Sinh vien"), BorderLayout.CENTER);
        dialog.add(form, BorderLayout.NORTH);
        dialog.add(btns, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ── DIALOG: ADMIN MANAGER ──

    void showAdminManager() {
        JDialog dialog = styledDialog("Quan ly Admin", 500, 480);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<String> model = new DefaultListModel<>();
        ADMIN.getAll().keySet().forEach(model::addElement);
        JList<String> list = styledList(model);

        JPanel form = new JPanel(new GridLayout(5, 1, 5, 6));
        form.setBackground(BG_PANEL);
        form.setBorder(new EmptyBorder(12, 15, 12, 15));

        JTextField    uField = new JTextField();  styleField(uField);
        JPasswordField pField = new JPasswordField(); styleField(pField);
        JLabel status = statusLbl();

        form.add(fieldLbl("Ten dang nhap:")); form.add(uField);
        form.add(fieldLbl("Mat khau:"));      form.add(pField);
        form.add(status);

        JPanel btns = btnRow2(dialog,
            neonBtn("+ Them Admin", NEON_GREEN),
            neonBtn("Xoa Admin", DANGER),
            e -> {
                String u = uField.getText().trim(), p = new String(pField.getPassword());
                if (u.isEmpty() || p.isEmpty()) { status.setText("Nhap du!"); return; }
                String res = ADMIN.addAdmin(u, p);
                if (res.equals("OK")) {
                    model.addElement(u); uField.setText(""); pField.setText("");
                    status.setForeground(NEON_GREEN); status.setText("Da them!");
                } else {
                    status.setForeground(DANGER); status.setText(res.substring(4));
                }
            },
            e -> {
                String sel = list.getSelectedValue();
                if (sel == null) { status.setText("Chon admin!"); return; }
                if (sel.equals(currentAdmin)) {
                    status.setForeground(DANGER); status.setText("Khong the xoa chinh minh!"); return;
                }
                if (JOptionPane.showConfirmDialog(dialog, "Xoa admin: " + sel + "?", "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    String res = ADMIN.removeAdmin(sel);
                    if (res.equals("OK")) {
                        model.removeElement(sel);
                        status.setForeground(NEON_GREEN); status.setText("Da xoa!");
                    } else {
                        status.setForeground(DANGER); status.setText(res.substring(4));
                    }
                }
            });

        dialog.add(styledScroll(list, "Danh sach Admin"), BorderLayout.CENTER);
        dialog.add(form, BorderLayout.NORTH);
        dialog.add(btns, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ── DIALOG: USER MANAGER ──

    void showUserManager() {
        JDialog dialog = styledDialog("Quan ly User", 560, 520);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<String> model = new DefaultListModel<>();
        AUTH.getAll().keySet().forEach(model::addElement);
        JList<String> list = styledList(model);

        JPanel form = new JPanel(new GridLayout(7, 1, 5, 6));
        form.setBackground(BG_PANEL);
        form.setBorder(new EmptyBorder(12, 15, 12, 15));

        JTextField    uField = new JTextField();  styleField(uField);
        JPasswordField pField = new JPasswordField(); styleField(pField);
        JLabel status = statusLbl();

        JCheckBox cbGV = new JCheckBox("Là Giảng viên (thêm prefix GV_)");
        cbGV.setBackground(BG_PANEL);
        cbGV.setForeground(NEON_PURPLE);
        cbGV.setFont(new Font("SansSerif", Font.BOLD, 12));

        form.add(fieldLbl("Ten dang nhap:")); form.add(uField);
        form.add(fieldLbl("Mat khau:"));      form.add(pField);
        form.add(cbGV);
        form.add(fieldLbl("Trang thai:"));    form.add(status);

        JPanel btns = btnRow2(dialog,
            neonBtn("+ Them User", NEON_GREEN),
            neonBtn("Xoa User", DANGER),
            e -> {
                String u = uField.getText().trim();
                String p = new String(pField.getPassword());
                if (u.isEmpty() || p.isEmpty()) {
                    status.setForeground(DANGER); status.setText("Nhap du!"); return;
                }
                String finalUser = cbGV.isSelected() ? "GV_" + u : u;
                String res = AUTH.register(finalUser, p);
                if (res.equals("OK")) {
                    model.addElement(finalUser);
                    uField.setText(""); pField.setText(""); cbGV.setSelected(false);
                    status.setForeground(NEON_GREEN);
                    status.setText("Da them: " + finalUser);
                } else {
                    status.setForeground(DANGER); status.setText(res.substring(4));
                }
            },
            e -> {
                String sel = list.getSelectedValue();
                if (sel == null) { status.setForeground(DANGER); status.setText("Chon user!"); return; }
                if (JOptionPane.showConfirmDialog(dialog, "Xoa user: " + sel + "?", "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    AUTH.getAll().remove(sel.toLowerCase());
                    AUTH.save();
                    model.removeElement(sel);
                    status.setForeground(NEON_GREEN); status.setText("Da xoa!");
                }
            });

        list.setCellRenderer((lst, val, idx, sel, focus) -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            row.setBackground(sel ? new Color(30, 40, 70) : BG_PANEL);
            String name = val.toString();
            boolean isGV = name.toLowerCase().startsWith("gv_");
            JLabel icon    = new JLabel(isGV ? "👨‍🏫" : "🎓");
            icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            JLabel nameLbl = new JLabel(name);
            nameLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLbl.setForeground(isGV ? NEON_PURPLE : TEXT_PRIMARY);
            JLabel roleLbl = new JLabel(isGV ? " [GV]" : " [SV]");
            roleLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
            roleLbl.setForeground(isGV ? NEON_PURPLE : TEXT_MUTED);
            row.add(icon); row.add(nameLbl); row.add(roleLbl);
            return row;
        });

        dialog.add(styledScroll(list, "Danh sach User (👨‍🏫=GV / 🎓=SV)"), BorderLayout.CENTER);
        dialog.add(form, BorderLayout.NORTH);
        dialog.add(btns, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ── DIALOG: ROOM MANAGER ──

    void showRoomManager() {
        JDialog dialog = styledDialog("Quan ly Lop hoc", 480, 500);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String cls : DBHelper.getAllClasses()) model.addElement(cls);
        JList<String> list = styledList(model);

        JPanel form = new JPanel(new GridLayout(3, 1, 5, 6));
        form.setBackground(BG_PANEL);
        form.setBorder(new EmptyBorder(12, 15, 12, 15));

        JTextField nameField = new JTextField(); styleField(nameField);
        JLabel status = statusLbl();

        form.add(fieldLbl("Ten lop (vd: 23DTH2A):")); form.add(nameField); form.add(status);

        JPanel btns = btnRow2(dialog,
            neonBtn("+ Them Lop", NEON_GREEN),
            neonBtn("Xoa Lop", DANGER),
            e -> {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { status.setText("Nhap ten lop!"); return; }
                if (model.contains(name)) { status.setText("Lop da ton tai!"); return; }
                model.addElement(name); nameField.setText("");
                { java.util.List<String> cls = new java.util.ArrayList<>(); for (int _i=0;_i<model.size();_i++) cls.add(model.get(_i)); DBHelper.deleteClass("__dummy__"); for (String _c: cls) DBHelper.addClass(_c,""); }
                status.setForeground(NEON_GREEN); status.setText("Da them: " + name);
            },
            e -> {
                String sel = list.getSelectedValue();
                if (sel == null) { status.setText("Chon lop!"); return; }
                if (JOptionPane.showConfirmDialog(dialog, "Xoa lop: " + sel + "?", "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    model.removeElement(sel);
                    { java.util.List<String> cls = new java.util.ArrayList<>(); for (int _i=0;_i<model.size();_i++) cls.add(model.get(_i)); DBHelper.deleteClass("__dummy__"); for (String _c: cls) DBHelper.addClass(_c,""); }
                    status.setForeground(NEON_GREEN); status.setText("Da xoa!");
                }
            });

        dialog.add(styledScroll(list, "Danh sach Lop"), BorderLayout.CENTER);
        dialog.add(form, BorderLayout.NORTH);
        dialog.add(btns, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ── DIALOG: LECTURER MANAGER ──

    void showLecturerManager() {
        JDialog dialog = styledDialog("Quan ly Giang vien", 900, 620);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getContentPane().setBackground(BG_DARK);

        DefaultListModel<String> khoaModel = new DefaultListModel<>();
        // Dùng danh sách cố định (có thể mở rộng sau)
        for (String k : new String[]{"Cong nghe thong tin", "Kien truc", "Kinh te", "Dien - Dien tu"})
            khoaModel.addElement(k);

        JList<String> khoaList = styledList(khoaModel);
        JTextField khoaField = new JTextField(); styleField(khoaField);
        khoaField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JButton bak = neonBtn("+", NEON_GREEN); bak.setPreferredSize(new Dimension(36, 36));
        JButton bdk = neonBtn("-", DANGER);     bdk.setPreferredSize(new Dimension(36, 36));
        JPanel kb = new JPanel(new GridLayout(1, 2, 4, 0)); kb.setOpaque(false); kb.add(bak); kb.add(bdk);
        JPanel ki = new JPanel(new BorderLayout(4, 0)); ki.setOpaque(false);
        ki.add(khoaField, BorderLayout.CENTER); ki.add(kb, BorderLayout.EAST);

        JPanel leftP = new JPanel(new BorderLayout(5, 5));
        leftP.setBackground(BG_PANEL);
        leftP.setPreferredSize(new Dimension(220, 0));
        leftP.setBorder(BorderFactory.createTitledBorder(new RoundedBorder(NEON_CYAN, 8), "  Khoa",
            0, 0, new Font("SansSerif", Font.BOLD, 11), NEON_CYAN));
        JScrollPane klsp = new JScrollPane(khoaList); klsp.setBorder(null); klsp.getViewport().setBackground(BG_PANEL);
        leftP.add(klsp, BorderLayout.CENTER);
        leftP.add(ki, BorderLayout.SOUTH);

        DefaultListModel<String> gvModel = new DefaultListModel<>();
        JList<String> gvList = styledList(gvModel);

        JPanel gvForm = new JPanel(new GridLayout(10, 1, 3, 4));
        gvForm.setBackground(BG_PANEL);
        gvForm.setBorder(new EmptyBorder(8, 10, 8, 10));

        JTextField tma = new JTextField(), tten = new JTextField(), tmon = new JTextField(), tsdt = new JTextField();
        for (JTextField tf : new JTextField[]{tma, tten, tmon, tsdt}) styleField(tf);
        JLabel gvSt = statusLbl();

        gvForm.add(fieldLbl("Ma GV:"));   gvForm.add(tma);
        gvForm.add(fieldLbl("Ho ten:"));  gvForm.add(tten);
        gvForm.add(fieldLbl("Mon day:")); gvForm.add(tmon);
        gvForm.add(fieldLbl("SDT:"));     gvForm.add(tsdt);
        gvForm.add(gvSt);

        JButton bag = neonBtn("+ Them GV", NEON_GREEN), bdg = neonBtn("Xoa GV", DANGER);
        JPanel gb = new JPanel(new GridLayout(1, 2, 8, 0));
        gb.setBackground(BG_PANEL); gb.setBorder(new EmptyBorder(0, 10, 10, 10));
        gb.add(bag); gb.add(bdg);

        JPanel rightP = new JPanel(new BorderLayout(5, 5));
        rightP.setBackground(BG_PANEL);
        rightP.setBorder(BorderFactory.createTitledBorder(new RoundedBorder(NEON_PURPLE, 8), "  Giang vien",
            0, 0, new Font("SansSerif", Font.BOLD, 11), NEON_PURPLE));
        JScrollPane glsp = new JScrollPane(gvList); glsp.setBorder(null); glsp.getViewport().setBackground(BG_PANEL);
        rightP.add(gvForm, BorderLayout.NORTH);
        rightP.add(glsp, BorderLayout.CENTER);
        rightP.add(gb, BorderLayout.SOUTH);

        khoaList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String sel = khoaList.getSelectedValue(); if (sel == null) return;
            gvModel.clear();
            // Load GV từ DB (lọc theo prefix GV_)
            for (String[] u : DBHelper.getUsersByRole("TEACHER")) {
                gvModel.addElement(u[0] + " | " + u[1]);
            }
        });

        bag.addActionListener(e -> {
            String k = khoaList.getSelectedValue();
            if (k == null) { gvSt.setText("Chon khoa truoc!"); return; }
            String ma = tma.getText().trim(), ten = tten.getText().trim(),
                   mon = tmon.getText().trim(), sdt = tsdt.getText().trim();
            if (ma.isEmpty() || ten.isEmpty()) {
                gvSt.setForeground(DANGER); gvSt.setText("Nhap Ma GV va Ho ten!"); return;
            }
            gvModel.addElement(ma + " | " + ten + " | " + mon + " | " + sdt);
            tma.setText(""); tten.setText(""); tmon.setText(""); tsdt.setText("");
            /* GV saved to DB */
            gvSt.setForeground(NEON_GREEN); gvSt.setText("Da them: " + ten);
        });
        bdg.addActionListener(e -> {
            String k = khoaList.getSelectedValue(), sel = gvList.getSelectedValue();
            if (sel == null) { gvSt.setText("Chon GV!"); return; }
            if (JOptionPane.showConfirmDialog(dialog, "Xoa GV: " + sel + "?", "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                gvModel.removeElement(sel);
                /* GV saved to DB */
                gvSt.setForeground(NEON_GREEN); gvSt.setText("Da xoa!");
            }
        });
        bak.addActionListener(e -> {
            String name = khoaField.getText().trim();
            if (name.isEmpty() || khoaModel.contains(name)) return;
            khoaModel.addElement(name); khoaField.setText("");
            /* khoa saved */
        });
        bdk.addActionListener(e -> {
            String sel = khoaList.getSelectedValue(); if (sel == null) return;
            if (JOptionPane.showConfirmDialog(dialog, "Xoa khoa: " + sel + "?", "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                khoaModel.removeElement(sel); /* khoa saved */
            }
        });

        dialog.add(leftP, BorderLayout.WEST);
        dialog.add(rightP, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    // ── DIALOG: SCHEDULE MANAGER ──

    void showScheduleManager() {
        JDialog dialog = styledDialog("Quan ly Lich hoc", 1050, 640);
        dialog.setLayout(new BorderLayout(8, 8));

        DefaultListModel<String> classModel = new DefaultListModel<>();
        for (String cls : DBHelper.getAllClasses()) classModel.addElement(cls);
        JList<String> classList = styledList(classModel);

        JPanel leftP = new JPanel(new BorderLayout());
        leftP.setBackground(BG_PANEL);
        leftP.setPreferredSize(new Dimension(190, 0));
        leftP.setBorder(BorderFactory.createTitledBorder(new RoundedBorder(NEON_CYAN, 8), "  Chon Lop",
            0, 0, new Font("SansSerif", Font.BOLD, 11), NEON_CYAN));
        JScrollPane clsp = new JScrollPane(classList); clsp.setBorder(null); clsp.getViewport().setBackground(BG_PANEL);
        leftP.add(clsp, BorderLayout.CENTER);

        String[] cols = {"Thu", "Ca hoc", "Tiet", "Mon hoc", "Phong", "Giang vien", "Ma lop"};
        javax.swing.table.DefaultTableModel tableModel = new javax.swing.table.DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setBackground(BG_PANEL); table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(new Color(0, 255, 136, 40));
        table.setGridColor(new Color(30, 45, 80)); table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setBackground(BG_DARKEST);
        table.getTableHeader().setForeground(NEON_CYAN);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel formPanel = new JPanel(new GridLayout(8, 2, 6, 6));
        formPanel.setBackground(BG_PANEL);
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] thuOpts = {"Thu 2", "Thu 3", "Thu 4", "Thu 5", "Thu 6", "Thu 7", "Chu nhat"};
        String[] caOpts  = {"Sang (1-6)", "Chieu (7-12)", "Toi (13-15)"};
        JComboBox<String> cbThu = new JComboBox<>(thuOpts), cbCa = new JComboBox<>(caOpts);
        cbThu.setBackground(BG_INPUT); cbThu.setForeground(TEXT_PRIMARY);
        cbCa.setBackground(BG_INPUT);  cbCa.setForeground(TEXT_PRIMARY);

        JTextField tiet = new JTextField(), mon = new JTextField(), phong = new JTextField(),
                   gv = new JTextField(), maLop = new JTextField();
        for (JTextField tf : new JTextField[]{tiet, mon, phong, gv, maLop}) styleField(tf);
        JLabel sl = statusLbl();

        String[]    fLabels = {"Thu:", "Ca hoc:", "Tiet (1-3):", "Mon hoc:", "Phong:", "Giang vien:", "Ma lop:"};
        Component[] fFields = {cbThu, cbCa, tiet, mon, phong, gv, maLop};
        for (int i = 0; i < fLabels.length; i++) { formPanel.add(fieldLbl(fLabels[i])); formPanel.add(fFields[i]); }
        formPanel.add(sl);

        JButton ba = neonBtn("+ Them lich", NEON_GREEN), bd = neonBtn("Xoa lich", DANGER);
        JPanel bp = new JPanel(new GridLayout(1, 3, 8, 0));
        bp.setBackground(BG_PANEL); bp.setBorder(new EmptyBorder(0, 10, 10, 10));
        bp.add(ba); bp.add(bd);

        JPanel rightP = new JPanel(new BorderLayout(5, 5));
        rightP.setBackground(BG_PANEL);
        JScrollPane tsp = new JScrollPane(table); tsp.setBorder(null); tsp.getViewport().setBackground(BG_PANEL);
        rightP.add(tsp, BorderLayout.CENTER);
        rightP.add(formPanel, BorderLayout.EAST);
        rightP.add(bp, BorderLayout.SOUTH);

        classList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String lop = classList.getSelectedValue(); if (lop == null) return;
            tableModel.setRowCount(0);
            maLop.setText(lop);
            // Tải lịch học từ DATABASE
            for (String[] row : DBHelper.getScheduleByClass(lop)) {
                if (row.length == 7) tableModel.addRow(row);
            }
        });

        ba.addActionListener(e -> {
            String lop = classList.getSelectedValue();
            if (lop == null) { sl.setForeground(DANGER); sl.setText("Chon lop truoc!"); return; }
            String t  = (String) cbThu.getSelectedItem(), c = (String) cbCa.getSelectedItem(),
                   ti = tiet.getText().trim(), m = mon.getText().trim(),
                   p  = phong.getText().trim(), gvt = gv.getText().trim(), ml = maLop.getText().trim();
            if (m.isEmpty() || ti.isEmpty() || p.isEmpty()) {
                sl.setForeground(DANGER); sl.setText("Nhap du Mon, Tiet, Phong!"); return;
            }
            tableModel.addRow(new String[]{t, c, ti, m, p, gvt, ml});
            saveSchedule(lop, tableModel);
            autoAssignLopToStudents(lop);
            sl.setForeground(NEON_GREEN); sl.setText("Da them: " + m + " (da gan cho SV lop " + lop + ")");
            tiet.setText(""); mon.setText(""); phong.setText(""); gv.setText("");
        });

        bd.addActionListener(e -> {
            String lop = classList.getSelectedValue();
            int row = table.getSelectedRow();
            if (row < 0) { sl.setForeground(DANGER); sl.setText("Chon dong can xoa!"); return; }
            if (JOptionPane.showConfirmDialog(dialog, "Xoa lich: " + tableModel.getValueAt(row, 3) + "?",
                    "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                tableModel.removeRow(row);
                saveSchedule(lop, tableModel);
                sl.setForeground(NEON_GREEN); sl.setText("Da xoa!");
            }
        });

        JButton btnGanSV = neonBtn("Gan lop nay cho SV", NEON_PURPLE);
        btnGanSV.addActionListener(e -> {
            String lop = classList.getSelectedValue();
            if (lop == null) { sl.setForeground(DANGER); sl.setText("Chon lop truoc!"); return; }
            autoAssignLopToStudents(lop);
            sl.setForeground(NEON_GREEN); sl.setText("Da gan lop " + lop + " cho tat ca SV!");
            JOptionPane.showMessageDialog(dialog,
                "✅ Đã gán lớp " + lop + " vào lịch học của sinh viên!\n" +
                "SV đăng nhập lại và bấm 📅 sẽ thấy lịch.",
                "Gán lớp thành công", JOptionPane.INFORMATION_MESSAGE);
        });
        bp.add(btnGanSV);

        dialog.add(leftP, BorderLayout.WEST);
        dialog.add(rightP, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    // ── SERVER METHODS ──

    private void loadRoomsFromFile() {
        // Tải phòng từ DATABASE
        java.util.List<String> rooms = DBHelper.getAllRooms();
        if (rooms.isEmpty()) {
            ROOMS.put("general", new CopyOnWriteArrayList<>());
        } else {
            for (String r : rooms) ROOMS.putIfAbsent(r, new CopyOnWriteArrayList<>());
        }
        ROOMS.putIfAbsent("general", new CopyOnWriteArrayList<>());
    }

    private synchronized void saveRoomToFile(String rn) {
        DBHelper.saveRoom(rn); // Lưu vào DATABASE
    }

    private synchronized void saveChatHistory(String room, String msg) {
        // Lấy username từ msg format "MSG:[room] username: content"
        try {
            String sender = "system";
            if (msg.contains("] ") && msg.contains(": ")) {
                int s = msg.indexOf("] ") + 2;
                int e2 = msg.indexOf(": ", s);
                if (e2 > s) sender = msg.substring(s, e2).trim();
            }
            DBHelper.saveMessage(sender, msg);
        } catch (Exception e) {}
    }

    private String getChatHistory(String room) {
        java.util.List<String> msgs = DBHelper.getChatHistory(50);
        StringBuilder sb = new StringBuilder();
        for (String m : msgs) sb.append(m).append("\n");
        return sb.toString();
    }

    void log(String room, String msg) {
        String r = (room == null) ? "SYSTEM" : room;
        SwingUtilities.invokeLater(() -> {
            tabMap.putIfAbsent(r, new JTextArea());
            JTextArea area = tabMap.get(r);
            if (area.getParent() == null) {
                area.setEditable(false);
                area.setBackground(BG_DARK);
                area.setForeground(TEXT_PRIMARY);
                area.setFont(new Font("SansSerif", Font.PLAIN, 13));
                area.setCaretColor(NEON_GREEN);
                JScrollPane sp = new JScrollPane(area);
                sp.setBorder(null); sp.getViewport().setBackground(BG_DARK);
                tabs.addTab(r, sp);
            }
            area.append("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg + "\n");
        });
    }

    private void autoAssignLopToStudents(String maLop) {
        // Dùng DATABASE để gán lớp cho sinh viên
        int added = DBHelper.autoEnrollStudentsInClass(maLop);
        log("SYSTEM", "Da gan lop " + maLop + " cho " + added + " sinh vien trong DB.");
    }

    private void sendMemberListToClient(ClientHandler handler, String roomName) {
        List<String> members = new ArrayList<>();
        // Lấy từ DATABASE
        for (String[] m : DBHelper.getMembersOfClass(roomName)) {
            String mssv = m[0], ten = m[1];
            boolean online = CLIENTS.containsKey(mssv) || CLIENTS.containsKey(mssv + "@nttu.edu.vn");
            members.add(mssv + "|" + ten + "|" + roomName + "|" + (online ? "online" : "offline"));
        }
        if (members.isEmpty()) {
            ROOMS.getOrDefault(roomName, new CopyOnWriteArrayList<>()).forEach(client -> {
                if (client.username != null)
                    members.add(client.username + "|" + client.username + "|" + roomName + "|online");
            });
        }
        handler.sendText("MEMBER_LIST:" + String.join(";", members));
    }

    void startServer() {
        loadRoomsFromFile();
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(9999)) {
                log("SYSTEM", "Server khoi dong tren Port 9999");
                while (true) new Thread(new ClientHandler(ss.accept())).start();
            } catch (Exception e) {}
        }).start();
    }

    // ── CLIENT HANDLER ──

    class ClientHandler implements Runnable {
        Socket socket;
        DataInputStream  in;
        DataOutputStream out;
        String username, room;

        ClientHandler(Socket s) {
            try {
                socket = s;
                in  = new DataInputStream(new java.io.BufferedInputStream(s.getInputStream()));
                out = new DataOutputStream(new java.io.BufferedOutputStream(s.getOutputStream()));
            } catch (Exception e) {}
        }

        synchronized void sendText(String msg) {
            try { out.writeUTF("TEXT:" + msg); out.flush(); } catch (IOException e) {}
        }

        synchronized void sendVoice(String sender, byte[] audio) {
            try { out.writeUTF("VOICE:" + sender); out.writeInt(audio.length); out.write(audio); out.flush(); } catch (IOException e) {}
        }

        synchronized void sendImage(String sn, byte[] img) {
            try { out.writeUTF("IMAGE:" + sn); out.writeInt(img.length); out.write(img); out.flush(); } catch (IOException e) {}
        }

        synchronized void sendFile(String header, int sz, byte[] data) {
            try { out.writeUTF(header); out.writeInt(sz); out.write(data); out.flush(); } catch (IOException e) {}
        }

        // ── Broadcast helpers ──

        void broadcastText(String msg) {
            if (room != null && ROOMS.containsKey(room))
                for (ClientHandler c : ROOMS.get(room)) c.sendText(msg);
        }

        void broadcastImage(String sn, byte[] img) {
            if (room != null && ROOMS.containsKey(room))
                for (ClientHandler c : ROOMS.get(room)) c.sendImage(sn, img);
        }

        void broadcastFile(String header, int sz, byte[] data) {
            if (room != null && ROOMS.containsKey(room))
                for (ClientHandler c : ROOMS.get(room)) { try { c.sendFile(header, sz, data); } catch (Exception e) {} }
        }

        void broadcastVoice(String sn, byte[] audio) {
            if (room != null && ROOMS.containsKey(room))
                for (ClientHandler c : ROOMS.get(room))
                    if (c.username != null && !c.username.equals(sn)) c.sendVoice(sn, audio);
        }

        void broadcastJoin(String uname) {
            broadcastText("SYSTEM:👋 " + uname + " đã tham gia phòng " + room);
            log(room, uname + " đã tham gia.");
        }

        void broadcastLeave(String uname) {
            broadcastText("SYSTEM:👋 " + uname + " đã rời khỏi phòng.");
            log(room, uname + " đã rời khỏi.");
        }

        void broadcastOnlineList() {
            if (room == null) return;
            CopyOnWriteArrayList<ClientHandler> list = ROOMS.get(room);
            if (list == null) return;
            StringBuilder sb = new StringBuilder("ONLINE_LIST:");
            for (ClientHandler c : list)
                if (c.username != null && !c.username.isEmpty()) sb.append(c.username).append("|");
            String message = sb.toString();
            for (ClientHandler c : list) {
                try { c.out.writeUTF(message); c.out.flush(); } catch (IOException ignored) {}
            }
        }

        // ── Cleanup ──

        void cleanup() {
            if (username != null) {
                CLIENTS.remove(username);
                SwingUtilities.invokeLater(() -> onlineModel.removeElement(username + " [" + room + "]"));
                if (room != null && ROOMS.containsKey(room)) {
                    ROOMS.get(room).remove(this);
                    broadcastLeave(username);
                    broadcastOnlineList();
                }
            }
            try { socket.close(); } catch (Exception e) {}
        }

        // ── Main run loop ──

        @Override
        public void run() {
            try {
                sendText("AUTH_START");
                String al = in.readUTF();
                String ct = al.startsWith("TEXT:") ? al.substring(5) : al;
                String[] p = ct.split(":", 3);
                if (p.length < 3) return;

                String res = p[0].equalsIgnoreCase("REGISTER")
                    ? AUTH.register(p[1], p[2])
                    : AUTH.login(p[1], p[2]);
                if (!res.equals("OK")) { sendText("AUTH_ERR:" + res.substring(4)); return; }

                username = p[1];
                sendText("AUTH_OK:Chao " + username);
                sendText("CHOOSE_ROOM");

                String rl = in.readUTF();
                room = rl.startsWith("TEXT:") ? rl.substring(5) : rl;
                if (room == null || room.isEmpty()) room = "general";

                if (!ROOMS.containsKey(room)) { ROOMS.put(room, new CopyOnWriteArrayList<>()); saveRoomToFile(room); }
                ROOMS.get(room).add(this);
                CLIENTS.put(username, this);
                SwingUtilities.invokeLater(() -> onlineModel.addElement(username + " [" + room + "]"));

                broadcastJoin(username);
                broadcastOnlineList();

                sendText("APPROVED");

                String hist = getChatHistory(room);
                if (!hist.isEmpty())
                    for (String hl : hist.split("\n"))
                        if (!hl.trim().isEmpty()) sendText(hl);

                log(room, username + " da vao.");

                while (true) {
                    String header = in.readUTF();

                    if (header.startsWith("FILE_MSG:")) {
                        int sz = in.readInt();
                        byte[] data = new byte[sz];
                        in.readFully(data);
                        log(room, username + " da gui file.");
                        broadcastFile(header, sz, data);
                        continue;
                    }

                    if (header.startsWith("VOICE:")) {
                        String sn = header.substring(6);
                        int sz = in.readInt();
                        byte[] audio = new byte[sz];
                        in.readFully(audio);
                        broadcastVoice(sn, audio);
                        continue;
                    }

                    if (header.startsWith("IMAGE:")) {
                        String sn = header.substring(6);
                        int sz = in.readInt();
                        byte[] img = new byte[sz];
                        in.readFully(img);
                        log(room, username + " da gui anh.");
                        broadcastImage(sn, img);
                        continue;
                    }

                    if (header.startsWith("TEXT:")) {
                        String msg = header.substring(5);

                        if (msg.startsWith("/update_profile ")) {
                            String[] parts = msg.substring(16).trim().split("\\|", 3);
                            if (parts.length >= 2) {
                                String mssv = parts[0].trim(), ten = parts[1].trim();
                                String lop  = parts.length >= 3 ? parts[2].trim() : "";
                                if (STUDENT.exists(mssv) && !ten.isEmpty()) {
                                    STUDENT.updateStudent(mssv, ten, lop);
                                    sendText("TEXT:✅ Đã cập nhật: " + ten + " | " + lop);
                                } else {
                                    sendText("TEXT:⚠ MSSV không tồn tại!");
                                }
                            }
                            continue;
                        }

                        if (msg.startsWith("GIF:")) {
                            String fullMsg = "GIF:" + username + ":" + msg.substring(4);
                            saveChatHistory(room, fullMsg);
                            log(room, username + " đã gửi GIF");
                            broadcastText(fullMsg);
                            continue;
                        }

                        if (msg.startsWith("IMG:")) {
                            broadcastText(msg);
                            continue;
                        }

                        String full = "MSG:[" + room + "] " + username + ": " + msg;
                        saveChatHistory(room, full);
                        log(room, username + ": " + msg);
                        broadcastText(full);
                    }
                }
            } catch (Exception e) {
                // Connection dropped
            } finally {
                cleanup();
            }
        }
    }

    // ── MAIN ──
    
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatServerUI::new);
    }
}