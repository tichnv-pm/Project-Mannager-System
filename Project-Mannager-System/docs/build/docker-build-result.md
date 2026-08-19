# Docker Build Result — Kết quả kiểm chứng Docker Compose (Prompt 24)

> Nguồn: `docs/build/docker-local-guide.md`, `docs/release/01-test-plan.md`.
> Ngày kiểm chứng: 2026-08-03. Máy: Windows, Docker Desktop 29.6.2 (daemon), Compose v5.3.1.

## 1. Kết quả build

| Hạng mục | Kết quả |
|---|---|
| `docker compose up -d --build` | ✅ EXIT 0 — 2 image build thành công (`pmdaily-backend`, `pmdaily-frontend`) |
| Build backend | ✅ Multi-stage: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre` (cài `wget` cho healthcheck) |
| Build frontend | ✅ Multi-stage: `node:24-alpine` (`npm ci` + `npm run build`) → `nginx:alpine` |
| Nginx config | ✅ `frontend/nginx.conf`: serve SPA + proxy `/api/` → `backend:8080` |

## 2. Trạng thái container

```
NAME               IMAGE                COMMAND                  SERVICE    STATUS                    PORTS
pmdaily-backend    pmdaily-backend      "java -jar app.jar"      backend    Up (healthy)              0.0.0.0:8080->8080/tcp
pmdaily-frontend   pmdaily-frontend     "/docker-entrypoint.…"   frontend   Up                        0.0.0.0:4200->80/tcp
pmdaily-postgres   postgres:16-alpine   "docker-entrypoint.s…"   postgres   Up (healthy)              0.0.0.0:5432->5432/tcp
```

- Healthcheck backend: `wget http://localhost:8080/actuator/health` (trong container) — **Healthy**.
- Healthcheck postgres: `pg_isready` — **Healthy**.

## 3. Kiểm chứng checklist thiết kế (design 07 mục 11)

| # | Hạng mục | Kết quả |
|---|---|---|
| 1 | `docker compose up -d --build` chạy trên Windows, 3 container healthy | ✅ PASS |
| 2 | Backend health 200; Swagger mở được; DB có migration V1 + V2 | ✅ PASS |
| 3 | Frontend mở được, đăng nhập được bằng tài khoản demo seed | ✅ PASS |
| 4 | Proxy `/api` hoạt động (login qua nginx thành công) | ✅ PASS |
| 5 | `docker compose down -v` rồi up lại → schema tự tạo lại (không lỗi migration) | ✅ PASS |

## 4. Kết quả kiểm chứng chi tiết

### 4.1 Health & Swagger

```powershell
GET http://localhost:8080/actuator/health  →  HTTP 200  {"status":"UP"}
GET http://localhost:8080/swagger-ui/index.html → HTTP 200
GET http://localhost:8080/v3/api-docs → HTTP 200 (PM Daily Work Management API)
```

### 4.2 Migration (sau `down -v` + up lại)

```
 version | description     | success
---------+-----------------+---------
 1       | init schema     | t
 2       | seed local data | t
```

- Schema + seed được tạo lại sạch, không lỗi Flyway khi reset volume.

### 4.3 Smoke test script `scripts/smoke-test.ps1` — **14/14 PASS**

```
GET / (SPA)                    PASS
GET /actuator/health           PASS
POST /auth/login sai pass      PASS (401)
POST /auth/login admin         PASS (ADMIN, accessToken)
GET /auth/me                   PASS
GET /dashboard/summary         PASS
GET /users?size=2              PASS
GET /roles                     PASS
GET /projects                  PASS
GET /meetings                  PASS
GET /reports/project-progress  PASS
GET /audit-logs?size=2         PASS
GET /notifications/unread-count PASS
GET /v3/api-docs               PASS
```

## 5. Ghi chú phát sinh

1. **Docker daemon không tự khởi động** khi bật máy → `start-local.ps1` tự khởi động Docker Desktop (chờ tối đa 150s) nếu daemon chưa sẵn sàng.
2. **PowerShell 5.1 trả `Content` dạng byte[]** cho JSON → script dùng `Get-ContentText` để decode UTF-8 (đã sửa, xác nhận PASS).
3. **Ports trống trước khi chạy**: kiểm tra `Get-NetTCPConnection -LocalPort 5432,8080,4200` không có process giữ — không xung đột.
4. Thời gian build lần đầu ~3–5 phút (download base image + dependencies); build lại nhanh hơn nhờ layer cache.

## 6. Kết luận

Docker Compose hoạt động đúng thiết kế trên Windows: full stack khởi động, healthcheck xanh, migration tự tạo, proxy `/api` hoạt động, smoke test 14/14 PASS, reset volume không lỗi. Sẵn sàng cho vận hành local và triển khai theo `docs/release/03-release-notes.md`.
