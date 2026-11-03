package com.grainwarehouse.portal.dto;

import jakarta.validation.constraints.Size;

/** BR-POR-09: khách tự hủy — ghi chú tùy chọn, lý do "Khách hủy qua app" được ghi tự động. */
public record PortalCancelRequest(
        @Size(max = 300, message = "Ghi chú tối đa 300 ký tự")
        String note) {
}
