# Docker Local Guide — Hướng dẫn vận hành bằng Docker Compose (Prompt 24)

> Nguồn: `docs/design/07-deployment-design.md`, `docs/build/environment-check.md`.
> Cập nhật: 2026-08-03 — đã kiểm chứng trên máy Windows + Docker Desktop 29.6.2 (chi tiết: `docker-build-result.md`).

## 1. Tổng quan

PM Daily chạy full-stack bằng Docker Compose gồm 3 service:

| Service | Image | Container | Port |
|---|---|---|---|
| `postgres` | postgres:16-alpine | pmdaily-postgres | 5432 |
| `backend` | pmdaily-backend (multi-stage build) | pmdaily-backend | 8080 |
| `frontend` | pmdaily-frontend (Nginx) | pmdaily-frontend | 4200 |

Luồng truy cập: trình duyệt → `http://localhost:4200` (Nginx) → proxy `/api/*` → backend `:8080` → PostgreSQL.

## 2. Yêu cầu

- Windows 10/11, Docker Desktop (WSL2 backend) — daemon phải đang chạy.
- File `.env` ở thư mục gốc (xem `.env.example`).

## 3. Các lệnh chính

```powershell
# Build + khởi động toàn bộ stack
docker compose up -d --build

# Xem trạng thái
docker compose ps

# Log (theo dõi thời gian thực)
docker compose logs -f
docker compose logs -f backend      # chỉ backend
docker compose logs -f postgres

# Dừng (GIỮ dữ liệu trong volume pgdata)
docker compose down

# Dừng + XÓA dữ liệu (volume) — migration V1/V2 sẽ chạy lại khi up
docker compose down -v

# Xóa toàn bộ image/volume rác (cẩn thận)
docker system prune
```

> Không cần nhớ các lệnh trên: dùng script `scripts/*.ps1` (mục 4).

## 4. Scripts tiện ích (khuyến nghị)

| Script | Chức năng |
|---|---|
| `scripts/start-local.ps1` | Kiểm tra daemon (tự khởi động Docker Desktop nếu cần), build + up, chờ backend healthy |
| `scripts/stop-local.ps1` | `docker compose down` — giữ dữ liệu |
| `scripts/reset-local.ps1` | `down -v` + up lại — xóa dữ liệu, yêu cầu gõ `RESET` để xác nhận |
| `scripts/smoke-test.ps1` | Smoke test E2E 14 bước (health, login sai/đúng, me, dashboard, users, roles, projects, meetings, reports, audit, notifications, swagger) |

Cách dùng (từ thư mục gốc):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
powershell -ExecutionPolicy Bypass -File scripts/stop-local.ps1
powershell -ExecutionPolicy Bypass -File scripts/reset-local.ps1
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```

## 5. Cấu hình qua `.env`

Sao chép `.env.example` → `.env` (script `start-local.ps1` tự làm nếu thiếu, nhưng **bắt buộc sửa `JWT_SECRET`**).

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `pmdaily` | Tài khoản DB container |
| `DB_URL` | `jdbc:postgresql://postgres:5432/pmdaily` | Container nội bộ — không đổi host |
| `DB_USERNAME` / `DB_PASSWORD` | `pmdaily` | Phải khớp POSTGRES_USER/PASSWORD |
| `JWT_SECRET` | — | **Bắt buộc**, ≥ 32 ký tự |
| `JWT_ACCESS_EXPIRATION` | `900000` (15 phút) | ms |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 ngày) | ms |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Phân tách `,` nếu nhiều origin |
| `SPRING_PROFILES_ACTIVE` | `local` | `local` = có seed dữ liệu demo |

> Bí mật không commit: `.env` nằm trong `.gitignore`.

## 6. Migration & dữ liệu

- Flyway tự chạy lúc backend khởi động: `V1__init_schema.sql` (schema), `V2__seed_local_data.sql` (seed demo, chỉ profile `local`).
- Kiểm tra trạng thái migration:

```powershell
docker exec pmdaily-postgres psql -U pmdaily -d pmdaily -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

- Muốn làm lại dữ liệu từ đầu: `scripts/reset-local.ps1` (hoặc `docker compose down -v` rồi up).
- Quy tắc: không sửa migration đã chạy; thay đổi schema tạo `V3__...` mới.

## 7. Kiểm tra sau khi khởi động

```powershell
# 1. Trạng thái container
docker compose ps                       # 3 container Up, backend/postgres (healthy)

# 2. Health backend
Invoke-WebRequest http://localhost:8080/actuator/health   # {"status":"UP"}

# 3. Swagger
#    http://localhost:8080/swagger-ui/index.html

# 4. Frontend + login demo
#    http://localhost:4200  →  admin / Admin@123

# 5. Smoke test tự động
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1   # 14/14 PASS
```

## 8. Xử lý sự cố Docker

| Sự cố | Nguyên nhân | Xử lý |
|---|---|---|
| `failed to connect ... dockerDesktopLinuxEngine` | Daemon chưa chạy | `Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'`, chờ `docker version` hiện Server |
| Port 5432/8080/4200 bị chiếm | Dịch vụ khác (Postgres local, Angular dev, v.v.) | `Get-NetTCPConnection -LocalPort 5432,8080,4200` tìm PID; dừng dịch vụ hoặc đổi port trong `.env`/compose |
| `JWT_SECRET is required` | Thiếu biến .env | Sửa `.env` theo `.env.example` |
| Backend không healthy, log `Connection refused` | Postgres chưa sẵn sàng | Đã có `depends_on: condition: service_healthy`; đợi thêm hoặc `docker compose logs backend` |
| Login sai mật khẩu dù đúng | Profile không phải `local` (seed chưa chạy) | Kiểm tra `SPRING_PROFILES_ACTIVE=local` trong `.env` |
| Image build lỗi `npm ci` / `mvn` | Mạng/registry | Chạy lại `docker compose build --no-cache backend frontend` |
| Container backend restart liên tục | Lỗi migration hoặc DB_URL sai | `docker compose logs backend`; sửa `.env`; `scripts/reset-local.ps1` |
| RAM ổn định cao | Nhiều image/container | `docker system prune` (không ảnh hưởng volume pgdata) |

## 9. Tài liệu liên quan

- Kết quả kiểm chứng thực tế: `docs/build/docker-build-result.md` (Prompt 24).
- Chạy không Docker (JDK/Maven/Node): `docs/build/windows-local-runbook.md` (Prompt 25).
- Kiểm tra môi trường: `docs/build/environment-check.md`.
- Release & deploy: `docs/release/03-release-notes.md`.
