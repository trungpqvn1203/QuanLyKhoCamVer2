/**
 * Định dạng hiển thị dùng chung — chỉ dùng Intl có sẵn trong trình duyệt, không cần thư viện.
 * Múi giờ cố định Việt Nam: chuỗi phân phối hoạt động trong nước, máy người dùng
 * đặt sai múi giờ cũng không làm lệch giờ hiển thị.
 */
const TIME_ZONE = 'Asia/Ho_Chi_Minh'

const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone: TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

const timeFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone: TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/**
 * "2026-10-16T03:05:00Z" (Instant từ backend, giờ UTC) → "16/10/2026 10:05" (giờ Việt Nam).
 * Ghép ngày trước giờ sau: định dạng mặc định của vi-VN đặt giờ lên trước, khó dò trong bảng.
 */
export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  return `${dateFormat.format(date)} ${timeFormat.format(date)}`
}
