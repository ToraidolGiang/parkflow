# frontend — React + TS + Vite (Tuấn Anh, Quyên, Giang)

Tuấn Anh khởi tạo: `npm create vite@latest . -- --template react-ts`, thêm Recharts + react-router, PR `feat/tuananh-scaffold` (làm đầu tiên, ~1 giờ: router, layout, API client, JSON giả, component sơ đồ trường).
Dev proxy `/api` mặc định → `http://localhost:8080`. Nếu cổng 8080 đang được ứng dụng khác sử dụng, đặt `VITE_API_PROXY_TARGET` (ví dụ `http://localhost:8081`) trước khi chạy Vite. Dùng JSON giả trong `src/shared/mock/` (đúng shape `docs/openapi.yaml`) khi backend chưa xong; hoặc chạy `npx @stoplight/prism-cli mock ../docs/openapi.yaml`.

## Thư mục (mỗi người một thư mục)
| Đường dẫn | Nội dung | Người làm |
|---|---|---|
| `src/shared/` | layout, API client, component sơ đồ trường, JSON giả | Tuấn Anh |
| `src/pages/dashboard/` | Dashboard quản trị | Tuấn Anh |
| `src/pages/forecast/` | Dự báo & Đề xuất, nút duyệt, cờ cảnh báo | Tuấn Anh |
| `src/pages/import/` | Nhập dữ liệu | Tuấn Anh |
| `src/pages/sv/` | luồng mobile Chọn nơi học, Gợi ý và trạng thái kế hoạch | Tuấn Anh |
| `src/pages/sv/route/` | màn Chỉ đường, kiểm tra link Maps an toàn | Tuấn Anh |
| `src/features/ai/` | khung chat trợ lý AI + công tắc Mock | Giang |

## Luồng sinh viên `/sv`

Ba route mobile hoạt động độc lập với sidebar quản trị:

- `/sv`: chọn tòa nhà và giờ đến.
- `/sv/recommendation`: xem bãi, cổng và lý do được gợi ý.
- `/sv/route`: mở Google Maps tới cổng và xem tuyến nội khu.

Luồng hiện dùng JSON giả trong `src/pages/sv/mock/`, chưa gọi backend thật. Lựa chọn được lưu ở `localStorage`; mã phiên chỉ nằm trong `sessionStorage`.

## Chạy và kiểm tra

```powershell
npm install
npm run dev
```

Mở `http://localhost:5173/sv`. Chạy toàn bộ kiểm tra bằng `npm test`, `npm run lint` và `npm run build`.
