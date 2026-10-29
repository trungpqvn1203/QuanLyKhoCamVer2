import { apiRequest, withQuery } from '../../api/client'
import { toFilterParams } from '../../utils/filters'

/** Doanh số = phiếu bán đã hoàn tất theo ngày bán; phiếu đã đảo không tính (BR-RPT-02). */
export interface SalesPeriod {
  revenue: number
  orderCount: number
  tons: number
}

export interface DailySales {
  date: string
  revenue: number
  orderCount: number
}

/** Trang tổng quan (BR-RPT-01..03): phần mọi vai trò được xem. Công nợ / quỹ lấy từ API riêng theo quyền. */
export interface Dashboard {
  today: string
  stock: {
    productCount: number
    outOfStockCount: number
    lowStockCount: number
    totalTons: number
    incomingTons: number
    draftInboundCount: number
  }
  sales: {
    today: SalesPeriod
    thisMonth: SalesPeriod
    draftCount: number
    /** Đủ 30 ngày, cũ → mới, ngày không bán = 0 */
    last30Days: DailySales[]
  }
}

export interface WarehouseColumn {
  id: number
  code: string
  name: string
  active: boolean
}

/** Báo cáo SP × kho (BR-RPT-04). Khóa map là id kho dạng chuỗi (JSON). */
export interface StockByWarehouse {
  warehouses: WarehouseColumn[]
  rows: {
    productId: number
    productCode: string
    productName: string
    categoryName: string
    unitName: string
    productActive: boolean
    quantities: Record<string, number>
    totalQuantity: number
    totalTons: number
  }[]
  warehouseTons: Record<string, number>
  totalTons: number
}

export type StockByWarehouseFilters = {
  keyword?: string
  categoryId?: number
  /** false = hiện cả SP hết hàng */
  hideZero?: boolean
}

export interface StockMovementRow {
  productId: number
  productCode: string
  productName: string
  categoryName: string
  unitName: string
  openingQuantity: number
  inboundQuantity: number
  outboundQuantity: number
  closingQuantity: number
  openingTons: number
  inboundTons: number
  outboundTons: number
  closingTons: number
}

/** Nhập – xuất – tồn theo kỳ (BR-RPT-05). */
export interface StockMovement {
  from: string
  to: string
  warehouseId?: number
  rows: StockMovementRow[]
  totals: { openingTons: number; inboundTons: number; outboundTons: number; closingTons: number }
}

export type StockMovementFilters = {
  from?: string
  to?: string
  warehouseId?: number
  categoryId?: number
}

const BASE = '/api/reports'

export const reportApi = {
  dashboard: () => apiRequest<Dashboard>(`${BASE}/dashboard`),

  stockByWarehouse: (filters: StockByWarehouseFilters) =>
    apiRequest<StockByWarehouse>(withQuery(`${BASE}/stock-by-warehouse`, toFilterParams(filters))),

  stockMovement: (filters: StockMovementFilters) =>
    apiRequest<StockMovement>(withQuery(`${BASE}/stock-movement`, toFilterParams(filters))),
}
