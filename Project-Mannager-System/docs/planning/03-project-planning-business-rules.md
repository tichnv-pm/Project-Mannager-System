# Planning 03 — Quy tắc nghiệp vụ Project Planning (Business Rules)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Quy ước ID: `PLN-RULE-<NHÓM>-<NN>`. Nhóm: `GEN, PLAN, VERSION, WBS, DEP, CAL, SCHED, CP, RES, BASE, CHG, LINK, TPL, PORT`.
> Mức: BẮT BUỘC (bắt buộc v1) / KHUYẾN NGHỊ / CHỜ XÁC NHẬN.
> Trạng thái: Draft — nguồn chính từ Prompt Project Planning Requirement.
> Tài liệu liên quan: `docs/planning/01`, `docs/planning/02`, `docs/04-business-rules.md`.

## 1. Quy tắc chung (PLN-RULE-GEN)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-GEN-01 | Mọi bảng planning dùng quy ước hệ thống: UUID PK, `snake_case`, `timestamptz` UTC, `version` (optimistic locking), `created_*/updated_*`. | BẮT BUỘC |
| PLN-RULE-GEN-02 | Xóa mềm cho dữ liệu nghiệp vụ (project_plans, plan_tasks, plan_versions, baselines...) — mapping/append-only không bắt buộc. | BẮT BUỘC |
| PLN-RULE-GEN-03 | Không trả Entity qua API — DTO + MapStruct. | BẮT BUỘC |
| PLN-RULE-GEN-04 | Error response chuẩn `timestamp/status/error/code/message/path/fieldErrors/traceId`; 409 khi version cũ. | BẮT BUỘC |
| PLN-RULE-GEN-05 | Audit log cho mọi hành động thay đổi plan (tạo/sửa/xóa/dependency/thay baseline/approve/change). | BẮT BUỘC |
| PLN-RULE-GEN-06 | Controller KHÔNG chứa thuật toán lập lịch; Entity KHÔNG chứa logic tính lịch phức tạp — dùng SchedulingEngine/CriticalPathEngine (domain service thuần). | BẮT BUỘC |
| PLN-RULE-GEN-07 | Thay đổi dependency/duration/calendar/constraint bắt buộc kích hoạt schedule recalc (không phụ thuộc job tích hợp). | BẮT BUỘC |
| PLN-RULE-GEN-08 | Scheduling/CriticalPath engine phải test độc lập (không cần DB). | BẮT BUỘC |
| PLN-RULE-GEN-09 | Phép tính nặng chạy **async job** khi plan > 200 task; ≤ 200 task chạy sync. | ĐÃ CHỐT |

## 2. Project Plan (PLN-RULE-PLAN)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-PLAN-01 | `planCode` unique trong project (key: project_id + plan_code). | BẮT BUỘC |
| PLN-RULE-PLAN-02 | Master Plan `parentPlanId` = NULL; Detail Plan `parentPlanId` → Master. CHỈ cho phép 1 cấp master-detail ở v1. | BẮT BUỘC |
| PLN-RULE-PLAN-03 | Cấm Detail Plan cha là Detail (chỉ có Master làm cha). | BẮT BUỘC |
| PLN-RULE-PLAN-04 | Một project có tối đa **1** plan ACTIVE dạng MASTER (unique partial trên (project_id, is_active) WHERE status='ACTIVE'). | BẮT BUỘC |
| PLN-RULE-PLAN-05 | `endDate >= startDate` cho plan (từ roll-up, or cấu hình thủ công khi DRAFT). | BẮT BUỘC |
| PLN-RULE-PLAN-06 | Cập nhật các vùng chỉ qua version (optimistic) — không cho "ghost write" ngoài vòng ghi. | BẮT BUỘC |

## 3. Plan Version (PLN-RULE-VERSION)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-VERSION-01 | `versionNo` tăng đơn điệu (1,2,3...); chống trùng trong plan (unique). | BẮT BUỘC |
| PLN-RULE-VERSION-02 | Chỉ một phiên bản = `activeVersionId` tại một thời điểm. | BẮT BUỘC |
| PLN-RULE-VERSION-03 | Không sửa đổi phiên bản đã lưu; phiên bản mới luôn là bản snapshot mới tại thời điểm duyệt/save. | BẮT BUỘC |
| PLN-RULE-VERSION-04 | Baseline chỉ tạo từ phiên bản APPROVED. | BẮT BUỘC |

## 4. WBS / Planning Task (PLN-RULE-WBS)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-WBS-01 | `taskCode` unique trong plan (trường hợp `wbsCode` cũng có thể unique theo cây). | BẮT BUỘC |
| PLN-RULE-WBS-02 | Cấm vòng lặp cha–con: kiểm tra toàn tuyến tổ tiên trước khi gán parent. | BẮT BUỘC |
| PLN-RULE-WBS-03 | Duy nhất metadata: không có hai task ở mức module trùng cấu trúc; `isSummary` tự suy (có con). | CHỜ XÁC NHẬN |
| PLN-RULE-WBS-04 | Không cho cấp con của MILESTONE hoặc EXTERNAL_TASK (lá). | BẮT BUỘC |
| PLN-RULE-WBS-05 | `wbsCode` re-number khi move (1, 1.1, 1.2, 2...) theo thứ tự `sequenceNumber`; tự động tính. | BẮT BUỘC |
| PLN-RULE-WBS-06 | Xóa summary còn con → **TỪ CHỐI (400 HAS_CHILDREN)**; người dùng phải xóa con trước (docs/planning/14 PLN-AC-WBS-04). | ĐÃ CHỐT |
| PLN-RULE-WBS-07 | Milestone task: `durationMinutes=0`, `percentComplete` chỉ 0 hoặc 100, ngày start=finish (nếu set). | BẮT BUỘC |
| PLN-RULE-WBS-08 | `percentComplete` của summary **không chỉnh tay** — tính theo roll-up (PLN-RULE-SCHED-*). | BẮT BUỘC |

## 5. Dependency (PLN-RULE-DEP)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-DEP-01 | predecessorId ≠ successorId (no self-loop). | BẮT BUỘC |
| PLN-RULE-DEP-02 | Cấm cycle (nếu add → vì sẽ tạo chu trình toàn đồ thị). | BẮT BUỘC |
| PLN-RULE-DEP-03 | Cả hai task cùng plan.project (không cross-project) ở v1. | BẮT BUỘC |
| PLN-RULE-DEP-04 | `lagMinutes` có thể **âm (cho phép)** với cảnh báo; config `scheduling.allowNegativeLag` (mặc định true). | ĐÃ CHỐT |
| PLN-RULE-DEP-05 | Một cặp (predecessor, successor, type) không trùng (unique). | BẮT BUỘC |

## 6. Scheduling & Calendar (PLN-RULE-SCHED)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-SCHED-01 | AUTO task: start/finish theo nguồn (predecessor + lag + duration + working day). Không đổi MANUAL. | BẮT BUỘC |
| PLN-RULE-SCHED-02 | Duration chuẩn tính theo ngày làm việc (giờ chuẩn nếu có). | BẮT BUỘC |
| PLN-RULE-SCHED-03 | Holiday/non-working/ngày đặc biệt loại trừ khi schedule. | BẮT BUỘC |
| PLN-RULE-SCHED-04 | Recalc downstream = các task phụ thuộc trực tiếp/gián tiếp; KHÔNG làm tổng thể khi chỉ thay đổi cục bộ. | BẮT BUỘC |
| PLN-RULE-SCHED-05 | Summary = min(start)+ max(finish) children; không sửa tay. | BẮT BUỘC |
| PLN-RULE-SCHED-06 | Constraint xung khắc dependency → trả warning (không crash). | BẮT BUỘC |
| PLN-RULE-SCHED-07 | Khi conflict về lịch do Issue/Risk/Execution → sinh change suggestion → PM quyết. | BẮT BUỘC |
| PLN-RULE-SCHED-08 | Milestone task không có duration; `plannedFinish = plannedStart` (constraint date). | BẮT BUỘC |

## 7. Critical Path (PLN-RULE-CP)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-CP-01 | `isCritical` = TotalFloat ≤ threshold (config; mặc định 0). | BẮT BUỘC |
| PLN-RULE-CP-02 | Forward/backward pass theo dependency thực tế; float ≥ 0. | BẮT BUỘC |
| PLN-RULE-CP-03 | Milestone được tính critical-path (float) như task thường. | ĐÃ CHỐT |
| PLN-RULE-CP-04 | Kết quả không lưu cứng — tính lại on-demand (GET /critical-path). | BẮT BUỘC |

## 8. Resource (PLN-RULE-RES)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-RES-01 | `allocationPercent` ∈ (0, 100] (%) cho USER; mặc định 100. | BẮT BUỘC |
| PLN-RULE-RES-02 | `plannedEffortMinutes`/`actualEffortMinutes` ≥ 0. | BẮT BUỘC |
| PLN-RULE-RES-03 | Tổng allocation một resource trong khoảng thời gian > capacity → **over-allocation warning** (không leveling). | BẮT BUỘC |
| PLN-RULE-RES-04 | Capacity mặc định tính từ working calendar traffic user (8 giờ x ngày làm). | KHUYẾN NGHỊ |
| PLN-RULE-RES-05 | Resource gán: `USER` (users), `ROLE` (catalog role), `EXTERNAL` (free text). **TEAM loại khỏi v1** (chưa có module teams). | ĐÃ CHỐT |
| PLN-RULE-RES-06 | Workload nhiều project = tổng theo user; cảnh báo trên range. | BẮT BUỘC |

## 9. Baseline (PLN-RULE-BASE)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-BASE-01 | Baseline chỉ tạo khi plan status = APPROVED (đúng chủ đề). | BẮT BUỘC |
| PLN-RULE-BASE-02 | Không bao giờ ghi đè baseline cũ (snapshot append). | BẮT BUỘC |
| PLN-RULE-BASE-03 | Snapshot = toàn vẹn tree (task dates/effort/progress + resource + milestone). | BẮT BUỘC |
| PLN-RULE-BASE-04 | Variance tính khi có baseline (0 nếu chưa có). | BẮT BUỘC |
| PLN-RULE-BASE-05 | Baseline không thay đổi khi planning tree thay đổi sau đó. | BẮT BUỘC |

## 10. Change (PLN-RULE-CHG)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-CHG-01 | Mọi thay đổi plan (task, dep, resource, dates) **sau khi APPROVED** phải có change history. | BẮT BUỘC |
| PLN-RULE-CHG-02 | Change suggestion do Issue/Risk/Execution → cần PM approve, không tự apply. | BẮT BUỘC |
| PLN-RULE-CHG-03 | Ghi đủ: reason, requestedBy, approvedBy, changedAt, before/after JSON, affectedTaskIds. | BẮT BUỘC |
| PLN-RULE-CHG-04 | Nếu change ảnh hưởng baseline trigger → tạo baseline mới (không sửa cũ). | BẮT BUỘC |
| PLN-RULE-CHG-05 | Change phải có audit log. | BẮT BUỘC |

## 11. Link (PLN-RULE-LINK)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-LINK-01 | Planning task & execution task là 2 entity riêng; liên kết qua `plan_links`, không cột list ID. | BẮT BUỘC |
| PLN-RULE-LINK-02 | 1 Execution Task → tối đa 1 planning task **chính** (linkType IMPLEMENTS/PRODUCES...), có thể nhiều link phụ type khác. | BẮT BUỘC |
| PLN-RULE-LINK-03 | Actual effort/progress roll-up từ Execution theo cấu hình (source enabled); task TASK không tự DONE khi execution chưa DONE. | ĐÃ CHỐT |
| PLN-RULE-LINK-04 | Không tự hoàn thành planning task nếu execution chưa DONE. | BẮT BUỘC |
| PLN-RULE-LINK-05 | Issue BLOCKED_BY / AFFECTS_SCHEDULE → tạo change suggestion (chờ PM). | BẮT BUỘC |
| PLN-RULE-LINK-06 | Risk OCCURRED → tạo Issue giữ liên kết (không mất). | BẮT BUỘC |
| PLN-RULE-LINK-07 | Action item → planning task: 1 liên kết (unique partial), tái sử dụng pattern UC-007. | BẮT BUỘC |
| PLN-RULE-LINK-08 | Baseline không đổi khi dữ liệu liên kết thay đổi. | BẮT BUỘC |

## 12. Template (PLN-RULE-TPL)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-TPL-01 | Template clone/sửa/version; bản gốc không xóa khi còn plan tham chiếu (soft flag). | BẮT BUỘC |
| PLN-RULE-TPL-02 | Template default (seed 8 type) chỉ chạy profile local. | BẮT BUỘC |
| PLN-RULE-TPL-03 | Bao gồm: phase, task mẫu, milestone, dependency mẫu, generic role, default duration, deliverable, acceptance criteria. | BẮT BUỘC |

## 13. Portfolio (PLN-RULE-PORT)

| ID | Quy tắc | Mức |
|---|---|---|
| PLN-RULE-PORT-01 | Portfolio đọc dữ liệu từ project_plans ACTIVE/COMPLETED và các metric; không có bảng riêng lưu số liệu (tính tại thời điểm đọc). | BẮT BUỘC |
| PLN-RULE-PORT-02 | Lọc theo PM / đơn vị / khách hàng / status / thời gian. | BẮT BUỘC |
| PLN-RULE-PORT-03 | Over-allocation chéo project dùng để hiển thị cảnh báo (nhiều plan). | BẮT BUỘC |

## 14. Tổng hợp quyết định đã chốt (2026-08-07)

> Trước khi triển khai code, PM/khách đã duyệt các quyết định dưới đây — ghi nhận làm "ĐÃ CHỐT" thay cho "CHỜ XÁC NHẬN".

| # | Nội dung | Quyết định đã chốt | Ảnh hưởng |
|---|---|---|---|
| 1 | Lead time (lag âm) (PLN-RULE-DEP-04) | **Cho phép + cảnh báo** (warning khi lag âm; config `scheduling.allowNegativeLag=true`) | Scheduling engine trả warning, không chặn |
| 2 | Milestone có critical không (PLN-RULE-CP-03) | **Có tính** — milestone vẫn được tính float; threshold float config mặc định 0 | Critical path tính cả milestone |
| 3 | Resource type TEAM/EXTERNAL (PLN-RULE-RES-05) | **Bỏ TEAM ở v1** — chỉ USER + ROLE + EXTERNAL (vì chưa có module teams) | Enum `plan_task_resources.resource_type` chỉ 3 giá trị |
| 4 | Roll-up actual từ Execution (PLN-RULE-LINK-03) | **Có, theo config** — execution được chỉ định nguồn mới roll-up; không tự hoàn thành khi execution chưa DONE | Link + config trên plan_links |
| 5 | Xóa summary còn con (PLN-RULE-WBS-06) | **Từ chối xóa** (400 `HAS_CHILDREN`) — phải xóa con trước; KHÔNG xóa cây | UI disable delete trên summary có con |
| 6 | Recalc sync/async (PLN-RULE-GEN-09) | **Async job cho bước nặng** — plan > 200 task chuyển ENQUEUED_RECALC, xử lý bằng scheduled job; ≤ 200 task chạy sync | Thêm bảng `plan_recalc_jobs` (hoặc cột status) + job |
| 7 | Baseline xóa (PLN-RULE-BASE) | **Bất biến, chỉ soft-delete** — không ghi đè, không xóa vật lý | Chỉ set deleted_at |
| 8 | Gán resource cho summary (PLN-RULE-RES) | **Cho gán "đại diện"** nhưng KHÔNG tính vào workload | Validator: summary được gán, workload bỏ qua |
| 9 | PROJECT_MEMBER cập nhật actual | **Có, giới hạn field** (actualStart/Finish/actualEffort/percentComplete/status) trên task được gán | Service check assignee |
| 10 | Change suggestion ai duyệt | **Dual-approve: PM dự án + ADMIN** cho plan có effort ≥ 10,000 phút (hoặc config); plan nhỏ PM đủ | Luồng change: PM approve → ADMIN approve → apply |

> Mọi quy tắc BẮT BUỘC đều được ánh xạ trong ma trận traceability `docs/planning/15`.