#!/usr/bin/env bash
# Phát hành lên máy chủ bằng MỘT lệnh, chạy từ máy của bạn (gốc repo):
#   PF_HOST=ubuntu@<địa-chỉ> PF_SSH_KEY=<đường-dẫn-khóa.pem> bash deploy/release.sh
# Biến tùy chọn: PF_DIR (thư mục trên máy chủ, mặc định parkflow-thi), PF_SSH_PORT (mặc định 22).
# Chỉ phát hành mã ĐÃ COMMIT (git archive), không đẩy tệp chưa commit hay bí mật. Cấu hình bí mật nằm sẵn trên máy chủ
# ở <PF_DIR>/shared/.env.prod (tạo một lần từ deploy/prod.env.example, không đưa vào repo).
set -euo pipefail

: "${PF_HOST:?Cần đặt PF_HOST, ví dụ ubuntu@1.2.3.4}"
: "${PF_SSH_KEY:?Cần đặt PF_SSH_KEY là đường dẫn khóa SSH}"
DIR="${PF_DIR:-parkflow-thi}"
PORT="${PF_SSH_PORT:-22}"
SSH=(ssh -i "$PF_SSH_KEY" -p "$PORT" -o StrictHostKeyChecking=accept-new "$PF_HOST")
SCP=(scp -i "$PF_SSH_KEY" -P "$PORT" -o StrictHostKeyChecking=accept-new)

cd "$(git rev-parse --show-toplevel)"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "Có thay đổi chưa commit. Commit hoặc cất đi trước khi phát hành." >&2
  exit 1
fi

ID="$(date +%Y%m%d-%H%M%S)-$(git rev-parse --short HEAD)"
PACK="$(mktemp -t pf-release-XXXXXX).tar.gz"
trap 'rm -f "$PACK"' EXIT

echo "Đóng gói bản $ID"
git archive --format=tar.gz -o "$PACK" HEAD

echo "Đẩy lên máy chủ"
"${SSH[@]}" "mkdir -p ~/$DIR/incoming"
"${SCP[@]}" "$PACK" "$PF_HOST:$DIR/incoming/$ID.tar.gz"
"${SCP[@]}" deploy/remote-release.sh "$PF_HOST:$DIR/remote-release.sh"

echo "Phát hành"
"${SSH[@]}" "bash ~/$DIR/remote-release.sh \$HOME/$DIR $ID"
echo "Xong. Bản $ID đang chạy."
