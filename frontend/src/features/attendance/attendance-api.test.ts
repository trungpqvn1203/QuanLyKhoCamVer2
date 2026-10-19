import { afterEach, describe, expect, it, vi } from 'vitest'
import { attendanceApi, dayIso, shiftMonth, weekdayLabel } from './attendance-api'

describe('shiftMonth', () => {
  it('lùi / tiến tháng, qua năm đúng', () => {
    expect(shiftMonth('2026-10', -1)).toBe('2026-09')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
  })
})

describe('dayIso / weekdayLabel', () => {
  it('ghép ngày có số 0 đầu; thứ đúng theo lịch', () => {
    expect(dayIso('2026-10', 5)).toBe('2026-10-05')
    expect(weekdayLabel('2026-10-20')).toBe('T3')
    expect(weekdayLabel('2026-10-25')).toBe('CN')
  })
})

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
