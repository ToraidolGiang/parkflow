# Cấu hình trường từ JSON seed

Tham khảo `CampusConfigService`, `CampusConfigController` và bootstrap trong repo ParkFlow. JSON seed dùng file `simulator/data/campus_config.json` của repo chung kết.

Khi khởi động, `CampusConfigBootstrap` gọi `CampusConfigService.seedIfAbsent()` qua bean Spring. Bãi và cổng được nạp vào `lots` và `forecast_gates`; tỷ lệ cổng và bãi thay thế vào `forecast_gate_shares` và `lot_alternates`. Tòa nhà, tuyến, thông tin bản đồ và metadata được giữ đầy đủ trong cột JSONB `campus_config.body`.

Cả lần seed chạy trong một giao dịch với khóa PostgreSQL. Có bản cấu hình rồi thì không nạp lại. Bãi/cổng đã tồn tại được giữ nguyên, không thay đổi số xe hoặc lịch sử. Cấu hình sai tham chiếu bị từ chối trước khi ghi. Không có chức năng cập nhật PUT trong thay đổi này.

`GET /api/config` trả JSON đã lưu trong CSDL và thêm `count` hiện tại cho từng bãi. Giang có thể dùng `current()` hoặc `currentWithCounts()` từ service, không phải đọc lại file JSON.

Migration mới: `V3__campus_config.sql`. Chưa đổi các migration đã có. JSON được đóng gói qua cấu hình resource trong `backend/pom.xml`.

Kiểm thử: `CampusConfigServiceTest`. Chạy đầy đủ ứng dụng cần cấu hình đúng tài khoản PostgreSQL trong `.env`.
