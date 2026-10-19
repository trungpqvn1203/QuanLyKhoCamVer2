package com.grainwarehouse.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.ApiResponse.ApiErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Chuyển mọi exception thành envelope lỗi chuẩn (docs/api/contract-spec.md).
 * Nguyên tắc: không trả message nội bộ (SQL, stack trace) ra ngoài — chỉ ghi log.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE = "Dữ liệu gửi lên không hợp lệ";

    // Bean Validation trên @RequestBody (@NotBlank, @Size...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ApiErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE, details);
    }

    // JSON sai cú pháp hoặc sai kiểu (ví dụ unit = "TON" không có trong enum)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE,
                    List.of(new ApiErrorDetail(fieldPath(mie.getPath()), "Giá trị không hợp lệ")));
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Body JSON không hợp lệ", null);
    }

    // Dữ liệu sai chỉ service kiểm tra được (ngày tương lai, SP lặp dòng...) — cùng định dạng lỗi validation
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE,
                List.of(new ApiErrorDetail(ex.getField(), ex.getMessage())));
    }

    // Path/query param sai kiểu (ví dụ /api/products/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE,
                List.of(new ApiErrorDetail(ex.getName(), "Giá trị không hợp lệ")));
    }

    // Tham số sort trỏ tới thuộc tính không tồn tại (ví dụ ?sort=abc)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadSort(PropertyReferenceException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE,
                List.of(new ApiErrorDetail("sort", "Không thể sắp xếp theo '" + ex.getPropertyName() + "'")));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), null);
    }

    // URL không tồn tại
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy đường dẫn", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Phương thức " + ex.getMethod() + " không được hỗ trợ", null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return error(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", ex.getMessage(), null);
    }

    // Tuyến phòng thủ cuối: DB chặn (UNIQUE/CHECK/FK) khi service chưa kiểm tra kịp,
    // ví dụ hai request tạo cùng tên sản phẩm đồng thời
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc DB: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "Dữ liệu vi phạm ràng buộc toàn vẹn", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Lỗi chưa xử lý", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại sau", null);
    }

    // Đường dẫn JSON → tên field giống Bean Validation: lines → [0] → orderedQuantity = "lines[0].orderedQuantity"
    // (phần tử mảng không có tên field, chỉ có index)
    private static String fieldPath(List<JsonMappingException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() == null) {
                field.append('[').append(ref.getIndex()).append(']');
            } else {
                field.append(field.isEmpty() ? "" : ".").append(ref.getFieldName());
            }
        }
        return field.toString();
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message,
                                                    List<ApiErrorDetail> details) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message, details));
    }
}
