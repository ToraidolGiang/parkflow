# Phiếu việc: Tuấn Anh (toàn bộ giao diện)

**Thư mục chỉ mình sửa:** `frontend/src/**`. Tài khoản GitHub: `AnhVuu215`.

**Đọc trước (15 phút):** `docs/thiet-ke/04-api.md` (các mục 1 đến 9: hình dạng JSON từng API), `05-du-lieu-mau.md` (dữ liệu để dựng màn hình).

**Cách làm để không chờ ai:** dựng mọi màn hình bằng **dữ liệu giả đúng hợp đồng** trước (tệp JSON trong `src/mock/`), đóng gói sau một hàm `getX()` chọn giữa giả và thật; khi API thật sẵn sàng chỉ đổi một chỗ.

## Việc theo thứ tự (hạn tuyệt đối)

| Mã | Hạn | Commit gợi ý | Ghi chú |
|---|---|---|---|
| T1 | 08:10 | `feat(ui): khung giao diện, định tuyến và máy khách HTTP` | `AppShell`, tuyến `/`, `/forecast`, `/sv`, `/assistant`, `/import`; `request`, `ApiError`, kiểu dữ liệu theo hợp đồng. **Làm ngay.** |
| T2 | 08:40 | `feat(ui): hook thăm dò 5 giây và trạng thái tải, lỗi` | `usePolling`, thành phần Loading, Error, Empty. |
| T3 | 09:20 | `feat(dashboard): thẻ số liệu và danh sách bãi (dữ liệu giả)` | Màu theo ngưỡng 80/100/120%. |
| T4 | 09:50 | `feat(dashboard): biểu đồ mức lấp đầy và danh sách cảnh báo` | SVG, tự viết, không thêm thư viện. |
| T5 | 10:20 | `feat(dashboard): nối API trạng thái bãi thật` | `GET /api/lots/status` (Giang, G8 dự kiến 09:35). |
| T6 | 10:50 | `feat(forecast): trang Dự báo với đường dự báo (dữ liệu giả)` | Đường dự báo so với sau điều hướng, chọn bãi. |
| T7 | 11:20 | `feat(forecast): nối API dự báo thật` | `GET /api/forecast` (Hào, H4 dự kiến 09:30). |
| T8 | 11:50 | `feat(approval): thẻ kế hoạch và nút duyệt` | `GET /api/plan/current`, `POST /api/plan/{v}/approve` (Hào, H10). |
| T9 | 12:20 | `feat(student): màn chọn tòa nhà và giờ đến` | `/sv`, nhớ lựa chọn bằng `localStorage`, ≤ 3 chạm. |
| T10 | 12:50 | `feat(student): màn gợi ý bãi và cổng kèm lý do` | `GET /api/student/recommendation` (Quyên, Q8). Banner khi kế hoạch đổi. |
| T11 | 13:15 | `feat(student): màn chỉ đường hai chặng` | Nút Google Maps (`google.com/maps/dir/?api=1&destination=<lat>,<lng>`), sơ đồ trường tô đường đi (Q9). |
| T12 | 13:40 | `feat(assistant): trang hỏi đáp có biểu đồ khớp số` | `POST /api/ai/chat` (Giang, G13). Công tắc Mock. |
| T14 | 13:50 | `feat(import): màn nhập CSV và ánh xạ cột có độ tin cậy` | `POST /api/import/upload` (Quyên, Q13) và `POST /api/ai/map-columns` (Giang, G19). |
| T13 | 14:00 | `style: hoàn thiện giao diện trên điện thoại và máy tính` | Không tràn ngang ở 390 px, lề trong ≥ 12 px, trạng thái rỗng, bàn phím. |

## Điểm cần ghi nhớ
- Màn quản lý tự cập nhật **5 giây** (thăm dò), không cần SSE.
- Màn sinh viên `/sv` là **web điện thoại, không cần đăng nhập**: một việc mỗi màn, chữ lớn, vùng chạm ≥ 44 px.
- Mọi số hiển thị lấy từ API, không tự tính lại trong giao diện (để chữ và hình khớp nhau).
- Mỗi trang có đủ trạng thái: đang tải, lỗi (có nút thử lại), rỗng.

Khung lời nhắc cho trợ lý AI và nhịp commit: xem `README.md` cùng thư mục.
