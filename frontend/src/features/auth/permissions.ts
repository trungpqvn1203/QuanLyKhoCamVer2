import type { CurrentUser, Role } from './auth-api'

/**
 * Bản sao ma trận phân quyền của backend (ADR-0003 mục 5, SecurityConfig.accessRules) — CHỈ để ẩn menu/nút cho
 * gọn. Quyền thật kiểm ở backend: sửa ở đây mà không sửa backend thì người dùng chỉ thấy nút rồi bị 403.
 * Quyền xem mà mọi vai trò đều có (sản phẩm, kho, khách hàng, phiếu bán, tồn kho) không cần khai báo.
 */
export type Permission =
  | 'catalog.write' // danh mục, sản phẩm, kho: thêm / sửa / ngừng
  | 'suppliers' // nhà cung cấp, liên kết NCC–SP
  | 'inbound' // phiếu nhập
  | 'customers.write' // khách hàng, bảng giá riêng
  | 'sales.write' // phiếu bán, thu tiền
  | 'debts' // công nợ
  | 'attendance'
  | 'employees'
  | 'payroll'
  | 'cashbook' // sổ quỹ, phiếu thu/chi khác (BR-CASH-07)
  | 'opening' // số dư đầu kỳ: tồn kho (BR-OPN-09), ghi nhận / đảo công nợ đầu kỳ (BR-ODB-08)
  | 'stockcount' // kiểm kê kho, lý do lệch (BR-CNT-10)
  | 'users'

const GRANTS: Record<Permission, Role[]> = {
  'catalog.write': ['SYSTEM_ADMIN'],
  suppliers: ['WAREHOUSE_MANAGER', 'SYSTEM_ADMIN'],
  inbound: ['WAREHOUSE_MANAGER', 'SYSTEM_ADMIN'],
  'customers.write': ['SALES_STAFF', 'SYSTEM_ADMIN'],
  'sales.write': ['SALES_STAFF', 'SYSTEM_ADMIN'],
  debts: ['SALES_STAFF', 'SYSTEM_ADMIN'],
  attendance: ['WAREHOUSE_MANAGER', 'SYSTEM_ADMIN'],
  employees: ['SYSTEM_ADMIN'],
  payroll: ['SYSTEM_ADMIN'],
  cashbook: ['SYSTEM_ADMIN'],
  opening: ['SYSTEM_ADMIN'],
  stockcount: ['SYSTEM_ADMIN'],
  users: ['SYSTEM_ADMIN'],
}

/** Kiêm nhiều vai trò → có quyền nếu MỘT trong các vai trò có quyền đó. */
export function can(user: CurrentUser | null, permission: Permission): boolean {
  return user !== null && user.roles.some((role) => GRANTS[permission].includes(role))
}
