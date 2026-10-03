# ParkFlow

Hệ thống dự báo và điều phối bãi xe trường đại học. ParkFlow nhận sự kiện quẹt thẻ xe vào ra, đếm số xe từng bãi theo thời gian thực, dự báo mức lấp đầy theo từng khung 15 phút, đề xuất điều hướng xe khi bãi sắp quá tải, để quản trị duyệt kế hoạch, cho sinh viên xem gợi ý bãi và đường đi, và có trợ lý AI diễn giải số liệu.

## Dùng thử trên cloud

Địa chỉ: https://13-229-38-221.sslip.io

Truy cập:

| Vai | Tài khoản | Mật khẩu | Xem được |
|---|---|---|---|
| Quản lý | `admin` | `Huce@123` | Toàn bộ: bảng điều khiển, dự báo, duyệt kế hoạch, trợ lý AI, nhập dữ liệu, quy tắc, cấu hình |
| Bảo vệ | `vanA` | `Huce@123` | Màn bảo vệ: xe vào từng cổng, mức tải, hành động mở sớm |
| Sinh viên | không cần | | https://13-229-38-221.sslip.io/sv (web điện thoại) |

Ảnh chụp: <img width="1916" height="912" alt="image" src="https://github.com/user-attachments/assets/53c00315-6ee5-4827-bbdc-d78b25be7c9f" />


Tài khoản trên chỉ dùng cho trình diễn. Các thao tác điều khiển giả lập (`/api/sim/*`) cần thêm khóa riêng gửi qua header `X-Api-Key`, nên khách không bấm nhầm được.

## Tính năng

| Nhóm | Làm được gì | API |
|---|---|---|
| Nhận sự kiện | Nhận một hoặc tối đa 500 sự kiện mỗi lần, chống trùng, kiểm tra từng sự kiện, từ chối số thẻ gốc (chỉ nhận mã băm) | `POST /api/events` (header `X-Api-Key`) |
| Trạng thái bãi | Số xe, phần trăm, mức xanh/vàng/đỏ/nghiêm trọng, xe vào 15 phút, cờ chất lượng dữ liệu | `GET /api/lots/status` |
| Dự báo | Đường dự báo từng 15 phút trong khung 06:00 đến 13:00 từ lịch sử cùng thứ, hệ số lệch theo thực tế, đỉnh | `GET /api/forecast?lotId=A&date=2026-10-05` |
| Kế hoạch điều hướng | Phân bổ giảm số xe phải chuyển, chống nhảy kế hoạch, tự lập mỗi phút, quản trị duyệt kèm phương án khi thiếu chỗ, tỷ lệ làm theo gợi ý | `GET /api/plan/current`, `POST /api/plan/{version}/approve`, `GET /api/plan/{version}/compliance` |
| Chất lượng dữ liệu | Cờ thiếu lượt ra, số vô lý, mất tín hiệu | `GET /api/quality/flags` |
| Màn bảo vệ | Xe vào từng cổng, mức tải, dự báo xe vào, hành động mở sớm của kế hoạch đang áp dụng | `GET /api/gates/status` |
| Quy tắc và cảnh báo | Tạo quy tắc từ câu tiếng Việt (xem trước rồi lưu), bật tắt, sửa, xóa; cảnh báo tự kích hoạt mỗi phút | `POST /api/ai/parse-rule`, `/api/rules`, `GET /api/alerts` |
| Trợ lý AI | Hỏi đáp trên số liệu đã tính, chặn phần trăm không có trong số liệu, có công tắc dữ liệu mẫu; báo cáo tuần | `POST /api/ai/chat`, `POST /api/ai/report`, `GET/PUT /api/ai/mode` |
| Nhập dữ liệu | Tải CSV, ánh xạ cột có độ tin cậy (dưới 0,8 bắt người dùng kiểm tra), xác nhận nhập | `POST /api/ai/map-columns`, `/api/import/*` |
| Giả lập | Phát lại một ngày dữ liệu qua đúng `POST /api/events`, tăng đột biến, đồng hồ giả lập | `POST /api/sim/start`, `/stop`, `/spike`, `GET /api/sim/state` |
| Sinh viên | Gợi ý bãi và cổng theo tòa nhà, giờ đến, kèm lý do và đường đi | `GET /api/student/recommendation` |
| Cấu hình, sức khỏe | Cấu hình trường (bãi, cổng, tòa nhà, tuyến); kiểm tra sức khỏe | `GET /api/config`, `GET /api/health` |

Màn hình: Bảng điều khiển `/`, Dự báo và Đề xuất `/forecast`, Trợ lý `/assistant`, Nhập dữ liệu `/import`, Quy tắc vận hành `/rules`, Cấu hình trường `/settings`, màn sinh viên `/sv` (web điện thoại, không cần đăng nhập).

Hợp đồng API đầy đủ: [`docs/thiet-ke/04-api.md`](docs/thiet-ke/04-api.md) và [`docs/thiet-ke/openapi.yaml`](docs/thiet-ke/openapi.yaml).

## Kiến trúc

```
Thẻ quẹt / giả lập --POST /api/events--> backend (kiểm tra, chống trùng, đếm xe) --> PostgreSQL
                                           |  dự báo, phân bổ, quy tắc, trợ lý AI
Trình duyệt (React) <--Caddy (HTTPS, /api/* chuyển vào backend)-->
```

| Thành phần | Công nghệ |
|---|---|
| Backend | Spring Boot 4.1 (Java 17), JPA và `JdbcTemplate`, Flyway (V0 đến V5), kiểm tra dữ liệu, Lombok |
| Cơ sở dữ liệu | PostgreSQL 16 |
| Giao diện | React 19, Vite, TypeScript, React Router, Vitest, Playwright |
| Trợ lý AI | Groq rồi Gemini (hạn chờ 8 giây, kiểm tra schema, chuyển nhà cung cấp), dữ liệu mẫu khi không có khóa |
| Triển khai | Docker Compose (Postgres, backend, Caddy), CI GitHub Actions |

Nguyên tắc: **số liệu do bộ tính tạo ra, AI chỉ diễn giải**; AI không nhận mã thẻ hay dữ liệu từng xe.

## Chạy trên máy

Cần Docker Desktop, JDK 17, Node 20 (xem `.nvmrc`).

```bash
cp .env.example .env                 # điền DB_PASSWORD nếu đổi
docker compose up -d db              # PostgreSQL 16 (cổng 5432; nếu bận, đặt DB_PORT=5433 và DB_URL tương ứng)

# Backend (cổng 8080)
cd backend && ./mvnw spring-boot:run # Windows: mvnw.cmd

# Giao diện (cổng 5173, tự chuyển /api sang 8080)
cd frontend && npm ci && npm run dev
```

Bản đóng gói như môi trường chạy thật (một lệnh, có HTTPS khi đặt tên miền):

```bash
cp deploy/prod.env.example deploy/.env.prod       # điền giá trị; tệp này không được commit
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod up -d --build
```

Cấu hình bằng biến môi trường (không có giá trị thật trong repo): `DB_PASSWORD`, `EVENTS_API_KEY` (khóa nhà cung cấp gửi sự kiện), `SIM_CONTROL_KEY` (khóa điều khiển giả lập), `GROQ_API_KEY`, `GEMINI_API_KEY` (để trống thì dùng dữ liệu mẫu), `AI_MOCK`, `HISTORY_AUTO_IMPORT` (nạp lịch sử khi khởi động, bản đóng gói bật sẵn), `SITE_ADDRESS` (tên miền hoặc `:80`).

Lần khởi động đầu trên CSDL trống: tự nạp cấu hình trường (4 bãi, 2 cổng) và nạp nền khoảng 634 nghìn sự kiện lịch sử trong chừng nửa phút, trong lúc đó trang vẫn dùng được.

## Demo nhanh (khoảng 5 phút)

Đặt `SIM_CONTROL_KEY` rồi gửi qua header `X-Api-Key`.

1. Mở bảng điều khiển `/`: các bãi đang trống.
2. Bắt đầu giả lập ngày demo: `POST /api/sim/start` với `{"date":"2026-10-03","speed":60}` (khoảng 90 giây cho cả ngày demo); số xe tăng dần, đồng hồ chuyển sang giờ giả lập.
3. Tăng đột biến: `POST /api/sim/spike` với `{"lotId":"A","pct":20}` khi giả lập đang chạy.
4. Mở `/forecast`: đường dự báo, đỉnh và phần trăm; xem kế hoạch điều hướng và duyệt.
5. Mở màn bảo vệ (`GET /api/gates/status`) và quy tắc `/rules`: tạo quy tắc từ một câu tiếng Việt, xem trước, lưu; cảnh báo xuất hiện khi thỏa điều kiện.
6. Mở `/assistant`: hỏi "Bãi nào nguy cơ quá tải?"; bật hoặc tắt dữ liệu mẫu bằng công tắc.
7. Mở `/import`: tải một CSV, xem ánh xạ cột có độ tin cậy.
8. Mở `/sv` trên điện thoại: chọn tòa nhà và giờ đến, nhận gợi ý bãi và đường đi.

Đặt lại dữ liệu demo giữa các lượt diễn tập: `bash deploy/reset-demo.sh` (chỉ chạy trên cơ sở dữ liệu demo).

## Kiểm thử

```bash
cd backend && ./mvnw verify          # hơn 200 bài kiểm thử; cần PostgreSQL đang chạy
cd frontend && npm test && npm run lint && npm run build
cd frontend && npx playwright test   # kịch bản đầu cuối
```

CI (`.github/workflows/ci.yml`) chạy backend `./mvnw verify` với Postgres và giao diện `lint`, `test`, `build` cho mọi PR; PR chỉ được gộp khi cả hai xanh. Kế hoạch kiểm thử: [`docs/ke-hoach-kiem-thu-va-danh-sach-kiem-tra-demo.md`](docs/ke-hoach-kiem-thu-va-danh-sach-kiem-tra-demo.md).

## Triển khai

Phát hành lên máy chủ bằng một lệnh, chỉ đẩy mã đã commit:

```bash
PF_HOST=<người-dùng>@<địa-chỉ> PF_SSH_KEY=<đường-dẫn-khóa> bash deploy/release.sh
```

Trên máy chủ: sao lưu cơ sở dữ liệu, dựng ảnh trong lúc bản cũ vẫn chạy, thay bản, kiểm tra sức khỏe, và **tự quay về bản trước nếu bản mới không lên**. Cấu hình bí mật nằm ở `shared/.env.prod` trên máy chủ, không nằm trong repo.

<!-- ĐIỀN: địa chỉ máy chủ và cách vào (nếu muốn ghi) -->
- Máy chủ: **(đang cập nhật)**

## Giới hạn đã biết

- **Đăng nhập đang tắt** (ai mở cũng là quản lý trình diễn). Tài khoản, vai bảo vệ và phiên đăng nhập thật có trong thiết kế nhưng chưa làm trong giờ thi; điều khiển giả lập vẫn cần khóa riêng.
- Dự báo chỉ trong khung **06:00 đến 13:00** và cần lịch sử cùng thứ; ngày chưa có lịch sử (ví dụ Chủ nhật) trả `NO_HISTORY`, trợ lý nói rõ là chưa có dự báo.
- Cờ `signal_lost` sẽ hiện khi giả lập dừng trong giờ hoạt động (bãi im quá 30 phút), đây là hành vi đúng.
- Xe vào cổng là **ước lượng** từ xe vào từng bãi nhân tỷ lệ cổng, chưa có đầu đếm thật ở cổng.

## Đóng góp

Nhóm bốn người, mỗi người một mảng và chỉ sửa trong thư mục của mình:

| Người | Phụ trách |
|---|---|
| Trịnh Trường Giang | Lược đồ cơ sở dữ liệu, nhận sự kiện, trạng thái bãi, trợ lý AI (chat, ánh xạ cột, quy tắc, báo cáo tuần), cảnh báo, màn bảo vệ, triển khai, tích hợp và gỡ lỗi liên mô-đun khi chạy thử bản đầy đủ |
| Đặng Thị Mỹ Quyên | Giả lập, dữ liệu mẫu, nạp lịch sử, cấu hình trường, nhập CSV, gợi ý sinh viên, kiểm thử đầu cuối, tài liệu |
| Bùi Viết Hào | Dự báo, phân bổ và chống nhảy, duyệt kế hoạch, chất lượng dữ liệu |
| Vũ Tuấn Anh | Toàn bộ giao diện |

Giang có nhiều commit hơn vì dựng lõi hạ tầng và xử lý các lỗi nằm giữa các mô-đun (ví dụ migration trùng phiên bản, truy vấn dự báo gây treo hệ thống, lỗi định tuyến) tìm ra khi chạy thử toàn bộ. Mỗi commit mang đúng tên người làm.

### Nguồn gốc mã

<!-- ĐIỀN: nhóm xác nhận và ghi trung thực nguồn gốc mã của từng phần (viết mới trong giờ thi, hay chuyển từ dự án khác của nhóm và có ghi rõ). Xem thêm docs/final-delivery.md để thống nhất cách diễn đạt. -->
**(đang cập nhật)**

### Ghi chú về công cụ AI

Theo xác nhận của Ban Tổ chức, nhóm được dùng công cụ AI hỗ trợ lập trình trong giờ thi. Người viết đọc lại, chạy kiểm thử và chịu trách nhiệm về từng commit mang tên mình.

## Tài liệu

- Kế hoạch ngày thi và phân công: [`docs/ke-hoach-ngay-thi.md`](docs/ke-hoach-ngay-thi.md), phiếu việc từng người: [`docs/phieu-viec/`](docs/phieu-viec/)
- Thiết kế (dữ liệu, thuật toán, API, dữ liệu mẫu, kịch bản kiểm thử, lời nhắc AI): [`docs/thiet-ke/`](docs/thiet-ke/)
- Quy ước commit và nhánh: [`docs/quy-uoc-commit.md`](docs/quy-uoc-commit.md)
- Cấu hình trường, nạp lịch sử, nhập CSV: [`docs/campus-config.md`](docs/campus-config.md), [`docs/history-importer.md`](docs/history-importer.md), [`docs/import-csv-api.md`](docs/import-csv-api.md)
- Đồng hồ giả lập: [`docs/sim-start-stop.md`](docs/sim-start-stop.md)
- Commit khung trắng gắn tag `khung-trang`; mọi thứ sau tag đó là công việc trong giờ thi.
