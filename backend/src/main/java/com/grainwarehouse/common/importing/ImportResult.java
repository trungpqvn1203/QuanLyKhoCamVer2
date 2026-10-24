package com.grainwarehouse.common.importing;

/** Kết quả nhập thành công: mọi dòng trong file đã được ghi. */
public record ImportResult(int importedCount) {
}
