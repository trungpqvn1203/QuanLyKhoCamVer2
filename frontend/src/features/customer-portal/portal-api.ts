import { apiRequest, withQuery } from '../../api/client'
import type { PageResponse } from '../../api/types'
import type { SaleRequestLine, SalesOrderStatus } from '../sales-orders/sales-order-api'

/** Khách đang đăng nhập app (ADR-0005). mustChangePassword → phải đổi mật khẩu cửa hàng cấp trước (BR-POR-03). */
export interface PortalMe {
  customerId: number
  customerCode: string
  customerName: string
  phone: string
  mustChangePassword: boolean
}

/** SP khách thấy: không giá, không tồn (BR-POR-06). */
export interface PortalProduct {
  id: number
  code: string
  name: string
  unitName: string
  categoryName: string
}

/** Dòng cửa hàng đã chốt — chỉ có sau khi xác nhận (BR-POR-08). */
export interface PortalOrderLine {
  productId: number
  productCode: string
  productName: string
  unitName: string
  quantity: number
  unitPrice: number
  lineAmount: number
}

export interface PortalOrder {
  id: number
  code: string
  status: SalesOrderStatus
  orderDate: string
  requestedDeliveryDate?: string
  note?: string
  createdAt: string
  /** Còn Chờ xác nhận → sửa / tự hủy được (BR-POR-09) */
  editable: boolean
  requestLines: SaleRequestLine[]
  lines: PortalOrderLine[]
  /** Chỉ có khi cửa hàng đã xác nhận */
  totalAmount?: number
  cancelReason?: string
}

export type PortalOrderSummary = Pick<
  PortalOrder,
  'id' | 'code' | 'status' | 'orderDate' | 'requestedDeliveryDate' | 'totalAmount' | 'createdAt'
>

export interface PortalOrderInput {
  requestedDeliveryDate?: string
  note?: string
  lines: { productId: number; quantity: number }[]
}

/** Trạng thái theo lời của khách (BR-POR-08) — không dùng chữ nội bộ "Nháp". */
export const PORTAL_STATUS_LABELS: Record<SalesOrderStatus, string> = {
  PENDING: 'Chờ cửa hàng xác nhận',
  DRAFT: 'Đã nhận đơn',
  COMPLETED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  REVERSED: 'Đã hủy giao',
}

export const PORTAL_STATUS_COLORS: Record<SalesOrderStatus, string> = {
  PENDING: 'gold',
  DRAFT: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'default',
  REVERSED: 'warning',
}

const BASE = '/api/portal'

/** Không hàm nào nhận hay trả token: token nằm trong cookie HttpOnly GW_CAT / GW_CRT do backend đặt. */
export const portalApi = {
  /** Cookie XSRF-TOKEN dùng chung với trang nhân viên — lấy trước request ghi đầu tiên (đăng nhập). */
  csrf: () => apiRequest<void>('/api/auth/csrf'),

  login: (phone: string, password: string) =>
    apiRequest<PortalMe>(`${BASE}/auth/login`, { method: 'POST', body: JSON.stringify({ phone, password }) }),

  logout: () => apiRequest<void>(`${BASE}/auth/logout`, { method: 'POST' }),

  me: () => apiRequest<PortalMe>(`${BASE}/me`),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>(`${BASE}/me/password`, {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),

  products: () => apiRequest<PortalProduct[]>(`${BASE}/products`),

  orders: (page = 0, size = 20) =>
    apiRequest<PageResponse<PortalOrderSummary>>(withQuery(`${BASE}/orders`, { page, size, sort: 'id,desc' })),

  order: (id: number) => apiRequest<PortalOrder>(`${BASE}/orders/${id}`),

  createOrder: (input: PortalOrderInput) =>
    apiRequest<PortalOrder>(`${BASE}/orders`, { method: 'POST', body: JSON.stringify(input) }),

  updateOrder: (id: number, input: PortalOrderInput) =>
    apiRequest<PortalOrder>(`${BASE}/orders/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  cancelOrder: (id: number, note?: string) =>
    apiRequest<PortalOrder>(`${BASE}/orders/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ note: note?.trim() || undefined }),
    }),
}
