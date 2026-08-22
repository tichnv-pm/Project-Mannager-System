# PM Daily — Kế hoạch & Kết quả kiểm thử (v1.2.0)

> Nguồn: `docs/00-project-overview.md` mục 11, `docs/06-acceptance-criteria.md`, và kết quả thực tế.
> Cập nhật: 2026-08-22 (v1.2.0 — PHÁT HÀNH HOÀN CHỈNH v1.1 & v1.2).

## 1. Phạm vi

Kiểm thử toàn diện trước khi phát hành v1.2.0 cho PM Daily Work Management:

- **Đơn vị (unit)**: Service, Utility, Mapper phía Backend.
- **Tích hợp (integration)**: API REST toàn bộ các module nghiệp vụ của v1.0, v1.1 (Planning) và v1.2 (Agile/Sprints, Wiki, QA, Git, EVM).
- **Frontend**: Test unit cho các service tầng dữ liệu (Vitest + HttpTestingController) + build production.
- **Tự động hóa E2E**: Thiết lập framework Playwright và viết kịch bản test E2E cho các luồng Lập kế hoạch, Agile/Sprints + Git, và QA + Tài chính EVM.
- **Smoke test E2E**: Kiểm chứng toàn bộ stack qua Docker Compose (PostgreSQL + Backend + Nginx/Frontend), kiểm chứng bằng HTTP thực tế.
- **Kiểm chứng hiệu năng (Performance)**: Đo đạc thời gian tính toán lập lịch (Scheduling recalc) và lấy dữ liệu Gantt SVG với quy mô 500 tasks.

## 2. Môi trường kiểm thử

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Hệ điều hành | Windows (win32) | Local dev |
| JDK | OpenJDK 21.0.12 LTS | Backend |
| Maven | 3.9.16 | Build backend |
| Node.js | v24.18.1 | Frontend |
| NPM | 11.16.0 | Quản lý package |
| Angular CLI | 22.x (zoneless, Vitest) | Frontend |
| Playwright | ^1.46.0 | E2E Framework |
| Docker Desktop | 29.6.2 (engine 29.6.2), Compose v5.3.1 | Runtime chính thức |
| PostgreSQL | 16-alpine (container) | DB production-like |

Cổng sử dụng: `5432` (PostgreSQL), `8080` (Backend), `4200` (Nginx → Frontend).

## 3. Chiến lược kiểm thử

- Backend: unit test + integration test (Spring Boot Test + H2 + MockMvc, profile `test`, seed riêng).
- Frontend: Vitest qua `@angular/build:unit-test` (lệnh `npm test`, không watch).
- Build: `mvn clean package` (backend) và `npm run build` (frontend — kiểm tra type-check + SCSS budget).
- E2E Playwright: Chạy qua Playwright test runner trỏ trực tiếp vào `http://localhost:4200` (môi trường Docker Compose).
- Smoke E2E: `docker compose up -d --build` → gọi HTTP qua Nginx proxy (port 4200) như người dùng thật.

## 4. Kết quả Backend — `mvn clean test`

**Tổng: 295 tests, 0 failures, 0 errors** (2026-08-22).

| Nhóm kiểm thử | Số test | Kết quả |
|---|---|---|
| Phân hệ v1.0.0 (Auth, Task, Project, Meeting, v.v.) | 222 | PASS |
| Phân hệ v1.1.0 (Plan, Task/WBS, Dependency, Calendar) | 56 | PASS |
| Phân hệ v1.2.0 (Agile/Sprints, Wiki, QA, Git, EVM Finance) | 17 | PASS |

Phạm vi kiểm thử quan trọng đã phủ ở v1.2.0:
- Sprints: Vòng đời Sprint (FUTURE -> ACTIVE -> CLOSED), phân bổ backlog, chặn ngày kết thúc vượt sprint.
- Wiki: Khởi tạo Wiki theo templates, lưu lịch sử sửa đổi, optimistic locking cho wiki pages.
- QA: Tạo Test Case, Test Run, Test Step. Khi Test Step đánh dấu FAILED, hệ thống tự động sinh Issue loại BUG và gán lại cho Developer.
- Git: Xác thực Webhook bằng HMAC-SHA256, Regex Parser trích xuất mã task từ commit message.
- EVM: Tính toán PV, EV, AC, CV, SV, CPI, SPI hàng ngày dựa trên baseline.

## 5. Kết quả Frontend — `npm test` (Vitest) + `npm run build`

**Vitest: 4 test files, 73 tests PASS** (2026-08-22):

| File | Số test | Nội dung |
|---|---|---|
| `app.spec.ts` | 1 | Smoke khởi tạo core |
| `report.service.spec.ts` | 4 | URL + params: tasks-by-status, overdue, export CSV |
| `admin.service.spec.ts` | 14 | CRUD Users/Roles/Permissions, Audit logs |
| `plan.service.spec.ts` | 54 | CRUD Plan, WBS actions, Scheduling, Resource capacity, Version & Baseline |

**Build**: `npm run build` — PASS (AOT + type-check).
*   **Đặc biệt**: Toàn bộ budget warnings của SCSS đã được dọn sạch nhờ gộp các component SCSS chung lên `styles.scss` (SCSS budget clean).

## 6. Kịch bản E2E Playwright — `npm run e2e`

Đã tích hợp framework Playwright vào frontend và tạo 3 kịch bản kiểm thử E2E:
1.  `planning.spec.ts`: Kiểm tra luồng Lập kế hoạch (WBS, Lịch găng CPM và Gantt SVG).
2.  `agile-git.spec.ts`: Kiểm tra luồng lập Sprint Backlog và tab tích hợp Git trong chi tiết Task.
3.  `qa-finance.spec.ts`: Kiểm tra màn hình QA (Test Cases & Test Runs) và tab báo cáo Tài chính EVM (SPI/CPI chart).

## 7. Kiểm chứng hiệu năng (Performance Test)

Đã chạy script test tải với **500 tasks** liên tiếp trên WBS:
-   **Thời gian thêm 500 tasks**: `17.70` giây.
-   **Đường găng & Lập lịch (Scheduling recalc)**: Chỉ tốn `365.18` ms (Mục tiêu: `< 1.5` giây $\rightarrow$ **Đạt**).
-   **Lấy dữ liệu Gantt SVG**: Chỉ tốn `177.33` ms (Mượt mà trên giao diện $\rightarrow$ **Đạt**).

## 8. Smoke test E2E & Demo Flow

### 8.1 Smoke Test (`scripts/smoke-test.ps1`)
Chạy trực tiếp qua Nginx proxy (`http://localhost:4200`): **14/14 PASS**.

### 8.2 Demo Flow (`scripts/demo-flow-project.ps1`)
Chạy luồng nghiệp vụ tạo dự án CRM mẫu, lập kế hoạch, phân bổ nguồn lực, baseline và sync milestone: **11/11 PASS**.

## 9. Kết luận

Mã nguồn đạt trạng thái chất lượng cao và sẵn sàng đóng gói phát hành: **295 backend tests + 73 frontend tests PASS, Playwright E2E sẵn sàng, build cả 2 stack sạch sẽ, smoke test & demo flow 100% PASS trên Docker**.
