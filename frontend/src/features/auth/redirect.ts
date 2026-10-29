/** Trang đầu sau đăng nhập: Tổng quan (BR-RPT-01) */
export const HOME_PATH = '/dashboard'

/**
 * ?redirect=... sau đăng nhập chỉ nhận đường dẫn NỘI BỘ. Nhận cả URL ngoài thì kẻ gian gửi link
 * "kho.vn/login?redirect=https://kho-gia.vn" — người dùng đăng nhập thật xong bị đưa sang trang giả (open redirect).
 * "//kho-gia.vn" cũng là URL ngoài (trình duyệt hiểu là cùng giao thức, khác tên miền).
 */
export function safeRedirect(target: string | null): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) return HOME_PATH
  if (target.startsWith('/login')) return HOME_PATH
  return target
}
