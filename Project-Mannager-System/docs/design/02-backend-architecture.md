# Design 02 — Kiến trúc Backend

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 04, `docs/02-functional-requirements.md`, `docs/04-business-rules.md`

## 1. Tổ chức package

```
com.example.pmdaily
├── config/          # OpenAPI, JpaAuditing, Scheduling, CORS, WebConfig
├── security/        # JwtService, JwtAuthFilter, SecurityConfig, UserPrincipal, PermissionEvaluator
├── common/          # BaseEntity, BaseAuditEntity, PageResponse, ApiResponse, ErrorCode, Constants, TimeUtil, ValidationGroups
├── exception/       # GlobalExceptionHandler, BusinessException, ResourceNotFoundException, ConflictException, ErrorResponse
├── audit/           # AuditEntity, AuditService, AuditAspect (@Audited), AuditLogRepository
├── auth/            # controller/service/repository/entity/dto/mapper — AuthModule
├── user/            # User, Role, Permission entities + repos + services + dtos + mappers
├── project/         # Project, ProjectMember
├── task/            # Task, TaskComment, Attachment, TaskHistory, TaskCodeGenerator
├── meeting/         # Meeting, MeetingParticipant
├── action-item/     # ActionItem
├── risk/            # Risk
├── issue/           # Issue
├── milestone/       # Milestone
├── notification/    # Notification, NotificationScheduler
├── dashboard/       # DashboardService (đọc số liệu)
├── report/          # ReportService, ExcelExportService
└── audit/           # (đọc audit log — Admin)
```

Mỗi module nghiệp vụ có đủ: `controller / service / repository / entity / dto / mapper / specification` (chỉ tạo khi cần — không tạo class rỗng).

## 2. Trách nhiệm từng lớp

| Lớp | Trách nhiệm | Cấm làm |
|---|---|---|
| `Controller` | Nhận request, gọi service, trả DTO; chỉ giữ logic HTTP (status code, header) | Không chứa logic nghiệp vụ, không truy cập repository |
| `Service` | Nghiệp vụ, transaction boundary (`@Transactional`), phối hợp repository, sinh mã, kiểm tra quyền phạm vi dữ liệu | Không xử lý HTTP, không trả Entity cho controller |
| `Repository` | Spring Data JPA + Specification; query aggregate nặng dùng native query khi cần | Không chứa logic nghiệp vụ |
| `Entity` | Mapping bảng; extend `BaseAuditEntity`; enum dạng string; quan hệ Lazy | Không serialize qua API |
| `DTO` | Request DTO (validation) / Response DTO (ổn định theo API); không dùng chung Entity | Không chứa logic |
| `Mapper` | MapStruct: Entity ↔ DTO; dùng `uses` cho mapper lồng | Không gọi service/repository |
| `Specification` | Xây dựng điều kiện lọc động; whitelist field | Không viết SQL string |

## 3. Base entity & audit

- `BaseEntity`: `id (UUID)`, `version (long)` — optimistic locking `@Version`.
- `BaseAuditEntity extends BaseEntity`: `createdAt, createdBy, updatedAt, updatedBy` — `@EntityListeners(AuditingEntityListener.class)` + `JpaAuditingConfig` cấp `AuditorAware` từ `UserPrincipal`.
- `SoftDeleteEntity` (khi cần): thêm `deletedAt, deletedBy` — mọi query mặc định lọc `deleted_at IS NULL` qua `@Where` hoặc repository helper (chọn 1 cách thống nhất, ưu tiên `@Where` để tránh quên).

## 4. Quy ước DTO & MapStruct

- `XxxRequest` (create/update), `XxxResponse`, `XxxSummaryResponse` (list).
- Create/Update tách riêng nếu khác trường; `version` chỉ ở Update.
- Mapper: `@Mapper(componentModel = "spring")`, đặt trong package `mapper` của module.
- Trong Service: chỉ thao tác Entity + Repository; chuyển đổi tại biên qua Mapper.
- Không map lazy collection trực tiếp — dùng DTO chứa `id + name` (VD `assignee: {id, fullName}`).

## 5. Validation

- Bean Validation (`jakarta.validation`) trên Request DTO; message tiếng Việt trong `messages.properties` (hoặc annotation message inline).
- Validation groups: `Create.class` / `Update.class` (VD `version` chỉ bắt buộc khi update).
- Nghiệp vụ (BLOCKED ⇒ reason, DONE ⇒ 100...) nằm ở Service, ném `BusinessException` với `ErrorCode`.
- Custom validator khi cần (VD `@ValidMeetingTime`, `@ValidTaskDates`).

## 6. Exception & error response

Chi tiết: `docs/design/05-error-handling-design.md`. Tóm tắt:
- `GlobalExceptionHandler` bắt: `MethodArgumentNotValidException` (400), `ConstraintViolationException` (400), `BusinessException` (mã hóa), `ResourceNotFoundException` (404), `OptimisticLockingFailureException` (409), `AccessDeniedException` (403), `AuthenticationException` (401), còn lại (500, ẩn stack trace).
- Error response: `timestamp, status, error, code, message, path, fieldErrors, traceId`.

## 7. Pagination, sort, search/filter

- Request: `page` (≥ 0, mặc định 0), `size` (1–100, mặc định 20), `sort` (`field,asc|desc` — **whitelist theo từng endpoint**).
- Trả: `PageResponse { content, page, size, totalElements, totalPages, hasNext, hasPrevious }` — nhất quán toàn app (không trả `org.springframework.data.domain.Page` trực tiếp).
- Filter động: `XxxSpecification` build `Specification<T>` từ các param; mọi param được whitelist + kiểm tra kiểu.
- Cột `deleted_at IS NULL` luôn được nối vào specification (hoặc `@Where`).

## 8. Transaction & concurrency

- `@Transactional` ở tầng Service (read-only cho query).
- Cập nhật: load entity có `version`, lưu lại → `OptimisticLockingFailureException` → 409.
- Sinh mã task/risk/issue: trong cùng transaction với INSERT, dùng unique constraint DB + retry khi conflict (xem `docs/04-business-rules.md` mục 12).

## 9. Dashboard & report (aggregate tại DB)

- `DashboardService` dùng repository query aggregate: `COUNT ... GROUP BY status/priority`, `MIN/MAX/AVG` cho projectProgress.
- Không load toàn bộ task vào bộ nhớ để tính — cấm N+1; review bằng test query + log SQL khi cần.
- Query thời gian: luôn chuyển đổi theo timezone người dùng tại tầng gọi (UTC trong DB).
- Export Excel: sinh file trong luồng không giữ bộ nhớ lớn; giới hạn 10.000 dòng.

## 10. Notification & scheduled job

- Sinh notification qua service trong cùng transaction của hành động nghiệp vụ (giao task, comment, họp, AI).
- `NotificationScheduler` (`@Scheduled`, cố định giờ, config) tìm task sắp đến hạn/quá hạn; dedupe theo `(recipientId, type, taskId, ngày)` — kiểm tra tồn tại trước khi insert; có unique index hỗ trợ.
- Job chạy theo giờ UTC; "hôm nay" tính theo timezone user khi hiển thị.

## 11. Cấu hình & profile

| Profile | Cấu hình |
|---|---|
| mặc định | Giá trị an toàn (VD JWT secret phải từ env — không có secret → fail fast khi start) |
| `local` | `application-local.yml`: DB local (docker postgres), seed data, CORS mở cho dev origin |
| `test` | `application-test.yml`: DB test (H2 tương thích PostgreSQL hoặc Testcontainers — chốt khi implement) |

- Mọi secret: `DB_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, `CORS_ALLOWED_ORIGINS`... đọc từ `EnvironmentVariable`/`application.yml` placeholder.
- Flyway: `V1__init_schema.sql`, `V2__seed_local_data.sql` (seed chỉ cho profile local qua config `spring.flyway.locations` theo profile hoặc `V2` đi kèm điều kiện).

## 12. Test chiến lược

| Tầng | Kỹ thuật | Nội dung |
|---|---|---|
| Unit test | JUnit 5 + Mockito | Service (rule nghiệp vụ, sinh mã, permission), Mapper (map đúng field), Specification |
| Integration test | `@SpringBootTest` + profile test | Controller + repository + security (403/401), Flyway migration, optimistic locking (409), soft delete (404) |
| Query test | Testcontainers hoặc H2 PG-mode (chốt khi implement) | Dashboard/report aggregate, N+1 check |
| Concurrent test | Test nhiều luồng | Sinh mã task không trùng; update version conflict |
