import { describe, expect, it } from 'vitest'
import { presetRange } from './period'

describe('presetRange', () => {
  it('hôm nay, hôm qua (qua đầu tháng), 7 ngày qua', () => {
    expect(presetRange('today', '2026-10-29')).toEqual({ from: '2026-10-29', to: '2026-10-29' })
    expect(presetRange('yesterday', '2026-11-01')).toEqual({ from: '2026-10-31', to: '2026-10-31' })
    expect(presetRange('last7Days', '2026-10-03')).toEqual({ from: '2026-09-27', to: '2026-10-03' })
  })

  it('tháng này: từ ngày 1 đến hôm nay', () => {
    expect(presetRange('thisMonth', '2026-10-29')).toEqual({ from: '2026-10-01', to: '2026-10-29' })
  })

  it('tháng trước: đủ tháng, kể cả qua năm và tháng 2 năm nhuận', () => {
    expect(presetRange('lastMonth', '2026-10-29')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(presetRange('lastMonth', '2027-01-15')).toEqual({ from: '2026-12-01', to: '2026-12-31' })
    expect(presetRange('lastMonth', '2028-03-10')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  })

  it('tùy chọn: không có khoảng cố định', () => {
    expect(presetRange('custom', '2026-10-29')).toBeUndefined()
  })
})
