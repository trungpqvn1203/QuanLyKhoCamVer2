import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import { createImportApi } from '../imports/import-api'

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
  /** BR-STK-03: mức tồn tối thiểu; không có = không cảnh báo Sắp hết */
  minStockQuantity?: number
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
  // null: người dùng xóa ô → gửi null → backend bỏ mức tối thiểu
  minStockQuantity?: number | null
}

export interface UpdateProductInput extends CreateProductInput {
  active: boolean
}

/** Bộ lọc danh sách — lọc phía backend (contract-spec: "Lọc sản phẩm"). `type` để dùng được với FilterBar. */
export type ProductFilters = {
  keyword?: string
  categoryId?: number
  active?: boolean
}

const BASE = '/api/products'

/** Nhập hàng loạt từ file Excel mẫu (BR-XLS). */
export const productImportApi = createImportApi(BASE, 'mau-nhap-san-pham.xlsx')

export const productApi = {
  list: ({ page, size }: PageQuery, filters: ProductFilters = {}) =>
    apiRequest<PageResponse<Product>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  create: (input: CreateProductInput) =>
    apiRequest<Product>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateProductInput) =>
    apiRequest<Product>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-PRD-08). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}

const CATEGORY_BASE = '/api/categories'

export const categoryApi = {
  /**
   * Danh mục cho ô chọn trong form sản phẩm.
   * Lấy 1 trang tối đa 100 (giới hạn backend) — đủ cho MVP, nhiều hơn thì cần API tìm kiếm.
   */
  listForSelect: () =>
    apiRequest<PageResponse<Category>>(withQuery(CATEGORY_BASE, { page: 0, size: 100, sort: 'name,asc' })),

  create: (name: string) =>
    apiRequest<Category>(CATEGORY_BASE, { method: 'POST', body: JSON.stringify({ name }) }),

  /** PUT = cập nhật toàn bộ: gửi lại mô tả hiện có để không bị xóa; active=true để mở lại (BR-CAT-04). */
  update: (category: Category, name: string, active: boolean) =>
    apiRequest<Category>(`${CATEGORY_BASE}/${category.id}`, {
      method: 'PUT',
      body: JSON.stringify({ name, description: category.description, active }),
    }),
}
