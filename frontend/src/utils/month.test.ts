import { describe, expect, it } from 'vitest'
import { dayIso, formatMonth, shiftMonth, weekdayLabel } from './month'

describe('shiftMonth', () => {
  it('lùi / tiến tháng, qua năm đúng', () => {
    expect(shiftMonth('2026-10', -1)).toBe('2026-09')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
  })
})

describe('dayIso / weekdayLabel / formatMonth', () => {
  it('ghép ngày có số 0 đầu; thứ đúng theo lịch; tháng kiểu Việt Nam', () => {
    expect(dayIso('2026-10', 5)).toBe('2026-10-05')
    expect(weekdayLabel('2026-10-20')).toBe('T3')
    expect(weekdayLabel('2026-10-25')).toBe('CN')
    expect(formatMonth('2026-10')).toBe('10/2026')
  })
})
