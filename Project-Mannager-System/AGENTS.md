# AGENTS.md

PM Daily Work Management — ứng dụng quản lý công việc hằng ngày cho quản lý dự án phần mềm.
Trạng thái: **v1.2.0 PHÁT HÀNH HOÀN CHỈNH (v1.1 & v1.2)** (✔ 2026-08-22): Backend **295 tests PASS**; Frontend **73 tests + build PASS** (SCSS budget warnings dọn sạch); E2E Playwright framework tích hợp sẵn (e2e/); Docker Compose 3 container Up + smoke E2E 14/14 PASS + CRM Demo Flow 11/11 PASS; Hiệu năng Gantt 500 tasks (recalc 365ms, Gantt API 177ms); Tài liệu Release đầy đủ (`docs/release/01-test-plan.md`, `02-code-review.md`, `03-release-notes.md`).
Giai đoạn hiện tại: **v1.2 — E2E SOFTWARE MANAGEMENT HOÀN TẤT ✔ 2026-08-22**: Tích hợp Agile/Sprints, Project Wiki templates, QA Testing module, Git Webhooks, và EVM Finance. Toàn bộ tests và builds đã pass, mã nguồn sạch sẽ.

## Quy trình làm việc (bắt buộc)

- Triển khai **tuần tự từng giai đoạn** theo bộ prompt đã thống nhất (docs/00-project-overview.md mục 11).
- **Không** tạo toàn bộ ứng dụng trong một lần; **không** tự ý tạo chức năng ngoài giai đoạn hiện tại.
- Trước khi sửa/tạo file: đọc tài liệu liên quan trong `docs/` trước — docs là nguồn sự thật chính.
- Không xóa file hoặc đổi kiến trúc đã thống nhất.
- Sau mỗi thay đổi phải liệt kê: file tạo mới, file cập nhật, mục đích, cách kiểm tra.
- Code phải compile được và test pass trước khi chuyển bước; không được xóa/skip test để build pass.
- Không viết TODO thay chức năng thật; không mock dữ liệu production; không hard-code secret.

## Công nghệ (đã thống nhất)

- Backend: Java 21, Spring Boot 3.x, Spring Security + JWT, Spring Data JPA, Flyway, Lombok, MapStruct, Bean Validation, Maven, springdoc (Swagger). Package gốc: `com.example.pmdaily`.
- Frontend: Angular 22 (standalone, signals, zoneless), Angular CDK, Reactive Forms, SCSS tự viết (không dùng Angular Material). Chưa dùng NgRx — dùng service + RxJS. E2E: Playwright (`frontend/e2e`).
- DB: PostgreSQL, PK UUID, `snake_case`, `timestamptz` (lưu UTC, UI hiển thị theo giờ người dùng).
- Chạy local: Docker Compose (postgres/backend/frontend-Nginx) hoặc thủ công Maven + npm.

## Lệnh (đã xác nhận — source thật từ Prompt 07)

- Backend: `mvn clean test`, `mvn clean package`, `mvn spring-boot:run -Dspring-boot.run.profiles=local` (JDK Temurin 21.0.12 + Maven 3.9.16 — đã kiểm chứng)
- Frontend: `npm install`, `npm test` (không watch — Vitest qua Angular builder), `npm run build`, `npm start` (Angular 22, zoneless, Node v24.18.1)
- Docker: `docker compose up -d --build`, `docker compose ps`, `docker compose logs -f`, `docker compose down [-v]` (Docker CLI 29.6.2 + Compose v5.3.1 — **đã kiểm chứng full stack PASS 2026-08-03**; daemon cần khởi động thủ công: `Start-Process "Docker Desktop.exe"`)
- Migration: Flyway trong `backend/src/main/resources/db/migration` (`V1__init_schema.sql`, `V2__seed_local_data.sql` — V2 chỉ chạy ở profile `local` qua `flyway.target`, xem docs/build/environment-check.md mục 5)

## Giai đoạn v1.1 — PROJECT PLANNING (bắt buộc)

- Nguồn sự thật: `docs/planning/01..15` + `docs/api/13-planning-api.md` + `docs/database/*` (phần B). Trước mỗi bước PLN-BE-*, đọc tài liệu planning tương ứng.
- Triển khai backend tuần tự: PLN-BE-01..10 — mỗi bước 1 phần: migration riêng (không sửa file migration đã chạy), unit/integration test, `mvn clean verify`, `mvn clean package`, cập nhật docs/test/planning, báo cáo file tạo/sửa.
- Quyền mới `plan:*` phải seed đồng bộ vào bảng `permissions` + gán cho role (docs/05 §3, docs/planning/04).
- Gantt: chưa thêm dependency cho tới khi PM/khách chốt license (docs/planning/13 §4) — caching kiểm duyệt.

## Kiến trúc

- Modular Monolith: mỗi module nghiệp vụ có `controller / service / repository / entity / dto / mapper / specification`.
- API prefix `/api/v1`, JSON, ISO-8601, pagination `page/size/sort`; error response thống nhất (`timestamp/status/error/code/message/path/fieldErrors/traceId`).
- Không trả Entity qua API (DTO + MapStruct); optimistic locking bằng `version`; soft delete cho dữ liệu nghiệp vụ.
- Secret đọc từ environment variable; application.yml chỉ chứa giá trị mặc định an toàn; profile `local`/`test`.

## Giai đoạn 0 — Vá rủi ro bảo mật & config (2026-11-11, merge từ `claude/tender-albattani-18pxg1` vào `develope`)

Theo kế hoạch cải tiến (đánh giá kiến trúc/code/tài liệu/nghiệp vụ), đã vá rủi ro bảo mật sau, không đổi kiến trúc:

- **Webhook secret không còn hard-code**: `GitWebhookController` trước đây có fallback `git-webhook-secret-key-12345` ngay trong code, trong khi endpoint `/api/v1/public/webhooks/**` là `permitAll()` — nếu quên set biến môi trường, ai cũng giả mạo được webhook. Nay bắt buộc qua `app.webhook.git-secret` → biến môi trường `GIT_WEBHOOK_SECRET` (fail-fast nếu thiếu, giống `JWT_SECRET`), secret mặc định chỉ còn ở profile `local`/`test`.
- **Flyway target & log level ở `develope`**: nhánh `develope` đã tự giải quyết 2 điểm này theo hướng khác (và đầy đủ hơn) trước khi merge — `flyway.target` cấu hình qua biến môi trường `${FLYWAY_TARGET:latest}` (không hard-code), `application-prod.yml` đã có sẵn connection pool + log level production-ready; giữ nguyên bản của `develope` cho 2 mục này khi merge, chỉ mang theo fix webhook secret từ nhánh kia.

**File cập nhật:** `backend/src/main/java/com/example/pmdaily/git/GitWebhookController.java`, `backend/src/main/resources/application.yml`, `application-local.yml`, `application-test.yml`, `docker-compose.yml`, `.env.example`.
**Kiểm tra:** `mvn test -Dtest=GitWebhookIntegrationTest` (6/6 PASS trên cả 2 nhánh, xác nhận secret mới hoạt động đúng cho cả GitHub HMAC và GitLab token) + `mvn test` toàn bộ backend trên `develope` sau merge.

**Ghi nhận ngoài phạm vi GĐ 0:** `mvn test` toàn bộ backend trên `develope` cho thấy 2 test PASS trước đó nay FAIL, không liên quan tới thay đổi GĐ 0 (`TaskIntegrationTest.update_member_onlyOwnAssignedTaskLimitedFields`, `TaskIntegrationTest.create_blockedWithoutReason_returns400` — tái hiện cả khi chạy riêng lẻ, không phải do thứ tự test). Chưa sửa trong phiên này; cần xử lý riêng.

## Giai đoạn 2 — Đồng bộ tài liệu với code (2026-11-11)

Theo kế hoạch cải tiến, đã sửa các điểm tài liệu lệch thực tế code (không đổi code/kiến trúc):

- **Angular Material không có thật**: README, AGENTS.md, `docs/00-project-overview.md`, `docs/design/03-frontend-architecture.md`, `docs/release/03-release-notes.md`, `docs/planning/13-gantt-ui-design.md` đều ghi dùng Angular Material — `frontend/package.json` không có dependency này và không có `mat-*` nào trong template. Đã sửa tất cả về đúng thực tế: Angular 22 standalone/signals + Angular CDK + SCSS tự viết, Gantt grid/timeline tự dựng 100%.
- **Bảng công nghệ `docs/00-project-overview.md` lỗi thời**: sửa "Testcontainers" → H2 (thực tế dùng), "Jasmine/Karma hoặc Jest" → Vitest + Playwright (thực tế dùng).
- **`docs/01-business-requirements.md` §9**: 5 câu hỏi mở từ v1 chưa từng được đóng dù đã phát hành v1.0–v1.2. Đã rà code để chốt 4/5 câu (UI tiếng Việt, quyết định ghi qua biên bản+audit, không có bước "nhận việc" bắt buộc, Excel chỉ cho Task export còn Report dùng CSV); câu còn lại (quy mô người dùng tối đa) xác nhận là **thật sự chưa có** — không bịa số, để ngỏ.
- **`docs/07-requirement-traceability-matrix.md`**: thêm mục 8c — traceability tóm tắt (tính năng ↔ migration ↔ API ↔ test) cho 5 tính năng v1.2 (Sprint/QA/Git/Wiki/Finance), vì các tính năng này chưa có bộ tài liệu acceptance/traceability đầy đủ như v1.1; ghi rõ đây vẫn là nợ tài liệu chưa đóng hết.

**File cập nhật:** `README.md`, `AGENTS.md`, `docs/00-project-overview.md`, `docs/01-business-requirements.md`, `docs/07-requirement-traceability-matrix.md`, `docs/design/03-frontend-architecture.md`, `docs/planning/13-gantt-ui-design.md`, `docs/release/03-release-notes.md`.
**Kiểm tra:** chỉ sửa tài liệu, đối chiếu từng khẳng định với `frontend/package.json`, mã nguồn `frontend/src` (grep `mat-`), và test backend hiện có trước khi ghi là "đã chốt".

## Giai đoạn 1 — CI/CD & Rate limiting (2026-11-11)

Theo kế hoạch cải tiến, đã thêm pipeline CI và rate limit; nhân tiện phát hiện + sửa 2 lỗi nghiệp vụ thật đang chặn CI chạy xanh (không nằm trong phạm vi GĐ 1 ban đầu nhưng phải sửa để CI có ý nghĩa):

- **CI/CD**: `.github/workflows/ci.yml` (ở gốc repo, ngoài thư mục `Project-Mannager-System/`) — job `backend` chạy `mvn -B clean verify` (JDK 21 temurin), job `frontend` chạy `npm ci` + `npm test` + `npm run build` (Node 22). Trước đây chưa có CI nào, mọi "đã kiểm chứng" là thủ công.
- **Rate limiting**: `RateLimitFilter` (in-memory, theo IP, sliding window, không thêm dependency mới) áp cho `/api/v1/auth/login` và `/api/v1/public/webhooks/**` — 2 endpoint `permitAll()` duy nhất, trước đây không có giới hạn nào ngoài login-lockout theo tài khoản. Mặc định 20 req/60s (login) và 60 req/60s (webhook), cấu hình qua env (`RATE_LIMIT_*`), **tắt ở profile `test`** để không phá test hiện có. Vượt giới hạn → 429 `RATE_LIMIT_EXCEEDED`.
- **Lỗi phát hiện khi chạy `mvn clean verify`/`npm run build` thật (ngoài phạm vi GĐ 1, đã sửa vì chặn CI xanh):**
  - `TaskService.update()` đã mất field-limiting cho MEMBER (vi phạm BR-TASK-13 bắt buộc) — bất kỳ ai `ensureCanView` được là sửa toàn bộ field của task bất kỳ. Khôi phục: dùng `ensureCanUpdateOrAssignee` + chỉ admin/PM mới sửa full field, MEMBER chỉ sửa status/progress/notes trên task được giao cho mình.
  - `applyStatusRules()` không còn throw `BLOCKER_REASON_REQUIRED` khi block task mà không có lý do (tự động điền "Tắc nghẽn" thay vì từ chối) — khôi phục throw khi chưa có lý do nào (giữ nguyên hành vi "giữ lý do cũ nếu không gửi lại" cho task đã blocked từ trước).
  - Frontend: `task-detail.component.ts` thiếu import `ProjectMemberResponse` → `npm run build` thật sự fail dù test vẫn pass (Vitest không chạy qua `tsc` strict như `ng build`).

**File tạo mới:** `.github/workflows/ci.yml`, `backend/src/main/java/com/example/pmdaily/security/RateLimitFilter.java`, `backend/src/test/java/com/example/pmdaily/security/RateLimitFilterIntegrationTest.java`.
**File cập nhật:** `backend/src/main/java/com/example/pmdaily/common/ErrorCode.java`, `backend/src/main/java/com/example/pmdaily/security/SecurityConfig.java`, `backend/src/main/java/com/example/pmdaily/task/TaskService.java`, `backend/src/main/resources/application.yml`, `application-test.yml`, `frontend/src/app/pages/tasks/task-detail.component.ts`.
**Kiểm tra:** `mvn test` toàn bộ backend — 297/297 PASS (295 cũ + 2 test rate-limit mới); `npm test` frontend — 73/73 PASS; `npm run build` frontend — thành công sau khi thêm import còn thiếu.

**Chưa làm trong GĐ 1 (để lại, có lý do):** chuyển test backend sang Testcontainers Postgres — môi trường phiên này không có Docker daemon chạy, không thể verify nếu làm; dọn migration demo-data lẫn trong lịch sử Flyway thật (V7–V10) — rủi ro cao, cần kế hoạch riêng; tăng coverage test component frontend — khối lượng lớn, để giai đoạn sau.

## Giai đoạn 3 (phần 1/7) — Epic/Story/Backlog + Story Point (2026-11-11)

GĐ3 (Agile/Quality nghiệp vụ) gồm 7 hạng mục, triển khai từng phần theo xác nhận với người dùng. Phần 1: nền tảng Epic/Story/Backlog + Story Point — PM xác nhận bắt đầu từ đây trước khi làm Velocity/Burndown (phụ thuộc vào phần này).

**Thiết kế:** tái dùng 100% hạ tầng Task sẵn có thay vì tạo entity mới — Epic/Story là Task với `type=EPIC/STORY`; hierarchy Epic→Story→Task dùng cơ chế `parentTask` đã có (FR-TASK-12); gán sprint dùng `sprintId` đã có trên Task — nhờ vậy Velocity/Burndown ở phần sau chỉ cần đọc dữ liệu có sẵn, không cần migrate lại. Thêm cột `story_points` (song song `estimate_minutes`) và validate mới **BR-TASK-19 (bắt buộc)**: Epic không có task cha; Story nếu có task cha thì cha phải là Epic (`400 INVALID_EPIC_STORY_HIERARCHY`).

**File tạo mới:** `backend/.../db/migration/V20__add_epic_story_backlog.sql`.
**File cập nhật:**
- Backend: `TaskType.java` (+EPIC, +STORY), `Task.java` (+storyPoints), `TaskService.java` (`applyParent` validate hierarchy, `create`/`update` set storyPoints), `ErrorCode.java` (+INVALID_EPIC_STORY_HIERARCHY), `TaskCreateRequest/TaskUpdateRequest/TaskResponse/TaskSummaryResponse` (+storyPoints), `TaskIntegrationTest.java` (+5 test).
- Frontend: `task.model.ts` (+EPIC/STORY, +storyPoints), `task-list.component.ts/html/scss` (dropdown, form field, badge màu), `task-detail.component.ts/html` (label, hiển thị + form sửa storyPoints).
- Docs: `docs/api/05-task-api.md`, `docs/02-functional-requirements.md` §1.3 (bỏ "cần xác nhận"), `docs/04-business-rules.md` (+BR-TASK-19).

**Kiểm tra:** backend 52/52 PASS trong `TaskIntegrationTest` (47 cũ + 5 mới) + `mvn test` toàn bộ; frontend `npm test` 73/73 PASS + `npm run build` thành công.

**Chưa làm (5/7 hạng mục còn lại của GĐ3, chờ xác nhận tiếp theo):** Definition of Done, Change Request mở rộng Scope–Schedule–Cost, KPI chất lượng (defect density/escape rate), Retrospective gắn sprintId. Chưa xây màn hình Backlog riêng (kéo-thả, nhóm theo Epic) — hiện tái dùng màn hình Task list có sẵn với filter `type=EPIC/STORY`.

## Giai đoạn 3 (phần 2/7) — Velocity + Burndown (2026-11-11)

**Velocity**: tính trực tiếp (live, không cache) từ `tasks.story_points` + `tasks.sprint_id` đã có — tổng/đã hoàn thành (status=DONE) story point + số task. API: `GET /sprints/{id}/velocity`, và `GET /projects/{projectId}/sprints/velocity-trend` (các sprint COMPLETED, sort theo endDate, cho biểu đồ xu hướng qua nhiều sprint).

**Burndown**: **không** suy ngược từ audit log hiện có — không phải mọi đường cập nhật trạng thái task đều ghi before/after status nhất quán (ví dụ `update()` chung chỉ log `TASK_UPDATED` không kèm status cũ/mới), nên suy ngược sẽ ra số liệu sai. Thay vào đó dùng **snapshot hàng ngày** qua `SprintBurndownScheduler` (cron 02:15, cùng mẫu `EvmScheduler`/`project_financial_snapshots` đã có từ V18) — bảng `sprint_burndown_snapshots` (V21). Đường "lý tưởng" tính tại query-time (tuyến tính theo `sprint.startDate/endDate`, baseline = tổng điểm ở snapshot đầu tiên), không lưu trữ. **Giới hạn đã biết:** vì dựa trên snapshot, dữ liệu Burndown chỉ có từ ngày tính năng được bật trở đi — không có dữ liệu lịch sử cho sprint đã chạy trước đó (giống cách EVM snapshot hoạt động).

**File tạo mới:** `backend/.../db/migration/V21__add_sprint_burndown_snapshots.sql`, `SprintBurndownSnapshot.java`, `SprintBurndownSnapshotRepository.java`, `SprintBurndownScheduler.java`, `dto/SprintVelocityResponse.java`, `dto/BurndownPointResponse.java`.
**File cập nhật:** `SprintRepository.java` (+findByStatusAndDeletedAtIsNull), `SprintService.java` (+getVelocity/getVelocityTrend/getBurndown), `SprintController.java` (+3 endpoint), `SprintIntegrationTest.java` (+3 test), frontend `sprint.model.ts`/`sprint.service.ts` (+2 API), `project-sprints.component.ts/html/scss` (thay mock "capacity" cũ bằng Velocity thật + biểu đồ Burndown SVG tự vẽ cho sprint ACTIVE, không thêm thư viện chart).
**Kiểm tra:** `SprintIntegrationTest` 4/4 PASS (1 cũ + 3 mới); `mvn test` toàn bộ backend; frontend `npm test` 73/73 + `npm run build` thành công.

## Giai đoạn 3 (phần 3/7) — Definition of Done (2026-11-11)

**Thiết kế:** DoD là checklist text PM định nghĩa theo dự án (không theo dõi trạng thái check từng item/từng task — không phải quy trình QA test case, giữ đơn giản). Gate: chỉ khi chuyển task sang DONE qua `PUT /tasks/{id}/status` — nếu dự án có ≥1 tiêu chí active, bắt buộc `dodConfirmed=true` trong request, nếu không trả `400 DOD_NOT_CONFIRMED`. Dự án không cấu hình DoD thì không bị chặn gì (tính năng opt-in theo dự án). BR-TASK-20 (bắt buộc, docs/04).

**File tạo mới:** package mới `com.example.pmdaily.dod` (`ProjectDodItem`, `ProjectDodItemRepository`, `ProjectDodItemService`, `ProjectDodItemController`, 3 DTO), `backend/.../db/migration/V22__add_project_dod_items.sql`, `ProjectDodItemIntegrationTest.java`, frontend `pages/projects/dod/{dod.model,dod.service}.ts`.
**File cập nhật:** `ErrorCode.java` (+DOD_NOT_CONFIRMED), `StatusUpdateRequest.java` (+dodConfirmed), `TaskService.java` (gate trong `changeStatus()`), `TaskIntegrationTest.java` (+2 test), frontend `task.model.ts` (+dodConfirmed), `task-list.component.ts/html` + `task-detail.component.ts/html` (hiển thị checklist + checkbox xác nhận khi chuyển DONE), `styles.scss` (+`.dod-checklist`, dùng style chung thay vì thêm vào `task-list.component.scss` đã gần sát budget 16kB).
**Kiểm tra:** `ProjectDodItemIntegrationTest` 6/6 PASS; `TaskIntegrationTest` 54/54 PASS (52 cũ + 2 mới); `mvn test` toàn bộ backend; frontend `npm test` 73/73 + `npm run build` thành công, không cảnh báo budget.

**Chưa làm:** màn hình quản lý DoD cho PM (tạo/sửa/xóa tiêu chí) — hiện chỉ có API, chưa có UI riêng; PM cần dùng API trực tiếp (Swagger) cho tới khi làm màn hình cấu hình dự án.

## Giai đoạn 3 (phần 4/7) — KPI chất lượng (2026-11-11)

**Thiết kế:** báo cáo thuần đọc dữ liệu có sẵn, không migration/entity mới — tái dùng `report` module (style SQL trực tiếp qua `jdbcTemplate`, giống `getRiskIssueSummary` đã có).

- **Escape rate**: % Issue có `test_case_id IS NULL` trên tổng Issue của dự án. Cơ sở: module QA (V15) đã tự động tạo Issue loại BUG kèm `test_case_id` khi 1 test step QA fail (đã có từ trước) — nên `test_case_id IS NOT NULL` nghĩa là Issue được phát hiện qua QA testing; `NULL` nghĩa là báo trực tiếp (không qua QA, coi như "lọt" ra ngoài/escaped).
- **Defect density**: tổng số Issue / số task `DONE` của dự án (0 nếu chưa có task DONE).

**File tạo mới:** `backend/src/main/java/com/example/pmdaily/report/dto/QualityKpiReportResponse.java`.
**File cập nhật:** `ReportService.java` (+getQualityKpi), `ReportController.java` (+endpoint `GET /reports/quality-kpi`), `ReportIntegrationTest.java` (+1 test), frontend `report.service.ts` (+model, +API), `report-list.component.ts/html` (+tab "🎯 KPI chất lượng").
**Kiểm tra:** `ReportIntegrationTest` 2/2 PASS; `mvn test` toàn bộ backend; frontend `npm test` 73/73 + `npm run build` thành công.

**Chưa làm (3/7 hạng mục còn lại của GĐ3):** Change Request mở rộng Scope–Schedule–Cost, Retrospective gắn Sprint, màn hình Backlog riêng (tùy chọn).

## Giai đoạn 3 (phần 5/7) — Retrospective gắn Sprint (2026-11-11)

**Thiết kế:** thêm `meetings.sprint_id` (plain UUID, không FK constraint, cùng mẫu `tasks.sprint_id` từ V14) — liên kết tùy chọn, không thêm field `type` phân loại meeting (giữ đơn giản, không ép buộc "Retrospective" phải là 1 loại họp riêng). Validate: `sprintId` (nếu có) phải thuộc cùng `projectId` của họp, sai → `400 BAD_REQUEST`. Filter `GET /meetings?sprintId=` để tìm họp (retro) gắn với 1 sprint, qua đó truy vết action item sau retro về đúng sprint.

**File tạo mới:** `backend/.../db/migration/V23__add_meeting_sprint_link.sql`.
**File cập nhật:** `Meeting.java` (+sprintId), `MeetingCreateRequest/MeetingUpdateRequest/MeetingResponse` (+sprintId), `MeetingService.java` (+applySprintId validate, +sprintId trong search()), `MeetingSpecification.java` (+sprintId filter), `MeetingController.java` (+query param), `MeetingIntegrationTest.java` (+3 test), frontend `meeting.model.ts`/`meeting.service.ts` (+sprintId), `meeting-list.component.ts/html` (dropdown chọn sprint theo dự án trong form tạo/sửa họp).
**Kiểm tra:** `MeetingIntegrationTest` 33/33 PASS (30 cũ + 3 mới); `mvn test` toàn bộ backend; frontend `npm test` 73/73 + `npm run build` thành công.

**Còn lại 2/7 hạng mục của GĐ3:** Change Request mở rộng Scope–Schedule–Cost (module Planning, chưa đụng tới trong phiên này — rủi ro/phức tạp cao nhất), màn hình Backlog riêng (tùy chọn, không phải 1 trong 7 hạng mục nghiệp vụ gốc).

## Giai đoạn 3 (phần 6/7) — Change Request mở rộng Scope–Schedule–Cost (2026-11-11)

**Thiết kế:** mở rộng change suggestion (`plan_change_requests`, module Planning) có sẵn thay vì tạo entity mới — đây là cơ chế Change Request thật của hệ thống (PLN-RULE-CHG). Theo PMBOK, mọi đề xuất thay đổi cần đánh giá đồng thời 3 ràng buộc Scope-Schedule-Cost trước khi PM/ADMIN duyệt:

- **Scope**: định tính, **không suy ra được bằng số** từ dữ liệu sẵn có → field `scopeImpact` bắt buộc PM tự ghi khi tạo suggestion (`400 VALIDATION_ERROR` nếu thiếu).
- **Schedule**: tính tự động (`scheduleImpactDays`) từ chính các `suggestedChanges` đã gửi — cộng lệch ngày của field `plannedFinish` (so oldValue/newValue) và/hoặc `durationMinutes` (chia 480 phút/ngày làm việc, cùng hằng số `DEFAULT_DAILY_MINUTES` đã dùng ở `WorkingCalendar`/`SchedulingEngine`).
- **Cost**: tính tự động (`costImpactAmount`) từ delta `plannedEffortMinutes` × `hourlyRate` của chủ task (`ProjectMember.hourlyRate`) — **tái dùng chính xác công thức BAC đã có ở `EvmScheduler`** (effort giờ × đơn giá/giờ), không phát sinh field giá/cost mới nào ở `PlanTask`/`ProjectPlan` (2 entity này không có field budget — đã xác nhận tra lại trước khi code). Field/task không có owner hoặc owner chưa có `hourlyRate` → cost impact phần đó tính là 0 (không chặn tạo suggestion, chỉ là ước lượng tham khảo cho người duyệt).

Giá trị impact được tính và lưu **tại thời điểm tạo suggestion** (không tính lại khi accept), vì đây là bức tranh PM/ADMIN cần thấy để quyết định duyệt hay không — giữ nguyên lại trên record để truy vết sau này giống các field review khác.

**File tạo mới:** `backend/.../db/migration/V24__add_plan_change_request_impact.sql`.
**File cập nhật:** `PlanChangeRequest.java` (+scopeImpact, +scheduleImpactDays, +costImpactAmount), `ChangeSuggestionCreateRequest.java` (+scopeImpact bắt buộc), `ChangeSuggestionResponse.java` (+3 field), `PlanChangeService.java` (+computeScheduleImpactDays, +computeCostImpactAmount, gọi trong `createSuggestion`), `PlanChangeLinkIntegrationTest.java` (+2 test: bắt buộc scopeImpact, tính đúng schedule/cost impact; thêm quyền `financial:update` cho PM trong test để set hourlyRate qua API finance có sẵn), docs: `docs/planning/03-project-planning-business-rules.md` (+PLN-RULE-CHG-06), `docs/planning/14-planning-acceptance-criteria.md` (+PLN-AC-CHG-06), `docs/api/13-planning-api.md` §2.9. Frontend: `plan.model.ts` (+scopeImpact/scheduleImpactDays/costImpactAmount), `plan-change.component.ts/html/scss` (form field "Đánh giá tác động phạm vi" bắt buộc khi tạo suggestion + hiển thị 3 chip Scope/Schedule/Cost trên mỗi suggestion), `plan.service.spec.ts` (bổ sung scopeImpact vào payload test).

**Kiểm tra:** `PlanChangeLinkIntegrationTest` 10/10 PASS (8 cũ + 2 mới); `mvn test` toàn bộ backend; frontend `npm test` 73/73 PASS + `npm run build` thành công, không cảnh báo budget.

**Còn lại 1/7 (tùy chọn, không phải hạng mục nghiệp vụ gốc):** màn hình Backlog riêng (kéo-thả, nhóm theo Epic) — hiện vẫn tái dùng màn hình Task list có sẵn với filter `type=EPIC/STORY`.

## Giai đoạn 3 (phần 7/7, tùy chọn) — Màn hình Backlog kéo-thả theo Epic (2026-11-11)

**Thiết kế:** thuần frontend (view mới, không entity/migration mới) — tab "📋 Backlog" trong Project Detail, mỗi cột là 1 Epic (+ cột "Chưa gán Epic"), card là Story/Task (không gồm Epic) nhóm theo `parentTaskId`, kéo-thả giữa cột để đổi Epic — cùng pattern CDK `DragDropModule`/`cdkDropListGroup` đã có ở `project-sprints.component`. Có thêm nhanh Epic/Story ngay trên board (tái dùng `TaskService.createTask`), không xây modal tạo task đầy đủ riêng (đã có ở Task list).

**Lỗi phát hiện + sửa khi làm (ngoài phạm vi ban đầu nhưng bắt buộc phải sửa để tính năng kéo-thả đúng):** `TaskUpdateRequest`/`TaskSummaryResponse` ở frontend thiếu hẳn field `parentTaskId` dù backend đã có từ GĐ3 phần 1 — và nghiêm trọng hơn, `TaskService.update()` (backend) coi `parentTaskId` thiếu/`null` trong `PUT /tasks/{id}` là "gỡ parent", khác quy ước "`null` = không đổi" của các field quan hệ khác trong cùng method (`sprintId`, `collaboratorIds`, `watcherIds`, `tagIds`). Hệ quả thực tế: **sửa bất kỳ field nào của 1 Story qua màn hình Task list/Task detail (không gửi lại `parentTaskId`, vì frontend trước đây không biết field này tồn tại) sẽ vô tình gỡ Story khỏi Epic** — bug âm thầm có từ khi Epic/Story ra mắt (GĐ3 phần 1), chưa có test nào bắt được vì không có test nào update 1 task có parent rồi kiểm tra parent còn giữ. Đã sửa:
- `TaskService.update()`: chỉ gọi `applyParent` khi `request.parentTaskId() != null` (cùng quy ước với field khác) — full update không còn vô tình gỡ parent.
- Thêm endpoint riêng `PUT /tasks/{id}/parent` (`ParentUpdateRequest`, giống mẫu `/assignee`: không cần `version`) để đổi/gỡ parent **tường minh** — `parentTaskId: null` tường minh mới gỡ hẳn (dùng cho board kéo-thả "thả vào cột Chưa gán Epic"). Quyền: chỉ ADMIN/PM dự án (không có MEMBER partial).

**File tạo mới:** `backend/.../task/dto/ParentUpdateRequest.java`, frontend `pages/projects/backlog/backlog-board.component.{ts,html,scss}`.
**File cập nhật:** `TaskService.java` (fix `update()` + `changeParent()` mới), `TaskController.java` (+`PUT /{id}/parent`, sửa số endpoint 24→25), `TaskIntegrationTest.java` (+4 test: regression giữ parent khi update field khác, set/gỡ parent qua endpoint mới, 403 MEMBER, 400 hierarchy sai), frontend `task.model.ts` (+parentTaskId ở `TaskSummaryResponse`/`TaskUpdateRequest`), `task.service.ts` (+`changeParent`), `project-detail.component.ts/html` (+tab "Backlog"), docs: `docs/api/05-task-api.md` (+§3.12, renumber 3.12→3.16 cũ thành 3.13→3.16, lưu ý quy ước `null`), `docs/07-requirement-traceability-matrix.md` (FR-TASK-12).

**Kiểm tra:** `TaskIntegrationTest` 58/58 PASS (54 cũ + 4 mới); `mvn test` toàn bộ backend; frontend `npm test` 73/73 PASS + `npm run build` thành công, không cảnh báo budget.

**GĐ3 (Agile/Quality nghiệp vụ) — HOÀN TẤT 7/7 hạng mục** (Epic/Story/Backlog, Velocity/Burndown, Definition of Done, KPI chất lượng, Change Request Scope-Schedule-Cost, Retrospective gắn Sprint, màn hình Backlog kéo-thả).

## Giai đoạn 4 (phần 1/3) — Observability: Actuator metrics (2026-11-12)

Trước đây Actuator chỉ expose `health,info`, không có metrics gì để theo dõi hệ thống (JVM heap, HTTP latency theo route, connection pool...).

- **Thêm** `io.micrometer:micrometer-registry-prometheus` (version theo Spring Boot BOM) + expose `metrics,prometheus` (ngoài `health,info` cũ) — `GET /actuator/metrics`, `GET /actuator/prometheus` (format Prometheus text để scrape).
- **Bảo mật:** `/actuator/health` vẫn public (`show-details: never`, không lộ chi tiết DB/disk); **mọi actuator khác chỉ ADMIN** (`SecurityConfig` +rule `requestMatchers("/actuator/**").hasRole("ADMIN")`) — metric nội bộ (tên route, thời gian xử lý...) không nên lộ cho user thường.
- **Phát hiện khi làm:** Spring Boot Test tự set `management.defaults.metrics.export.enabled=false` cho mọi `@SpringBootTest` (tránh overhead khi test) — phải set tường minh `management.prometheus.metrics.export.enabled=true` trong `application.yml` (không dựa vào `management.defaults.*`) để endpoint hoạt động nhất quán cả khi test và khi chạy thật.

**File cập nhật:** `backend/pom.xml`, `application.yml`, `SecurityConfig.java`, `SecuritySmokeIntegrationTest.java` (+4 test), `docs/design/07-deployment-design.md` (+mục 12).
**Kiểm tra:** `SecuritySmokeIntegrationTest` 8/8 PASS; `mvn test` toàn bộ backend 327/327 PASS.

**Chưa làm:** dựng Prometheus/Grafana server thật để scrape + dashboard — phiên này chỉ đảm bảo backend "phơi" đúng/đủ metric và bảo mật đúng.

## Giai đoạn 4 (phần 2/3) — Backup/restore PostgreSQL (2026-11-12)

Production chạy trên **Neon Postgres** (`render.yaml`), nên chiến lược tính cả đặc thù này thay vì giả định tự quản lý Postgres:

- **Lớp bảo vệ chính:** Neon Point-in-Time Recovery (PITR, theo plan đang dùng) — không cần code, chỉ cần biết dùng khi cần.
- **Lớp bổ sung:** `scripts/backup-db.sh` (pg_dump, tự parse `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` sẵn có ra host/port/dbname/sslmode, không cần biến môi trường mới) + `scripts/restore-db.sh <file>` (pg_restore, bắt gõ lại đúng tên DB đích để xác nhận trước khi ghi đè).
- **Quy tắc:** không commit file `.dump` vào git; backup trước mỗi migration rủi ro trên production; restore luôn test trên Neon branch tạm trước, không restore trực tiếp lên production khi chưa kiểm chứng.

**File tạo mới:** `scripts/backup-db.sh`, `scripts/restore-db.sh`.
**File cập nhật:** `docs/design/07-deployment-design.md` (+mục 13).
**Kiểm tra:** `bash -n` cả 2 script (cú pháp hợp lệ) + test logic parse JDBC URL cho cả 2 kiểu (Neon có `sslmode=require`, docker-compose local không có query string) — **chưa chạy restore thật** vì phiên này không có Neon branch thử nghiệm hoặc Postgres thật để kiểm chứng full cycle; cần làm trước khi dùng thật trên production.

## Giai đoạn 4 (phần 3/3) — Stakeholder report: share-link cho người ngoài tổ chức (2026-11-12)

PM duyệt làm cả 3 hạng mục GĐ4, nhưng hạng mục này public-facing (rủi ro bảo mật cao nhất GĐ4) nên scoping chặt trước khi code:

**Phạm vi:** chỉ chia sẻ **1 báo cáo** — Project Progress (% tiến độ + done/total task, đã có sẵn ở `GET /reports/project-progress`) — không mở rộng sang EVM/Risk/Issue (dữ liệu nhạy cảm hơn, ngoài phạm vi stakeholder cần xem). "PDF" dùng `window.print()` (CSS `@media print`), không thêm thư viện PDF mới.

**Thiết kế:**
- **Token = chính `id` (UUID) của bản ghi** `report_share_links` — cùng quy ước "UUID không đoán được" dùng cho mọi entity khác trong hệ thống, không thêm cột token riêng.
- **Revoke = soft delete** (`deletedAt`), không thêm field riêng. Hết hạn (`expiresAt`, tùy chọn) kiểm tra cùng lúc với revoke qua `ReportShareLink.isActive()`.
- **Tạo/thu hồi link yêu cầu quyền `report:share` mới** (nghiêm hơn `report:view` — ADMIN/PM dự án, không có MEMBER) vì đây là hành động public-facing thật sự khác về rủi ro so với chỉ xem báo cáo nội bộ.
- **Endpoint public** `GET /api/v1/public/shared-reports/{token}` — `permitAll` (cùng mẫu `/api/v1/public/webhooks/**`), có `RateLimitFilter` riêng (30 req/60s/IP, chống dò token). Lỗi luôn `404` đồng nhất cho cả 3 trường hợp (chưa tồn tại/đã thu hồi/đã hết hạn) — không phân biệt qua message, tránh lộ thông tin cho người dò token.
- **Tái dùng tối đa:** `ReportService.getProjectProgress()` đã có logic SQL tính progress theo RBAC actor — tách thêm `getProjectProgressByProjectId(projectId)` (không qua actor, vì quyền đã xác thực riêng qua token) dùng chung đúng 1 câu SQL, không viết lại.
- **Frontend:** quản lý link (tạo/copy/thu hồi) ngay trong tab "📈 Tiến độ dự án" ở màn hình Report (chỉ hiện khi có `report:share`); xem công khai ở route riêng `/public/shared-reports/:token` — **ngoài** `MainLayoutComponent`/`authGuard` (route đứng độc lập trong `app.routes.ts`, trước route `**`), trang sáng màu riêng (không dùng dark theme nội bộ) vì là trang gửi cho khách hàng/nhà đầu tư.

**File tạo mới:** `backend/.../db/migration/V25__add_report_share_links.sql` (bảng + seed quyền `report:share` cho ADMIN/PROJECT_MANAGER), `ReportShareLink.java`, `ReportShareLinkRepository.java`, `ReportShareLinkService.java`, `ReportShareLinkController.java`, `PublicReportController.java`, `dto/{ReportShareLinkCreateRequest,ReportShareLinkResponse,PublicProjectProgressResponse}.java`, `ReportShareLinkIntegrationTest.java` (4 test). Frontend: `report.service.ts` (+model, +4 API), `public-shared-report.component.{ts,html,scss}`.
**File cập nhật:** `ReportService.java` (+getProjectProgressByProjectId), `SecurityConfig.java` (+permitAll `/api/v1/public/shared-reports/**`), `RateLimitFilter.java` (+rule shared-report), `application.yml` (+rate-limit.shared-report), `app.routes.ts` (+route public), `report-list.component.ts/html/scss` (+UI quản lý link trong tab Tiến độ dự án), docs: `docs/api/12-report-audit-api.md` (+§3.8, renumber audit-logs 3.8→3.9), `docs/05-user-roles-permissions.md` (+`report:share`), `docs/07-requirement-traceability-matrix.md`.

**Kiểm tra:** `ReportShareLinkIntegrationTest` 4/4 PASS; `mvn test` toàn bộ backend 331/331 PASS; frontend `npm test` 73/73 PASS + `npm run build` thành công, không cảnh báo budget.

**GĐ4 (Hạ tầng/Observability) — HOÀN TẤT 3/3 hạng mục** (Actuator metrics, Backup/restore PostgreSQL, Stakeholder report share-link).
