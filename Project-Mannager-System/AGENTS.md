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
