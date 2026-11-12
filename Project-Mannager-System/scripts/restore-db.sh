#!/usr/bin/env bash
set -euo pipefail

# Restore PostgreSQL từ file backup tạo bởi scripts/backup-db.sh (GĐ4 cải tiến, docs/design/07 §13).
# GHI ĐÈ dữ liệu trong database đích — chỉ chạy khi chắc chắn DB_URL đang trỏ đúng nơi muốn restore.
# Dùng cùng biến môi trường DB_URL/DB_USERNAME/DB_PASSWORD như backup-db.sh.

if [[ $# -ne 1 ]]; then
  echo "Dùng: $0 <đường-dẫn-file-dump>" >&2
  exit 1
fi
dump_file="$1"
[[ -f "$dump_file" ]] || { echo "Không tìm thấy file: $dump_file" >&2; exit 1; }

: "${DB_URL:?DB_URL chưa được set (dạng jdbc:postgresql://host:port/dbname?sslmode=require)}"
: "${DB_USERNAME:?DB_USERNAME chưa được set}"
: "${DB_PASSWORD:?DB_PASSWORD chưa được set}"

uri="${DB_URL#jdbc:}"
host_port_db="${uri#postgresql://}"
host_port="${host_port_db%%/*}"
db_and_query="${host_port_db#*/}"
dbname="${db_and_query%%\?*}"
PGHOST="${host_port%%:*}"
PGPORT="${host_port##*:}"
sslmode="prefer"
if [[ "$db_and_query" == *"sslmode="* ]]; then
  sslmode="${db_and_query#*sslmode=}"
  sslmode="${sslmode%%&*}"
fi

export PGHOST PGPORT
export PGDATABASE="$dbname"
export PGUSER="$DB_USERNAME"
export PGPASSWORD="$DB_PASSWORD"
export PGSSLMODE="$sslmode"

echo "SẮP RESTORE vào ${PGDATABASE}@${PGHOST}:${PGPORT} từ ${dump_file}"
echo "Dữ liệu hiện có trong database đích có thể bị GHI ĐÈ. Gõ đúng tên database để xác nhận:"
read -r confirm
if [[ "$confirm" != "$PGDATABASE" ]]; then
  echo "Không khớp, hủy." >&2
  exit 1
fi

pg_restore --clean --if-exists --no-owner --no-privileges --dbname="$PGDATABASE" "$dump_file"
echo "Restore hoàn tất vào ${PGDATABASE}."
