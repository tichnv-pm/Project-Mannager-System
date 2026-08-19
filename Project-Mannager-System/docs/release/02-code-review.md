# PM Daily — Kết quả Review mã nguồn (v1.0.0)

> Nguồn: Prompt 23 (Release). Cập nhật: 2026-08-03.
> Phạm vi: toàn bộ `backend/` (Java 21 + Spring Boot 3) và `frontend/` (Angular 22) sau Prompt 22/23.

## 1. Checklist tổng thể

| # | Hạng mục | Kết quả | Ghi chú |
|---|---|---|---|
| 1 | Code compile & test pass trước khi chuyển bước | ✔ | 216 BE + 15 FE + build cả 2 stack |
| 2 | Không TODO/FIXME/HACK thay chức năng | ✔ | Chỉ có chuỗi "TODO" thuộc domain task status |
| 3 | Không hard-code secret trong code | ✔ | Toàn bộ đọc từ env; `application.yml` chỉ giá trị mặc định an toàn; `.env` không commit |
| 4 | Không trả Entity qua API | ✔ | 100% DTO + MapStruct |
| 5 | Không xóa/skip test để build pass | ✔ | Không có test bị skip (0 skipped) |
| 6 | Không mock dữ liệu production | ✔ | Seed chỉ chạy profile `local` (Flyway `target`), profile `test` có seed riêng |
| 7 | Audit log cho hành động quan trọng | ✔ | AOP `AuditAspect` + sanitizer dữ liệu nhạy cảm |
| 8 | Validation backend là nguồn chính | ✔ | Bean Validation + `@Valid` trên toàn bộ request |
| 9 | Optimistic locking | ✔ | `version` trên BaseEntity; conflict → 409 (kiểm chứng bằng test) |
| 10 | Xử lý lỗi thống nhất | ✔ | `GlobalExceptionHandler` + `ErrorResponse` chuẩn (`traceId`/`fieldErrors`) |
| 11 | Phân quyền đầy đủ | ✔ | Method security `@PreAuthorize` + route guard FE + `*appHasPermission` |

## 2. Review Backend

### 2.1 Kiến trúc

- Modular Monolith đúng chuẩn: mỗi module `controller/service/repository/entity/dto/mapper/specification` (modul `user` có thêm `UserSpecification` mới).
- Sinh mã task chống race: đã có test concurrent; mã tự sinh có lock (Prompt 11, `TaskService`).
- Dashboard/Report aggregate tại DB (query `GROUP BY`), không N+1 trên luồng chính.

### 2.2 Bảo mật

- JWT access 15 phút + refresh 7 ngày lưu DB, rotation + reuse detection (`JwtServiceTest`, `AuthIntegrationTest`).
- Login lock 5 lần/5 phút (BR-AUTH-08) — có test.
- `UserResponse` tuyệt đối không chứa `passwordHash` (BR-AUTH-03) — có test.
- Reset password chỉ ADMIN có `user:manage`.
- Password policy: ≥8 ký tự, có hoa/thường/số/ký tự đặc biệt, max 72 (BCrypt).
- Nội dung nhạy cảm trong audit log được sanitize (`AuditDataSanitizer`, 5 tests).

### 2.3 Chất lượng code

- Không phát hiện TODO/FIXME/HACK.
- Ngoại lệ nghiệp vụ dùng `BusinessException(ErrorCode, message)` nhất quán.
- `PageResponse` dùng chung toàn hệ thống; sort whitelist chống injection (UserAdmin sort `passwordHash` → 400 VALIDATION_ERROR — có log từ smoke).
- Flyway: `V1` schema + `V2` seed local (chỉ profile `local`); `ddl-auto: validate` chặn lệch entity/schema.

### 2.4 Vấn đề ghi nhận (không chặn release)

| ID | Vấn đề | Mức | Cách xử lý |
|---|---|---|---|
| B-01 | Hibernate warning `firstResult/maxResults specified with collection fetch; applying in memory` ở 1 truy vấn user list (entity graph roles) | Thấp | Chấp nhận ở v1 (dữ liệu ít); xem xét batch fetch / count query riêng khi scale |
| B-02 | `update` user không validate `roleIds` trước khi gán (chỉ bắt ở FK) | Thấp | FK violation trả 400 qua handler; cải thiện message khi cần |
| B-03 | Report export chưa có giới hạn `size` cho file CSV rất lớn | Thấp | Giới hạn dữ liệu seed/local; ghi vào NFR cho v1.1 |

## 3. Review Frontend

### 3.1 Kiến trúc

- Standalone components + signals, không NgRx (đúng quyết định); service + RxJS.
- `ReportService`/`AdminService` tách riêng theo module — gọi đúng 6/7 endpoint mới.
- Guard + directive permission đồng bộ với backend (32 permission codes seed khớp catalog FE — có test).

### 3.2 Chất lượng

- Template type-check AOT chạy sạch (bắt lỗi kiểu ngay build).
- Không dùng `any` trong template (đã sửa `tab.key as any` → typed array).
- Chống double submit: nút submit disabled khi `submitting` (report export, user modal, role save).
- Download file dùng Blob URL + revoke đúng cách; upload có size/loading state.
- Optimistic locking: Admin UI dùng `version` trả về từ response khi sửa user/đổi status.

### 3.3 Vấn đề ghi nhận (không chặn release)

| ID | Vấn đề | Mức | Cách xử lý |
|---|---|---|---|
| F-01 | `task-list.component.scss` vượt budget 16 kB (+0.3 kB), `task-detail.component.scss` (+2.2 kB) | Thấp | Cảnh báo build, không chặn; gộp SCSS/giảm lặp ở v1.1 |
| F-02 | `admin-panel` `switchTab` chưa cache dữ liệu (reload mỗi lần đổi tab) | Thấp | Chấp nhận v1 (dữ liệu nhỏ); thêm cache khi cần |
| F-03 | Chưa có E2E framework (Playwright/Cypress) | Trung bình | Nằm ngoài phạm vi v1; kịch bản thủ công trong `01-test-plan.md` mục 7 |

## 4. Review tài liệu

- `docs/api/02-user-admin-api.md`: đã bổ sung `version` vào ví dụ `UserResponse` (khớp code).
- `docs/00-project-overview.md`: trạng thái Prompt 22 ✔, Prompt 23+ phân rõ đã làm/còn lại.
- `AGENTS.md`: lệnh chuẩn (`npm test` cho Vitest, không dùng `npx vitest` trực tiếp).
- Toàn bộ spec/API còn lại không lệch so với code (kiểm chứng qua test + smoke).

## 5. Kết luận

Không có vấn đề chặn release. 7 ghi nhận mức Thấp/Trung bình được theo dõi ở v1.1 (Backlog). Mã nguồn sẵn sàng đóng gói theo `03-release-notes.md`.
