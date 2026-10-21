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

/** Cookie do backend đặt (không HttpOnly) — JS đọc rồi gửi lại trong header để chứng minh request do chính trang này tạo. */
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])
const REFRESH_PATH = '/api/auth/refresh'

/** API đăng nhập/gia hạn: 401 ở đây là kết quả thật (sai mật khẩu, hết phiên), không phải tín hiệu để gia hạn. */
const NO_REFRESH_PATHS = new Set(['/api/auth/login', REFRESH_PATH, '/api/auth/logout', '/api/auth/csrf'])

/** Đọc một cookie mà JS được phép đọc. Token đăng nhập (HttpOnly) KHÔNG đọc được bằng cách này — đó là chủ đích. */
export function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined
  const prefix = `${name}=`
  const found = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return found ? decodeURIComponent(found.slice(prefix.length)) : undefined
}

let sessionExpiredHandler: (() => void) | null = null

/** AuthProvider đăng ký: gia hạn thất bại → về trang đăng nhập. */
export function onSessionExpired(handler: (() => void) | null) {
  sessionExpiredHandler = handler
}

type RefreshOutcome = 'renewed' | 'race' | 'failed'
let refreshInFlight: Promise<RefreshOutcome> | null = null

/**
 * Gia hạn phiên — chỉ MỘT request gia hạn tại một thời điểm (single-flight): 5 API cùng nhận 401 thì cùng chờ
 * chung một lần gia hạn, không gửi 5 lần (gửi 5 lần với cùng refresh token sẽ bị backend coi là dùng lại token).
 * 409 REFRESH_RACE: tab khác vừa gia hạn trước, cookie mới đã có sẵn → chỉ cần gửi lại request gốc.
 */
function refreshSession(): Promise<RefreshOutcome> {
  refreshInFlight ??= send(REFRESH_PATH, { method: 'POST' })
    .then((response): RefreshOutcome => (response.ok ? 'renewed' : response.status === 409 ? 'race' : 'failed'))
    .catch((): RefreshOutcome => 'failed')
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

/**
 * fetch cùng tên miền tự gửi kèm cookie (credentials: 'same-origin') — kể cả cookie token HttpOnly mà JS không đọc
 * được. Request ghi (POST/PUT/DELETE) gắn thêm header CSRF.
 */
function send(path: string, init: RequestInit): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const csrfToken = SAFE_METHODS.has(method) ? undefined : readCookie(CSRF_COOKIE)
  return fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { [CSRF_HEADER]: csrfToken } : {}),
      ...init.headers,
    },
  })
}

async function sendOrThrow(path: string, init: RequestInit): Promise<Response> {
  try {
    return await send(path, init)
  } catch {
    // fetch chỉ ném lỗi khi không có phản hồi (mất mạng, backend tắt)
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }
}

/**
 * Gọi API và trả về `data` khi `success = true`; ngược lại ném ApiError.
 * Đường dẫn tương đối "/api/..." — Vite proxy chuyển sang backend :8080 (vite.config.ts).
 * 401 (access token 15 phút đã hết hạn) → gia hạn một lần rồi gửi lại; vẫn 401 → báo hết phiên.
 */
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await sendOrThrow(path, init)

  if (response.status === 401 && !NO_REFRESH_PATHS.has(path)) {
    const outcome = await refreshSession()
    if (outcome !== 'failed') response = await sendOrThrow(path, init)
    if (response.status === 401) sessionExpiredHandler?.()
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
