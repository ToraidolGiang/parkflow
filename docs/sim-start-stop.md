# Giả lập: bắt đầu và dừng

Chuyển phần phát lại theo đồng hồ từ ParkFlow, tham khảo commit `489b588` (`feat: replay parking events with simulator controls and spikes`). Chỉ tích hợp bắt đầu, dừng và trạng thái; dùng `ParkFlowClock`, `ReplayLog`, `EventSender` đã có ở repo chung kết.

Đặt `SIM_CONTROL_KEY` trong `.env`, khởi động backend rồi gọi:

```http
POST /api/sim/start
Content-Type: application/json
X-Api-Key: <SIM_CONTROL_KEY>

{"date":"2026-09-22","speed":30}
```

`speed` mặc định 30, hợp lệ từ 1 đến 3600. Giả lập bắt đầu lúc 06:00 theo múi giờ Việt Nam, phát sự kiện qua `/api/events`. `GET /api/sim/state` trả giờ giả lập, trạng thái, số gửi/nhận/trùng/còn lại và lỗi.

Dừng bằng `POST /api/sim/stop` với cùng header `X-Api-Key`. Đồng hồ dùng chung trở về giờ thật. Một yêu cầu gửi sự kiện đang thực hiện có thể hoàn tất sau khi dừng. Không xóa dữ liệu đã nhận; phát lại cùng log được bộ nhập loại trùng theo mã sự kiện.

Khi cổng nhập cấu hình `EVENTS_API_KEY`, đặt `parkflow.sim.api-key` bằng một khóa được cổng nhập chấp nhận. Không có khóa điều khiển thì API bắt đầu/dừng trả `503 SIM_DISABLED`.

Kiểm thử: `SimulationServiceTest` và `ParkFlowClockTest`.
