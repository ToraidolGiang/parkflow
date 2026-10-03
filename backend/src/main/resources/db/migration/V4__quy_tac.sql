-- Quy tắc cảnh báo và điều hướng do quản trị xác nhận (tạo từ câu tiếng Việt hoặc nhập tay).
CREATE TABLE rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('alert', 'routing')),
    scope VARCHAR(20) NOT NULL,
    from_time VARCHAR(5) NOT NULL,
    to_time VARCHAR(5) NOT NULL,
    metric VARCHAR(20) NOT NULL CHECK (metric IN ('forecast_pct', 'current_pct', 'inflow_15m')),
    op VARCHAR(2) NOT NULL CHECK (op IN ('>=', '>', '<=', '<')),
    value DOUBLE PRECISION NOT NULL,
    action VARCHAR(300) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
