-- GĐ3 (cải tiến nghiệp vụ): nền tảng Epic/Story/Backlog cho Agile.
-- Epic/Story là Task với type=EPIC/STORY (tái dùng hierarchy parent/child + sprint_id sẵn có của tasks),
-- story_points là đơn vị ước lượng độ phức tạp song song với estimate_minutes (giờ công), dùng cho Velocity sau này.
ALTER TABLE tasks ADD COLUMN story_points INTEGER;

COMMENT ON COLUMN tasks.story_points IS
    'Story point (Agile) cho Epic/Story; không bắt buộc, song song với estimate_minutes.';

ALTER TABLE tasks DROP CONSTRAINT ck_tasks_type;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_type
    CHECK (type IN ('FEATURE', 'BUG', 'IMPROVEMENT', 'TASK', 'OTHER', 'EPIC', 'STORY'));
