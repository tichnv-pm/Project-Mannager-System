# PM Daily — Kết quả Review mã nguồn (v1.2.0)

> Cập nhật: 2026-08-22 (Phát hành v1.2.0).
> Phạm vi: Toàn bộ `backend/` và `frontend/` sau khi tích hợp phân hệ v1.1 (Planning) và v1.2 (E2E Software Management).

## 1. Checklist tổng thể

| # | Hạng mục | Kết quả | Ghi chú |
|---|---|---|---|
| 1 | Code compile & test pass trước khi chuyển bước | ✔ | 295 BE + 73 FE + build cả 2 stack |
| 2 | Không TODO/FIXME/HACK thay chức năng | ✔ | Chỉ có trạng thái "TODO" trong danh mục task |
| 3 | Không hard-code secret trong code | ✔ | Dùng env variables; `.env` không commit |
| 4 | Không trả Entity qua API | ✔ | Dùng DTO + MapStruct mappers |
| 5 | Không xóa/skip test để build pass | ✔ | 0 skipped test |
| 6 | Không mock dữ liệu production | ✔ | Seed profile local độc lập |
| 7 | Audit log cho hành động quan trọng | ✔ | Ghi nhật ký audit log cho QA, Wiki, và tài chính |
| 8 | Validation backend là nguồn chính | ✔ | Bean Validation + `@Valid` đầy đủ |
| 9 | Optimistic locking | ✔ | Sử dụng `@Version` trên các entities |
| 10 | Xử lý lỗi thống nhất | ✔ | GlobalExceptionHandler hoạt động tốt |
| 11 | Phân quyền đầy đủ | ✔ | Method security `@PreAuthorize` kết hợp directive `*appHasPermission` |

## 2. Review Backend

### 2.1 Kiến trúc & Giải thuật
- **Modular Monolith**: Tách biệt rõ ràng các package nghiệp vụ mới: `sprint`, `wiki`, `qa`, `git`.
- **Lập lịch CPM**: Giải thuật CPM Forward/Backward pass tương tác mượt mà với Sprint Boundary, đưa ra cảnh báo `WARNING_OUT_OF_SPRINT_BOUNDARY` chính xác nếu task vượt hạn sprint.
- **Mô hình EVM**: Triển khai scheduler `EvmScheduler.java` tính toán PV, EV, AC hàng ngày từ baseline.
- **Git Commit Regex Parser**: Trích xuất mã task qua regex chuẩn `^\[([A-Z0-9]+-TASK-\d+)\]\s*(.*)$`.

### 2.2 Bảo mật & Encryption
- **Xác thực Git Webhook**: Tính toán mã băm HMAC-SHA256 để xác thực signature từ GitHub/GitLab.
- **Mã hóa mức cột (Column-Level Encryption)**: Cột đơn giá nhân sự `hourly_rate` trong bảng `project_members` được mã hóa đối xứng **AES-256-GCM** tầng database, giải mã khi API gọi.

### 2.3 Chất lượng code
- Thiết kế clean, không TODO, không hardcode.
- Flyway migration đồng bộ hoàn toàn với schema database.

## 3. Review Frontend

### 3.1 Giao diện & Tích hợp (UI Integration)
- **Tích hợp các tab mới trong Chi tiết dự án**:
  - `sprints`: Giao diện quản lý backlog, kéo thả task vào sprint, kích hoạt/đóng sprint.
  - `wiki`: Soạn thảo tài liệu theo cây thư mục và wiki templates có sẵn.
  - `qa`: Quản lý Test Cases, Test Runs và cập nhật kết quả từng Test Step.
  - `finance`: Hiển thị chỉ số CPI/SPI và vẽ biểu đồ tiến trình EVM bằng SVG tự dựng.
- **Tích hợp tab Git trong Chi tiết Task**: Hiển thị danh sách commit và pull request liên kết.

### 3.2 Chất lượng & Tối ưu hóa
- **SCSS Budget**: Warning vượt budget SCSS đã được giải quyết triệt để nhờ tái cấu trúc các stylesheet dùng chung lên `styles.scss` toàn cục.
- **E2E Testing**: Tích hợp Playwright E2E framework và viết các spec file phủ toàn bộ kịch bản chính.

## 4. Kết luận

Toàn bộ các backlog của phiên bản v1.0 và v1.1 bao gồm: **SCSS budget warning, Playwright E2E framework, và xuất báo cáo CSV streaming** đều đã được giải quyết hoàn tất và kiểm chứng thành công. Hệ thống sẵn sàng cho v1.2.0 release.
