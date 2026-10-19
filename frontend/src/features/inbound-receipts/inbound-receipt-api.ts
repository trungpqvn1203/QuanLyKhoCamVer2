import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { ProductUnit } from '../products/product-api'

/** BR-IMP-11, 12: Nháp → Hoàn tất / Đã hủy; Hoàn tất → Đã đảo. */
export type ReceiptStatus = 'DRAFT' | 'COMPLETED' | 'CANCELLED' | 'REVERSED'

export const RECEIPT_STATUS_LABELS: Record<ReceiptStatus, string> = {
  DRAFT: 'Nháp',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  REVERSED: 'Đã đảo',
}

export interface InboundReceiptLine {
  id: number
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  orderedQuantity: number
  receivedQuantity: number
  // JSON number: đơn giá tối đa 2 chữ số lẻ, thành tiền là số nguyên đồng → đủ chính xác để hiển thị
  unitPrice: number
  lineAmount: number
}

/** Chi tiết phiếu (GET /{id}, kết quả các thao tác). Trường null bị bỏ khỏi JSON → tùy chọn. */
export interface InboundReceipt {
  id: number
  code: string
  status: ReceiptStatus
  documentDate: string // "YYYY-MM-DD"
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  supplierId: number
  supplierCode: string
  supplierName: string
  note?: string
  totalAmount: number
  reason?: string
  completedAt?: string
  cancelledAt?: string
  reversedAt?: string
  createdAt: string
  updatedAt: string
  lines: InboundReceiptLine[]
}

/** Một dòng trong danh sách — không có dòng hàng. */
export type InboundReceiptSummary = Omit<InboundReceipt, 'lines' | 'note' | 'reason' | 'cancelledAt' | 'reversedAt' | 'updatedAt'>

export interface InboundReceiptLineInput {
  productId: number
  orderedQuantity: number
  /** Không gửi = bằng SL đặt (BR-IMP-03) */
  receivedQuantity?: number
  unitPrice: number
}

/** Body cho cả tạo (POST) và sửa phiếu nháp (PUT) — PUT thay toàn bộ dòng. */
export interface InboundReceiptInput {
  supplierId: number
  warehouseId: number
  documentDate?: string
  note?: string
  lines: InboundReceiptLineInput[]
}

/** Bộ lọc danh sách — lọc phía backend (contract-spec: "Phiếu nhập kho"). */
export type InboundReceiptFilters = {
  keyword?: string
  status?: ReceiptStatus
  warehouseId?: number
  supplierId?: number
  documentDateFrom?: string
  documentDateTo?: string
}

const BASE = '/api/inbound-receipts'

export const inboundReceiptApi = {
  /** Mới nhất lên đầu */
  list: ({ page, size }: PageQuery, filters: InboundReceiptFilters = {}) =>
    apiRequest<PageResponse<InboundReceiptSummary>>(
      withQuery(BASE, { page, size, sort: 'id,desc', ...toFilterParams(filters) }),
    ),

  get: (id: number) => apiRequest<InboundReceipt>(`${BASE}/${id}`),

  create: (input: InboundReceiptInput) =>
    apiRequest<InboundReceipt>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: InboundReceiptInput) =>
    apiRequest<InboundReceipt>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Nháp → Hoàn tất: tồn tăng theo SL thực nhận (BR-IMP-04). */
  complete: (id: number) => apiRequest<InboundReceipt>(`${BASE}/${id}/complete`, { method: 'POST' }),

  /** Nháp → Đã hủy; lý do tùy chọn. */
  cancel: (id: number, reason?: string) =>
    apiRequest<InboundReceipt>(`${BASE}/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason?.trim() || undefined }),
    }),

  /** Hoàn tất → Đã đảo; lý do bắt buộc (BR-IMP-06). */
  reverse: (id: number, reason: string) =>
    apiRequest<InboundReceipt>(`${BASE}/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),
}
