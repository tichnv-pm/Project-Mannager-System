# PM Daily Work Management — Release Notes v1.0.0

> Nguồn: Prompt 24 (Release), `docs/00-project-overview.md`, `docs/design/07-deployment-design.md`.
> Ngày phát hành: **2026-08-04** (v1.0.0 — PHÁT HÀNH HOÀN CHỈNH).

## 1. Tổng quan

PM Daily Work Management v1.0.0 — Ứng dụng quản lý công việc hằng ngày cho quản lý dự án phần mềm (nội bộ, tiếng Việt).

## 2. Tính năng

| Module | Tính năng chính |
|---|---|
| Auth | Login (lock 5 lần/5 phút), refresh token rotation, logout, đổi mật khẩu, ADMIN reset mật khẩu |
| Dashboard | 10 chỉ số summary, cảnh báo quá hạn/blocker/risk cao, biểu đồ status/priority, tiến độ dự án |
| Project | CRUD + soft delete, quản lý thành viên (PM/ADMIN), search/filter |
| Task | Kanban 6 cột, 25 endpoints (CRUD, search lọc, state machine, tags/collaborators/watchers, comments, attachments, history, export Excel, mã tự sinh `PRJXXX-TASK-000001`) |
| Meeting | CRUD, quick filter, complete khóa biên bản, participants, attachments, action items (CRUD + convert → task) |
| Risk & Issue | CRUD, ma trận level/severity, convert risk OCCURRED → issue |
| Milestone | CRUD, COMPLETED bắt buộc progress 100% |
| Notification | In-app + unread badge realtime, cron job task sắp hạn/quá hạn |
| Report | 5 báo cáo (status/assignee/overdue/progress/risk-issue), filter dự án + ngày, export CSV |
| Admin | Người dùng (CRUD + vô hiệu hóa), vai trò & phân quyền, nhật ký audit |

## 3. Kiến trúc & công nghệ

- **Backend**: Java 21, Spring Boot 3.x, Spring Security + JWT, Spring Data JPA, Flyway, MapStruct, Bean Validation, springdoc, Maven. Modular Monolith 15 module, package `com.example.pmdaily`.
- **Frontend**: Angular 22 (standalone, zoneless, signals), Angular Material, Reactive Forms, Vitest, SCSS.
- **DB**: PostgreSQL 16, PK UUID, `snake_case`, `timestamptz` UTC.
- **Deploy**: Docker Compose (postgres + backend + frontend-Nginx reverse proxy `/api/`).

## 4. Chất lượng phát hành

- Backend: **222 tests PASS** (0 failure, 0 skipped) — `mvn clean test`.
- Frontend: **19 tests PASS** — `npm test`; build PASS — `npm run build`.
- Smoke E2E trên Docker Compose chính thức: **15/15 PASS** (chi tiết `docs/release/01-test-plan.md`).
- Review mã nguồn: không vấn đề chặn; 7 ghi nhận v1.1 (`docs/release/02-code-review.md`).

## 5. Hướng dẫn cài đặt & chạy

### 5.1 Yêu cầu

- Docker Desktop (daemon chạy) — khuyến nghị; hoặc JDK 21 + Node ≥ 22 + PostgreSQL 16 chạy thủ công.

### 5.2 Chạy bằng Docker Compose (chuẩn)

```powershell
# 1. Tạo .env từ mẫu (nếu chưa có)
Copy-Item .env.example .env
#    → sửa JWT_SECRET (tối thiểu 32 ký tự), POSTGRES_PASSWORD, DB_PASSWORD

# 2. Build & khởi động
docker compose up -d --build

# 3. Kiểm tra
docker compose ps                 # cả 3 container Up
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# 4. Truy cập
#    Frontend: http://localhost:4200  (proxy /api → backend:8080)

# 5. Dừng
docker compose down               # giữ dữ liệu (volume pgdata)
docker compose down -v            # xóa luôn dữ liệu
```

### 5.3 Tài khoản demo (chỉ local)

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `Admin@123` | ADMIN hệ thống |
| `pm.minh` | `Pm@12345` | PROJECT_MANAGER |
| `member1` | `Member@123` | PROJECT_MEMBER |
| `member2` / `member3` | `Member@123` | PROJECT_MEMBER |

> Mật khẩu demo KHÔNG dùng cho production (NFR-DATA-02).

### 5.4 Chạy thủ công (không Docker)

```powershell
# Backend (JDK 21 + Maven 3.9+)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
# Frontend (Node ≥ 22)
cd frontend
npm install
npm start          # http://localhost:4200 (proxy → localhost:8080)
```

## 6. Cấu hình môi trường

| Biến | Mặc định | Bắt buộc | Mô tả |
|---|---|---|---|
| `POSTGRES_DB/USER/PASSWORD` | `pmdaily` | — | DB container |
| `DB_URL` | `jdbc:postgresql://postgres:5432/pmdaily` | — | Chuỗi kết nối backend |
| `DB_USERNAME` / `DB_PASSWORD` | `pmdaily` | — | Tài khoản DB của backend |
| `JWT_SECRET` | — | ✔ | Khóa JWT ≥ 32 ký tự |
| `JWT_ACCESS_EXPIRATION` | `900000` (15 phút) | — | ms |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 ngày) | — | ms |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | — | Danh sách origin |
| `SPRING_PROFILES_ACTIVE` | `local` | — | `local` có seed demo |

## 7. Runbook — Xử lý sự cố

| Sự cố | Nguyên nhân | Xử lý |
|---|---|---|
| `docker compose up` lỗi port 5432/8080/4200 | Port bị chiếm (IDE, service khác) | `Get-NetTCPConnection -LocalPort 5432,8080,4200` tìm process; đổi port trong compose hoặc dừng process |
| Backend không Healthy, log `Connection refused` | Postgres chưa sẵn sàng | `docker compose logs backend`; đợi healthcheck postgres (có `depends_on: condition: service_healthy`) |
| `JWT_SECRET is required` | Thiếu biến trong `.env` | Sửa `.env` đúng mẫu `.env.example` |
| Login 401 dù đúng mật khẩu | Nhầm profile (seed không chạy ở production profile) | Dùng `SPRING_PROFILES_ACTIVE=local`; kiểm tra account `admin/Admin@123` |
| Dữ liệu seed lặp sau khi build lại | Volume cũ chứa dữ liệu | `docker compose down -v && docker compose up -d --build` |
| Frontend trắng trang, console 404 `/api/...` | Proxy nginx chưa reach backend | `docker compose ps` kiểm tra backend Healthy; xem `docker compose logs frontend` |
| RAM Docker Desktop cao | Nhiều container/image | `docker system prune` (cẩn thận xóa image cần dùng) |

## 8. Backup & Restore (PostgreSQL)

```powershell
# Backup
docker exec pmdaily-postgres pg_dump -U pmdaily pmdaily > backup-2026-08-03.sql
# Restore
Get-Content backup-2026-08-03.sql | docker exec -i pmdaily-postgres psql -U pmdaily pmdaily
```

## 9. Checklist phát hành

- [x] `mvn clean test` — 222 PASS
- [x] `npm test` — 19 PASS
- [x] `npm run build` — PASS
- [x] `docker compose up -d --build` — 3 container Up, backend Healthy
- [x] Smoke test E2E 15/15 — login/me/dashboard/users/roles/reports/audit/notifications
- [x] Không hard-code secret; `.env` không commit
- [x] Tài liệu: test plan, code review, release notes hoàn chỉnh
- [x] Kịch bản thủ công mục 7 — kiểm chứng luồng chính qua smoke E2E 15/15 PASS trên Docker Compose thật

## 10. Backlog v1.1

1. E2E framework (Playwright/Cypress) thay kịch bản thủ công.
2. Giới hạn kích thước export CSV + streaming khi dữ liệu lớn.
3. Tối ưu SCSS (giảm budget warning task list/detail).
4. Cache tab Admin/Report; batch fetch user list khi scale.
5. Hỗ trợ email notification (ngoài phạm vi v1).
