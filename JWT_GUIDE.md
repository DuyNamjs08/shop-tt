# Hướng dẫn JWT Authentication trong Spring Boot

## JWT là gì?

JWT (JSON Web Token) là một chuỗi mã hóa dùng để **xác thực người dùng**.

Luồng hoạt động:

```
1. User đăng ký/đăng nhập  →  Server trả về token
2. User gửi request kèm token  →  Server kiểm tra token  →  Cho phép/Từ chối
```

---

## Cấu trúc các file đã tạo

```
src/main/java/com/voda/demo/
│
├── entity/
│   └── User.java                    ── Bảng users (thêm field password)
│
├── repository/
│   └── UserRepository.java          ── Thêm findByEmail() để tìm user khi đăng nhập
│
├── dto/                             ── Data Transfer Object (dữ liệu gửi/nhận từ client)
│   ├── RegisterRequest.java         ── Dữ liệu đăng ký: name, email, password
│   ├── LoginRequest.java            ── Dữ liệu đăng nhập: email, password
│   └── AuthResponse.java            ── Trả về: token
│
├── security/                        ── Phần xử lý JWT
│   ├── JwtUtil.java                 ── Tạo token, đọc token, kiểm tra token
│   └── JwtAuthenticationFilter.java ── Chặn mỗi request để kiểm tra token
│
├── config/
│   └── SecurityConfig.java          ── Cấu hình: API nào cần token, API nào không
│
└── controller/
    └── AuthController.java          ── API đăng ký + đăng nhập
```

---

## Chi tiết từng file

### 1. Entity — `entity/User.java`

Thêm field `password` để lưu mật khẩu đã mã hóa.

```java
@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;    // unique, dùng để đăng nhập
    private String password; // lưu mật khẩu đã mã hóa bằng BCrypt
}
```

### 2. Repository — `repository/UserRepository.java`

Thêm method `findByEmail()` — Spring tự tạo SQL query từ tên method.

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    // Spring tự hiểu: SELECT * FROM users WHERE email = ?
}
```

### 3. DTO — `dto/`

DTO là object dùng để **nhận dữ liệu từ client**, tách biệt với Entity.

| File | Fields | Dùng khi |
|---|---|---|
| `RegisterRequest.java` | name, email, password | Đăng ký |
| `LoginRequest.java` | email, password | Đăng nhập |
| `AuthResponse.java` | token | Trả về token cho client |

**Tại sao dùng DTO thay vì Entity?**
- Không để client gửi thẳng `id` hoặc các field nhạy cảm
- Tách biệt dữ liệu API và dữ liệu database

### 4. JwtUtil — `security/JwtUtil.java`

Class tiện ích xử lý JWT token. Có 3 method:

| Method | Chức năng |
|---|---|
| `generateToken(email)` | Tạo token từ email, hết hạn sau 24h |
| `getEmailFromToken(token)` | Giải mã token → lấy ra email |
| `validateToken(token)` | Kiểm tra token có hợp lệ và chưa hết hạn không |

Cấu hình trong `application.properties`:

```properties
jwt.secret=MySecretKeyForJwtTokenMustBeAtLeast32Characters  # Khóa bí mật
jwt.expiration=86400000  # 24 giờ (tính bằng milliseconds)
```

### 5. JwtAuthenticationFilter — `security/JwtAuthenticationFilter.java`

Filter này **chạy trước mọi request**. Luồng xử lý:

```
Request đến
    │
    ▼
Có header "Authorization: Bearer <token>" không?
    │
    ├── Không → Bỏ qua, để Spring Security xử lý (sẽ bị chặn nếu API cần auth)
    │
    └── Có → Token hợp lệ không?
            │
            ├── Không → Bỏ qua (sẽ bị chặn)
            │
            └── Có → Set user vào SecurityContext → Request được đi tiếp
```

### 6. SecurityConfig — `config/SecurityConfig.java`

Cấu hình API nào cần token, API nào không:

```java
.requestMatchers("/api/auth/**").permitAll()  // Đăng ký/đăng nhập: KHÔNG cần token
.requestMatchers("/").permitAll()             // Trang chủ: KHÔNG cần token
.anyRequest().authenticated()                 // Tất cả còn lại: CẦN token
```

Các Bean quan trọng:

| Bean | Chức năng |
|---|---|
| `PasswordEncoder` | Mã hóa mật khẩu bằng BCrypt (không lưu plain text) |
| `AuthenticationManager` | Quản lý xác thực của Spring Security |

### 7. AuthController — `controller/AuthController.java`

2 API xác thực:

**POST `/api/auth/register`** — Đăng ký:

```
Nhận: { name, email, password }
   → Mã hóa password bằng BCrypt
   → Lưu vào database
   → Tạo token
Trả về: { token }
```

**POST `/api/auth/login`** — Đăng nhập:

```
Nhận: { email, password }
   → Tìm user theo email
   → So sánh password
   → Tạo token
Trả về: { token }
```

---

## Dependencies đã thêm vào `pom.xml`

| Dependency | Chức năng |
|---|---|
| `spring-boot-starter-security` | Spring Security framework |
| `jjwt-api` | API tạo/đọc JWT token |
| `jjwt-impl` | Implementation của jjwt |
| `jjwt-jackson` | Serialize/deserialize JSON trong token |

---

## Test API

### Đăng ký

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Jacky", "email": "jacky@gmail.com", "password": "123456"}'
```

Kết quả:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Đăng nhập

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "jacky@gmail.com", "password": "123456"}'
```

### Gọi API cần xác thực (kèm token)

```bash
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

### Gọi API không có token → Bị chặn

```bash
curl http://localhost:8080/api/users
# → 403 Forbidden
```

---

## Tóm tắt luồng hoàn chỉnh

```
[Đăng ký / Đăng nhập]
Client gửi email + password
    → AuthController nhận
    → Kiểm tra / lưu user
    → JwtUtil tạo token
    → Trả token về client

[Gọi API khác]
Client gửi request + header "Authorization: Bearer <token>"
    → JwtAuthenticationFilter chặn request
    → JwtUtil kiểm tra token
    → Hợp lệ → cho đi tiếp vào Controller
    → Không hợp lệ → trả 403 Forbidden
```
