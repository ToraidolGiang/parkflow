-- Cảnh báo do quy tắc kích hoạt. Mỗi (quy tắc, bãi) chỉ báo một lần mỗi ngày (khóa duy nhất theo ngày).
CREATE TABLE alerts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
    at TIMESTAMPTZ NOT NULL,
    day DATE NOT NULL,
    lot_id VARCHAR(20) NOT NULL,
    message VARCHAR(300) NOT NULL,
    UNIQUE (rule_id, lot_id, day)
);
CREATE INDEX idx_alerts_at ON alerts (at DESC);
