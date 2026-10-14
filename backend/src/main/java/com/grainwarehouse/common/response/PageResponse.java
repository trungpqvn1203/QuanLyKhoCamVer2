package com.grainwarehouse.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Dữ liệu phân trang theo contract-spec: { items: [...], pagination: {...} }.
 * Không trả thẳng Page của Spring ra JSON vì cấu trúc của nó dài và có thể đổi theo phiên bản.
 */
public record PageResponse<T>(List<T> items, PaginationInfo pagination) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<T> items = page.getContent().stream().map(mapper).toList();
        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
        return new PageResponse<>(items, pagination);
    }

    // @JsonProperty: tránh Jackson rút gọn "isFirst" thành "first" như với getter boolean
    public record PaginationInfo(
            int page,
            int size,
            long totalElements,
            int totalPages,
            @JsonProperty("isFirst") boolean isFirst,
            @JsonProperty("isLast") boolean isLast) {
    }
}
