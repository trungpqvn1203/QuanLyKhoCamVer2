import { afterEach, describe, expect, it, vi } from 'vitest'
import { transferApi } from './transfer-api'
import type { Transfer } from './transfer-api'
import { draftTotalQuantity, toTons, toTransferFormValues, toTransferInput, totalTons } from './transfer-form'

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

describe('transferApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('list - bộ lọc trống không gửi, sắp mới nhất trước', async () => {
    const fetchMock = mockOk()

    await transferApi.list({ page: 0, size: 20 }, { sourceWarehouseId: 1, targetWarehouseId: undefined, status: 'DRAFT' })

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/api/transfers')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '0',
      size: '20',
      sort: 'id,desc',
      sourceWarehouseId: '1',
      status: 'DRAFT',
    })
  })

  it('complete / reverse / cancel - đúng đường dẫn, POST; đảo gửi lý do, hủy bỏ lý do trống', async () => {
    const fetchMock = mockOk()

    await transferApi.complete(4)
    await transferApi.reverse(4, 'Chuyển nhầm kho')
    await transferApi.cancel(4, '   ')

    const calls = fetchMock.mock.calls.map(([url, init]) => ({ url, ...(init as RequestInit) }))
    expect(calls.map((call) => call.url)).toEqual(['/api/transfers/4/complete', '/api/transfers/4/reverse', '/api/transfers/4/cancel'])
    expect(calls.every((call) => call.method === 'POST')).toBe(true)
    expect(JSON.parse(calls[1].body as string)).toEqual({ reason: 'Chuyển nhầm kho' })
    expect(JSON.parse(calls[2].body as string)).toEqual({})
  })
})

describe('transfer-form', () => {
  it('toTransferInput - đủ hai kho, ghi chú trống bỏ đi, dòng chỉ có SP và SL', () => {
    expect(
      toTransferInput({
        sourceWarehouseId: 1,
        targetWarehouseId: 2,
        documentDate: '2026-11-12',
        note: '  ',
        lines: [
          { productId: 5, quantity: 30 },
          { productId: 6, quantity: 20 },
        ],
      }),
    ).toEqual({
      sourceWarehouseId: 1,
      targetWarehouseId: 2,
      documentDate: '2026-11-12',
      note: undefined,
      lines: [
        { productId: 5, quantity: 30 },
        { productId: 6, quantity: 20 },
      ],
    })
  })

  it('toTransferFormValues - nạp phiếu nháp vào form', () => {
    const values = toTransferFormValues({
      sourceWarehouseId: 1,
      targetWarehouseId: 2,
      documentDate: '2026-11-12',
      note: 'xe 29H',
      lines: [{ productId: 5, quantity: 30, netWeightKg: 25 }],
    } as Transfer)

    expect(values).toEqual({
      sourceWarehouseId: 1,
      targetWarehouseId: 2,
      documentDate: '2026-11-12',
      note: 'xe 29H',
      lines: [{ productId: 5, quantity: 30 }],
    })
  })

  it('tổng SL bỏ qua dòng chưa nhập', () => {
    expect(draftTotalQuantity([{ productId: 1, quantity: 3 }, { productId: 2 }, undefined])).toBe(3)
    expect(draftTotalQuantity(undefined)).toBe(0)
  })

  it('BR-STK-02: quy ra tấn làm tròn đến kg; tổng cộng theo kg nên không dính sai số số thực', () => {
    expect(toTons(30, 25)).toBe(0.75)
    expect(toTons(3, 0.3335)).toBe(0.001) // 1,0005 kg → 1 kg
    // 0,1 tấn + 0,2 tấn: cộng số thực sẽ ra 0,30000000000000004
    expect(totalTons([{ quantity: 4, netWeightKg: 25 }, { quantity: 8, netWeightKg: 25 }])).toBe(0.3)
  })

  it('tổng tấn của form: tra trọng lượng theo SP, bỏ dòng chưa có SL hoặc không biết trọng lượng', () => {
    const weights = new Map([
      [5, 25],
      [6, 40],
    ])
    expect(
      totalTons([{ productId: 5, quantity: 30 }, { productId: 6, quantity: 10 }, { productId: 7, quantity: 99 }, { productId: 5 }], weights),
    ).toBe(1.15)
  })
})
