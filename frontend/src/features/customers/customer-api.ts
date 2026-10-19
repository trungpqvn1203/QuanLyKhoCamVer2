import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'

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

export const customerApi = {
  list: ({ page, size }: PageQuery, filters: CustomerFilters = {}) =>
    apiRequest<PageResponse<Customer>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  create: (input: CreateCustomerInput) =>
    apiRequest<Customer>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateCustomerInput) =>
    apiRequest<Customer>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-CUS-05). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}
