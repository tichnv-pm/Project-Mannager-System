-- =============================================================================
-- PM Daily Work Management - Flyway migration V7 - AGILE DEMO FIXES & PORTFOLIO (v1.1)
-- Nguon: docs/planning/12 (portfolio), docs/planning/06 (AC-LINK-03: target phải tồn tại
--        và cùng project với kế hoạch). Chay o profile local (flyway.target = latest).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. SỬA LINK CHÉO PROJECT (AC-LINK-03)
--    V6 seed link e31/e32/e33 trỏ issue 901 / risk 801 / milestone 1001 cua project
--    301 trong khi plan b01 thuoc project 303 -> vi pham AC-LINK-03 (xem duoc nhung
--    update/toggle se tra 400 "phải thuộc cùng project với kế hoạch").
--    Xoa va chèn lai tro toi du lieu rieng cua project 303 (id moi ben duoi).
-- -----------------------------------------------------------------------------
DELETE FROM plan_links WHERE id IN (
    '00000000-0000-0000-0000-000000000e31',
    '00000000-0000-0000-0000-000000000e32',
    '00000000-0000-0000-0000-000000000e33'
);

-- -----------------------------------------------------------------------------
-- 2. MILESTONE / RISK / ISSUE RIÊNG CỦA DỰ ÁN 303 (PRJ-AGILE, Nền tảng Nông sản)
-- -----------------------------------------------------------------------------
INSERT INTO milestones (id, project_id, name, description, planned_date, status, progress, note, created_by) VALUES
    ('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000303', 'Release 1.0 MVP AgriCorp', 'Phát hành MVP Nền tảng Nông sản E-Commerce cho AgriCorp Việt Nam', '2026-08-28', 'IN_PROGRESS', 65, 'Sau khi hoàn tất Sprint 1 & Sprint 2', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000303', 'Release 2.0 Analytics & Logistics', 'Bản mở rộng phân tích dữ liệu và logistics giao hàng', '2027-02-28', 'NOT_STARTED', 0, NULL, '00000000-0000-0000-0000-000000000002');

INSERT INTO risks (id, code, project_id, title, description, probability, impact, level, owner_id, mitigation_plan, contingency_plan, status, due_date, created_by) VALUES
    ('00000000-0000-0000-0000-000000000803', 'RSK000003', '00000000-0000-0000-0000-000000000303',
     'Phụ thuộc cổng thanh toán VNPAY/Momo chưa ký kết', 'API kiểm duyệt và checksum SHA256 của nhà cung cấp chưa hoàn tất thủ tục hợp đồng',
     'HIGH', 'HIGH', 'CRITICAL', '00000000-0000-0000-0000-000000000002',
     'Song song làm việc với môi trường sandbox, dự phòng thêm 2 tuần phát triển',
     'Giảm phạm vi Sprint 2: lùi tích hợp thanh toán sang Release 2.0',
     'MONITORING', '2026-08-25', '00000000-0000-0000-0000-000000000002');

INSERT INTO issues (id, code, project_id, title, description, severity, owner_id, root_cause, solution, status, due_date, created_by) VALUES
    ('00000000-0000-0000-0000-000000000902', 'ISS000002', '00000000-0000-0000-0000-000000000303',
     'Mất phiên giỏ hàng Realtime trên Redis Cluster', 'Khách hàng mất giỏ hàng khi scale node Redis giữa phiên mua',
     'HIGH', '00000000-0000-0000-0000-000000000005',
     'Chưa cấu hình persistence và eviction policy phù hợp cho session giỏ hàng',
     'Bật AOF persistence cho Redis, thêm cơ chế session store dự phòng, hẹn giờ evict hợp lý',
     'IN_PROGRESS', '2026-08-20', '00000000-0000-0000-0000-000000000002');

-- -----------------------------------------------------------------------------
-- 3. CHÈN LẠI LINK CHO PLAN B01 — TARGET CÙNG PROJECT 303 (AC-LINK-03)
-- -----------------------------------------------------------------------------
INSERT INTO plan_links (id, plan_id, planning_task_id, target_type, target_id, link_type, note, is_primary_execution) VALUES
    ('00000000-0000-0000-0000-000000000e31', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c08', 'ISSUE', '00000000-0000-0000-0000-000000000902', 'RELATED', 'Liên kết tới Issue mất phiên giỏ hàng Realtime', false),
    ('00000000-0000-0000-0000-000000000e32', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c09', 'RISK', '00000000-0000-0000-0000-000000000803', 'BLOCKED_BY', 'Liên kết tới Rủi ro phụ thuộc cổng thanh toán VNPAY/Momo', false),
    ('00000000-0000-0000-0000-000000000e33', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c06', 'MILESTONE', '00000000-0000-0000-0000-000000001004', 'RELATED', 'Liên kết trực tiếp tới Mốc Release 1.0 MVP AgriCorp của dự án', false);

-- -----------------------------------------------------------------------------
-- 4. ĐỒNG BỘ BỘ ĐẾM SINH MÃ (đã chèn risk 803, issue 902)
-- -----------------------------------------------------------------------------
UPDATE global_sequences SET seq = 3 WHERE name = 'risk';
UPDATE global_sequences SET seq = 2 WHERE name = 'issue';

-- -----------------------------------------------------------------------------
-- 5. SEED PORTFOLIO (docs/planning/12: bảng portfolios/portfolio_projects — V4 §12,
--    chưa hề có dữ liệu mẫu, tab Portfolio trên UI đang rỗng)
-- -----------------------------------------------------------------------------
INSERT INTO portfolios (id, name, owner_id, description, is_shared) VALUES
    ('00000000-0000-0000-0000-000000008001', 'Danh mục PMO 2026', '00000000-0000-0000-0000-000000000002', 'Tổng hợp các dự án đang triển khai trong năm 2026 của phòng PMO', TRUE),
    ('00000000-0000-0000-0000-000000008002', 'Agile Transformation', '00000000-0000-0000-0000-000000000002', 'Dự án tiên phong chuyển đổi theo chuẩn Agile Scrum', FALSE);

INSERT INTO portfolio_projects (portfolio_id, project_id, weight) VALUES
    ('00000000-0000-0000-0000-000000008001', '00000000-0000-0000-0000-000000000301', 2),
    ('00000000-0000-0000-0000-000000008001', '00000000-0000-0000-0000-000000000302', 1),
    ('00000000-0000-0000-0000-000000008001', '00000000-0000-0000-0000-000000000303', 3),
    ('00000000-0000-0000-0000-000000008002', '00000000-0000-0000-0000-000000000303', 1);