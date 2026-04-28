package com.mycompany.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mycompany.chatapp.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    // Tự động tạo lệnh: SELECT * FROM users WHERE username = ?
    Optional<User> findByUsername(String username);
}