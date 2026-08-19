-- =============================================================================
-- PM Daily Work Management - Flyway migration V9 - CRITICAL PATH DEMO CHAIN FIX (v1.1)
-- Nguon: docs/planning/08 (PLN-RULE-SCHED-05), docs/planning/09 (PLN-FR-CP).
-- Chay o profile local (flyway.target = latest).
--
-- V8 da them dependency c09->c10 (d08) va duration c10 = 62.400 phut, nhung c10
-- la PHASE (is_summary = true) nen SchedulingEngine bo qua (PLN-RULE-SCHED-05:
-- summary lay ngay tu roll-up children) -> c10 khong duoc schedule lai -> chain
-- sprint van khong rang buoc vao planned_finish cua plan -> critical = 0.
--
-- Fix: chuyen c10 thanh task thuong (is_summary = false) de engine lap lich duoc.
-- Sau migration nay chay: POST /api/v1/plans/{id}/recalc -> toan bo chain
-- TSK-101..TSK-202 + RELEASE 2.0 tro thanh critical path (float <= 0).
-- =============================================================================

UPDATE plan_tasks
SET is_summary = false
WHERE id = '00000000-0000-0000-0000-000000000c10';