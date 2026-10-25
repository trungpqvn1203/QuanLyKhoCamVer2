import { apiRequest } from '../../api/client'
import type { PaymentInput, PaymentMethod } from '../sales-orders/sales-order-api'

/** Một tháng lương chưa trả đủ (BR-EPAY-01). month dạng "YYYY-MM". */
export interface UnpaidMonth {
  month: string
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
}

/**
 * Công nợ hai chiều của một nhân viên (BR-EPAY-01, 03): lương công ty còn nợ + tạm ứng nhân viên còn nợ.
 * netAmount = lương chưa trả − dư tạm ứng; dương = công ty nợ nhân viên, âm = nhân viên đang giữ tạm ứng nhiều hơn.
 */
export interface EmployeeDebt {
  employeeId: number
  employeeCode: string
  employeeName: string
  employeeActive: boolean
  unpaidSalaryAmount: number
  advanceBalance: number
  netAmount: number
  unpaidMonths: UnpaidMonth[]
}

/** Một dòng lịch sử tạm ứng. REVERSAL = hoàn một khoản ứng ghi nhầm (BR-EPAY-04). */
export interface EmployeeAdvance {
  id: number
  type: 'ADVANCE' | 'REVERSAL'
  method: PaymentMethod
  amount: number
  advanceDate: string
  note?: string
  reversalOfId?: number
  /** Khoản ứng này đã bị hoàn → không hoàn lại được */
  reversed: boolean
  createdAt: string
}

/** Số dư tạm ứng hiện có của một nhân viên và lịch sử ứng / hoàn (mới nhất trước). */
export interface AdvanceLedger {
  employeeId: number
  employeeCode: string
  employeeName: string
  employeeActive: boolean
  advanceBalance: number
  advances: EmployeeAdvance[]
}

export const employeeDebtApi = {
  /** Nhân viên đang còn lương chưa trả hoặc còn dư tạm ứng, lương chưa trả nhiều nhất lên đầu (không phân trang). */
  list: () => apiRequest<EmployeeDebt[]>('/api/employee-debts'),
}

const advanceBase = (employeeId: number) => `/api/employees/${employeeId}/advances`

export const advanceApi = {
  get: (employeeId: number) => apiRequest<AdvanceLedger>(advanceBase(employeeId)),

  /** Ứng tiền mặt và/hoặc chuyển khoản (BR-EPAY-03); paymentDate là ngày ứng. */
  record: (employeeId: number, input: PaymentInput) =>
    apiRequest<AdvanceLedger>(advanceBase(employeeId), { method: 'POST', body: JSON.stringify(input) }),

  /** Hoàn khoản ứng ghi nhầm; lý do bắt buộc. Số dư tạm ứng phải còn đủ (BR-EPAY-04). */
  reverse: (employeeId: number, advanceId: number, reason: string) =>
    apiRequest<AdvanceLedger>(`${advanceBase(employeeId)}/${advanceId}/reverse`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
}
