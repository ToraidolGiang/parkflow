CREATE TABLE plan_versions (
    version BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('draft','pending_approval','approved','auto_applied')),
    body JSONB NOT NULL CHECK (jsonb_typeof(body) = 'object'),
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    approved_by TEXT
);
CREATE TABLE plan_state (
    target_date DATE PRIMARY KEY,
    latest_version BIGINT REFERENCES plan_versions(version),
    applied_version BIGINT REFERENCES plan_versions(version)
);
CREATE INDEX idx_plan_versions_date ON plan_versions(target_date, version DESC);