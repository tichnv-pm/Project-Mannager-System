#!/usr/bin/env bash
set -euo pipefail

# Backup PostgreSQL thủ công (GĐ4 cải tiến — chiến lược backup/restore, docs/design/07 §13).
# Dùng chung biến môi trường DB_URL/DB_USERNAME/DB_PASSWORD như backend (application.yml) —
# không cần set riêng, chỉ cần `source .env` hoặc export trước khi chạy.
#
# LƯU Ý: với production hiện tại (Neon Postgres), đây KHÔNG phải lớp bảo vệ chính — Neon tự có
# Point-in-Time Recovery (PITR) theo plan đang dùng. Script này tạo bản sao thủ công bổ sung
# (trước khi chạy migration rủi ro, lưu trữ ngoài Neon, hoặc xuất dữ liệu để chuyển host).

: "${DB_URL:?DB_URL chưa được set (dạng jdbc:postgresql://host:port/dbname?sslmode=require)}"
: "${DB_USERNAME:?DB_USERNAME chưa được set}"
: "${DB_PASSWORD:?DB_PASSWORD chưa được set}"

# jdbc:postgresql://host:port/dbname?sslmode=require -> PGHOST/PGPORT/PGDATABASE/PGSSLMODE
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

out_dir="${BACKUP_DIR:-./backups}"
mkdir -p "$out_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
out_file="$out_dir/pmdaily-${PGDATABASE}-${timestamp}.dump"

echo "Backing up ${PGDATABASE}@${PGHOST}:${PGPORT} -> ${out_file}"
pg_dump --format=custom --no-owner --no-privileges --file="$out_file"
echo "Xong: ${out_file} ($(du -h "$out_file" | cut -f1))"
echo "LƯU Ý: không commit file .dump này vào git — chuyển sang nơi lưu trữ an toàn riêng (private storage/encrypted disk)."
