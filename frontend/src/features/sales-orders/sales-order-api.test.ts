import { afterEach, describe, expect, it, vi } from 'vitest'
import { customerPriceApi, salesOrderApi } from './sales-order-api'

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

describe('salesOrderApi.list', () => {
  it('mặc định mới nhất lên đầu, không gửi bộ lọc trống', async () => {
    const captured = stubFetch()

    await salesOrderApi.list({ page: 0, size: 20 })

    const { url } = captured()
    expect(url.pathname).toBe('/api/sales-orders')
    expect(url.searchParams.get('sort')).toBe('id,desc')
    expect([...url.searchParams.keys()].sort()).toEqual(['page', 'size', 'sort'])
  })

  it('gửi bộ lọc khách hàng và "còn nợ" theo tên tham số của contract-spec', async () => {
    const captured = stubFetch()

    await salesOrderApi.list({ page: 0, size: 20 }, { customerId: 7, unpaidOnly: 'true', keyword: ' px01 ' })

    const { url } = captured()
    expect(url.searchParams.get('customerId')).toBe('7')
    expect(url.searchParams.get('unpaidOnly')).toBe('true')
    expect(url.searchParams.get('keyword')).toBe('px01')
  })
})

describe('salesOrderApi - thanh toán', () => {
  it('thu tiền: POST /{id}/payments với tiền mặt + chuyển khoản', async () => {
    const captured = stubFetch()

    await salesOrderApi.recordPayment(5, { cashAmount: 300000, transferAmount: 500000, paymentDate: '2026-10-20' })

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/sales-orders/5/payments')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      cashAmount: 300000,
      transferAmount: 500000,
      paymentDate: '2026-10-20',
    })
  })

  it('hoàn khoản thu: POST /{id}/payments/{paymentId}/reverse kèm lý do', async () => {
    const captured = stubFetch()

    await salesOrderApi.reversePayment(5, 12, 'Ghi nhầm')

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/sales-orders/5/payments/12/reverse')
    expect(JSON.parse(init.body as string)).toEqual({ reason: 'Ghi nhầm' })
  })
})

describe('customerPriceApi', () => {
  it('đặt giá: PUT /api/customers/{id}/prices/{productId}', async () => {
    const captured = stubFetch()

    await customerPriceApi.set(3, 9, 305000.5)

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/customers/3/prices/9')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ unitPrice: 305000.5 })
  })
})
