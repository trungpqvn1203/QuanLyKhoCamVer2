import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'

/** Xe giao hàng (BR-VEH-01..04). capacityTons không có = không cảnh báo quá tải. */
export interface Vehicle {
  id: number
  plateNumber: string
  name?: string
  capacityTons?: number
  active: boolean
  createdAt: string
  updatedAt: string
}

/** active chỉ dùng khi sửa (mở lại xe đã ngừng); không gửi = giữ nguyên. */
export interface VehicleInput {
  plateNumber: string
  name?: string
  capacityTons?: number
  active?: boolean
}

export type VehicleFilters = {
  keyword?: string
  active?: boolean
}

const BASE = '/api/vehicles'

export const vehicleApi = {
  list: ({ page, size }: PageQuery, filters: VehicleFilters = {}) =>
    apiRequest<PageResponse<Vehicle>>(withQuery(BASE, { page, size, sort: 'plateNumber,asc', ...toFilterParams(filters) })),

  create: (input: VehicleInput) => apiRequest<Vehicle>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: VehicleInput) =>
    apiRequest<Vehicle>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: ngừng dùng (BR-VEH-04). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}

/** "29H-123.45 · Xe Hino · 5 tấn" — ô chọn xe và tiêu đề chuyến. */
export function vehicleLabel(vehicle: Pick<Vehicle, 'plateNumber' | 'name' | 'capacityTons'>): string {
  const parts = [vehicle.plateNumber]
  if (vehicle.name) parts.push(vehicle.name)
  if (vehicle.capacityTons !== undefined) parts.push(`${vehicle.capacityTons.toLocaleString('vi-VN')} tấn`)
  return parts.join(' · ')
}
