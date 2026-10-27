import { afterEach, describe, expect, it, vi } from 'vitest'
import { movementKind, signedQuantity, stockTransactionApi } from './stock-transaction-api'

function stubFetch() {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, data: {}, timestamp: '' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return () => {
    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    return new URL(url, 'http://localhost')
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('stockTransactionApi.list', () => {
  it('mặc định mới ghi sổ lên đầu, không gửi bộ lọc trống', async () => {
    const captured = stubFetch()

    await stockTransactionApi.list({ page: 0, size: 20 })

    const url = captured()
    expect(url.pathname).toBe('/api/inventory/transactions')
    expect(url.searchParams.get('sort')).toBe('id,desc')
    expect([...url.searchParams.keys()].sort()).toEqual(['page', 'size', 'sort'])
  })

  it('gửi đủ bộ lọc theo tên tham số của contract-spec', async () => {
    const captured = stubFetch()

    await stockTransactionApi.list(
      { page: 1, size: 50 },
      {
        productId: 5,
        warehouseId: 2,
        type: 'OUT',
        documentType: 'OUTBOUND',
        keyword: ' px0001 ',
        createdFrom: '2026-10-01',
        createdTo: '2026-10-20',
      },
    )

    const url = captured()
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('productId')).toBe('5')
    expect(url.searchParams.get('warehouseId')).toBe('2')
    expect(url.searchParams.get('type')).toBe('OUT')
    expect(url.searchParams.get('documentType')).toBe('OUTBOUND')
    expect(url.searchParams.get('keyword')).toBe('px0001')
    expect(url.searchParams.get('createdFrom')).toBe('2026-10-01')
    expect(url.searchParams.get('createdTo')).toBe('2026-10-20')
  })
})

describe('movementKind', () => {
  it('ghép loại chứng từ và giao dịch đảo thành nghiệp vụ', () => {
    expect(movementKind({ documentType: 'INBOUND' })).toBe('INBOUND')
    expect(movementKind({ documentType: 'INBOUND', reversalOfId: 1 })).toBe('INBOUND_REVERSAL')
    expect(movementKind({ documentType: 'OUTBOUND' })).toBe('SALE')
    expect(movementKind({ documentType: 'OUTBOUND', reversalOfId: 1 })).toBe('SALE_REVERSAL')
    expect(movementKind({ documentType: 'OPENING_STOCK' })).toBe('OPENING')
    expect(movementKind({ documentType: 'OPENING_STOCK', reversalOfId: 1 })).toBe('OPENING_REVERSAL')
    expect(movementKind({ documentType: 'STOCK_COUNT', type: 'IN' })).toBe('COUNT_SURPLUS')
    expect(movementKind({ documentType: 'STOCK_COUNT', type: 'OUT' })).toBe('COUNT_SHORTAGE')
    expect(movementKind({ documentType: 'STOCK_COUNT', type: 'IN', reversalOfId: 1 })).toBe('COUNT_REVERSAL')
  })
})

describe('signedQuantity', () => {
  it('IN dương, OUT âm', () => {
    expect(signedQuantity({ type: 'IN', quantity: 20 })).toBe(20)
    expect(signedQuantity({ type: 'OUT', quantity: 5 })).toBe(-5)
  })
})
