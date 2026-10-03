# Phiếu việc: Giang (nền móng, ingest, AI, triển khai, tích hợp)

**Thư mục chỉ mình sửa:** `backend/.../{common,domain,repository,ingest,ai}`, `controller/{Event,Lots,Config,Ai}Controller`, `db/migration/V1*`, `deploy/**`, `.github/**`. Tài khoản GitHub: `ToraidolGiang`.

## Việc theo thứ tự (hạn tuyệt đối)

| Mã | Hạn | Nội dung |
|---|---|---|
| G1–G2 | 08:05 | Xác nhận khung chạy; lược đồ `V1` (`lots`, `events`, `forecast_gates`) + thực thể, repository |
| G3–G7 | 09:00 | Lỗi API thống nhất, đồng hồ, kiểm tra hợp lệ, ghi sự kiện chống trùng, `POST /api/events`, test |
| G8 | 09:35 | `GET /api/lots/status`, `GET /api/config` |
| tích hợp | 09:35–10:30 | Gộp M1/M2, sửa lệch hợp đồng, rà PR của cả nhóm (5 phút mỗi PR) |
| G10–G14 | 10:30–12:30 | Trợ lý AI: Mock, số liệu, Groq/Gemini, `POST /api/ai/chat`, chặn số sai |
| G19 | 12:30 | AI ánh xạ cột CSV kèm độ tin cậy |
| G20–G22 | 12:50–13:30 | Triển khai AWS: Dockerfile, compose production + Caddy, script phát hành (thử bằng Docker local) |
| G23 | 13:50 | AI tạo quy tắc từ tiếng Việt, báo cáo tuần |
| G15–G18 | rải rác, xong 14:25 | Tích hợp M3/M4, kịch bản demo, sửa lỗi cuối, tag `v-chung-ket`, chạy `kiem-tra-cua-so-thi.sh` |

## Triển khai (làm như hiện tại)
Một máy AWS EC2 chạy Docker Compose (Postgres + backend + Caddy, HTTPS qua tên miền `<ip>.sslip.io`), phát hành bằng một lệnh qua SSH. Máy chủ và đường dẫn khóa là **tham số khi chạy**, không ghi vào repo. Chỉ phát hành khi luồng đã chạy (khoảng M3–M4); script giữ bản cũ để quay lại.
