# Hợp đồng API

> Cập nhật 30/09: profile `huce` dùng các bãi `A`, `B`, `C1`, `C2`. `/api/config` trả cấu hình HUCE; student và simulator đã có API thật. Xem `docs/11-quyen-30-09.md` để chạy và `docs/openapi.yaml` để lấy kiểu dữ liệu hiện tại. Các ví dụ A/B/C/D bên dưới thuộc cấu hình cũ.

> Chốt trước 12:00 thứ 3 (29/09). Frontend code theo đúng shape này với JSON giả; backend trả số thật khi xong.
> **Nguồn chính là `docs/openapi.yaml`** (OpenAPI 3.1); file này là bản đọc nhanh, lệch thì theo yaml. JSON mẫu đúng shape: `frontend/src/shared/mock/`.
> Kiểm tra yaml: `npx @redocly/cli lint docs/openapi.yaml`. Chạy server giả: `npx @stoplight/prism-cli mock docs/openapi.yaml`.
> Base URL: `/api` (trên cloud, Caddy proxy `/api/*` sang backend cùng máy). Thời gian dùng ISO 8601 có múi giờ `+07:00`.

## Quy ước chung

- `lotId`: `A` | `B` | `C` | `D` · `gate`: `MAIN` (Cổng chính) | `EAST` (Cổng phía Đông)
- `level`: `green` (<80%) · `yellow` (80–<100%) · `red` (100–<120%) · `critical` (≥120%), tính theo sức chứa **thiết kế**
- `dataFlag`: `ok` | `suspect` | `signal_lost` | `estimated`
- Lỗi: `{ "error": "CODE", "message": "..." }` với HTTP 400 (dữ liệu sai), 401 (sai API key), 404 (`NO_PLAN` khi chưa có kế hoạch), 409 (`PLAN_STALE`: bản đã duyệt hoặc đã bị thay), 413 (`BATCH_TOO_LARGE`: quá 500 sự kiện), 503 (AI không phản hồi)
- Không đăng nhập (bản demo); chỉ `POST /api/events` cần `X-Api-Key`.
- Sự kiện từ nhà cung cấp dùng `snake_case`, phản hồi API dùng `camelCase`. Thời gian đầy đủ: ISO 8601 `+07:00`; giờ trong ngày: `HH:mm`.
- Khi trình giả lập chạy, mọi mốc thời gian là **giờ mô phỏng**; frontend lấy "bây giờ" từ `clock.now` ở `/api/lots/status`.
- AI **không bao giờ** nhận `card_hash` hay dữ liệu từng xe, chỉ số liệu tổng hợp.

---

## 1. `POST /api/events` — nhận sự kiện quẹt thẻ · Hào

Header: `X-Api-Key: <key của nhà cung cấp>`. Nhận 1 sự kiện hoặc mảng tối đa 500.

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
`direction`: `in` | `out` · `vehicle_type`: `motorbike` | `bicycle` | `car`

Trả về `200`:
```json
{ "accepted": 1, "duplicate": 0, "rejected": [] }
```
`rejected`: `[{ "event_id": "...", "reason": "UNKNOWN_LOT" }]`

## 2. `GET /api/lots/status` — trạng thái hiện tại · Hào

Frontend poll mỗi 5 giây. Ví dụ rút gọn (đủ 4 bãi: `frontend/src/shared/mock/lots-status.json`):

```json
{
  "clock": { "mode": "sim", "now": "2026-10-03T07:21:05+07:00" },
  "updatedAt": "2026-10-03T07:21:05+07:00",
  "lots": [
    { "lotId": "A", "name": "Bãi A", "count": 460, "capacity": 500, "maxCapacity": 600,
      "pct": 92.0, "level": "yellow", "gate": "MAIN", "nearBuilding": "Nhà hành chính",
      "lastEventAt": "2026-10-03T07:20:58+07:00", "dataFlag": "ok" }
  ],
  "totals": { "count": 1235, "overloadedLots": 0, "inflowLast15": 338 }
}
```
`clock.mode`: `sim` (giả lập đang chạy) | `real`.

## 3. `GET /api/forecast?lotId=A&date=2026-10-03` — dự báo · Hào

```json
{
  "lotId": "A", "date": "2026-10-03", "k": 1.073, "generatedAt": "2026-10-03T07:21:00+07:00",
  "peak": { "t": "07:45", "count": 605, "pct": 121.0 },
  "points": [
    { "t": "06:00", "actual": 20,  "forecast": 20,  "afterPlan": 20 },
    { "t": "07:15", "actual": 452, "forecast": 424, "afterPlan": 424 },
    { "t": "07:45", "actual": null, "forecast": 605, "afterPlan": 520 }
  ],
  "thresholds": { "yellow": 80, "over": 100, "critical": 120 }
}
```
`points` mỗi 15 phút từ 06:00 đến 13:00; `actual = null` cho thời điểm tương lai. `afterPlan` là sau khi áp dụng kế hoạch **đang áp dụng** (v3 chuyển 85 xe → 520). Ví dụ đầy đủ: `mock/forecast-A.json` (07:20, k = 1,073, đỉnh 605 = 121%).

## 4. `GET /api/plan/current` — kế hoạch phân luồng · Giang

```json
{
  "version": 3,
  "status": "approved",
  "createdAt": "2026-10-02T18:00:00+07:00",
  "window": { "from": "07:15", "to": "08:00" },
  "reason": "Bãi A dự báo 565/500 (113%) lúc 07:15–08:00",
  "before": [ { "lotId": "A", "count": 565, "pct": 113 }, { "lotId": "B", "count": 311, "pct": 89 },
              { "lotId": "C", "count": 324, "pct": 81 }, { "lotId": "D", "count": 198, "pct": 66 } ],
  "after":  [ { "lotId": "A", "count": 480, "pct": 96 }, { "lotId": "B", "count": 336, "pct": 96 },
              { "lotId": "C", "count": 384, "pct": 96 }, { "lotId": "D", "count": 198, "pct": 66 } ],
  "moves": [
    { "from": "A", "to": "C", "vehicles": 60, "quota": 60, "used": 12 },
    { "from": "A", "to": "B", "vehicles": 25, "quota": 25, "used": 0 }
  ],
  "excluded": [ { "lotId": "D", "reason": "Cổng chính đang quá tải" } ],
  "shortfall": 0,
  "gateActions": [ { "gate": "EAST", "openFrom": "06:45", "normalOpen": "07:00", "extraGuard": "06:45–08:00" } ],
  "envelope": { "maxMoveTotal": 150, "maxTargetPct": 1.0, "allowedDest": ["B", "C"] },
  "pending": null
}
```
`status`: `draft` | `approved` | `auto_applied` | `pending_approval`. Chưa có kế hoạch → `404 NO_PLAN`.
Khi `shortfall > 0` có thêm `shortfallOptions: [{ "code": "USE_LOT_D", "label": "..." }, { "code": "ACCEPT_HIGHER", "label": "..." }]` để quản trị chọn lúc duyệt.
Ví dụ đầy đủ kèm bản chờ duyệt (TC-05): `mock/plan-current.json`; không có bản chờ: `mock/plan-current-no-pending.json`.
Khi có bản vượt khung, `pending` chứa bản đó (cùng shape) để dashboard hiện nút Duyệt, còn bản đang áp dụng giữ nguyên ở ngoài.

## 5. `POST /api/plan/{version}/approve` — quản trị duyệt · Hào

Body: `{ "approvedBy": "admin", "option": "ACCEPT_HIGHER" }` (`option` bắt buộc khi `shortfall > 0`) → `200 { "version": 4, "status": "approved" }`. Bản đã duyệt hoặc đã bị thay bằng bản mới → `409 PLAN_STALE`.

## 6. `GET /api/student/recommendation?building=GDA&arrive=07:10&sessionId=...` — gợi ý sinh viên · Quyên

`building`: `NHC` (Nhà hành chính) | `TV` (Thư viện) | `GDA` (Giảng đường A) | `KTT` (Khu thể thao). `sessionId` (bắt buộc) do frontend sinh và lưu trên máy để giữ gợi ý cũ (chống nhảy). `arrive` (tùy chọn, `HH:mm`) mặc định là giờ hiện tại.
**Chưa có kế hoạch được duyệt:** trả gợi ý mặc định (bãi gần tòa nhà), `planVersion: null`, `changed: false`, không trả lỗi (`mock/student-recommendation-no-plan.json`). Kế hoạch đổi: `mock/student-recommendation-changed.json`.

```json
{
  "lotId": "C", "lotName": "Bãi C", "gate": "EAST", "gateName": "Cổng phía Đông",
  "openFrom": "06:45",
  "expectedPct": 96,
  "reason": "Bãi A dự kiến đầy lúc 07:15",
  "planVersion": 3,
  "changed": false,
  "changeMessage": null,
  "route": {
    "mapsUrl": "https://www.google.com/maps/dir/?api=1&destination=21.0000,105.0000",
    "path": [ [720, 400], [720, 520], [800, 520] ],
    "steps": [ "Vào Cổng phía Đông", "Đi thẳng trục chính ~80 m", "Bãi C bên phải, cạnh Giảng đường A" ],
    "walkMinutes": 2
  }
}
```
`path`: toạ độ điểm trên **sơ đồ SVG** của trường (không phải GPS). Toạ độ trong `mapsUrl` là toạ độ thật của cổng, lấy từ cấu hình.

## 7. `GET /api/config` — cấu hình trường (chỉ đọc) · Quyên

Trả nguyên file cấu hình (xem `05-du-lieu-mau.md`): bãi, cổng, tòa nhà, ca học, 8 tuyến chỉ đường, ngưỡng.

## 8. Trình giả lập · Quyên

| Endpoint | Body | Trả về |
|---|---|---|
| `POST /api/sim/start` | `{ "date": "2026-09-22", "speed": 30 }` (1 giây thật = 30 giây mô phỏng) | `{ "running": true, "simTime": "06:00", "speed": 30 }` |
| `POST /api/sim/stop` | — | `{ "running": false }` |
| `POST /api/sim/spike` | `{ "lotId": "A", "pct": 20 }` (bỏ `lotId` = mọi bãi) | `{ "spike": { "lotId": "A", "pct": 20 } }` |
| `GET /api/sim/state` | — | `{ "running", "simTime", "speed", "spike", "sent" }` |

Trình giả lập gửi sự kiện qua **đúng** `POST /api/events` như nhà cung cấp thật (có API key riêng `SIMULATOR`).
Backend dùng **đồng hồ mô phỏng** (`simTime`) khi trình giả lập đang chạy để dự báo lại theo giờ mô phỏng.

## 9. AI · Giang

Chi tiết prompt và schema: `08-ai-prompts.md`. Mọi endpoint trả thêm `source`: `gemini` | `groq` | `mock`. Thứ tự gọi Groq rồi Gemini; cả hai lỗi thì **503 `AI_UNAVAILABLE` kèm `fallback`** (phản hồi Mock cùng shape) để giao diện vẫn có nội dung.

| Endpoint | Body | Trả về |
|---|---|---|
| `POST /api/ai/chat` | `{ "question": "Sáng mai có bãi nào nguy cơ quá tải không?", "history": [] }` | `{ "answer": "markdown", "facts": [{ "label": "Bãi A", "value": "113%" }], "suggestions": ["..."], "source": "gemini" }` |

**Mốc thời gian của câu hỏi (`POST /api/ai/chat`)**: máy chủ đọc câu hỏi và lịch sử bằng quy tắc (`AiScope`, không gọi mô hình): *sáng/trưa/chiều/tối, hôm nay, ngày mai, lúc 8h / 8 giờ 30 / 08:15, ngày dd/MM, thứ…, bãi A/B/C1/C2*. Không nêu mốc thì kế thừa mốc câu hỏi trước; chưa từng có thì dùng mặc định (từ bây giờ đến hết hôm nay, hoặc sáng hôm sau nếu ngoài giờ) và ghi rõ trong `chart.title`. Số liệu dự báo lấy từ `ForecastProvider.series` (cùng hàm với trang Dự báo). `chart` có thêm: `title`, `date`, `from`, `to`, `at`, `note`, `defaulted` và mỗi bãi có `peakPct/peakTime` (đỉnh trong khung), `atPct/atTime` (tại giờ được hỏi), `afterPct` (sau kế hoạch), `series[{t,pct,afterPct}]`. Mọi phần trăm trong `answer` phải khớp số liệu; không khớp thì máy chủ thay bằng câu dựng từ số liệu và đặt `corrected: true`.

**Trợ lý theo vai (`POST /api/ai/chat`)**: quản lý nhận toàn cảnh khuôn viên và kế hoạch; **bảo vệ cũng hỏi được** nhưng backend chỉ đưa cho trợ lý ca, việc và các bãi ở cổng họ được phân công hôm nay cùng cảnh báo của các bãi đó (không có kế hoạch, quy tắc, cấu hình hay thông tin người khác); tối đa 10 câu mỗi phút mỗi bảo vệ, vượt thì 429 `RATE_LIMITED`. `history` gửi lượt trước bằng `{role, text}` (không phải `content`). Phản hồi, ngoài `answer` (markdown cơ bản: đậm, nghiêng, gạch đầu dòng), `facts`, `suggestions`, `source`, còn có `chart` = `{unit, lots:[{lotId,name,pct,count,capacity,dataFlag,peakPct?,peakTime?}]}` và `proposal` = `{version,status,shortfall,lines[]}` hoặc null (luôn null với bảo vệ). Cả hai do backend dựng từ số liệu, không lấy từ lời mô hình, nên khớp với số trong câu trả lời. Khi AI không dùng được, 503 vẫn kèm `fallback` (cùng dạng, `source: mock`) và giao diện hiển thị nó với nhãn "Dữ liệu mẫu".
| `POST /api/ai/map-columns` | `{ "kind": "swipe_log" \| "schedule", "headers": [...], "sampleRows": [[...]] }` (tối đa 5 dòng) | `{ "mappings": [{ "column": "ts_in", "field": "time_in", "confidence": 0.98 }], "source": "gemini" }` |
| `POST /api/ai/parse-rule` | `{ "text": "Báo cho tôi từ hôm trước nếu bãi nào sắp đầy" }` | `{ "rule": { "type", "scope", "from", "to", "metric", "op", "value", "action" }, "explanation": "..." }` |
| `POST /api/ai/report` | `{ "weekStart": "2026-09-14" }` | `{ "markdown": "...", "source": "gemini" }` |
| `GET /api/ai/mode` · `PUT /api/ai/mode` | `{ "mock": true }` | Công tắc Mock Data |

## 10. Nhóm Nên có / Cắt được (đã chốt sẵn hợp đồng, làm hết)

Shape đầy đủ trong `openapi.yaml`, JSON mẫu trong `frontend/src/shared/mock/`. Mức: **Nên có** = xong và test tối 01/10; **Cắt được** = làm nếu còn giờ, nhưng không được bỏ.

| Endpoint | Mức | Chủ | Mô tả ngắn | Mẫu |
|---|---|---|---|---|
| `GET /api/health` | Bắt buộc | Hào | `{status, db}` cho UptimeRobot và `deploy.sh` | |
| `GET /api/quality/flags` | Nên có | Hào | Chi tiết cờ `dataFlag`: `missing_out`, `implausible_count`, `signal_lost` | `quality-flags.json` |
| `POST /api/import/upload` | Nên có | Hào | Tải CSV (multipart) → `importId`, `headers`, `sampleRows`, `rowCount`, `errors` | `import-preview.json` |
| `POST /api/import/{importId}/confirm` | Nên có | Hào | Gửi `mappings` đã xác nhận → `imported`, `skipped`, `rejected`, `errors` (nhập thật; chỉ quản lý) | `import-result.json` |
| `GET /api/gates/status` | Cắt được | Giang | Màn Bảo vệ: xe vào từng cổng, dự báo, hành động mở sớm/thêm bảo vệ | `gates-status.json` |
| `GET /api/plan/{version}/compliance` | Cắt được | Giang | Tỷ lệ làm theo gợi ý (`arrived / issued`) | `compliance-v3.json` |
| `GET /api/stream` (SSE) | Cắt được | Hào | Sự kiện `lots`, `plan`, `alert`, `ping`; frontend vẫn phải chạy được bằng poll 5 giây | |
| `PUT /api/config` | Cắt được | Quyên | Sửa cấu hình trường (kiểm tra hợp lệ, sai thì 400 và giữ bản cũ) | |
| `POST /api/ai/parse-rule` | Cắt được | Giang | Câu tiếng Việt → `RuleDraft` để xem trước | `parse-rule.json` |
| `GET/POST /api/rules`, `PATCH/DELETE /api/rules/{id}` | Cắt được | Giang | Lưu, liệt kê, sửa một phần (bật/tắt, đổi trường), xóa quy tắc đã xác nhận | `rules.json` |
| `GET /api/alerts` | Cắt được | Giang | Cảnh báo do quy tắc kích hoạt | `alerts.json` |
| `POST /api/ai/report` | Cắt được | Giang | Báo cáo tuần dạng markdown | |
| Ký HMAC trên `POST /api/events` | Cắt được | Hào | Header `X-Timestamp` + `X-Signature` = hex(HMAC-SHA256(secret, timestamp + "." + body)); lệch > 5 phút hoặc sai chữ ký → `401 BAD_SIGNATURE`; bật theo từng nhà cung cấp | |

`GET /api/schedule/summary?day=` (quản lý và bảo vệ): lịch học đã nhập theo giờ bắt đầu và tòa nhà. Nhập log quẹt thẻ ghi vào `events` như dữ liệu lịch sử (không đổi số xe hiện tại; mã thẻ băm; mã sự kiện trùng với log đóng gói nên nhập lại không nhân đôi); lịch học ghi vào `class_schedule`. Dự báo dùng lịch học qua `scheduleFactor` (phản hồi `GET /api/forecast`): tỷ lệ giữa số sinh viên vào lớp trong giờ tới của ngày đó và trung bình các ngày cùng thứ đã nhập lịch, chặn trong 0,7 đến 1,5, chỉ áp khi có ít nhất 2 ngày lịch cùng thứ để so sánh (nếu không thì bằng 1). Đây là tỷ lệ so với thường lệ, chưa phải hệ số hiệu chỉnh bằng dữ liệu; tắt bằng `PARKFLOW_FORECAST_SCHEDULE_ADJUST=false`.
Luồng trang Nhập dữ liệu: `import/upload` → `ai/map-columns` (cột `confidence < 0.8` bắt buộc xác nhận) → `import/{id}/confirm`.

## 10. Bổ sung 02/10: đăng nhập, vai, ca trực, nhiệm vụ, số liệu cho sinh viên

Quyền theo vai do backend kiểm ở mọi `/api/**` (`AccessPolicy`, mặc định chỉ quản lý). Cookie phiên `pf_session` (`HttpOnly`, `SameSite=Strict`). Lỗi quyền: `401 UNAUTHORIZED` (chưa đăng nhập) và `403 FORBIDDEN` (sai vai).

| Endpoint | Vai | Mô tả |
|---|---|---|
| `POST /api/auth/login` `{username, password}` | công khai | Đăng nhập; 401 `INVALID_CREDENTIALS`, 429 `TOO_MANY_ATTEMPTS` sau 5 lần sai/15 phút |
| `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/password` `{current, next}` | đã đăng nhập | Đăng xuất, thông tin người dùng, đổi mật khẩu của mình |
| `GET /api/users`, `POST /api/users`, `PATCH /api/users/{id}` | quản lý | Tài khoản `{username, fullName, role: manager\|guard, password}`; khóa/mở (`active`), đặt lại mật khẩu (`password`), đổi vai; khóa hoặc đặt lại thì hủy phiên ngay; không khóa được quản lý cuối cùng hay chính mình |
| `GET /api/staff/board?day=` | quản lý | Theo cổng: ca trực, nhiệm vụ (trạng thái, ai nhận lúc nào, `overdue` sau 15 phút chưa nhận), `summary` |
| `POST /api/assignments` `{userId, gate, day?, from, to, note?}`, `DELETE /api/assignments/{id}` | quản lý | Phân công bảo vệ vào cổng trong ngày |
| `POST /api/tasks` `{gate, title, detail?, from?, to?, assignedTo?, day?}`, `PATCH /api/tasks/{id}` `{status:"cancelled"}` | quản lý | Giao việc; `assignedTo` rỗng thì mọi bảo vệ đang trực cổng đó đều thấy |
| `GET /api/my/gate-status` | bảo vệ | Tình hình hiện tại các bãi ở cổng đang trực, **không gọi mô hình**: `{asOf, openTasks, chart, alerts, note?}`; `chart` cùng dạng với `/api/ai/chat`. Trang Hỏi đáp của bảo vệ gọi mỗi 30 giây khi tab đang hiện |
| `GET /api/my/tasks` | bảo vệ, quản lý | `{day, assignments, tasks}` của chính mình |
| `GET /api/default-shifts`, `POST /api/default-shifts` `{userId, gate, from, to, weekdays[1-7], note?}`, `PATCH /api/default-shifts/{id}` `{active?, from?, to?, weekdays?, note?}`, `DELETE /api/default-shifts/{id}` | quản lý | Ca mặc định của bảo vệ. Màn Nhân sự cho phép thêm, sửa giờ/thứ/ghi chú, bật/tắt và xóa. Bộ lập lịch (mỗi phút) tự tạo ca cho hôm nay và ngày mai từ các ca mặc định đang bật khớp thứ trong tuần; không nhân đôi, không tạo lại ca quản lý đã xóa tay trong ngày đó, bỏ qua tài khoản bị khóa. Xóa ca mặc định cũng bỏ các ca tự tạo từ hôm nay trở đi. Mỗi ngày báo quản lý (chuông) một lần cho cổng chưa có bảo vệ. `GET /api/staff/board` đánh dấu ca tự tạo bằng `auto: true` | Sửa giờ, ghi chú hoặc thứ thì các ca đã tự tạo từ hôm nay trở đi đổi theo (ca ở thứ không còn bị bỏ; ca các ngày đã qua giữ nguyên); bật/tắt không đụng ca đã có.
| `GET /api/rules/activity` | quản lý | Quy tắc đang tác động thế nào, mỗi quy tắc một dòng: cảnh báo có `firedToday`, `lastFiredAt`, `lastMessage`, `notifiesManagers` (số quản lý) và `notifiesGuards` (tên bảo vệ đang trực cổng của bãi trong phạm vi quy tắc theo ca hôm nay); điều phối có `thresholdPct` và `inCurrentPlan`/`planVersion` (quy tắc đang bật và được nêu trong lý do của kế hoạch hiện hành). Cảnh báo của quy tắc vào chuông của quản lý và của các bảo vệ đó |
| `GET /api/staff/roster?day=` | quản lý | Danh sách bảo vệ đang hoạt động: `online` (thấy trong 90 giây qua), ca trong ngày, số việc `open`/`acknowledged`/`done`/`overdue` trên đúng các việc họ nhìn thấy, `lastActionAt` |
| `GET /api/plan/current` (bổ sung) | quản lý, bảo vệ | Ngoài các trường cũ có thêm: `targetDate` (ngày kế hoạch áp dụng), `appliedVersion` (phiên bản đang có hiệu lực, null nếu chưa có), `awaitingDecision` và `awaiting` (bản **đang chờ người quyết**, kể cả bản nháp đầu ngày chưa có bản nào áp dụng). Bản nháp **không** phải bản đang áp dụng; trường `pending` cũ giữ nguyên để tương thích |
| `PUT /api/automation` (bổ sung) | quản lý | Đổi chế độ có tác dụng ngay: phản hồi có `effect` = `{action: auto_approved|still_waiting|nothing_pending, version, date, note}` do xét lại kế hoạch đang chờ (hôm nay, rồi ngày mai) theo chế độ mới; `modes[].example` là ví dụ cụ thể của từng chế độ |
| `GET /api/automation/log` | quản lý | 10 lần gần nhất hệ thống tự duyệt thay người: `{version, targetDate, at, shortfall, summary}` |
| `GET /api/automation`, `PUT /api/automation` `{mode?, ackMinutes?}` | quản lý | Ủy quyền cho hệ thống: `mode` = `manual` (mọi kế hoạch chờ duyệt) / `semi` (mặc định: tự duyệt khi không còn thiếu chỗ) / `auto` (tự duyệt cả khi thiếu chỗ, chọn phương án có mức lấp đầy cao nhất thấp nhất trong các phương án bộ phân bổ đã tính, không có thì chấp nhận mức cao hơn). `ackMinutes` (1 đến 240, mặc định 15) là hạn nhận việc. Kế hoạch tự duyệt ghi người duyệt `Hệ thống (tự động)` và báo quản lý qua chuông. `POST /api/plan/{v}/approve` trả thêm `tasksCreated` (nhiệm vụ vừa sinh cho hôm nay) |
| `GET /api/my/notifications?limit=30`, `POST /api/my/notifications/read` `{ids?}` | bảo vệ, quản lý | Chuông thông báo của chính mình: `{unread, items:[{id,kind,title,body,link,createdAt,read}]}` (mới nhất trước, tối đa 100). `read` không có `ids` là đánh dấu tất cả; chỉ đụng thông báo của chính người gọi. Gọi định kỳ cũng đánh dấu người dùng đang online (`GET /api/users` có `lastSeenAt` và `online` = thấy trong 90 giây qua). Nguồn: nhiệm vụ mới và quá hạn (bộ quét 10 giây `TaskNotifier`), bảo vệ nhận/xong (cho quản lý), kế hoạch cần duyệt hoặc tự áp dụng, cảnh báo của quy tắc (quản lý và bảo vệ trực cổng bãi đó). Mỗi sự kiện báo một lần cho mỗi người; thiết bị đã đăng ký Web Push nhận thêm thông báo đẩy |
| `GET /api/my/push/key`, `POST /api/my/push` `{endpoint, keys:{p256dh,auth}}`, `DELETE /api/my/push` `{endpoint}` | bảo vệ, quản lý | Thông báo đẩy: khóa công khai VAPID và trạng thái thiết bị; đăng ký/gỡ thiết bị (503 `PUSH_DISABLED` nếu máy chủ chưa có khóa; chỉ gỡ được thiết bị của chính mình). Bộ quét 10 giây gửi nhiệm vụ mới cho đúng người được giao hoặc mọi bảo vệ trực cổng đó hôm nay, mỗi nhiệm vụ một lần |
| `POST /api/my/tasks/{id}/ack`, `POST /api/my/tasks/{id}/done` `{note}` | bảo vệ, quản lý | Nhận, hoàn thành (ghi tài khoản và giờ). **`note` bắt buộc khi báo xong** (ít nhất 3 ký tự, tối đa 300), thiếu thì 400 `NOTE_REQUIRED`; 403 sai cổng, 409 đã xử lý. Mỗi việc kèm `outcome` = `{lots, pctCreated, pctDone, pctAfter}`: mức lấp đầy (%) cao nhất của các bãi dùng cổng đó lúc tạo việc, lúc báo xong và 15 phút sau (giờ thật), null nếu chưa đo; đây là bằng chứng gián tiếp, không có cảm biến chứng minh cổng đã mở |
| `GET /api/student/lots` | công khai | `{updatedAt, lots:[{lotId, count, capacity, pct, dataFlag}]}` nhẹ, cho bản đồ sinh viên |
| `GET /api/flow?date=` | bảo vệ, quản lý | Xe vào theo 15 phút: `{date, slots:[{t, actual\|null, forecast\|null}]}` |
| `GET /api/sim/dates` | công khai | Ngày có log giả lập (`default`: 2026-09-22) |

Nhiệm vụ tự sinh (`TaskGenerator`, mỗi 10 giây): từ `gateActions` của kế hoạch đang áp dụng (mở cổng sớm, thêm bảo vệ; một nhiệm vụ cho mỗi ngày, cổng, loại, phiên bản; phiên bản cũ chưa ai nhận thì hủy) và khi cổng ở mức đỏ trở lên (điều hướng xe). `GET /api/student/recommendation` có thêm `basis` (`plan|forecast|live`); `GET /api/lots/status` có `dataFlag: "estimated"` khi hôm nay chưa có sự kiện và số hiển thị là ước tính theo lịch sử.
