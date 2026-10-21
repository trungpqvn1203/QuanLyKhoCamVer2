import { afterEach, describe, expect, it, vi } from 'vitest'
import { userApi } from './user-api'

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

describe('userApi', () => {
  it('danh sách sắp theo tên đăng nhập, gửi đúng bộ lọc', async () => {
    const captured = stubFetch()

    await userApi.list({ page: 0, size: 20 }, { keyword: ' lan ', role: 'SALES_STAFF', active: true })

    const { url } = captured()
    expect(url.pathname).toBe('/api/users')
    expect(url.searchParams.get('sort')).toBe('username,asc')
    expect(url.searchParams.get('keyword')).toBe('lan')
    expect(url.searchParams.get('role')).toBe('SALES_STAFF')
    expect(url.searchParams.get('active')).toBe('true')
  })

  it('đặt lại mật khẩu gửi POST với mật khẩu mới', async () => {
    const captured = stubFetch()

    await userApi.resetPassword(7, 'mat-khau-moi')

    const { url, init } = captured()
    expect(url.pathname).toBe('/api/users/7/reset-password')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ newPassword: 'mat-khau-moi' })
  })
})
