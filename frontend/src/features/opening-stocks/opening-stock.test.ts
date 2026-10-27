import { afterEach, describe, expect, it, vi } from 'vitest'
import { openingStockApi } from './opening-stock-api'
import { draftTotalAmount, draftTotalQuantity, toOpeningFormValues, toOpeningInput } from './opening-stock-form'
import type { OpeningStock } from './opening-stock-api'

function mockOk() {
  // Mỗi lần gọi một Response mới: body chỉ đọc được một lần
  const fetchMock = vi.fn().mockImplementation(() =>
    Promise.resolve(
      new Response(JSON.stringify({ success: true, data: [], timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('openingStockApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('productsWithMovement - gửi warehouseId', async () => {
    const fetchMock = mockOk()

    await openingStockApi.productsWithMovement(3)

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/api/opening-stocks/products-with-movement')
    expect(url.searchParams.get('warehouseId')).toBe('3')
  })

  it('list - bộ lọc trống không gửi, sắp mới nhất trước', async () => {
    const fetchMock = mockOk()

    await openingStockApi.list({ page: 0, size: 20 }, { status: 'DRAFT', warehouseId: undefined })

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '0', size: '20', sort: 'id,desc', status: 'DRAFT' })
  })
})

describe('opening-stock-form', () => {
  it('toOpeningInput - đơn giá để trống thì không gửi (BR-OPN-10), ghi chú trống bỏ đi', () => {
    expect(
      toOpeningInput({
        warehouseId: 1,
        documentDate: '2026-11-01',
        note: '  ',
        lines: [
          { productId: 5, quantity: 500, unitPrice: 310000 },
          { productId: 6, quantity: 300, unitPrice: null },
        ],
      }),
    ).toEqual({
      warehouseId: 1,
      documentDate: '2026-11-01',
      note: undefined,
      lines: [
        { productId: 5, quantity: 500, unitPrice: 310000 },
        { productId: 6, quantity: 300, unitPrice: undefined },
      ],
    })
  })

  it('toOpeningFormValues - đơn giá 0 trên phiếu đã lưu hiện ô trống', () => {
    const values = toOpeningFormValues({
      warehouseId: 1,
      documentDate: '2026-11-01',
      lines: [
        { productId: 5, quantity: 500, unitPrice: 0 },
        { productId: 6, quantity: 300, unitPrice: 1000 },
      ],
    } as OpeningStock)
    expect(values.lines).toEqual([
      { productId: 5, quantity: 500, unitPrice: undefined },
      { productId: 6, quantity: 300, unitPrice: 1000 },
    ])
  })

  it('tổng tạm: SL cộng mọi dòng; giá trị bỏ qua dòng chưa có đơn giá, làm tròn HALF_UP từng dòng', () => {
    const lines = [{ productId: 1, quantity: 3, unitPrice: 12345.5 }, { productId: 2, quantity: 10 }, undefined]
    expect(draftTotalQuantity(lines)).toBe(13)
    expect(draftTotalAmount(lines)).toBe(37037) // 37.036,5 → 37.037
  })
})
