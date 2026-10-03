# Kế hoạch kiểm thử và danh sách kiểm tra demo

Quyên phụ trách. Tài liệu này là việc **Q1**: tiêu chí “xong” từng mảng, ma trận kịch bản, lệnh tự động, và checklist demo. Đáp án số liệu lấy từ [`thiet-ke/03-thuat-toan-du-bao-phan-luong.md`](thiet-ke/03-thuat-toan-du-bao-phan-luong.md). Cấu hình trường và ngày demo lấy từ [`thiet-ke/05-du-lieu-mau.md`](thiet-ke/05-du-lieu-mau.md). Hợp đồng API: [`thiet-ke/04-api.md`](thiet-ke/04-api.md), [`thiet-ke/openapi.yaml`](thiet-ke/openapi.yaml). Kịch bản chi tiết gốc: [`thiet-ke/06-kich-ban-test.md`](thiet-ke/06-kich-ban-test.md).

Ghi kết quả vào cột **Kết quả** (`✅` / `❌` + một dòng lỗi). Chạy lần 1 trên localhost; lần 2 trên bản đã triển khai (nếu kịp).

## 0. Mục tiêu và nguyên tắc

Mục tiêu giờ thi: **luồng demo bắt buộc chạy trọn trong ≤ 5 phút**, số trên dashboard / dự báo / kế hoạch / `/sv` / trợ lý **khớp nhau**. AI chỉ diễn giải số đã tính; không có `card_hash` / `card_id` trong FACTS hay log gửi mô hình.

| Lớp | Khi nào chạy | Ai | Tiêu chí đạt |
|---|---|---|---|
| Đơn vị + hợp đồng (`./mvnw verify`, `npm test`) | Mỗi PR; mỗi mốc M1–M4 | Chủ mã | CI xanh |
| Dữ liệu (`node simulator/verify.mjs`) | Sau Q2 và mỗi lần sinh lại CSV | Quyên | Đủ ngày, đủ ca, đủ tuyến, sức chứa không đổi |
| Phát lại (`replay` + tích hợp ingest) | Sau Q4/Q5 | Quyên, Giang | Lượt 1 accepted; lượt 2 toàn duplicate; số xe cuối không đổi |
| Thủ công theo bảng TC-* | Sau M2 (ingest+sim), M3 (plan+sv), đóng băng | Quyên (ghi), chủ mã (sửa) | Mọi mục **Bắt buộc** ✅ |
| E2E Playwright (Q10) | Sau M3 | Quyên / Tuấn Anh | Ba luồng chính xanh |
| Demo bấm giờ (3 lượt) | T+5:45 và T+6:30 | Cả đội | ≤ 5 phút, không lỗi chặn demo |

Cắt phần **Nên có / Làm đủ** nếu trễ: vẫn phải giữ ingest, giả lập, dự báo–phân bổ, dashboard, `/sv`, trợ lý Mock.

## 1. Môi trường

- Docker Postgres, JDK 17, Node 24, múi giờ `Asia/Ho_Chi_Minh`.
- Sao chép `.env.example` → `.env`. `EVENTS_API_KEY` phải chấp nhận khóa giả lập. `SIM_CONTROL_API_KEY` riêng cho start/stop/spike.
- Hai CSDL: `parkflow` (chạy) và `parkflow_test` (kiểm thử). Khi `verify` đặt `DB_URL` sang CSDL test nếu muốn tách dữ liệu.
- Ngày demo mặc định: **22/09/2026**, bắt đầu **06:00**, tốc độ **×30**. Đỉnh vào ca 1 khoảng 06:10. Bật spike trước lượt vào nếu cần chứng minh đột biến.
- Nạp lịch sử **trước** khi gọi forecast. Start sim không tự nạp lịch sử và không reset số xe.
- Profile / cấu hình HUCE: bốn nhà xe **A / B / C1 / C2** sức chứa **1.450 / 1.450 / 300 / 300**; cổng `MAIN` (Giải Phóng) và `EAST` (Trần Đại Nghĩa). PUT `/api/config` từ `simulator/data/campus_config.json` nếu DB đã có config cũ (build mới không ghi đè).

```bash
docker compose up -d db
cd backend && ./mvnw verify && ./mvnw spring-boot:run
cd frontend && npm ci && npm test && npm run lint && npm run build && npm run dev
node simulator/verify.mjs
```

## 2. Tiêu chí xong từng mảng (theo phạm vi ngày thi)

Cột **Mốc** là lúc muộn nhất phải kiểm được trên máy tích hợp.

### 2.1 Bắt buộc

| Mảng | Chủ | Mốc | Xong khi |
|---|---|---|---|
| Ingest `POST /api/events` | Giang | M1 | TC-D1…D4; một hoặc mảng ≤ 500; `X-Api-Key` sai → 401; trùng `event_id` → `duplicate`, `count` không đổi |
| Giả lập phát lại + spike | Quyên | M2 | `start` ngày có log, tốc độ 1–3600; start khi đang chạy → 409; `stop` về giờ thật, **không xóa dữ liệu**; spike `{lotId,pct}` chỉ thêm lượt **vào** sau khi bật; `pct:0` tắt tạo thêm; sai key → `state.error`, không báo gửi thành công |
| Dự báo | Hào | M2 | `GET /api/forecast?lotId=&date=` có `points`, `peak`, `thresholds`; thiếu lịch sử → `NO_HISTORY`; đường nền = trung vị cùng thứ; k kẹp 0,7–1,5 |
| Phân bổ + chống nhảy | Hào | M3 | `GET /api/plan/current`: Σ before = Σ after; đích < 90% trước phân bổ; mục tiêu 96%; lệch < 15 xe **và** < 3% → không sinh bản mới; có `shortfall` khi thiếu chỗ |
| Dashboard 5 giây | Tuấn Anh | M2 | Poll `/api/lots/status`; màu green < 80% · yellow 80–<100% · red 100–<120% · critical ≥ 120%; `clock.mode=sim` khi giả lập chạy; số nhảy ≤ 10 giây sau start |
| `/sv` | Tuấn Anh + Quyên | M3 | Chọn tòa + giờ → gợi ý bãi/cổng + lý do; một `sessionId` một suất (poll không tăng `used`); kế hoạch `pending` không dùng; chỉ đường đủ bước + Maps |
| Trợ lý AI | Giang + Tuấn Anh | M3 | Câu hỏi tiếng Việt; số khớp `/api/forecast` và `/api/plan/current`; Mock bật được; loading; trả lời ≤ 8 giây |

### 2.2 Nên có / Làm đủ (kiểm nếu còn giờ)

| Mảng | Chủ | Xong khi |
|---|---|---|
| Duyệt kế hoạch | Hào, Tuấn Anh | Bản đầu `draft`; trong khung (chuyển ≤ 150, đích đã cho phép, ≤ 100%) → `auto_applied`; ngoài khung → `pending_approval` + nút Duyệt; sinh viên giữ bản cũ đến khi duyệt |
| Chất lượng dữ liệu | Hào | `out` khi count=0 → không âm, `dataFlag: suspect`; mất tín hiệu 5 phút trong 06:30–09:00 → `signal_lost` + “ước tính”; có tín hiệu lại thì hết cờ |
| Nhập CSV | Quyên, Tuấn Anh | Tệp mẫu nạp được; dòng sai được báo; chạy lại không nhân sự kiện |
| AI ánh xạ cột | Giang, Tuấn Anh | `vendor_sample.csv`: cột ≥ 85% qua; cột < 80% bắt tick xác nhận |
| Cloud / HTTPS | Giang | Trang chủ HTML thật, `/api/health` JSON, `/sv` mở được — không phải trang placeholder |
| Bảo vệ / nhiệm vụ / Web Push | (nếu làm thêm) | Vai guard chỉ thấy việc và bãi cổng mình; không lộ kế hoạch toàn trường |

## 3. Đáp án tính tay HUCE (đối chiếu phân bổ)

Giả định chung: kế hoạch 07:00–07:45 (4 khung 15 phút); hai cổng tải nền 50%, năng lực 200 xe/15 phút. Mục tiêu 96%: **1.392 / 1.392 / 288 / 288**. Đích phải dưới 90% trước phân bổ. A ưu tiên B → C1 → C2. Tải cổng dưới đây là đầu vào tính tay, không phải tải đo từ CSV.

### HT-1 — Một bãi quá tải, còn đủ chỗ

- Trước: A=1.500, B=1.000, C1=200, C2=180; tổng **2.880**.
- Chuyển A → B **108** xe; cổng mặc định B = EAST.
- Sau: **1.392 / 1.108 / 200 / 180**; `shortfall=0`; tải EAST ≈ **63,5%**.
- Bản đầu là `draft`. Nếu đã có bản duyệt cho phép B, hạn 150 xe và 100% thì bản này nằm trong khung tự áp dụng.

### HT-2 — A tăng đúng 20%, vượt khung

- Đây là phép tính đỉnh **+20%** để đối chiếu. Nút spike thêm lượt vào theo xác suất 20% — **một lần chạy không nhất thiết ra đúng số này**.
- A=1.800; B=1.000; C1=200; C2=180; tổng **3.180**.
- Chuyển 408: B nhận 392, C1 nhận 16. Sau: **1.392 / 1.392 / 216 / 180**; `shortfall=0`.
- Tổng chuyển **408 > 150** → `pending_approval` nếu đang có bản áp dụng. Sinh viên dùng bản cũ đến khi duyệt.
- EAST ước lượng ~99% sau A→B. Thuật toán chọn cổng tải thấp hơn nhưng **chưa giảm quota** khi cổng vượt 90% — không mô tả đây là đã giải quyết nghẽn cổng.

### HT-3 — Cả bốn quá tải (đỉnh CSV 16/09 = kịch bản demo 22/09)

- Trước: **1.520 / 1.470 / 320 / 320**, tổng **3.630**.
- Nhu cầu về 96% = 270 xe; không có đích hợp lệ → `moves=[]`, `shortfall=270`; sau giữ nguyên.
- Tổng sức chứa 3.500 nên vượt thiết kế **130 xe** (khác thiếu 270 để đạt 96% = 3.360).
- Không hứa với sinh viên rằng phân luồng nội bộ sẽ tạo thêm chỗ.

## 4. Ma trận kịch bản

### 4.1 Dữ liệu vào — Giang, sau M1

| Mã | Kịch bản | Bước | Kết quả mong đợi | Kết quả |
|---|---|---|---|---|
| TC-D1 | Sự kiện hợp lệ | Một `in` bãi A, đúng key | `accepted: 1`; `count` A +1; `lastEventAt` cập nhật | |
| TC-D2 | Chống trùng | Gửi lại đúng TC-D1 | `duplicate: 1`; `count` không đổi | |
| TC-D3 | Sai API key | Key sai | HTTP 401, không ghi | |
| TC-D4 | Bãi không tồn tại | `lot_id: "Z"` | `rejected` có `UNKNOWN_LOT` | |
| TC-D5 | Số âm | `out` khi count=0 | `count` giữ 0, `dataFlag: suspect` | |
| TC-D6 | Mất tín hiệu | 06:30–09:00 sim, ngừng gửi B 5 phút | B `signal_lost`, dashboard cảnh báo, số “ước tính” | |
| TC-D7 | Có tín hiệu lại | Gửi bù 5 phút đó | Hết cờ, `count` khớp tổng sự kiện | |
| TC-D8 | Phiên treo | `in` > 24h không `out` | Job 00:00 đóng; không tính vào ngày mới | |

### 4.2 Dự báo và phân luồng — Hào, sau M3

Bảng TC-01…TC-03 dưới đây là bộ **A/B/C/D 500/350/400/300** trong tài liệu thuật toán (ví dụ có đáp án). Khi chạy HUCE, **đối chiếu HT-1…HT-3**, không bắt số 565/480.

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-01 | Kế hoạch nền (bộ ví dụ A–D) | A 565→480 (−85); B 311→336 (+25); C 324→384 (+60); D không chọn vì cổng quá tải; mở cổng sớm; `shortfall=0` | |
| TC-02 | Bảo toàn tổng xe | Σ before = Σ after | |
| TC-03 | Trần mục tiêu | Sau phân bổ mọi bãi ≤ 96% **nếu còn chỗ** | |
| TC-04 | Hệ số k | 07:20, `count=460`, nền 06:00=20, nền 07:20=430, `peak_f=565` → `k≈1,073`, `peak_n≈605` | |
| TC-05 | Thiếu chỗ | `peak_A=605`: chuyển 115, `shortfall=10`, chờ duyệt | |
| TC-06 | Chống nhảy | `peak_A` lệch 8 xe | **Không** sinh bản mới | |
| TC-07 | Trong khung | Tổng chuyển ≤ 150, đích đã cho phép, ≤ 100% | `auto_applied` | |
| TC-08 | Ngoài khung | Như TC-05 / HT-2 | Nút Duyệt; `/sv` vẫn bản cũ | |
| TC-09 | Duyệt | Bấm Duyệt | `approved`; lần mở `/sv` sau có banner `changed` một lần | |
| TC-H1 | HUCE một bãi đầy | Khớp HT-1 | |
| TC-H2 | HUCE +20% | Khớp hướng HT-2 (không đòi đúng 408 trên một lần spike) | |
| TC-H3 | HUCE bốn bãi đầy | Khớp HT-3: `moves=[]`, `shortfall=270` | |

### 4.3 Giả lập — Quyên, sau M2

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-SIM1 | Start | Ngày có dữ liệu, speed 30; `clock.mode=sim`; dashboard nhảy số ≤ 10 giây | |
| TC-SIM2 | Start trùng | Start khi đang chạy | 409 | |
| TC-SIM3 | Stop | Đồng hồ về giờ thật; dữ liệu ngày đó còn | |
| TC-SIM4 | Spike A +20% | Chỉ thêm `in` A sau khi bật; thẻ mới; có `out` tương ứng nếu phiên gốc có `out` | |
| TC-SIM5 | Tắt spike | `pct:0`; các `out` đã xếp hàng vẫn gửi | |
| TC-SIM6 | Sai key điều khiển | Dừng `state.error`; không báo sent thành công | |
| TC-SIM7 | Chạy lại | Cùng `event_id` ổn định → backend `duplicate`, không nhân xe | |

### 4.4 Sinh viên `/sv` — Quyên + Tuấn Anh, sau M3

Chưa có kế hoạch: H3 lúc 06:30 → gợi ý C1, cổng đã mở, `planVersion=null`, không bịa GPS.

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-S0 | Chưa có plan, H3 06:30 | C1, cổng mở, `planVersion=null` | |
| TC-S1 | Gần bãi không quá tải | Tòa gần C / C1 khi C chưa đầy → gợi ý bãi gần | |
| TC-S2 | Gần bãi sắp đầy | Tòa gần A khi A trong kế hoạch điều hướng → bãi thay + cổng + lý do có giờ đầy | |
| TC-S3 | Hết quota | Hết suất A→đích thì người sau nhận bãi tiếp theo trong thứ tự ưu tiên | |
| TC-S4 | Một phiên một suất | Cùng `sessionId` poll lại | `used` không tăng | |
| TC-S5 | Chống nhảy phía SV | Cùng `sessionId` sau khi plan đổi | Giữ gợi ý cũ + banner `changed` một lần | |
| TC-S6 | Chỉ đường | Sơ đồ tô cổng → bãi, đủ bước, có phút đi bộ | |
| TC-S7 | Google Maps | `google.com/maps/dir/?api=1&destination=...` đúng cổng | |
| TC-S8 | Mobile 360–430 px | Không cuộn ngang, nút đủ lớn | |

### 4.5 AI — Giang + Tuấn Anh, sau M3

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| TC-A1 | Hỏi mẫu | “Sáng mai có bãi nào nguy cơ quá tải không?” | Số khớp `/api/forecast` và `/api/plan/current`; có loading | |
| TC-A2 | Gemini lỗi | Key Gemini sai | Chuyển Groq, `source: groq` | |
| TC-A3 | Mock | Bật công tắc Mock | Trả ngay, `source: mock`, UI “Dữ liệu mẫu” | |
| TC-A4 | Ánh xạ cột | `vendor_sample.csv` | Cột ≥ 85% qua; cột < 80% bắt xác nhận | |
| TC-A5 | Không lộ PII | Log request AI | Không có `card_hash` / `card_id` | |
| TC-A6 | Số sai bị sửa | Mô hình nêu % không có trong FACTS | Máy chủ thay câu từ số liệu, `corrected: true` | |

## 5. Kiểm thử theo mốc tích hợp

Cả đội dừng ~10 phút, merge `main`, chạy đúng mục này rồi mới làm tiếp.

| Mốc | Giờ | Kiểm tra tối thiểu | Đạt khi |
|---|---|---|---|
| M1 | T+1:30 | TC-D1, D2, D3; Flyway lên; frontend khung mở được | Ingest sống |
| M2 | T+3:00 | TC-SIM1, dashboard nhảy số, `GET /api/forecast` không 500 (hoặc `NO_HISTORY` rõ ràng) | Sim + status + forecast nối được |
| M3 | T+4:30 | HT-1 hoặc TC-01 trên API; duyệt hoặc draft hiện UI; `/sv` trả JSON gợi ý | Hai phía quản trị–sinh viên |
| M4 | T+5:45 | Một lượt demo đầy đủ (mục 6) | ≤ 5 phút trên localhost |
| Đóng băng | T+6:30 | Ba lượt demo; `verify` + `npm test`; script cửa sổ thi | Không lỗi chặn thuyết trình |

## 6. Danh sách kiểm tra demo (bấm giờ, 3 lượt)

Chuẩn bị trước (không tính giờ thuyết trình): DB sạch hoặc script reset ngày demo; lịch sử đã nạp; config HUCE; Mock AI sẵn nếu mạng kém; một điện thoại / cửa sổ hẹp cho `/sv`.

1. Bật giả lập 22/09 ×30 từ 06:00 → dashboard nhảy số **≤ 10 giây**, `clock.mode=sim`.
2. Bãi A (hoặc bãi nguồn) chuyển đỏ / cảnh báo cao điểm hiện.
3. Trang Dự báo & Đề xuất: số khớp API; nếu bản `draft`/`pending` thì **bấm Duyệt**.
4. Điện thoại: `/sv` chọn tòa gần bãi đang điều hướng → gợi ý thay thế + Chỉ đường.
5. Đột biến +20% bãi A → **≤ 60 giây** có kế hoạch mới (hoặc cùng bản nếu lệch dưới ngưỡng chống nhảy — nói rõ với BGK).
6. Hỏi trợ lý câu mẫu → trả lời **≤ 8 giây**, số khớp màn Dự báo.
7. Tổng **≤ 5 phút**.

Câu nói nếu thiếu chỗ (HT-3): hệ thống **báo shortfall**, không hứa tạo thêm chỗ trong nội bộ bãi.

| Lượt | Máy | Thời gian | Lỗi gặp | Người sửa | Đạt |
|---|---|---|---|---|---|
| 1 — localhost | | | | | |
| 2 — localhost / sửa lỗi | | | | | |
| 3 — bản nộp (local hoặc cloud) | | | | | |

### 6.1 Checklist 60 giây trước khi BGK xem

- [ ] Postgres và backend sống (`/api/health`)
- [ ] Frontend mở được `/`, `/forecast`, `/sv`, `/assistant`
- [ ] Lịch sử đã nạp; `GET /api/forecast?lotId=A&date=2026-09-22` trả JSON
- [ ] Sim chưa chạy hoặc đã reset về 06:00
- [ ] Khóa điều khiển sim đã dán sẵn (không tìm `.env` trước mặt BGK)
- [ ] Mock AI bật nếu không chắc mạng
- [ ] Điện thoại cùng Wi-Fi / URL `/sv`

## 7. Lệnh tự động (ghi nhận, không thay thế bảng TC)

```powershell
node simulator/verify.mjs
node --test simulator/replay.test.mjs simulator/replay.integration.test.mjs
cd backend; .\mvnw.cmd verify
cd frontend; npm test -- --maxWorkers=2; npm run lint; npm run build
```

E2E sau Q10 (ba luồng): quản trị xem dashboard; đột biến thấy điều hướng; sinh viên nhận gợi ý. Chụp màn vào thư mục kết quả e2e nếu đã cấu hình.

Cuối buổi (không phải test sản phẩm, là kiểm chứng liêm chính):

```bash
scripts/bao-cao-commit.sh "2026-10-03T07:30:00+07:00"
scripts/kiem-tra-cua-so-thi.sh "2026-10-03T07:30:00+07:00" "2026-10-03T14:30:00+07:00"
```

## 8. Phạm vi cố ý không chặn demo

- GPS thật / bản đồ dẫn đường thực địa (tọa độ demo, SVG minh họa).
- Một lần spike cho ra đúng +20% đỉnh tính tay.
- Giảm quota khi cổng vượt 90% sau phân bổ.
- Màn bảo vệ, ca trực, Web Push, tự động hóa duyệt — chỉ kiểm nếu đã làm.
- Cloud: nếu vẫn là trang HTTPS placeholder thì ghi **BLOCKED**, demo localhost.
