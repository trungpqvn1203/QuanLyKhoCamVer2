package com.grainwarehouse.auth.entity;

/**
 * Vai trò nghiệp vụ (overview.md, ADR-0003). Một người có thể kiêm nhiều vai trò — quyền = hợp quyền các vai trò.
 * Trong Spring Security mỗi vai trò thành authority "ROLE_" + tên (ví dụ ROLE_SALES_STAFF), hasRole("SALES_STAFF") khớp nó.
 */
public enum Role {
    /** Quản lý kho: nhà cung cấp, phiếu nhập, chấm công */
    WAREHOUSE_MANAGER,
    /** Nhân viên bán hàng: khách hàng, phiếu bán, thu tiền, công nợ */
    SALES_STAFF,
    /** Quản lý hệ thống: toàn quyền, danh mục/SP/kho, lương, người dùng */
    SYSTEM_ADMIN
}
