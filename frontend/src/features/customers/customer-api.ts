import { apiRequest, fetchAllPages, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import { createImportApi } from '../imports/import-api'

/** BR-CUS-03 — phải khớp enum CustomerType ở backend. */
export type CustomerType = 'DEALER' | 'FARM' | 'RETAIL'

export const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = {
  DEALER: 'Đại lý',
  FARM: 'Trang trại',
  RETAIL: 'Khách lẻ',
}

export const CUSTOMER_TYPE_OPTIONS = (Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[]).map((type) => ({
  value: type,
  label: CUSTOMER_TYPE_LABELS[type],
}))

export interface Customer {
  id: number
  code: string
  name: string
  type: CustomerType
  /** Dạng đã chuẩn hóa, chỉ còn chữ số (BR-CUS-06) */
  phone: string
  // Trường tùy chọn: bỏ trống thì backend lưu NULL và bỏ khỏi JSON (NON_NULL)
  contactName?: string
  address?: string
  note?: string
  /** BR-PRC-04: bảng giá áp dụng cho khách (ví dụ "Giá lẻ", "Giá đại lý") */
  priceListId?: number
  priceListName?: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateCustomerInput {
  name: string
  type: CustomerType
  phone: string
  contactName?: string
  address?: string
  note?: string
  priceListId?: number | null
}

export interface UpdateCustomerInput extends CreateCustomerInput {
  active: boolean
}

/** Bộ lọc danh sách — lọc phía backend. keyword khớp mã, tên hoặc SĐT. */
export type CustomerFilters = {
  keyword?: string
  type?: CustomerType
  active?: boolean
}

const BASE = '/api/customers'

/** Nhập hàng loạt từ file Excel mẫu (BR-XLS). */
export const customerImportApi = createImportApi(BASE, 'mau-nhap-khach-hang.xlsx')

export const customerApi = {
  list: ({ page, size }: PageQuery, filters: CustomerFilters = {}) =>
    apiRequest<PageResponse<Customer>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  /** Tải trọn bộ khách hàng qua nhiều trang (mỗi trang 100 bản ghi) cho các ô chọn tham chiếu (phiếu bán, công nợ đầu kỳ). */
  listAll: (filters: CustomerFilters = {}) => fetchAllPages((query) => customerApi.list(query, filters)),

  create: (input: CreateCustomerInput) =>
    apiRequest<Customer>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateCustomerInput) =>
    apiRequest<Customer>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-CUS-05). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}
