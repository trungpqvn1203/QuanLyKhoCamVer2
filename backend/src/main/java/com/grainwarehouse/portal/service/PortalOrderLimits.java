package com.grainwarehouse.portal.service;

/**
 * Giới hạn đơn khách tự đặt qua app (BR-POR-12) — chặn một tài khoản (hoặc người lấy được mật khẩu của khách) làm
 * ngập danh sách Chờ xác nhận của cửa hàng. Chỉ áp cho app; nhân viên ghi đơn hộ không bị giới hạn.
 * Hằng số (không phải cấu hình) vì @Max / @Size cần giá trị lúc biên dịch.
 */
public final class PortalOrderLimits {

    /** Số đơn app đang Chờ xác nhận tối đa của một khách. */
    public static final int MAX_PENDING_ORDERS = 5;
    public static final int MAX_LINES = 50;
    public static final int MAX_LINE_QUANTITY = 9999;
    public static final String MAX_LINE_QUANTITY_TEXT = "9.999";
    /** Ngày muốn giao: từ hôm nay đến tối đa ngần này ngày tới (giờ Việt Nam). */
    public static final int MAX_DELIVERY_DAYS_AHEAD = 60;

    private PortalOrderLimits() {
    }
}
