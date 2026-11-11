-- GĐ3 (cải tiến nghiệp vụ) phần 6/7: mở rộng Change Request đánh giá tác động Scope-Schedule-Cost.
-- Schedule/Cost tính tự động khi tạo suggestion từ suggested_changes (plannedFinish/durationMinutes/
-- plannedEffortMinutes cũ-mới), tái dùng công thức BAC của EvmScheduler (effort giờ x hourly_rate chủ task)
-- cho cost. Scope là đánh giá định tính, không suy ra được bằng số -> PM phải ghi rõ khi tạo suggestion.
ALTER TABLE plan_change_requests
    ADD COLUMN scope_impact TEXT,
    ADD COLUMN schedule_impact_days INTEGER,
    ADD COLUMN cost_impact_amount DOUBLE PRECISION;
