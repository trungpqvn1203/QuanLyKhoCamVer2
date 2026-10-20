import { apiRequest } from '../../api/client'

export interface PayrollLine {
  employeeId: number
  employeeCode: string
  employeeName: string
  employeeActive: boolean
  workUnits: number
  tons: number
  /** Đơn giá dùng để tính: bản chốt = đơn giá LÚC CHỐT; tạm tính = đơn giá hiện tại (BR-PAY-02) */
  dailyWage?: number
  tonRate?: number
  dayAmount: number
  tonAmount: number
  totalAmount: number
}

export interface PayrollHistoryEntry {
  payrollId: number
  status: 'CANCELLED'
  totalAmount: number
  finalizedAt: string
  cancelledAt: string
  cancelReason: string
}

export interface PayrollSheet {
  /** "YYYY-MM" */
  month: string
  /** false = tạm tính từ chấm công hiện tại; true = bản đã chốt, chấm công tháng này bị khóa (BR-PAY-03) */
  finalized: boolean
  payrollId?: number
  finalizedAt?: string
  totalAmount: number
  items: PayrollLine[]
  /** Các lần chốt đã hủy của tháng (mới nhất trước) */
  history: PayrollHistoryEntry[]
}

const base = (month: string) => `/api/payrolls/${month}`

export const payrollApi = {
  sheet: (month: string) => apiRequest<PayrollSheet>(base(month)),

  finalize: (month: string) => apiRequest<PayrollSheet>(`${base(month)}/finalize`, { method: 'POST' }),

  /** BR-PAY-03: bắt buộc lý do; bản cũ giữ lại trong history. */
  cancel: (month: string, reason: string) =>
    apiRequest<PayrollSheet>(`${base(month)}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }),
}
