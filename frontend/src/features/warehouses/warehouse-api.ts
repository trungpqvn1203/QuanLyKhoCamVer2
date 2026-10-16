import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'

export type WarehouseType = 'CENTRAL' | 'RETAIL'

export const WAREHOUSE_TYPE_LABELS: Record<WarehouseType, string> = {
  CENTRAL: 'Kho tập kết',
  RETAIL: 'Cửa hàng bán lẻ',
}

export interface Warehouse {
  id: number
  code: string
  name: string
  type: WarehouseType
  address: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateWarehouseInput {
  name: string
  type: WarehouseType
  address: string
}

export interface UpdateWarehouseInput extends CreateWarehouseInput {
  active: boolean
}

const BASE = '/api/warehouses'

export const warehouseApi = {
  list: ({ page, size }: PageQuery) =>
    apiRequest<PageResponse<Warehouse>>(withQuery(BASE, { page, size, sort: 'id,asc' })),

  create: (input: CreateWarehouseInput) =>
    apiRequest<Warehouse>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateWarehouseInput) =>
    apiRequest<Warehouse>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-WH-04). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}
