import { describe, expect, it } from 'vitest'
import { formatPhone, isValidPhone } from './phone'

describe('isValidPhone (BR-CUS-06)', () => {
  it('chấp nhận số có khoảng trắng, chấm, gạch; di động 10 số và máy bàn 11 số', () => {
    expect(isValidPhone('0901234567')).toBe(true)
    expect(isValidPhone('0901 234 567')).toBe(true)
    expect(isValidPhone('0901.234-567')).toBe(true)
    expect(isValidPhone('0283 456 7890')).toBe(true)
  })

  it('từ chối thiếu số 0 đầu, +84, sai độ dài, có chữ', () => {
    expect(isValidPhone('901234567')).toBe(false)
    expect(isValidPhone('+84901234567')).toBe(false)
    expect(isValidPhone('090123456')).toBe(false)
    expect(isValidPhone('090123456789')).toBe(false)
    expect(isValidPhone('09012a4567')).toBe(false)
  })
})

describe('formatPhone', () => {
  it('nhóm số cho dễ đọc', () => {
    expect(formatPhone('0901234567')).toBe('0901 234 567')
    expect(formatPhone('02834567890')).toBe('0283 456 7890')
  })
})
