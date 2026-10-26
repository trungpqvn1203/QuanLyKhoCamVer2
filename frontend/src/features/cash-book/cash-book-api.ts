import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'

/** BR-CASH-01: một quỹ tiền mặt + một tài khoản ngân hàng. */
export type CashAccount = 'CASH' | 'BANK'

export const CASH_ACCOUNT_LABELS: Record<CashAccount, string> = {
  CASH: 'Tiền mặt',
  BANK: 'Tài khoản',
}

/** IN = thu vào, OUT = chi ra. */
export type CashDirection = 'IN' | 'OUT'

export const CASH_DIRECTION_LABELS: Record<CashDirection, string> = {
  IN: 'Thu',
  OUT: 'Chi',
}

/** Nguồn một dòng sổ quỹ (BR-CASH-02) — khớp VIEW cash_flow_entry ở backend. */
export type CashEntrySource = 'SALE_PAYMENT' | 'SUPPLIER_PAYMENT' | 'PAYROLL_PAYMENT' | 'EMPLOYEE_ADVANCE' | 'CASH_VOUCHER'

export const CASH_SOURCE_LABELS: Record<CashEntrySource, string> = {
  SALE_PAYMENT: 'Thu tiền khách',
  SUPPLIER_PAYMENT: 'Trả NCC',
  PAYROLL_PAYMENT: 'Trả lương',
  EMPLOYEE_ADVANCE: 'Tạm ứng',
  CASH_VOUCHER: 'Phiếu thu/chi',
}

export interface OpeningChange {
  oldStartDate?: string
  oldOpeningCash?: number
  oldOpeningBank?: number
  newStartDate: string
  newOpeningCash: number
  newOpeningBank: number
  changedBy: string
  changedAt: string
}

/** Đầu kỳ hiện tại + lịch sử đặt/sửa (mới nhất trước). configured = false → chưa đặt. */
export interface CashBookOpening {
  configured: boolean
  startDate?: string
  openingCash?: number
  openingBank?: number
  updatedAt?: string
  changes: OpeningChange[]
}

export interface OpeningInput {
  startDate: string
  openingCash: number
  openingBank: number
}

export interface AccountSummary {
  account: CashAccount
  balance: number
  todayIn: number
  todayOut: number
  /** Số dư âm → cảnh báo đỏ, không chặn khoản chi (BR-CASH-06) */
  negative: boolean
}

export interface CashBookSummary {
  configured: boolean
  today: string
  startDate?: string
  cash?: AccountSummary
  bank?: AccountSummary
}

/** Một dòng sổ quỹ. amount có dấu (+ thu, − chi); balance vắng mặt khi lọc theo nguồn. */
export interface CashEntry {
  source: CashEntrySource
  sourceId: number
  account: CashAccount
  entryDate: string
  amount: number
  direction: CashDirection
  reference?: string
  counterparty?: string
  category?: string
  note?: string
  /** Dòng hoàn */
  reversal: boolean
  /** Khoản gốc đã bị hoàn */
  reversed: boolean
  createdAt: string
  balance?: number
}

export interface CashLedger {
  account: CashAccount
  startDate: string
  from: string
  to: string
  source?: CashEntrySource
  openingBalance: number
  totalIn: number
  totalOut: number
  closingBalance: number
  negative: boolean
  entries: CashEntry[]
}

export interface LedgerQuery {
  account: CashAccount
  from?: string
  to?: string
  source?: CashEntrySource
}

export interface CashCategory {
  id: number
  name: string
  direction: CashDirection
  active: boolean
}

export interface CashVoucher {
  id: number
  code: string
  type: 'VOUCHER' | 'REVERSAL'
  direction: CashDirection
  account: CashAccount
  categoryId: number
  categoryName: string
  amount: number
  voucherDate: string
  counterparty?: string
  note?: string
  reversalOfId?: number
  reversalOfCode?: string
  reversed: boolean
  createdAt: string
}

export interface VoucherInput {
  direction: CashDirection
  account: CashAccount
  categoryId: number
  amount: number
  voucherDate?: string
  counterparty?: string
  note?: string
}

export type VoucherFilters = {
  keyword?: string
  direction?: CashDirection
  account?: CashAccount
}

export const cashBookApi = {
  summary: () => apiRequest<CashBookSummary>('/api/cash-book/summary'),

  /** Sổ quỹ một tài khoản; không gửi from/to = đầu tháng → hôm nay (giờ VN, backend quyết định). */
  ledger: ({ account, from, to, source }: LedgerQuery) =>
    apiRequest<CashLedger>(withQuery('/api/cash-book', { account, from, to, source })),

  opening: () => apiRequest<CashBookOpening>('/api/cash-book/opening'),

  setOpening: (input: OpeningInput) =>
    apiRequest<CashBookOpening>('/api/cash-book/opening', { method: 'PUT', body: JSON.stringify(input) }),
}

export const cashVoucherApi = {
  /** Mới nhất lên đầu */
  list: ({ page, size }: PageQuery, filters: VoucherFilters = {}) =>
    apiRequest<PageResponse<CashVoucher>>(withQuery('/api/cash-vouchers', { page, size, ...toFilterParams(filters) })),

  create: (input: VoucherInput) =>
    apiRequest<CashVoucher>('/api/cash-vouchers', { method: 'POST', body: JSON.stringify(input) }),

  /** Phiếu hoàn: mã gốc + "-H", lý do bắt buộc (BR-CASH-04). */
  reverse: (id: number, reason: string) =>
    apiRequest<CashVoucher>(`/api/cash-vouchers/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),
}

export const cashCategoryApi = {
  list: () => apiRequest<CashCategory[]>('/api/cash-categories'),

  create: (name: string, direction: CashDirection) =>
    apiRequest<CashCategory>('/api/cash-categories', { method: 'POST', body: JSON.stringify({ name, direction }) }),

  update: (id: number, name: string, active: boolean) =>
    apiRequest<CashCategory>(`/api/cash-categories/${id}`, { method: 'PUT', body: JSON.stringify({ name, active }) }),
}

/** Tách số tiền có dấu thành cột Thu (+) / Chi (−) của sổ quỹ — hàm thuần để test không cần React. */
export function signedAmountParts(amount: number): { income?: number; expense?: number } {
  return amount >= 0 ? { income: amount } : { expense: -amount }
}
