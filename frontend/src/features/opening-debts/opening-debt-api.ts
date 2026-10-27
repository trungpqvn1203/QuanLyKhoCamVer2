import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import type { PaymentDirection, PaymentInput, SalePayment } from '../sales-orders/sales-order-api'

/** Khách nợ mình (thu) hay mình nợ NCC (trả). */
export type OpeningDebtParty = 'CUSTOMER' | 'SUPPLIER'

/** Một khoản nợ đầu kỳ — chứng từ không có dòng hàng, tạo thẳng ở trạng thái Hoàn tất (BR-ODB-01). */
export interface OpeningDebt {
  id: number
  code: string
  party: OpeningDebtParty
  status: ReceiptStatus
  documentDate: string
  partyId: number
  partyCode: string
  partyName: string
  partyActive: boolean
  note?: string
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
  reason?: string
  createdBy?: string
  completedAt?: string
  reversedAt?: string
  createdAt: string
  updatedAt: string
  /** Chỉ có ở chi tiết, không có ở danh sách */
  payments?: SalePayment[]
}

export interface OpeningDebtLineInput {
  partyId: number
  amount: number
  note?: string
}

/** Ghi nhận theo lô: mỗi dòng một khoản, cả lô cùng ngày chốt nợ (BR-ODB-04). */
export interface OpeningDebtInput {
  documentDate?: string
  lines: OpeningDebtLineInput[]
}

export type OpeningDebtFilters = {
  /** Mã chứng từ hoặc tên khách / NCC */
  keyword?: string
  status?: ReceiptStatus
  partyId?: number
  unpaidOnly?: 'true'
}

/** Văn bản theo loại: khách (thu) / NCC (trả) — để một bộ màn hình dùng chung cho cả hai. */
export const PARTY_TEXT: Record<
  OpeningDebtParty,
  { party: string; title: string; direction: PaymentDirection; paidLabel: string; codeLabel: string }
> = {
  CUSTOMER: { party: 'Khách hàng', title: 'Công nợ khách đầu kỳ', direction: 'receive', paidLabel: 'Đã thu', codeLabel: 'Mã KH' },
  SUPPLIER: { party: 'Nhà cung cấp', title: 'Công nợ NCC đầu kỳ', direction: 'pay', paidLabel: 'Đã trả', codeLabel: 'Mã NCC' },
}

const BASES: Record<OpeningDebtParty, string> = {
  CUSTOMER: '/api/opening-customer-debts',
  SUPPLIER: '/api/opening-supplier-debts',
}

/** Cùng bộ endpoint cho hai loại, khác URL gốc. Màn hình dùng OPENING_DEBT_APIS (hàm ổn định cho useFilteredList). */
export function openingDebtApi(party: OpeningDebtParty) {
  const base = BASES[party]
  return {
    list: ({ page, size }: PageQuery, filters: OpeningDebtFilters = {}) =>
      apiRequest<PageResponse<OpeningDebt>>(withQuery(base, { page, size, sort: 'id,desc', ...toFilterParams(filters) })),

    get: (id: number) => apiRequest<OpeningDebt>(`${base}/${id}`),

    create: (input: OpeningDebtInput) =>
      apiRequest<OpeningDebt[]>(base, { method: 'POST', body: JSON.stringify(input) }),

    /** Chỉ khi chưa thu/trả đồng nào (BR-ODB-06); lý do bắt buộc. */
    reverse: (id: number, reason: string) =>
      apiRequest<OpeningDebt>(`${base}/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),

    recordPayment: (id: number, input: PaymentInput) =>
      apiRequest<OpeningDebt>(`${base}/${id}/payments`, { method: 'POST', body: JSON.stringify(input) }),

    reversePayment: (id: number, paymentId: number, reason: string) =>
      apiRequest<OpeningDebt>(`${base}/${id}/payments/${paymentId}/reverse`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
  }
}

export const OPENING_DEBT_APIS: Record<OpeningDebtParty, ReturnType<typeof openingDebtApi>> = {
  CUSTOMER: openingDebtApi('CUSTOMER'),
  SUPPLIER: openingDebtApi('SUPPLIER'),
}
