import { afterEach, describe, expect, it, vi } from 'vitest'
import { payrollApi } from './payroll-api'

describe('payrollApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function stubFetch() {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: {}, timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    return () => {
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit | undefined]
      return { path: new URL(url, 'http://localhost').pathname, init }
    }
  }

  it('sheet / finalize - tháng nằm trong đường dẫn', async () => {
    const captured = stubFetch()

    await payrollApi.finalize('2026-09')

    expect(captured().path).toBe('/api/payrolls/2026-09/finalize')
    expect(captured().init?.method).toBe('POST')
  })

  it('cancel - gửi lý do trong body', async () => {
    const captured = stubFetch()

    await payrollApi.cancel('2026-09', 'Quên chấm ngày 3')

    expect(captured().path).toBe('/api/payrolls/2026-09/cancel')
    expect(JSON.parse(captured().init?.body as string)).toEqual({ reason: 'Quên chấm ngày 3' })
  })
})
