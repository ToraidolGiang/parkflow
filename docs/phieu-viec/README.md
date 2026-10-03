# Phiếu việc ngày thi

Mỗi người một phiếu: [Hào](hao.md) · [Tuấn Anh](tuan-anh.md) · [Quyên](quyen.md) · [Giang](giang.md). Chi tiết từng commit và phụ thuộc nằm ở [`../ke-hoach-ngay-thi.md`](../ke-hoach-ngay-thi.md) (bảng phân công mục 2 là chuẩn; cột "phụ trách" trong tài liệu thiết kế `thiet-ke/` là bản cũ, bỏ qua).

**Mốc tuyệt đối (T0 = 07:30, hết giờ ≈ 14:30):** M1 09:00 · M2 10:30 · M3 12:00 · M4 13:15 · đóng băng 13:30.

## 15 phút đầu (mọi người)

```bash
git clone https://github.com/ToraidolGiang/parkflow-chung-ket.git && cd parkflow-chung-ket
git config user.name "<Họ tên>" && git config user.email "<email GitHub đã xác minh>"   # đúng tài khoản của mình
cp .env.example .env && docker compose up -d db                                         # PostgreSQL 16
cd backend && ./mvnw verify            # Windows: mvnw.cmd verify ; cd ../frontend && npm ci && npm test
```

Chạy xong cả hai lệnh kiểm tra thì bắt đầu việc đầu tiên trong phiếu (việc đầu tiên của mỗi người **không cần chờ ai**).

## Nhịp làm việc

1. `git switch -c feat/<tên>-<việc>` từ `main` mới nhất. Mỗi việc một nhánh nhỏ.
2. Viết test trước hoặc cùng lúc; chạy `./mvnw test -Dtest=...` hoặc `npm test` cho phần mình.
3. `git add -p` (xem lại từng đoạn) rồi `git commit -m "loại(phạm-vi): mô tả ngắn"`. Commit khi xong một phần trọn vẹn, khoảng 30 đến 45 phút một lần, **ít nhất 2 commit mỗi giờ**.
4. `git pull --rebase origin main && git push -u origin HEAD`, rồi `gh pr create --fill` (hoặc nút trên GitHub). Chờ CI xanh, một người khác xem nhanh 5 phút, merge bằng **"Rebase and merge"** (không "Squash").
5. Không đẩy thẳng lên `main`, không sửa dấu thời gian, không viết lại lịch sử đã đẩy.
6. Mỗi mốc: `scripts/bao-cao-commit.sh "2026-10-03T07:30:00+07:00"` để xem nhịp commit cả đội.

## Dùng trợ lý AI cho phần của mình (được phép)

Dán khung lời nhắc dưới đây, điền chỗ `<...>`; **bạn đọc lại, chạy test và chịu trách nhiệm** trước khi commit:

> Dự án ParkFlow (điều phối bãi xe trường đại học): backend Spring Boot 4 (Java 17, Flyway, JPA, PostgreSQL), frontend React 19 + Vite + TypeScript. Đọc `docs/thiet-ke/<tệp>` mục `<mục>` và hợp đồng API ở `docs/thiet-ke/04-api.md`. Việc: `<mô tả một việc nhỏ trong phiếu>`. Ràng buộc: chỉ sửa trong `<thư mục của tôi>`; viết test (`<JUnit|Vitest>`) cho các ca `<liệt kê>`; giữ đúng tên trường của hợp đồng; code ngắn gọn, bình luận tiếng Việt khi cần giải thích vì sao; không thêm thư viện mới. Làm xong cho tôi lệnh chạy test.

## Khi bị kẹt hoặc trễ mốc

Nhắn cả nhóm ngay, đừng im lặng: người có việc đang chờ mình (cột "phụ thuộc" trong kế hoạch) cần biết. Người rảnh đứng ra giúp; **không commit hộ bằng danh nghĩa người khác**, chỉ mở PR gắn tên họ hoặc ngồi cùng họ viết.
