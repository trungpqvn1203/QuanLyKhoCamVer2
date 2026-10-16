import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'

export type ProductUnit = 'BAG' | 'KG'

export const PRODUCT_UNIT_LABELS: Record<ProductUnit, string> = {
  BAG: 'Bao',
  KG: 'Kg',
}

export interface Product {
  id: number
  code: string
  name: string
  categoryId: number
  categoryName: string
  unit: ProductUnit
  // JSON number: đủ chính xác cho khối lượng NUMERIC(10,3); tiền tệ sau này cần cân nhắc riêng
  netWeightKg: number
  description?: string // field null bị bỏ khỏi JSON (NON_NULL)
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface Category {
  id: number
  name: string
  description?: string
  active: boolean
}

export interface CreateProductInput {
  name: string
  categoryId: number
  unit: ProductUnit
  netWeightKg: number
  description?: string
}

export interface UpdateProductInput extends CreateProductInput {
  active: boolean
}

/** Bộ lọc danh sách — lọc phía backend (contract-spec: "Lọc sản phẩm"). */
export interface ProductFilters {
  keyword?: string
  categoryId?: number
  active?: boolean
}

export function hasActiveFilters({ keyword, categoryId, active }: ProductFilters): boolean {
  return Boolean(keyword?.trim()) || categoryId !== undefined || active !== undefined
}

const BASE = '/api/products'

export const productApi = {
  list: ({ page, size }: PageQuery, { keyword, categoryId, active }: ProductFilters = {}) =>
    apiRequest<PageResponse<Product>>(
      withQuery(BASE, {
        page,
        size,
        sort: 'id,asc',
        keyword: keyword?.trim() || undefined,
        categoryId,
        active: active === undefined ? undefined : String(active),
      }),
    ),

  create: (input: CreateProductInput) =>
    apiRequest<Product>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateProductInput) =>
    apiRequest<Product>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-PRD-08). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}

export const categoryApi = {
  /**
   * Danh mục cho ô chọn trong form sản phẩm.
   * Lấy 1 trang tối đa 100 (giới hạn backend) — đủ cho MVP, nhiều hơn thì cần API tìm kiếm.
   */
  listForSelect: () =>
    apiRequest<PageResponse<Category>>(withQuery('/api/categories', { page: 0, size: 100, sort: 'name,asc' })),
}
