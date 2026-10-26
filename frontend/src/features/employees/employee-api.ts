import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import { createImportApi } from '../imports/import-api'

export interface Employee {
  id: number
  code: string
  name: string
  // Trường tùy chọn: bỏ trống thì backend lưu NULL và bỏ khỏi JSON (NON_NULL)
  phone?: string
  /** BR-EMP-02: lương 1 ngày công; không có = không hưởng theo ngày */
  dailyWage?: number
  /** BR-EMP-02: đơn giá 1 tấn bốc xuống hàng; không có = không hưởng theo cách này */
  unloadTonRate?: number
  /** BR-EMP-04: đơn giá 1 tấn đi giao hàng (khác đơn giá bốc xuống); không có = không hưởng theo cách này */
  deliveryTonRate?: number
  /** BR-EPAY-03: tiền đã ứng và chưa trừ vào lương; luôn có, ≥ 0 */
  advanceBalance: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateEmployeeInput {
  name: string
  phone?: string
  // null: người dùng xóa ô → backend bỏ cách tính lương đó (PUT = thay toàn bộ)
  dailyWage?: number | null
  unloadTonRate?: number | null
  deliveryTonRate?: number | null
}

export interface UpdateEmployeeInput extends CreateEmployeeInput {
  active: boolean
}

export type EmployeeFilters = {
  keyword?: string
  active?: boolean
}

const BASE = '/api/employees'

/** Nhập hàng loạt từ file Excel mẫu (BR-XLS). */
export const employeeImportApi = createImportApi(BASE, 'mau-nhap-nhan-vien.xlsx')

export const employeeApi = {
  list: ({ page, size }: PageQuery, filters: EmployeeFilters = {}) =>
    apiRequest<PageResponse<Employee>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  create: (input: CreateEmployeeInput) =>
    apiRequest<Employee>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateEmployeeInput) =>
    apiRequest<Employee>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-EMP-01). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}
