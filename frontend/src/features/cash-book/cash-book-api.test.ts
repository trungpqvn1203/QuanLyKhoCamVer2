import { afterEach, describe, expect, it, vi } from 'vitest'
import { cashBookApi, cashVoucherApi, signedAmountParts } from './cash-book-api'

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

describe('cashBookApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('ledger - gửi account, khoảng ngày, nguồn; bỏ tham số trống', async () => {
    const fetchMock = mockOk()

    await cashBookApi.ledger({ account: 'BANK', from: '2026-10-01', to: '2026-10-26', source: 'CASH_VOUCHER' })
    await cashBookApi.ledger({ account: 'CASH' })

    const first = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(first.pathname).toBe('/api/cash-book')
    expect(Object.fromEntries(first.searchParams)).toEqual({
      account: 'BANK',
      from: '2026-10-01',
      to: '2026-10-26',
      source: 'CASH_VOUCHER',
    })
    const second = new URL(fetchMock.mock.calls[1][0] as string, 'http://localhost')
    expect(Object.fromEntries(second.searchParams)).toEqual({ account: 'CASH' })
  })

  it('reverse - POST lý do vào đúng phiếu', async () => {
    const fetchMock = mockOk()

    await cashVoucherApi.reverse(12, 'Ghi nhầm')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/cash-vouchers/12/reverse')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ reason: 'Ghi nhầm' })
  })
})

describe('signedAmountParts (cột Thu / Chi của sổ quỹ)', () => {
  it('số dương vào cột Thu, số âm vào cột Chi (đổi dấu)', () => {
    expect(signedAmountParts(5000000)).toEqual({ income: 5000000 })
    expect(signedAmountParts(-300000)).toEqual({ expense: 300000 })
  })
})
