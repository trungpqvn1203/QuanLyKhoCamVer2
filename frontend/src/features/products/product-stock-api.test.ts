import { afterEach, describe, expect, it, vi } from 'vitest'
import { isByWarehouse, productStockApi } from './product-stock-api'

describe('isByWarehouse', () => {
  it('không lọc kho - trường vắng mặt HOẶC null đều không phải số theo kho', () => {
    expect(isByWarehouse({})).toBe(false)
    // Lỗi đã gặp: backend trả "warehouseId": null → so !== undefined cho ra true → ô tồn hiện 0
    expect(isByWarehouse({ warehouseId: null as unknown as undefined })).toBe(false)
  })

  it('có lọc kho - đúng', () => {
    expect(isByWarehouse({ warehouseId: 3 })).toBe(true)
  })
})

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

describe('productStockApi', () => {
  it('list - gửi cả bộ lọc sản phẩm lẫn kho và tình trạng tồn', async () => {
    const capturedUrl = stubFetch()

    await productStockApi.list(
      { page: 1, size: 20 },
      { keyword: ' heo ', categoryId: 2, active: true, warehouseId: 3, stockStatus: 'LOW' },
    )

    const url = capturedUrl()
    expect(url.pathname).toBe('/api/inventory/products')
    expect(url.searchParams.get('keyword')).toBe('heo')
    expect(url.searchParams.get('categoryId')).toBe('2')
    expect(url.searchParams.get('active')).toBe('true')
    expect(url.searchParams.get('warehouseId')).toBe('3')
    expect(url.searchParams.get('stockStatus')).toBe('LOW')
    expect(url.searchParams.get('sort')).toBe('id,asc')
  })

  it('get - tồn từng kho của một SP', async () => {
    const capturedUrl = stubFetch()

    await productStockApi.get(42)

    expect(capturedUrl().pathname).toBe('/api/inventory/products/42')
  })
})
