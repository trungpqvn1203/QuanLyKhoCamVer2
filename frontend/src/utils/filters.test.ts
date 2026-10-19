import { describe, expect, it } from 'vitest'
import { hasActiveFilters, toFilterParams } from './filters'

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
