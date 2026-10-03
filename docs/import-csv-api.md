# Q13: API nhập CSV lịch sử

Chuyển và điều chỉnh `ImportController`, `ImportService`, `CsvReader` từ ParkFlow; phạm vi chỉ log quẹt thẻ, không nhập lịch học.

1. `POST /api/import/upload`: multipart với `file` và `kind=swipe_log` (mặc định). Trả `importId`, `headers`, tối đa 5 `sampleRows`, `rowCount`, lỗi số cột theo dòng. Chưa ghi CSDL.
2. `POST /api/import/{importId}/confirm`: body JSON chứa `mappings`, mỗi phần tử có `column` (tên cột CSV) và `field` (trường chuẩn). Hoặc gọi `POST /api/import/confirm` với thêm `importId` trong body.

Ví dụ ánh xạ file đóng gói:

```json
{
  "importId": "<ID từ upload>",
  "mappings": [
    {"column":"ts_in","field":"time_in"},
    {"column":"ts_out","field":"time_out"},
    {"column":"gate_id","field":"gate_id"},
    {"column":"card_id","field":"card_id"},
    {"column":"lot_id","field":"lot_id"},
    {"column":"vehicle_type","field":"vehicle_type"}
  ]
}
```

Bắt buộc ánh xạ `time_in`, `card_id`, `lot_id`, `vehicle_type`; `time_out` và `gate_id` tùy chọn. `ignore` bỏ qua cột. Kiểm tra cột trống/trùng, số cột từng dòng, ngày giờ hợp lệ, giờ ra không trước giờ vào, bãi/cổng tồn tại và loại xe hỗ trợ. Mã thẻ chưa băm được SHA-256 trước khi lưu.

File UTF-8, hỗ trợ BOM, dấu phẩy/chấm phẩy/tab, trường ngoặc kép và xuống dòng. Giới hạn 20 MB, 500.000 dòng, 10 phiên chờ; phiên hết hạn sau 30 phút. Tối đa 50 lỗi giá trị được trả trong `errors` với số dòng. Dòng hợp lệ được nhập, dòng sai bị từ chối; kết quả có `imported`, `skipped` (sự kiện trùng), `rejected` (dòng sai). Một dòng có cả giờ vào/ra tạo hai sự kiện.

Cùng file chạy lại không trùng nhờ `ON CONFLICT(event_id) DO NOTHING`. Nạp lịch sử không cập nhật số xe hiện tại. Cả lần confirm chạy trong một giao dịch; lỗi CSDL quay lui. Phiên đã confirm xong không dùng lại, muốn nhập lại thì upload lại file.

Hai endpoint kiểm tra `X-Api-Key` bằng `ApiKeyGuard`: khi đặt `EVENTS_API_KEY`, phải gửi khóa hợp lệ. Giữ hành vi dev hiện có: để trống cấu hình thì không chặn. Dữ liệu cổng/bãi dùng cấu hình CSDL từ phần campus.

Kiểm thử: `ImportServiceTest`, `CsvReaderTest`, `HistoryImporterTest`, `GlobalExceptionHandlerTest`.
