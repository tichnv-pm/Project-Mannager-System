# Environment Check — Kiểm tra môi trường máy local

> Ngày kiểm tra: 2026-08-01 | Prompt 07 (Skeleton). Máy: Windows (PowerShell).
> Cập nhật: 2026-08-03 — Docker daemon đã khởi động được; `docker compose up -d --build` kiểm chứng PASS trên Postgres thật (Prompt 23 Release).

## 1. Kết quả kiểm tra công cụ

| Công cụ | Yêu cầu dự án | Kết quả | Trạng thái |
|---|---|---|---|
| Java (JDK) | 21 | Temurin 21.0.12+8 (đã thêm PATH) | ✅ CÓ |
| Maven | 3.9+ | 3.9.16 (`C:\Users\tichnv1\tools\apache-maven-3.9.16`) | ✅ CÓ |
| Docker / Docker Compose | Desktop cho Windows | CLI 29.6.2 + Compose v5.3.1; daemon khởi động được bằng `Start-Process "Docker Desktop.exe"` — **đã kiểm chứng compose full stack PASS 2026-08-03** | ✅ CÓ (đã verify) |
| Git | (khuyến nghị) | Không tìm thấy `git` | ❌ CHƯA CÀI |
| PostgreSQL client (`psql`) | — | Không tìm thấy | ❌ CHƯA CÀI |
| Node.js | 24 | `v24.18.1` | ✅ CÓ |
| npm | 11+ | `11.16.0` | ✅ CÓ |

## 2. Việc đã làm được

| Bước | Lệnh | Kết quả |
|---|---|---|
| Scaffold Angular | `npx @angular/cli@latest new frontend ...` | ✅ Angular 22.1.0, zoneless |
| Build production | `cd frontend; npm run build` | ✅ Thành công (~27s, initial 215 kB) |
| Unit test | `cd frontend; npm test` | ✅ 1 test file, 2 tests pass (Vitest) |
| Backend compile + test | `cd backend; mvn clean test` | ✅ **BUILD SUCCESS** — 1 test (contextLoads) pass; Flyway V1 chạy trên H2 (MODE=PostgreSQL) |
| Backend package | `cd backend; mvn clean package` | ✅ (tương đương build success — chưa chạy lại riêng, chạy cùng `mvn clean test`) |

## 3. Vấn đề còn lại

1. **Docker daemon cần khởi động thủ công sau khi bật máy**: CLI 29.6.2 có; daemon chưa tự chạy khi boot — chạy `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` rồi chờ `docker version` trả Server version (đã kiểm chứng 2026-08-03).
2. **Local profile đã kiểm chứng trên Postgres thật** qua Docker Compose (Postgres 16-alpine, backend + nginx) — 2026-08-03. Chạy `mvn spring-boot:run -Dspring-boot.run.profiles=local` trực tiếp vẫn cần Postgres ở `localhost:5432` (chính là container compose).
3. **Git chưa cài** (không bắt buộc cho build).

## 4. Quyết định kỹ thuật phát sinh (Prompt 07 verify)

### 4.1 Test profile dùng migration H2 riêng

- `V1__init_schema.sql` (PostgreSQL) không chạy được trên H2 (thiếu `gen_random_uuid()`, `timestamptz`, `jsonb`, partial index, functional index).
- **Giải pháp**: bản H2-compatible đặt tại `backend/src/test/resources/db/test-migration/V1__init_schema.sql`; `application-test.yml` trỏ `spring.flyway.locations: classpath:db/test-migration`. Khác biệt với bản PostgreSQL:
  - `uuid DEFAULT RANDOM_UUID() PRIMARY KEY` (H2 yêu cầu `DEFAULT` trước `PRIMARY KEY`).
  - `timestamp with time zone` thay `timestamptz`.
  - `json` thay `jsonb` (audit_logs).
  - Bỏ partial index (`WHERE ...`) và functional index (`CAST(created_at AS DATE)`).
  - **Quy tắc**: khi sửa V1 PostgreSQL phải đồng bộ bản H2 tương ứng.
- Lưu ý H2 version resolve từ Spring Boot 3.5.4 BOM là **2.2.224** (không phải 2.3.x).

### 4.2 V2 seed local đã tạo

- `backend/src/main/resources/db/migration/V2__seed_local_data.sql` (sao từ `database/seed-data.sql`) — trước đây chỉ tồn tại ở `database/`, chưa có trong `db/migration`.
- Cơ chế chạy giữ nguyên: `application.yml` + `application-test.yml` có `spring.flyway.target: 1` (chỉ V1); `application-local.yml` `target:` rỗng (V1 + V2).
- Chưa kiểm chứng chạy trên Postgres thật (chờ Docker/Postgres local).

## 5. Prompt 08 — Backend foundation (2026-08-02)

Đã xây foundation theo `docs/design/02/04/05/06`:

**File tạo mới (`backend/src/main/java/com/example/pmdaily/`):**

| Package | File | Mục đích |
|---|---|---|
| `common` | `BaseEntity`, `BaseAuditEntity`, `SoftDeleteEntity` | id UUID + version; audit fields; soft delete |
| `common` | `PageResponse`, `ErrorCode`, `Constants`, `TimeUtil`, `ValidationGroups` | Phân trang thống nhất; catalog mã lỗi; hằng số; tiện ích thời gian; validation groups |
| `common` | `TraceIdFilter` | traceId mỗi request → MDC + header `X-Trace-Id` |
| `exception` | `ErrorResponse`, `FieldErrorItem`, `BusinessException`, `ResourceNotFoundException`, `ConflictException`, `GlobalExceptionHandler` | Error response thống nhất + ánh xạ exception (design 05 §2) |
| `config` | `JpaAuditingConfig`, `OpenApiConfig`, `WebConfig` (CORS), `SchedulingConfig` | Auditing; Swagger bearer; CORS từ env; @EnableScheduling |
| `security` | `UserPrincipal`, `JwtService`, `JwtAuthFilter`, `SecurityConfig` | JWT HS256 + fail-fast secret ≥ 32 ký tự; stateless chain; 401/403 JSON |
| `audit` | `AuditLog`, `AuditLogRepository`, `AuditService`, `AuditDataSanitizer`, `@Audited`, `AuditAspect` | Ghi audit_logs, che field nhạy cảm, AOP hỗ trợ |

**File cập nhật:** `application.yml` (logging pattern MDC), `AGENTS.md`, `docs/00-project-overview.md`.

**Test (26 tests, `mvn clean test` PASS):** `ErrorCodeTest`, `JwtServiceTest` (claims/expired/tampered/fail-fast), `GlobalExceptionHandlerTest` (400/403/404/409/500 + JSON shape), `SecuritySmokeIntegrationTest` (401 JSON, health public, 404 khi có token), `AuditDataSanitizerTest`, `PMDailyApplicationTests`. `mvn clean package` → jar OK.

**Lưu ý:** profile mặc định không có `JWT_SECRET` → fail-fast khi chạy thật (đúng thiết kế); profile test có secret mặc định.

## 6. Prompt 09 — Auth module (2026-08-02)

Đã xây module xác thực & tài khoản theo `docs/api/01-auth-api.md`, `docs/use-cases/UC-001-login.md`:

**File tạo mới (`backend/src/main/java/com/example/pmdaily/`):**

| Package | File | Mục đích |
|---|---|---|
| `user` | `UserStatus`, `User`, `Role`, `Permission` | Entity tài khoản, vai trò, quyền (JPA) |
| `user` | `UserRepository`, `RoleRepository`, `PermissionRepository` | Repository; `findByUsername`/`findById` kèm `@EntityGraph` (roles + permissions) |
| `user/dto` | `UserResponse` | Response user — không bao giờ chứa passwordHash (BR-AUTH-03) |
| `user/mapper` | `UserMapper` | MapStruct: User → UserResponse; roles/permissions gom danh sách code |
| `auth` | `RefreshToken`, `RefreshTokenRepository` | Refresh token lưu DB — chỉ lưu SHA-256 hash + rotation (`revokedAt`/`replacedBy`); `revokeAllByUserId` bulk |
| `auth/dto` | `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `ChangePasswordRequest`, `ResetPasswordRequest`, `TokenResponse` | DTO + Bean Validation |
| `auth` | `AuthService`, `AuthController` | Login (lock 5 lần/5 phút, BR-AUTH-08), refresh (rotation + reuse detection, BR-AUTH-09), logout idempotent, me, change/reset password |

**File cập nhật:** `ErrorCode` (thêm `ACCOUNT_LOCKED` → HTTP 423), `application.yml` (cấu hình lock qua `LOGIN_MAX_FAILED_ATTEMPTS`/`LOGIN_LOCK_DURATION_MINUTES`), `UserRepository` (bỏ `findByUsernameWithRoles`/`findByIdWithRoles` — Spring Data parse nhầm thành derived query; dùng `findByUsername`/`findById` + `@EntityGraph`).

**Test (Prompt 09: 32 tests — tổng `mvn clean test` 58 tests PASS):**
- `AuthServiceTest` (16 unit tests, Mockito): login success/fail/lock/inactive/not-found, refresh rotation/reuse/expired, logout, change/reset password.
- `AuthIntegrationTest` (16 tests, MockMvc + H2): full flow login/refresh/me/logout/change-password/reset-password, 400/401/403/423, reuse detection.

**Phát hiện & sửa bug quan trọng trong Prompt 09:**
- `@Transactional(noRollbackFor = BusinessException.class)` cho `login` và `refresh`: nếu không, exception nghiệp vụ làm **rollback** luôn cả việc ghi attempts/lock (BR-AUTH-08) và revoke chain (BR-AUTH-09) — thất bại im lặng ngoài đời thật.
- Test data setup: `SET REFERENTIAL_INTEGRITY FALSE` phải chạy trên **cùng connection** với các lệnh DELETE (`ConnectionCallback`), vì `users.updated_by` tự tham chiếu — nếu không H2 ném FK violation khi dọn dữ liệu giữa các test.

**Quyết định kỹ thuật:**
- Refresh token lưu dạng **hash SHA-256** (không lưu raw) — chỉ trả raw đúng 1 lần khi cấp.
- Login sai lần thứ 5 → khóa 5 phút + `failed_login_attempts` reset về 0 (khi hết khóa, không tính tiếp từ 5).
- `logout`/`refresh` với token không tồn tại → idempotent (không ném lỗi), không ghi audit.

## 7. Prompt 10 — Project module (2026-08-02)

Đã xây module dự án & thành viên theo `docs/api/04-project-api.md`, `docs/use-cases/UC-003-project-management.md`, `docs/use-cases/UC-004-member-management.md` (FR-PROJ-01..07, BR-PROJ):

**File tạo mới (`backend/src/main/java/com/example/pmdaily/project/`):**

| Package | File | Mục đích |
|---|---|---|
| `project` | `ProjectStatus`, `ProjectMemberRole` | Enum trạng thái dự án (5 giá trị), vai trò thành viên (7 giá trị) — đúng docs 02 mục 1.5/1.6 |
| `project` | `Project` | Entity dự án: code/name/description/status/startDate/endDate/projectManager/customerName/progress/note + members (OneToMany), soft delete |
| `project` | `ProjectMember` | Entity thành viên (bảng quan hệ — **không** có `version` trong schema nên không kế thừa `BaseEntity`; tự khai báo audit fields + `@EntityListeners`) |
| `project` | `ProjectRepository`, `ProjectMemberRepository` | `findById`/`findByCode` + `@EntityGraph`; `countOpenTasks` (native SQL trên bảng `tasks` — chưa có entity Task ở Prompt 10); `countGroupByProjectIds` (count 1 query cho list) |
| `project` | `ProjectSpecification` | Tìm kiếm: notDeleted + keyword (code/name/customerName) + status + projectManager + memberOf (join members) |
| `project/dto` | `ProjectCreateRequest`, `ProjectUpdateRequest`, `ProjectResponse`, `ProjectMemberRequest`, `ProjectMemberRoleRequest`, `ProjectMemberResponse` | DTO + Bean Validation; `ProjectUpdateRequest` kèm `version` bắt buộc (BR-GEN-08) |
| `project/mapper` | `ProjectMapper` | MapStruct: Project→ProjectResponse (projectManagerId), ProjectMember→ProjectMemberResponse (user.id/username/fullName/email) |
| `project` | `ProjectService`, `ProjectController` | 9 endpoints theo spec; kiểm tra kép quyền toàn cục (@PreAuthorize) + membership/PM dự án (service) |

**File cập nhật:** `ProjectRepository` (bỏ `existsByCodeIgnoreCase` — spec 04 mục 5.1: code **phân biệt hoa thường** → `existsByCode`), `ProjectMemberRepository` (dùng `findByProjectIdAndUser_Id` — Spring Data không parse `userId` thành `user.id`).

**Test (Prompt 10: 26 tests — tổng `mvn clean test` 84 tests PASS):**
- `ProjectIntegrationTest` (26 tests, MockMvc + H2): CRUD dự án, duplicate code (409), invalid date range (400), version conflict (409), soft delete (404 sau khi xóa), BR-PROJ-09 (dự án ACTIVE có task mở phải `confirm=true`), membership (thêm/đổi vai trò/xóa, 409 trùng, 400 PROJECT_MANAGER_REQUIRED khi xóa PM cuối), phân quyền kép (403 cho viewer/dev).

**Quyết định kỹ thuật:**
- PM dự án (thành viên có role `PROJECT_MANAGER`) + ADMIN hệ thống được manage/update/delete; MEMBER/VIEWER chỉ xem dự án mình tham gia (spec mục 1).
- Tạo dự án kèm `projectManagerId` → tự thêm PM làm thành viên (BR-PROJ-06); đổi PM trong update → thêm PM mới, giữ PM cũ (spec 3.4).
- `memberCount` map thủ công qua count query (không map `members.size` — tránh lazy-load N+1 trên list).
- Sort whitelist theo spec (`code, name, status, startDate, endDate, createdAt, progress`), ngoài whitelist → 400 VALIDATION_ERROR.
- Test data: role code dùng đúng `ADMIN`/`PROJECT_MANAGER` (production chuẩn, service check `roles.contains("ADMIN")`); permission code dùng chung qua `findByCode` (tránh unique violation giữa admin và pm cùng `project:view`); role `MEMBER` unique theo username (roles.code có unique constraint).

**Review sau Prompt 10 (86 tests PASS) — 3 bug đã sửa:**
1. **N+1 trong danh sách dự án**: `findAll(spec, pageable)` không có `@EntityGraph(projectManager)` → lazy-load từng dòng (vi phạm docs/design/02 mục 4). Đã override `findAll(Specification, Pageable)` + EntityGraph trong `ProjectRepository`.
2. **`page`/`size` không validate**: spec 04 mục 3.1 yêu cầu page ≥ 0, size 1–100 → `400 VALIDATION_ERROR`; trước đây `PageRequest.of(page=-1)` ném `IllegalArgumentException` → rơi vào 500. Đã thêm `validatePagination` trong `ProjectService.search`.
3. **Đổi PM khi PM mới đã là thành viên**: trước đây chỉ set `projectManager` mà không nâng role → người mới là PM dự án nhưng không có membership role `PROJECT_MANAGER` → không update/delete được dự án (BR-PROJ-06, spec 04 mục 3.4). Đã nâng role qua `ifPresentOrElse` (đã là member → nâng role; chưa → thêm mới).

## 8. Prompt 11 — Task module (2026-08-02)

Đã xây module công việc theo `docs/api/05-task-api.md` (FR-TASK-01..17, BR-TASK-01..17):

**File tạo mới (`backend/src/main/java/com/example/pmdaily/task/`):**

| Package | File | Mục đích |
|---|---|---|
| `task` | `TaskStatus`, `TaskPriority`, `TaskType`, `TaskSource` | Enum theo schema (6 trạng thái, 4 ưu tiên, 5 loại, 5 nguồn) |
| `task` | `Task` | Entity công việc (extends SoftDeleteEntity): code/project/parentTask/title/description/reporter/assignee/status/priority/type/source/dates/progress/blocked/estimate/notes |
| `task` | `Tag`, `TaskAssignee`, `TaskWatcher`, `TaskTag`, `TaskComment`, `Attachment` | Bảng quan hệ (không có `version` trong schema → không kế thừa BaseEntity, tự khai báo audit + `@EntityListeners`); unique uk theo schema |
| `task` | `TaskRepository`, `TagRepository`, `TaskAssigneeRepository`, `TaskWatcherRepository`, `TaskTagRepository`, `TaskCommentRepository`, `AttachmentRepository` | `findById`/`findAll(Spec, Pageable)`/danh sách con + `@EntityGraph`; `existsByCode`; count theo task |
| `task` | `TaskCodeGenerator` | Sinh mã `PRJ001-TASK-000001` (BR-TASK-14, docs/04 mục 12): bộ đếm `project_sequences`, retry 5 lần, collision check `existsByCode`, hết → `CODE_EXHAUSTED` |
| `task` | `TaskSpecification` | notDeleted + keyword (title/code) + project/assignee/status/priority/type/tag (subquery) + date ranges + overdue + blocked + memberOf (subquery ProjectMember) |
| `task/dto` | `TaskCreateRequest`, `TaskUpdateRequest`, `StatusUpdateRequest`, `ProgressUpdateRequest`, `BlockerUpdateRequest`, `AssigneeUpdateRequest`, `UserIdsRequest`, `TagIdsRequest`, `CommentRequest`, `TaskResponse`, `TaskSummaryResponse`, `CommentResponse`, `AttachmentResponse`, `UserBriefResponse`, `TagBriefResponse`, `TaskHistoryEntry` | DTO + Bean Validation; update kèm `version` bắt buộc (BR-GEN-08) |
| `task/mapper` | `TaskMapper` | MapStruct: Task→Response/Summary (tags/collaborators/watchers/counts), Comment/Attachment → Response (gán author/uploadedBy) |
| `task` | `TaskService` (~1000 dòng), `TaskController` | 25 endpoints theo spec: CRUD, search lọc 13 tiêu chí, my-tasks/today/overdue, export Excel (POI XSSFWorkbook, ≤ 10.000 rows), assignee/status/progress/blocker, children, tags/collaborators/watchers, comments, attachments (upload/download/delete, whitelist mime, max 10MB), history (đọc audit_logs, diff before/after → changes) |

**File cập nhật:** `backend/pom.xml` (thêm `org.apache.poi:poi-ooxml:5.2.5`), `application.yml` (multipart max-file-size 10MB), `application-test.yml` (`app.storage.path: target/test-uploads`), `AuditLogRepository` (thêm `findByEntityTypeAndEntityIdOrderByCreatedAtAsc`).

**Test (Prompt 11: 47 tests — tổng `mvn clean test` 133 tests PASS):**
- `TaskIntegrationTest` (47 tests, MockMvc + H2): tạo (mã tự sinh PRJ100-TASK-000001, NOT_PROJECT_MEMBER, PARENT_TASK_PROJECT_MISMATCH, BLOCKER_REASON_REQUIRED, INVALID_DATE_RANGE), chi tiết/403/404, list lọc status/keyword/sort/pageSize, update (409 CONFLICT version, PROGRESS_REQUIRED_FOR_DONE, member chỉ sửa task mình), delete (chặn xóa cha còn con 400 BR-TASK-17, 403), assignee, state machine (TODO→IN_PROGRESS→REVIEW→DONE, INVALID_STATUS_TRANSITION, BLOCKED), progress, blocker, children, tags/collaborators/watchers, comments (CRUD/403/validation), attachments (upload/list/download/delete, 400 exe, 413 >10MB), history (TASK_CREATED + TASK_STATUS_CHANGE với changes.status.from/to), export (xlsx, member 403), my-tasks/today/overdue, mã song song theo project.

**Quyết định kỹ thuật:**
- **BR-TASK-17**: chặn xóa cha khi còn task con → `400 BAD_REQUEST` "Công việc còn N công việc con, không thể xóa" (đúng mặc định v1 của spec mục 3.6 — yêu cầu xóa con trước).
- **State machine** tuân đúng sơ đồ spec 05 mục 1.1: chỉ TODO→IN_PROGRESS→REVIEW→DONE; REVIEW→IN_PROGRESS (review lại); BLOCKED từ mọi trạng thái (bắt buộc blockerReason, trừ DONE/CANCELLED); CANCELLED từ mọi trạng thái; ngoài sơ đồ → 400 INVALID_STATUS_TRANSITION.
- History: audit status/progress/assignee/blocker ghi **beforeData/afterData** theo field (vd `{"status": "TODO"}` → `{"status": "IN_PROGRESS"}`) để diff ra `changes.{field}.{from,to}` đúng schema `TaskHistoryEntry`.
- Sinh mã dùng JdbcTemplate `UPDATE ... SET task_seq = task_seq + 1 WHERE project_id=?` (row-lock) + `INSERT ... VALUES (?, 1)` bắt DuplicateKey → retry. **Không dùng** `INSERT ... ON CONFLICT ... RETURNING` của PostgreSQL — H2 không hỗ trợ → bad SQL grammar trên test profile.
- Quyền: MEMBER chỉ cập nhật task mình là assignee (status/progress/notes; trường khác bỏ qua); assignee/collaborators/watchers phải là member dự án → 400 NOT_PROJECT_MEMBER; list/export: ADMIN/PM xem tất cả, member khác chỉ xem dự án mình tham gia (memberOf subquery).

**Bug đã sửa trong Prompt 11:**
1. **NPE list task**: `TaskSpecification.blocked(boolean)` là primitive nhưng search() truyền `null` qua ternary → auto-unbox NPE khi không gửi `blocked`. Đã đổi thành `blocked == null ? null : TaskSpecification.blocked(blocked)`.
2. **List luôn rỗng**: `TaskSpecification.overdue(LocalDate)` luôn trả spec không-null → khi không request, Hibernate bind `due_date < null` → 0 rows. Đã trả `null` khi `today == null` (giống các optional spec khác).
3. **TaskCodeGenerator trên H2**: `ON CONFLICT DO UPDATE ... RETURNING` là cú pháp PostgreSQL — H2 ném bad SQL grammar → 5 retry → CODE_EXHAUSTED 500. Đã chuyển sang UPDATE-then-INSERT portable (xem quyết định kỹ thuật).
4. History thiếu field diff: audit ghi flat `{from,to}` → đổi sang before/after theo field.

## 9. Prompt 12 — Meeting module (2026-08-02)

Đã xây module cuộc họp theo `docs/api/06-meeting-api.md` (FR-MEET-01..07, BR-MEET-01..06):

**File tạo mới (`backend/src/main/java/com/example/pmdaily/meeting/`):**

| Package | File | Mục đích |
|---|---|---|
| `meeting` | `MeetingStatus` | Enum 4 trạng thái: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED (schema `ck_meetings_status`) |
| `meeting` | `Meeting` | Entity cuộc họp (extends SoftDeleteEntity): project/title/startTime/endTime/location/meetingLink/chairperson/status/agenda/content/conclusion |
| `meeting` | `MeetingParticipant` | Bảng quan hệ (không version trong schema → tự khai báo audit + `@EntityListeners`); UNIQUE (meeting_id, user_id) theo schema |
| `meeting` | `MeetingRepository`, `MeetingParticipantRepository` | `findById`/`findAll(Spec, Pageable)` + `@EntityGraph` (project + chairperson); `findByMeetingIdOrderByCreatedAtAsc`, `deleteByMeetingId`, `existsByMeetingIdAndUser_Id` |
| `meeting` | `MeetingSpecification` | notDeleted + keyword (title) + project + status + timeRange (startTime) + memberOf (subquery ProjectMember) |
| `meeting/dto` | `MeetingCreateRequest`, `MeetingUpdateRequest`, `MeetingCompleteRequest`, `MeetingParticipantsRequest`, `MeetingResponse` | DTO + Bean Validation; update kèm `version` bắt buộc (BR-GEN-08) |
| `meeting/mapper` | `MeetingMapper` | MapStruct: Meeting→Response (chairperson/participants/attachments map thủ công trong service — tránh lazy-load N+1) |
| `meeting` | `MeetingService`, `MeetingController` | 11 endpoints theo spec: list (lọc keyword/projectId/status/fromTime/toTime, sort whitelist `title, startTime, endTime, status, createdAt`), today (startTime thuộc hôm nay UTC, status ≠ CANCELLED), create, get, update (409 version), complete, participants, delete (soft), attachments (list/upload/delete) + **download** (mở rộng v1 để `AttachmentResponse.filePath` hoạt động) |

**File cập nhật:**
- `task/Attachment.java` + `task/AttachmentRepository.java`: thêm `meeting` (ManyToOne `meeting_id`) + `findByMeetingIdAndDeletedAtIsNullOrderByCreatedAtAsc` — dùng chung bảng `attachments` cho task và cuộc họp (schema `ck_attachments_owner`).

**Test (Prompt 12: 30 tests — tổng `mvn clean test` 163 tests PASS):**
- `MeetingIntegrationTest` (30 tests, MockMvc + H2): tạo (201, mặc định SCHEDULED, participants), BR-MEET-01 endTime ≤ startTime → 400, BR-MEET-02/03 chairperson/participant ngoài project → 400 NOT_PROJECT_MEMBER, trùng participant → 400, BR-MEET-04 thiếu cả location và link → 400, member không có `meeting:manage` → 403, get chi tiết (attachments)/403/404, list lọc keyword+status (outsider thấy 0), fromTime > toTime → 400 INVALID_DATE_RANGE, sort ngoài whitelist → 400, today (loại CANCELLED, chỉ hôm nay), update (đổi participants, version cũ → 409), BR-MEET-06 hủy rồi đổi trạng thái khác → 400, complete (chủ trì không có quyền manage vẫn complete được — 200; member khác → 403; thiếu conclusion → 400; họp CANCELLED → 400), participants add/remove (trùng → 400, ngoài project → 400), delete (204 + 404 sau đó; member → 403), attachments (upload/list/download/delete, sizeBytes UTF-8 15, 413 > 10MB, 400 mime lạ, member upload → 403).

**Quyết định kỹ thuật:**
- **complete** (`PUT /meetings/{id}/complete`): controller chỉ yêu cầu `meeting:view`; service kiểm tra `meeting:manage` HOẶC người gọi là chủ trì họp (spec mục 3.6, 05-user-roles-permissions §4) — member là chủ trì không có quyền manage vẫn hoàn thành được.
- **BR-MEET-06**: CANCELLED là trạng thái cuối — update họp đã hủy sang trạng thái khác → 400 BAD_REQUEST; họp đã hủy không complete được.
- **BR-MEET-01** dùng `BAD_REQUEST` (có `ck_meetings_time` CHECK trong schema); sort whitelist theo spec 06 mục 3.1.
- **Attachments của meeting**: dùng chung bảng `attachments` + rule 10MB/whitelist mime như task; lưu tại `{storage}/meetings/{meetingId}/`; download URL `/api/v1/meetings/{id}/attachments/{attachmentId}/download`.
- **Update họp**: yêu cầu đầy đủ field như create (spec: `MeetingUpdateRequest = Create + version`); không cho đổi dự án (400 BAD_REQUEST); `participantIds = null` → giữ nguyên người tham gia hiện tại.
- **actionItems trong MeetingResponse** chưa điền (module Action Item thuộc Prompt 13) — field được bỏ khỏi response v1, sẽ bổ sung cùng Prompt 13 (không trả dữ liệu rỗng giả).
- **Notification `MEETING_INVITED`** (hậu điều kiện tạo họp) chưa gửi — module Notification chưa tồn tại, ghi nhận để triển khai ở Prompt Notification.
- **today** tính theo ngày UTC (giống TaskService.today dùng LocalDate.now()) — đồng nhất v1; NFR-TZ theo timezone user chờ Prompt liên quan.

## 10. Checklist Prompt 07

- [x] Kiểm tra môi trường (kết quả mục 1)
- [x] Skeleton root: `.gitignore`, `README.md`, `.env.example`, `docker-compose.yml`
- [x] Skeleton Backend: `pom.xml`, main class, 3 profile config, test class
- [x] Dockerfile backend + frontend, `frontend/nginx.conf`
- [x] Skeleton Frontend: Angular 22 scaffold, build + test pass
- [x] Backend compile + test pass (2026-08-02, sau khi cài JDK 21 + Maven 3.9.16)
- [x] `docker compose up -d --build` kiểm chứng (2026-08-03, Prompt 23 Release): postgres/backend/frontend Up, backend + postgres Healthy; smoke E2E 15/15 PASS — chi tiết `docs/release/01-test-plan.md`
