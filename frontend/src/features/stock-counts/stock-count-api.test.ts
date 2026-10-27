import { afterEach, describe, expect, it, vi } from 'vitest'
import { draftDifference, stockCountApi, stockCountReasonApi } from './stock-count-api'

function mockOk() {
  // Mỗi lần gọi một Response mới: body chỉ đọc được một lần
  const fetchMock = vi.fn().mockImplementation(() =>
    Promise.resolve(
      new Response(JSON.stringify({ success: true, data: {}, timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('stockCountApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('update - gửi dòng không kèm SL sổ sách (hệ thống tự chụp)', async () => {
    const fetchMock = mockOk()

    await stockCountApi.update(4, { lines: [{ productId: 1, countedQuantity: 115, reasonId: 2 }, { productId: 3 }] })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/stock-counts/4')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({
      lines: [{ productId: 1, countedQuantity: 115, reasonId: 2 }, { productId: 3 }],
    })
  })

  it('refreshBook / complete / reasons - đúng URL và method', async () => {
    const fetchMock = mockOk()

    await stockCountApi.refreshBook(4)
    await stockCountApi.complete(4)
    await stockCountReasonApi.update(2, 'Hao hụt', false)

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/stock-counts/4/refresh-book',
      '/api/stock-counts/4/complete',
      '/api/stock-count-reasons/2',
    ])
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string)).toEqual({ name: 'Hao hụt', active: false })
  })
})

describe('draftDifference', () => {
  it('chưa đếm → undefined; đếm rồi → đếm − sổ sách (âm = thiếu)', () => {
    expect(draftDifference(120, undefined)).toBeUndefined()
    expect(draftDifference(120, null)).toBeUndefined()
    expect(draftDifference(120, 115)).toBe(-5)
    expect(draftDifference(0, 3)).toBe(3)
  })
})
