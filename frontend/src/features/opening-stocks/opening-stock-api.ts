import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import type { ProductUnit } from '../products/product-api'

export interface OpeningStockLine {
  id: number
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  quantity: number
  /** Tùy chọn, chỉ tham khảo (BR-OPN-10): không nhập = 0 */
  unitPrice: number
  lineAmount: number
}

/** Chi tiết phiếu tồn đầu kỳ. Cùng vòng đời với phiếu nhập: Nháp → Hoàn tất / Đã hủy; Hoàn tất → Đã đảo. */
export interface OpeningStock {
  id: number
  code: string
  status: ReceiptStatus
  documentDate: string // "YYYY-MM-DD"
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  note?: string
  lineCount: number
  totalQuantity: number
  totalAmount: number
  reason?: string
  /** Tên đăng nhập người lập / người xác nhận */
  createdBy?: string
  completedBy?: string
  completedAt?: string
  cancelledAt?: string
  reversedAt?: string
  createdAt: string
  updatedAt: string
  lines: OpeningStockLine[]
}

export type OpeningStockSummary = Pick<
  OpeningStock,
  | 'id'
  | 'code'
  | 'status'
  | 'documentDate'
  | 'warehouseId'
  | 'warehouseCode'
  | 'warehouseName'
  | 'lineCount'
  | 'totalQuantity'
  | 'totalAmount'
  | 'createdBy'
  | 'completedBy'
  | 'completedAt'
  | 'createdAt'
>

export interface OpeningStockLineInput {
  productId: number
  quantity: number
  unitPrice?: number
}

/** Body cho cả tạo (POST) và sửa phiếu nháp (PUT) — PUT thay toàn bộ dòng. */
export interface OpeningStockInput {
  warehouseId: number
  documentDate?: string
  note?: string
  lines: OpeningStockLineInput[]
}

export type OpeningStockFilters = {
  keyword?: string
  status?: ReceiptStatus
  warehouseId?: number
  documentDateFrom?: string
  documentDateTo?: string
}

const BASE = '/api/opening-stocks'

export const openingStockApi = {
  /** Mới nhất lên đầu */
  list: ({ page, size }: PageQuery, filters: OpeningStockFilters = {}) =>
    apiRequest<PageResponse<OpeningStockSummary>>(withQuery(BASE, { page, size, sort: 'id,desc', ...toFilterParams(filters) })),

  get: (id: number) => apiRequest<OpeningStock>(`${BASE}/${id}`),

  /** Id các SP đã có nhập/xuất tại kho — không nhập đầu kỳ được nữa (BR-OPN-05). */
  productsWithMovement: (warehouseId: number) =>
    apiRequest<number[]>(withQuery(`${BASE}/products-with-movement`, { warehouseId })),

  create: (input: OpeningStockInput) => apiRequest<OpeningStock>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: OpeningStockInput) =>
    apiRequest<OpeningStock>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Nháp → Hoàn tất: tồn kho tăng đúng số đã khai (BR-OPN-03). */
  complete: (id: number) => apiRequest<OpeningStock>(`${BASE}/${id}/complete`, { method: 'POST' }),

  cancel: (id: number, reason?: string) =>
    apiRequest<OpeningStock>(`${BASE}/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason?.trim() || undefined }),
    }),

  /** Hoàn tất → Đã đảo; lý do bắt buộc (BR-OPN-06). */
  reverse: (id: number, reason: string) =>
    apiRequest<OpeningStock>(`${BASE}/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),
}
