ALTER TABLE plan_change_requests ADD COLUMN reviewed_by_2 uuid REFERENCES users(id);
ALTER TABLE plan_change_requests ADD COLUMN reviewed_at_2 timestamp with time zone;