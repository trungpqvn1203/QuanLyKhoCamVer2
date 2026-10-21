import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, onSessionExpired, withQuery } from './client'

function mockFetchResponse(status: number, body?: unknown) {
  const response =
    body === undefined
      ? new Response(null, { status })
      : new Response(typeof body === 'string' ? body : JSON.stringify(body), {
          status,
          headers: { 'Content-Type': 'application/json' },
        })
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function catchApiError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    if (error instanceof ApiError) return error
    throw error
  }
  throw new Error('Kỳ vọng ném ApiError nhưng request thành công')
}

afterEach(() => {
  vi.unstubAllGlobals()
  onSessionExpired(null)
})

describe('apiRequest', () => {
  it('trả về data khi success = true', async () => {
    mockFetchResponse(200, { success: true, data: { id: 1, code: 'KHO001' }, timestamp: '2026-10-16T00:00:00Z' })

    await expect(apiRequest('/api/warehouses/1')).resolves.toEqual({ id: 1, code: 'KHO001' })
  })

  it('gửi Content-Type JSON khi có body', async () => {
    const fetchMock = mockFetchResponse(201, { success: true, data: {}, timestamp: '' })

    await apiRequest('/api/warehouses', { method: 'POST', body: '{}' })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' })
  })

  it('204 No Content (DELETE) - không đọc body, trả undefined', async () => {
    mockFetchResponse(204)

    await expect(apiRequest('/api/warehouses/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('400 VALIDATION_FAILED - ném ApiError giữ nguyên code, message, details', async () => {
    mockFetchResponse(400, {
      success: false,
      error: {
        code: 'VALIDATION_FAILED',
        message: 'Dữ liệu không hợp lệ',
        details: [{ field: 'name', message: 'Tên kho không được để trống' }],
      },
      timestamp: '',
    })

    const error = await catchApiError(apiRequest('/api/warehouses', { method: 'POST', body: '{}' }))
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.message).toBe('Dữ liệu không hợp lệ')
    expect(error.details).toEqual([{ field: 'name', message: 'Tên kho không được để trống' }])
  })

  it('409 DUPLICATE_RESOURCE - message của backend hiển thị cho người dùng, details rỗng', async () => {
    mockFetchResponse(409, {
      success: false,
      error: { code: 'DUPLICATE_RESOURCE', message: "Kho 'Kho Hà Nội' (KHO001) đã tồn tại" },
      timestamp: '',
    })

    const error = await catchApiError(apiRequest('/api/warehouses'))
    expect(error.code).toBe('DUPLICATE_RESOURCE')
    expect(error.message).toBe("Kho 'Kho Hà Nội' (KHO001) đã tồn tại")
    expect(error.details).toEqual([])
  })

  it('500 - thay message bằng thông báo chung, không lộ chi tiết kỹ thuật', async () => {
    mockFetchResponse(500, {
      success: false,
      error: { code: 'INTERNAL_ERROR', message: 'NullPointerException at ...' },
      timestamp: '',
    })

    const error = await catchApiError(apiRequest('/api/products'))
    expect(error.code).toBe('INTERNAL_ERROR')
    expect(error.message).toBe('Hệ thống gặp lỗi, vui lòng thử lại sau.')
  })

  it('backend tắt (fetch reject) - ApiError NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = await catchApiError(apiRequest('/api/products'))
    expect(error.status).toBe(0)
    expect(error.code).toBe('NETWORK_ERROR')
  })

  it('phản hồi không phải JSON (ví dụ trang lỗi HTML của proxy) - ApiError INVALID_RESPONSE', async () => {
    mockFetchResponse(502, '<html>Bad Gateway</html>')

    const error = await catchApiError(apiRequest('/api/products'))
    expect(error.code).toBe('INVALID_RESPONSE')
  })
})

describe('withQuery', () => {
  it('ghép tham số và bỏ qua undefined', () => {
    expect(withQuery('/api/products', { page: 0, size: 20, sort: undefined })).toBe('/api/products?page=0&size=20')
  })

  it('không có tham số - giữ nguyên đường dẫn', () => {
    expect(withQuery('/api/products', {})).toBe('/api/products')
  })
})

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const OK = { success: true, data: { id: 1 }, timestamp: '' }
const UNAUTHORIZED = { success: false, error: { code: 'UNAUTHORIZED', message: 'Hết phiên' }, timestamp: '' }

/** fetch giả trả lần lượt theo URL: mỗi URL một hàng đợi response. */
function routedFetch(routes: Record<string, Response[]>) {
  // Khai báo đủ 2 tham số như fetch thật để đọc được init (header, method) trong mock.calls
  const fetchMock = vi.fn((path: string, _init?: RequestInit) => {
    const queue = routes[path]
    if (!queue?.length) throw new Error(`Không có response cho ${path}`)
    return Promise.resolve(queue.shift()!)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('apiRequest - CSRF', () => {
  it('request ghi gắn header X-XSRF-TOKEN từ cookie; GET không gắn', async () => {
    vi.stubGlobal('document', { cookie: 'other=1; XSRF-TOKEN=abc%3D123' })
    const fetchMock = routedFetch({ '/api/customers': [json(201, OK), json(200, OK)] })

    await apiRequest('/api/customers', { method: 'POST', body: '{}' })
    await apiRequest('/api/customers')

    const post = fetchMock.mock.calls[0][1] as RequestInit
    expect((post.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc=123')
    expect(post.credentials).toBe('same-origin')
    const get = fetchMock.mock.calls[1][1] as RequestInit
    expect((get.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined()
  })
})

describe('apiRequest - tự gia hạn khi access token hết hạn', () => {
  it('401 → gia hạn → gửi lại request gốc một lần và trả dữ liệu', async () => {
    const fetchMock = routedFetch({
      '/api/products': [json(401, UNAUTHORIZED), json(200, OK)],
      '/api/auth/refresh': [json(200, { success: true, data: {}, timestamp: '' })],
    })

    await expect(apiRequest('/api/products')).resolves.toEqual({ id: 1 })
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/products', '/api/auth/refresh', '/api/products'])
  })

  it('nhiều request cùng nhận 401 → chỉ MỘT lần gia hạn (single-flight)', async () => {
    const fetchMock = routedFetch({
      '/api/products': [json(401, UNAUTHORIZED), json(200, OK)],
      '/api/warehouses': [json(401, UNAUTHORIZED), json(200, OK)],
      '/api/auth/refresh': [json(200, { success: true, data: {}, timestamp: '' })],
    })

    await Promise.all([apiRequest('/api/products'), apiRequest('/api/warehouses')])
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
  })

  it('409 REFRESH_RACE (tab khác vừa gia hạn) → gửi lại request gốc, không báo hết phiên', async () => {
    const expired = vi.fn()
    onSessionExpired(expired)
    routedFetch({
      '/api/products': [json(401, UNAUTHORIZED), json(200, OK)],
      '/api/auth/refresh': [json(409, { success: false, error: { code: 'REFRESH_RACE', message: '' }, timestamp: '' })],
    })

    await expect(apiRequest('/api/products')).resolves.toEqual({ id: 1 })
    expect(expired).not.toHaveBeenCalled()
  })

  it('gia hạn thất bại → báo hết phiên và ném 401', async () => {
    const expired = vi.fn()
    onSessionExpired(expired)
    routedFetch({
      '/api/products': [json(401, UNAUTHORIZED)],
      '/api/auth/refresh': [json(401, UNAUTHORIZED)],
    })

    const error = await catchApiError(apiRequest('/api/products'))
    expect(error.status).toBe(401)
    expect(expired).toHaveBeenCalledOnce()
  })

  it('đăng nhập sai (401 ở /api/auth/login) → không thử gia hạn', async () => {
    const fetchMock = routedFetch({ '/api/auth/login': [json(401, UNAUTHORIZED)] })

    await catchApiError(apiRequest('/api/auth/login', { method: 'POST', body: '{}' }))
    expect(fetchMock).toHaveBeenCalledOnce()
  })
})
