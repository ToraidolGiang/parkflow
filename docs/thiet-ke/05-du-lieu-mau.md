# Dữ liệu mẫu Đại học Xây dựng Hà Nội

## Cấu hình đang dùng

Nguồn duy nhất: `simulator/data/campus_config.json`. Tên trường và vị trí tương đối lấy từ sơ đồ người dùng cung cấp; người dùng xác nhận hai hầm đều ở phía sau G3.

| Mã | Nhà xe | Vị trí | Sức chứa mô phỏng | Giới hạn mô phỏng |
|---|---|---|---:|---:|
| A | Nhà xe hầm 1 sau G3 | Phía sau G3 | 1.450 | 1.740 |
| B | Nhà xe hầm 2 sau G3 | Phía sau G3 | 1.450 | 1.740 |
| C1 | Nhà xe C1 gần H3 | Gần H3 | 300 | 360 |
| C2 | Nhà xe C2 gần H3 | Gần H3 | 300 | 360 |

Hai cổng: `MAIN` / `G1` = Cổng Giải Phóng; `EAST` / `G2` = Cổng Trần Đại Nghĩa. Giữ mã kỹ thuật để thuận tiện tích hợp, tên cổng lấy từ cấu hình.
Có **8 tuyến** (2 cổng × 4 nhà xe A/B/C1/C2). C1/C2 thay mã C gộp; không còn mã C hoặc D trong dữ liệu mẫu.
Tòa nhà theo ảnh: H1, H2, H3, G3, A1, A2, Thư viện (`TV`), Thí nghiệm (`TN`).

Người dùng xác nhận **mỗi hầm A/B dưới 1.500 xe**, tổng hai hầm gần 3.000; **C gồm 2 nhà xe gần H3, tổng khoảng 600 xe**. Bộ mẫu chọn A/B 1.450 xe mỗi hầm (tổng 2.900), C1/C2 tạm chia đều 300 xe mỗi nhà theo xác nhận người dùng, tổng 600 xe. Vị trí riêng của C1/C2 trên sơ đồ là minh họa, cần xác minh thực tế. Cột giới hạn là ngưỡng cảnh báo mô phỏng 120%, không phải sức chứa thực tế.

**Chưa được trường xác nhận:** sức chứa, giới hạn, số sinh viên, giờ mở cổng, tỷ lệ phương tiện, phân bố cổng, bãi gần nhất và phút đi bộ. Các giá trị này phục vụ demo.
Hai điểm xuống hầm chỉ đặt tương đối phía sau G3; chưa có vị trí chính xác hoặc đường xe được phép đi. GPS để `null`; SVG dùng khung **720 × 960** theo ảnh khuôn viên người dùng cung cấp, không phải bản đồ dẫn đường thực tế.

Hình học bản đồ nằm trong `map` (khung nhìn, đường nội bộ, sân trường), `buildings[].footprint` (kích thước tòa nhà) và các tọa độ `svg`. Dashboard và màn sinh viên cùng đọc cấu hình này. Bố cục: H3 trên trái; H1 dọc bên trái; Thí nghiệm trên giữa; G3/H2 bên phải; Thư viện dưới H2; A1/A2 phía dưới. A/B ở dải sau G3; C1 ở phía trên H3, C2 ở dải bên trái theo cách diễn giải nét khoanh trong ảnh. Vị trí này cần xác nhận khi khảo sát thực địa. Tám tuyến đã cập nhật theo đường nội bộ mới.

Với DB đã lưu cấu hình, cập nhật phần hình học qua PUT `/api/config`; build JAR mới không tự ghi đè cấu hình đã lưu. Bản chạy local trong phiên làm việc đã được cập nhật riêng phần hình học, giữ nguyên sức chứa và dữ liệu xe.

Quy mô giả lập: `totalStudents = 12000`. Lưu lượng sáng thứ 2–4 lấy các tỷ lệ A/B/C1/C2 = 13%/9%/2%/1,8% quy mô trường (1.560/1.080/240/216 xe); thứ 5–7 lấy 7%/6,5%/1,5%/1,4%. Bốn ngày kịch bản giữ số liệu riêng. Đây là giả định demo, không phải khảo sát thực tế. Tổng mới 156.999 phiên thay cho giới hạn khoảng 24.000 để giữ cao điểm lặp lại suốt bốn tuần.

## Bộ dữ liệu

- `swipe_log.csv`: **252.914 phiên**, 37 ngày liên tục từ 17/08 đến 22/09/2026 (31 ngày có xe: bỏ chủ nhật và ngày lễ 02/09), khoảng 30 MB. Lưu lượng nền khoảng 1 xe mỗi phút nên giữa các đợt vào lớp số xe vẫn nhúc nhích (ngày demo 22/09: khoảng cách lớn nhất giữa hai sự kiện liên tiếp ≈ 5 phút); các đợt vào lớp trải ±45 phút và rời bãi trải ±40 phút quanh giờ tan. Ngày đặc biệt kèm `scenarios.json`: lễ Quốc khánh 02/09, tuần thi 07 đến 12/09, sự kiện 10/09, lịch học dày 18/09, mưa 20/08 và 15/09. `node simulator/generate.mjs` sinh lại toàn bộ; tăng `numDays` để thêm ngày (thời gian nạp lịch sử và kiểm thử tăng theo tỷ lệ).
- Cột: `ts_in,ts_out,gate_id,card_id,lot_id,vehicle_type`. Thời gian local Asia/Ho_Chi_Minh, thẻ giả băm SHA-256. Loại xe: Xe máy, Xe đạp, Ô tô.
- `scenarios.json`: chứa thông tin mô tả nhãn và loại từng ngày (normal, exam, rain, event, holiday).
- Đỉnh trung vị ngày thường A/B/C1/C2: dao động tự nhiên ±8 đến 12%. Bốn ngày cao điểm trong bảng dưới dùng để kiểm thử đầy và quá tải; không phải thống kê thực tế của trường.
- `demo_log.csv`: riêng ngày 22/09, **7.743 phiên**, phát lại kịch bản mượt.
- `schedule.csv`: **2.240 dòng lịch học mô phỏng**, 7 tòa có lớp mẫu × 5 ca, biến động theo hệ số ngày đặc biệt. Không xếp lớp mẫu trong thư viện. Đây không phải thời khóa biểu chính thức; không dùng lịch này để suy ra số xe.
- `vendor_sample.csv`: 100 phiên với tên cột khác, mã phương tiện XM/XD/OTO để demo AI ánh xạ. Không bảo đảm mức tin cậy AI.
- `validation_report.json`: số liệu kiểm tra thực tế, gồm lưu lượng từng ngày. Các số cũ 340 xe/15 phút và tải cổng chính 190/200 không còn là ràng buộc của cấu hình HUCE.

## Ngày đầy và quá tải mô phỏng

| Ngày | A / 1.450 | B / 1.450 | C1 / 300 | C2 / 300 | Tình huống |
|---|---:|---:|---:|---:|---|
| 01/09 | 1.450 | 1.000 | 240 | 220 | A đầy |
| 08/09 | 1.250 | 1.450 | 300 | 240 | B và C1 đầy |
| 15/09 | 1.500 | 1.300 | 320 | 280 | A và C1 quá tải |
| 16/09 | 1.520 | 1.470 | 320 | 320 | Cả bốn quá tải |

Đỉnh tính từ sự kiện vào/ra của từng ngày, đầu ngày bằng 0; phiên thiếu lượt ra cần backend xử lý theo quy tắc chất lượng. “Đầy” là đạt 100%, “quá tải” là vượt 100%; số xe vẫn dưới ngưỡng mô phỏng 120%. Danh sách kịch bản nằm ở `sampleScenarios` trong cấu hình để có thể sinh lại. Lịch học 5 ca được giữ nguyên.

## Sinh dữ liệu và phát lại

### Lịch học đã chốt với người dùng

Mỗi tiết 50 phút, nghỉ giữa tiết 5 phút; mỗi ca 3 tiết. `campus_config.json` lưu chi tiết tại `sessions.shifts`, gồm giờ bắt đầu/kết thúc từng ca và 15 tiết. Bộ sinh đọc trực tiếp cấu hình này để tạo `schedule.csv`.

| Ca | Tiết | Bắt đầu | Kết thúc | Nghỉ đến ca tiếp |
|---|---|---|---|---|
| 1 | 1–3 | 06:30 | 09:10 | 15 phút |
| 2 | 4–6 | 09:25 | 12:05 | 25 phút |
| 3 | 7–9 | 12:30 | 15:10 | 15 phút |
| 4 | 10–12 | 15:25 | 18:05 | 40 phút |
| 5 | 13–15 | 18:45 | 21:25 | — |

Đây là lịch người dùng xác nhận, chưa xác minh với công bố chính thức của trường. Giờ mở cổng và log xe vẫn là kịch bản mô phỏng riêng: log hiện bắt đầu 06:45, không dùng để mô phỏng việc đến trước ca 1 lúc 06:30. Cần điều chỉnh kịch bản lưu lượng nếu demo việc đến học đúng giờ.

Xem [hướng dẫn](../simulator/data/README.md). Bộ sinh đọc trực tiếp cấu hình JSON, không lấy ví dụ từ tài liệu và không ghi đè cấu hình. Seed cố định để kết quả có thể lặp lại.
CLI tách phiên thành sự kiện in/out, sắp theo giờ rồi gửi `POST /api/events`, mặc định ×30, kèm X-Api-Key. Không tự sinh lượt ra bị thiếu. Module sim đã dùng đồng hồ Spring Boot khi chạy; hướng dẫn ở `docs/11-quyen-30-09.md`. Chưa thử cloud.

## Ảnh hưởng tích hợp

API student, sim và dashboard đã nối cấu hình HUCE; các mock admin khác vẫn minh họa cấu hình cũ. Xem `docs/11-quyen-30-09.md` về giới hạn tích hợp. Bộ dữ liệu và CLI hiện chỉ chấp nhận A/B/C1/C2; màn chọn tòa cần đọc danh sách trong cấu hình. Kịch bản loại bãi D ở `docs/06` cần viết lại cho A/B/C1/C2. Backend profile huce và OpenAPI đã hỗ trợ C1/C2.
Khi đổi từ bộ dữ liệu cũ sang HUCE, dùng cơ sở dữ liệu demo trống hoặc quy trình reset của nhóm; không trộn hai bộ dữ liệu vào cùng số đếm. Không tự reset trong CLI.
