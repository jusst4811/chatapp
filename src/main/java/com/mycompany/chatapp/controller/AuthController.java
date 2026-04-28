package com.mycompany.chatapp.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mycompany.chatapp.service.DBHelper;
import com.mycompany.chatapp.service.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Vui lòng nhập đầy đủ username và password"
            ));
        }

        String result = DBHelper.login(username, DBHelper.hashPassword(password));

        if (result.equals("OK")) {
            String token = JwtUtil.generateToken(username);
            List<String> classes = DBHelper.getUserClasses(username);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đăng nhập thành công");
            response.put("username", username);
            response.put("token", token);
            response.put("classes", classes);
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Sai username hoặc password"
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String fullName = body.getOrDefault("fullName", username);
        String role     = body.getOrDefault("role", "STUDENT");

        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Thiếu thông tin đăng ký"
            ));
        }

        String result = DBHelper.register(username, DBHelper.hashPassword(password), role, fullName);

        if (result.equals("OK")) {
            String token = JwtUtil.generateToken(username);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đăng ký thành công",
                "username", username,
                "token", token
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Đăng ký thất bại"
            ));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Thiếu token"
            ));
        }
        String token = authHeader.substring(7);
        if (JwtUtil.validateToken(token)) {
            String username = JwtUtil.extractUsername(token);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "username", username
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "success", false,
            "message", "Token không hợp lệ"
        ));
    }
}
