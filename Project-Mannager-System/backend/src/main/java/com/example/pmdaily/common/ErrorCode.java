package com.example.pmdaily.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Catalog mã lỗi nghiệp vụ (docs/design/05-error-handling-design.md muc 3).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu đã bị thay đổi, vui lòng tải lại"),
    DUPLICATE(HttpStatus.CONFLICT, "Dữ liệu đã tồn tại"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File vượt quá kích thước cho phép"),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "Không thể chuyển trạng thái này"),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoảng thời gian không hợp lệ"),
    NOT_PROJECT_MEMBER(HttpStatus.BAD_REQUEST, "Người thực hiện phải thuộc dự án"),
    PARENT_TASK_PROJECT_MISMATCH(HttpStatus.BAD_REQUEST, "Công việc cha phải cùng dự án"),
    CIRCULAR_PARENT(HttpStatus.BAD_REQUEST, "Không thể tạo vòng lặp công việc cha–con"),
    BLOCKER_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Phải nhập lý do blocker"),
    PROGRESS_REQUIRED_FOR_DONE(HttpStatus.BAD_REQUEST, "Tiến độ phải là 100 khi hoàn thành"),
    CODE_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể sinh mã, vui lòng thử lại"),
    ALREADY_LINKED(HttpStatus.CONFLICT, "Đối tượng đã được liên kết"),
    EXPORT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Dữ liệu vượt giới hạn xuất, hãy thu hẹp bộ lọc"),
    PROJECT_MANAGER_REQUIRED(HttpStatus.BAD_REQUEST, "Cần có ít nhất một PM cho dự án"),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),
    ACCOUNT_INACTIVE(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Tài khoản bị khóa tạm thời do nhập sai nhiều lần, vui lòng thử lại sau"),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đúng định dạng"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Thiếu tham số bắt buộc"),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Giá trị tham số không đúng kiểu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    INVALID_PARENT(HttpStatus.BAD_REQUEST, "Task lá không thể làm cha"),
    INVALID_PARENT_PLAN(HttpStatus.BAD_REQUEST, "Kế hoạch cha không hợp lệ hoặc không cùng loại"),
    HAS_CHILDREN(HttpStatus.BAD_REQUEST, "Đối tượng còn phần tử con, không thể xóa"),
    PLAN_NOT_APPROVED(HttpStatus.BAD_REQUEST, "Kế hoạch chưa được duyệt nên chưa thể thực hiện"),
    PLAN_VERSION_CONFLICT(HttpStatus.CONFLICT, "Phiên bản kế hoạch đã lỗi thời, vui lòng tải lại"),
    SELF_DEPENDENCY(HttpStatus.BAD_REQUEST, "Không thể tạo dependency giữa task với chính nó"),
    DEPENDENCY_CYCLE(HttpStatus.BAD_REQUEST, "Không thể tạo vòng lặp dependency"),
    CROSS_PROJECT_DEPENDENCY(HttpStatus.BAD_REQUEST, "Dependency phải thuộc cùng một kế hoạch"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống");

    private final HttpStatus status;
    private final String defaultMessage;
}
