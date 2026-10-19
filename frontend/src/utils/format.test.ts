import { describe, expect, it } from 'vitest'
import { EMPTY_TEXT, formatDate, formatDateTime, orEmpty, todayIso } from './format'

describe('formatDate', () => {
  it('LocalDate từ backend → dd/mm/yyyy, không lệch ngày', () => {
    expect(formatDate('2026-10-19')).toBe('19/10/2026')
  })
})

describe('todayIso', () => {
  it('ngày theo giờ Việt Nam, không theo UTC', () => {
    // 18:30 UTC ngày 19/10 = 01:30 sáng 20/10 ở Việt Nam
    expect(todayIso(new Date('2026-10-19T18:30:00Z'))).toBe('2026-10-20')
    expect(todayIso(new Date('2026-10-19T10:00:00Z'))).toBe('2026-10-19')
  })
})

describe('formatDateTime', () => {
  it('đổi giờ UTC từ backend sang giờ Việt Nam (+7), ngày trước giờ sau', () => {
    expect(formatDateTime('2026-10-16T03:05:00Z')).toBe('16/10/2026 10:05')
  })

  it('qua ngày mới khi cộng 7 tiếng vượt nửa đêm', () => {
    expect(formatDateTime('2026-12-31T18:30:00Z')).toBe('01/01/2027 01:30')
  })
})

describe('orEmpty', () => {
  it('trường không có, rỗng hoặc chỉ khoảng trắng → gạch ngang thống nhất', () => {
    expect(orEmpty(undefined)).toBe(EMPTY_TEXT)
    expect(orEmpty(null)).toBe(EMPTY_TEXT)
    expect(orEmpty('   ')).toBe(EMPTY_TEXT)
  })

  it('có giá trị → giữ nguyên', () => {
    expect(orEmpty('0901234567')).toBe('0901234567')
  })
})
