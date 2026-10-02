#!/usr/bin/env bash
# Báo cáo nhịp commit của cả đội kể từ lúc bắt đầu thi.
# Cách dùng: scripts/bao-cao-commit.sh "2026-10-03T06:30:00+07:00" [nhánh, mặc định origin/main]
# In: số commit theo người, số commit theo giờ (từ lúc bắt đầu), và các giờ có người chưa commit.
set -euo pipefail
START="${1:?Thiếu giờ bắt đầu, ví dụ: scripts/bao-cao-commit.sh \"2026-10-03T06:30:00+07:00\"}"
REF="${2:-origin/main}"
cd "$(git rev-parse --show-toplevel)"
git fetch -q origin 2>/dev/null || true

echo "== Số commit theo người (từ $START, nhánh $REF, không tính commit merge) =="
git log "$REF" --no-merges --since="$START" --format='%an' | sort | uniq -c | sort -rn || true

echo
echo "== Số commit theo giờ kể từ lúc bắt đầu =="
START_EPOCH="$(date -d "$START" +%s)"
git log "$REF" --no-merges --since="$START" --format='%at %an' | while read -r at name; do
  hour=$(( (at - START_EPOCH) / 3600 ))
  printf 'T+%d\t%s\n' "$hour" "$name"
done | sort | uniq -c | sort -k2,2 -k1,1nr || true

echo
TOTAL="$(git log "$REF" --no-merges --since="$START" --format='%H' | wc -l | tr -d ' ')"
echo "Tổng: $TOTAL commit. Mục tiêu khoảng 80 đến 100 commit, mỗi người 20% đến 30%, ít nhất 2 commit mỗi giờ."
