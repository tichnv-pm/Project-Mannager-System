# 05 — Vai trò người dùng & phân quyền (User Roles & Permissions)

> Dự án: PM Daily Work Management
> Mô hình: 2 cấp — **vai trò hệ thống** (system role, toàn cục) + **vai trò trong dự án** (project member role, theo từng dự án).
> Permission-based authorization: người dùng thực hiện hành động khi có quyền; quyền phát sinh từ vai trò hệ thống, kết hợp kiểm tra phạm vi dự án (project membership).

## 1. Vai trò hệ thống

| Vai trò | Mô tả | Người gán |
|---|---|---|
| `ADMIN` | Quản trị hệ thống: tài khoản, vai trò, quyền, audit; truy cập toàn bộ dữ liệu | ADMIN |
| `PROJECT_MANAGER` | Quản lý dự án: tạo dự án, giao việc, họp, risk/issue/milestone, báo cáo | ADMIN |
| `PROJECT_MEMBER` | Thành viên dự án: thực hiện và cập nhật công việc được giao | ADMIN |
| `VIEWER` | Chỉ xem | ADMIN |

## 2. Vai trò trong dự án (ProjectMemberRole)

`PROJECT_MANAGER`, `TECH_LEAD`, `BUSINESS_ANALYST`, `DEVELOPER`, `TESTER`, `DEVOPS`, `MEMBER`.

Phạm vi áp dụng (v1): dùng để hiển thị và hỗ trợ các quy tắc đặc biệt như quyền quản lý dự án (PROJECT_MANAGER trong dự án = quyền PM dự án). Các vai trò còn lại ở v1 có quyền thao tác như thành viên thường; chi tiết quyền theo vai trò trong dự án có thể mở rộng sau.

## 3. Danh sách quyền (Permission codes)

| Quyền | Mô tả |
|---|---|
| `user:view` | Xem danh sách/chi tiết người dùng |
| `user:manage` | Tạo/sửa/vô hiệu hóa tài khoản, gán vai trò hệ thống |
| `role:manage` | Quản lý vai trò & quyền |
| `project:view` | Xem dự án |
| `project:create` | Tạo dự án |
| `project:update` | Sửa dự án |
| `project:delete` | Xóa mềm dự án |
| `project-member:manage` | Thêm/xóa/đổi vai trò thành viên dự án |
| `task:view` | Xem công việc |
| `task:create` | Tạo công việc |
| `task:update` | Cập nhật công việc (mọi trường trong phạm vi quyền dự án) |
| `task:delete` | Xóa mềm công việc |
| `task:assign` | Giao việc / đổi người thực hiện |
| `task:comment` | Bình luận |
| `task:attachment` | Upload/delete file đính kèm |
| `task:export` | Xuất Excel |
| `meeting:view` | Xem cuộc họp |
| `meeting:manage` | Tạo/sửa/xóa/hoàn thành cuộc họp, quản lý người tham gia |
| `action-item:view` | Xem action item |
| `action-item:manage` | Tạo/sửa/xóa action item, chuyển thành task |
| `risk:view` | Xem risk |
| `risk:manage` | Tạo/sửa/xóa risk, chuyển risk thành issue |
| `issue:view` | Xem issue |
| `issue:manage` | Tạo/sửa/xóa issue |
| `milestone:view` | Xem milestone |
| `milestone:manage` | Tạo/sửa/xóa milestone |
| `dashboard:view` | Xem dashboard |
| `report:view` | Xem báo cáo |
| `report:export` | Export báo cáo |
| `notification:view` | Xem thông báo của mình |
| `notification:manage` | Đánh dấu đã đọc |
| `audit:view` | Xem nhật ký hoạt động |
| `plan:view` | Xem plan, WBS, Gantt, baseline, portfolio summary |
| `plan:create` | Tạo plan mới (master/detail) |
| `plan:update` | Sửa WBS, task, dependency, calendar của plan |
| `plan:delete` | Xóa mềm plan |
| `plan:approve` | SUBMITTED → APPROVED; kích hoạt ACTIVE; tạo trạng thái plan |
| `plan:version` | Tạo phiên bản plan mới (snapshot) |
| `plan:baseline` | Tạo baseline + xem variance |
| `plan:change` | Tạo/duyệt change history sau APPROVED |
| `plan:resource` | Gán resource, chỉnh capacity, xem workload |
| `plan:template` | Quản lý template (CRUD/version/clone) |
| `plan:link` | Tạo/xóa liên kết plan_links tới execution/issue/risk |
| `plan:schedule` | Trigger recalc / xem warnings & critical path |

## 4. Ma trận vai trò hệ thống × quyền

| Quyền | ADMIN | PROJECT_MANAGER | PROJECT_MEMBER | VIEWER |
|---|---|---|---|---|
| `user:view` | ✔ | — | — | — |
| `user:manage` | ✔ | — | — | — |
| `role:manage` | ✔ | — | — | — |
| `project:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `project:create` | ✔ | ✔ | — | — |
| `project:update` | ✔ | ✔ (PM dự án) | — | — |
| `project:delete` | ✔ | ✔ (PM dự án) | — | — |
| `project-member:manage` | ✔ | ✔ (PM dự án) | — | — |
| `task:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `task:create` | ✔ | ✔ | ✔ (nếu được cấu hình — mặc định có) | — |
| `task:update` | ✔ | ✔ | ✔ (task được giao — giới hạn trường) | — |
| `task:delete` | ✔ | ✔ (PM dự án) | — | — |
| `task:assign` | ✔ | ✔ (PM dự án) | — | — |
| `task:comment` | ✔ | ✔ | ✔ | — |
| `task:attachment` | ✔ | ✔ | ✔ | — |
| `task:export` | ✔ | ✔ | — | — |
| `meeting:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `meeting:manage` | ✔ | ✔ (PM dự án) | — | — |
| `action-item:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `action-item:manage` | ✔ | ✔ (PM dự án) | — | — |
| `risk:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `risk:manage` | ✔ | ✔ (PM dự án) | — | — |
| `issue:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `issue:manage` | ✔ | ✔ (PM dự án) | — | — |
| `milestone:view` | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| `milestone:manage` | ✔ | ✔ (PM dự án) | — | — |
| `dashboard:view` | ✔ | ✔ | ✔ (phạm vi project của mình) | ✔ (phạm vi project của mình) |
| `report:view` | ✔ | ✔ | — | ✔ |
| `report:export` | ✔ | ✔ | — | — |
| `notification:view` | ✔ | ✔ | ✔ | ✔ |
| `notification:manage` | ✔ | ✔ | ✔ | ✔ |
| `audit:view` | ✔ | — | — | — |
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

Ghi chú:
- **(project tham gia)**: quyền chỉ áp dụng cho các dự án người dùng là thành viên.
- **(PM dự án)**: quyền chỉ áp dụng cho dự án nơi người dùng có vai trò `PROJECT_MANAGER` trong `project_members`.
- ADMIN có toàn quyền ở mọi dự án kể cả không phải thành viên.
- PROJECT_MEMBER: `task:update` chỉ cho phép sửa task mình là assignee (hoặc được cấp quyền), giới hạn trường `status/progress/notes` (chi tiết FR-TASK-02).

## 5. Traceability — Chức năng × Vai trò (yêu cầu Prompt 02)

| Chức năng | ADMIN | PROJECT_MANAGER | PROJECT_MEMBER | VIEWER |
|---|---|---|---|---|
| Đăng nhập / refresh / logout / đổi mật khẩu | ✔ | ✔ | ✔ | ✔ |
| Quản lý tài khoản & vai trò | ✔ | — | — | — |
| Xem dashboard hằng ngày | ✔ | ✔ | ✔ (project của mình) | ✔ (project của mình) |
| Quản lý dự án (tạo/sửa/xóa) | ✔ | ✔ (PM dự án) | — | — |
| Xem dự án & thành viên | ✔ | ✔ | ✔ | ✔ |
| Quản lý thành viên dự án | ✔ | ✔ (PM dự án) | — | — |
| Quản lý công việc (tạo/sửa/xóa/giao) | ✔ | ✔ | Tạo ✔ / sửa task được giao ✔ | — |
| Xem công việc | ✔ | ✔ | ✔ | ✔ |
| Bình luận & file đính kèm | ✔ | ✔ | ✔ | — |
| Công việc của tôi / hôm nay / quá hạn | ✔ | ✔ | ✔ | — |
| Xuất Excel | ✔ | ✔ | — | — |
| Quản lý cuộc họp | ✔ | ✔ (PM dự án) | — | — |
| Xem cuộc họp / biên bản | ✔ | ✔ | ✔ | ✔ |
| Quản lý action item | ✔ | ✔ (PM dự án) | — | — |
| Theo dõi action item được giao | ✔ | ✔ | ✔ | — |
| Quản lý risk | ✔ | ✔ (PM dự án) | — | — |
| Xem risk | ✔ | ✔ | ✔ | ✔ |
| Quản lý issue | ✔ | ✔ (PM dự án) | — | — |
| Xem issue | ✔ | ✔ | ✔ | ✔ |
| Ghi nhận quyết định (biên bản/notes) | ✔ | ✔ | — | — |
| Quản lý milestone | ✔ | ✔ (PM dự án) | — | — |
| Xem milestone | ✔ | ✔ | ✔ | ✔ |
| Xem thông báo / đánh dấu đã đọc | ✔ | ✔ | ✔ | ✔ |
| Xem báo cáo | ✔ | ✔ | — | ✔ |
| Export báo cáo | ✔ | ✔ | — | — |
| Xem nhật ký hoạt động | ✔ | — | — | — |
| Quản lý project plan (tạo/sửa/xóa/duyệt/baseline) | ✔ | ✔ (PM dự án) | — | — |
| Soạn WBS, dependency, recalc scheduling | ✔ | ✔ (PM dự án) | — | — |
| Gán resource / xem workload / quản lý template | ✔ | ✔ (PM dự án; template chỉ ADMIN) | — (chỉ xem workload mình) | — |
| Cập nhật actual trên task được gán (giới hạn field) | ✔ | ✔ | ✔ (task được gán) | — |
| Xem plan / Gantt / critical path / portfolio | ✔ | ✔ | ✔ (project tham gia) | ✔ (project tham gia) |
| Quản lý change history sau APPROVED | ✔ | ✔ (PM dự án) | — | — |

## 6. Quy tắc phân quyền đặc biệt

1. **Kiểm tra kép**: ngoài quyền toàn cục, mọi thao tác dữ liệu dự án còn kiểm tra membership (thuộc dự án, chưa xóa mềm).
2. **Member sửa task**: chỉ task mình là assignee (hoặc được ủy quyền); chỉ các trường `status/progress/notes` + chuyển trạng thái hợp lệ.
3. **Owner risk/issue**: được cập nhật trạng thái của risk/issue mình phụ trách (nếu xác nhận theo FR-RISK-02/FR-ISS-02).
4. **Chủ trì họp**: được hoàn thành họp; quyền sửa họp chờ xác nhận (BR-MEET-05).
5. **Admin bảo vệ**: ADMIN không thể vô hiệu hóa chính mình (chờ xác nhận FR-USER-01).
6. **403 khi đủ điều kiện**: không đủ quyền hoặc không thuộc phạm vi dữ liệu → `403 FORBIDDEN` với code phân biệt (`ACCESS_DENIED`).
7. **UI ẩn menu theo quyền**: sidebar/action ẩn theo permission; Backend vẫn kiểm tra lại (không tin UI).
8. **Data phạm vi**: danh sách mặc định của MEMBER/VIEWER chỉ gồm dự án họ tham gia; ADMIN/PM xem theo quyền.

## 7. Điểm cần xác nhận

1. PROJECT_MEMBER có được tạo task không (ma trận mặc định: có)?
2. VIEWER có được xem báo cáo không (mặc định: có, không export)?
3. Owner risk/issue có được tự cập nhật trạng thái không?
4. Chủ trì họp có quyền sửa họp không?
5. Có cần giao quyền task:update theo vai trò trong dự án (TECH_LEAD... ) ngay ở v1 không?

## 8. Project Planning — quyền & ngữ cảnh (v1.1)

- Toàn bộ quyền `plan:*` áp dụng theo phạm vi dự án như `project:view/update` (quy tắc 1) — PM chỉ thao tác plan của dự án mình là PROJECT_MANAGER.
- PROJECT_MEMBER: cập nhật actual trên task được gán (giới hạn `status/progress/actualStart/actualFinish/actualEffortMinutes`); ngoài ra chỉ `plan:view` (docs/planning/04 §3).
- Portfolio dùng chung `plan:view` (không có `portfolio:*` riêng ở v1.1 — docs/planning/04 §5.4).
- Baseline do PM tạo trực tiếp khi plan APPROVED, không cần bước duyệt riêng (docs/planning/04 §5.5).
- `plan:template`: CRUD chỉ ADMIN; PM được phép clone template chỉ-đọc (docs/planning/04 §5.3).
