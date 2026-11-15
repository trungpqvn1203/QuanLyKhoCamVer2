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

  it('listAll tải lần lượt trang 0 rồi ghép các trang tiếp theo khi tổng khách vượt 100', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [{ id: 1, code: 'KH0001' }],
              pagination: { page: 0, size: 100, totalElements: 101, totalPages: 2, isFirst: true, isLast: false },
            },
            timestamp: '',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [{ id: 101, code: 'KH0101' }],
              pagination: { page: 1, size: 100, totalElements: 101, totalPages: 2, isFirst: false, isLast: true },
            },
            timestamp: '',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    const items = await customerApi.listAll({ active: true })

    expect(items).toEqual([
      { id: 1, code: 'KH0001' },
      { id: 101, code: 'KH0101' },
    ])
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/customers?page=0&size=100&sort=id%2Casc&active=true')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/customers?page=1&size=100&sort=id%2Casc&active=true')
  })
})
