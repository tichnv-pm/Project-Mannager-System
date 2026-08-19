# Planning 04 — Vai trò & phân quyền Project Planning (Permissions)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn chính từ Prompt Project Planning Requirement + `docs/05-user-roles-permissions.md`.
> Quy ước: giữ mô hình 2 cấp hiện tại (vai trò hệ thống + vai trò trong dự án); bổ sung nhóm quyền `plan:*`.

## 1. Nguyên tắc chung

- Tất cả quyền planning thêm vào catalog **32 permission hiện có** → mở rộng thành **40+** (xem docs/05).
- Vai trò dùng chính: `ADMIN` (toàn quyền), `PROJECT_MANAGER` (quản lý kế hoạch dự án của mình), `PROJECT_MEMBER` (được gán task, cập nhật progress/effort thực tế của task mình), `VIEWER` (chỉ xem).
- Quyền theo **phạm vi dự án**: PM chỉ thao tác plan của dự án mình là PM; MEMBER/VIEWER chỉ xem plan của dự án mình tham gia (giống quy tắc `project:view`).
- Portfolio: ADMIN/PM xem toàn bộ; MEMBER/VIEWER xem theo dự án họ tham gia.
- **Kiểm tra kép**: method security (`@PreAuthorize`) + service-level membership/PM check (đúng chuẩn hiện tại).

## 2. Danh sách quyền mới (Permission codes)

| Quyền | Mô tả |
|---|---|
| `plan:view` | Xem plan, WBS, Gantt, baseline, portfolio summary |
| `plan:create` | Tạo plan mới (master/detail) |
| `plan:update` | Sửa WBS, task, dependency, calendar của plan |
| `plan:delete` | Xóa mềm plan |
| `plan:approve` | SUBMITTED → APPROVED; kích hoạt ACTIVE; tạo baseline |
| `plan:version` | Tạo phiên bản plan mới (snapshot) |
| `plan:baseline` | Tạo baseline + xem variance |
| `plan:change` | Tạo/duyệt change history sau APPROVED |
| `plan:resource` | Gán resource, chỉnh capacity, xem workload |
| `plan:template` | Quản lý template (CRUD/version/clone) |
| `plan:link` | Tạo/xóa liên kết plan_links tới execution/issue/risk/... |
| `plan:schedule` | Trigger recalc / xem warnings & critical path |

## 3. Ma trận vai trò hệ thống × quyền mới

| Quyền | ADMIN | PROJECT_MANAGER | PROJECT_MEMBER | VIEWER |
|---|---|---|---|---|
| `plan:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `plan:create` | ✔ | ✔ (PM dự án) | — | — |
| `plan:update` | ✔ | ✔ (PM dự án) | — | — |
| `plan:delete` | ✔ | ✔ (PM dự án) | — | — |
| `plan:approve` | ✔ | ✔ (PM dự án) | — | — |
| `plan:version` | ✔ | ✔ (PM dự án) | — | — |
| `plan:baseline` | ✔ | ✔ (PM dự án) | — | — |
| `plan:change` | ✔ | ✔ (PM dự án) | — | — |
| `plan:resource` | ✔ | ✔ (PM dự án) | — | — |
| `plan:template` | ✔ | — | — | — |
| `plan:link` | ✔ | ✔ (PM dự án) | — | — |
| `plan:schedule` | ✔ | ✔ (PM dự án) | — | — |

> Ghi chú:
> - PROJECT_MEMBER: quyền `plan:view` + quyền cập nhật **giới hạn** trên task mình được gán: cập nhật `actualStart/actualFinish/actualEffortMinutes/percentComplete/status` (không sửa lịch planned, không đổi parent/dependency).
> - PM không phải ADMIN vẫn quản lý được plan của dự án mình là PROJECT_MANAGER trong `project_members` (quy tắc "(PM dự án)" — giống docs/05).
> - ADMIN toàn quyền mọi dự án kể cả không phải thành viên.

## 4. Traceability — Chức năng × Vai trò

| Chức năng | ADMIN | PROJECT_MANAGER | PROJECT_MEMBER | VIEWER |
|---|---|---|---|---|
| Xem portfolio / master / detail / Gantt | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| Tạo/sửa/xóa plan | ✔ | ✔ (PM dự án) | — | — |
| Soạn WBS, dependency, schedule recalc | ✔ | ✔ (PM dự án) | — | — |
| Tạo baseline & so sánh variance | ✔ | ✔ (PM dự án) | — | — |
| Duyệt change history sau APPROVED | ✔ | ✔ (PM dự án) | — | — |
| Gán resource / xem workload / capacity | ✔ | ✔ (PM dự án) | — (chỉ xem workload của mình) | — |
| Cập nhật actual trên task được gán | ✔ | ✔ | ✔ (task gán cho mình, giới hạn field) | — |
| Quản lý template | ✔ | — | — | — |
| Tạo liên kết plan_links | ✔ | ✔ (PM dự án) | — | — |
| Xem critical path / warnings | ✔ | ✔ | ✔ (xem) | ✔ (xem) |

## 5. Điểm đã chốt (2026-08-07)

1. ~~PROJECT_MEMBER có được cập nhật actual ở v1~~ → **CÓ**, giới hạn field (`actualStart/actualFinish/actualEffortMinutes/percentComplete/status`) trên task được gán.
2. ~~Ai duyệt change history~~ → **Dual-approve**: PM dự án + ADMIN cho plan effort ≥ 10,000 phút (config `planning.change.dualApproveThresholdMinutes`); plan nhỏ PM duyệt đủ.
3. ~~`plan:template` cho PM clone~~ → **Có**: PM được clone template (chỉ đọc); CRUD template chỉ ADMIN.
4. ~~Portfolio quyền riêng~~ → **Dùng chung `plan:view`**.
5. ~~Baseline cần bước duyệt riêng~~ → **Không**: PM tạo baseline trực tiếp khi APPROVED.
6. Bổ sung: quyền `plan:recalc` không tách riêng — dùng `plan:schedule` trigger recalc (đã chốt docs/planning/03 mục 14 #6).