# AGENTS.md

PM Daily Work Management — ứng dụng quản lý công việc hằng ngày cho quản lý dự án phần mềm.
Trạng thái: **v1.0.0 PHÁT HÀNH HOÀN CHỈNH** (Prompt 02–24 đều ✔ 2026-08-04): Backend 222 tests PASS; Frontend 19 tests + build PASS; Docker Compose 3 container Up + smoke E2E 15/15 PASS; Tài liệu Release đầy đủ (`docs/release/01-test-plan.md`, `02-code-review.md`, `03-release-notes.md`).
Giai đoạn hiện tại: **v1.1 — PROJECT PLANNING HOÀN TẤT ✔ 2026-08-11**: Backend 10 sub-modules PLN-BE-01..10 ✔ 2026-08-07/08 — **281 tests PASS**; **Frontend module `planning` PLN-FE-01..10 ✔ 2026-08-10/11** (plan list/editor WBS, dependency, calendar, recalc/critical path, resource, baseline, change/link, template/portfolio, Gantt — **Gantt tự dựng SVG, license đã chốt 2026-08-07** docs/planning/13 §4; npm test 73 PASS + build PASS). Backlog v1.1 còn: E2E framework (Playwright/Cypress) — export CSV streaming ✔ và tối ưu SCSS budget ✔ đã hoàn tất 2026-08-11 (281 tests PASS; npm test 73 PASS + build PASS không warning budget) — kế hoạch chi tiết `docs/status-report-2026-08-10.md`.

## Quy trình làm việc (bắt buộc)

- Triển khai **tuần tự từng giai đoạn** theo bộ prompt đã thống nhất (docs/00-project-overview.md mục 11).
- **Không** tạo toàn bộ ứng dụng trong một lần; **không** tự ý tạo chức năng ngoài giai đoạn hiện tại.
- Trước khi sửa/tạo file: đọc tài liệu liên quan trong `docs/` trước — docs là nguồn sự thật chính.
- Không xóa file hoặc đổi kiến trúc đã thống nhất.
- Sau mỗi thay đổi phải liệt kê: file tạo mới, file cập nhật, mục đích, cách kiểm tra.
- Code phải compile được và test pass trước khi chuyển bước; không được xóa/skip test để build pass.
- Không viết TODO thay chức năng thật; không mock dữ liệu production; không hard-code secret.

## Công nghệ (đã thống nhất)

- Backend: Java 21, Spring Boot 3.x, Spring Security + JWT, Spring Data JPA, Flyway, Lombok, MapStruct, Bean Validation, Maven, springdoc (Swagger). Package gốc: `com.example.pmdaily`.
- Frontend: Angular (stable, tương thích Node hiện tại), Angular Material, Reactive Forms, SCSS. Chưa dùng NgRx — dùng service + RxJS.
- DB: PostgreSQL, PK UUID, `snake_case`, `timestamptz` (lưu UTC, UI hiển thị theo giờ người dùng).
- Chạy local: Docker Compose (postgres/backend/frontend-Nginx) hoặc thủ công Maven + npm.

## Lệnh (đã xác nhận — source thật từ Prompt 07)

- Backend: `mvn clean test`, `mvn clean package`, `mvn spring-boot:run -Dspring-boot.run.profiles=local` (JDK Temurin 21.0.12 + Maven 3.9.16 — đã kiểm chứng)
- Frontend: `npm install`, `npm test` (không watch — Vitest qua Angular builder), `npm run build`, `npm start` (Angular 22, zoneless, Node v24.18.1)
- Docker: `docker compose up -d --build`, `docker compose ps`, `docker compose logs -f`, `docker compose down [-v]` (Docker CLI 29.6.2 + Compose v5.3.1 — **đã kiểm chứng full stack PASS 2026-08-03**; daemon cần khởi động thủ công: `Start-Process "Docker Desktop.exe"`)
- Migration: Flyway trong `backend/src/main/resources/db/migration` (`V1__init_schema.sql`, `V2__seed_local_data.sql` — V2 chỉ chạy ở profile `local` qua `flyway.target`, xem docs/build/environment-check.md mục 5)

## Giai đoạn v1.1 — PROJECT PLANNING (bắt buộc)

- Nguồn sự thật: `docs/planning/01..15` + `docs/api/13-planning-api.md` + `docs/database/*` (phần B). Trước mỗi bước PLN-BE-*, đọc tài liệu planning tương ứng.
- Triển khai backend tuần tự: PLN-BE-01..10 — mỗi bước 1 phần: migration riêng (không sửa file migration đã chạy), unit/integration test, `mvn clean verify`, `mvn clean package`, cập nhật docs/test/planning, báo cáo file tạo/sửa.
- Quyền mới `plan:*` phải seed đồng bộ vào bảng `permissions` + gán cho role (docs/05 §3, docs/planning/04).
- Gantt: chưa thêm dependency cho tới khi PM/khách chốt license (docs/planning/13 §4) — caching kiểm duyệt.

## Kiến trúc

- Modular Monolith: mỗi module nghiệp vụ có `controller / service / repository / entity / dto / mapper / specification`.
- API prefix `/api/v1`, JSON, ISO-8601, pagination `page/size/sort`; error response thống nhất (`timestamp/status/error/code/message/path/fieldErrors/traceId`).
- Không trả Entity qua API (DTO + MapStruct); optimistic locking bằng `version`; soft delete cho dữ liệu nghiệp vụ.
- Secret đọc từ environment variable; application.yml chỉ chứa giá trị mặc định an toàn; profile `local`/`test`.
