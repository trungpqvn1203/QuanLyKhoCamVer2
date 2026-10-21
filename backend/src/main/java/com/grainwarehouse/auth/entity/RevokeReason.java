package com.grainwarehouse.auth.entity;

/** Lý do thu hồi refresh token — khớp CHECK ck_refresh_token_reason (V14). */
public enum RevokeReason {
    /** Người dùng bấm đăng xuất trên thiết bị đó */
    LOGOUT,
    /** Token đã bị thay thế lại được dùng sau thời gian ân hạn → nghi bị trộm, thu hồi cả family */
    REUSE_DETECTED,
    /** Người dùng tự đổi mật khẩu → thu hồi các phiên khác */
    PASSWORD_CHANGED,
    /** Quản lý hệ thống khóa tài khoản, đổi vai trò hoặc đặt lại mật khẩu */
    ADMIN_ACTION
}
