package com.mycompany.chatapp.service;

import java.sql.*;
import java.util.*;

/**
 * DBHelper - Kết nối và thao tác với SQL Server database
 * Thay thế toàn bộ việc đọc/ghi file txt
 */
public class DBHelper {

    private static final String URL =
        "jdbc:sqlserver://localhost:1433;databaseName=ClassroomDB;" +
        "encrypt=true;trustServerCertificate=true";
    private static final String USER = "sa";
    private static final String PASS = "YourStrong@Passw0rd";

    // ── Lấy kết nối ──
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // ════════════════════════════════════════════
    // AUTH - Đăng nhập / Đăng ký
    // ════════════════════════════════════════════

    /** Đăng nhập: trả về "OK" hoặc "ERR:..." */
    public static String login(String username, String hashedPassword) {
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, hashedPassword);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return "OK";
            // Kiểm tra user có tồn tại không
            String check = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement ps2 = c.prepareStatement(check)) {
                ps2.setString(1, username.toLowerCase());
                ResultSet rs2 = ps2.executeQuery();
                if (!rs2.next()) return "ERR:Không tồn tại user";
                return "ERR:Sai mật khẩu";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERR:Lỗi kết nối database";
        }
    }

    /** Đăng ký user mới */
    public static String register(String username, String hashedPassword, String role, String fullName) {
        // Kiểm tra đã tồn tại chưa
        String check = "SELECT id FROM users WHERE username = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(check)) {
            ps.setString(1, username.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return "ERR:Tên đã tồn tại";
        } catch (SQLException e) {
            return "ERR:Lỗi kết nối database";
        }

        String sql = "INSERT INTO users (username, password, role, full_name) VALUES (?, ?, ?, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, hashedPassword);
            ps.setString(3, role != null ? role : "STUDENT");
            ps.setString(4, fullName != null ? fullName : username);
            ps.executeUpdate();
            return "OK";
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERR:Lỗi đăng ký";
        }
    }

    /** Xóa user */
    public static boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Lấy tất cả users */
    public static Map<String, String> getAllUsers() {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT username, password FROM users ORDER BY username";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("username"), rs.getString("password"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /** Lấy users theo role */
    public static List<String[]> getUsersByRole(String role) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT username, full_name, role FROM users WHERE role = ? ORDER BY username";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role.toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("username"),
                    rs.getString("full_name"),
                    rs.getString("role")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ════════════════════════════════════════════
    // ADMIN
    // ════════════════════════════════════════════

    /** Lấy tất cả admin */
    public static Map<String, String> getAllAdmins() {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT username, password FROM users WHERE role = 'ADMIN' ORDER BY username";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("username"), rs.getString("password"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Nếu chưa có admin nào, tạo mặc định
        if (map.isEmpty()) {
            String defaultHash = hashPassword("admin123");
            registerAdmin("admin", defaultHash);
            map.put("admin", defaultHash);
        }
        return map;
    }

    /** Đăng ký admin mới */
    public static String registerAdmin(String username, String hashedPassword) {
        return register(username, hashedPassword, "ADMIN", username);
    }

    /** Xóa admin */
    public static boolean deleteAdmin(String username) {
        // Kiểm tra còn ít nhất 1 admin
        String count = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(count);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) <= 1) return false; // Không xóa admin cuối
        } catch (SQLException e) {
            return false;
        }
        String sql = "DELETE FROM users WHERE username = ? AND role = 'ADMIN'";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ════════════════════════════════════════════
    // STUDENTS
    // ════════════════════════════════════════════

    /** Lấy tất cả sinh viên (username -> "fullName | class") */
    public static Map<String, String> getAllStudents() {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT u.username, u.full_name, c.name AS class_name " +
                     "FROM users u " +
                     "LEFT JOIN enrollments e ON u.id = e.user_id " +
                     "LEFT JOIN classes c ON e.class_id = c.id " +
                     "WHERE u.role = 'STUDENT' " +
                     "ORDER BY u.username";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username  = rs.getString("username");
                String fullName  = rs.getString("full_name");
                String className = rs.getString("class_name");
                String val = (className != null && !className.isEmpty())
                    ? fullName + " | " + className : fullName;
                map.putIfAbsent(username, val);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /** Thêm sinh viên */
    public static boolean addStudent(String mssv, String fullName, String className) {
        String result = register(mssv, hashPassword("1"), "STUDENT", fullName);
        if (!result.equals("OK") && !result.contains("tồn tại")) return false;
        if (className != null && !className.isEmpty()) {
            enrollUserInClass(mssv, className);
        }
        return true;
    }

    /** Xóa sinh viên */
    public static boolean removeStudent(String mssv) {
        return deleteUser(mssv);
    }

    /** Cập nhật thông tin sinh viên */
    public static boolean updateStudent(String mssv, String fullName, String className) {
        String sql = "UPDATE users SET full_name = ? WHERE username = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, mssv.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        if (className != null && !className.isEmpty()) {
            enrollUserInClass(mssv, className);
        }
        return true;
    }

    /** Kiểm tra sinh viên tồn tại */
    public static boolean studentExists(String mssv) {
        String sql = "SELECT id FROM users WHERE username = ? AND role = 'STUDENT'";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, mssv.toLowerCase());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ════════════════════════════════════════════
    // CLASSES (Lớp học)
    // ════════════════════════════════════════════

    /** Lấy tất cả lớp */
    public static List<String> getAllClasses() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT name FROM classes ORDER BY name";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Thêm lớp mới */
    public static boolean addClass(String className, String teacher) {
        String sql = "INSERT INTO classes (name, teacher) VALUES (?, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, className);
            ps.setString(2, teacher != null ? teacher : "");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false; // Có thể đã tồn tại
        }
    }

    /** Xóa lớp */
    public static boolean deleteClass(String className) {
        String sql = "DELETE FROM classes WHERE name = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, className);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Lấy thành viên của lớp (để gửi member list) */
    public static List<String[]> getMembersOfClass(String className) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT u.username, u.full_name " +
                     "FROM users u " +
                     "JOIN enrollments e ON u.id = e.user_id " +
                     "JOIN classes c ON e.class_id = c.id " +
                     "WHERE c.name = ? " +
                     "ORDER BY u.username";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, className);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("username"),
                    rs.getString("full_name"),
                    className
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ════════════════════════════════════════════
    // ENROLLMENTS (Ghi danh)
    // ════════════════════════════════════════════

    /** Lấy danh sách lớp của user */
    public static List<String> getUserClasses(String username) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT c.name FROM classes c " +
                     "JOIN enrollments e ON c.id = e.class_id " +
                     "JOIN users u ON u.id = e.user_id " +
                     "WHERE u.username = ? " +
                     "ORDER BY c.name";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString("name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Ghi danh user vào lớp */
    public static boolean enrollUserInClass(String username, String className) {
        // Lấy user_id và class_id
        String sqlUser  = "SELECT id FROM users WHERE username = ?";
        String sqlClass = "SELECT id FROM classes WHERE name = ?";
        String sqlCheck = "SELECT id FROM enrollments WHERE user_id = ? AND class_id = ?";
        String sqlIns   = "INSERT INTO enrollments (user_id, class_id) VALUES (?, ?)";

        try (Connection c = getConnection()) {
            int userId = -1, classId = -1;

            try (PreparedStatement ps = c.prepareStatement(sqlUser)) {
                ps.setString(1, username.toLowerCase());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) userId = rs.getInt("id");
            }
            try (PreparedStatement ps = c.prepareStatement(sqlClass)) {
                ps.setString(1, className);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) classId = rs.getInt("id");
            }

            if (userId == -1) return false; // User không tồn tại
            if (classId == -1) {
                // Tự tạo lớp nếu chưa có
                addClass(className, "");
                try (PreparedStatement ps = c.prepareStatement(sqlClass)) {
                    ps.setString(1, className);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) classId = rs.getInt("id");
                }
            }

            // Kiểm tra đã ghi danh chưa
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, userId); ps.setInt(2, classId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return true; // Đã có rồi
            }

            // Insert
            try (PreparedStatement ps = c.prepareStatement(sqlIns)) {
                ps.setInt(1, userId); ps.setInt(2, classId);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Xóa hết ghi danh của user */
    public static void clearUserEnrollments(String username) {
        String sql = "DELETE FROM enrollments WHERE user_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Gán lớp cho tất cả sinh viên thuộc lớp đó */
    public static int autoEnrollStudentsInClass(String className) {
        // Lấy tất cả STUDENT
        List<String[]> students = getUsersByRole("STUDENT");
        int count = 0;
        for (String[] sv : students) {
            String mssv = sv[0];
            List<String> classes = getUserClasses(mssv);
            if (!classes.contains(className)) {
                if (enrollUserInClass(mssv, className)) count++;
            }
        }
        return count;
    }

    // ════════════════════════════════════════════
    // SCHEDULES (Lịch học)
    // ════════════════════════════════════════════

    /** Lấy lịch học theo lớp - trả về [thu, ca, tiet, mon, phong, gv, maLop] */
    public static List<String[]> getScheduleByClass(String className) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT s.day_of_week, " +
                     "CASE WHEN CAST(s.start_time AS VARCHAR) < '12:00' THEN 'Sang (1-6)' " +
                     "     WHEN CAST(s.start_time AS VARCHAR) < '18:00' THEN 'Chieu (7-12)' " +
                     "     ELSE 'Toi (13-15)' END AS ca, " +
                     "'1-3' AS tiet, " +
                     "c.name AS mon, " +
                     "s.room_name, " +
                     "c.teacher, " +
                     "c.name AS maLop " +
                     "FROM schedules s " +
                     "JOIN classes c ON s.class_id = c.id " +
                     "WHERE c.name = ? " +
                     "ORDER BY s.day_of_week, s.start_time";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, className);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString(1), // thu
                    rs.getString(2), // ca
                    rs.getString(3), // tiet
                    rs.getString(4), // mon
                    rs.getString(5), // phong
                    rs.getString(6), // gv
                    rs.getString(7)  // maLop
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Thêm lịch học */
    public static boolean addSchedule(String className, String dayOfWeek,
                                       String startTime, String endTime, String roomName) {
        String sqlClass = "SELECT id FROM classes WHERE name = ?";
        String sqlIns   = "INSERT INTO schedules (class_id, day_of_week, start_time, end_time, room_name) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = getConnection()) {
            int classId = -1;
            try (PreparedStatement ps = c.prepareStatement(sqlClass)) {
                ps.setString(1, className);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) classId = rs.getInt("id");
            }
            if (classId == -1) return false;

            try (PreparedStatement ps = c.prepareStatement(sqlIns)) {
                ps.setInt(1, classId);
                ps.setString(2, dayOfWeek);
                ps.setString(3, startTime.isEmpty() ? "07:00" : startTime);
                ps.setString(4, endTime.isEmpty() ? "09:30" : endTime);
                ps.setString(5, roomName);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Lưu toàn bộ lịch học của lớp (xóa cũ, thêm mới) */
    public static void saveScheduleForClass(String className,
                                             List<String[]> rows) {
        // rows: [thu, ca, tiet, mon, phong, gv, maLop]
        String sqlClass = "SELECT id FROM classes WHERE name = ?";
        String sqlDel   = "DELETE FROM schedules WHERE class_id = ?";
        String sqlIns   = "INSERT INTO schedules (class_id, day_of_week, start_time, end_time, room_name) VALUES (?, ?, ?, ?, ?)";

        try (Connection c = getConnection()) {
            int classId = -1;
            try (PreparedStatement ps = c.prepareStatement(sqlClass)) {
                ps.setString(1, className);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) classId = rs.getInt("id");
            }
            if (classId == -1) return;

            try (PreparedStatement ps = c.prepareStatement(sqlDel)) {
                ps.setInt(1, classId); ps.executeUpdate();
            }

            for (String[] row : rows) {
                // Chuyển ca sang giờ
                String ca = row.length > 1 ? row[1] : "";
                String start = "07:00", end = "09:30";
                if (ca.contains("Chieu") || ca.contains("7-12")) { start = "13:00"; end = "17:30"; }
                else if (ca.contains("Toi") || ca.contains("13-15")) { start = "18:00"; end = "21:00"; }

                try (PreparedStatement ps = c.prepareStatement(sqlIns)) {
                    ps.setInt(1, classId);
                    ps.setString(2, row.length > 0 ? row[0] : "");
                    ps.setString(3, start);
                    ps.setString(4, end);
                    ps.setString(5, row.length > 4 ? row[4] : "");
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════
    // MESSAGES (Chat history)
    // ════════════════════════════════════════════

    /** Lưu tin nhắn */
    public static void saveMessage(String senderUsername, String content) {
        String sqlUser = "SELECT id FROM users WHERE username = ?";
        String sqlIns  = "INSERT INTO messages (sender_id, content) VALUES (?, ?)";
        try (Connection c = getConnection()) {
            int senderId = -1;
            try (PreparedStatement ps = c.prepareStatement(sqlUser)) {
                ps.setString(1, senderUsername.toLowerCase());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) senderId = rs.getInt("id");
            }
            if (senderId == -1) return;

            try (PreparedStatement ps = c.prepareStatement(sqlIns)) {
                ps.setInt(1, senderId);
                ps.setString(2, content);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Lấy lịch sử chat (50 tin gần nhất) */
    public static List<String> getChatHistory(int limit) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT TOP " + limit + " u.username, m.content, m.created_at " +
                     "FROM messages m JOIN users u ON m.sender_id = u.id " +
                     "ORDER BY m.created_at DESC";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(0, rs.getString("username") + ": " + rs.getString("content"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ════════════════════════════════════════════
    // ATTENDANCE (Điểm danh)
    // ════════════════════════════════════════════

    /** Lưu điểm danh */
    public static void saveAttendance(String username, String className, String status) {
        String sql = "INSERT INTO attendance (user_id, class_id, date, status) " +
                     "SELECT u.id, c.id, CAST(GETDATE() AS DATE), ? " +
                     "FROM users u, classes c " +
                     "WHERE u.username = ? AND c.name = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, username.toLowerCase());
            ps.setString(3, className);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Lấy điểm danh của sinh viên trong lớp */
    public static List<String[]> getAttendance(String username, String className) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT a.date, a.status FROM attendance a " +
                     "JOIN users u ON a.user_id = u.id " +
                     "JOIN classes c ON a.class_id = c.id " +
                     "WHERE u.username = ? AND c.name = ? " +
                     "ORDER BY a.date DESC";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, className);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{rs.getString("date"), rs.getString("status")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ════════════════════════════════════════════
    // ROOMS (Phòng chat - lưu vào classes table)
    // ════════════════════════════════════════════

    /** Lấy danh sách phòng chat (dựa trên classes) */
    public static List<String> getAllRooms() {
        return getAllClasses();
    }

    /** Thêm phòng chat */
    public static void saveRoom(String roomName) {
        addClass(roomName, "");
    }

    // ════════════════════════════════════════════
    // UTILITIES
    // ════════════════════════════════════════════

    /** Hash password SHA-256 */
    public static String hashPassword(String pw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(pw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Kiểm tra kết nối */
    public static boolean testConnection() {
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}