import { afterEach, describe, expect, it, vi } from 'vitest'
import { attendanceApi } from './attendance-api'

describe('attendanceApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('record - PUT đúng đường dẫn (nhân viên, ngày)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: {}, timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await attendanceApi.record(7, '2026-10-05', { status: 'FULL', tons: 12.5 })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url, 'http://localhost').pathname).toBe('/api/attendance/7/2026-10-05')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ status: 'FULL', tons: 12.5 })
  })
})
