#!/usr/bin/env bash
# Kiểm chứng liêm chính lịch sử commit so với cửa sổ thi.
# Cách dùng: scripts/kiem-tra-cua-so-thi.sh "<bắt đầu ISO>" "<kết thúc ISO>" [nhánh, mặc định origin/main]
# Quy tắc kiểm:
#  1. Mọi commit sau commit khung (tag `khung-trang`) có giờ TÁC GIẢ trong [bắt đầu, kết thúc].
#  2. Giờ tác giả không lệch giờ commit quá 15 phút (lệch nhiều là dấu hiệu viết lại lịch sử hoặc sửa dấu thời gian);
#     commit tạo bởi "Rebase and merge" trên GitHub có giờ commit muộn hơn giờ tác giả, sẽ được liệt kê để người kiểm tự đánh giá.
#  3. Không có commit chưa có email tác giả hoặc tác giả là tài khoản không thuộc nhóm (in danh sách để đối chiếu).
# Thoát mã 0 khi không có vi phạm quy tắc 1; cảnh báo quy tắc 2 và 3 không làm lỗi.
set -uo pipefail
START="${1:?Thiếu giờ bắt đầu}"
END="${2:?Thiếu giờ kết thúc}"
REF="${3:-origin/main}"
cd "$(git rev-parse --show-toplevel)"
git fetch -q origin 2>/dev/null || true

S="$(date -d "$START" +%s)"; E="$(date -d "$END" +%s)"
BASE="$(git rev-list -n 1 khung-trang 2>/dev/null || true)"
if [ -z "$BASE" ]; then echo "!! Không thấy tag khung-trang (commit khung trắng)."; exit 2; fi

violations=0; skew=0; total=0
while IFS='|' read -r sha at ct name email subject; do
  total=$((total+1))
  if [ "$at" -lt "$S" ] || [ "$at" -gt "$E" ]; then
    echo "VI PHẠM: $sha $name '$subject' có giờ tác giả $(date -d "@$at" '+%F %T') ngoài cửa sổ."
    violations=$((violations+1))
  fi
  diff=$(( ct > at ? ct - at : at - ct ))
  if [ "$diff" -gt 900 ]; then
    echo "CẢNH BÁO: $sha $name '$subject' giờ commit lệch giờ tác giả $((diff/60)) phút."
    skew=$((skew+1))
  fi
done < <(git log "$REF" --no-merges "$BASE..$REF" --format='%H|%at|%ct|%an|%ae|%s')

echo
echo "== Tác giả (để đối chiếu với danh sách nhóm) =="
git log "$REF" --no-merges "$BASE..$REF" --format='%an <%ae>' | sort | uniq -c | sort -rn
echo
echo "Đã kiểm $total commit sau khung trắng: $violations vi phạm cửa sổ, $skew cảnh báo lệch giờ."
[ "$violations" -eq 0 ]
