package com.mycompany.chatapp.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mycompany.chatapp.service.DBHelper;

public class ClientHandler implements Runnable {
    Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    String username;
    String room;
    private final CopyOnWriteArrayList<ClientHandler> clients;
    private final ChannelManager channelManager;

    public ClientHandler(Socket socket, CopyOnWriteArrayList<ClientHandler> clients, ChannelManager channelManager) {
        this.socket = socket;
        this.clients = clients;
        this.channelManager = channelManager;
        try {
            in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void sendText(String msg) {
        try { out.writeUTF("TEXT:" + msg); out.flush(); } catch (IOException e) {}
    }

    public synchronized void sendVoice(String senderName, byte[] audio) {
        try {
            out.writeUTF("VOICE:" + senderName);
            out.writeInt(audio.length);
            out.write(audio);
            out.flush();
        } catch (IOException e) {}
    }

    @Override
    public void run() {
        try {
            sendText("AUTH_START");
            String authLine = in.readUTF();
            String content = authLine.startsWith("TEXT:") ? authLine.substring(5) : authLine;
            String[] p = content.split(":", 3);
            if (p.length < 3) return;

            username = p[1].trim();
            sendText("AUTH_OK:Chào " + username + "!");

            sendText("CHOOSE_ROOM");
            String roomLine = in.readUTF();
            room = roomLine.startsWith("TEXT:") ? roomLine.substring(5) : roomLine;
            if (room == null || room.isEmpty()) room = "general";
            if (!room.startsWith("#")) room = "#" + room.trim();

            clients.add(this);
            channelManager.joinChannel(username, room);

            // Gửi danh sách thành viên từ DATABASE
            sendMemberList();

            sendText("APPROVED");
            broadcastText("MSG:[Server] " + username + " đã vào phòng.");
            broadcastUserList();

            while (true) {
                String header = in.readUTF();

                if (header.startsWith("VOICE:")) {
                    String sender = header.substring(6);
                    int size = in.readInt();
                    byte[] audio = new byte[size];
                    in.readFully(audio);
                    broadcastVoice(sender, audio);
                    continue;
                }

                if (header.startsWith("TEXT:")) {
                    String msg = header.substring(5);
                    String fullMsg = "MSG:[" + room + "] " + username + ": " + msg;
                    channelManager.addMessage(room, fullMsg);
                    broadcastText(fullMsg);
                }
            }
        } catch (IOException e) {
            // client thoát
        } finally {
            cleanup();
        }
    }

    // ── GỬI DANH SÁCH THÀNH VIÊN TỪ DATABASE ──
    private void sendMemberList() {
        String className = room.startsWith("#") ? room.substring(1) : room;
        StringBuilder sb = new StringBuilder("MEMBER_LIST:");

        // Lấy từ DATABASE thay vì students.txt
        List<String[]> members = DBHelper.getMembersOfClass(className);

        for (String[] m : members) {
            // m = [username, fullName, className]
            String mssv    = m[0];
            String ten     = m[1];
            boolean online = isUserOnline(mssv);
            sb.append(mssv).append("|")
              .append(ten).append("|")
              .append(className).append("|")
              .append(online ? "online" : "offline").append(";");
        }

        // Fallback nếu không có trong DB: dùng user đang online trong room
        if (sb.length() == 12) {
            for (ClientHandler c : clients) {
                if (c.room != null && c.room.equals(room) && c.username != null) {
                    sb.append(c.username).append("|")
                      .append(c.username).append("|")
                      .append(className).append("|online;");
                }
            }
        }

        sendText(sb.toString());
    }

    private boolean isUserOnline(String mssv) {
        for (ClientHandler c : clients) {
            if (c.username != null &&
                (c.username.equalsIgnoreCase(mssv) ||
                 c.username.startsWith(mssv) ||
                 c.username.contains(mssv))) {
                return true;
            }
        }
        return false;
    }

    void broadcastText(String msg) {
        for (ClientHandler c : clients)
            if (room != null && room.equals(c.room))
                c.sendText(msg);
    }

    void broadcastVoice(String sender, byte[] audio) {
        for (ClientHandler c : clients)
            if (room != null && room.equals(c.room) && !c.username.equals(sender))
                c.sendVoice(sender, audio);
    }

    private void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USERLIST:");
        for (ClientHandler c : clients)
            if (c.username != null) sb.append(c.username).append(",");
        for (ClientHandler c : clients) c.sendText(sb.toString());
    }

    void cleanup() {
        clients.remove(this);
        if (username != null) channelManager.removeUser(username);
        broadcastText("MSG:[Server] " + username + " đã thoát.");
        broadcastUserList();
        try { socket.close(); } catch (IOException e) {}
    }

    public String getUsername() { return username; }
}