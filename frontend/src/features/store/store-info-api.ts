import { apiRequest } from '../../api/client'

/** Thông tin cửa hàng in trên phiếu (BR-PRT-01). configured = false: chưa nhập, các trường khác không có. */
export interface StoreInfo {
  configured: boolean
  name?: string
  address?: string
  phone?: string
  receiptFooter?: string
  updatedAt?: string
}

export interface StoreInfoInput {
  name: string
  address?: string
  phone?: string
  receiptFooter?: string
}

const BASE = '/api/store-info'

export const storeInfoApi = {
  get: () => apiRequest<StoreInfo>(BASE),

  save: (input: StoreInfoInput) => apiRequest<StoreInfo>(BASE, { method: 'PUT', body: JSON.stringify(input) }),
}
