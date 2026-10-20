import { afterEach, describe, expect, it, vi } from 'vitest'
import { customerApi } from './customer-api'

function stubFetch() {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, data: {}, timestamp: '' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return () => new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('customerApi.list', () => {
  it('không có bộ lọc - chỉ gửi page, size, sort', async () => {
    const capturedUrl = stubFetch()

    await customerApi.list({ page: 0, size: 20 })

    expect([...capturedUrl().searchParams.keys()].sort()).toEqual(['page', 'size', 'sort'])
  })

  it('gửi keyword đã cắt khoảng trắng, loại khách và trạng thái', async () => {
    const capturedUrl = stubFetch()

    await customerApi.list({ page: 0, size: 20 }, { keyword: ' 0901 ', type: 'FARM', active: false })

    const url = capturedUrl()
    expect(url.pathname).toBe('/api/customers')
    expect(url.searchParams.get('keyword')).toBe('0901')
    expect(url.searchParams.get('type')).toBe('FARM')
    expect(url.searchParams.get('active')).toBe('false')
  })
})
