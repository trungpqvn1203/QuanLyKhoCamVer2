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
  /** Tháng đã chốt lương → bảng công chỉ xem (BR-PAY-03) */
  payrollFinalized: boolean
  employees: AttendanceRow[]
}

export interface AttendanceInput {
  status: DayStatus
  tons?: number
  note?: string
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
