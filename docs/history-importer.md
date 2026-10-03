# Nạp lịch sử vào CSDL

`com.parkflow.backend.ingest.HistoryImporter` được chuyển từ commit `5673bfa` của ParkFlow.

Gọi `historyImporter.importPackaged()` trên bean Spring để nạp `simulator/swipe_log.csv` đã đóng gói. Hàm trả số sự kiện mới thêm; phát sự kiện ứng dụng `HistoryImporter.Imported(count)` sau khi ghi. Không tự nạp khi khởi động và chưa thêm API hay giao diện nhập.

- Một giao dịch cho cả lần nạp, gửi tối đa 500 sự kiện mỗi lô.
- Khóa giao dịch PostgreSQL để các lần nạp không chạy chồng nhau.
- `ON CONFLICT(event_id) DO NOTHING`: cùng mã sự kiện chạy lại không trùng; không sửa sự kiện đã có.
- Đánh dấu `historical = true`; không cập nhật số xe, thời gian sự kiện mới nhất hoặc trạng thái bãi.
- Kiểm tra mọi bãi trong log đã tồn tại trước khi ghi. Lỗi bất kỳ làm giao dịch quay lui.

Cùng bộ CSV chạy lại không trùng. Bộ dữ liệu mới có mã sự kiện khác sẽ được nạp bổ sung; chức năng thay thế lịch sử cũ nằm ngoài thay đổi này.

Kiểm thử đơn vị: `HistoryImporterTest`. Kiểm thử PostgreSQL: `HistoryImporterDatabaseTest`.
