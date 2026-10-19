package com.grainwarehouse.common.search;

/**
 * Tiêu chí tìm kiếm có ô từ khóa tự do (query param {@code keyword}).
 * Record tiêu chí có field {@code keyword} thì implement interface này
 * (SearchConventionTest kiểm tra) → dùng được {@link CommonSpecifications#keywordContains(HasKeyword, String...)}.
 * Cột nào được tìm do XxxSpecifications quyết định, không do client gửi lên.
 */
public interface HasKeyword {

    String keyword();
}
