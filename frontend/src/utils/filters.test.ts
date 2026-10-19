import { describe, expect, it } from 'vitest'
import { displayDateToIso, hasActiveFilters, toFilterParams } from './filters'

describe('hasActiveFilters', () => {
  it('nhận ra từng loại bộ lọc, kể cả active=false', () => {
    expect(hasActiveFilters({})).toBe(false)
    expect(hasActiveFilters({ keyword: undefined, active: undefined })).toBe(false)
    expect(hasActiveFilters({ keyword: '  ' })).toBe(false)
    expect(hasActiveFilters({ keyword: 'heo' })).toBe(true)
    expect(hasActiveFilters({ categoryId: 1 })).toBe(true)
    expect(hasActiveFilters({ active: false })).toBe(true)
  })
})

describe('toFilterParams', () => {
  it('cắt khoảng trắng từ khóa, bỏ từ khóa rỗng, boolean thành chuỗi', () => {
    expect(toFilterParams({ keyword: '  heo ', categoryId: 2, active: false })).toEqual({
      keyword: 'heo',
      categoryId: 2,
      active: 'false',
    })
    expect(toFilterParams({ keyword: '   ', active: undefined })).toEqual({ keyword: undefined, active: undefined })
  })
})

describe('displayDateToIso', () => {
  it('đổi ngày hiển thị DD/MM/YYYY sang ISO cho backend', () => {
    expect(displayDateToIso('01/10/2026')).toBe('2026-10-01')
  })

  it('đầu khoảng bỏ trống hoặc sai định dạng - không lọc đầu đó', () => {
    expect(displayDateToIso('')).toBeUndefined()
    expect(displayDateToIso('2026-10-01')).toBeUndefined()
  })
})
