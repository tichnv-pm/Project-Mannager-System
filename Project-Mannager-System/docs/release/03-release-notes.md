# PM Daily Work Management — Release Notes v1.2.0

> Cập nhật: 2026-08-22 (v1.2.0 — PHÁT HÀNH HOÀN CHỈNH v1.1 & v1.2).

## 1. Tổng quan

PM Daily Work Management v1.2.0 nâng cấp toàn diện ứng dụng lên chuẩn quản lý dự án công nghệ toàn trình (End-to-End Software Management) với các cấu phần từ Lập kế hoạch (WBS/CPM), Phân bổ nguồn lực, Lập lịch, đến Thực thi Agile Sprints, Đảm bảo chất lượng (QA), Tích hợp Git và Quản lý tài chính dự án (EVM).

## 2. Tính năng mới & Mở rộng

| Phân hệ | Tính năng chính |
|---|---|
| **v1.1 Project Planning** | - **WBS Editor**: Lập kế hoạch phân rã công việc dạng cây (indent/outdent/move), tự động đánh số `wbs_code`. <br> - **Scheduling Engine**: Topo forward pass theo dependency (FS/SS/FF/SF + lag) và working calendar. <br> - **Critical Path (CPM)**: Tính toán float và đánh dấu đường găng (critical path). <br> - **Resource Planning**: Gán allocation %, xem biểu đồ workload DAY/WEEK/MONTH chéo dự án. <br> - **Gantt UI**: SVG Gantt chart tự vẽ, hiển thị timeline, critical path, milestones, và dependencies. <br> - **Baseline & Version**: Chụp snapshot baseline, so sánh phiên bản (variance). |
| **v1.2 E2E Software Management** | - **Agile/Sprints**: Tạo sprint, gán backlog, kéo thả công việc, cảnh báo vượt hạn sprint. <br> - **Project Wiki**: Biên soạn tài liệu theo mẫu có sẵn (Getting Started, Architecture, v.v.). <br> - **QA Testing**: Lập test case, chạy test run. Test step thất bại sẽ tự động kích hoạt tạo `Issue` loại `BUG` gán lại cho Dev. <br> - **Git Integration**: Tự động chuyển trạng thái task và liên kết commit/PR qua Git Webhook (xác thực HMAC-SHA256). <br> - **EVM Finance**: Quản lý đơn giá thành viên (mã hóa cột AES-256-GCM), tính toán chỉ số PV, EV, AC, CPI, SPI hàng ngày vẽ đồ thị SVG. |

## 3. Kiến trúc & Công nghệ

- **Backend**: Java 21, Spring Boot 3.x, Spring Security + JWT, Spring Data JPA, Flyway, MapStruct, Maven. Database PostgreSQL 16 (AES-256-GCM column encryption).
- **Frontend**: Angular 22 (standalone, zoneless, signals), Angular Material, Reactive Forms, Vitest, SCSS.
- **E2E Testing**: Playwright framework (`@playwright/test`).
- **Deploy**: Docker Compose (postgres + backend + frontend-Nginx reverse proxy).

## 4. Chất lượng phát hành

- Backend: **295 tests PASS** (0 failure, 0 skipped) — `mvn clean test`.
- Frontend: **73 tests PASS** — `npm test`; build PASS — `npm run build` (SCSS budget clean).
- Smoke E2E: **14/14 PASS** — `scripts/smoke-test.ps1`.
- Demo Flow CRM: **11/11 PASS** — `scripts/demo-flow-project.ps1`.
- Playwright E2E: Sẵn sàng 3 spec files phủ các luồng chính.
- Hiệu năng Gantt (500 tasks): Recalc mất `365` ms, Gantt API mất `177` ms ($\rightarrow$ **Rất tốt**).

## 5. Hướng dẫn chạy local

### 5.1 Khởi chạy bằng Docker Compose (chuẩn)

```powershell
# 1. Tạo .env từ mẫu
Copy-Item .env.example .env
#    → Cấu hình JWT_SECRET (tối thiểu 32 ký tự) và các mật khẩu

# 2. Khởi chạy Docker Desktop (nếu chưa chạy)
# 3. Build & khởi động các container
docker compose up -d --build

# 4. Chạy script tạo dữ liệu mẫu CRM chuẩn
powershell -ExecutionPolicy Bypass -File scripts/demo-flow-project.ps1
```

*   **Truy cập**:
    *   Frontend: `http://localhost:4200`
    *   Backend/Swagger: `http://localhost:8080/swagger-ui.html`

### 5.2 Tài khoản demo

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `Admin@123` | ADMIN hệ thống |
| `pm.minh` | `Pm@12345` | PROJECT_MANAGER |
| `member1` | `Member@123` | PROJECT_MEMBER (Developer 1) |
| `member2` | `Member@123` | PROJECT_MEMBER (Tester) |
| `member3` | `Member@123` | PROJECT_MEMBER (Developer 2) |

## 6. Checklist phát hành v1.2.0

- [x] `mvn clean test` — 295 PASS
- [x] `npm test` — 73 PASS
- [x] `npm run build` — PASS (SCSS budget warnings dọn sạch)
- [x] `docker compose up -d --build` — 3 container Up, backend Healthy
- [x] Smoke test E2E 14/14 PASS
- [x] Demo flow CRM 11/11 PASS (Dữ liệu mẫu chuẩn hóa)
- [x] Performance check Gantt 500 tasks (recalc < 400ms, Gantt API < 200ms)
- [x] Tài liệu: test plan, code review, release notes hoàn chỉnh

## 7. Backlog tiếp theo (v1.3.0)

1.  Hỗ trợ Email/SMS Notifications thực tế (ngoài in-app).
2.  Bổ sung Realtime updates bằng WebSockets.
3.  Cấu hình đa ngôn ngữ (i18n).
