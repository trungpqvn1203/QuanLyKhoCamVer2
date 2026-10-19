import { afterEach, describe, expect, it, vi } from 'vitest'
import { customerApi, formatPhone, isValidPhone } from './customer-api'

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

describe('isValidPhone (BR-CUS-06)', () => {
  it('chấp nhận số có khoảng trắng, chấm, gạch; di động 10 số và máy bàn 11 số', () => {
    expect(isValidPhone('0901234567')).toBe(true)
    expect(isValidPhone('0901 234 567')).toBe(true)
    expect(isValidPhone('0901.234-567')).toBe(true)
    expect(isValidPhone('0283 456 7890')).toBe(true)
  })

  it('từ chối thiếu số 0 đầu, +84, sai độ dài, có chữ', () => {
    expect(isValidPhone('901234567')).toBe(false)
    expect(isValidPhone('+84901234567')).toBe(false)
    expect(isValidPhone('090123456')).toBe(false)
    expect(isValidPhone('090123456789')).toBe(false)
    expect(isValidPhone('09012a4567')).toBe(false)
  })
})

describe('formatPhone', () => {
  it('nhóm số cho dễ đọc', () => {
    expect(formatPhone('0901234567')).toBe('0901 234 567')
    expect(formatPhone('02834567890')).toBe('0283 456 7890')
  })
})
