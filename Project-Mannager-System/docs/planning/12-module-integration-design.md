# Planning 12 — Module Integration Design & Template (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement (mục template/integration).
> Tài liệu: `docs/planning/01` (17 phase template), `docs/planning/02` (FPLan-T=TPL), `docs/planning/13` (Gantt), `docs/api/00-overview`.

## 1. Module lifecycle — SaaS kết nối (compile-link — tham chiếu)

Planning nằm là module trong Modular Monolith. Quan hệ với module khác:

| Module | Liên hệ | Cách tích hợp |
|---|---|---|
| Project | plan thuộc `projects.id`; `project_members` xác định phạm vi quyền | FK + filter theo `projectId` |
| Users/Teams | resource USER/TEAM | FK `resource_id` |
| Execution Task | `plan_links` (Execution → planning), actual cham roll-up | `plan_links` + config |
| Issue / Risk | `plan_links` (Issue/Risk → planning) | plan_links |
| Notification | cảnh báo (over-allocation, plan trễ) | `notification.events` (loại `PLAN_*`) |

> Không tạo bảng tổng hợp lưu cross-module — dùng view/query + event giữa module (giống `docs/architecture` hiện tại — Trạng thái: Modular Monolith events).

## 2. Template Plan

### 2.1 Bảng `plan_templates`

```
id, template_code, template_name, description, template_type (FULL/PARTIAL), 
category, version_no, status (PUBLISHED/DRAFT), organization_id,
is_built_in (default TRUE cho 8 template mặc định), version, ...
```

### 2.2 Danh sách template mặc định (8) — nguồn `docs/planning/01`

| # | template_code | template_name | Ngành/Đặc tả |
|---|---|---|---|
| 1 | FULL_SDL | Software Development Lifecycle | cho dự án dev mới (đang hỗ trợ thôi 17 phase) |
| 2 | AGILE_SPRINT | Agile Sprint (Scrum) | Sprint-based, phase lặp |
| 3 | PMO_STANDARD | PMO Standard | phòng PMO dùng chung |
| 4 | MAINTENANCE | Maintenance & Support | bảo trì |
| 5 | INFRASTRUCTURE | Infrastructure / Cloud | dự án hạ tầng |
| 6 | MARKETING | Marketing Campaign | dự án marketing |
| 7 | VENDOR | Vendor / SOW deliverables | có milestone-thanh toán |
| 8 | DATA | Data Project | pipeline/analytics |

### 2.3 Phase template (17) — nguồn `docs/planning/01` + thống nhất

1. INITIATION
2. REQUIREMENTS
3. DESIGN
4. ARCHITECTURE
5. DEVELOPMENT
6. INTEGRATION
7. TESTING
8. QUALITY_ASSURANCE
9. DEPLOYMENT
10. TRAINING
11. DOCUMENTATION
12. UAT
13. SECURITY_AUDIT
14. PERFORMANCE
15. SUPPORT_WARRANTY
16. MAINTENANCE
17. CLOSURE

Mỗi phase có milestone mặc định: `taskType = PHASE` (summary) + subsystem MILESTONE "Phase X Complete" (vd Requirements Completed).

### 2.4 template tasks

Plan template → khi tạo plan từ template: copy `plan_template_tasks` (tương tự như WBS `plan_tasks`), không copy dependency (để PM tạo), độ sâu ≤ 2.

### 2.5 Version template

`plan_templates` có version; mỗi lần sửa template → tăng version (số nguyên standalone), thay vì lưu change history. Khi mẫu mở rộng nhiều thay đổi (v_next) → tạo record mới.

## 5. danh sách quan điểm – cần nắm trước khi code

- Template chạy PUBLIC/Internal (xác định scope).
- Tạo plan từ template: PM chọn template → hệ thống sinh Master (default) họ hoặc Detail nếu chọn phase subset.

## 6. Event cần publish (mục events)

| Event | Trigger | Consumer |
|---|---|---|
| `plan.created`, `plan.updated`, `plan.submitted`, `plan.approved`, `plan.baseline_created`, `plan.recalc.done` | service | notification, audit |
| `plan.task_actual_updated` | MEMBER cập nhật actual | Execution rollup, notification |

## 7. Gantt UI – giao tiếp

- Backend trả **tree JSON** (`plan_tasks` + dependencies + resources) + `critical`, `baseline`, `versionInfo`.
- Frontend render 2 khung: **Grid left** (WBS table) + **Timeline right** (bars). Phần cuộn đồng bộ.
- `docs/planning/13` chi tiết.

## 8. Trạng thái doc: draft — cần xây dựng thêm template chi tiết mốc (18) theo từng template (FULL_SDL: 17 phases + milestone).