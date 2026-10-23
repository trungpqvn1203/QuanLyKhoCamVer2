package com.grainwarehouse.common.importing;

import lombok.Getter;

import java.util.List;

/**
 * Bước xác nhận nhập gặp dòng lỗi → không ghi dòng nào (transaction rollback)
 * → HTTP 400 VALIDATION_FAILED, details[].field dạng "rows[12].phone" (12 = số dòng Excel).
 */
@Getter
public class ImportFailedException extends RuntimeException {

    private final List<ImportError> errors;

    public ImportFailedException(List<ImportError> errors) {
        super("File có " + errors.size() + " lỗi, chưa nhập dòng nào. Hãy kiểm tra lại file");
        this.errors = List.copyOf(errors);
    }
}
