-- GĐ3 (cải tiến nghiệp vụ) phần 5/7: gắn cuộc họp (vd. Retrospective) với Sprint.
-- Không ràng buộc type riêng (vd. "RETROSPECTIVE") — meeting module hiện không có field type;
-- sprint_id chỉ là liên kết tùy chọn để truy vết action item sau retro về đúng sprint, cùng mẫu
-- plain UUID (không FK constraint) như tasks.sprint_id đã có từ V14.
ALTER TABLE meetings ADD COLUMN sprint_id UUID;

CREATE INDEX idx_meetings_sprint_id ON meetings(sprint_id);
