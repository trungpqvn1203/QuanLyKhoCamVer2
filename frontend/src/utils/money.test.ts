import { describe, expect, it } from 'vitest'
import { formatPriceInput, formatVnd, hasAtMostTwoDecimals, lineAmount, parsePriceInput } from './money'

describe('lineAmount - cùng quy tắc HALF_UP với backend (BR-MON-05)', () => {
  // Cùng bộ số với StockDocumentItemTest ở backend
  it.each([
    [3, 12345.5, 37037],
    [1, 0.5, 1],
    [1, 2.5, 3],
    [1, 0.49, 0],
    [10, 250000, 2500000],
    [0, 99999.99, 0],
    [7, 0, 0],
    [3, 0.1, 0], // 0.1 không biểu diễn chính xác bằng số thực; tính theo xu vẫn ra 30 xu → 0 đ
    [7, 0.15, 1], // 105 xu → làm tròn HALF_UP lên 1 đ
  ])('%d × %d = %d', (quantity, unitPrice, expected) => {
    expect(lineAmount(quantity, unitPrice)).toBe(expected)
  })
})

describe('formatVnd', () => {
  it('nhóm hàng nghìn bằng dấu chấm, kèm "đ"', () => {
    // Intl có thể dùng khoảng trắng không ngắt — chuẩn hóa trước khi so
    expect(formatVnd(2037037).replace(/\s/g, ' ')).toBe('2.037.037 đ')
    expect(formatVnd(12345.5).replace(/\s/g, ' ')).toBe('12.345,5 đ')
  })
})

describe('ô nhập đơn giá', () => {
  it('formatPriceInput - nhóm nghìn bằng ".", thập phân bằng ","', () => {
    expect(formatPriceInput(1234567)).toBe('1.234.567')
    expect(formatPriceInput('1234567.5')).toBe('1.234.567,5')
    expect(formatPriceInput('12.')).toBe('12,')
    expect(formatPriceInput(undefined)).toBe('')
  })

  it('parsePriceInput - đọc ngược kiểu Việt Nam về chuỗi số chuẩn', () => {
    expect(parsePriceInput('1.234.567')).toBe('1234567')
    expect(parsePriceInput('1.234.567,5')).toBe('1234567.5')
    expect(parsePriceInput(undefined)).toBe('')
  })

  it('format(parse(x)) giữ nguyên chuỗi người dùng thấy', () => {
    expect(formatPriceInput(parsePriceInput('250.000,25'))).toBe('250.000,25')
  })
})

describe('hasAtMostTwoDecimals', () => {
  it('nhận 0, 1 hoặc 2 chữ số lẻ; từ chối 3 chữ số lẻ', () => {
    expect(hasAtMostTwoDecimals(300000)).toBe(true)
    expect(hasAtMostTwoDecimals(52500.5)).toBe(true)
    expect(hasAtMostTwoDecimals(0.1)).toBe(true)
    expect(hasAtMostTwoDecimals(1000.123)).toBe(false)
  })
})
