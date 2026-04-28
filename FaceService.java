package com.mycompany.chatapp;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import javax.imageio.ImageIO;
import org.json.JSONObject;

/**
 * FaceService.java
 * Class nay giup Java goi Python face-service qua HTTP
 * 
 * Cach dung:
 *   FaceService fs = new FaceService();
 *   fs.dangKy("SV001", "Nguyen Van A", bufferedImage);
 *   FaceService.KetQua kq = fs.nhanDien(bufferedImage);
 */
public class FaceService {

    private static final String BASE_URL = "http://localhost:8000";
    private final HttpClient client = HttpClient.newHttpClient();

    // ---------------------------------------------------
    // Ket qua nhan dien tra ve cho Java
    // ---------------------------------------------------
    public static class KetQua {
        public boolean thanhCong;
        public String mssv;
        public String hoTen;
        public String thoiGian;
        public String thongBao;

        @Override
        public String toString() {
            return thanhCong
                ? "[OK] " + hoTen + " (" + mssv + ") - " + thoiGian
                : "[FAIL] " + thongBao;
        }
    }

    // ---------------------------------------------------
    // Kiem tra server co dang chay khong
    // ---------------------------------------------------
    public boolean kiemTraServer() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/"))
                .GET()
                .build();
            HttpResponse<String> res = client.send(req, BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------
    // Dang ky khuon mat sinh vien
    // Goi khi them sinh vien moi vao he thong
    // ---------------------------------------------------
    public boolean dangKy(String mssv, String hoTen, BufferedImage anh) {
        try {
            byte[] anhBytes = toBytes(anh);
            String boundary = "----Boundary" + System.currentTimeMillis();

            byte[] body = buildMultipart(boundary, mssv, hoTen, anhBytes);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/dangky?mssv=" + mssv + "&hoten=" + hoTen))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body))
                .build();

            HttpResponse<String> res = client.send(req, BodyHandlers.ofString());
            JSONObject json = new JSONObject(res.body());
            return json.optBoolean("success", false);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------------------------------------------
    // Nhan dien khuon mat de diem danh
    // Goi khi chup anh tu webcam
    // ---------------------------------------------------
    public KetQua nhanDien(BufferedImage anh) {
        KetQua kq = new KetQua();
        try {
            byte[] anhBytes = toBytes(anh);
            String boundary = "----Boundary" + System.currentTimeMillis();
            byte[] body = buildMultipartFile(boundary, anhBytes);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/nhandien"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body))
                .build();

            HttpResponse<String> res = client.send(req, BodyHandlers.ofString());
            JSONObject json = new JSONObject(res.body());

            kq.thanhCong = json.optBoolean("success", false);
            kq.mssv      = json.optString("mssv", "");
            kq.hoTen     = json.optString("hoten", "");
            kq.thoiGian  = json.optString("thoigian", "");
            kq.thongBao  = json.optString("message", "");

        } catch (Exception e) {
            kq.thanhCong = false;
            kq.thongBao  = "Loi ket noi: " + e.getMessage();
        }
        return kq;
    }

    // ---------------------------------------------------
    // Lay danh sach diem danh hom nay
    // ---------------------------------------------------
    public String getDiemDanh() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/diemdanh"))
                .GET()
                .build();
            HttpResponse<String> res = client.send(req, BodyHandlers.ofString());
            return res.body();
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    // ---------------------------------------------------
    // Helper: Chuyen BufferedImage -> byte[]
    // ---------------------------------------------------
    private byte[] toBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    // ---------------------------------------------------
    // Helper: Tao multipart body cho dang ky
    // ---------------------------------------------------
    private byte[] buildMultipart(String boundary, String mssv, String hoTen, byte[] imgBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String CRLF = "\r\n";
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);

        writer.append("--" + boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"face.jpg\"").append(CRLF);
        writer.append("Content-Type: image/jpeg").append(CRLF);
        writer.append(CRLF).flush();
        out.write(imgBytes);
        out.flush();
        writer.append(CRLF).flush();
        writer.append("--" + boundary + "--").append(CRLF).flush();

        return out.toByteArray();
    }

    // ---------------------------------------------------
    // Helper: Tao multipart body cho nhan dien
    // ---------------------------------------------------
    private byte[] buildMultipartFile(String boundary, byte[] imgBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String CRLF = "\r\n";
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);

        writer.append("--" + boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"webcam.jpg\"").append(CRLF);
        writer.append("Content-Type: image/jpeg").append(CRLF);
        writer.append(CRLF).flush();
        out.write(imgBytes);
        out.flush();
        writer.append(CRLF).flush();
        writer.append("--" + boundary + "--").append(CRLF).flush();

        return out.toByteArray();
    }
}