# Phiếu việc: Quyên (dữ liệu, giả lập, nhập CSV, gợi ý sinh viên, kiểm thử, tài liệu)

**Thư mục chỉ mình sửa:** `backend/src/main/java/com/parkflow/backend/{sim,importer,student}`, `simulator/**`, `frontend/e2e/**`, `README.md`, `docs/**` (trừ `docs/thiet-ke/`), `db/migration/V3*`. Tài khoản GitHub: `quyendang2505`.

**Đọc trước (15 phút):** `docs/thiet-ke/05-du-lieu-mau.md`, `06-kich-ban-test.md`, `04-api.md` mục 6, 7, 8 và mục nhập dữ liệu.

## Việc theo thứ tự (hạn tuyệt đối)

| Mã | Hạn | Commit gợi ý | Ghi chú |
|---|---|---|---|
| Q1 | 08:00 | `docs: kế hoạch kiểm thử và danh sách kiểm tra demo` | **Làm ngay.** Kịch bản test, tiêu chí xong từng mảng. |
| Q2 | 08:45 | `feat(data): bộ sinh dữ liệu lịch sử và ngày demo` | `simulator/generate.mjs`: **đẩy bản 7 ngày trước 08:30** để Hào có dữ liệu, rồi bản nhiều tuần. Lưu lượng mượt, không có đoạn trống quá 15 phút. |
| Q3 | 09:15 | `feat(sim): đọc log và gửi sự kiện tới ingest` | `ReplayLog`, `EventSender` (HTTP, thử lại). Cần `POST /api/events` của Giang (G6, dự kiến 09:00). |
| Q4 | 09:45 | `feat(sim): chạy theo đồng hồ giả lập, bắt đầu và dừng` | `POST /api/sim/start|stop`, `GET /api/sim/state`. |
| Q5 | 10:10 | `feat(sim): nút tăng đột biến +20%` | `POST /api/sim/spike`: thêm xe mới có cặp vào/ra. |
| Q6 | 10:40 | `feat(importer): nạp lịch sử vào CSDL` | `HistoryImporter` theo lô, chạy lại không trùng. |
| Q7 | 11:00 | `feat(campus): cấu hình trường vào CSDL` | Bãi, cổng, tòa nhà, tuyến từ JSON seed; Giang cần cho `GET /api/config`. |
| Q13 | 11:30 | `feat(importer): API nhập CSV lịch sử có kiểm tra cột` | `POST /api/import/upload`, `/confirm`: giới hạn kích thước, kiểm tra cột và giá trị, báo dòng lỗi. |
| Q8 | 11:50 | `feat(student): thuật toán gợi ý bãi và cổng` | Bãi gần nơi học, tránh bãi sắp đầy theo kế hoạch, lý do bằng chữ. Cần kế hoạch của Hào (H10). |
| Q9 | 12:15 | `feat(student): dữ liệu chỉ đường và API sinh viên` | `GET /api/student/recommendation`: tuyến, bước, phút đi bộ. |
| Q10 | 12:50 | `test(e2e): kịch bản Playwright cho ba luồng chính` | Quản trị xem dashboard, đột biến thấy điều hướng, sinh viên nhận gợi ý. |
| Q11 | 13:20 | `docs: README, hợp đồng API cuối và ảnh chụp` | Cách chạy, ảnh màn hình, bảng API đã làm, ghi chú dùng AI. |
| Q12 | 13:50 | `chore: script đặt lại dữ liệu demo` | Xóa dữ liệu ngày demo, nạp lại để demo lặp lại được. |

## Điểm cần ghi nhớ
- Người đang chờ mã của bạn: Hào (Q2 cho dự báo), Giang (Q7 cho `config`), Tuấn Anh (Q8, Q9 cho màn sinh viên, Q13 cho màn nhập CSV). Đẩy bản nhỏ dùng được trước, hoàn thiện sau.
- Trình giả lập gửi sự kiện qua **đúng** `POST /api/events` như một nhà cung cấp thật.
- Dữ liệu mẫu: nhiều tuần, khác nhau giữa các ngày cùng thứ (±10%), có đỉnh quanh giờ vào học; mã thẻ đã băm.

Khung lời nhắc cho trợ lý AI và nhịp commit: xem `README.md` cùng thư mục.
