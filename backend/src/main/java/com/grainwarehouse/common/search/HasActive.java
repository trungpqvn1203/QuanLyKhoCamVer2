package com.grainwarehouse.common.search;

/**
 * Tiêu chí tìm kiếm có lọc trạng thái hoạt động (query param {@code active}: true/false, không gửi = tất cả).
 * Record tiêu chí có field {@code active} thì implement interface này (SearchConventionTest kiểm tra).
 */
public interface HasActive {

    Boolean active();
}
