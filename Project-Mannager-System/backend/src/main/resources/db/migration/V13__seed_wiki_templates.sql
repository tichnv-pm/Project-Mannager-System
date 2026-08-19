-- V13__seed_wiki_templates.sql
-- Nạp dữ liệu mẫu cho Wiki phát triển phần mềm

-- Thư mục gốc mẫu Wiki
INSERT INTO wiki_page_templates (id, parent_template_id, title, content_placeholder, sequence_no) VALUES 
('00000000-0000-0000-0000-000000000101', NULL, '1. Hướng dẫn Bắt đầu (Getting Started)', '# 1. Hướng dẫn Bắt đầu cho dự án

Chào mừng thành viên mới. Dưới đây là các bước để bắt đầu làm việc trong dự án này.', 1),
('00000000-0000-0000-0000-000000000102', NULL, '2. Thiết kế Kiến trúc & Hệ thống (Architecture & Design)', '# 2. Kiến trúc Hệ thống

Mô tả thiết kế kiến trúc tổng thể, sơ đồ khối, và các công nghệ cốt lõi sử dụng trong dự án.', 2),
('00000000-0000-0000-0000-000000000103', NULL, '3. Quy chuẩn Viết Code (Coding Guidelines)', '# 3. Quy chuẩn Viết Code

Quy tắc đặt tên, cấu trúc dự án và quy ước coding conventions bắt buộc đối với nhà phát triển.', 3),
('00000000-0000-0000-0000-000000000104', NULL, '4. Quy trình Chất lượng & Kiểm thử (QA Guidelines)', '# 4. Quy trình QA & Testing

Quy trình viết kịch bản test, chạy kiểm thử và tiêu chuẩn hoàn thành DoD (Definition of Done).', 4),
('00000000-0000-0000-0000-000000000105', NULL, '5. Triển khai & Vận hành (Deployment)', '# 5. Hướng dẫn Deploy & Vận hành

Cấu hình môi trường Staging/Production, quy trình CI/CD và hướng dẫn sao lưu/phục hồi dữ liệu.', 5);

-- Các trang con
INSERT INTO wiki_page_templates (id, parent_template_id, title, content_placeholder, sequence_no) VALUES 
('00000000-0000-0000-0000-000000000111', '00000000-0000-0000-0000-000000000101', '1.1 Hướng dẫn thiết lập môi trường Local', '# Cài đặt Môi trường Local

## 1. Yêu cầu Hệ thống:
* JDK: OpenJDK 21
* Node.js: v24.x
* PostgreSQL: 16+

## 2. Các bước chạy local:
1. Chạy cơ sở dữ liệu: `docker compose up -d postgres`
2. Chạy Backend: `./mvnw spring-boot:run`
3. Chạy Frontend: `npm install && npm start`', 1),
('00000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000000101', '1.2 Quy trình Git & Quy ước nhánh', '# Quy ước Git & Workflow

## 1. Đặt tên nhánh:
* Feature: `feature/PRJ-TASK-XXXXXX`
* Bugfix: `bugfix/PRJ-TASK-XXXXXX`
* Hotfix: `hotfix/description`

## 2. Quy chuẩn commit message:
* Định dạng: `[PRJ-TASK-XXXXXX] Tin nhắn commit của bạn`
* Ví dụ: `[CRM-TASK-001042] Implement forgot password API`', 2),
('00000000-0000-0000-0000-000000000121', '00000000-0000-0000-0000-000000000102', '2.1 Sơ đồ kiến trúc & Thành phần hệ thống', '# Sơ đồ Kiến trúc Tổng thể

[Điền sơ đồ kiến trúc hệ thống bằng hình ảnh hoặc Mermaid diagram vào đây]', 1),
('00000000-0000-0000-0000-000000000122', '00000000-0000-0000-0000-000000000102', '2.2 Thiết kế cơ sở dữ liệu & Cấu trúc bảng', '# Thiết kế Cơ sở dữ liệu

[Mô tả thiết kế DB, quan hệ thực thể ERD hoặc các cấu hình migration đặc biệt]', 2),
('00000000-0000-0000-0000-000000000131', '00000000-0000-0000-0000-000000000103', '3.1 Quy tắc đặt tên & Cấu trúc thư mục', '# Quy tắc đặt tên & Coding Conventions

* PascalCase cho tên Class Java.
* camelCase cho tên biến, tên phương thức và biến Javascript/Typescript.
* snake_case cho tên cột, tên bảng cơ sở dữ liệu.', 1),
('00000000-0000-0000-0000-000000000132', '00000000-0000-0000-0000-000000000103', '3.2 Quy trình Code Review & Pull Request checklist', '# Quy trình Code Review & PR Checklist

## Trước khi tạo Pull Request:
* [ ] Code compile không lỗi.
* [ ] Chạy thành công toàn bộ unit tests.
* [ ] Kiểm tra lint và formatting sạch sẽ.
* [ ] Ít nhất 1 Senior Developer duyệt (Approve) trước khi Merge.', 2);
