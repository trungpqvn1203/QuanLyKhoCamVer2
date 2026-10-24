package com.grainwarehouse.common.importing;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nhập hàng loạt từ Excel theo hai bước (docs/business/rules.md — BR-XLS):
 * 1. preview: kiểm tra MỌI dòng, trả danh sách lỗi, không ghi gì.
 * 2. commit: kiểm tra lại từ đầu (dữ liệu có thể đã đổi kể từ lúc preview), không lỗi thì ghi TẤT CẢ trong một
 *    transaction; một dòng lỗi → không ghi dòng nào.
 * Mỗi dòng đi qua đúng các kiểm tra của form nhập tay: Bean Validation của request + kiểm tra nghiệp vụ của service.
 */
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    /** BR-XLS-04. Đổi số này thì sửa cả hướng dẫn trong file mẫu (tự cập nhật) và tài liệu. */
    public static final int MAX_ROWS = 1000;

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final Validator validator;

    /** File mẫu .xlsx dạng tải về (Content-Disposition: attachment) — không bọc ApiResponse vì không phải JSON. */
    public ResponseEntity<byte[]> template(ImportDefinition<?, ?> definition) {
        byte[] content = ExcelWorkbooks.template(definition, definition.columns(), MAX_ROWS);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(definition.fileName()).build().toString())
                .body(content);
    }

    /**
     * Không đặt @Transactional ở đây: prepare() của từng dòng chạy trong transaction chỉ-đọc riêng của service.
     * Nếu bọc chung một transaction, exception nghiệp vụ đầu tiên (ví dụ tên trùng) đánh dấu cả transaction
     * "rollback-only" → kết thúc hàm sẽ lỗi UnexpectedRollbackException thay vì trả danh sách lỗi.
     */
    public <R, E> ImportPreview preview(ImportDefinition<R, E> definition, MultipartFile file) {
        Evaluation<E> result = evaluate(definition, file);
        int rowsWithErrors = (int) result.errors().stream().map(ImportError::row).distinct().count();
        return new ImportPreview(result.totalRows(), result.totalRows() - rowsWithErrors, result.errors());
    }

    /**
     * Một transaction cho cả file. Kiểm tra hết trước, ghi sau: lỗi được phát hiện trước khi có câu INSERT nào.
     * Hai người cùng nhập một tên giữa lúc kiểm tra và lúc ghi → UNIQUE của DB chặn → rollback cả file (409).
     */
    @Transactional
    public <R, E> ImportResult commit(ImportDefinition<R, E> definition, MultipartFile file) {
        Evaluation<E> result = evaluate(definition, file);
        if (!result.errors().isEmpty()) {
            throw new ImportFailedException(result.errors());
        }
        definition.saveAll(result.prepared());
        return new ImportResult(result.prepared().size());
    }

    private <R, E> Evaluation<E> evaluate(ImportDefinition<R, E> definition, MultipartFile file) {
        List<ImportColumn> columns = definition.columns();
        List<ImportRow> rows;
        try (InputStream input = file.getInputStream()) {
            rows = ExcelWorkbooks.read(input, definition, columns, MAX_ROWS);
        } catch (IOException e) {
            throw new InvalidRequestException("file", "Không đọc được file đã tải lên");
        }

        List<ImportError> errors = new ArrayList<>();
        List<E> prepared = new ArrayList<>();
        // (field, khóa so trùng) → số dòng xuất hiện ĐẦU TIÊN, để báo "trùng với dòng 5"
        Map<String, Map<String, Integer>> seen = new HashMap<>();

        for (ImportRow row : rows) {
            R request = definition.parse(row);
            for (ConstraintViolation<R> violation : validator.validate(request)) {
                String field = violation.getPropertyPath().toString();
                // Ô sai kiểu đã có lỗi riêng ("phải là số") — bỏ lỗi "không được để trống" sinh ra do field bị null
                if (!row.hasError(field)) {
                    row.error(field, violation.getMessage());
                }
            }
            if (!row.hasErrors()) {
                checkDuplicateInFile(definition, request, row, seen);
            }
            if (!row.hasErrors()) {
                prepare(definition, request, row).ifPresent(prepared::add);
            }
            errors.addAll(row.errors());
        }
        return new Evaluation<>(rows.size(), errors, prepared);
    }

    private <R> void checkDuplicateInFile(ImportDefinition<R, ?> definition, R request, ImportRow row,
                                          Map<String, Map<String, Integer>> seen) {
        definition.duplicateKeys(request).forEach((field, key) -> {
            if (key == null) {
                return;
            }
            Integer firstRow = seen.computeIfAbsent(field, f -> new HashMap<>()).putIfAbsent(key, row.number());
            if (firstRow != null) {
                row.error(field, "Trùng với dòng " + firstRow + " trong file");
            }
        });
    }

    /** Exception của service (đúng thông báo như khi tạo tay) → lỗi của dòng, gắn vào cột liên quan nếu biết. */
    private <R, E> Optional<E> prepare(ImportDefinition<R, E> definition, R request, ImportRow row) {
        try {
            return Optional.of(definition.prepare(request));
        } catch (InvalidRequestException e) {
            row.error(e.getField(), e.getMessage());
        } catch (DuplicateResourceException e) {
            row.error(e.getField(), e.getMessage());
        } catch (BusinessException | ResourceNotFoundException e) {
            row.error(null, e.getMessage());
        }
        return Optional.empty();
    }

    private record Evaluation<E>(int totalRows, List<ImportError> errors, List<E> prepared) {
    }
}
