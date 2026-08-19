# Planning 13 — Gantt UI (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: **ĐÃ CHỐT 2026-08-07 — Tự dựng SVG, không dependency** (quyết định PM/khách đã duyệt).
> Tài liệu: `docs/planning/07` (WBS), `docs/planning/09` (critical), `docs/planning/11` (baseline overlay), `docs/planning/06`.

## 1. Yêu cầu UI Gantt (v1 MVP)

- Hiển thị WBS tree + timeline Gantt theo ngày/tuần/tháng.
- Drag để: thay đổi ngày bắt đầu (task AUTO → recalc), kéo-thanh duration, đánh dấu dependency bằng liên kết mũi tên (FS/SS/FF/SF).
- Highlight critical path; overlay baseline (màu xám) so với current.
- Thêm/sửa task nội trong Grid.
- Milestone = hình kim cương; summary = thanh đậm + chevron; task = bar.
- Cuộn ngang mượt, giữ cột WBS khi cuộn timeline (fixed left).

## 2. Lựa chọn công nghệ

| Thư viện | License | Ghi chú | Quyết định |
|---|---|---|---|
| [dhtmlx Gantt](https://dhtmlx.com/docs/products/dhtmlxGantt/) | GPLv2 (free) **hoặc** Commercial | Full tính năng, cần mua license commercial khi dùng trong phần mềm đóng | KHÔNG dùng commercial; nếu GPL phù hợp (dự án này là internal) ⇒ xem license policy dự án |
| [Frappe Gantt](https://frappe.io/gantt) | MIT | Nhẹ, không dependency tree table, map6e taskbar | Tiềm năng v1 — dùng kèm tự dựng bảng trái |
| [syncfusion ej2-gantt] | Commercial (community license có điều kiện) | Full, cần trả phí | KHÔNG |
| [angular-gantt ]/ [gantt-elastic] | MIT (gantt-elastic) | angular-gantt cũ/không sync; gantt-elastic cần lập | Đề xuất xem thêm |

**Quyết định (đã chốt 2026-08-07):** dùng **Option A — Tự dựng SVG** (Grid Angular Material + timeline SVG tự vẽ, drag-drop đơn giản bằng SVG pointer events). License sạch 100%, không thêm dependency. Giới hạn hỗ trợ 500 task/plan ở v1.

## 4. Chọn triển khai thực tế (ĐÃ CHỐT)

**Option A — Tự dựng (đã chọn):** Grid table (Angular Material) + timeline vẽ bằng SVG (no-dep). Hỗ trợ drag-drop đơn giản. Cost thấp, license sạch 100%, phù hợp scope v1. Cần xử lý cuộn đồng bộ.

**Option B — Frappe Gantt (MIT):** giữ làm fallback không ưu tiên — không cần thêm dependency.

**Đã loại:** Option C — dhtmlx Gantt (GPLv2/commercial) — phụ thuộc license, không dùng.

## 5. Data contract frontend–backend

```
GET /plans/{id}/gantt  → {
  plan: {...},
  calendars: [...],
  tasks: [ {id, parentId, wbsCode, name, type, start, finish, durationMinutes,
            effort, percentComplete, status, scheduleMode, isCritical, color?,
            resources [] , baseline: {start, finish}|null } ],
  dependencies: [ {from, to, type, lag} ]
}
```

- update task: PUT `/plans/{id}/tasks/{taskId}` (start/finish/duration) — server runs recalc, trả về tree + warnings.
- CRUD dependency: POST/DELETE `/tasks/{id}/dependencies`.
- Undo muốn trả `warnings` + `recalculatedDates`.

## 6. Interaction & recalc trigger

| Tương tác | Gọi API | Hậu quả |
|---|---|---|
| Kéo thanh task | PUT task (start) | recalc auto downstream + warnings |
| Kéo mũi tên dependency | POST dependency | recalc |
| Tạo task (Insert) | POST task | renumber |
| Đổi parent (drag nó khi hierarchy) | PUT parentId | renumber + recalc |
| Highlight critical | GET `/critical-path` | không lưu |

## 7. Trạng thái UI (local only — không lưu DB)

| State | Xử lý |
|---|---|
| Expanded/collapsed nodes | client |
| Scroll snap (today) | client |
| Grid column width | client |
| Theme | client |

## 8. Những gì v1 KHÔNG làm (scope)

- Resource library trong Gantt (chỉ hiển thị allocation %),
- Gantt trên mobile (đề xuất allow horizontal scroll), 
- Baseline multiple trong UI (chỉ active + variance tooltip).

## 9. Quyết định cần chốt (rút gọn từ mục 3/4)

1. Chọn **A (tự dựng SVG)** hay **B (Frappe Gantt MIT)** — quyết định này là *"khách hàng/PM"*, không phải technical.
2. Ngưỡng hiệu năng Gantt (số task/plan tối đa hỗ trợ v1: 200/300/500) — đảm bảo NFR-PERF.
3. Đồng thuận critical path highlight trong Gantt bằng màu mặc định thế nào.

> 🚨 Trước khi thêm bất kỳ dependency nào: kiểm chứng license lại bằng web (frappe-gantt MIT, dhtmlx GPL2/Com, syncfusion community conditions).