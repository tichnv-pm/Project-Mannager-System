# Database 04 — Quy tắc database

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 05, `docs/04-business-rules.md`, `docs/design/06-logging-audit-design.md`

## 1. Quy tắc đặt tên

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Bảng | `snake_case`, số nhiều khi là tập hợp | `task_assignees` |
| Cột | `snake_case` | `actual_completed_at` |
| PK | `pk_<tên bảng>` | `pk_tasks` |
| FK | `fk_<bảng>_<cột>` | `fk_tasks_project_id` |
| UNIQUE | `uk_<bảng>_<các cột>` | `uk_tasks_code_active` |
| CHECK | `ck_<bảng>_<ý nghĩa>` | `ck_tasks_progress_range` |
| Index thường | `ix_<bảng>_<các cột>` | `ix_tasks_due_date` |

## 2. Quy tắc bất biến

1. Mọi PK là `uuid` (gen_random_uuid — không cần extension, có sẵn PostgreSQL ≥ 13).
2. Mọi ngày giờ là `timestamptz` lưu UTC; ngày kế hoạch (start/due/planned) dùng `date` (không nhạy múi giờ — xem `01-data-model.md` DB-03).
3. Mọi FK đều có index phục vụ join nếu bảng > vài trăm dòng (đã liệt kê `03-index-strategy.md`).
4. Cấm lưu danh sách ID dạng chuỗi/array text trong cột — M:N phải qua bảng mapping.
5. Cấm cột `is_deleted` kiểu boolean — dùng `deleted_at` (+`deleted_by`) để biết khi nào xóa.
6. Bảng mapping (`user_roles`, `role_permissions`, `project_members`, `task_assignees`, `task_watchers`, `task_tags`, `meeting_participants`): không version, không soft delete — xóa vật lý khi cần.
7. Bảng append-only (`audit_logs`, `refresh_tokens` theo vòng đời revoke, `notifications` đọc/ghi đơn giản): không version, không update phức tạp.
8. Enum: `varchar` + CHECK (DB-04); thêm giá trị mới → migration `ALTER TABLE ... DROP CONSTRAINT / ADD CONSTRAINT`.
9. **Project Planning (v1.1)**: 
   - `project_plans.parent_plan_id` chỉ DETAIL→MASTER (1 cấp) — service ép + CHECK partial.
   - `plan_versions` có snapshot (jsonb) nhưng dữ liệu hoạt động vẫn nằm ở bảng thật — snapshot dùng để diff & phục hồi tham chiếu.
   - `plan_baselines` bất biến — **cấm UPDATE/DELETE ghi đè** (chỉ soft delete nếu được chốt docs/planning/11 §6.4).
   - `plan_links` polymorphic (target_type + target_id) — không FK cứng vào bảng đích (như attachments task/meeting nullable).
   - Mọi thao tác scheduling (recalc) là phép tính phía service — DB chỉ lưu kết quả cuối (start/finish/is_critical) — tránh cột computed trùng lặp.

## 3. Migration (Flyway)

1. File: `V<version>__<mô tả>.sql` trong `backend/src/main/resources/db/migration`.
2. **Không bao giờ sửa file migration đã chạy** — chỉ tạo file mới.
3. Mỗi migration là 1 transaction (PostgreSQL DDL transactional — tận dụng; tách DDL dài thành nhiều migration nhỏ cho rõ).
4. Seed dữ liệu local: `V2__seed_local_data.sql` chỉ chạy profile `local` (cấu hình vị trí migration theo profile khi implement).
5. `database/schema.sql` + `database/seed-data.sql` là bản **tham chiếu** giữ đồng bộ với migration (Prompt 08 chuyển đổi chính thức).
6. Không dùng trigger tự động cập nhật `updated_at` — JPA `@PreUpdate`/Auditing xử lý; DB chỉ giữ `DEFAULT now()`.

## 4. Xóa dữ liệu

1. Xóa mềm: set `deleted_at = now(), deleted_by = <actor>`; mọi query mặc định lọc `deleted_at IS NULL`.
2. Không xóa vật lý dữ liệu nghiệp vụ khi đang vận hành; chỉ khi dọn dẹp có chủ đích (script riêng, có xác nhận).
3. Xóa mềm cha (task/project/meeting): con **giữ nguyên**, chỉ ẩn theo điều kiện truy vấn (task con theo `parent_task_id` hiển thị trong detail cha — đã xóa cha thì không truy cập được cha, con vẫn còn trong danh sách lọc project).
4. Bảng mapping: xóa vật lý khi gỡ quan hệ (VD gỡ thành viên dự án).

## 5. Sinh mã nghiệp vụ an toàn concurrent

1. Task code: `PRJ001-TASK-000001` — bộ đếm theo project (bảng `project_sequences` hoặc đếm trong transaction kèm lock) + UNIQUE partial làm lớp bảo vệ + retry.
2. Risk/issue code: bộ đếm toàn cục (`RSK000001`, `ISS000001`) — cùng cơ chế.
3. Retry: khi vi phạm unique (race), tăng giá trị bộ đếm và thử lại (tối đa N lần) trước khi báo lỗi `CODE_EXHAUSTED` (design 05).
4. Chi tiết: `docs/04-business-rules.md` mục 12; kiểm chứng test concurrent ở Prompt 11.

## 6. Bảo mật dữ liệu

1. `users.password_hash`: BCrypt — không có trigger/function nào đọc ra dưới dạng plaintext; cấm index trên nội dung.
2. `refresh_tokens.token_hash`: SHA-256 — không lưu plaintext.
3. `audit_logs.before_data/after_data`: không ghi password/token; trường nhạy cảm được che trước khi lưu (design 06 mục 5).
4. File: DB chỉ lưu metadata + path server sinh — không tin `file_name` từ user làm path.
5. Không tạo role DB production hơn mức cần: ứng dụng dùng 1 user DB có quyền đủ cho app (không `superuser`).

## 7. Đồng bộ docs ↔ code

1. Mọi thay đổi schema phải cập nhật: `docs/database/*` + `database/schema.sql` + migration tương ứng — đồng thời trong cùng commit.
2. Naming Entity ↔ bảng: JPA `@Table` tường minh `snake_case` (không dùng naming strategy mặc định mù — cấu hình `spring.jpa.hibernate.naming.physical-strategy` chuẩn hoặc viết rõ annotation).
