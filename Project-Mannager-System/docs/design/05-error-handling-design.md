# Design 05 — Thiết kế xử lý lỗi (Error Handling Design)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 04/06, `docs/03-non-functional-requirements.md` (NFR-UX-03)

## 1. Cấu trúc error response (thống nhất)

```json
{
  "timestamp": "2026-08-01T10:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "path": "/api/v1/tasks",
  "fieldErrors": [
    { "field": "title", "message": "Tiêu đề không được để trống" }
  ],
  "traceId": "a1b2c3d4-..."
}
```

| Trường | Ý nghĩa |
|---|---|
| `timestamp` | ISO-8601 UTC thời điểm lỗi |
| `status` | HTTP status |
| `error` | Tên chuẩn HTTP (BAD_REQUEST...) |
| `code` | Mã lỗi nghiệp vụ (catalog mục 3) |
| `message` | Message tiếng Việt hiển thị được |
| `path` | Đường dẫn API |
| `fieldErrors` | Lỗi theo field (khi có) — `[]` nếu không có |
| `traceId` | ID truy vết request (MDC) |

Response thành công dạng list: `PageResponse { content, page, size, totalElements, totalPages, hasNext, hasPrevious }`. Response create/detail: trả thẳng DTO (không bọc `ApiResponse` ngoài) — nhất quán với Swagger; tránh wrapper thừa (BR-GEN-05 chỉ bắt buộc error thống nhất).

## 2. Ánh xạ exception → response (GlobalExceptionHandler)

| Exception | Status | `code` | Ghi chú |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | fieldErrors từ bean validation |
| `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | query param/body violation |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_JSON` | JSON sai định dạng/enum |
| `MissingServletRequestParameterException` | 400 | `MISSING_PARAMETER` | Thiếu param bắt buộc |
| `MethodArgumentTypeMismatchException` | 400 | `TYPE_MISMATCH` | Sai kiểu (VD UUID) |
| `BusinessException` (code riêng) | theo code | catalog mục 3 | Lỗi nghiệp vụ có message chuẩn |
| `ResourceNotFoundException` | 404 | `NOT_FOUND` | message kèm tên đối tượng |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` | Không đủ quyền |
| `AuthenticationException` | 401 | `UNAUTHORIZED` | Token sai/hết hạn |
| `OptimisticLockingFailureException` | 409 | `CONFLICT` | Version cũ |
| `DuplicateKeyException` (unique) | 409 | `DUPLICATE` | Mã/username/email trùng |
| `MaxUploadSizeExceededException` | 413 | `PAYLOAD_TOO_LARGE` | File quá giới hạn |
| `MethodNotAllowedException` | 405 | `METHOD_NOT_ALLOWED` | — |
| `NoHandlerFoundException` | 404 | `NOT_FOUND` | Route không tồn tại |
| Mọi exception khác | 500 | `INTERNAL_ERROR` | Ẩn chi tiết; log stack trace server-side |

## 3. Catalog mã lỗi nghiệp vụ (BusinessException)

| Mã | HTTP | Message mặc định (tiếng Việt) |
|---|---|---|
| `VALIDATION_ERROR` | 400 | "Dữ liệu không hợp lệ" |
| `BAD_REQUEST` | 400 | "Yêu cầu không hợp lệ" |
| `UNAUTHORIZED` | 401 | "Phiên đăng nhập không hợp lệ" |
| `ACCESS_DENIED` | 403 | "Bạn không có quyền thực hiện thao tác này" |
| `NOT_FOUND` | 404 | "Không tìm thấy dữ liệu" |
| `CONFLICT` | 409 | "Dữ liệu đã bị thay đổi, vui lòng tải lại" |
| `DUPLICATE` | 409 | "Dữ liệu đã tồn tại" |
| `PAYLOAD_TOO_LARGE` | 413 | "File vượt quá kích thước cho phép" |
| `INVALID_STATUS_TRANSITION` | 400 | "Không thể chuyển trạng thái này" |
| `INVALID_DATE_RANGE` | 400 | "Khoảng thời gian không hợp lệ" |
| `NOT_PROJECT_MEMBER` | 400 | "Người thực hiện phải thuộc dự án" |
| `PARENT_TASK_PROJECT_MISMATCH` | 400 | "Công việc cha phải cùng dự án" |
| `CIRCULAR_PARENT` | 400 | "Không thể tạo vòng lặp công việc cha–con" |
| `BLOCKER_REASON_REQUIRED` | 400 | "Phải nhập lý do blocker" |
| `PROGRESS_REQUIRED_FOR_DONE` | 400 | "Tiến độ phải là 100 khi hoàn thành" |
| `CODE_EXHAUSTED` | 500 | "Không thể sinh mã, vui lòng thử lại" |
| `ALREADY_LINKED` | 409 | "Đối tượng đã được liên kết" |
| `EXPORT_LIMIT_EXCEEDED` | 400 | "Dữ liệu vượt giới hạn xuất, hãy thu hẹp bộ lọc" |
| `PROJECT_MANAGER_REQUIRED` | 400 | "Cần có ít nhất một PM cho dự án" |
| `INVALID_LOGIN` | 401 | "Tên đăng nhập hoặc mật khẩu không đúng" |
| `ACCOUNT_INACTIVE` | 401 | "Tên đăng nhập hoặc mật khẩu không đúng" (giữ chung — BR-AUTH-05) |
| `ACCOUNT_LOCKED` | 423 | "Tài khoản bị khóa tạm thời, vui lòng thử lại sau" (BR-AUTH-08 — thêm ở Prompt 09) |

## 4. Trace ID

- Tạo UUID trong `OncePerRequestFilter`, đưa vào MDC (`traceId`) → gắn vào mọi log của request; dùng chung cho error response.
- `traceId` trả về client giúp đối chiếu log khi support.

## 5. Xử lý phía Frontend

| Tình huống | UI |
|---|---|
| 400 `VALIDATION_ERROR` có fieldErrors | Đặt lỗi vào từng control; snackbar message tổng |
| 400 không fieldErrors | Snackbar message |
| 401 sau refresh thất bại | Logout → `/auth/login` (không hiện lỗi) |
| 403 | Nếu mở trang → `/403`; nếu action → snackbar + ẩn nút |
| 404 | Snackbar; điều hướng `/404` khi cần |
| 409 CONFLICT | Dialog conflict → nút "Tải lại dữ liệu mới" |
| 413 | Snackbar "File quá lớn" |
| Network/0/timeout | Snackbar "Không thể kết nối máy chủ" + nút Retry |
| 500 | Snackbar "Đã xảy ra lỗi hệ thống" (không hiện stack trace) |

## 6. Quy ước bổ sung

1. Controller không tự catch Exception — để `GlobalExceptionHandler` xử lý; chỉ catch khi cần chuyển đổi nghiệp vụ cụ thể.
2. Không trả `Optional.get()` không kiểm tra — dùng `.orElseThrow(() -> new ResourceNotFoundException(...))`.
3. Không bắt `Exception` chung chung trong Service mà không xử lý.
4. Message lỗi: tiếng Việt, cụ thể, kèm field khi có thể; không lộ nội dung kỹ thuật.
5. Cấm log/trả về: password, token, JWT secret, stack trace ra client.
