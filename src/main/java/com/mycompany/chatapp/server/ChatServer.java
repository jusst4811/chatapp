package com.mycompany.chatapp.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mycompany.chatapp.service.DBHelper;

/**
 * ChatServer - Server chạy ngầm không cần giao diện Swing
 * Dùng để deploy lên cloud (AWS, Railway, VPS...)
 * 
 * Cách chạy:
 *   java -cp chatapp.jar com.mycompany.chatapp.ChatServer
 * hoặc trong Eclipse: Run As → Java Application
 */
public class ChatServer {

    private static final int PORT = 9999;

    // ── Quản lý clients và rooms ──
    static final ConcurrentHashMap<String, ClientHandler> CLIENTS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, CopyOnWriteArrayList<ClientHandler>> ROOMS  = new ConcurrentHashMap<>();

    // ── Auth helper (dùng lại DBHelper) ──
    static String login(String user, String pw) {
        return DBHelper.login(user, DBHelper.hashPassword(pw));
    }

    static String register(String user, String pw) {
        return DBHelper.register(user, DBHelper.hashPassword(pw), "STUDENT", user);
    }

    // ── Logging ──
    static void log(String msg) {
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg);
    }

    // ── Load rooms từ DB ──
    static void loadRooms() {
        List<String> rooms = DBHelper.getAllRooms();
        if (rooms.isEmpty()) {
            ROOMS.put("general", new CopyOnWriteArrayList<>());
        } else {
            for (String r : rooms) ROOMS.putIfAbsent(r, new CopyOnWriteArrayList<>());
        }
        ROOMS.putIfAbsent("general", new CopyOnWriteArrayList<>());
        log("Loaded " + ROOMS.size() + " rooms from DB.");
    }

    // ── Lưu chat history vào DB ──
    static void saveMessage(String room, String senderUsername, String content) {
        DBHelper.saveMessage(senderUsername, content);
    }

    // ── Lấy lịch sử chat ──
    static List<String> getChatHistory(int limit) {
        return DBHelper.getChatHistory(limit);
    }

    // ── MAIN ──
    public static void main(String[] args) {
        log("=== CHAT SERVER STARTING ===");

        // Kiểm tra kết nối DB
        if (!DBHelper.testConnection()) {
            log("❌ Không thể kết nối Database! Kiểm tra SQL Server.");
            System.exit(1);
        }
        log("✅ Database connected.");

        loadRooms();

        // Bắt signal tắt server sạch
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Server đang tắt...");
            CLIENTS.values().forEach(c -> {
                try { c.socket.close(); } catch (Exception ignored) {}
            });
            log("Server đã tắt.");
        }));

        // Bắt đầu lắng nghe
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("✅ Server đang lắng nghe tại port " + PORT);
            log("Nhấn Ctrl+C để tắt server.");
            log("================================");

            while (true) {
                Socket client = serverSocket.accept();
                log("Client mới kết nối: " + client.getInetAddress());
                new Thread(new ClientHandler(client)).start();
            }
        } catch (IOException e) {
            log("❌ Lỗi server: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    // CLIENT HANDLER
    // ══════════════════════════════════════════
    static class ClientHandler implements Runnable {
        Socket socket;
        DataInputStream  in;
        DataOutputStream out;
        String username, room;

        ClientHandler(Socket s) {
            try {
                socket = s;
                in  = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            } catch (Exception e) {
                log("Lỗi khởi tạo ClientHandler: " + e.getMessage());
            }
        }

        // ── Gửi tin nhắn ──
        synchronized void sendText(String msg) {
            try { out.writeUTF("TEXT:" + msg); out.flush(); } catch (IOException e) {}
        }

        synchronized void sendVoice(String sender, byte[] audio) {
            try {
                out.writeUTF("VOICE:" + sender);
                out.writeInt(audio.length);
                out.write(audio);
                out.flush();
            } catch (IOException e) {}
        }

        synchronized void sendImage(String sn, byte[] img) {
            try {
                out.writeUTF("IMAGE:" + sn);
                out.writeInt(img.length);
                out.write(img);
                out.flush();
            } catch (IOException e) {}
        }

        synchronized void sendFile(String header, int sz, byte[] data) {
            try {
                out.writeUTF(header);
                out.writeInt(sz);
                out.write(data);
                out.flush();
            } catch (IOException e) {}
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
                for (ClientHandler c : ROOMS.get(room)) {
                    try { c.sendFile(header, sz, data); } catch (Exception e) {}
                }
        }

        void broadcastVoice(String sn, byte[] audio) {
            if (room != null && ROOMS.containsKey(room))
                for (ClientHandler c : ROOMS.get(room))
                    if (c.username != null && !c.username.equals(sn))
                        c.sendVoice(sn, audio);
        }

        void broadcastOnlineList() {
            if (room == null) return;
            CopyOnWriteArrayList<ClientHandler> list = ROOMS.get(room);
            if (list == null) return;
            StringBuilder sb = new StringBuilder("ONLINE_LIST:");
            for (ClientHandler c : list)
                if (c.username != null) sb.append(c.username).append("|");
            String message = sb.toString();
            for (ClientHandler c : list) {
                try { c.out.writeUTF(message); c.out.flush(); } catch (IOException ignored) {}
            }
        }

        void sendMemberList() {
            String className = (room != null && room.startsWith("#")) ? room.substring(1) : room;
            List<String> members = new ArrayList<>();

            for (String[] m : DBHelper.getMembersOfClass(className)) {
                String mssv = m[0], ten = m[1];
                boolean online = CLIENTS.containsKey(mssv) || CLIENTS.containsKey(mssv + "@nttu.edu.vn");
                members.add(mssv + "|" + ten + "|" + className + "|" + (online ? "online" : "offline"));
            }

            if (members.isEmpty()) {
                CopyOnWriteArrayList<ClientHandler> list = ROOMS.getOrDefault(room, new CopyOnWriteArrayList<>());
                for (ClientHandler c : list)
                    if (c.username != null)
                        members.add(c.username + "|" + c.username + "|" + className + "|online");
            }

            sendText("MEMBER_LIST:" + String.join(";", members));
        }

        // ── Cleanup khi client ngắt kết nối ──
        void cleanup() {
            if (username != null) {
                CLIENTS.remove(username);
                if (room != null && ROOMS.containsKey(room)) {
                    ROOMS.get(room).remove(this);
                    broadcastText("SYSTEM:👋 " + username + " đã rời khỏi phòng.");
                    broadcastOnlineList();
                }
                log("Client rời: " + username + " [" + room + "]");
            }
            try { socket.close(); } catch (Exception e) {}
        }

        // ── Main run loop ──
        @Override
        public void run() {
            try {
                // 1. Gửi tín hiệu bắt đầu auth
                sendText("AUTH_START");

                // 2. Nhận thông tin đăng nhập
                String al = in.readUTF();
                String ct = al.startsWith("TEXT:") ? al.substring(5) : al;
                String[] p = ct.split(":", 3);
                if (p.length < 3) return;

                // 3. Xử lý login/register
                String res = p[0].equalsIgnoreCase("REGISTER")
                    ? register(p[1], p[2])
                    : login(p[1], p[2]);

                if (!res.equals("OK")) {
                    sendText("AUTH_ERR:" + res.substring(4));
                    return;
                }

                username = p[1];
                sendText("AUTH_OK:Chao " + username);
                log("User đăng nhập: " + username);

                // 4. Chọn phòng
                sendText("CHOOSE_ROOM");
                String rl = in.readUTF();
                room = rl.startsWith("TEXT:") ? rl.substring(5) : rl;
                if (room == null || room.isEmpty()) room = "general";

                // 5. Vào phòng
                if (!ROOMS.containsKey(room)) {
                    ROOMS.put(room, new CopyOnWriteArrayList<>());
                    DBHelper.saveRoom(room);
                }
                ROOMS.get(room).add(this);
                CLIENTS.put(username, this);

                broadcastText("SYSTEM:👋 " + username + " đã tham gia phòng " + room);
                broadcastOnlineList();
                sendMemberList();
                sendText("APPROVED");
                log(username + " vào phòng: " + room);

                // 6. Gửi lịch sử chat
                List<String> history = getChatHistory(50);
                for (String hl : history)
                    if (!hl.trim().isEmpty()) sendText(hl);

                // 7. Vòng lặp nhận tin nhắn
                while (true) {
                    String header = in.readUTF();

                    if (header.startsWith("FILE_MSG:")) {
                        int sz = in.readInt();
                        byte[] data = new byte[sz];
                        in.readFully(data);
                        log(username + " gửi file");
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
                        log(username + " gửi ảnh");
                        broadcastImage(sn, img);
                        continue;
                    }

                    if (header.startsWith("TEXT:")) {
                        String msg = header.substring(5);

                        // Cập nhật profile
                        if (msg.startsWith("/update_profile ")) {
                            String[] parts = msg.substring(16).trim().split("\\|", 3);
                            if (parts.length >= 2) {
                                String mssv = parts[0].trim(), ten = parts[1].trim();
                                String lop  = parts.length >= 3 ? parts[2].trim() : "";
                                boolean ok = DBHelper.updateStudent(mssv, ten, lop);
                                sendText(ok ? "TEXT:✅ Đã cập nhật: " + ten : "TEXT:⚠ MSSV không tồn tại!");
                            }
                            continue;
                        }

                        // GIF
                        if (msg.startsWith("GIF:")) {
                            String fullMsg = "GIF:" + username + ":" + msg.substring(4);
                            saveMessage(room, username, fullMsg);
                            broadcastText(fullMsg);
                            continue;
                        }

                        // IMG base64
                        if (msg.startsWith("IMG:")) {
                            broadcastText(msg);
                            continue;
                        }

                        // Tin nhắn thường
                        String full = "MSG:[" + room + "] " + username + ": " + msg;
                        saveMessage(room, username, full);
                        log(room + " | " + username + ": " + msg);
                        broadcastText(full);
                    }
                }

            } catch (Exception e) {
                // Client ngắt kết nối - bình thường
            } finally {
                cleanup();
            }
        }
    }
}
