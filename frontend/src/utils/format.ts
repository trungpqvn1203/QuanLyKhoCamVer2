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

/** "2026-10-19" (LocalDate từ backend, không có giờ) → "19/10/2026". Không qua Date để khỏi lệch múi giờ. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

const isoDateFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/**
 * Hôm nay theo giờ Việt Nam, dạng "YYYY-MM-DD" (en-CA định dạng sẵn kiểu này).
 * Không dùng new Date().toISOString(): đó là ngày UTC — trước 7h sáng ở VN vẫn ra "hôm qua".
 */
export function todayIso(now: Date = new Date()): string {
  return isoDateFormat.format(now)
}

const integerFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const tonsFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/** Số lượng nguyên nhóm hàng nghìn kiểu Việt Nam: 1200 → "1.200". */
export function formatQuantity(quantity: number): string {
  return integerFormat.format(quantity)
}

/** Tấn (BR-STK-02, backend đã làm tròn 3 chữ số lẻ): 1.25 → "1,25 tấn"; 3 → "3 tấn". */
export function formatTons(tons: number): string {
  return `${tonsFormat.format(tons)} tấn`
}

/** Ký hiệu thống nhất cho trường trống (trường tùy chọn bị bỏ khỏi JSON khi NULL — contract-spec). */
export const EMPTY_TEXT = '—'

/** Chuỗi trống / chỉ khoảng trắng / không có → "—", để bảng và khung chi tiết không có ô rỗng. */
export function orEmpty(value?: string | null): string {
  return value?.trim() ? value : EMPTY_TEXT
}
