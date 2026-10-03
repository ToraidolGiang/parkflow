# Phiếu việc: Hào (dự báo, phân bổ, duyệt, chất lượng)

**Thư mục chỉ mình sửa:** `backend/src/main/java/com/parkflow/backend/{forecast,plan,approval,quality}`, `controller/{Forecast,Plan}Controller`, `db/migration/V2*`. Tài khoản GitHub: `Haohao2309`.

**Đọc trước (15 phút):** `docs/thiet-ke/03-thuat-toan-du-bao-phan-luong.md` (toàn bộ), `04-api.md` mục 3, 4, 5.

## Việc theo thứ tự (hạn tuyệt đối)

| Mã | Hạn | Commit gợi ý | Ghi chú |
|---|---|---|---|
| H1 | 08:10 | `feat(forecast): toán dự báo khung 15 phút` | **Làm ngay, không cần CSDL.** `ForecastMath`: 29 khung 06:00–13:00, trung vị, nội suy, kẹp k trong [0,7; 1,5]. Test thuần. |
| H2 | 08:40 | `feat(forecast): đường nền từ lịch sử cùng thứ` | Cần bảng `events` của Giang (G2, dự kiến 08:05). Trung vị 4 ngày cùng thứ. |
| H3 | 09:10 | `feat(forecast): hệ số lệch k và đường dự báo đã chỉnh` | Neo vào số xe hiện tại; chỉ tính k khi đủ 30 xe. |
| H4 | 09:30 | `feat(controller): GET /api/forecast` | Lỗi `BAD_REQUEST`, `UNKNOWN_LOT`, `NO_HISTORY` đúng hợp đồng. |
| H5 | 09:45 | `test(forecast): kiểm thử dự báo` | Thiếu lịch sử, ngày tương lai, đỉnh. |
| H6 | 10:20 | `feat(plan): mô hình đầu vào đầu ra của phân bổ` | `Move`, `GateAction`, `Plan`, ràng buộc sức chứa, công suất cổng, hạn ngạch. |
| H7 | 11:00 | `feat(plan): thuật toán phân bổ giảm số xe điều hướng` | Bãi nguồn quá tải, bãi đích dưới `WARN`, ít xe điều hướng nhất, báo `shortfall`. |
| H8 | 11:20 | `feat(plan): chống nhảy kế hoạch` | `HYST_ABS` 15 xe, `HYST_PCT` 3%. |
| H9 | 11:45 | `feat(approval): lược đồ và dịch vụ phiên bản kế hoạch` | `V2__ke_hoach.sql`; trạng thái `draft`, `pending_approval`, `approved`, `auto_applied`. |
| H10 | 12:05 | `feat(controller): kế hoạch hiện hành và duyệt` | `GET /api/plan/current`, `POST /api/plan/{v}/approve` kèm phương án khi thiếu chỗ. |
| H11 | 12:25 | `feat(plan): tự lập kế hoạch mỗi phút` | `@Scheduled` 60 giây. |
| H12 | 12:50 | `feat(quality): cờ chất lượng dữ liệu` | Thiếu lượt ra, số vô lý, mất tín hiệu; `GET /api/quality/flags`. |
| H13 | 13:15 | `test(plan): kịch bản đột biến trên giả lập` | Cần nút đột biến của Quyên (Q5, 10:10). |
| H14 | 13:45 | `fix(plan): chỉnh ngưỡng theo kết quả chạy thử` | Cập nhật docs thuật toán nếu đổi tham số. |

## Điểm cần ghi nhớ
- Số liệu do bộ tính tạo ra, **AI chỉ diễn giải** (đừng để mã dự báo phụ thuộc AI).
- Khung dự báo thật là **06:00–13:00**; ngoài khung trả `NO_HISTORY` hoặc đường nền rỗng, không bịa số.
- Người đang chờ mã của bạn: Tuấn Anh (H4 cho trang Dự báo, H10 cho thẻ duyệt), Quyên (H10 cho gợi ý sinh viên), Giang (H4, H10 cho trợ lý). Merge sớm từng phần nhỏ.

Khung lời nhắc cho trợ lý AI và nhịp commit: xem `README.md` cùng thư mục.
