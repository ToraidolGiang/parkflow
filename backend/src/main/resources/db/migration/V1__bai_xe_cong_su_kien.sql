-- Lược đồ lõi: bãi xe, cổng và sự kiện quẹt thẻ.
-- Số liệu mô tả: docs/thiet-ke/02-giai-phap-du-lieu.md và 04-api.md (mục 1 và 2).

-- Bãi xe. `capacity` là sức chứa thiết kế (100%); `max_capacity` là mức tối đa chịu được (tối đa 120% thiết kế).
-- `current_count` là số xe hiển thị (đã kẹp, không âm); `raw_count` là số tính thô từ sự kiện (có thể âm khi thiếu lượt vào).
CREATE TABLE lots (
    lot_id        VARCHAR(20) PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    capacity      INTEGER      NOT NULL CHECK (capacity > 0),
    max_capacity  INTEGER      NOT NULL CHECK (max_capacity >= capacity),
    gate          VARCHAR(20),
    current_count INTEGER      NOT NULL DEFAULT 0 CHECK (current_count >= 0),
    raw_count     INTEGER      NOT NULL DEFAULT 0,
    last_event_at TIMESTAMP WITH TIME ZONE,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Cổng vào. `capacity_per_15` là số xe tối đa qua cổng trong 15 phút; `normal_open` là giờ mở cổng bình thường (HH:mm).
CREATE TABLE forecast_gates (
    gate            VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    capacity_per_15 INTEGER      NOT NULL CHECK (capacity_per_15 > 0),
    normal_open     VARCHAR(5)   NOT NULL DEFAULT '06:00'
);

-- Sự kiện quẹt thẻ. `event_id` do nhà cung cấp tạo và là khóa chống trùng (gửi lại cùng mã thì không tính hai lần).
-- `card_hash` là mã thẻ đã băm; hệ thống không bao giờ lưu số thẻ gốc. `historical` đánh dấu sự kiện nạp từ lịch sử (không phải phát trực tiếp).
CREATE TABLE events (
    event_id     VARCHAR(64) PRIMARY KEY,
    ts           TIMESTAMP WITH TIME ZONE NOT NULL,
    lot_id       VARCHAR(20)  NOT NULL REFERENCES lots (lot_id),
    direction    VARCHAR(3)   NOT NULL CHECK (direction IN ('in', 'out')),
    vehicle_type VARCHAR(20)  NOT NULL,
    card_hash    VARCHAR(128) NOT NULL,
    historical   BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_lot_ts ON events (lot_id, ts);
CREATE INDEX idx_events_card_ts ON events (card_hash, ts);
