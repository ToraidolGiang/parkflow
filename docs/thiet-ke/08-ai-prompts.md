# AI — prompt, schema và dự phòng

> Giang phụ trách. Thử từng prompt trên Google AI Studio trước khi code; ghi lại phiên bản ổn định vào đây.

## 1. Nguyên tắc chung

- **Số liệu do bộ tính tạo ra, AI chỉ diễn giải.** Backend gom số liệu tổng hợp thành `facts` rồi mới gọi AI.
- AI không nhận `card_hash`, `card_id` hay dữ liệu từng xe.
- Mọi đầu ra dạng JSON đều **kiểm tra schema** trước khi trả frontend; sai schema → gọi lại 1 lần → vẫn sai thì trả lỗi rõ ràng.
- Thứ tự gọi: **Groq (`openai/gpt-oss-120b`) → Gemini (`gemini-3.1-flash-lite`)**; đã đảo so với bản đầu vì Gemini bản miễn phí hay quá tải. Timeout mỗi nhà cung cấp 8 giây; sai schema thì hỏi lại 1 lần rồi chuyển nhà cung cấp kế tiếp. Model đổi được bằng biến `GROQ_MODEL`, `GEMINI_MODEL` (nhà cung cấp hay đổi hoặc ngừng model).
- Cả hai đều lỗi: **503 `AI_UNAVAILABLE` kèm `fallback` là câu trả lời Mock** (dựng từ số liệu thật, `source` = `mock`). Công tắc Mock bật thủ công thì trả 200 `source` = `mock`.
- Công tắc Mock trên UI (`PUT /api/ai/mode`) để demo không gián đoạn; khi Mock bật, UI ghi rõ "Dữ liệu mẫu".
- Key để trong biến môi trường `GEMINI_API_KEY`, `GROQ_API_KEY`, không commit.

## 2. Trợ lý hỏi đáp — `POST /api/ai/chat`

**System prompt**
```
Bạn là trợ lý điều phối bãi xe ParkFlow của trường đại học. Trả lời bằng tiếng Việt, ngắn gọn, cho ban quản trị và tổ bảo vệ.
Chỉ dùng số liệu trong FACTS. Không tự tính thêm, không bịa số. Nếu FACTS không có thông tin, nói rõ là chưa có dữ liệu.
Luôn nêu: bãi nào, bao nhiêu %, khung giờ nào, đề xuất gì. Kết thúc bằng một câu nhắc: phương án cần quản trị duyệt trước khi áp dụng.
Trả về JSON đúng schema.
```

**User message**
```
FACTS: {"date":"2026-09-22","lots":[...],"plan":{...},"forecastPeaks":[...],"dataFlags":[...]}
CÂU HỎI: Sáng mai có bãi nào nguy cơ quá tải không?
```

**Schema đầu ra**
```json
{ "answer": "string (markdown)", "facts": [{ "label": "string", "value": "string" }], "suggestions": ["string"] }
```

**Câu hỏi gợi ý trên UI:** "Sáng mai có bãi nào nguy cơ quá tải không?" · "Tuần này bãi nào căng nhất, vào mấy giờ?" · "Sáng mai cần thêm bảo vệ ở cổng nào?"

## 3. Ánh xạ cột — `POST /api/ai/map-columns`

**System prompt**
```
Bạn ánh xạ cột của file dữ liệu bãi xe vào trường hệ thống ParkFlow.
Trường hệ thống cho log quẹt thẻ: time_in, time_out, gate, card_id, lot, vehicle_type.
Trường hệ thống cho lịch học: class_id, building, date, start_time, end_time, students.
Dựa vào tên cột VÀ giá trị mẫu. Mỗi cột trả đúng một trường hoặc "ignore".
confidence từ 0 đến 1; giá trị viết tắt hoặc mơ hồ thì confidence < 0.8. Trả JSON đúng schema.
```

**Schema đầu ra**
```json
{ "mappings": [{ "column": "string", "field": "string", "confidence": 0.0, "note": "string" }] }
```

Frontend: cột < 0,8 tô vàng/cam, bắt tick "Tôi đã kiểm tra ánh xạ …" mới cho bấm Xem trước.

## 4. Câu nói → quy tắc — `POST /api/ai/parse-rule` (Cắt được)

**Schema đầu ra**
```json
{
  "rule": { "name": "string", "type": "alert|routing", "scope": "ALL|A|B|C|D",
            "from": "HH:mm", "to": "HH:mm", "metric": "forecast_pct|current_pct|inflow_15m",
            "op": ">=|>|<=|<", "value": 0, "action": "string" },
  "explanation": "string (tiếng Việt, một câu: Nếu … thì …)"
}
```
`routing` chỉ khi câu nói muốn điều hướng/chuyển xe; bắt buộc `forecast_pct`, `>=`/`>`, giá trị 50–100 (kế hoạch phân luồng dùng nó làm ngưỡng kích hoạt, docs/03 mục 4b). Còn lại là `alert`.

Ví dụ: "Báo cho tôi từ hôm trước nếu bãi nào sắp đầy trước giờ vào học" → `alert`, `ALL`, 06:30–08:00, `forecast_pct >= 90`, "Thông báo điều hành từ 18:00 hôm trước".

## 5. Báo cáo tuần — `POST /api/ai/report` (Cắt được)

Backend gửi số liệu tuần đã tổng hợp (tổng lượt, lấp đầy trung bình, số ngày quá tải từng bãi, đỉnh, khung cao điểm, kế hoạch tuần tới). AI viết markdown 4 mục: **Tóm tắt tuần · Bãi căng nhất · Khung giờ cao điểm · Đề xuất vận hành tuần tới**. Khối "Trước / sau điều hướng" do frontend vẽ từ `/api/plan/current`, không để AI vẽ.

## 6. Dữ liệu Mock

Mỗi endpoint có một file JSON mẫu trong `backend/src/main/resources/mock/` đúng schema, nội dung khớp số liệu 22/09 (A 113%, chuyển 85 xe: 60 sang C, 25 sang B, Cổng phía Đông mở 06:45).
