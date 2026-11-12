import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'

export interface TransferLine {
  id: number
  productId: number
  productCode: string
  productName: string
  unitName: string
  /** Để quy ra tấn (BR-STK-02) */
  netWeightKg: number
  quantity: number
}

/**
 * Chi tiết phiếu điều chuyển (BR-TRF-05..12). Cùng vòng đời với phiếu nhập: Nháp → Hoàn tất / Đã hủy;
 * Hoàn tất → Đã đảo. Không có tiền — hàng chỉ đổi kho.
 */
export interface Transfer {
  id: number
  code: string
  status: ReceiptStatus
  documentDate: string // "YYYY-MM-DD"
  sourceWarehouseId: number
  sourceWarehouseCode: string
  sourceWarehouseName: string
  targetWarehouseId: number
  targetWarehouseCode: string
  targetWarehouseName: string
  note?: string
  lineCount: number
  totalQuantity: number
  reason?: string
  /** Tên đăng nhập người lập / người hoàn tất */
  createdBy?: string
  completedBy?: string
  completedAt?: string
  cancelledAt?: string
  reversedAt?: string
  createdAt: string
  updatedAt: string
  lines: TransferLine[]
}

export type TransferSummary = Pick<
  Transfer,
  | 'id'
  | 'code'
  | 'status'
  | 'documentDate'
  | 'sourceWarehouseId'
  | 'sourceWarehouseCode'
  | 'sourceWarehouseName'
  | 'targetWarehouseId'
  | 'targetWarehouseCode'
  | 'targetWarehouseName'
  | 'lineCount'
  | 'totalQuantity'
  | 'createdBy'
  | 'completedBy'
  | 'completedAt'
  | 'createdAt'
>

export interface TransferLineInput {
  productId: number
  quantity: number
}

/** Body cho cả tạo (POST) và sửa phiếu nháp (PUT) — PUT thay toàn bộ dòng. */
export interface TransferInput {
  sourceWarehouseId: number
  targetWarehouseId: number
  documentDate?: string
  note?: string
  lines: TransferLineInput[]
}

export type TransferFilters = {
  keyword?: string
  status?: ReceiptStatus
  sourceWarehouseId?: number
  targetWarehouseId?: number
  documentDateFrom?: string
  documentDateTo?: string
}

const BASE = '/api/transfers'

export const transferApi = {
  /** Mới nhất lên đầu */
  list: ({ page, size }: PageQuery, filters: TransferFilters = {}) =>
    apiRequest<PageResponse<TransferSummary>>(withQuery(BASE, { page, size, sort: 'id,desc', ...toFilterParams(filters) })),

  get: (id: number) => apiRequest<Transfer>(`${BASE}/${id}`),

  create: (input: TransferInput) => apiRequest<Transfer>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: TransferInput) =>
    apiRequest<Transfer>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Nháp → Hoàn tất: kho nguồn giảm, kho đích tăng trong cùng một transaction (BR-TRF-01). */
  complete: (id: number) => apiRequest<Transfer>(`${BASE}/${id}/complete`, { method: 'POST' }),

  cancel: (id: number, reason?: string) =>
    apiRequest<Transfer>(`${BASE}/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason?.trim() || undefined }),
    }),

  /** Hoàn tất → Đã đảo: hàng về lại kho nguồn; lý do bắt buộc (BR-TRF-12). */
  reverse: (id: number, reason: string) =>
    apiRequest<Transfer>(`${BASE}/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),
}
