import { todayIso } from './format'

/**
 * Tháng dạng "YYYY-MM" (giống backend YearMonth). Tính bằng số, không qua Date để khỏi lệch múi giờ.
 * Dùng cho bảng công và bảng lương.
 */

/** Tháng hiện tại theo giờ Việt Nam. */
export function currentMonth(): string {
  return todayIso().slice(0, 7)
}

/** "2026-01" lùi 1 → "2025-12". */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthValue] = month.split('-').map(Number)
  const index = year * 12 + (monthValue - 1) + delta
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

/** "2026-10" → "10/2026" */
export function formatMonth(month: string): string {
  const [year, monthValue] = month.split('-')
  return `${monthValue}/${year}`
}

/** "2026-10" + 5 → "2026-10-05" */
export function dayIso(month: string, day: number): string {
  return `${month}-${String(day).padStart(2, '0')}`
}

const WEEKDAY_LABELS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

/** "2026-10-05" → "T2". Date.UTC: thứ của một NGÀY không phụ thuộc múi giờ máy người dùng. */
export function weekdayLabel(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return WEEKDAY_LABELS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]
}
