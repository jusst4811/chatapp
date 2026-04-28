package com.mycompany.chatapp.server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChannelManager {

    // Map: tên channel → danh sách message history
    private final Map<String, List<String>> channelMessages = new HashMap<>();

    // Map: username → channel hiện tại của user đó
    private final Map<String, String> userChannels = new HashMap<>();

    // Các channel mặc định khi server khởi động
    private static final String[] DEFAULT_CHANNELS = {
        "#general", "#random", "#gaming", "#voice"
    };

    public ChannelManager() {
        for (String ch : DEFAULT_CHANNELS) {
            channelMessages.put(ch, new CopyOnWriteArrayList<>());
        }
    }

    // User vào channel — trả về history của channel đó
    public synchronized String joinChannel(String username, String channel) {
        // Tạo channel mới nếu chưa tồn tại
        channelMessages.putIfAbsent(channel, new CopyOnWriteArrayList<>());
        userChannels.put(username, channel);
        return getHistory(channel);
    }

    // Lấy channel hiện tại của user
    public synchronized String getCurrentChannel(String username) {
        return userChannels.getOrDefault(username, "#general");
    }

    // Thêm message vào channel
    public synchronized void addMessage(String channel, String message) {
        channelMessages.putIfAbsent(channel, new CopyOnWriteArrayList<>());
        List<String> msgs = channelMessages.get(channel);
        msgs.add(message);
        // Giữ tối đa 100 message gần nhất
        if (msgs.size() > 100) {
            msgs.remove(0);
        }
    }

    // Lấy history — ghép bằng dấu | để gửi qua socket 1 dòng
    public synchronized String getHistory(String channel) {
        List<String> msgs = channelMessages.getOrDefault(channel, new ArrayList<>());
        if (msgs.isEmpty()) return "";
        return String.join("|", msgs);
    }

    // Lấy danh sách tất cả channel
    public synchronized List<String> getChannelList() {
        return new ArrayList<>(channelMessages.keySet());
    }

    // User rời khỏi (disconnect)
    public synchronized void removeUser(String username) {
        userChannels.remove(username);
    }

    // Lấy danh sách user đang ở 1 channel cụ thể
    public synchronized List<String> getUsersInChannel(String channel) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> e : userChannels.entrySet()) {
            if (e.getValue().equals(channel)) {
                result.add(e.getKey());
            }
        }
        return result;
    }
}