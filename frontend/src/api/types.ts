// Kiểu dữ liệu khớp docs/api/contract-spec.md

export interface ApiSuccess<T> {
  success: true
  data: T
  message?: string
  timestamp: string
}

export interface FieldErrorDetail {
  field?: string
  message: string
}

export interface ApiFailure {
  success: false
  error: {
    code: string
    message: string
    details?: FieldErrorDetail[]
  }
  timestamp: string
}

export type ApiEnvelope<T> = ApiSuccess<T> | ApiFailure

export interface Pagination {
  page: number // bắt đầu từ 0 (Spring)
  size: number
  totalElements: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
}

export interface PageResponse<T> {
  items: T[]
  pagination: Pagination
}

export interface PageQuery {
  page: number // bắt đầu từ 0
  size: number
}
