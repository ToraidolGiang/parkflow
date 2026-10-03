# Dữ liệu mô phỏng cho ngày thi 03/10/2026

Toàn bộ dữ liệu là giả lập; mã thẻ SHA-256 không chứa thông tin sinh viên thật.
Timestamp dùng giờ địa phương Asia/Ho_Chi_Minh (UTC+7).

| Tệp | Nội dung hiện tại |
|---|---|
| `swipe_log.csv` | 319.171 phiên lịch sử từ 17/08 đến 02/10/2026; không chứa ngày demo 03/10 |
| `demo_log.csv` | 1.990 phiên ngày thứ Bảy 03/10/2026, chỉ ghi nhận đến 07:30:00 |
| `schedule.csv` | 1.435 dòng lịch học mô phỏng, gồm cả ngày 03/10 |
| `campus_config.json` | Cấu hình trường, bốn bãi A/B/C1/C2, hai cổng G1/G2 và tuyến đường |
| `scenarios.json` | Nhãn ngày lịch sử và ngày demo; chủ nhật bổ sung được đánh dấu nghỉ |
| `vendor_sample.csv` | Đủ 1.990 phiên của demo 03/10 đến 07:30, dùng tên cột nhà cung cấp và mã xe XM/XD/OTO |
| `validation_report.json` | Số lượng, đỉnh từng ngày, lượt ra bị thiếu và kết quả kiểm tra tính từ CSV hiện tại |

## Cách tạo lại

Từ thư mục gốc dự án, chạy Node.js:

```powershell
node simulator/generate.mjs
```

Script giữ các phiên lịch sử đến hết 22/09, tạo lại phần bổ sung 23/09–02/10
và thay log demo bằng ngày 03/10 đến 07:30:00. Không sinh phiên hoặc lịch học vào chủ nhật.
Chạy lại cho cùng kết quả, không cộng dồn bản ghi. Script cần các CSV nguồn
đang có trong thư mục này; không cần tải repo cũ hoặc cài thư viện.

## Phương pháp và dự báo

- Giữ nguyên 252.914 phiên nguồn đến 22/09, bổ sung 66.257 phiên lịch sử.
- Mỗi ngày mới lấy mẫu từ ngày có nhãn normal cùng thứ trong dữ liệu nguồn.
  Lưu lượng biến thiên 96–104%; demo dùng mức 104% của mẫu thứ Bảy.
- Giờ vào/ra dịch cùng nhau tối đa 3 phút, giữ thời lượng và giới hạn
  06:00–22:00. Mã thẻ mới được sinh từ seed cố định. Giữ các phiên thiếu
  lượt ra để thử xử lý chất lượng dữ liệu, không tự bù lượt ra.
- Bốn thứ Bảy gần nhất trước demo là **05/09, 12/09, 19/09, 26/09**;
  mỗi ngày đều có đủ bốn bãi để tính đường nền cùng thứ.
- Lịch học bổ sung lấy từ cùng ngày nguồn với log xe, giữ ca học và tòa nhà.
- Đây là dữ liệu mô phỏng dựa trên mẫu cũ, không phải quan sát thực tế độc lập.

Nạp `swipe_log.csv` làm lịch sử; phát `demo_log.csv` theo đồng hồ giả lập
**03/10/2026**, bắt đầu từ 06:00 để gửi đủ các lượt vào đầu ngày.
Mốc chốt cố định là **07:30:00 (UTC+7)**: loại các phiên vào sau mốc này,
để trống `ts_out` nếu lượt ra chưa xảy ra. Có 6 phiên đã ra và 1.984 phiên
chưa ghi nhận lượt ra; ô trống lúc này không mặc nhiên là lỗi thiếu dữ liệu.
Lịch học cả ngày được giữ để phục vụ dự báo; đó là kế hoạch, không phải log sự kiện.
`vendor_sample.csv` là cùng bộ phiên demo dưới định dạng nhà cung cấp để thử
ánh xạ cột; không phải bộ phiên mới để cộng thêm vào log demo.
Không nạp trước toàn bộ log demo vào lịch sử dự báo. Dữ liệu chưa được nạp
vào database; script này chỉ chuẩn bị tệp, không gửi sự kiện đến API.

Đỉnh quan sát đến 07:30 của A/B/C1/C2 lần lượt **831/778/198/178 xe**. Đây là ngày thứ Bảy
bình thường, không bảo đảm kích hoạt phân luồng quá tải khi tăng thêm 20%.

Script kiểm tra cột CSV, mã bãi/cổng/xe, thứ tự vào-ra, phiên trùng, số xe
không âm trong ngày, lịch học và đủ bốn ngày cùng thứ trước khi ghi tệp.
Báo cáo đỉnh tính từ số xe đầu ngày bằng 0, giữ các phiên chưa có lượt ra
đến hết khoảng quan sát (07:30 đối với demo, cuối ngày đối với lịch sử);
chưa mô phỏng quy tắc xử lý qua đêm của backend.

Xem `NGUON-DU-LIEU.md` để biết commit nguồn và những tệp đã được cập nhật.
