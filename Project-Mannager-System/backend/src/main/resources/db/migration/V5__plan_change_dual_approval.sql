-- =============================================================================
-- PM Daily Work Management - Flyway migration V5 - CHANGE DUAL APPROVAL (v1.1)
-- Nguon: docs/planning/14 PLN-AC-CHG-02b (plan total effort >= 10.000 phut -> can 2
-- nguoi duyet: PM + ADMIN truoc khi APPLIED), docs/database/02-data-dictionary.md.
-- Mo rong sang ma khong sua V4 (da chay trong moi truong thuc te).
-- =============================================================================
ALTER TABLE plan_change_requests ADD COLUMN reviewed_by_2 uuid REFERENCES users(id);
ALTER TABLE plan_change_requests ADD COLUMN reviewed_at_2 timestamptz;