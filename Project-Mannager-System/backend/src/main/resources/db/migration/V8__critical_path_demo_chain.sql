-- =============================================================================
-- PM Daily Work Management - Flyway migration V8 - CRITICAL PATH DEMO CHAIN (v1.1)
-- Nguon: docs/planning/09 (PLN-FR-CP-01..05), PLN-RULE-CP-01 (critical khi float <= 0).
-- Chay o profile local (flyway.target = latest).
--
-- Vấn đề: plan b01 (Master Plan PRJ-AGILE) có planned_finish = 2027-02-28 nhưng
-- phase Release 2.0 (c10) KHÔNG có dependency nối với chain Sprint (c03..c09) và
-- duration_minutes c10 quá nhỏ so với span Sep-2026 -> Feb-2027. Backward pass CPM
-- gán LF = planFinish cho task cuối chain -> mọi task float lớn -> criticalTaskCount = 0
-- (Gantt không hiện đường gantt đỏ). Sửa dữ liệu demo:
--   1. Nối dependency c09 -> c10 (Sprint 2 ket thuc -> Release 2.0 bat dau).
--   2. Cập nhật duration c10 = 62.400 phut (~130 ngay lam viec, Sep 2026 - Feb 2027).
-- Sau V8, chay recalc (POST /api/v1/plans/{id}/recalc) de can chinh lai lich tu dong;
-- toan bo chain c03..c10 thanh critical path nhat quan voi CPM.
-- =============================================================================

INSERT INTO plan_task_dependencies (id, plan_id, predecessor_task_id, successor_task_id, dependency_type, lag_minutes) VALUES
    ('00000000-0000-0000-0000-000000000d08', '00000000-0000-0000-0000-000000000b01',
     '00000000-0000-0000-0000-000000000c09', '00000000-0000-0000-0000-000000000c10',
     'FS', 0);

UPDATE plan_tasks
SET duration_minutes = 62400,
    planned_effort_minutes = 62400,
    is_critical = false
WHERE id = '00000000-0000-0000-0000-000000000c10';