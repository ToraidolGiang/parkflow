# Thuật toán dự báo và phân luồng

> Đặc tả cho Hào (mục 1–3: số xe, dự báo), Giang (mục 4 và chống nhảy ở mục 6: phân bổ), Hào (khung duyệt ở mục 6), Quyên (mục 5: gợi ý sinh viên; đáp án test). Số liệu trong ví dụ là **dữ liệu mô phỏng** ngày 22/09/2026.
> Nguyên tắc: **số liệu do bộ tính tạo ra, AI chỉ diễn giải.**

## 0. Tham số cấu hình

| Tham số | Giá trị mặc định | Ý nghĩa |
|---|---|---|
| `SLOT` | 15 phút | Khung dự báo |
| `LOOP` | 60 giây | Chu kỳ dự báo lại + phân bổ |
| `YELLOW` | 0,80 | Ngưỡng đổi màu vàng trên giao diện (`level`) |
| `WARN` | 0,90 | Bãi đích chỉ được chọn khi dự báo dưới ngưỡng này (không liên quan màu hiển thị) |
| `OVER` | 1,00 | Ngưỡng quá tải (100% thiết kế) |
| `TARGET` | 0,96 | Mức lấp đầy mục tiêu sau phân luồng (giữ biên an toàn) |
| `TARGET_MAX` | 1,00 | Mức tối đa cho phép khi thiếu chỗ (vượt khung → phải duyệt) |
| `K_MIN`, `K_MAX` | 0,7 · 1,5 | Kẹp hệ số lệch |
| `K_MIN_BASE` | 30 xe | Dưới mức này chưa tính k (k = 1) |
| `HYST_ABS`, `HYST_PCT` | 15 xe · 3% | Ngưỡng chống nhảy kế hoạch |
| `GATE_WARN` | 0,90 | Cổng coi là quá tải khi lưu lượng dự báo > 90% năng lực cổng |
| `OPEN_EARLY` | 30 phút trước khung cao điểm | Giờ mở sớm cổng nhận xe điều hướng |

Cấu hình trường mẫu:

| Bãi | Thiết kế | Tối đa | Cổng | Tòa nhà gần |
|---|---|---|---|---|
| A | 500 | 600 | Cổng chính | Nhà hành chính |
| B | 350 | 420 | Cổng phía Đông | Thư viện |
| C | 400 | 480 | Cổng phía Đông | Giảng đường A |
| D | 300 | 360 | Cổng chính | Khu thể thao |

Bãi thay thế ưu tiên: **A → C, B**; **B → C**. Năng lực cổng: khai báo trong cấu hình (xe/15 phút).

**Tỷ lệ cổng (`gateShare`)**: mỗi bãi khai báo trong cấu hình tỷ lệ xe vào qua từng cổng (tổng = 1). Mẫu: A 0,9 Cổng chính / 0,1 Cổng phía Đông; B 0,1 / 0,9; C 0,1 / 0,9; D 0,9 / 0,1. Đây là **ước lượng do quản trị khai báo** (chưa có dữ liệu đo cổng); khi có camera AI ở cổng thì thay bằng số đo thực. Không khai báo thì coi 100% vào cổng mặc định của bãi.

## 1. Số xe hiện có

```
count(lot, now) = count_đầu_ngày(lot) + Σ in(lot, 00:00→now) − Σ out(lot, 00:00→now)
count_đầu_ngày(lot) = số phiên còn mở lúc 00:00 (có in, chưa có out, chưa quá 24h)
```

Cập nhật tức thì mỗi khi nhận sự kiện hợp lệ; kẹp trong `[0, max]`, vượt thì bật cờ `suspect`.

## 2. Dự báo nền (lập lúc 18:00 hôm trước)

Cho mỗi bãi, mỗi slot 15 phút `s` của ngày mục tiêu:

```
in_f(s)  = trung vị in(s)  của 4 ngày cùng thứ gần nhất
out_f(s) = trung vị out(s) của 4 ngày cùng thứ gần nhất
(tuỳ chọn) nhân hệ số lịch học: sĩ số có tiết đầu ở tòa gần bãi (ngày mục tiêu) / trung bình 4 ngày đó
count_f(s+1) = count_f(s) + in_f(s) − out_f(s),   count_f(06:00) = count_đầu_ngày dự kiến
peak_f = max count_f(s)  và thời điểm đạt đỉnh
```

Ví dụ 22/09: Bãi A đỉnh **565 xe (113%)** trong 07:15–08:00; B 311 (89%); C 324 (81%); D 198 (66%).

## 3. Dự báo lại trong ca (mỗi `LOOP`, 06:00–09:00)

```
net_actual   = count(lot, now) − count(lot, 06:00)
net_forecast = count_f(now)    − count_f(06:00)
k = net_forecast ≥ K_MIN_BASE ? clamp(net_actual / net_forecast, K_MIN, K_MAX) : 1

với mỗi slot tương lai s > now:
  count_n(s) = count(lot, now) + k × (count_f(s) − count_f(now))
peak_n = max count_n(s)
```

Ví dụ lúc **07:20**, Bãi A: `count_f(06:00)=20`, `count_f(07:20)=430`, thực tế `count(07:20)=460`.
→ `k = 440 / 410 = 1,073` → `peak_n = 460 + 1,073 × (565 − 430) ≈ 605` (121%).

## 4. Phân bổ (phân luồng)

### Đầu vào
`peak_i` (dự báo lại) của từng bãi, cấu hình bãi/cổng, danh sách ưu tiên, cổng quá tải, khung đã duyệt (mục 6).

### Thuật toán

```
1. Nguồn  S = { i : peak_i ≥ OVER × cap_i }
   need_i = peak_i − TARGET × cap_i

2. Đích   D = { j : peak_j < WARN × cap_j  và  không có cổng nào của j quá tải }
   (tải cổng: tổng theo bãi của gateShare(bãi, cổng) × k × in_f(bãi, slot); quá tải khi > GATE_WARN × năng lực cổng)
   room_j = TARGET × cap_j − peak_j

3. Với mỗi nguồn i theo need giảm dần:
     duyệt đích theo danh sách ưu tiên của i, rồi các đích còn lại theo room giảm dần
     move = min(need_i, room_j) → ghi {from:i, to:j, vehicles:move}
     need_i −= move; room_j −= move

4. Nếu còn need: lặp lại bước 2–3 với TARGET_MAX thay cho TARGET
     → kế hoạch có "vượt mức mục tiêu", cần duyệt nếu ngoài khung

5. Nếu vẫn còn need: shortfall = Σ need còn lại
     → đưa lựa chọn cho quản trị: (a) dùng bãi có cổng quá tải, (b) chấp nhận nguồn ở mức cao hơn
     → KHÔNG tự đưa phương án giả khả thi

6. Hạn ngạch gợi ý: quota(move) = ceil(vehicles / compliance)
     compliance = tỷ lệ sinh viên làm theo đo được (mặc định 1,0 khi chưa có số đo)

6b. Cổng cho từng move: chọn tuyến (cổng → bãi đích) có mức dùng cổng thấp hơn; hòa thì dùng cổng mặc định của bãi đích.
    Sau phân bổ, cộng số xe điều hướng vào tải của cổng đã chọn; nếu vượt năng lực thì báo vào `shortfall` hoặc cảnh báo cổng.
7. Hành động cổng: cổng có bãi đích nhận xe → mở sớm từ (đầu khung cao điểm − OPEN_EARLY),
     bố trí thêm bảo vệ đến hết khung cao điểm
```

### Ví dụ chuẩn 22/09 (đáp án test TC-01)

| Bãi | Cổng | Thiết kế | Trước | Sau | Thay đổi |
|---|---|---|---|---|---|
| A | Cổng chính | 500 | 565 (113%) | 480 (96%) | −85 |
| B | Cổng phía Đông | 350 | 311 (89%) | 336 (96%) | +25 |
| C | Cổng phía Đông | 400 | 324 (81%) | 384 (96%) | +60 |
| D | Cổng chính | 300 | 198 (66%) | 198 (66%) | không chọn |

- `need_A = 565 − 480 = 85` (tối thiểu 65 để về 100%; lấy 85 để giữ biên an toàn).
- Theo ưu tiên A → C: `room_C = 384 − 324 = 60` → 60; A → B: `room_B = 336 − 311 = 25` → 25.
- D bị loại vì **Cổng chính quá tải** — hướng xe sang D chỉ làm cổng tắc thêm.
- Cổng phía Đông mở **06:45** thay vì 07:00, thêm bảo vệ 06:45–08:00.
- Tổng nhu cầu giữ nguyên 1.398 xe; chỉ điều hướng xe **sắp đến**, không di chuyển xe đã đỗ.

### Ví dụ đột biến (đáp án test TC-05)

Lúc 07:20 dự báo lại A = **605**:
- `need_A = 605 − 480 = 125`. Ở mức 96%: C 60 + B 25 = 85 → còn thiếu 40.
- Nâng lên `TARGET_MAX` 100%: C `400 − 324 = 76`, B `350 − 311 = 39` → 115 → còn thiếu **10**.
- `shortfall = 10` → quản trị chọn: cho 10 xe vào Bãi D (Cổng chính đông thêm) **hoặc** chấp nhận A ở 490/500 (98%).

### 4b. Quy tắc điều phối (`routing`) hạ ngưỡng điều hướng

Mặc định bãi bị coi là nguồn cần giảm tải khi đỉnh dự báo ≥ 100% sức chứa và được đưa về 96%. Quy tắc `routing` đang bật làm thay đổi đúng điều kiện này, không đổi phần còn lại của thuật toán:
- Khớp bãi nếu `scope` là `ALL` hoặc đúng mã bãi, và khung `from`–`to` của quy tắc chứa **giờ đỉnh dự báo** của bãi. Chỉ nhận `metric = forecast_pct`, `op` là `>=` hoặc `>`, `value` từ 50 đến 100 (API trả 400 nếu khác).
- Ngưỡng kích hoạt của bãi = `value`% (nhiều quy tắc khớp thì lấy thấp nhất; không bao giờ cao hơn 100%).
- Mức đưa về = `min(96%, ngưỡng − 4 điểm %)`, giữ nguyên khoảng cách 4 điểm của mặc định. Ví dụ ngưỡng 90% thì đưa về 86%.
- Kế hoạch vẫn qua duyệt và khung duyệt như mọi kế hoạch; `reason` nêu tên quy tắc. Không có quy tắc thì kết quả không đổi (TC-01, TC-05).
- Chống nhảy (mục 6) so mức quá tải theo ngưỡng của bãi. Quy tắc tạo/sửa/xóa có hiệu lực ở vòng tính lại kế tiếp (tối đa một phút).

## 5. Gợi ý cho sinh viên (`/sv`)

```
bãi_mặc_định = bãi gần tòa nhà sinh viên chọn
nếu bãi_mặc_định là nguồn trong kế hoạch ĐÃ DUYỆT:
    lấy move đầu tiên còn quota (theo thứ tự trong kế hoạch)
    → gợi ý bãi đích + cổng + giờ mở + route; quota −1; ghi planVersion
    nếu mọi move hết quota → gợi ý bãi_mặc_định nếu chưa tới max, ngược lại báo "các bãi đều gần đầy"
ngược lại:
    gợi ý bãi_mặc_định
```

- Gợi ý đã phát **giữ nguyên** cho sinh viên đó (lưu theo phiên trình duyệt), kể cả khi kế hoạch đổi → chống nhảy.
- Khi kế hoạch có phiên bản mới áp dụng, sinh viên **mở lại** màn sẽ thấy banner "Bãi C sắp đầy — chuyển sang Bãi B" (`changed = true`).
- Đường đi (`route`) lấy từ cấu hình 8 tuyến cổng → bãi, không tính toán.

## 6. Chống nhảy và khung duyệt

**Chỉ tạo phiên bản kế hoạch mới khi** một trong các điều kiện:
- `|peak_n − peak_cũ| ≥ max(HYST_ABS, HYST_PCT × cap)` ở bất kỳ bãi nào, hoặc
- một bãi đổi mức (vượt/thôi vượt `OVER`), hoặc
- xuất hiện / thay đổi `shortfall`.

**Khung duyệt** (quản trị duyệt kèm kế hoạch lúc 18:00):
```json
{ "maxMoveTotal": 150, "maxTargetPct": 1.00, "allowedDest": ["B", "C"] }
```

| Kế hoạch mới | Trạng thái | Sinh viên |
|---|---|---|
| Nằm trong khung | `auto_applied` — tự áp dụng, ghi log | Nhận gợi ý theo bản mới |
| Vượt khung (dùng D, vượt 150 xe, có shortfall) | `pending_approval` — chờ quản trị bấm Duyệt | Tiếp tục nhận gợi ý theo bản đã duyệt gần nhất |

## 7. Vòng phản hồi (Nên có / mở rộng)

`compliance = số xe thực tế vào bãi đích trong khung / số suất đã phát cho bãi đích`.
Ví dụ chỉ 60% làm theo → lần sau muốn chuyển 60 xe thì phát ~100 suất.

**Cách tính khi triển khai (`GET /api/plan/{version}/compliance`):** log thẻ không gắn với `sessionId` của sinh viên nên không biết xe nào là xe được chuyển. Công thức gốc (mọi xe vào bãi đích chia số suất) sẽ vượt xa 100% vì bãi đích vẫn nhận xe bình thường. Vì vậy `arrived` là **ước lượng**: số xe vào bãi đích vượt dự báo nền trong khung của kế hoạch (tính đến giờ hiện tại), chia cho các bãi nguồn theo tỷ lệ suất đã phát và không vượt quá số suất. Chưa đo được (khung chưa mở, bãi đích chưa có dự báo nền) thì trả 1,0.

## 8. Checklist cài đặt

**Hào**
- [ ] `POST /api/events`: chống trùng, cập nhật `lot_status`, mở/đóng phiên theo `card_hash`
- [ ] Job 00:00: đóng phiên > 24h, đặt `count_đầu_ngày`
- [ ] Job 18:00 (demo: endpoint gọi tay): dự báo nền
- [ ] `@Scheduled(fixedRate = 60000)`: tính k → `peak_n`, đưa cho `plan`
- [ ] Heartbeat: 5 phút không có sự kiện trong 06:30–09:00 → `signal_lost`

**Giang**
- [ ] Phân bổ quota (mục 4), chống nhảy (mục 6), lưu phiên bản kế hoạch
- [ ] Unit test TC-01 và TC-05 phải ra **đúng** số ở mục 4

**Hào (duyệt)**
- [ ] Khung duyệt và `POST /api/plan/{version}/approve` (mục 6): thay đổi vượt khung phải duyệt lại

**Quyên**
- [ ] `GET /api/student/recommendation` theo mục 5, giữ gợi ý cũ cho `sessionId` đã nhận
