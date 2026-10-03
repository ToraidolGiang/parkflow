package com.parkflow.backend.controller;

import com.parkflow.backend.common.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chế độ ĐĂNG NHẬP TẮT (bản trình diễn): máy chủ coi mọi người là quản lý "demo" và `GET /api/auth/me` trả vai đó, nên giao diện vào thẳng,
 * đúng như giao diện đã thiết kế cho "máy chủ tắt đăng nhập". Chưa có tài khoản, vai bảo vệ hay phiên đăng nhập thật; đổi mật khẩu bị từ chối.
 * Việc thay đổi dữ liệu nhạy cảm (điều khiển giả lập) vẫn cần khóa riêng (`X-Api-Key`), nên không phụ thuộc vào chế độ này.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static Map<String, Object> demoManager() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", 1);
        user.put("username", "quanly");
        user.put("fullName", "Quản lý (bản trình diễn)");
        user.put("role", "manager");
        return user;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return demoManager();
    }

    /** Không kiểm tra mật khẩu vì đăng nhập đang tắt; trả đúng người dùng của `me` để giao diện nhất quán. */
    @PostMapping("/login")
    public Map<String, Object> login() {
        return demoManager();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // không có phiên để hủy
    }

    @PostMapping("/password")
    public void password() {
        throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_DISABLED", "Đăng nhập đang tắt trong bản trình diễn, không có mật khẩu để đổi.");
    }
}
