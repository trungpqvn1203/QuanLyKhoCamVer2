import { apiRequest, withQuery } from '../../api/client'

/** BR-ATT-01 — phải khớp enum DayStatus ở backend. */
export type DayStatus = 'FULL' | 'HALF' | 'ABSENT'

export const DAY_STATUS_LABELS: Record<DayStatus, string> = {
  FULL: 'Cả ngày',
  HALF: 'Nửa ngày',
  ABSENT: 'Nghỉ',
}

/** Ký hiệu trong ô bảng công — kiểu bảng chấm công giấy quen thuộc: X đi làm, ½ nửa ngày, N nghỉ. */
export const DAY_STATUS_SYMBOLS: Record<DayStatus, string> = {
  FULL: 'X',
  HALF: '½',
  ABSENT: 'N',
}

export interface AttendanceDay {
  employeeId: number
  /** "YYYY-MM-DD" */
  date: string
  status: DayStatus
  tons: number
  note?: string
}

export interface AttendanceRow {
  employeeId: number
  employeeCode: string
  employeeName: string
  active: boolean
  /** Có lương ngày / đơn giá tấn không (BR-EMP-02) — không có thì phần đó không tính tiền */
  paysByDay: boolean
  paysByTon: boolean
  totalWorkUnits: number
  totalTons: number
  /** Chỉ các ngày ĐÃ chấm; ngày vắng mặt trong danh sách = chưa chấm (khác "Nghỉ") */
  days: AttendanceDay[]
}

export interface MonthlyAttendance {
  /** "YYYY-MM" */
  month: string
  daysInMonth: number
  /** Hôm nay theo giờ VN do backend trả — ô sau ngày này bị khóa (BR-ATT-02) */
  today: string
  employees: AttendanceRow[]
}

export interface AttendanceInput {
  status: DayStatus
  tons?: number
  note?: string
}

/** "2026-10" + 5 → "2026-10-05" */
export function dayIso(month: string, day: number): string {
  return `${month}-${String(day).padStart(2, '0')}`
}

/** "2026-01" lùi 1 → "2025-12". Tính bằng số, không qua Date để khỏi lệch múi giờ. */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthValue] = month.split('-').map(Number)
  const index = year * 12 + (monthValue - 1) + delta
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

const WEEKDAY_LABELS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

/** "2026-10-05" → "T2". Date.UTC: thứ của một NGÀY không phụ thuộc múi giờ máy người dùng. */
export function weekdayLabel(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return WEEKDAY_LABELS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]
}

const BASE = '/api/attendance'

export const attendanceApi = {
  month: (month: string) => apiRequest<MonthlyAttendance>(withQuery(BASE, { month })),

  /** PUT ghi đè cả ô (tạo nếu chưa có). */
  record: (employeeId: number, date: string, input: AttendanceInput) =>
    apiRequest<AttendanceDay>(`${BASE}/${employeeId}/${date}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Xóa ô ghi nhầm → ngày đó trở về "chưa chấm". */
  clear: (employeeId: number, date: string) =>
    apiRequest<void>(`${BASE}/${employeeId}/${date}`, { method: 'DELETE' }),
}
