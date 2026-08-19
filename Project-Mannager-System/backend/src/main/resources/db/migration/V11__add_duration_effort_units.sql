-- V11__add_duration_effort_units.sql
-- Thêm đơn vị tính thời lượng/công sức cho tasks và plan tasks

ALTER TABLE tasks 
    ADD COLUMN estimate_unit VARCHAR(50) NOT NULL DEFAULT 'MINUTE',
    ADD CONSTRAINT chk_task_estimate_unit CHECK (estimate_unit IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH'));

ALTER TABLE plan_tasks 
    ADD COLUMN duration_unit VARCHAR(50) NOT NULL DEFAULT 'MINUTE',
    ADD COLUMN effort_unit VARCHAR(50) NOT NULL DEFAULT 'MINUTE',
    ADD CONSTRAINT chk_pt_duration_unit CHECK (duration_unit IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH')),
    ADD CONSTRAINT chk_pt_effort_unit CHECK (effort_unit IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH'));

ALTER TABLE plan_template_tasks 
    ADD COLUMN duration_unit VARCHAR(50) NOT NULL DEFAULT 'MINUTE',
    ADD COLUMN effort_unit VARCHAR(50) NOT NULL DEFAULT 'MINUTE',
    ADD CONSTRAINT chk_ptt_duration_unit CHECK (duration_unit IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH')),
    ADD CONSTRAINT chk_ptt_effort_unit CHECK (effort_unit IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH'));

ALTER TABLE plan_change_requests 
    ADD COLUMN duration_unit VARCHAR(50) DEFAULT 'MINUTE',
    ADD COLUMN effort_unit VARCHAR(50) DEFAULT 'MINUTE';
