# Hồ sơ hoàn thiện Q7–Q12

Các phần đã chuyển từ ParkFlow cũ vào repo chung kết:

- Q7: cấu hình trường trong CSDL và `GET /api/config`.
- Q8: `GET /api/student/recommendation` chọn bãi gần tòa và cổng theo seed campus.
- Q9: dữ liệu tuyến, chỉ đường nội khu và liên kết Google Maps khi có tọa độ.
- Q10: fixture Playwright và các kịch bản layout, thông báo, tự động hóa, ca trực và trợ lý.
- Q11: tài liệu API nhập CSV, cấu hình campus, importer và hướng dẫn luồng sinh viên.
- Q12: `deploy/reset-demo.sh` dọn dữ liệu demo và đặt lại số xe.

Frontend chạy bằng `npm run dev`; backend dùng `backend/mvnw spring-boot:run` sau khi cấu hình PostgreSQL. Các endpoint sinh viên dùng JSON cấu hình đã seed vào CSDL, không đọc file riêng ở runtime.
