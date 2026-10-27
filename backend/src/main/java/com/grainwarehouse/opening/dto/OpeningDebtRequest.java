package com.grainwarehouse.opening.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Ghi nhận công nợ đầu kỳ theo lô (BR-ODB-04): mỗi dòng thành một chứng từ, tất cả trong một transaction.
 * Ngày chốt nợ chung cho cả lô; không gửi = hôm nay, không được ở tương lai.
 */
public record OpeningDebtRequest(
        LocalDate documentDate,

        @NotEmpty(message = "Phải có ít nhất một dòng công nợ")
        @Size(max = 1000, message = "Tối đa 1.000 dòng mỗi lần ghi nhận")
        @Valid
        List<OpeningDebtLineRequest> lines) {
}
