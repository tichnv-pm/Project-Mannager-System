-- GĐ3 (cải tiến nghiệp vụ) phần 3/7: Definition of Done (DoD) theo dự án.
-- DoD là checklist điều kiện PM định nghĩa cho dự án; khi chuyển task sang DONE (PATCH .../status),
-- nếu dự án có ít nhất 1 item active thì bắt buộc actor xác nhận đã đáp ứng (dodConfirmed=true),
-- không theo dõi trạng thái check từng item/từng task (giữ đơn giản — không phải quy trình QA test case).
CREATE TABLE project_dod_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES users(id)
);

CREATE INDEX idx_project_dod_items_project ON project_dod_items(project_id);
