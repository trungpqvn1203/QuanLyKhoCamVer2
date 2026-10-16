import type { ApiEnvelope, FieldErrorDetail } from './types'

/**
 * Lỗi đã chuẩn hóa từ envelope thất bại của backend.
 * Màn hình chỉ cần bắt ApiError, không phải tự đọc JSON lỗi.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: FieldErrorDetail[]

  constructor(status: number, code: string, message: string, details: FieldErrorDetail[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

const NETWORK_ERROR_MESSAGE = 'Không kết nối được máy chủ. Kiểm tra backend đã chạy chưa.'
const SERVER_ERROR_MESSAGE = 'Hệ thống gặp lỗi, vui lòng thử lại sau.'

/**
 * Gọi API và trả về `data` khi `success = true`; ngược lại ném ApiError.
 * Đường dẫn tương đối "/api/..." — Vite proxy chuyển sang backend :8080 (vite.config.ts).
 */
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
  } catch {
    // fetch chỉ ném lỗi khi không có phản hồi (mất mạng, backend tắt)
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }

  // 204 No Content (ví dụ DELETE): không có body để đọc
  if (response.status === 204) {
    return undefined as T
  }

  let body: ApiEnvelope<T>
  try {
    body = (await response.json()) as ApiEnvelope<T>
  } catch {
    // Phản hồi không phải JSON (ví dụ proxy trả trang lỗi HTML)
    throw new ApiError(response.status, 'INVALID_RESPONSE', SERVER_ERROR_MESSAGE)
  }

  // Luôn kiểm tra success trước khi đọc data (contract-spec)
  if (body.success) {
    return body.data
  }

  const { code, message, details } = body.error
  // 500: không hiển thị chi tiết kỹ thuật cho người dùng
  const userMessage = response.status >= 500 ? SERVER_ERROR_MESSAGE : message
  throw new ApiError(response.status, code, userMessage, details ?? [])
}

/** Ghép query string, bỏ qua giá trị undefined. */
export function withQuery(path: string, params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) query.set(key, String(value))
  }
  const text = query.toString()
  return text ? `${path}?${text}` : path
}
