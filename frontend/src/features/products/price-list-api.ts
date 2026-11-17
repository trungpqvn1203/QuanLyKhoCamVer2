import { apiRequest, fetchAllPages, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'

export interface PriceList {
  id: number
  name: string
  description?: string
  active: boolean
  itemCount: number
  customerCount: number
  createdAt: string
  updatedAt: string
}

export interface CreatePriceListInput {
  name: string
  description?: string
}

export interface UpdatePriceListInput extends CreatePriceListInput {
  active: boolean
}

export type PriceListFilters = {
  keyword?: string
  active?: boolean
}

export interface PriceListItem {
  priceListId: number
  productId: number
  productCode?: string
  productName?: string
  unitName?: string
  productActive?: boolean
  unitPrice: number
  updatedAt: string
}

const BASE = '/api/price-lists'

export const priceListApi = {
  list: ({ page, size }: PageQuery, filters: PriceListFilters = {}) =>
    apiRequest<PageResponse<PriceList>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),

  /** Tải toàn bộ bảng giá qua nhiều trang cho ô chọn tham chiếu (trong form Khách hàng). */
  listAll: (filters: PriceListFilters = {}) => fetchAllPages((query) => priceListApi.list(query, filters)),

  get: (id: number) => apiRequest<PriceList>(`${BASE}/${id}`),

  create: (input: CreatePriceListInput) =>
    apiRequest<PriceList>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: UpdatePriceListInput) =>
    apiRequest<PriceList>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  deactivate: (id: number) => apiRequest<void>(`${BASE}/${id}`, { method: 'DELETE' }),

  /** Toàn bộ dòng giá của một bảng giá (dùng khi mở chi tiết bảng giá hoặc lập phiếu bán cho khách thuộc bảng giá này). */
  listItems: (priceListId: number) => apiRequest<PriceListItem[]>(`${BASE}/${priceListId}/items`),

  setItemPrice: (priceListId: number, productId: number, unitPrice: number) =>
    apiRequest<PriceListItem>(`${BASE}/${priceListId}/items/${productId}`, {
      method: 'PUT',
      body: JSON.stringify({ unitPrice }),
    }),

  removeItemPrice: (priceListId: number, productId: number) =>
    apiRequest<void>(`${BASE}/${priceListId}/items/${productId}`, { method: 'DELETE' }),
}
