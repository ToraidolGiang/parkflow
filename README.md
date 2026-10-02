# ParkFlow (khung trắng cho vòng chung kết)

Hệ thống dự báo và điều phối bãi xe trường đại học. **Repo này là khung code trắng**: đã cài sẵn cơ sở dữ liệu, thư viện, cấu hình, CI và tài liệu thiết kế; **chưa có dòng logic nghiệp vụ nào**. Toàn bộ logic được viết và commit trong giờ thi theo [kế hoạch ngày thi](docs/ke-hoach-ngay-thi.md).

## Có gì sẵn

| Phần | Nội dung |
|---|---|
| `backend/` | Spring Boot 4.1 (Java 17): web, JPA, Flyway, validation, PostgreSQL, Lombok, thư viện Web Push; lớp khởi động và 13 gói rỗng có mô tả trách nhiệm (`package-info.java`); một bài test khởi động |
| `frontend/` | React 19, Vite, TypeScript, React Router, Vitest và Testing Library, ESLint, Playwright; khung `App` trống |
| `docker-compose.yml` | PostgreSQL 16 có sẵn hai CSDL `parkflow` (chạy) và `parkflow_test` (kiểm thử) |
| `.github/workflows/ci.yml` | CI: backend `./mvnw verify` với Postgres, frontend `lint`, `test`, `build` |
| `docs/thiet-ke/` | Tài liệu thiết kế (không phải mã): giải pháp dữ liệu, thuật toán dự báo và phân luồng, hợp đồng API và `openapi.yaml`, dữ liệu mẫu, kịch bản test, lời nhắc AI |
| `scripts/` | `bao-cao-commit.sh` (nhịp commit), `kiem-tra-cua-so-thi.sh` (kiểm chứng liêm chính lịch sử) |

## Chạy lần đầu (khoảng 5 phút)

Cần: Docker Desktop, JDK 17, Node 24 (xem `.nvmrc`).

```bash
cp .env.example .env                 # điền DB_PASSWORD nếu đổi
docker compose up -d db              # PostgreSQL 16 (cổng 5432; nếu bận, đặt DB_PORT=5433 và DB_URL tương ứng)

# Backend (cổng 8080)
cd backend && ./mvnw verify          # build và chạy test (Windows: mvnw.cmd)
./mvnw spring-boot:run

# Frontend (cổng 5173, tự chuyển /api sang 8080)
cd frontend && npm ci && npm test && npm run dev
```

Kiểm thử backend dùng CSDL riêng: đặt `DB_URL=jdbc:postgresql://localhost:5432/parkflow_test` khi chạy `./mvnw verify` nếu muốn tách khỏi dữ liệu chạy ứng dụng.

## Làm việc nhóm

- Kế hoạch, phân công, lịch commit từng người: [`docs/ke-hoach-ngay-thi.md`](docs/ke-hoach-ngay-thi.md)
- Quy ước commit và nhánh: [`docs/quy-uoc-commit.md`](docs/quy-uoc-commit.md)
- Commit khung trắng được gắn tag `khung-trang`; mọi thứ sau tag đó là công việc trong giờ thi.
