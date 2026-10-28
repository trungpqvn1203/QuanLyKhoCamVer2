import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import { createImportApi } from '../imports/import-api'

export interface Product {
  id: number
  code: string
  name: string
  categoryId: number
  categoryName: string
  unitId: number
  /** "Bao", "Kg", "Túi"… — người dùng tự đặt (BR-UNIT-01) */
  unitName: string
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

/** Đơn vị tính (BR-UNIT-01..04): mỗi SP một đơn vị, không quy đổi. */
export interface ProductUnit {
  id: number
  name: string
  /** Chỉ đơn vị hệ thống (Kg = 1): SP dùng đơn vị này phải có khối lượng tịnh đúng bằng số này */
  fixedNetWeightKg?: number
  active: boolean
}

export interface CreateProductInput {
  name: string
  categoryId: number
  unitId: number
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

const UNIT_BASE = '/api/product-units'

export const productUnitApi = {
  /** Ít bản ghi: 1 trang tối đa 100 như danh mục. */
  listForSelect: () =>
    apiRequest<PageResponse<ProductUnit>>(withQuery(UNIT_BASE, { page: 0, size: 100, sort: 'name,asc' })),

  create: (name: string) => apiRequest<ProductUnit>(UNIT_BASE, { method: 'POST', body: JSON.stringify({ name }) }),

  /** active=true để mở lại đơn vị đã ngừng dùng (BR-UNIT-02). */
  update: (unit: ProductUnit, name: string, active: boolean) =>
    apiRequest<ProductUnit>(`${UNIT_BASE}/${unit.id}`, { method: 'PUT', body: JSON.stringify({ name, active }) }),
}
