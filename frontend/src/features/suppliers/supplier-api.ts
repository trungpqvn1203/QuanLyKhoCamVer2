import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { ProductUnit } from '../products/product-api'
import { createImportApi } from '../imports/import-api'

export interface Supplier {
  id: number
  code: string
  name: string
  // Trường tùy chọn: bỏ trống thì backend lưu NULL và bỏ khỏi JSON (NON_NULL)
  /** BR-SUP-09: mã số thuế, đã chuẩn hóa (10 số hoặc 10 số-3 số) */
  taxCode?: string
  phone?: string
  email?: string
  address?: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateSupplierInput {
  name: string
  taxCode?: string
  phone?: string
  email?: string
  address?: string
}

export interface UpdateSupplierInput extends CreateSupplierInput {
  active: boolean
}

/** Một SP đang liên kết với NCC (BR-SUP-05). productActive = SP còn kinh doanh. */
export interface SupplierProduct {
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  productActive: boolean
}

/** Một NCC đang được phép cung cấp SP — chiều ngược của SupplierProduct. */
export interface ProductSupplierLink {
  supplierId: number
  supplierCode: string
  supplierName: string
  taxCode?: string
  phone?: string
  supplierActive: boolean
}

/** Bộ lọc danh sách — lọc phía backend (contract-spec: "Lọc nhà cung cấp, kho"). */
export type SupplierFilters = {
  keyword?: string
  active?: boolean
}

const BASE = '/api/suppliers'

/** Nhập hàng loạt từ file Excel mẫu (BR-XLS). */
export const supplierImportApi = createImportApi(BASE, 'mau-nhap-nha-cung-cap.xlsx')

export const supplierApi = {
  list: ({ page, size }: PageQuery, filters: SupplierFilters = {}) =>
    apiRequest<PageResponse<Supplier>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  create: (input: CreateSupplierInput) =>
    apiRequest<Supplier>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdateSupplierInput) =>
    apiRequest<Supplier>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Không xóa cứng: backend đặt active = false (BR-SUP-04); các liên kết SP được giữ (BR-SUP-07). */
  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),

  listProducts: (supplierId: number, { page, size }: PageQuery) =>
    apiRequest<PageResponse<SupplierProduct>>(
      withQuery(`${BASE}/${supplierId}/products`, { page, size, sort: 'product.code,asc' }),
    ),

  /** Chiều ngược: NCC đang được phép cung cấp SP này. Gán/gỡ vẫn dùng linkProduct/unlinkProduct. */
  listSuppliersOfProduct: (productId: number, { page, size }: PageQuery) =>
    apiRequest<PageResponse<ProductSupplierLink>>(
      withQuery(`/api/products/${productId}/suppliers`, { page, size, sort: 'supplier.code,asc' }),
    ),

  /** Gán mới hoặc bật lại liên kết đã gỡ (BR-SUP-05). */
  linkProduct: (supplierId: number, productId: number) =>
    apiRequest<SupplierProduct>(`${BASE}/${supplierId}/products`, {
      method: 'POST',
      body: JSON.stringify({ productId }),
    }),

  /** Gỡ liên kết: backend chỉ đặt active = false, không xóa dòng (BR-SUP-05). */
  unlinkProduct: (supplierId: number, productId: number) =>
    apiRequest<void>(`${BASE}/${supplierId}/products/${productId}`, { method: 'DELETE' }),
}
