package com.mycompany.chatapp.model;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import com.mycompany.chatapp.service.DBHelper;

/**
 * ScheduleSystem - Hệ thống lịch học (dùng DATABASE thay vì file txt)
 */
public class ScheduleSystem {

    // ── Palette ──
    static final Color BG_DARKEST  = new Color(10, 12, 18);
    static final Color BG_DARK     = new Color(15, 18, 28);
    static final Color BG_PANEL    = new Color(20, 24, 38);
    static final Color BG_SIDEBAR  = new Color(13, 16, 26);
    static final Color BG_INPUT    = new Color(25, 30, 48);
    static final Color BG_CARD     = new Color(28, 34, 52);
    static final Color NEON_GREEN  = new Color(0, 255, 136);
    static final Color NEON_CYAN   = new Color(0, 212, 255);
    static final Color NEON_PURPLE = new Color(130, 80, 255);
    static final Color NEON_ORANGE = new Color(255, 160, 0);
    static final Color TEXT_PRIMARY = new Color(220, 230, 255);
    static final Color TEXT_MUTED  = new Color(100, 120, 160);
    static final Color DANGER      = new Color(255, 60, 80);

    // ════════════════════════════════════════════
    // MODEL CLASSES (giữ nguyên để tương thích)
    // ════════════════════════════════════════════
    public static class SinhVien {
        public String maSV, hoTen, lopGoc, email;
        public SinhVien(String maSV, String hoTen, String lopGoc, String email) {
            this.maSV = maSV; this.hoTen = hoTen;
            this.lopGoc = lopGoc; this.email = email;
        }
    }


    // ── GiaoVien class (để tương thích với ChatClientUI) ──
    public static class GiaoVien {
        public String maGV, hoTen, khoa, monDay;
        public GiaoVien(String maGV, String hoTen, String khoa, String monDay) {
            this.maGV = maGV; this.hoTen = hoTen;
            this.khoa = khoa; this.monDay = monDay;
        }
    }

    /** Tìm giảng viên theo username - kiểm tra từ DATABASE */
    public static GiaoVien findGiaoVien(String username) {
        if (username == null) return null;
        // Kiểm tra role trong DB
        List<String[]> teachers = DBHelper.getUsersByRole("TEACHER");
        for (String[] t : teachers) {
            if (t[0].equalsIgnoreCase(username) ||
                t[0].equalsIgnoreCase(username.replace("@nttu.edu.vn", ""))) {
                return new GiaoVien(t[0], t[1], "", "");
            }
        }
        // Kiểm tra theo prefix gv_ hoặc username chứa @
        List<String[]> admins = DBHelper.getUsersByRole("ADMIN");
        for (String[] a : admins) {
            if (a[0].equalsIgnoreCase(username)) {
                return new GiaoVien(a[0], a[1], "", "");
            }
        }
        return null;
    }

    public static class ScheduleEntry {
        public String thu, ca, tiet, mon, phong, gv, maLop;
        public ScheduleEntry(String[] p) {
            if (p.length >= 7) {
                thu = p[0].trim(); ca = p[1].trim(); tiet = p[2].trim();
                mon = p[3].trim(); phong = p[4].trim(); gv = p[5].trim(); maLop = p[6].trim();
            }
        }
    }

    // ════════════════════════════════════════════
    // DATA ACCESS - TỪ DATABASE
    // ════════════════════════════════════════════

    public static String extractMSSV(String username) {
        if (username == null) return "";
        if (username.contains("@")) return username.substring(0, username.indexOf('@')).trim();
        return username.trim();
    }

    public static SinhVien findSinhVien(String username) {
        String mssv = extractMSSV(username);
        // Lấy từ DB
        List<String[]> results = DBHelper.getUsersByRole("STUDENT");
        for (String[] row : results) {
            // row = [username, fullName, role]
            if (row[0].equalsIgnoreCase(mssv) || row[0].equalsIgnoreCase(username)) {
                // Lấy lớp gốc từ enrollments
                List<String> classes = DBHelper.getUserClasses(mssv);
                String lopGoc = classes.isEmpty() ? "" : classes.get(0);
                return new SinhVien(row[0], row[1], lopGoc, row[0] + "@nttu.edu.vn");
            }
        }
        return null;
    }

    public static void initSinhVienClasses(String username) {
        String mssv = extractMSSV(username);
        SinhVien sv = findSinhVien(username);
        if (sv == null) return;

        List<String> classes = DBHelper.getUserClasses(mssv);
        if (!classes.contains(sv.lopGoc) && !sv.lopGoc.isEmpty()) {
            DBHelper.enrollUserInClass(mssv, sv.lopGoc);
        }
    }

    public static List<String> getUserClasses(String username) {
        String mssv = extractMSSV(username);
        return DBHelper.getUserClasses(mssv);
    }

    public static void saveUserClasses(String username, List<String> classes) {
        String mssv = extractMSSV(username);
        // Xóa hết rồi thêm lại
        DBHelper.clearUserEnrollments(mssv);
        for (String cls : classes) {
            DBHelper.enrollUserInClass(mssv, cls);
        }
    }

    // Load tất cả lịch học từ DB theo danh sách lớp
    public static List<ScheduleEntry> loadAllSchedules(List<String> classes) {
        List<ScheduleEntry> all = new ArrayList<>();
        Set<String> loaded = new HashSet<>();

        for (String lop : classes) {
            if (lop == null || lop.trim().isEmpty() || loaded.contains(lop)) continue;
            loaded.add(lop);

            // Lấy từ DATABASE
            List<String[]> rows = DBHelper.getScheduleByClass(lop);
            for (String[] row : rows) {
                // row = [day_of_week, ca, tiet, mon, room_name, gv, maLop]
                if (row.length >= 7) {
                    all.add(new ScheduleEntry(row));
                }
            }
        }
        return all;
    }

    // ════════════════════════════════════════════
    // UI METHODS
    // ════════════════════════════════════════════

    /** Hiển thị lịch cho Giảng viên */
    public static void showTeacherSchedule(JFrame parent, String teacherName) {
        JOptionPane.showMessageDialog(parent,
                "📅 LỊCH DẠY CỦA GIẢNG VIÊN\n\n" +
                "Giảng viên: " + teacherName + "\n\n" +
                "Chức năng xem lịch dạy chi tiết đang được hoàn thiện.",
                "Lịch Giảng Viên", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Hiển thị lịch tuần cho Sinh viên - lấy từ DATABASE */
    public static void showWeeklySchedule(JFrame parent, String mssv) {
        JDialog dialog = new JDialog(parent, "📅 Lịch Học Tuần Này - " + mssv, true);
        dialog.setSize(1000, 650);
        dialog.setLocationRelativeTo(parent);
        dialog.getContentPane().setBackground(BG_DARKEST);

        List<String> classes = getUserClasses(mssv);
        List<ScheduleEntry> schedules = loadAllSchedules(classes);

        String[] columns = {"Thứ", "Ca học", "Tiết", "Môn học", "Phòng", "Giảng viên", "Mã lớp"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        for (ScheduleEntry s : schedules) {
            model.addRow(new Object[]{s.thu, s.ca, s.tiet, s.mon, s.phong, s.gv, s.maLop});
        }

        if (schedules.isEmpty()) {
            model.addRow(new Object[]{"--", "--", "--", "Chưa có lịch học", "--", "--", "--"});
        }

        JTable table = new JTable(model);
        table.setBackground(BG_PANEL);
        table.setForeground(TEXT_PRIMARY);
        table.setRowHeight(28);
        table.getTableHeader().setBackground(BG_DARK);
        table.getTableHeader().setForeground(NEON_CYAN);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Lịch học của bạn (MSSV: " + mssv + ") - " + classes.size() + " lớp");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(NEON_GREEN);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_DARKEST);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    /** Phương thức Admin gán lớp */
    public static void showAdminClassAssign(JFrame parent, ConcurrentHashMap<String, String> authUsers) {
        JOptionPane.showMessageDialog(parent,
                "🔧 Chức năng Gán Lớp cho Sinh Viên\n\n" +
                "Số user hiện có: " + authUsers.size() + "\n\n" +
                "Chức năng này đang được phát triển đầy đủ.\n" +
                "Bạn có thể dùng nút \"Gan lop nay cho SV\" trong phần Quản lý Lịch học.",
                "Gán Lớp Sinh Viên", JOptionPane.INFORMATION_MESSAGE);
    }
}