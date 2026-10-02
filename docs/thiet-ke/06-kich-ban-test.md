# Kịch bản kiểm thử

**Kết quả kiểm tra cloud cho mốc 01/10:** xem [báo cáo và ma trận đầy đủ](12-quyen-01-10-cloud-test.md). Kiểm tra ngày 30/09: tất cả kịch bản chức năng đang BLOCKED do cloud trả trang HTTPS placeholder; không có kịch bản cloud nào được ghi PASS.

> Quyên phụ trách. Chạy lần 1 trên localhost tối thứ 4, lần 2 trên bản cloud tối thứ 5. Ghi kết quả vào cột cuối (✅ / ❌ + mô tả lỗi).
> Đáp án số liệu lấy từ `03-thuat-toan-du-bao-phan-luong.md`.

## Đáp án tính tay ngày 30/09 — cấu hình HUCE

Các bảng TC-01…TC-S cũ bên dưới dùng bộ A/B/C/D 500/350/400/300. Khi chạy HUCE, dùng ba kịch bản này với A/B/C1/C2 có sức chứa **1.450/1.450/300/300**. Bộ kiểm tra đối chiếu: `QuyenScenariosTest`.

Giả định cho cả ba: kế hoạch trong 07:00–07:45 (4 khung 15 phút); hai cổng đều có tải nền 50%, năng lực 200 xe/15 phút. Mục tiêu 96% là **1.392/1.392/288/288**. Bãi đích phải dưới 90% trước phân bổ; A ưu tiên B → C1 → C2. Các tải cổng này là đầu vào tính tay, không phải tải cổng đo từ CSV.

### 1. Một bãi quá tải, còn đủ chỗ

- Trước: A=1.500, B=1.000, C1=200, C2=180; tổng **2.880**.
- Cần chuyển: 1.500 − 1.392 = **108** xe. B còn 1.392 − 1.000 = **392** chỗ ở mục tiêu 96%.
- Chuyển A → B **108** xe, quota 108. Hai cổng hòa tải nên chọn cổng mặc định B là EAST (Trần Đại Nghĩa).
- Sau: **1.392 / 1.108 / 200 / 180**; tổng vẫn **2.880**, `shortfall=0`.
- Tải EAST ước lượng sau điều hướng: 50% + 108/(4×200) = **63,5%**.
- Kế hoạch đầu tiên là `draft`, phải duyệt. Với một bản đã duyệt cho phép B, giới hạn 150 xe và 100%, bản mới này nằm trong khung tự áp dụng.

### 2. A tăng 20%, vượt khung tự áp dụng

- Đây là phép tính đỉnh tăng **chính xác 20%** để đối chiếu; nút spike sinh thêm lượt vào với xác suất 20%, nên một lần chạy không nhất thiết cho đúng số này.
- A=1.500×1,2=**1.800**; giữ B=1.000, C1=200, C2=180; tổng **3.180**.
- Cần chuyển 1.800−1.392=**408**. B nhận 392; còn 16 chuyển C1.
- Sau: **1.392 / 1.392 / 216 / 180**, tổng **3.180**, `shortfall=0`.
- A→B đi EAST (392 xe); tải EAST lên 99%. A→C1 chọn MAIN đang 50%, tăng lên 52% sau 16 xe.
- Tổng chuyển **408 > 150** nên ngoài khung, cần `pending_approval` nếu có bản đang áp dụng. Sinh viên vẫn dùng bản đã duyệt cho tới khi quản trị duyệt bản mới.
- Lưu ý thuật toán hiện tại chọn cổng có tải thấp hơn nhưng chưa giảm quota khi tải sau phân bổ vượt 90% (EAST 99% ở đây); không mô tả kết quả này là đã giải quyết nghẽn cổng.

### 3. Cả bốn nhà xe quá tải, không có đích nhận

- Đỉnh CSV 16/09, cũng là kịch bản demo 22/09: **1.520 / 1.470 / 320 / 320**, tổng **3.630**.
- Nhu cầu giảm về 96%: **128 + 78 + 32 + 32 = 270 xe**.
- Tất cả bãi ≥100%, không có bãi đích hợp lệ. `moves=[]`, `shortfall=270`; sau vẫn **1.520/1.470/320/320**.
- Tổng sức chứa là 3.500, nên vượt thiết kế **130 xe**; con số này khác thiếu 270 xe để đạt mục tiêu 96% (3.360).
- Kế hoạch cần quản trị quyết định; không hứa với sinh viên rằng phân luồng nội bộ sẽ tạo thêm 270 chỗ.

### Kiểm thử student và sim trên HUCE

- `/api/config`: 4 nhà xe, 8 tuyến, 5 ca học. `/api/student/recommendation?building=H3&arrive=06:30&sessionId=...`: chưa có kế hoạch → C1, chọn cổng đã mở, `planVersion=null`, không tạo GPS giả.
- Mỗi phiên/lựa chọn giữ một suất; polling cùng lựa chọn không tăng `used`. Kế hoạch chờ duyệt không được dùng. Bản đã duyệt mới giữ gợi ý cũ nếu còn phù hợp và trả banner `changed` một lần.
- `/api/sim/start`: ngày có dữ liệu, tốc độ 1–3600; chạy lại khi đang chạy → 409. Stop chuyển đồng hồ về giờ thật; không xóa dữ liệu.
- Spike `{lotId:"A",pct:20}` chỉ sinh thêm cho lượt vào A sau khi bật; thẻ mới, có lượt ra tương ứng nếu phiên gốc có lượt ra. `pct:0` tắt tạo thêm, các lượt ra đã xếp hàng vẫn được gửi.
- Sai key ingest/lỗi phản hồi: dừng với `state.error`, không báo đã gửi thành công; start lại sử dụng ID ổn định để backend loại trùng.

## 1. Dữ liệu vào

| Mã | Kịch bản | Bước | Kết quả mong đợi | Kết quả |
|---|---|---|---|---|
| TC-D1 | Nhận sự kiện hợp lệ | Gửi 1 sự kiện `in` Bãi A | `accepted: 1`; `count` Bãi A +1; `lastEventAt` cập nhật | |
| TC-D2 | Chống trùng | Gửi lại đúng sự kiện TC-D1 | `duplicate: 1`; `count` không đổi | |
| TC-D3 | Sai API key | Gửi với key sai | HTTP 401, không ghi gì | |
| TC-D4 | Bãi không tồn tại | `lot_id: "Z"` | `rejected` có `UNKNOWN_LOT` | |
| TC-D5 | Số âm | Gửi `out` cho bãi đang 0 xe | `count` giữ 0, `dataFlag: suspect` | |
| TC-D6 | Mất tín hiệu | Trong 06:30–09:00 (giờ mô phỏng), dừng gửi sự kiện Bãi B 5 phút | Bãi B `signal_lost`, dashboard có cảnh báo, số xe gắn "ước tính" | |
| TC-D7 | Có tín hiệu lại | Gửi bù sự kiện của 5 phút đó | Cờ biến mất, `count` khớp tổng sự kiện | |
| TC-D8 | Phiên treo | Phiên `in` quá 24h không có `out` | Job 00:00 tự đóng; không tính vào số xe ngày mới | |

## 2. Dự báo và phân luồng

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-01 | Kế hoạch nền ngày 22/09 | A 565 → 480 (−85); B 311 → 336 (+25); C 324 → 384 (+60); D 198 không chọn, lý do "Cổng chính quá tải"; Cổng phía Đông mở 06:45; `shortfall = 0` | |
| TC-02 | Tổng xe giữ nguyên | Σ before = Σ after = 1.398 | |
| TC-03 | Không bãi nào vượt | Sau phân luồng mọi bãi ≤ 96% | |
| TC-04 | Dự báo lại đúng công thức | Lúc 07:20 với `count=460`, `count_f(06:00)=20`, `count_f(07:20)=430`, `peak_f=565` → `k ≈ 1,073`, `peak_n ≈ 605` | |
| TC-05 | Thiếu chỗ | Với `peak_A = 605`: chuyển 115 (C +76, B +39) ở mức 100%; `shortfall = 10`; kế hoạch `pending_approval` | |
| TC-06 | Chống nhảy | Đổi dữ liệu để `peak_A` lệch 8 xe | **Không** sinh phiên bản kế hoạch mới | |
| TC-07 | Trong khung → tự áp dụng | Dự báo tăng nhưng tổng chuyển ≤ 150, chỉ dùng B/C, ≤ 100% | `auto_applied`, không cần bấm Duyệt | |
| TC-08 | Vượt khung → chờ duyệt | Như TC-05 | Dashboard hiện nút Duyệt; sinh viên vẫn nhận gợi ý theo bản cũ | |
| TC-09 | Duyệt | Bấm Duyệt ở TC-08 | `status: approved`; `/sv` lần mở tiếp theo có banner `changed` | |

## 3. Màn sinh viên `/sv`

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-S1 | Sinh viên học Giảng đường A | Gợi ý Bãi C (bãi gần, không quá tải) | |
| TC-S2 | Sinh viên học Nhà hành chính (gần A) | Gợi ý Bãi C, Cổng phía Đông, mở 06:45, lý do "Bãi A dự kiến đầy lúc 07:15" | |
| TC-S3 | Hết quota A → C | 60 sinh viên Nhà hành chính (khác `sessionId`) lần lượt mở `/sv` | Người thứ 61 nhận **Bãi B**; người thứ 86 nhận lại **Bãi A** | |
| TC-S4 | Chống nhảy phía sinh viên | Cùng `sessionId` mở lại sau khi kế hoạch đổi | Vẫn giữ gợi ý cũ + banner thông báo | |
| TC-S5 | Chỉ đường | Bấm Chỉ đường | Sơ đồ tô đúng tuyến cổng → bãi, đủ bước, có phút đi bộ | |
| TC-S6 | Google Maps | Bấm "Mở Google Maps" trên điện thoại | Mở app/web Maps chỉ tới đúng vị trí cổng thật | |
| TC-S7 | Giao diện mobile | Mở trên điện thoại 360–430 px | Không cuộn ngang, nút đủ lớn để bấm | |

## 4. AI

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-A1 | Hỏi trợ lý | "Sáng mai có bãi nào nguy cơ quá tải không?" | Trả lời có Bãi A, 113%, 07:15–08:00, 85 xe; số khớp `/api/plan/current`; có loading khi chờ | |
| TC-A2 | Gemini lỗi | Đặt sai key Gemini | Tự chuyển Groq, `source: groq` | |
| TC-A3 | Cả hai lỗi / bật Mock | Bật công tắc Mock | Trả lời mẫu ngay, `source: mock`, UI ghi rõ "Dữ liệu mẫu" | |
| TC-A4 | Ánh xạ cột | Nạp `vendor_sample.csv` | 5 cột ≥ 85%; `PhuongTien` < 80% → bắt tick xác nhận mới cho qua | |
| TC-A5 | Không lộ dữ liệu | Xem log request gửi AI | Không có `card_hash` / `card_id` | |

## 5. Toàn luồng demo (chạy 3 lượt, bấm giờ)

1. Bật trình giả lập → dashboard nhảy số trong ≤ 10 giây
2. Bãi A chuyển đỏ, cảnh báo cao điểm hiện
3. Dự báo & Đề xuất đúng TC-01 → bấm Duyệt
4. Điện thoại: TC-S2 + Chỉ đường
5. Bấm đột biến +20% Bãi A → trong ≤ 60 giây kế hoạch mới xuất hiện
6. Hỏi trợ lý AI → trả lời ≤ 8 giây
7. Tổng thời gian ≤ 5 phút

| Lượt | Thời gian | Lỗi gặp | Người sửa |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
