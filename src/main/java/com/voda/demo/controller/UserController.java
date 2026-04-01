package com.voda.demo.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voda.demo.entity.User;
import com.voda.demo.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "User", description = "CRUD quản lý user")
public class UserController {
    private final UserService userService;

    @Operation(summary = "Lấy danh sách user")
    @GetMapping("/")
    public List<User> getAllUsers(@RequestParam String param) {
        return userService.getAllUsers();
    }

    @Operation(summary = "Lấy user theo ID")
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }
    
    @Operation(summary = "Tạo user mới")
    @PostMapping("path")
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }
    
    @Operation(summary = "Cập nhật user")
    @PutMapping("path/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.updateUser(id, user);
    }

    @Operation(summary = "Xoá user")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
