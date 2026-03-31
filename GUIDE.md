# Hướng dẫn xây dựng 1 API trong Spring Boot

## Tổng quan luồng dữ liệu

```
Client (Browser/Postman)
    │
    ▼
Controller  ──  nhận request, trả response
    │
    ▼
Service     ──  xử lý logic nghiệp vụ
    │
    ▼
Repository  ──  truy vấn database
    │
    ▼
Entity      ──  đại diện cho bảng trong database
```

---

## Các folder cần tạo

```
src/main/java/com/voda/demo/
├── entity/          ① Định nghĩa bảng
├── repository/      ② Truy vấn database
├── service/         ③ Xử lý logic
└── controller/      ④ Nhận request từ client
```

Bạn code theo thứ tự **① → ② → ③ → ④**.

---

## Bước 1: Entity — Định nghĩa bảng trong database

**File:** `entity/User.java`

```java
package com.voda.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data       // Lombok tự tạo getter/setter
@Entity     // Đánh dấu đây là 1 bảng trong DB
public class User {

    @Id                                              // Khóa chính
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Tự tăng
    private Long id;

    private String name;
    private String email;
}
```

**Hiểu đơn giản:** 1 Entity = 1 bảng trong database. Mỗi field = 1 cột.

---

## Bước 2: Repository — Truy vấn database

**File:** `repository/UserRepository.java`

```java
package com.voda.demo.repository;

import com.voda.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // Không cần viết gì cả!
    // JpaRepository đã có sẵn: findAll, findById, save, deleteById...
}
```

**Hiểu đơn giản:** Kế thừa `JpaRepository` là bạn có ngay các method CRUD mà không cần viết SQL.

---

## Bước 3: Service — Xử lý logic nghiệp vụ

**File:** `service/UserService.java`

```java
package com.voda.demo.service;

import com.voda.demo.entity.User;
import com.voda.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service                // Đánh dấu đây là tầng xử lý logic
@RequiredArgsConstructor // Lombok tự tạo constructor để inject dependency
public class UserService {

    private final UserRepository userRepository;

    // Lấy tất cả user
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Lấy user theo id
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Tạo user mới
    public User createUser(User user) {
        return userRepository.save(user);
    }

    // Cập nhật user
    public User updateUser(Long id, User user) {
        User existing = getUserById(id);
        existing.setName(user.getName());
        existing.setEmail(user.getEmail());
        return userRepository.save(existing);
    }

    // Xóa user
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

**Hiểu đơn giản:** Service là nơi đặt logic. Controller không nên gọi trực tiếp Repository.

---

## Bước 4: Controller — Nhận request từ client

**File:** `controller/UserController.java`

```java
package com.voda.demo.controller;

import com.voda.demo.entity.User;
import com.voda.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController          // Đánh dấu đây là REST API controller
@RequestMapping("/api/users")  // URL gốc cho tất cả API trong controller này
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // GET http://localhost:8080/api/users
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // GET http://localhost:8080/api/users/1
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    // POST http://localhost:8080/api/users
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    // PUT http://localhost:8080/api/users/1
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.updateUser(id, user);
    }

    // DELETE http://localhost:8080/api/users/1
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
```

---

## Tóm tắt các Annotation quan trọng

| Annotation | Đặt ở đâu | Ý nghĩa |
|---|---|---|
| `@Entity` | Entity class | Đây là bảng trong DB |
| `@Id` | Field | Khóa chính |
| `@GeneratedValue` | Field | Tự tăng ID |
| `@Data` | Class | Lombok tạo getter/setter |
| `@Service` | Service class | Đánh dấu tầng logic |
| `@RestController` | Controller class | Đánh dấu REST API |
| `@RequestMapping` | Class/Method | Định nghĩa URL |
| `@GetMapping` | Method | Xử lý GET request |
| `@PostMapping` | Method | Xử lý POST request |
| `@PutMapping` | Method | Xử lý PUT request |
| `@DeleteMapping` | Method | Xử lý DELETE request |
| `@PathVariable` | Parameter | Lấy giá trị từ URL (`/users/1` → id=1) |
| `@RequestBody` | Parameter | Lấy dữ liệu JSON từ body request |
| `@RequiredArgsConstructor` | Class | Lombok tạo constructor inject |

---

## Test API bằng curl

```bash
# Tạo user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Jacky", "email": "jacky@gmail.com"}'

# Lấy tất cả users
curl http://localhost:8080/api/users

# Lấy user theo id
curl http://localhost:8080/api/users/1

# Cập nhật user
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Jacky Updated", "email": "jacky.new@gmail.com"}'

# Xóa user
curl -X DELETE http://localhost:8080/api/users/1
```

---

## Quy tắc nhớ

> **Muốn thêm API mới cho 1 đối tượng (ví dụ: Product, Order...), lặp lại 4 bước:**
> 1. Tạo Entity trong `entity/`
> 2. Tạo Repository trong `repository/`
> 3. Tạo Service trong `service/`
> 4. Tạo Controller trong `controller/`
