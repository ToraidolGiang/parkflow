# Nguồn dữ liệu

- Repo: https://github.com/ToraidolGiang/parkflow
- Commit nguồn: `eb8b3b2118e373d21dba22f4cb61dfba1754f425`
- Thư mục nguồn: `simulator/data/`
- Ngày lấy: 03/10/2026.

Ban đầu đã sao chép nguyên trạng 8 tệp: `README.md`, `campus_config.json`,
`demo_log.csv`, `scenarios.json`, `schedule.csv`, `swipe_log.csv`,
`validation_report.json`, `vendor_sample.csv`. SHA-256 của từng tệp đã
được đối chiếu và khớp với bản tải từ repo nguồn.

Đây là dữ liệu mô phỏng. Chưa nạp vào cơ sở dữ liệu.

## Cập nhật cho ngày thi 03/10/2026

Theo yêu cầu người dùng, script mới `simulator/generate.mjs`
được viết trong dự án này để bổ sung lịch sử 23/09–02/10, tạo demo 03/10
và bổ sung lịch học. Không sao chép script nghiệp vụ từ repo nguồn.

`swipe_log.csv`, `demo_log.csv`, `schedule.csv`, `scenarios.json`,
`validation_report.json` và `README.md` đã được cập nhật, nên không còn
khớp hash bản nguồn. Các phiên lịch sử đến hết 22/09 được giữ nguyên.
`campus_config.json` vẫn giữ nguyên bản nguồn. `vendor_sample.csv` đã được
tạo lại từ đủ 1.990 phiên demo 03/10 đến 07:30, giữ tên cột nhà cung cấp
và chuyển loại xe sang XM/XD/OTO; không còn là 100 phiên cũ ngày 14/09.

Phương pháp lấy mẫu cùng thứ, seed, ngày nguồn từng ngày bổ sung và
kết quả kiểm tra được ghi trong `validation_report.json`.

Log demo được chốt tại 07:30:00 ngày 03/10/2026 (UTC+7) theo yêu cầu:
chỉ giữ lượt vào đến mốc này và không ghi lượt ra trong tương lai.
Lịch sử đến 02/10 và lịch học cả ngày giữ nguyên.
