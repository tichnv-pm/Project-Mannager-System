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
