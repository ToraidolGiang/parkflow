-- JSON giữ đủ tòa nhà, tuyến và metadata cho GET /api/config.
CREATE TABLE campus_config (
    id SMALLINT PRIMARY KEY CHECK(id=1),
    body JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE forecast_gate_shares (
    lot_id VARCHAR(20) NOT NULL REFERENCES lots(lot_id),
    gate VARCHAR(20) NOT NULL REFERENCES forecast_gates(gate),
    share DOUBLE PRECISION NOT NULL CHECK(share>=0 AND share<=1),
    PRIMARY KEY(lot_id,gate)
);
CREATE TABLE lot_alternates (
    lot_id VARCHAR(20) NOT NULL REFERENCES lots(lot_id),
    alternate_id VARCHAR(20) NOT NULL REFERENCES lots(lot_id),
    rank INTEGER NOT NULL CHECK(rank>0),
    PRIMARY KEY(lot_id,alternate_id),
    CHECK(lot_id<>alternate_id)
);
