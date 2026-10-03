#!/usr/bin/env bash
# Chạy TRÊN máy chủ (do release.sh gọi qua SSH): giải nén bản mới, sao lưu CSDL, dựng ảnh khi bản cũ vẫn chạy,
# thay bản, kiểm tra sức khỏe, và tự quay về bản trước nếu bản mới không lên.
# Dùng: remote-release.sh <thư-mục-triển-khai> <mã-bản> ; gói mã ở <thư-mục>/incoming/<mã-bản>.tar.gz
set -euo pipefail

BASE="${1:?Thiếu thư mục triển khai}"
ID="${2:?Thiếu mã bản}"
RELEASES="$BASE/releases"
SHARED="$BASE/shared"
ENV_FILE="$SHARED/.env.prod"
KEEP=4   # giữ bấy nhiêu bản gần nhất để quay lại

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
compose() { local rel="$1"; shift; docker compose -p parkflow-thi -f "$RELEASES/$rel/deploy/docker-compose.prod.yml" --env-file "$ENV_FILE" "$@"; }

mkdir -p "$RELEASES" "$SHARED" "$BASE/backups"
[ -f "$ENV_FILE" ] || { echo "Thiếu $ENV_FILE. Tạo từ deploy/prod.env.example rồi chạy lại." >&2; exit 2; }
chmod 600 "$ENV_FILE"

log "Giải nén bản $ID"
mkdir -p "$RELEASES/$ID"
tar -xzf "$BASE/incoming/$ID.tar.gz" -C "$RELEASES/$ID"

PREV=""
[ -L "$BASE/current" ] && PREV="$(basename "$(readlink "$BASE/current")")"

# Sao lưu CSDL nếu bản cũ đang chạy (không chặn việc phát hành nếu chưa có gì để sao lưu).
if [ -n "$PREV" ] && docker compose -p parkflow-thi ps --status running --services 2>/dev/null | grep -qx db; then
  log "Sao lưu CSDL trước khi thay bản"
  docker compose -p parkflow-thi exec -T db pg_dump -U parkflow parkflow | gzip > "$BASE/backups/$ID.sql.gz"
fi

log "Dựng ảnh (bản cũ vẫn đang chạy)"
compose "$ID" build

log "Thay bản"
if ! compose "$ID" up -d --remove-orphans; then
  log "Không khởi động được bản mới"; ROLLBACK=1
fi

healthy() {
  for _ in $(seq 1 60); do
    state="$(docker inspect -f '{{.State.Health.Status}}' parkflow-thi-backend-1 2>/dev/null || echo none)"
    [ "$state" = "healthy" ] && return 0
    sleep 5
  done
  return 1
}

if [ "${ROLLBACK:-0}" = 1 ] || ! healthy; then
  log "Bản $ID không khỏe."
  if [ -n "$PREV" ] && [ -d "$RELEASES/$PREV" ]; then
    log "Quay về bản $PREV"
    compose "$PREV" up -d --remove-orphans
  fi
  docker compose -p parkflow-thi logs --tail 40 backend >&2 || true
  exit 1
fi

ln -sfn "$RELEASES/$ID" "$BASE/current"
log "Bản $ID đang chạy khỏe."

# Dọn các bản cũ, giữ KEEP bản mới nhất.
ls -1dt "$RELEASES"/*/ 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -rf
rm -f "$BASE/incoming/$ID.tar.gz"
