import { afterEach, describe, expect, it, vi } from 'vitest'
import { OPENING_DEBT_APIS } from './opening-debt-api'

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

describe('OPENING_DEBT_APIS', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('khách và NCC gọi đúng URL gốc của mình', async () => {
    const fetchMock = mockOk()

    await OPENING_DEBT_APIS.CUSTOMER.recordPayment(7, { cashAmount: 1000 })
    await OPENING_DEBT_APIS.SUPPLIER.reverse(9, 'Nhập nhầm')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/opening-customer-debts/7/payments')
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(url).toBe('/api/opening-supplier-debts/9/reverse')
    expect(JSON.parse(init.body as string)).toEqual({ reason: 'Nhập nhầm' })
  })

  it('list - lọc theo khách và chỉ khoản còn nợ, mới nhất trước', async () => {
    const fetchMock = mockOk()

    await OPENING_DEBT_APIS.CUSTOMER.list({ page: 0, size: 10 }, { partyId: 3, unpaidOnly: 'true' })

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/api/opening-customer-debts')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '0',
      size: '10',
      sort: 'id,desc',
      partyId: '3',
      unpaidOnly: 'true',
    })
  })

  it('create - gửi ngày chốt nợ và các dòng', async () => {
    const fetchMock = mockOk()

    await OPENING_DEBT_APIS.SUPPLIER.create({ documentDate: '2026-10-01', lines: [{ partyId: 2, amount: 34000000 }] })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/opening-supplier-debts')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ documentDate: '2026-10-01', lines: [{ partyId: 2, amount: 34000000 }] })
  })
})
