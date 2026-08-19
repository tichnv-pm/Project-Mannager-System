# 03 — Yêu cầu phi chức năng (Non-Functional Requirements)

> Dự án: PM Daily Work Management
> Quy ước ID: `NFR-<NHÓM>-<NN>`.

## 1. Hiệu năng (NFR-PERF)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-PERF-01 | API thường dùng phải phản hồi nhanh | Dashboard và danh sách task/meeting/risk/issue trả về ≤ 2s với dữ liệu ≤ 10.000 task/project trong môi trường local Docker |
| NFR-PERF-02 | Danh sách có phân trang | Mọi danh sách dùng `page/size`; size tối đa 100 |
| NFR-PERF-03 | Dashboard/Report không load toàn bộ dữ liệu | Số liệu aggregate tại DB (COUNT/GROUP BY), không N+1; cấm vòng lặp truy vấn theo từng dòng |
| NFR-PERF-04 | Export có giới hạn | Export tối đa 10.000 dòng; vượt giới hạn thì từ chối và yêu cầu thu hẹp filter |
| NFR-PERF-05 | Tránh N+1 | Các endpoint trả list/detail được review bởi query plan; không truy vấn theo từng bản ghi trong vòng lặp |

## 2. Bảo mật (NFR-SEC)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-SEC-01 | Mật khẩu | Hash BCrypt (strength ≥ 10); không lưu/lọc/trả password hash qua API |
| NFR-SEC-02 | JWT | Access token sống ngắn (15 phút mặc định); refresh token sống dài hơn (7 ngày) và lưu DB, có revoke; secret đọc từ environment variable, không commit |
| NFR-SEC-03 | Không lộ thông tin | Login thất bại trả message chung, không tiết lộ username có tồn tại; không log token/password |
| NFR-SEC-04 | Authorization | Permission-based (method security); mọi endpoint không công khai đều kiểm tra quyền; 403 cho đúng role; kiểm tra cả phạm vi dữ liệu (project membership) |
| NFR-SEC-05 | Phòng chống cơ bản | Parameterized query chống SQL injection (JPA/Specification); escape/encode chống XSS ở Frontend; không tin input, validate tất cả field |
| NFR-SEC-06 | CORS | Chỉ cho phép origin cấu hình được; không dùng `*` với credentials |
| NFR-SEC-07 | File upload | Giới hạn kích thước (10MB/file), whitelist mime type; kiểm tra nội dung file |
| NFR-SEC-08 | Session bảo mật | Đổi mật khẩu/reset → revoke refresh tokens; refresh token bị revoke không dùng lại được |

## 3. Độ tin cậy & khả dụng (NFR-REL)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-REL-01 | Nhất quán dữ liệu | Giao dịch có transaction boundary rõ ràng; lỗi giữa chừng phải rollback |
| NFR-REL-02 | Chống mất dữ liệu do cập nhật đồng thời | Optimistic locking (`version`) trên mọi entity nghiệp vụ; cập nhật version cũ trả 409 |
| NFR-REL-03 | Khởi động lại an toàn | Flyway migration chạy idempotent; dữ liệu seed local chỉ áp dụng cho profile local |
| NFR-REL-04 | Job sinh notification không trùng | Dedupe theo (recipient, type, taskId, ngày); chạy lặp cùng ngày không tạo bản ghi mới |

## 4. Khả dụng (NFR-UX)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-UX-01 | Responsive | UI chạy tốt trên desktop và tablet (≥ 768px); không bắt buộc mobile phone |
| NFR-UX-02 | Trạng thái UI | Mọi danh sách có loading, empty state, error state kèm retry |
| NFR-UX-03 | Phản hồi lỗi | Lỗi API hiển thị message rõ ràng theo từng trường; không hiện stack trace cho người dùng |
| NFR-UX-04 | Chống thao tác lặp | Chống double submit khi tạo/sửa; xóa phải confirm dialog |
| NFR-UX-05 | Ngôn ngữ | UI/message tiếng Việt (chưa cần đa ngôn ngữ) |

## 5. Bảo trì (NFR-MNT)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-MNT-01 | Kiến trúc module rõ ràng | Backend modular monolith: mỗi module có controller/service/repository/entity/dto/mapper; không import chéo giữa module nghiệp vụ ngoài phạm vi cho phép |
| NFR-MNT-02 | DTO + MapStruct | Không trả Entity qua API; mapper tập trung |
| NFR-MNT-03 | Cấu hình tách profile | `local` / `test` / mặc định; secret qua environment variable |
| NFR-MNT-04 | Log có trace | Mỗi request có traceId; log đúng level; không log token/password/secret |
| NFR-MNT-05 | Migration có kiểm soát | Mọi thay đổi schema qua Flyway; không sửa file migration đã chạy |
| NFR-MNT-06 | Không nợ kỹ thuật | Không TODO, không mock production, không code chết, không duplicate logic nghiêm trọng |

## 6. Tương thích & môi trường (NFR-COMPAT)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-COMPAT-01 | Backend | Java 21, Spring Boot 3.x, Maven build được trên Windows |
| NFR-COMPAT-02 | Frontend | Angular stable tương thích Node hiện tại; chạy được trên Chrome/Firefox/Edge mới nhất |
| NFR-COMPAT-03 | DB | PostgreSQL 16 (Docker image đã chốt) |
| NFR-COMPAT-04 | Docker | Docker Compose chạy trên Windows (Docker Desktop) với đủ healthcheck giữa các service |
| NFR-COMPAT-05 | Cổng mạng | Mặc định: PostgreSQL 5432, Backend 8080, Frontend 4200 (dev) / 80 (Nginx) — có thể đổi qua env |

## 7. Ngày giờ & múi giờ (NFR-TZ)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-TZ-01 | Lưu trữ UTC | Mọi trường ngày giờ lưu `timestamptz` (UTC) ở DB |
| NFR-TZ-02 | Trao đổi API | JSON ISO-8601 kèm offset (VD `2026-08-01T10:00:00Z` hoặc `+07:00`) |
| NFR-TZ-03 | Hiển thị | UI hiển thị theo múi giờ trình duyệt người dùng; "hôm nay" của dashboard/today tính theo timezone người dùng |
| NFR-TZ-04 | Thống nhất | Không lưu 2 dạng ngày trong cùng bảng; không so sánh ngày bằng string |

## 8. Logging & Audit (NFR-LOG)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-LOG-01 | Trace ID | Global exception handler và log gắn traceId; error response có traceId |
| NFR-LOG-02 | Audit log | Ghi nhật ký hành động quan trọng: login/logout, tạo/sửa/xóa dữ liệu nghiệp vụ, thay đổi phân quyền; lưu before/after dạng JSONB |
| NFR-LOG-03 | Cấm log nhạy cảm | Không log mật khẩu, token, refresh token, secret |
| NFR-LOG-04 | Audit truy vấn được | Admin có màn hình xem audit log, lọc theo user/hành động/thời gian |

## 9. Khả năng kiểm thử (NFR-TEST)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-TEST-01 | Unit test | Service/utility có unit test (JUnit 5 + Mockito); component/service Angular có test |
| NFR-TEST-02 | Integration test | Controller + repository test bằng Spring Boot Test (profile test, DB test); Testcontainers nếu cần |
| NFR-TEST-03 | Không bỏ qua test | Test fail phải sửa root cause; không skip/xóa test để build pass |
| NFR-TEST-04 | Swagger | API document tự sinh (springdoc), mọi endpoint có mô tả |
| NFR-TEST-05 | Smoke test | Có script smoke test (curl/PowerShell) cho luồng chính trên local |

## 10. Tài liệu (NFR-DOC)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-DOC-01 | Nguồn sự thật | `docs/` là nguồn chính; code thay đổi phải cập nhật tài liệu tương ứng |
| NFR-DOC-02 | README | Hướng dẫn cài đặt/chạy local (Windows), tài khoản demo, lệnh test/build |
| NFR-DOC-03 | Runbook | Có runbook xử lý lỗi thường gặp (port, migration, CORS, Docker) |

## 11. Bảo mật dữ liệu & vòng đời (NFR-DATA)

| ID | Yêu cầu | Tiêu chí chấp nhận |
|---|---|---|
| NFR-DATA-01 | Soft delete | Dữ liệu nghiệp vụ xóa mềm, không xóa vật lý; truy vấn mặc định loại bỏ dữ liệu đã xóa |
| NFR-DATA-02 | Seed local | Seed data chỉ chạy cho profile local, dùng password demo công khai, không dùng cho production |
| NFR-DATA-03 | Không lưu secret | Không lưu secret trong DB hoặc repo; `.env` không commit |
