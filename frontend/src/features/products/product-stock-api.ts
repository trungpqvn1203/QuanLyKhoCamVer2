import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { Product, ProductFilters, ProductUnit } from './product-api'

/** BR-STK-04 — tính trên tổng mọi kho. */
export type StockStatus = 'IN_STOCK' | 'LOW' | 'OUT'

export const STOCK_STATUS_LABELS: Record<StockStatus, string> = {
  IN_STOCK: 'Còn hàng',
  LOW: 'Sắp hết',
  OUT: 'Hết hàng',
}

/**
 * Một dòng của GET /api/inventory/products: đủ trường của Product (dùng luôn cho xem/sửa) + số tồn.
 * Số lượng là số nguyên (BR-INV-07); tấn có tối đa 3 chữ số lẻ (BR-STK-02).
 */
export interface ProductStock extends Product {
  stockQuantity: number
  stockTons: number
  /** Tổng SL đặt trên phiếu nhập còn Nháp — chưa phải tồn (BR-STK-06) */
  incomingQuantity: number
  stockStatus: StockStatus
  // Chỉ có khi lọc theo kho (BR-STK-05)
  warehouseId?: number
  warehouseStockQuantity?: number
  warehouseStockTons?: number
  warehouseIncomingQuantity?: number
}

export interface WarehouseStock {
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  /** false: kho đã ngừng nhưng hàng vẫn nằm đó, vẫn tính vào tổng (BR-STK-01) */
  warehouseActive: boolean
  quantity: number
  tons: number
  incomingQuantity: number
  /** Lần đổi tồn gần nhất; không có nếu kho chỉ có hàng đang về */
  updatedAt?: string
}

export interface ProductStockDetail {
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  netWeightKg: number
  minStockQuantity?: number
  stockQuantity: number
  stockTons: number
  incomingQuantity: number
  stockStatus: StockStatus
  warehouses: WarehouseStock[]
}

/**
 * Dòng này có số theo kho không. So `!= null` (bắt cả null lẫn undefined), không so `!== undefined`:
 * API từng trả `"warehouseId": null` khi không lọc kho → `null !== undefined` là true → ô tồn hiện nhầm 0.
 */
export function isByWarehouse(row: Pick<ProductStock, 'warehouseId'>): boolean {
  return row.warehouseId != null
}

/** Bộ lọc của trang Sản phẩm = bộ lọc sản phẩm + kho + tình trạng tồn. */
export type ProductStockFilters = ProductFilters & {
  warehouseId?: number
  stockStatus?: StockStatus
}

const BASE = '/api/inventory/products'

export const productStockApi = {
  list: ({ page, size }: PageQuery, filters: ProductStockFilters = {}) =>
    apiRequest<PageResponse<ProductStock>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  get: (productId: number) => apiRequest<ProductStockDetail>(`${BASE}/${productId}`),
}
