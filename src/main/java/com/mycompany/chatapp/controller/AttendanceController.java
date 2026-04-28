package com.mycompany.chatapp.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycompany.chatapp.DBHelper;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    // ══════════════════════════════════════════
    // GET /api/attendance/{username}?className=23DTH1B
    // Lấy điểm danh của sinh viên
    // ══════════════════════════════════════════
    @GetMapping("/{username}")
    public ResponseEntity<Map<String, Object>> getAttendance(
            @PathVariable String username,
            @RequestParam(required = false) String className) {

        if (className == null || className.isEmpty()) {
            // Lấy tất cả lớp của sinh viên rồi gộp điểm danh
            List<String> classes = DBHelper.getUserClasses(username);
            List<Map<String, String>> allAttendance = new ArrayList<>();

            for (String cls : classes) {
                List<String[]> records = DBHelper.getAttendance(username, cls);
                for (String[] r : records) {
                    Map<String, String> record = new HashMap<>();
                    record.put("date",      r[0]);
                    record.put("status",    r[1]);
                    record.put("className", cls);
                    allAttendance.add(record);
                }
            }

            return ResponseEntity.ok(Map.of(
                "success",    true,
                "username",   username,
                "total",      allAttendance.size(),
                "attendance", allAttendance
            ));
        }

        // Lấy điểm danh theo lớp cụ thể
        List<String[]> records = DBHelper.getAttendance(username, className);
        List<Map<String, String>> result = new ArrayList<>();
        int present = 0, absent = 0;

        for (String[] r : records) {
            Map<String, String> record = new HashMap<>();
            record.put("date",   r[0]);
            record.put("status", r[1]);
            result.add(record);
            if ("Present".equalsIgnoreCase(r[1])) present++;
            else absent++;
        }

        return ResponseEntity.ok(Map.of(
            "success",    true,
            "username",   username,
            "className",  className,
            "total",      result.size(),
            "present",    present,
            "absent",     absent,
            "attendance", result
        ));
    }

    // ══════════════════════════════════════════
    // POST /api/attendance
    // Lưu điểm danh
    // Body: { "username": "abc", "className": "23DTH1B", "status": "Present" }
    // ══════════════════════════════════════════
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveAttendance(@RequestBody Map<String, String> body) {
        String username  = body.get("username");
        String className = body.get("className");
        String status    = body.getOrDefault("status", "Present");

        if (username == null || className == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Thiếu username hoặc className"
            ));
        }

        DBHelper.saveAttendance(username, className, status);

        return ResponseEntity.ok(Map.of(
            "success",   true,
            "message",   "Đã lưu điểm danh",
            "username",  username,
            "className", className,
            "status",    status
        ));
    }
}
