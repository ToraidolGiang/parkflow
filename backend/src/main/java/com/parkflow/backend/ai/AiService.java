package com.parkflow.backend.ai;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Trợ lý hỏi đáp trên số liệu đã tính. Thứ tự gọi: các nhà cung cấp theo thứ tự ưu tiên (Groq rồi Gemini); công tắc Mock bật thì không gọi ai.
 * Đầu ra sai schema thì hỏi lại 1 lần, vẫn sai thì sang nhà cung cấp kế tiếp; cả hai lỗi thì {@link AiUnavailable} kèm câu trả lời Mock.
 * Nhật ký chỉ ghi tên nhà cung cấp và loại lỗi, không ghi câu hỏi, số liệu hay câu trả lời.
 */
@Slf4j
@Service
public class AiService {
    public static final int MAX_QUESTION = 500;
    static final String CHAT_SYSTEM = """
            Bạn là trợ lý điều phối bãi xe ParkFlow của trường đại học. Trả lời bằng tiếng Việt, ngắn gọn, cho ban quản trị và tổ bảo vệ.
            Chỉ dùng số liệu trong FACTS. Không tự tính thêm, không bịa số. Nếu FACTS không có thông tin, nói rõ là chưa có dữ liệu.
            Luôn nêu: bãi nào, bao nhiêu %, khung giờ nào, đề xuất gì. Kết thúc bằng một câu nhắc: phương án cần quản trị duyệt trước khi áp dụng.
            Nếu FACTS có forecastNote thì không được kết luận về nguy cơ quá tải. Mọi phần trăm bạn viết phải là số nguyên văn trong FACTS.
            Trả về JSON đúng schema: {"answer":"markdown","facts":[{"label":"...","value":"..."}],"suggestions":["..."]}
            Trong facts: label là "Bãi A", value là chuỗi có đơn vị, ví dụ "113%" hoặc "605 xe".
            """;

    /** Một lượt trò chuyện trước đó: `role` là "user" hoặc "assistant". */
    public record Turn(String role, String text) {}

    private final List<AiClient> clients;
    private final AiMode mode;
    private final AiFacts facts;
    private final MockAi mock;
    private final ObjectMapper mapper;

    public AiService(List<AiClient> clients, AiMode mode, AiFacts facts, MockAi mock, ObjectMapper mapper) {
        this.clients = clients;
        this.mode = mode;
        this.facts = facts;
        this.mock = mock;
        this.mapper = mapper;
    }

    public ObjectNode chat(String question, List<Turn> history) {
        if (question == null || question.isBlank() || question.length() > MAX_QUESTION) throw new IllegalArgumentException("question");
        ObjectNode data = facts.build();
        if (mode.mock()) return withSource(mock.chat(data), MockAi.SOURCE);

        StringBuilder user = new StringBuilder("FACTS: ").append(mapper.writeValueAsString(data));
        if (history != null) {
            history.stream().skip(Math.max(0, history.size() - 6)).forEach(turn -> {
                if (turn != null && turn.text() != null && ("user".equals(turn.role()) || "assistant".equals(turn.role())))
                    user.append("\nLỊCH SỬ ").append(turn.role()).append(": ").append(cut(turn.text(), MAX_QUESTION));
            });
        }
        user.append("\nCÂU HỎI: ").append(question.strip());

        ObjectNode result = run(CHAT_SYSTEM, user.toString(), AiService::validChat, () -> withSource(mock.chat(data), MockAi.SOURCE));
        if (!MockAi.SOURCE.equals(result.path("source").asString()) && !NumberGuard.onlyKnownPercentages(result.path("answer").asString(), data)) {
            log.warn("AI {} nêu phần trăm không có trong số liệu: thay bằng câu trả lời dữ liệu mẫu", result.path("source").asString());
            result = withSource(mock.chat(data), MockAi.SOURCE);
            result.put("guarded", true);
        }
        if (!result.has("facts")) result.putArray("facts");
        if (!result.has("suggestions")) result.putArray("suggestions");
        return result;
    }

    /** Gọi lần lượt các nhà cung cấp đang bật; trả JSON đã qua kiểm tra schema kèm `source`. */
    ObjectNode run(String system, String user, Predicate<JsonNode> valid, Supplier<ObjectNode> fallback) {
        for (AiClient client : clients) {
            if (!client.enabled()) continue;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    JsonNode parsed = parse(client.complete(system, user));
                    if (parsed != null && parsed.isObject() && valid.test(parsed)) return withSource((ObjectNode) parsed, client.source());
                    log.warn("AI {} trả sai schema (lần {})", client.source(), attempt + 1);
                } catch (AiClient.AiUnavailableException e) {
                    log.warn("AI {} không dùng được: {}", client.source(), e.getMessage());
                    break;   // lỗi mạng, hạn mức, quá hạn chờ: không hỏi lại, sang nhà cung cấp kế tiếp
                }
            }
        }
        throw new AiUnavailable("Trợ lý AI tạm thời không dùng được.", fallback.get());
    }

    /** Lấy JSON từ văn bản mô hình trả, bỏ hàng rào ```json nếu có. Không phải JSON thì null. */
    JsonNode parse(String raw) {
        if (raw == null) return null;
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            text = firstLine < 0 ? "" : text.substring(firstLine + 1);
            int fence = text.lastIndexOf("```");
            if (fence >= 0) text = text.substring(0, fence);
            text = text.strip();
        }
        try {
            return mapper.readTree(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean validChat(JsonNode node) {
        JsonNode answer = node.get("answer");
        if (answer == null || !answer.isString() || answer.asString().isBlank() || answer.asString().length() > 4000) return false;
        JsonNode shown = node.get("facts");
        if (shown != null) {
            if (!shown.isArray()) return false;
            for (JsonNode fact : shown) if (!fact.path("label").isString() || !fact.path("value").isString()) return false;
        }
        JsonNode suggestions = node.get("suggestions");
        if (suggestions != null) {
            if (!suggestions.isArray()) return false;
            for (JsonNode suggestion : suggestions) if (!suggestion.isString()) return false;
        }
        return true;
    }

    private static ObjectNode withSource(ObjectNode node, String source) {
        node.put("source", source);
        return node;
    }

    private static String cut(String text, int max) {
        String single = text.replace('\n', ' ').strip();
        return single.length() <= max ? single : single.substring(0, max);
    }
}
