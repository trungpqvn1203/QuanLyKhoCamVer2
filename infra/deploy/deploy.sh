#!/usr/bin/env bash
# Deploy một phiên bản lên server. Chạy TRÊN SERVER: workflow CD (.github/workflows/cd.yml) chép file này lên
# rồi gọi qua SSH. Chạy tay cũng được nếu có token đọc được repo + GHCR.
#
#   printf '%s\n' "$TOKEN" | bash deploy.sh <thư mục app> <mã commit> <owner/repo> <user GitHub>
#
# TOKEN đọc từ stdin (không truyền qua tham số → không lộ trong `ps`). Workflow dùng GITHUB_TOKEN của job deploy:
# vừa đọc repo private, vừa pull image GHCR, và tự hết hạn khi job kết thúc → server không giữ bí mật lâu dài nào.
#
# Thứ tự cố định: lấy code → backup DB → pull image → up → kiểm tra. Bước nào lỗi thì dừng (set -e),
# không bao giờ chạy bản mới khi chưa backup được.
set -euo pipefail

if [ $# -ne 4 ]; then
  echo "Dùng: printf '%s\\n' \"\$TOKEN\" | bash deploy.sh <app_dir> <commit_sha> <owner/repo> <github_user>" >&2
  exit 2
fi

APP_DIR=$1
SHA=$2
REPO=$3
GH_USER=$4
BACKUP_DIR="$HOME/backups"
IFS= read -r TOKEN || true
if [ -z "${TOKEN:-}" ]; then
  echo "Thiếu token ở stdin" >&2
  exit 2
fi

log() { printf '\n==> %s\n' "$*"; }
dc() { docker compose -f infra/docker/docker-compose.prod.yml --env-file .env.prod "$@"; }

cd "$APP_DIR"
[ -f .env.prod ] || { echo "Không thấy $APP_DIR/.env.prod" >&2; exit 1; }

# Dù thành công hay lỗi, xóa token GHCR khỏi ~/.docker/config.json
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT

log "1/6 Lấy code commit ${SHA:0:7} (compose + script phải cùng phiên bản với image)"
# Header Authorization truyền qua biến môi trường GIT_CONFIG_* → không ghi token vào .git/config, không hiện trong `ps`
AUTH=$(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)
GIT_TERMINAL_PROMPT=0 GIT_CONFIG_COUNT=1 \
  GIT_CONFIG_KEY_0="http.https://github.com/.extraheader" \
  GIT_CONFIG_VALUE_0="AUTHORIZATION: basic $AUTH" \
  git fetch --quiet "https://github.com/$REPO.git" main
# --ff-only: server có sửa tay làm lệch lịch sử thì dừng, không âm thầm ghi đè
git merge --ff-only --quiet "$SHA"
git log -1 --oneline

log "2/6 Đăng nhập GHCR"
printf '%s' "$TOKEN" | docker login ghcr.io -u "$GH_USER" --password-stdin >/dev/null

log "3/6 Backup DB trước khi có migration mới"
mkdir -p "$BACKUP_DIR"
if [ -n "$(dc ps -q db)" ]; then
  FILE="$BACKUP_DIR/pre-deploy-$(date +%Y%m%d-%H%M%S)-${SHA:0:7}.sql.gz"
  # Nháy đơn: $POSTGRES_USER/$POSTGRES_DB được đọc BÊN TRONG container db
  if ! dc exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$FILE"; then
    rm -f "$FILE"
    echo "Backup thất bại → dừng, KHÔNG deploy" >&2
    exit 1
  fi
  ls -lh "$FILE"
  find "$BACKUP_DIR" -name 'pre-deploy-*.sql.gz' -mtime +30 -delete
else
  echo "Container db chưa chạy (lần deploy đầu tiên) → không có gì để backup"
fi

log "4/6 Tải image ${SHA:0:7} từ GHCR (server không build nữa)"
IMAGE_TAG="$SHA" dc pull backend web

# Ghi lại phiên bản đang chạy → lần sau gõ tay `dc up -d` vẫn chạy đúng bản này, không quay về tag mặc định
if grep -q '^IMAGE_TAG=' .env.prod; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$SHA/" .env.prod
else
  printf '\nIMAGE_TAG=%s\n' "$SHA" >> .env.prod
fi

log "5/6 Khởi động: db → backend (Flyway chạy migration) → web"
# --wait: chờ mọi container running/healthy; backend unhealthy → lệnh trả lỗi
if ! dc up -d --no-build --wait --wait-timeout 300; then
  echo "Khởi động thất bại. Trạng thái và log backend:" >&2
  dc ps
  dc logs --tail 80 backend
  exit 1
fi

log "6/6 Kiểm tra đi qua Nginx → backend → DB"
dc exec -T web wget -qO- http://localhost/api/health
echo
dc ps

# Image cũ không còn container nào dùng → xóa cho đỡ đầy đĩa. Cần rollback thì pull lại tag cũ từ GHCR
docker image prune -af >/dev/null
log "Deploy ${SHA:0:7} thành công"
