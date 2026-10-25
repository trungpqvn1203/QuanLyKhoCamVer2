import { afterEach, describe, expect, it, vi } from 'vitest'
import { employeeApi } from './employee-api'

function stubFetch() {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, data: {}, timestamp: '' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return () => {
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    return { url: new URL(url, 'http://localhost'), init }
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('employeeApi', () => {
  it('list - gửi keyword đã cắt khoảng trắng và trạng thái', async () => {
    const captured = stubFetch()

    await employeeApi.list({ page: 0, size: 20 }, { keyword: ' bốc ', active: true })

    const { url } = captured()
    expect(url.pathname).toBe('/api/employees')
    expect(url.searchParams.get('keyword')).toBe('bốc')
    expect(url.searchParams.get('active')).toBe('true')
  })

  it('update - đơn giá bị bỏ gửi null (PUT = thay toàn bộ), không bị JSON.stringify làm mất trường', async () => {
    const captured = stubFetch()

    await employeeApi.update(5, { name: 'A', dailyWage: 300000, unloadTonRate: null, deliveryTonRate: 60000, active: true })

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/employees/5')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({
      name: 'A',
      dailyWage: 300000,
      unloadTonRate: null,
      deliveryTonRate: 60000,
      active: true,
    })
  })
})
