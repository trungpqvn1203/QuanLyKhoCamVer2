import { afterEach, describe, expect, it, vi } from 'vitest'
import { supplierApi } from './supplier-api'

function stubFetch(status = 200) {
  const body = status === 204 ? null : JSON.stringify({ success: true, data: {}, timestamp: '' })
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body, { status, headers: { 'Content-Type': 'application/json' } }),
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

describe('supplierApi - liên kết NCC–SP', () => {
  it('listProducts - đúng đường dẫn con, gửi page/size và sắp theo mã SP', async () => {
    const captured = stubFetch()

    await supplierApi.listProducts(7, { page: 1, size: 10 })

    const { url } = captured()
    expect(url.pathname).toBe('/api/suppliers/7/products')
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('size')).toBe('10')
    expect(url.searchParams.get('sort')).toBe('product.code,asc')
  })

  it('linkProduct - POST body chỉ có productId', async () => {
    const captured = stubFetch()

    await supplierApi.linkProduct(7, 42)

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/suppliers/7/products')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ productId: 42 })
  })

  it('unlinkProduct - DELETE theo productId, nhận 204 không lỗi', async () => {
    const captured = stubFetch(204)

    await expect(supplierApi.unlinkProduct(7, 42)).resolves.toBeUndefined()

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/suppliers/7/products/42')
    expect(init.method).toBe('DELETE')
  })

  it('listSuppliersOfProduct - URL nằm dưới /api/products, sắp theo mã NCC', async () => {
    const captured = stubFetch()

    await supplierApi.listSuppliersOfProduct(42, { page: 0, size: 10 })

    const { url } = captured()
    expect(url.pathname).toBe('/api/products/42/suppliers')
    expect(url.searchParams.get('sort')).toBe('supplier.code,asc')
  })
})
