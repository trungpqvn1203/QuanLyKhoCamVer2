import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import type { ProductUnit } from '../products/product-api'

export interface StockCountLine {
  id: number
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  productActive: boolean
  /** SL sổ sách lúc chụp (BR-CNT-03) */
  bookQuantity: number
  /** Không có = chưa đếm */
  countedQuantity?: number
  /** Dương = thừa, âm = thiếu; không có = chưa đếm */
  difference?: number
  reasonId?: number
  reasonName?: string
  reasonNote?: string
}

/** Phiếu kiểm kê. Nháp = đang kiểm; Hoàn tất = đã ghi chênh lệch vào tồn (BR-CNT-06). */
export interface StockCount {
  id: number
  code: string
  status: ReceiptStatus
  documentDate: string
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  note?: string
  bookSnapshotAt: string
  lineCount: number
  countedLineCount: number
  differenceLineCount: number
  surplusQuantity: number
  shortageQuantity: number
  reason?: string
  createdBy?: string
  completedBy?: string
  completedAt?: string
  cancelledAt?: string
  reversedAt?: string
  createdAt: string
  updatedAt: string
  /** Id các SP trên phiếu có nhập/xuất sau lúc chụp sổ sách — chỉ phiếu đang kiểm */
  movedSinceSnapshot: number[]
  lines: StockCountLine[]
}

export type StockCountSummary = Pick<
  StockCount,
  | 'id'
  | 'code'
  | 'status'
  | 'documentDate'
  | 'warehouseId'
  | 'warehouseCode'
  | 'warehouseName'
  | 'lineCount'
  | 'countedLineCount'
  | 'differenceLineCount'
  | 'surplusQuantity'
  | 'shortageQuantity'
  | 'createdBy'
  | 'completedBy'
  | 'completedAt'
  | 'createdAt'
>

export interface StockCountLineInput {
  productId: number
  countedQuantity?: number
  reasonId?: number
  reasonNote?: string
}

export type StockCountFilters = {
  keyword?: string
  status?: ReceiptStatus
  warehouseId?: number
  documentDateFrom?: string
  documentDateTo?: string
}

export interface StockCountReason {
  id: number
  name: string
  active: boolean
}

/** "Đang kiểm" dễ hiểu hơn "Nháp" cho phiếu kiểm kê; các trạng thái khác giữ như phiếu nhập. */
export const STOCK_COUNT_STATUS_LABELS: Record<ReceiptStatus, string> = {
  DRAFT: 'Đang kiểm',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  REVERSED: 'Đã đảo',
}

const BASE = '/api/stock-counts'

export const stockCountApi = {
  list: ({ page, size }: PageQuery, filters: StockCountFilters = {}) =>
    apiRequest<PageResponse<StockCountSummary>>(withQuery(BASE, { page, size, sort: 'id,desc', ...toFilterParams(filters) })),

  get: (id: number) => apiRequest<StockCount>(`${BASE}/${id}`),

  /** Nạp sẵn mọi SP đang có tồn tại kho và chụp SL sổ sách (BR-CNT-02, 03). */
  create: (input: { warehouseId: number; documentDate?: string; note?: string }) =>
    apiRequest<StockCount>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  /** Thay toàn bộ dòng; SL sổ sách không gửi — hệ thống giữ số đã chụp. */
  update: (id: number, input: { documentDate?: string; note?: string; lines: StockCountLineInput[] }) =>
    apiRequest<StockCount>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  refreshBook: (id: number) => apiRequest<StockCount>(`${BASE}/${id}/refresh-book`, { method: 'POST' }),

  /** Ghi phần chênh vào tồn (thừa tăng, thiếu giảm) — BR-CNT-06. */
  complete: (id: number) => apiRequest<StockCount>(`${BASE}/${id}/complete`, { method: 'POST' }),

  cancel: (id: number, reason?: string) =>
    apiRequest<StockCount>(`${BASE}/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason?.trim() || undefined }),
    }),

  reverse: (id: number, reason: string) =>
    apiRequest<StockCount>(`${BASE}/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),
}

export const stockCountReasonApi = {
  list: () => apiRequest<StockCountReason[]>('/api/stock-count-reasons'),

  create: (name: string) =>
    apiRequest<StockCountReason>('/api/stock-count-reasons', { method: 'POST', body: JSON.stringify({ name }) }),

  update: (id: number, name: string, active: boolean) =>
    apiRequest<StockCountReason>(`/api/stock-count-reasons/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ name, active }),
    }),
}

/** Chênh lệch đang nhập trên form: chưa đếm → undefined. */
export function draftDifference(bookQuantity: number, counted?: number | null): number | undefined {
  return counted === undefined || counted === null ? undefined : counted - bookQuantity
}
