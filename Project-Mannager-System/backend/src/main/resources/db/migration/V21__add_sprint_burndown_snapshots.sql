-- GĐ3 (cải tiến nghiệp vụ) phần 2/7: Velocity + Burndown.
-- Velocity tính trực tiếp từ tasks.story_points + tasks.sprint_id (đã có từ V20), không cần bảng mới.
-- Burndown cần dữ liệu lịch sử theo ngày (remaining points mỗi ngày trong sprint) — không thể suy ngược
-- chính xác từ audit log hiện có (không phải mọi đường cập nhật trạng thái đều ghi before/after status
-- nhất quán), nên dùng snapshot hàng ngày qua scheduled job, cùng mẫu với project_financial_snapshots (V18).
CREATE TABLE sprint_burndown_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sprint_id UUID NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    total_story_points INTEGER NOT NULL,
    completed_story_points INTEGER NOT NULL,
    remaining_story_points INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_sprint_burndown_date UNIQUE (sprint_id, snapshot_date)
);
