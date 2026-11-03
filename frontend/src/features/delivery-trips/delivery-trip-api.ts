import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { SalesOrderStatus } from '../sales-orders/sales-order-api'

/** BR-DLV-02: Đang xếp → Đã xuất. */
export type DeliveryTripStatus = 'PLANNED' | 'DISPATCHED'

export const TRIP_STATUS_LABELS: Record<DeliveryTripStatus, string> = {
  PLANNED: 'Đang xếp',
  DISPATCHED: 'Đã xuất',
}

export interface DeliveryOrderLine {
  productId: number
  productCode: string
  productName: string
  unitName: string
  quantity: number
  tons: number
}

/**
 * Một phiếu bán nhìn từ phía giao hàng — điểm giao của chuyến (có stopOrder) hoặc phiếu chờ xếp. Không có giá
 * (BR-DLV-08). documentStatus khác DRAFT trên chuyến Đang xếp = phiếu bị hủy / hoàn tất riêng sau khi xếp (BR-DLV-07).
 */
export interface DeliveryOrder {
  stopOrder?: number
  documentId: number
  documentCode: string
  documentStatus: SalesOrderStatus
  documentDate: string
  requestedDeliveryDate?: string
  customerId: number
  customerCode: string
  customerName: string
  customerPhone?: string
  customerAddress?: string
  warehouseId: number
  warehouseName: string
  note?: string
  totalTons: number
  lines: DeliveryOrderLine[]
}

export interface DeliveryTripSummary {
  id: number
  code: string
  tripDate: string
  status: DeliveryTripStatus
  vehicleId: number
  vehiclePlate: string
  capacityTons?: number
  driverId: number
  driverName: string
  stopCount: number
  totalTons: number
  /** Chỉ có khi vượt tải trọng xe (BR-DLV-04) */
  overloadTons?: number
  dispatchedAt?: string
}

export interface DeliveryTrip extends DeliveryTripSummary {
  vehicleName?: string
  driverCode: string
  driverPhone?: string
  note?: string
  createdBy: string
  createdAt: string
  updatedAt: string
  dispatchedBy?: string
  stops: DeliveryOrder[]
}

export interface Driver {
  id: number
  code: string
  name: string
  phone?: string
}

/** documentIds theo đúng thứ tự giao; sửa chuyến gửi lại toàn bộ danh sách (BR-DLV-05). */
export interface DeliveryTripInput {
  tripDate: string
  vehicleId: number
  driverId: number
  note?: string
  documentIds: number[]
}

export type DeliveryTripFilters = {
  keyword?: string
  status?: DeliveryTripStatus
  tripDateFrom?: string
  tripDateTo?: string
}

const BASE = '/api/delivery-trips'

export const deliveryTripApi = {
  list: ({ page, size }: PageQuery, filters: DeliveryTripFilters = {}) =>
    apiRequest<PageResponse<DeliveryTripSummary>>(withQuery(BASE, { page, size, ...toFilterParams(filters) })),

  get: (id: number) => apiRequest<DeliveryTrip>(`${BASE}/${id}`),

  assignableOrders: () => apiRequest<DeliveryOrder[]>(`${BASE}/assignable-orders`),

  drivers: () => apiRequest<Driver[]>(`${BASE}/drivers`),

  create: (input: DeliveryTripInput) =>
    apiRequest<DeliveryTrip>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: DeliveryTripInput) =>
    apiRequest<DeliveryTrip>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  remove: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),

  dispatch: (id: number) => apiRequest<DeliveryTrip>(`${BASE}/${id}/dispatch`, { method: 'POST' }),
}
