package com.mycompany.chatapp.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycompany.chatapp.service.DBHelper;

@RestController
@RequestMapping("/api/classes")
@CrossOrigin(origins = "*")
public class ClassController {

    // ══════════════════════════════════════════
    // GET /api/classes
    // Lấy tất cả lớp học
    // ══════════════════════════════════════════
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllClasses() {
        List<String> classes = DBHelper.getAllClasses();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "total",   classes.size(),
            "classes", classes
        ));
    }

    // ══════════════════════════════════════════
    // GET /api/classes/{className}/members
    // Lấy danh sách sinh viên trong lớp
    // ══════════════════════════════════════════
    @GetMapping("/{className}/members")
    public ResponseEntity<Map<String, Object>> getMembers(@PathVariable String className) {
        List<String[]> members = DBHelper.getMembersOfClass(className);

        List<Map<String, String>> result = new ArrayList<>();
        for (String[] m : members) {
            Map<String, String> map = new HashMap<>();
            map.put("username", m[0]);
            map.put("fullName", m[1]);
            map.put("className", className);
            result.add(map);
        }

        return ResponseEntity.ok(Map.of(
            "success",   true,
            "className", className,
            "total",     result.size(),
            "members",   result
        ));
    }

    // ══════════════════════════════════════════
    // GET /api/classes/{className}/schedule
    // Lấy lịch học của lớp
    // ══════════════════════════════════════════
    @GetMapping("/{className}/schedule")
    public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable String className) {
        List<String[]> rows = DBHelper.getScheduleByClass(className);

        List<Map<String, String>> schedule = new ArrayList<>();
        for (String[] row : rows) {
            if (row.length >= 7) {
                Map<String, String> entry = new HashMap<>();
                entry.put("thu",      row[0]);
                entry.put("ca",       row[1]);
                entry.put("tiet",     row[2]);
                entry.put("mon",      row[3]);
                entry.put("phong",    row[4]);
                entry.put("giaoVien", row[5]);
                entry.put("maLop",    row[6]);
                schedule.add(entry);
            }
        }

        return ResponseEntity.ok(Map.of(
            "success",   true,
            "className", className,
            "schedule",  schedule
        ));
    }

    // ══════════════════════════════════════════
    // GET /api/classes/user/{username}
    // Lấy danh sách lớp của 1 sinh viên
    // ══════════════════════════════════════════
    @GetMapping("/user/{username}")
    public ResponseEntity<Map<String, Object>> getUserClasses(@PathVariable String username) {
        List<String> classes = DBHelper.getUserClasses(username);
        return ResponseEntity.ok(Map.of(
            "success",  true,
            "username", username,
            "total",    classes.size(),
            "classes",  classes
        ));
    }
}
