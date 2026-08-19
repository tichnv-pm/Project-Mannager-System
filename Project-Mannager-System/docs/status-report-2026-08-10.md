# PM Daily Work Management — Báo cáo hiện trạng & Kế hoạch thực hiện tiếp theo

> Ngày kiểm tra: 2026-08-10 (kiểm chứng trực tiếp trên mã nguồn + test reports, không chỉ dựa vào tài liệu).

## 1. Hiện trạng hệ thống (đã kiểm chứng)

### 1.1 Đã hoàn thành

| Hạng mục | Trạng thái | Bằng chứng kiểm chứng 2026-08-10 |
|---|---|---|
| v1.0.0 Release | ✔ Hoàn chỉnh | Tài liệu `docs/release/01..03`, smoke E2E 15/15 (đã ghi nhận) |
| v1.1 Backend PLN-BE-01..10 (10/10) | ✔ Hoàn thành | 30 file surefire-reports → **276 tests, 0 failures/errors** (bổ sung 2 GET read-only khi triển khai PLN-FE-03/06 → **278 tests**) |
| Migrations Flyway | ✔ V1..V6 | `V4__project_planning.sql` (plan + seed permissions `plan:*` cho 4 role), `V5__plan_change_dual_approval.sql`, `V6__plan_template_and_portfolio.sql` (8 templates: FULL_SDL… DATA) |
| Backend modules planning | ✔ 9 controllers | `PlanController`, `PlanTaskController`, `PlanDependencyController` (qua PlanTask), `PlanCalendarController`, `PlanResourceController`, `PlanBaselineController`, `PlanChangeController`, `PlanLinkController`, `PlanTemplateController`, `PortfolioController` |
| API docs planning | ✔ | `docs/api/13-planning-api.md` |
| Tài liệu planning | ✔ (1 điểm lệch) | `docs/planning/01..15` — **thiếu cập nhật**: `15-requirement-traceability.md` §5b PLN-BE-10 vẫn để trạng thái `○`; `00-project-overview.md` dòng tiêu đề vẫn ghi "còn PLN-BE-09..10" |

### 1.2 Chưa triển khai / tồn đọng

| # | Hạng mục | Hiện trạng chi tiết |
|---|---|---|
| 1 | **Frontend v1.1 — Module `planning`** (lớn nhất) | **ĐÃ HOÀN TẤT PLN-FE-01..10 ✔ 2026-08-10/11** (27 file mới trong `pages/planning/` + routes lazy-load) |
| 2 | **Gantt UI** | **✔ HOÀN THÀNH 2026-08-11** — license chốt 2026-08-07 tự dựng SVG (docs/planning/13 §4); backend `GET /plans/{id}/gantt` + 2 tests (281 backend tests PASS); FE `plan-gantt.component.*` (grid WBS + timeline SVG, zoom, expand, today, critical, baseline, milestone, dep arrows); npm test 73 PASS + build PASS |
| 3 | **E2E framework (Playwright/Cypress)** | Chưa có dependency trong `frontend/package.json`; hiện dùng smoke script thủ công `scripts/smoke-test.ps1` |
| 4 | **Export CSV streaming** | **✔ HOÀN THÀNH 2026-08-11** — `ReportService.exportReport` ghi thẳng `OutputStream` (PrintWriter UTF-8 autoflush, giữ `@Transactional` + audit), `ReportController.export` dùng **Writer trực tiếp** `HttpServletResponse.getOutputStream()` (StreamingResponseBody không chạy qua MockMvc — body rỗng); `ReportIntegrationTest` thêm assertion nội dung CSV (`Report: tasks-by-status`, `Status,Count`) — **1/1 PASS, 281 backend tests PASS** |
| 5 | **Tối ưu SCSS budget** | **✔ HOÀN THÀNH 2026-08-11** — gộp bộ button/modal/alert dùng chung từ `task-list.component.scss` + `task-detail.component.scss` lên `styles.scss` (global, không tính budget component); 2 component còn giữ phần riêng (task-preview/delete-confirm); `npm run build` hết warning budget + `npm test` 73 PASS |
| 6 | **Sync trạng thái docs** | `docs/planning/15` §5b + `docs/00-project-overview.md` tiêu đề chưa phản ánh PLN-BE-10 ✔ |

### 1.3 Ghi chú môi trường

- Docker daemon hiện **không chạy** (cần `Start-Process "Docker Desktop.exe"` khi cần kiểm chứng container).
- Backend `target/` có đầy đủ surefire reports (build gần nhất) — jar chưa package lại (chỉ có classes).

## 2. Kế hoạch thực hiện tiếp theo (thứ tự đề xuất)

> Tuân thủ quy trình: mỗi bước hoàn chỉnh (code + test + docs) rồi mới chuyển bước; docs là nguồn sự thật; không sửa migration đã chạy.

### Bước 0 — Sync tài liệu (✔ HOÀN THÀNH 2026-08-10)
- ✔ Cập nhật `docs/planning/15-requirement-traceability.md` §5b: PLN-BE-10 → ✔ 2026-08-08 (ghi 276 tests PASS, sửa endpoint thực tế `/plans/from-template`, `/portfolio` — bỏ `/gantt` chưa tồn tại, ghi chú Gantt chờ license).
- ✔ Cập nhật `docs/00-project-overview.md` dòng tiêu đề + bảng mục 11: PLN-BE-09/10 ✔, tổng 276 tests, bổ sung hàng PLN-FE-01..10 (○ Chưa bắt đầu).
- ✔ Cập nhật `AGENTS.md` trạng thái: backlog v1.1 bổ sung Frontend module `planning` (PLN-FE-01..10) — hạng mục lớn nhất còn lại.
- **Kiểm tra**: review nội dung 3 file — không cần build.

### Bước 1 — Frontend v1.1: Module `planning` (công việc chính, chia nhỏ theo backend)
1. `PLN-FE-01` Plan list + create/update/delete + lifecycle (submit/approve/activate) UI, master–detail. — **✔ HOÀN THÀNH 2026-08-10** (chi tiết `docs/planning/15` §5c).
2. `PLN-FE-02` Plan Editor: WBS cây (add/move/indent/outdent, wbs_code), roll-up header. — **✔ HOÀN THÀNH 2026-08-10** (chi tiết `docs/planning/15` §5c).
3. `PLN-FE-03` Dependency editor (FS/SS/FF/SF + lag) + cảnh báo vòng lặp từ BE. — **✔ HOÀN THÀNH 2026-08-10** (chi tiết `docs/planning/15` §5c).
4. `PLN-FE-04` Calendar UI (org plan_calendars + project effective). — **✔ HOÀN THÀNH 2026-08-10** (chi tiết `docs/planning/15` §5c).
5. `PLN-FE-05` Scheduling: nút Recalc + warnings panel + Critical Path view. — **✔ HOÀN THÀNH 2026-08-10** (chi tiết `docs/planning/15` §5c).
6. `PLN-FE-06` Resource: gán allocation, capacity, workload (DAY/WEEK/MONTH), over-allocation cảnh báo. — **✔ HOÀN THÀNH 2026-08-10** (chi tiết `docs/planning/15` §5c).
7. `PLN-FE-07` Version + Baseline + variance hiển thị. — **✔ HOÀN THÀNH 2026-08-11** (chi tiết `docs/planning/15` §5c; npm test 60 PASS, 42 service tests).
8. `PLN-FE-08` Change requests (2 cấp duyệt) + Plan links. — **✔ HOÀN THÀNH 2026-08-11** (chi tiết `docs/planning/15` §5c; 279 backend tests, npm test 68 PASS, 50 service tests)
9. `PLN-FE-09` Template (tạo plan từ template) + Portfolio dashboard. — **✔ HOÀN THÀNH 2026-08-11** (chi tiết `docs/planning/15` §5c; npm test 72 PASS, 54 service tests)
10. `PLN-FE-10` Gantt UI. — **✔ HOÀN THÀNH 2026-08-11** (chi tiết `docs/planning/15` §5c; backend `/gantt` + 2 tests — 281 backend tests PASS; npm test 73 PASS; 55 service tests)
- Mỗi bước: service + models + page + vitest; kiểm tra `npm test` + `npm run build`.
- Lưu ý: phân quyền sidebar theo permission `plan:*` (đã seed từ V4).

### Bước 2 — Gantt UI (PLN-FE-10) — **✔ HOÀN THÀNH 2026-08-11**
- License đã chốt 2026-08-07: **tự dựng SVG, không dependency** (docs/planning/13 §4).
- ✔ Backend `GET /plans/{id}/gantt` 2026-08-11: `PlanGanttService` (tree + isCritical live + baseline overlay + resources + dependencies, warnings read-only) + `PlanGanttIntegrationTest` 2 tests — **281 backend tests PASS + BUILD SUCCESS**.
- ✔ FE `plan-gantt.component.*`: grid WBS trái sticky + timeline SVG phải (sync scroll), zoom Ngày/Tuần/Tháng, expand/collapse, today line, critical đỏ, baseline xám, milestone kim cương, summary bar đậm, dep arrows FS, resource chip, tooltip — npm test **73 PASS** + build PASS.
- Chờ kiểm chứng hiệu năng 500 task/plan (PLN-NFR-GEN-02) qua e2e Playwright (Bước 3).

### Bước 3 — Backlog chất lượng (còn 1 mục AGENTS.md)
1. **E2E framework**: thêm Playwright, viết kịch bản cover luồng planning chính (tạo plan → WBS → dependency → recalc → baseline → approve), chạy trên Docker Compose.
2. **Export CSV streaming** — **✔ HOÀN THÀNH 2026-08-11**: Writer trực tiếp qua `HttpServletResponse.getOutputStream()` (MockMvc không execute `StreamingResponseBody` → body rỗng); test assert nội dung CSV PASS.
3. **SCSS budget** — **✔ HOÀN THÀNH 2026-08-11**: chuyển button/modal/alert chung sang `styles.scss`, giữ phần riêng từng component; `npm run build` sạch budget warnings.

### Bước 4 — Release v1.1
- `mvn clean verify` + `mvn clean package` (BE 276+ tests), `npm test` + `npm run build` (FE).
- Docker Compose up + smoke E2E full (kể cả kịch bản planning).
- Tài liệu release: `01-test-plan.md`, `02-code-review.md`, `03-release-notes.md` (v1.1).
- Cập nhật `docs/00-project-overview.md` + `AGENTS.md`.

### Ước lượng tổng
- Bước 0: ✔ 2026-08-10 · Bước 1+2: ~10–14 ngày làm việc (lớn nhất) · Bước 3: ~2–3 ngày · Bước 4: ~1–2 ngày.

## 3. Rủi ro cần lưu ý
1. Gantt render hiệu năng 500 task (PLN-NFR-GEN-02) → đo bằng e2e Playwright (future); backend bugs có thể xuất hiện khi FE dùng thật (giảm thiểu: test từng tab).
2. FRONTEND v1.1 khối lượng lớn → chia nhỏ PLN-FE-01..10, test từng bước không dồn.
3. E2E Playwright cần Docker daemon + full stack lên → khởi động Docker sớm, chạy smoke trước khi viết kịch bản mở rộng.
4. File migration đã chạy (V4–V6) không được sửa — mọi thay đổi DB mới dùng V7+.

## 4. Bổ sung 2026-08-13 — Luồng demo chuẩn + đồng bộ mốc baseline → Milestone

### 4.1 Quyết định (theo yêu cầu PM)
- Dựng **luồng demo đầy đủ**: tạo dự án → lập kế hoạch master/detail theo chuẩn phát triển phần mềm (FULL_SDL 17 phases) → phân bổ nguồn lực → giao việc → baseline.
- **Mốc baseline của kế hoạch** (plan_task `task_type=MILESTONE`, ví dụ MS-SP1/MS-DTL1) được **nâng thành milestone trong tính năng Milestone** và liên kết ngược về kế hoạch bằng `plan_links target_type=MILESTONE` — cùng cơ chế như task/họp/risk/issue (docs/planning/03 LINL, docs/api/13 §2.8).

### 4.2 Dữ liệu mẫu mới (migration `V10__demo_crm_flow_and_milestone_sync.sql`)
- PRJ-AGILE (303): milestone 1006 (từ MS-SP1/b01-c06) và 1007 (từ MS-DTL1/b02-e05) + link e51/e52.
- Dự án mới **PRJ-CRM (304)**: Master `PF-CRM-MASTER` (17 phases chuẩn FULL_SDL, APPROVED, baseline 1 kèm version 1), Detail `PF-CRM-DEV` (5 tasks + deps FS + milestone CRM-D5), phân bổ 5 allocation (member1/2/3), giao việc 2 execution task (421/422) qua `plan_links EXECUTION_TASK is_primary_execution=true`, milestone 1008 (từ CRM-D5) + link d403.

### 4.3 Script demo (idempotent)
- `scripts/demo-flow-project.ps1` — chạy lại được, tự bỏ qua dữ liệu đã tồn tại; **11/11 PASS** (2026-08-13).
- `scripts/integration-planning-sweep.ps1` cập nhật milestone check (303 = 4 mốc) — **23/23 PASS**; `scripts/smoke-test.ps1` — **14/14 PASS**.
- Backend build trong Docker image kèm toàn bộ test (282 tests PASS trong image build).

### 4.4 Lưu ý vận hành
- Enum ràng buộc `project_members.role` (PROJECT_MANAGER/DEVELOPER/...) và `tasks.source` (MANUAL/...) — seed SQL phải dùng đúng enum.
- Mốc sync là dữ liệu demo (không phải tính năng tự động): PM vẫn tạo milestone + link thủ công qua UI; nếu cần "tự động promote mốc kế hoạch → Milestone" thì ghi backlog v1.2 (đề xuất: nút "Tạo Milestone từ mốc kế hoạch" + payload tự điền).