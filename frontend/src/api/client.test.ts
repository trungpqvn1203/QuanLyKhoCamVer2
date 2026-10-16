import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, withQuery } from './client'

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
