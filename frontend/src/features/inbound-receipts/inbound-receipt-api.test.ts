import { afterEach, describe, expect, it, vi } from 'vitest'
import { inboundReceiptApi } from './inbound-receipt-api'

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

describe('inboundReceiptApi.list', () => {
  it('mặc định mới nhất lên đầu, không gửi bộ lọc trống', async () => {
    const captured = stubFetch()

    await inboundReceiptApi.list({ page: 0, size: 20 })

    const { url } = captured()
    expect(url.pathname).toBe('/api/inbound-receipts')
    expect(url.searchParams.get('sort')).toBe('id,desc')
    expect([...url.searchParams.keys()].sort()).toEqual(['page', 'size', 'sort'])
  })

  it('gửi đủ bộ lọc theo tên tham số của contract-spec', async () => {
    const captured = stubFetch()

    await inboundReceiptApi.list(
      { page: 0, size: 20 },
      {
        keyword: ' pn0001 ',
        status: 'COMPLETED',
        warehouseId: 3,
        supplierId: 4,
        documentDateFrom: '2026-10-01',
        documentDateTo: '2026-10-19',
      },
    )

    const { url } = captured()
    expect(url.searchParams.get('keyword')).toBe('pn0001')
    expect(url.searchParams.get('status')).toBe('COMPLETED')
    expect(url.searchParams.get('warehouseId')).toBe('3')
    expect(url.searchParams.get('supplierId')).toBe('4')
    expect(url.searchParams.get('documentDateFrom')).toBe('2026-10-01')
    expect(url.searchParams.get('documentDateTo')).toBe('2026-10-19')
  })
})

describe('inboundReceiptApi - đổi trạng thái bằng hành động riêng', () => {
  it('complete - POST /{id}/complete, không có body', async () => {
    const captured = stubFetch()

    await inboundReceiptApi.complete(5)

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/inbound-receipts/5/complete')
    expect(init.method).toBe('POST')
    expect(init.body).toBeUndefined()
  })

  it('cancel - lý do chỉ có khoảng trắng thì không gửi', async () => {
    const captured = stubFetch()

    await inboundReceiptApi.cancel(5, '   ')

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/inbound-receipts/5/cancel')
    expect(JSON.parse(init.body as string)).toEqual({})
  })

  it('reverse - gửi lý do', async () => {
    const captured = stubFetch()

    await inboundReceiptApi.reverse(5, 'Nhập nhầm kho')

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/inbound-receipts/5/reverse')
    expect(JSON.parse(init.body as string)).toEqual({ reason: 'Nhập nhầm kho' })
  })
})
