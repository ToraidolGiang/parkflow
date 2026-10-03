package com.parkflow.backend.rules;

import com.parkflow.backend.common.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Lưu, liệt kê, sửa một phần và xóa quy tắc đã được quản trị xác nhận. Mọi thay đổi đều qua kiểm tra {@link RuleDraft#problems}. */
@Service
@RequiredArgsConstructor
public class RuleStore {
    /** Trường được phép gửi trong PATCH; `id`, `createdAt` và trường lạ bị từ chối. */
    private static final Set<String> PATCHABLE = Set.of("enabled", "name", "type", "scope", "from", "to", "metric", "op", "value", "action");

    public record Rule(long id, String name, String type, String scope, String from, String to, String metric, String op,
                       double value, String action, boolean enabled, String createdAt) {}

    private final JdbcTemplate jdbc;

    public Set<String> lotIds() {
        return new HashSet<>(jdbc.queryForList("SELECT lot_id FROM lots", String.class));
    }

    public List<Rule> list() {
        return jdbc.query("SELECT * FROM rules ORDER BY id", (rs, i) -> map(rs));
    }

    public Rule create(RuleDraft draft) {
        validate(draft);
        long id = jdbc.queryForObject("""
                INSERT INTO rules (name, type, scope, from_time, to_time, metric, op, value, action)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, draft.name().strip(), draft.type(), draft.scope(), draft.from(), draft.to(), draft.metric(),
                draft.op(), draft.value(), draft.action());
        return get(id);
    }

    public Rule get(long id) {
        List<Rule> found = jdbc.query("SELECT * FROM rules WHERE id = ?", (rs, i) -> map(rs), id);
        if (found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Không có quy tắc này.");
        return found.get(0);
    }

    /** Chỉ gửi trường cần đổi. Thân rỗng, trường lạ hoặc quy tắc sau khi gộp không hợp lệ thì 400 và giữ nguyên bản cũ. */
    public Rule patch(long id, JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Thân yêu cầu rỗng: cần ít nhất một trường để sửa.");
        body.propertyNames().forEach(field -> {
            if (!PATCHABLE.contains(field))
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Không được sửa trường `" + field + "`.");
        });
        Rule current = get(id);
        boolean enabled = body.has("enabled") ? bool(body.get("enabled")) : current.enabled();
        RuleDraft merged = new RuleDraft(
                text(body, "name", current.name()), text(body, "type", current.type()), text(body, "scope", current.scope()),
                text(body, "from", current.from()), text(body, "to", current.to()), text(body, "metric", current.metric()),
                text(body, "op", current.op()), body.has("value") ? number(body.get("value")) : current.value(),
                text(body, "action", current.action()));
        validate(merged);
        jdbc.update("""
                UPDATE rules SET name = ?, type = ?, scope = ?, from_time = ?, to_time = ?, metric = ?, op = ?, value = ?,
                       action = ?, enabled = ? WHERE id = ?
                """, merged.name().strip(), merged.type(), merged.scope(), merged.from(), merged.to(), merged.metric(), merged.op(),
                merged.value(), merged.action(), enabled, id);
        return get(id);
    }

    public void delete(long id) {
        if (jdbc.update("DELETE FROM rules WHERE id = ?", id) == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Không có quy tắc này.");
    }

    private void validate(RuleDraft draft) {
        List<String> problems = draft.problems(lotIds());
        if (!problems.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", problems.get(0));
    }

    private static String text(JsonNode body, String field, String fallback) {
        if (!body.has(field)) return fallback;
        JsonNode node = body.get(field);
        if (!node.isString()) throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "`" + field + "` phải là chuỗi.");
        return node.asString();
    }

    private static Double number(JsonNode node) {
        if (!node.isNumber()) throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "`value` phải là số.");
        return node.asDouble();
    }

    private static boolean bool(JsonNode node) {
        if (!node.isBoolean()) throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "`enabled` phải là true hoặc false.");
        return node.asBoolean();
    }

    private static Rule map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Rule(rs.getLong("id"), rs.getString("name"), rs.getString("type"), rs.getString("scope"), rs.getString("from_time"),
                rs.getString("to_time"), rs.getString("metric"), rs.getString("op"), rs.getDouble("value"), rs.getString("action"),
                rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant().atZone(com.parkflow.backend.common.ParkFlowClock.ZONE)
                .toOffsetDateTime().toString());
    }
}
