import { afterEach, describe, expect, it, vi } from 'vitest'
import { productApi } from './product-api'

function stubFetchAndCaptureUrl() {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({ success: true, data: { items: [], pagination: {} }, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ),
  )
  vi.stubGlobal('fetch', fetchMock)
  return () => new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('productApi.list', () => {
  it('không có bộ lọc - chỉ gửi page, size, sort (giống request cũ)', async () => {
    const capturedUrl = stubFetchAndCaptureUrl()

    await productApi.list({ page: 2, size: 20 })

    const params = capturedUrl().searchParams
    expect([...params.keys()].sort()).toEqual(['page', 'size', 'sort'])
    expect(params.get('page')).toBe('2')
  })

  it('gửi keyword đã cắt khoảng trắng, categoryId và active=false', async () => {
    const capturedUrl = stubFetchAndCaptureUrl()

    await productApi.list({ page: 0, size: 20 }, { keyword: '  cám heo ', categoryId: 3, active: false })

    const params = capturedUrl().searchParams
    expect(params.get('keyword')).toBe('cám heo')
    expect(params.get('categoryId')).toBe('3')
    expect(params.get('active')).toBe('false')
  })

  it('keyword chỉ có khoảng trắng - không gửi keyword', async () => {
    const capturedUrl = stubFetchAndCaptureUrl()

    await productApi.list({ page: 0, size: 20 }, { keyword: '   ' })

    expect(capturedUrl().searchParams.has('keyword')).toBe(false)
  })
})
