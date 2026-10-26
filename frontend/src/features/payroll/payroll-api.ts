import { apiRequest } from '../../api/client'
import type { PaymentInput } from '../sales-orders/sales-order-api'

export interface PayrollLine {
  employeeId: number
  employeeCode: string
  employeeName: string
  employeeActive: boolean
  workUnits: number
  unloadTons: number
  deliveryTons: number
  /** Đơn giá dùng để tính: bản chốt = đơn giá LÚC CHỐT; tạm tính = đơn giá hiện tại (BR-PAY-02) */
  dailyWage?: number
  unloadTonRate?: number
  deliveryTonRate?: number
  /** BR-PAY-04: ba phần, mỗi phần làm tròn riêng; tổng = tổng ba phần */
  dayAmount: number
  unloadAmount: number
  deliveryAmount: number
  totalAmount: number
  /** BR-EPAY-01: đã trả / còn nợ — chỉ có nghĩa khi đã chốt (tạm tính luôn 0) */
  paidAmount: number
  outstandingAmount: number
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
  /** false = bảng lương chốt trước khi có công nợ nhân viên, coi như đã trả đủ (BR-EPAY-05) */
  tracksDebt: boolean
  totalAmount: number
  items: PayrollLine[]
  /** Các lần chốt đã hủy của tháng (mới nhất trước) */
  history: PayrollHistoryEntry[]
}

/** BR-EPAY-02: ADVANCE_DEDUCTION = trừ vào tạm ứng, không có tiền ra khỏi quỹ. */
export type PayrollPaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'ADVANCE_DEDUCTION'

export const PAYROLL_PAYMENT_METHOD_LABELS: Record<PayrollPaymentMethod, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  ADVANCE_DEDUCTION: 'Trừ tạm ứng',
}

/** Một dòng lịch sử trả lương. REVERSAL = hoàn một khoản trả ghi nhầm (BR-EPAY-04). */
export interface PayrollPayment {
  id: number
  type: 'PAYMENT' | 'REVERSAL'
  method: PayrollPaymentMethod
  amount: number
  paymentDate: string
  note?: string
  reversalOfId?: number
  /** Khoản trả này đã bị hoàn → không hoàn lại được */
  reversed: boolean
  createdAt: string
}

/** Tình trạng trả lương của một nhân viên trong một tháng đã chốt + số dư tạm ứng hiện có. */
export interface PayrollItemPayments {
  month: string
  employeeId: number
  employeeCode: string
  employeeName: string
  employeeActive: boolean
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
  tracksDebt: boolean
  advanceBalance: number
  payments: PayrollPayment[]
}

const base = (month: string) => `/api/payrolls/${month}`

export const payrollApi = {
  sheet: (month: string) => apiRequest<PayrollSheet>(base(month)),

  finalize: (month: string) => apiRequest<PayrollSheet>(`${base(month)}/finalize`, { method: 'POST' }),

  /** BR-PAY-03: bắt buộc lý do; bản cũ giữ lại trong history. */
  cancel: (month: string, reason: string) =>
    apiRequest<PayrollSheet>(`${base(month)}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }),
}

const itemBase = (month: string, employeeId: number) => `${base(month)}/items/${employeeId}`

export const payrollPaymentApi = {
  get: (month: string, employeeId: number) => apiRequest<PayrollItemPayments>(itemBase(month, employeeId)),

  /** Tiền mặt, chuyển khoản và/hoặc trừ tạm ứng; tổng không vượt số còn nợ (BR-EPAY-02). */
  pay: (month: string, employeeId: number, input: PaymentInput) =>
    apiRequest<PayrollItemPayments>(`${itemBase(month, employeeId)}/payments`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  /** Hoàn khoản trả ghi nhầm; lý do bắt buộc (BR-EPAY-04). */
  reverse: (month: string, employeeId: number, paymentId: number, reason: string) =>
    apiRequest<PayrollItemPayments>(`${itemBase(month, employeeId)}/payments/${paymentId}/reverse`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
}
