# Planning 01 — Yêu cầu nghiệp vụ Project Planning (Business Requirements)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Quy ước ID: `PLN-BR-<NHÓM>-<NN>`, nhóm: `GEN, MASTER, WBS, SCHED, CP, RES, BASE, LINK, TPL, GANTT, PORT`.
> Trạng thái: Draft — nguồn chính từ Prompt Project Planning Requirement.
> Tài liệu liên quan: `docs/00-project-overview.md`, `docs/planning/02..15`, `docs/01-business-requirements.md`.

## 1. Bối cảnh

PM Daily Work Management v1.0.0 đang quản lý **vận hành hằng ngày** (task, meeting, risk, issue, milestone) nhưng **chưa có khả năng lập kế hoạch toàn trình**: WBS, Gantt, dependency, baseline, critical path, resource planning theo nhiều dự án. PM chủ yếu lập kế hoạch bằng Excel riêng — không thống nhất, không kết nối với dữ liệu vận hành hiện có.

Phân hệ **PROJECT PLANNING — LẬP KẾ HOẠCH TOÀN TRÌNH DỰ ÁN** bổ sung khả năng lập và theo dõi kế hoạch theo mô hình **Master Plan – Detail Plan – Portfolio**, tích hợp với các module vận hành đã có (Execution Task, Issue, Risk, Meeting, Action Item, Milestone) qua bảng liên kết riêng.

Phân hệ này **độc lập**: Planning Task không dùng chung entity với Execution Task; nhưng có các bảng liên kết (`plan_links`) để ràng buộc hai chiều khi cần.

## 2. Mục tiêu nghiệp vụ

| # | Mục tiêu (PLN-BR) | Cách đo (gợi ý) |
|---|---|---|
| PLN-BR-GEN-01 | PM lập kế hoạch toàn trình đủ vòng đời dự án phần mềm (35 bước — xem mục 3) và theo dõi **planned vs actual** | % dự án mới có Master Plan ACTIVE |
| PLN-BR-GEN-02 | Lập WBS nhiều cấp (phase → work package → task/milestone) và trình bày trực quan dạng Gantt | Số kế hoạch chạy qua Plan Editor hằng tuần |
| PLN-BR-GEN-03 | Quản lý dependency (FS/SS/FF/SF + lag) và xác định critical path | Số báo cáo critical path được sử dụng |
| PLN-BR-GEN-04 | Chốt baseline khi kế hoạch APPROVED và đo variance (lịch/effort/milestone) | % kế hoạch có baseline; số delay-days báo cáo |
| PLN-BR-GEN-05 | Phân bổ nguồn lực (user/team/role/external) và phát hiện over-allocation > 100% | Số cảnh báo over-allocation giảm dần |
| PLN-BR-GEN-06 | Master Plan tổng hợp từ Detail Plan; Portfolio tổng hợp nhiều dự án (timeline, tiến độ, xung đột nguồn lực) | Roll-up khớp công thức chuẩn |
| PLN-BR-GEN-07 | Tạo kế hoạch nhanh từ template (phase, WBS, milestone, role chuẩn) | Thời gian tạo plan từ template < 5 phút |
| PLN-BR-GEN-08 | Mọi thay đổi kế hoạch sau APPROVED có dấu vết (change history) và cần xác nhận của PM | Đủ change history + audit log |

## 3. Vòng đời dự án phần mềm (chuẩn để tạo template & master plan)

Hệ thống hỗ trợ **35 bước** vòng đời dự án phần mềm (chuẩn hóa từ tài liệu yêu cầu của phân hệ):

| Bước | Tên bước | Phase tổng (template) |
|---|---|---|
| 1 | Tiếp nhận yêu cầu khách hàng | Project Initiation |
| 2 | Khảo sát hiện trạng | Project Initiation |
| 3 | Thu thập yêu cầu | Requirement Analysis |
| 4 | Làm rõ yêu cầu | Requirement Analysis |
| 5 | Phân tích nghiệp vụ | Requirement Analysis |
| 6 | Xác nhận phạm vi | Requirement Analysis |
| 7 | Lập kế hoạch dự án | Project Planning |
| 8 | Thiết kế giải pháp | Solution Design |
| 9 | Thiết kế kiến trúc | Solution Design |
| 10 | Thiết kế database | Solution Design |
| 11 | Thiết kế UI/UX | Solution Design |
| 12 | Chuẩn bị môi trường | Environment Preparation |
| 13 | Phát triển Backend | Development |
| 14 | Phát triển Frontend | Development |
| 15 | Phát triển tích hợp | Integration |
| 16 | Unit Test | System Testing |
| 17 | Code Review | System Testing |
| 18 | Integration Test | System Testing |
| 19 | System Test | System Testing |
| 20 | Security Test | Security & Performance Testing |
| 21 | Performance Test | Security & Performance Testing |
| 22 | Regression Test | Security & Performance Testing |
| 23 | UAT | User Acceptance Testing |
| 24 | Sửa lỗi | Deployment Preparation |
| 25 | Chuẩn bị triển khai | Deployment Preparation |
| 26 | Chuẩn bị migration dữ liệu | Deployment Preparation |
| 27 | Triển khai Production | Production Deployment |
| 28 | Smoke Test | Go-live |
| 29 | Go-live | Go-live |
| 30 | Hypercare | Hypercare |
| 31 | Đào tạo | Handover |
| 32 | Nghiệm thu | Handover |
| 33 | Bàn giao | Handover |
| 34 | Bảo hành | Warranty & Maintenance |
| 35 | Đóng dự án | Project Closure |

> Lưu ý: danh sách 35 bước là tài liệu tham chiếu chuẩn hóa; bộ **template chuẩn 17 phase + milestone mặc định** được định nghĩa tại `docs/planning/12-module-integration-design.md` (mục Template).

## 4. Yêu cầu nghiệp vụ theo nhóm module

| ID | Nhóm | Mô tả nghiệp vụ | Giá trị | Tài liệu chi tiết |
|---|---|---|---|---|
| PLN-BR-MASTER | Master – Detail – Portfolio | Master Plan (nhiều version, 1 ACTIVE); Detail Plan con; Portfolio tổng hợp nhiều dự án | Nhìn toàn cảnh + chi tiết | `docs/planning/06` |
| PLN-BR-WBS | WBS | WBS nhiều cấp, indent/outdent/move, wbsCode tự động, expand/collapse, cấm vòng lặp cha-con | Cấu trúc kế hoạch rõ ràng | `docs/planning/07` |
| PLN-BR-SCHED | Auto scheduling & Calendar | AUTO/MANUAL, working calendar, holiday, special date, recalc downstream, 4 loại dependency + lag + constraint | Kế hoạch theo đúng ngày làm việc | `docs/planning/08` |
| PLN-BR-CP | Critical Path | Forward/backward pass, ES/EF/LS/LF, Total/Free Float, critical task/path | Biết chuỗi công việc tới hạn | `docs/planning/09` |
| PLN-BR-RES | Resource planning | Gán user/team/role/external, allocation, capacity, workload, over-allocation (cảnh báo) | Không chồng chéo nhân lực | `docs/planning/10` |
| PLN-BR-BASE | Baseline & Version | Nhiều version plan, status, baseline snapshot, variance, change history | Kiểm soát thay đổi | `docs/planning/11` |
| PLN-BR-LINK | Liên kết module | plan_links tới Execution Task/Issue/Risk/Meeting/Action Item/Milestone | Khớp vận hành | `docs/planning/12` |
| PLN-BR-TPL | Template | Template phase/milestone/role, clone, version, bỏ phase | Tạo nhanh, chuẩn hóa | `docs/planning/12` |
| PLN-BR-GANTT | Gantt UI | Plan Editor: bảng + timeline đồng bộ, zoom, critical/baseline/today line, indicators | Trực quan | `docs/planning/13` |
| PLN-BR-PORT | Portfolio | Timeline nhiều dự án, tổng hợp tiến độ, xung đột nguồn lực, lọc PM/đơn vị/khách hàng/status | Nhìn danh mục | `docs/planning/06` |

## 5. Phạm vi — Trong phân hệ (v1)

1. Master Plan – Detail Plan – Portfolio.
2. WBS tree + thao tác trên cây (add sibling/child, indent/outdent, move up/down, move parent, expand/collapse, wbsCode tự động, chặn vòng lặp cha–con, chặn xóa summary còn con — hoặc xóa cả cây khi xác nhận).
3. Task Dependency 4 loại (FS/SS/FF/SF) + lag; chặn self-dependency và cycle.
4. Working Calendar chuẩn tổ chức + dự án; auto scheduling (working day, loại trừ cuối tuần/nghi/ngày đặc biệt).
5. Baseline snapshot (chỉ khi APPROVED) + so sánh variance; không ghi đè baseline.
6. Critical Path Engine.
7. Resource planning: assignment + workload + capacity + **mới** over-allocation cảnh báo (chọn cảnh báo, chưa auto leveling).
8. Liên kết `plan_links` (MVP: Execution Task, Issue, Risk, Meeting, Action Item, Milestone; mở rộng sau: Attachment/Document/Release/Deployment/Change Request).
9. Progress roll-up theo trọng số effort/duration.
10. Template: tạo plan từ template, clone, sửa, version template, bỏ phase không áp dụng.
11. Permissions, audit, optimistic locking, validation — đúng chuẩn toàn hệ thống.

## 6. Phạm vi ngoài phân hệ (v1)

- Resource **leveling** tự động (chỉ **cảnh báo** over-allocation).
- Dependency **cross-project** (để roadmap).
- Real-time (WebSocket).
- Email/SMS notification.
- Mobile app, đa ngôn ngữ.
- Tự động **không cần xác nhận** khi Issue/Risk/ExecutionTask thay đổi làm thay đổi lịch — mọi thay đổi lịch kế hoạch phải được PM **xác nhận**; baseline **không** tự đổi khi dữ liệu liên kết đổi.

## 7. Giả định (PLN-NFR-…)

- Quy mô trung-một: vài trăm user, ≤ hàng nghìn planning tasks/plan.
- Đơn vị thời gian trong planning: **phút** (`durationMinutes`, `plannedEffortMinutes`, `lagMinutes`), tương thích `estimateMinutes` của Execution Task.
- Lịch làm việc mặc định 5 ngày/tuần (2–6), 8 giờ/ngày; cấu hình theo dự án.
- Baseline viết: chỉ tạo khi kế hoạch APPROVED và chỉ chụp snapshot (không chỉnh sửa).
- Scheduling engine và Critical Path engine phải **chạy độc lập** (test không phụ thuộc DB khi bài-toán thuần).
- Thao tác sửa lịch nặng có thể chạy background job (v1.1+).
- Không dùng thư viện Gantt thương mại trước khi hoàn thành đánh giá license (`docs/planning/13`).
- Vẫn là **Modular Monolith**; chưa tách microservice.

## 8. Rủi ro & cách xử lý

| # | Rủi ro | Mức | Cách xử lý (giảm thiểu) |
|---|---|---|---|
| 1 | Phạm vi lớn (18 bảng mới, 12 module, engine phức tạp) | CAO | Triển khai tuần tự PLN-BE-01..10; mỗi step kết thúc bằng test + migration + báo cáo riêng |
| 2 | Scheduling/critical path sai thuật toán | CAO | Phân lớp engine thuần (không phụ thuộc Spring/JPA); unit test với case chuẩn (lời giải tham chiếu) |
| 3 | Roll-up sai công thức khi effort=0 / duration=0 | TRUNG BÌNH | Định nghĩa chuẩn công thức + fallback (trọng số effort → duration → avg) và test chuyên dụng |
| 4 | Gantt phức tạp khi tự dựng | TRUNG BÌNH | Đánh giá ≥3 thư viện (license); ưu tiên open-source; MVP dùng Angular table + SVG/CSS timeline |
| 5 | Concurrency (2 PM sửa cùng plan) | TRUNG BÌNH | Optimistic locking mọi entity; 409 khi xung đột |
| 6 | Hiệu năng khi recalc full plan | TRUNG BÌNH | Giới hạn kích thước plan; incremental recalc; background job (v1.1+) |
| 7 | Sợ chồng chéo giữa planning & execution | TRUNG BÌNH | Dùng `plan_links` tách biệt; nguồn dữ liệu actual có thể roll-up từ Execution nhưng luôn do PM đã duyệt |

## 9. Người dùng đại diện (Personas)

- **PM — anh Minh.** Lập master plan theo template, phân tích detail (Backend/Frontend/Testing), chốt baseline, theo dõi critical path, duyệt change khi Issue/Risk tác động.
- **Tech Lead — chị Lan.** Được gán plan task của detail backend, cập nhật progress/effort actual, xem workload của team.
- **Member — chị Hồng.** Xem plan task của mình được gán, cập nhật actual (progress/remaining), tránh chồng việc khi được cảnh báo over-allocation.
- **ADMIN — anh Đức.** Phân quyền gói module `plan:*`, duy trì template & calendar chuẩn tổ chức, theo dõi audit.