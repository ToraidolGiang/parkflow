# Kế hoạch ngày thi: dựng lại ParkFlow và commit trong giờ thi

> Chung kết **03/10/2026**, thi tập trung. Quy định kỹ thuật của Ban Tổ chức (thể lệ mục IV.2): được chuẩn bị trước **khung code trắng** (project template, cài sẵn database và thư viện); **toàn bộ dòng code logic nghiệp vụ phải được viết và commit lên GitHub trong giờ thi**.
> Cửa sổ thi của nhóm: **7 giờ, từ 07:30 đến 14:30** (BTC cho phép dùng công cụ AI hỗ trợ lập trình). Mốc `T+giờ:phút` tính từ 07:30.

## 0. Nguyên tắc liêm chính (đọc trước)

1. **Repo này chỉ có khung trắng**: hạ tầng, thư viện, cấu hình, CI, tài liệu thiết kế. Không có dòng logic nghiệp vụ nào (không có dự báo, phân bổ, nhập sự kiện, gợi ý sinh viên, AI, giả lập).
2. **Mã nghiệp vụ được viết mới trong giờ thi** từ tài liệu thiết kế `docs/thiet-ke/` (bản phân tích và đặc tả, không phải mã). Không đưa vào repo mã đã viết trước giờ thi.
3. **Không sửa dấu thời gian, không viết lại lịch sử** (`--date`, `GIT_AUTHOR_DATE`, `rebase` đã đẩy, `push --force` lên `main`). Mọi commit giữ nguyên giờ thật. Script `scripts/kiem-tra-cua-so-thi.sh` kiểm chứng điều này và chạy ở cuối buổi.
4. Mỗi người commit bằng **tài khoản GitHub của chính mình** (`git config user.name/email` đúng, email đã xác minh trên GitHub) để lịch sử phản ánh đúng ai làm gì.
5. **Đã xác nhận với Ban Tổ chức:** cửa sổ 7 giờ; được dùng công cụ AI hỗ trợ lập trình. Nhóm ghi rõ việc dùng AI trong README. Mọi thành viên đều có mặt hôm nay.

## 1. Phạm vi sản phẩm cho giờ thi

Chọn theo `Kế hoạch triển khai` của nhóm (mức Bắt buộc, Nên có, Cắt được). Mục tiêu: **làm đủ**: Bắt buộc chạy trọn luồng demo, rồi Nên có, rồi triển khai và phần AI mở rộng, theo đúng thứ tự này.

| Mức | Tính năng | Chủ trì |
|---|---|---|
| Bắt buộc | Ingest `POST /api/events` (xác thực, chống trùng, cập nhật số xe từng bãi) | Giang |
| Bắt buộc | Trình giả lập hệ thống giữ xe: phát lại log, nút tăng đột biến +20% | Quyên |
| Bắt buộc | Dự báo: đường nền (trung vị các ngày cùng thứ), dự báo lại theo hệ số lệch k, đỉnh từng bãi | Hào |
| Bắt buộc | Phân bổ có ràng buộc (sức chứa, công suất cổng, hạn ngạch), chống nhảy kế hoạch, báo phần thiếu | Hào |
| Bắt buộc | Dashboard quản trị tự cập nhật 5 giây | Tuấn Anh |
| Bắt buộc | Màn sinh viên `/sv`: chọn tòa nhà, gợi ý bãi và cổng kèm lý do, chỉ đường | Tuấn Anh (giao diện), Quyên (gợi ý) |
| Bắt buộc | Trợ lý AI hỏi đáp tiếng Việt trên số liệu đã tính, có công tắc dữ liệu mẫu (Mock) | Giang (máy chủ), Tuấn Anh (giao diện) |
| Nên có | Vòng duyệt kế hoạch (bản nháp, chờ duyệt, đã duyệt) và phương án khi thiếu chỗ | Hào, Tuấn Anh |
| Nên có | Kiểm tra chất lượng dữ liệu (thiếu lượt ra, mất tín hiệu bãi) | Hào |
| Nên có | Nhập dữ liệu lịch sử CSV (máy chủ và giao diện) | Quyên (máy chủ), Tuấn Anh (giao diện) |
| Làm đủ | AI ánh xạ cột (kèm độ tin cậy) cho nhập CSV | Giang (máy chủ), Tuấn Anh (giao diện) |
| Làm đủ | Triển khai AWS: Docker Compose + Caddy (HTTPS), phát hành một lệnh qua SSH | Giang |
| Làm đủ | AI tạo quy tắc từ câu tiếng Việt, báo cáo AI tuần | Giang |

**Hợp đồng API đã chốt** (đọc trước khi viết): `docs/thiet-ke/04-api.md` và `docs/thiet-ke/openapi.yaml`. Thuật toán: `docs/thiet-ke/03-thuat-toan-du-bao-phan-luong.md`. Dữ liệu mẫu: `docs/thiet-ke/05-du-lieu-mau.md`. Giao diện làm **theo hợp đồng bằng dữ liệu giả trước** (mock-first) rồi nối API thật khi backend sẵn sàng, để không ai chờ ai.

## 2. Đội hình và sở hữu mã (để không giẫm chân)

| Thành viên | GitHub | Mảng | Thư mục sở hữu (chỉ người này sửa) |
|---|---|---|---|
| Trịnh Trường Giang | `ToraidolGiang` | Nền móng, ingest, AI, tích hợp, demo | `backend/.../{common,domain,repository,ingest,ai}`, `controller/{Event,Lots,Config,Ai}Controller`, `db/migration/V1*` |
| Bùi Viết Hào | `Haohao2309` | Dự báo, phân bổ, duyệt, chất lượng | `backend/.../{forecast,plan,approval,quality}`, `controller/{Forecast,Plan}Controller`, `db/migration/V2*` |
| Vũ Tuấn Anh | `AnhVuu215` | Toàn bộ giao diện | `frontend/src/**` |
| Đặng Thị Mỹ Quyên | `quyendang2505` | Dữ liệu, giả lập, gợi ý sinh viên, kiểm thử, tài liệu | `backend/.../{sim,importer,student}`, `simulator/**`, `frontend/e2e/**`, `README.md`, `docs/**` (trừ thiết kế) |

Quy tắc: sửa thư mục của người khác thì **nhờ họ làm** hoặc mở PR gắn tên họ. Migration đánh số theo người (Giang `V1`, Hào `V2`, Quyên `V3`) để không trùng số.

**Phương án khi một người vắng** (Quyên bận): việc của Quyên chuyển theo thứ tự: giả lập và nhập dữ liệu sang Giang (sau T+2:15), gợi ý sinh viên sang Hào (sau T+4:00), kiểm thử e2e và tài liệu sang Tuấn Anh (sau T+5:30). Mỗi dòng việc của Quyên ở mục 4 ghi sẵn người nhận thay.

## 3. Quy trình commit và nhánh

- **Nhịp:** mỗi commit là một phần việc trọn vẹn, không quá khoảng 30 đến 45 phút làm; **ít nhất 2 commit mỗi giờ mỗi người**; không giữ mã trên máy quá 45 phút. Mục tiêu khoảng **80 đến 100 commit** toàn đội, mỗi người từ 20% đến 30%.
- **Nhánh:** `feat/<tên>-<mô-tả>` rồi PR vào `main`. PR nhỏ, **CI xanh mới merge** (backend `./mvnw verify`, frontend `lint`, `test`, `build`). Merge bằng **"Rebase and merge" hoặc "Create a merge commit"**, không "Squash" (squash làm mất các commit nhỏ của từng người).
- **Thông điệp:** `loại(phạm-vi): mô tả ngắn bằng tiếng Việt`, loại gồm `feat`, `fix`, `test`, `docs`, `chore`, `refactor`, `style`. Chi tiết ở `docs/quy-uoc-commit.md`.
- **Không đẩy thẳng lên `main`.** Ngoại lệ duy nhất: commit khung ở T+0:00 của Giang.
- **Mốc tích hợp** (cả đội dừng 10 phút, merge, chạy thử luồng): **M1 T+1:30, M2 T+3:00, M3 T+4:30, M4 T+5:45**; **đóng băng T+6:30**, 30 phút cuối chỉ sửa lỗi và chuẩn bị demo.
- **Theo dõi:** `scripts/bao-cao-commit.sh "<giờ bắt đầu ISO>"` in số commit theo người và theo giờ; chạy mỗi mốc tích hợp.

## 4. Lịch commit chi tiết theo người

Cột **Xong lúc** là hạn tương đối `T+giờ:phút`. Cột **Phụ thuộc** ghi việc phải có trước (mã `G`, `H`, `T`, `Q` + số). Cột kiểm tra là cách tự chứng minh commit đúng trước khi push.

### 4.1 Giang (G): nền móng, ingest, AI, tích hợp

| # | Xong lúc | Commit (thông điệp gợi ý) | Nội dung | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| G1 | 0:10 | `chore: xác nhận khung chạy được trên máy cả đội` | Clone, `docker compose up -d db`, `./mvnw verify`, `npm ci && npm test`; ghi vào PR đầu tiên ai đã chạy xong | — | CI xanh |
| G2 | 0:35 | `feat(db): lược đồ bãi xe và sự kiện quẹt thẻ` | `V1__bai_xe_su_kien.sql`: `lots`, `events` (khóa `event_id`, chỉ mục `lot_id, ts`), `forecast_gates`; thực thể `Lot`, `Event`; `LotRepository` | — | backend khởi động, Flyway áp dụng |
| G3 | 0:50 | `feat(common): lỗi API thống nhất và đồng hồ hệ thống` | `ApiException`, `GlobalExceptionHandler` (`{error,message}`), `ParkFlowClock` (giả lập hoặc thật) | G2 | test đơn vị trình xử lý lỗi |
| G4 | 1:05 | `feat(ingest): kiểm tra hợp lệ sự kiện quẹt thẻ` | `EventValidator`: `event_id`, `ts` ISO-8601, `lot_id`, `direction` in/out, `vehicle_type`, `card_hash` | G3 | test các ca sai |
| G5 | 1:20 | `feat(ingest): ghi sự kiện, chống trùng và cập nhật số xe bãi` | `EventIngestService`: `INSERT ... ON CONFLICT DO NOTHING`, khóa hàng bãi, tăng giảm `current_count` | G4 | test trùng, test song song |
| G6 | 1:30 | `feat(controller): POST /api/events nhận một hoặc nhiều sự kiện` | Mảng tối đa 500 (413), khóa `X-Api-Key`, trả `{accepted,duplicate,rejected}` | G5 | `curl` gửi 1 và gửi lô |
| G7 | 1:45 | `test(ingest): kiểm thử đầu cuối ingest` | MockMvc: hợp lệ, trùng, sai bãi, quá lô, thiếu khóa | G6 | `./mvnw verify` |
| G8 | 2:05 | `feat(controller): trạng thái bãi và cấu hình trường` | `GET /api/lots/status`, `GET /api/config` (đọc từ bảng) | G2, Q7 | `curl` đúng hợp đồng |
| G9 | 2:20 | `chore: tích hợp M1 và sửa va chạm` | Merge `main`, chạy luồng giả lập, sửa lỗi hợp đồng | M1 | luồng giả lập đẩy số xe lên |
| G10 | 3:00 | `feat(ai): giao diện AiClient và trợ lý dữ liệu mẫu` | `AiClient`, `MockAi`, công tắc `AI_MOCK` | G3 | test Mock trả JSON đúng schema |
| G11 | 3:30 | `feat(ai): dựng số liệu cho trợ lý từ kết quả đã tính` | `AiFacts`: số xe, đỉnh dự báo, kế hoạch (AI chỉ diễn giải số này) | H4, H10 | test không có mã thẻ trong FACTS |
| G12 | 4:00 | `feat(ai): gọi Groq và Gemini có hạn chờ và dự phòng` | Hai client HTTP, hạn chờ 8 giây, đổi nhà cung cấp khi lỗi | G10 | test với client giả |
| G13 | 4:25 | `feat(controller): POST /api/ai/chat` | Kiểm tra câu hỏi, giới hạn tần suất, trả lời kèm `facts`, `suggestions`, `source` | G11, G12 | `curl` hỏi mẫu |
| G14 | 4:45 | `feat(ai): chặn câu trả lời nêu số không có trong số liệu` | So số phần trăm trong lời mô hình với FACTS, sai thì dùng câu dựng từ số liệu | G13 | test số sai bị thay |
| G19 | 5:00 | `feat(importer): AI ánh xạ cột CSV kèm độ tin cậy` | Gửi tiêu đề cột và vài dòng mẫu (che cột mã thẻ), nhận ánh xạ + độ tin cậy; dưới 0,8 thì hỏi lại người dùng | G12, Q6 | test với client giả |
| G20 | 5:20 | `chore(deploy): Dockerfile cho backend và giao diện` | Ảnh Java 17 chạy jar, ảnh Node dựng giao diện rồi phát tĩnh bằng Caddy | G17 | `docker build` hai ảnh |
| G21 | 5:40 | `chore(deploy): compose production và Caddyfile` | `docker-compose.prod.yml` (db, backend, web), Caddy tự cấp HTTPS và chuyển `/api/*` | G20 | `docker compose -f docker-compose.prod.yml up --build`, `/api/health` ok |
| G22 | 6:00 | `chore(deploy): script phát hành một lệnh qua SSH` | `release.sh`, `remote-release.sh`: sao lưu CSDL, dựng khi bản cũ còn chạy, thay bản, giữ bản cũ để quay lại; máy chủ và khóa là tham số | G21 | chạy thử khô (`PF_DRY_RUN=1`) |
| G23 | 6:20 | `feat(ai): tạo quy tắc từ câu tiếng Việt và báo cáo tuần` | `POST /api/rules/parse`, `POST /api/report/week` dựng từ số liệu đã tính | G13 | test chặn câu sai schema |
| G15 | 5:15 | `chore: tích hợp M3 và kiểm tra luồng đủ hai phía` | Quản trị duyệt, sinh viên thấy đổi; sửa lỗi hợp đồng | M3 | luồng demo chạy hết |
| G16 | 6:35 | `docs: kịch bản demo và cách chạy qua Localhost hoặc Ngrok` | Các bước demo, lệnh `ngrok http 5173` | M4 | người khác chạy theo được |
| G17 | 6:50 | `fix: sửa lỗi sau chạy thử toàn hệ thống` | Gom các lỗi còn lại | đóng băng | CI xanh |
| G18 | 6:55 | `chore: gắn thẻ phiên bản nộp bài` | `git tag v-chung-ket`, chạy `kiem-tra-cua-so-thi.sh` | — | không có cảnh báo |

### 4.2 Hào (H): dự báo, phân bổ, duyệt, chất lượng

| # | Xong lúc | Commit (thông điệp gợi ý) | Nội dung | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| H1 | 0:40 | `feat(forecast): toán dự báo khung 15 phút` | `ForecastMath`: 29 khung từ 06:00 đến 13:00, trung vị, nội suy, kẹp k (0,7 đến 1,5); **không cần CSDL** | — | test đơn vị thuần |
| H2 | 1:10 | `feat(forecast): đường nền từ lịch sử cùng thứ` | `ForecastRepository.historyDates/historyDay`, trung vị 4 ngày cùng thứ | G2 | test với dữ liệu mẫu |
| H3 | 1:40 | `feat(forecast): hệ số lệch k và đường dự báo đã chỉnh` | Neo vào số xe hiện tại, chỉ tính k khi đủ 30 xe | H1, H2 | test k |
| H4 | 2:00 | `feat(controller): GET /api/forecast` | `lotId`, `date`; lỗi `BAD_REQUEST`, `UNKNOWN_LOT`, `NO_HISTORY`; trả `points`, `peak`, `thresholds` | H3 | `curl` đúng hợp đồng |
| H5 | 2:15 | `test(forecast): kiểm thử dự báo` | Ca thiếu lịch sử, ngày tương lai, đỉnh | H4 | `verify` xanh |
| H6 | 2:50 | `feat(plan): mô hình đầu vào đầu ra của phân bổ` | Bản ghi `Move`, `GateAction`, `Plan`, ràng buộc sức chứa, công suất cổng, hạn ngạch | H3 | biên dịch, test mô hình |
| H7 | 3:30 | `feat(plan): thuật toán phân bổ giảm số xe điều hướng` | Chọn bãi nguồn quá tải, bãi đích dưới ngưỡng, ít xe điều hướng nhất, báo `shortfall` | H6 | test ca quá tải, ca thiếu chỗ |
| H8 | 3:50 | `feat(plan): chống nhảy kế hoạch` | Ngưỡng `HYST_ABS` 15 xe, `HYST_PCT` 3%, chỉ lập lại khi lệch đủ lớn | H7 | test không đổi khi lệch nhỏ |
| H9 | 4:15 | `feat(approval): lược đồ và dịch vụ phiên bản kế hoạch` | `V2__ke_hoach.sql`: `approval_plans`, `approval_state`; trạng thái `draft`, `pending_approval`, `approved`, `auto_applied` | H8 | test chuyển trạng thái |
| H10 | 4:35 | `feat(controller): kế hoạch hiện hành và duyệt` | `GET /api/plan/current`, `POST /api/plan/{v}/approve` kèm phương án khi thiếu chỗ | H9 | `curl` duyệt |
| H11 | 4:55 | `feat(plan): tự lập kế hoạch mỗi phút` | `@Scheduled` mỗi 60 giây: dự báo, phân bổ, gửi vào duyệt | H10 | chạy với giả lập, thấy bản mới |
| H12 | 5:20 | `feat(quality): cờ chất lượng dữ liệu` | Thiếu lượt ra, số vô lý, mất tín hiệu bãi; `GET /api/quality/flags` | G5 | test cờ |
| H13 | 5:45 | `test(plan): kịch bản đột biến trên giả lập` | Đột biến +20% làm bãi quá tải thì có điều hướng, không nhảy liên tục | H11, Q5 | log kịch bản |
| H14 | 6:15 | `fix(plan): chỉnh ngưỡng theo kết quả chạy thử` | Điều chỉnh tham số, cập nhật docs thuật toán | đóng băng | `verify` xanh |

### 4.3 Tuấn Anh (T): giao diện

| # | Xong lúc | Commit (thông điệp gợi ý) | Nội dung | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| T1 | 0:40 | `feat(ui): khung giao diện, định tuyến và máy khách HTTP` | `AppShell` (thanh bên, vùng nội dung), tuyến `/`, `/forecast`, `/sv`, `/assistant`; `request`, `ApiError`, kiểu dữ liệu theo hợp đồng | — | `npm test`, `npm run build` |
| T2 | 1:10 | `feat(ui): hook thăm dò 5 giây và trạng thái tải, lỗi` | `usePolling`, thành phần Loading, Error, Empty | T1 | test hook |
| T3 | 1:50 | `feat(dashboard): thẻ số liệu và danh sách bãi (dữ liệu giả)` | KPI, mức lấp đầy, màu theo ngưỡng 80/100/120% | T2 | xem trên trình duyệt |
| T4 | 2:20 | `feat(dashboard): biểu đồ mức lấp đầy và danh sách cảnh báo` | Biểu đồ SVG, huy hiệu cảnh báo | T3 | ảnh chụp |
| T5 | 2:50 | `feat(dashboard): nối API trạng thái bãi thật` | Thay dữ liệu giả bằng `GET /api/lots/status` | G8 | số nhảy theo giả lập |
| T6 | 3:20 | `feat(forecast): trang Dự báo với đường dự báo (dữ liệu giả)` | Đường dự báo so với sau điều hướng, chọn bãi | T2 | ảnh chụp |
| T7 | 3:50 | `feat(forecast): nối API dự báo thật` | `GET /api/forecast`, trạng thái tải, lỗi, thiếu lịch sử | H4 | đúng số của API |
| T8 | 4:20 | `feat(approval): thẻ kế hoạch và nút duyệt` | Hiển thị di chuyển, mở cổng sớm, phần thiếu; nút duyệt kèm phương án | H10 | duyệt thành công |
| T9 | 4:50 | `feat(student): màn chọn tòa nhà và giờ đến` | `/sv`, nhớ lựa chọn bằng `localStorage`, thao tác dưới 3 chạm | T1 | test |
| T10 | 5:20 | `feat(student): màn gợi ý bãi và cổng kèm lý do` | Thẻ lớn, mức lấp đầy dự kiến, banner khi kế hoạch đổi | Q8 | test |
| T11 | 5:45 | `feat(student): màn chỉ đường hai chặng` | Nút Google Maps (`google.com/maps/dir/?api=1&destination=...`), sơ đồ trường tô đường đi | Q9 | ảnh chụp trên điện thoại |
| T12 | 6:10 | `feat(assistant): trang hỏi đáp có biểu đồ khớp số` | Khung chat, gợi ý câu hỏi, công tắc Mock | G13 | hỏi thử |
| T14 | 6:20 | `feat(import): màn nhập CSV và ánh xạ cột có độ tin cậy` | Tải tệp, xem trước, sửa ánh xạ cột AI đề xuất, xác nhận nhập | G19, Q13 | nhập thử tệp mẫu |
| T13 | 6:30 | `style: hoàn thiện giao diện trên điện thoại và máy tính` | Lề, tràn ngang, trạng thái rỗng, hỗ trợ bàn phím | đóng băng | không tràn ngang ở 390 px |

### 4.4 Quyên (Q): dữ liệu, giả lập, gợi ý sinh viên, kiểm thử, tài liệu (người nhận thay khi vắng)

| # | Xong lúc | Commit (thông điệp gợi ý) | Nội dung | Phụ thuộc | Nhận thay |
|---|---|---|---|---|---|
| Q1 | 0:30 | `docs: kế hoạch kiểm thử và danh sách kiểm tra demo` | Kịch bản test, tiêu chí xong từng mảng | — | Tuấn Anh |
| Q2 | 1:15 | `feat(data): bộ sinh dữ liệu lịch sử và ngày demo` | `simulator/generate.mjs` sinh CSV nhiều tuần, lưu lượng mượt, ngày demo | — | Giang |
| Q3 | 1:45 | `feat(sim): đọc log và gửi sự kiện tới ingest` | `ReplayLog`, `EventSender` (HTTP, thử lại) | G6 | Giang |
| Q4 | 2:15 | `feat(sim): chạy theo đồng hồ giả lập, bắt đầu và dừng` | `SimulationService`, `POST /api/sim/start|stop`, `GET /api/sim/state` | Q3 | Giang |
| Q5 | 2:40 | `feat(sim): nút tăng đột biến +20%` | `POST /api/sim/spike` thêm xe mới có cặp vào/ra | Q4 | Giang |
| Q6 | 3:10 | `feat(importer): nạp lịch sử vào CSDL` | `HistoryImporter` theo lô, chạy lại không trùng | G2, Q2 | Giang |
| Q7 | 3:30 | `feat(campus): cấu hình trường vào CSDL` | Nạp bãi, cổng, tòa nhà, tuyến đường từ JSON seed | G2 | Giang |
| Q13 | 4:00 | `feat(importer): API nhập CSV lịch sử có kiểm tra cột` | `POST /api/import/...`: giới hạn kích thước, kiểm tra cột và giá trị, nạp theo lô, báo dòng lỗi | Q6 | nhập tệp mẫu, dòng sai bị báo | Giang |
| Q8 | 4:20 | `feat(student): thuật toán gợi ý bãi và cổng` | `StudentService`: bãi gần nơi học, tránh bãi sắp đầy theo kế hoạch, lý do bằng chữ | H10 | Hào |
| Q9 | 4:45 | `feat(student): dữ liệu chỉ đường và API sinh viên` | `GET /api/student/recommendation`, tuyến, bước, phút đi bộ | Q8 | Hào |
| Q10 | 5:20 | `test(e2e): kịch bản Playwright cho ba luồng chính` | Quản trị xem dashboard, đột biến thấy điều hướng, sinh viên nhận gợi ý | M3 | Tuấn Anh |
| Q11 | 5:50 | `docs: README, hợp đồng API cuối và ảnh chụp` | Cách chạy, ảnh màn hình, bảng API đã làm | M4 | Tuấn Anh |
| Q12 | 6:20 | `chore: script đặt lại dữ liệu demo` | Xóa dữ liệu ngày demo, nạp lại để demo lặp lại được | đóng băng | Giang |

## 5. Đường găng và rủi ro

**Đường găng:** G2 (lược đồ) mở khóa G5, H2, Q6, Q7; G6 (ingest) mở khóa Q3, Q4 (giả lập); H4 (API dự báo) mở khóa T7, G11; H10 (API kế hoạch) mở khóa T8, Q8. Vì vậy G2 phải xong trong 35 phút đầu, và mọi người làm phần **không phụ thuộc** trước (H1 toán thuần, T1 đến T4 dữ liệu giả, Q1 đến Q2 tài liệu và dữ liệu).

| Rủi ro | Dấu hiệu | Cách xử lý |
|---|---|---|
| Lược đồ G2 trễ | Quá T+0:45 chưa merge | Giang chia nhỏ: chỉ `lots`, `events` trước, bảng khác sau; Hào và Quyên tạm dùng dữ liệu trong bộ nhớ |
| Hợp đồng API lệch | Giao diện nối API thật bị lỗi trường | Sửa theo `docs/thiet-ke/04-api.md`; nếu đổi hợp đồng thì sửa tài liệu trong cùng PR |
| Dữ liệu mẫu chưa xong | Hào không có lịch sử để thử | Q2 đẩy bản nhỏ (7 ngày) trước T+1:00, bản đầy đủ sau |
| AI không dùng được (hạn mức, mạng) | Gọi Groq/Gemini lỗi | Công tắc Mock (G10) bật mặc định; demo vẫn chạy |
| Trễ tiến độ | Quá mốc 30 phút | Cắt theo mức: bỏ Nên có trước (chất lượng dữ liệu, nhập CSV), giữ Bắt buộc |
| Xung đột merge | PR bị xung đột | Mỗi người sở hữu thư mục riêng (mục 2); `git pull --rebase origin main` trước khi push nhánh |
| Quên liêm chính | Commit dồn cuối giờ, giờ lạ | Chạy `bao-cao-commit.sh` mỗi mốc; commit nhỏ đều đặn |

## 6. Nếu Ban Tổ chức giữ đủ 12 giờ (T+7:00 đến T+12:00)

Làm tiếp theo thứ tự: (1) chặn câu trả lời AI nêu số sai và biểu đồ khớp số (nếu chưa); (2) màn bảo vệ: nhiệm vụ tại cổng sinh từ kế hoạch, nhận việc và báo xong; (3) thông báo trong ứng dụng; (4) nhập dữ liệu CSV từ giao diện kèm ánh xạ cột; (5) tối ưu hiệu năng (một truy vấn cho số xe thật cả ngày); (6) kiểm thử e2e đầy đủ và triển khai lên máy chủ. Mỗi mục là một nhóm commit riêng, chia theo sở hữu ở mục 2.

## 7. Kiểm tra cuối buổi

```bash
scripts/bao-cao-commit.sh "2026-10-03T07:30:00+07:00"      # số commit theo người và theo giờ
scripts/kiem-tra-cua-so-thi.sh "2026-10-03T07:30:00+07:00" "2026-10-03T14:30:00+07:00"   # mọi commit logic nằm trong cửa sổ, giờ tác giả và giờ commit khớp nhau
```

Danh sách nộp: link repo, tag `v-chung-ket`, README (cách chạy, ảnh chụp, bảng API đã làm), kịch bản demo.
