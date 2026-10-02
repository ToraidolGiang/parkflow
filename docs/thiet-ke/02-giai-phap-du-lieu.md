# Giải pháp thu thập và tích hợp dữ liệu

> Trả lời điểm yếu số 2 của BGK: dữ liệu vào phải **tự động, chuẩn xác, liên tục**. Không có phương án đếm thủ công.

## 1. Hiện trạng tại HUCE

- Sinh viên **quẹt thẻ tại lối ra/vào từng bãi**, không quẹt ở cổng trường.
- → Log của hệ thống giữ xe **đã theo từng bãi**. Vấn đề "cổng dùng chung" (Cổng chính phục vụ cả Bãi A và D) không ảnh hưởng tới việc đếm xe trong bãi.
- Tải của từng cổng hiện **suy ra từ cấu hình** (bãi nào đi cổng nào). Muốn đo trực tiếp thì dùng thiết bị ở tầng mở rộng (mục 5).

## 2. Ba tầng triển khai

| Tầng | Trường hợp | Cách làm | Chi phí |
|---|---|---|---|
| **1. Tích hợp hệ thống có sẵn** | Trường đã có quẹt thẻ (HUCE) | Nhận sự kiện qua API từ phần mềm giữ xe | Gần như 0 |
| **2. Cung cấp kèm module giữ xe** | Trường chưa có hệ thống | ParkFlow cung cấp module ghi nhận vào/ra theo bãi + quản lý thẻ, **phối hợp nhà cung cấp đầu đọc RFID**; ghi đúng schema chuẩn | Thiết bị + triển khai |
| **3. Mở rộng bằng camera AI / cảm biến** | Muốn tăng độ chính xác, cảnh báo sớm | Thiết bị gửi sự kiện vào cùng API | Theo quy mô |

Dù trường ở tầng nào, dữ liệu **đi vào cùng một cửa** → phần dự báo và phân luồng dùng lại nguyên vẹn.

> Lưu ý khi pitch tầng 2: không nói "tự làm phần mềm giữ xe" (BGK sẽ hỏi cấp thẻ, mất thẻ, thu phí…). Nói "module giữ xe cơ bản, phối hợp nhà cung cấp thiết bị".

## 3. Tầng 1 — Hai cách kết nối

| Cách | Khi nào | ParkFlow làm gì |
|---|---|---|
| **Push (webhook)** | Nhà cung cấp hợp tác được | Mở `POST /api/events` để nhận sự kiện ngay khi có lượt quẹt |
| **Pull** | Nhà cung cấp không sửa hệ thống được, nhưng có API đọc hoặc cho quyền đọc DB | Mỗi 1 phút ParkFlow gọi API / đọc DB (chỉ đọc) lấy bản ghi mới |

Cả hai đều tự động hoàn toàn và đi qua cùng bộ chuẩn hóa.

```
Phần mềm giữ xe (push) ─┐
Phần mềm giữ xe (pull) ─┤
Module ParkFlow (tầng 2)┼──► Ingest API ──► Kiểm tra chất lượng ──► lot_status ──► Dự báo & Phân luồng
Camera / cảm biến (tầng 3)┤      (schema chuẩn)
CSV lịch sử (nạp 1 lần) ─┘
```

### Sự kiện chuẩn

```json
{
  "event_id": "evt_20261003_071244_A_0012",
  "ts": "2026-10-03T07:12:44+07:00",
  "lot_id": "A",
  "direction": "in",
  "vehicle_type": "motorbike",
  "card_hash": "e91d4c..."
}
```

### Bảo mật và độ tin cậy khi nhận

- **Xác thực:** mỗi nhà cung cấp một API key (header `X-Api-Key`); mở rộng: ký HMAC nội dung request.
- **Chống trùng:** theo `event_id` → mất mạng gửi lại không bị đếm hai lần.
- **Ẩn danh:** mã thẻ được băm ngay khi nhận; AI không bao giờ thấy mã thẻ.

### Vai trò của AI ánh xạ cột

Mỗi phần mềm giữ xe đặt tên trường khác nhau (`ts_in`, `time_in`, `ThoiGianVao`…). AI đề xuất ánh xạ payload/file của nhà cung cấp mới vào schema chuẩn kèm độ tin cậy; cột dưới 80% bắt buộc người dùng xác nhận. Tải CSV dùng để **nạp dữ liệu lịch sử** lúc đầu (vài tuần) cho mô hình dự báo.

## 4. Dữ liệu chuẩn xác và liên tục

### Kiểm tra từng sự kiện

| Lỗi | Xử lý tự động |
|---|---|
| Sự kiện trùng | Loại theo `event_id` |
| Có lượt vào, không có lượt ra | Phiên "treo" quá 24h tự đóng; số xe qua đêm đối chiếu giới hạn ≤ 5% |
| Số xe vô lý (âm, vượt tối đa) | Kẹp trong [0, tối đa], bật cờ `suspect` |
| Sai số cộng dồn | 00:00 hằng ngày: số xe đầu ngày = số phiên còn mở của bãi |

### Ngưỡng bật cờ chất lượng (mỗi ngày, từng bãi)

Một lượt quẹt đúp hay vài phiên tự đóng không được làm bãi "đáng ngờ" cả ngày (báo động giả). Hằng số nằm ở `QualityPolicy`.

| Tình huống | Cờ | Khi nào bãi thành `suspect` |
|---|---|---|
| Quẹt không khớp phiên (ra mà không có phiên mở; vào khi phiên đang mở) | `implausible_count` | Chỉ khi có **từ 5 lượt VÀ từ 1%** tổng lượt quẹt của bãi trong ngày |
| Phiên thiếu lượt ra bị tự đóng sau 24 giờ | `missing_out` (luôn hiện trong `GET /api/quality/flags`) | Chỉ khi số phiên tự đóng trong ngày **vượt 5% sức chứa thiết kế** |
| Số xe vượt sức chứa tối đa (số hiển thị bị kẹp) | `implausible_count` | Ngay |
| Số xe qua đêm vượt 5% sức chứa thiết kế | `implausible_count` | Ngay |
| 5 phút không có sự kiện trong 06:30–09:00 | `signal_lost` (trạng thái `estimated` nếu có dự báo nền) | Ngay |

Đo trên bộ mẫu HUCE (311.645 sự kiện): 0 lượt không khớp; phiên thiếu lượt ra mỗi ngày trung bình 44,8 (A), 37,4 (B), 8,4 (C1), 7,5 (C2), ngưỡng 5% là 72, 72, 15, 15. Vì vậy A, B, C2 không còn `suspect` chỉ vì phiên tự đóng; C1 chỉ `suspect` vào những ngày xấu nhất (lớn nhất 17).

### Phát hiện mất tín hiệu

- Trong 06:30–09:00, bãi nào **5 phút không có sự kiện** → cờ `signal_lost`, dashboard cảnh báo "Mất tín hiệu Bãi B từ 07:18".
- Trong lúc mất tín hiệu: số xe bãi đó lấy theo **dự báo**, gắn nhãn "ước tính".
- Có tín hiệu lại: nguồn gửi bù sự kiện (chống trùng sẵn), hệ thống tính lại, bỏ nhãn.

Dashboard luôn hiện **thời điểm cập nhật** và **cờ dữ liệu** của từng bãi.

## 5. Tầng 3 — Mở rộng (trình bày trên slide, không làm)

| Thiết bị | Đặt ở | Giá trị thêm so với quẹt thẻ ở bãi |
|---|---|---|
| Camera AI nhận diện biển số / đếm xe | Cổng trường | **Biết sớm hơn vài phút** (xe qua cổng trước khi tới bãi) → gợi ý kịp lúc; **đo tải từng cổng** thay vì suy ra từ cấu hình |
| Cảm biến hồng ngoại / vòng từ | Cổng trường | Như trên ở mức đếm lưu lượng, rẻ hơn, không thu dữ liệu cá nhân |
| Camera AI đếm chỗ trống | Trên cao trong bãi | Đối chiếu số xe thật: xe lấn lối đi, xe không quẹt thẻ, hiệu chỉnh sai lệch |
| Kết hợp cổng + bãi | — | Đo **tỷ lệ sinh viên làm theo gợi ý** → đánh giá hiệu quả, cải thiện dự báo |
| Camera AI ở cổng | — | Đếm xe **theo từng cổng** → thay ước lượng `gateShare` bằng số đo thực; gửi qua trường tùy chọn `gate` của sự kiện (`POST /api/events`) |

Tất cả gửi sự kiện vào cùng Ingest API → mở rộng không phải sửa lõi.

**Quyền riêng tư:** biển số và mã thẻ được băm tại khâu nhập; AI chỉ nhận số liệu tổng hợp. (Nhóm tự kiểm tra văn bản pháp luật hiện hành về bảo vệ dữ liệu cá nhân trước khi đưa tên văn bản lên slide.)
