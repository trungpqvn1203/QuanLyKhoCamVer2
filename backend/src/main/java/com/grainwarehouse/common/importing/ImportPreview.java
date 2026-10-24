package com.grainwarehouse.common.importing;

import java.util.List;

/** Kết quả kiểm tra file (chưa ghi DB). validRows = totalRows - số dòng có lỗi. */
public record ImportPreview(int totalRows, int validRows, List<ImportError> errors) {
}
