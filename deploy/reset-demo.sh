#!/usr/bin/env bash
# Đưa dữ liệu demo về trạng thái sạch giữa các lượt diễn tập.
# Chỉ chạy trên DB demo. Giữ nguyên cấu hình campus và đặt lại sự kiện/kế hoạch.
set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE_FILE="deploy/docker-compose.prod.yml"
ENV_FILE="deploy/.env.prod"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Thiếu $ENV_FILE. Sao chép deploy/prod.env.example và điền DB_PASSWORD." >&2
  exit 1
fi
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop backend
trap 'docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" start backend' EXIT
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T db \
  psql -v ON_ERROR_STOP=1 -U parkflow -d parkflow <<'SQL'
BEGIN;
TRUNCATE events, plan_state, plan_versions RESTART IDENTITY CASCADE;
UPDATE lots SET current_count=0, raw_count=0, last_event_at=NULL, updated_at=now();
COMMIT;
SQL
echo ">> Đã reset. Bật lại trình giả lập khi cần."
