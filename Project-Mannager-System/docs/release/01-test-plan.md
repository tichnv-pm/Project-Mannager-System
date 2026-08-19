# PM Daily — Kế hoạch & Kết quả kiểm thử (v1.0.0)

> Nguồn: Prompt 23 (Release), `docs/00-project-overview.md` mục 11, `docs/06-acceptance-criteria.md`.
> Cập nhật: 2026-08-03.

## 1. Phạm vi

Kiểm thử toàn diện trước khi phát hành v1.0.0 cho PM Daily Work Management:

- **Đơn vị (unit)**: Service, Utility, Mapper phía Backend.
- **Tích hợp (integration)**: API REST toàn bộ 15 module nghiệp vụ (MockMvc + H2).
- **Frontend**: Test unit cho service tầng dữ liệu (Vitest + HttpTestingController) + build production.
- **Smoke test E2E**: Toàn bộ stack chạy qua Docker Compose (PostgreSQL + Backend + Nginx/Frontend), kiểm chứng bằng HTTP thực tế.

## 2. Môi trường kiểm thử

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Hệ điều hành | Windows (win32) | Local dev |
| JDK | OpenJDK 21.0.12 LTS | Backend |
| Maven | 3.9.16 | Build backend |
| Node.js | v24.18.1 | Frontend |
| Angular CLI | 22.x (zoneless, standalone, Vitest) | Frontend |
| Docker Desktop | 29.6.2 (engine 29.6.2), Compose v5.3.1 | Runtime chính thức |
| PostgreSQL | 16-alpine (container) | DB production-like |

Cổng sử dụng: `5432` (PostgreSQL), `8080` (Backend), `4200` (Nginx → Frontend).

## 3. Chiến lược kiểm thử

- Backend: unit test (Mockito, không cần DB) + integration test (Spring Boot Test + H2 + MockMvc, profile `test`, seed riêng).
- Frontend: Vitest qua `@angular/build:unit-test` (lệnh `npm test`, không watch), service tests dùng `provideHttpClientTesting`.
- Build: `mvn clean package` (backend) và `npm run build` (frontend — kiểm tra type-check + AOT + budget).
- Smoke E2E: `docker compose up -d --build` → gọi HTTP qua Nginx proxy (port 4200) như người dùng thật.

## 4. Kết quả Backend — `mvn clean test`

**Tổng: 216 tests, 0 failures, 0 errors** (2026-08-03).

| Nhóm kiểm thử | Số test | Kết quả |
|---|---|---|
| `AuthServiceTest` (unit) | 16 | PASS |
| `AuthIntegrationTest` | 16 | PASS |
| `TaskIntegrationTest` | 47 | PASS |
| `ProjectIntegrationTest` | 28 | PASS |
| `MeetingIntegrationTest` | 30 | PASS |
| `ActionItemIntegrationTest` | 27 | PASS |
| `UserAdminIntegrationTest` *(Prompt 22)* | 16 | PASS |
| `JwtServiceTest` (unit) | 6 | PASS |
| `AuditDataSanitizerTest` (unit) | 5 | PASS |
| `SecuritySmokeIntegrationTest` | 4 | PASS |
| `GlobalExceptionHandlerTest` (unit) | 8 | PASS |
| `ErrorCodeTest` (unit) | 2 | PASS |
| `DashboardIntegrationTest` | 1 | PASS |
| `ReportIntegrationTest` | 1 | PASS |
| `AuditLogIntegrationTest` | 1 | PASS |
| `RiskIntegrationTest` | 2 | PASS |
| `IssueIntegrationTest` | 2 | PASS |
| `MilestoneIntegrationTest` | 1 | PASS |
| `NotificationIntegrationTest` | 2 | PASS |
| `PMDailyApplicationTests` (smoke context) | 1 | PASS |

Phạm vi kiểm thử quan trọng đã phủ:

- Auth: login lock 5 lần/5 phút, refresh rotation + reuse detection, logout idempotent, change/reset password.
- Task: state machine 6 trạng thái, mã tự sinh concurrent, phân quyền theo role, export Excel.
- Meeting/ActionItem: participants, attachments, complete khóa biên bản, convert action item → task.
- UserAdmin: create/update/status với optimistic locking (version), phân quyền `user:view/manage`, `role:manage`, chống self-deactivate, chống gỡ quyền `role:manage` khỏi ADMIN.
- Security: 401/403, inactive user bị chặn, JWT hết hạn, phương thức HTTP không cho phép.

## 5. Kết quả Frontend — `npm test` (Vitest) + `npm run build`

**Vitest: 3 test files, 15 tests PASS** (2026-08-03):

| File | Số test | Nội dung |
|---|---|---|
| `app.spec.ts` | 1 | Smoke khởi tạo core |
| `report.service.spec.ts` | 4 | URL + params: tasks-by-status, overdue (pagination), project-progress (multi projectId), export CSV blob |
| `admin.service.spec.ts` | 10 | Users GET/POST/PUT/PATCH, roles permissions PUT, audit logs GET, catalog permission duy nhất + đủ 32 code seed |

**Build**: `npm run build` — PASS (AOT + type-check). Cảnh báo còn lại (không chặn release):

- `task-list.component.scss` 16.33 kB > budget 16.00 kB (+0.3 kB).
- `task-detail.component.scss` 18.16 kB > budget 16.00 kB (+2.2 kB).
- *Đã biết, cố ý giữ nguyên (style dày cho Kanban/detail); không ảnh hưởng chức năng.*

## 6. Smoke test E2E — Docker Compose

### 6.1 Triển khai

```powershell
docker compose up -d --build
```

Kết quả (2026-08-03):

| Container | Trạng thái | Healthcheck |
|---|---|---|
| `pmdaily-postgres` (postgres:16-alpine) | Up | Healthy (pg_isready) |
| `pmdaily-backend` (pmdaily-backend) | Up | Healthy (GET /actuator/health = `{"status":"UP"}`) |
| `pmdaily-frontend` (nginx) | Up | — |

### 6.2 Kịch bản & kết quả

Kiểm chứng qua Nginx proxy (`http://localhost:4200`) — đúng đường truyền trình duyệt thật.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `GET http://localhost:4200` trả trang SPA | HTTP 200, `text/html` — PASS |
| 2 | `GET /actuator/health` (backend) | `{"status":"UP"}` — PASS |
| 3 | `POST /api/v1/auth/login` sai mật khẩu | HTTP 401, audit ghi `LOGIN_FAILED` — PASS |
| 4 | `POST /api/v1/auth/login` admin/`Admin@123` | HTTP 200, accessToken + refreshToken, roles=`ADMIN`, 32 permissions — PASS |
| 5 | `GET /api/v1/auth/me` (Bearer token) | HTTP 200 — PASS |
| 6 | `GET /api/v1/dashboard/summary` | HTTP 200 — 10 chỉ số (tasksToday=1, overdue=2, inProgress=2, blocked=1, pendingActionItems=2, highRisks=1, openIssues=1…) — PASS |
| 7 | `GET /api/v1/users?size=2` | HTTP 200 — PASS |
| 8 | `GET /api/v1/roles` | HTTP 200 — PASS |
| 9 | `GET /api/v1/reports/tasks-by-status?projectId=…` | HTTP 200 — PASS |
| 10 | `GET /api/v1/reports/project-progress` | HTTP 200 — PASS |
| 11 | `GET /api/v1/audit-logs?size=2` | HTTP 200 — PASS |
| 12 | `GET /api/v1/projects` | HTTP 200 — PASS |
| 13 | `GET /api/v1/meetings` | HTTP 200 — PASS |
| 14 | `GET /api/v1/notifications/unread-count` | HTTP 200 — PASS |
| 15 | Audit log ghi nhận hoạt động đăng nhập (write path) | `LOGIN_SUCCESS` × 4, `LOGIN_FAILED` × 1 — PASS |

**Kết luận smoke test: 15/15 PASS.** Toàn bộ luồng trình duyệt → Nginx → Backend → PostgreSQL hoạt động, bao gồm API mới (users/roles/reports/audit) và write path (audit log).

## 7. Kiểm thử thủ công (khuyến nghị sau deploy)

Chưa có E2E framework (Playwright/Cypress) ở v1 — danh sách kịch bản thủ công trước khi bàn giao:

- [ ] Đăng nhập/đăng xuất, refresh token khi mở lại tab sau 15 phút.
- [ ] Tạo/sửa/xóa dự án → tạo task → đổi trạng thái → complete → xem dashboard cập nhật.
- [ ] Tạo meeting → thêm participant → upload attachment → tạo action item → convert → task xuất hiện trong danh sách.
- [ ] Create user qua Admin UI → user mới login được; vô hiệu hóa → user bị chặn login.
- [ ] Chỉnh quyền role ở Admin UI → quyền mới có hiệu lực ngay (sidebar/route guard).
- [ ] Report UI: đổi tab, filter dự án/ngày, export CSV mở được đúng nội dung.
- [ ] Nhật ký audit hiển thị hành động của phiên hiện tại.

## 8. Chặn release (đã xử lý trong v1.0.0)

| Vấn đề | Cách xử lý |
|---|---|
| `UserResponse` không trả `version` nhưng update/status yêu cầu version → FE không thể gọi | Thêm `long version` vào `UserResponse` (MapStruct tự map), cập nhật docs 02 |
| `npx vitest run` trực tiếp fail (thiếu Angular setup) | Dùng đúng lệnh chuẩn `npm test` (Angular unit-test builder) — ghi chú trong AGENTS.md |

## 9. Kết luận

Toàn bộ tiêu chí chất lượng trước phát hành đạt: **216 backend tests + 15 FE tests PASS, build cả 2 stack PASS, smoke test E2E 15/15 PASS trên Docker Compose chính thức**. Sẵn sàng chuyển sang `docs/release/02-code-review.md` và `03-release-notes.md`.
