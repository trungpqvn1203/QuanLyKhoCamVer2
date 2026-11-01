import { describe, expect, it } from 'vitest'
import { foldVietnamese, matchesSearch, resolveQuickAdd } from './product-quick-add'

describe('foldVietnamese', () => {
  it('bỏ dấu, đổi đ thành d, chữ thường', () => {
    expect(foldVietnamese('Cám Đậm Đặc Heo')).toBe('cam dam dac heo')
  })
})

describe('matchesSearch', () => {
  const label = 'C01 · Cám heo 25kg'

  it('gõ không dấu vẫn khớp', () => {
    expect(matchesSearch(label, 'cam heo')).toBe(true)
  })

  it('khớp theo mã và không cần đúng thứ tự từ', () => {
    expect(matchesSearch(label, 'c01')).toBe(true)
    expect(matchesSearch(label, '25 heo')).toBe(true)
  })

  it('thiếu một từ thì không khớp', () => {
    expect(matchesSearch(label, 'cam ga')).toBe(false)
  })

  it('ô trống khớp mọi SP', () => {
    expect(matchesSearch(label, '  ')).toBe(true)
  })
})

describe('resolveQuickAdd', () => {
  it('phiếu chưa có dòng nào - thêm dòng đầu tiên', () => {
    expect(resolveQuickAdd(undefined, 5)).toEqual({ action: 'append', index: 0 })
    expect(resolveQuickAdd([], 5)).toEqual({ action: 'append', index: 0 })
  })

  it('SP mới - thêm cuối bảng', () => {
    expect(resolveQuickAdd([{ productId: 1 }, { productId: 2 }], 5)).toEqual({ action: 'append', index: 2 })
  })

  it('SP đã có trên phiếu - đưa về dòng cũ, không tạo dòng trùng', () => {
    expect(resolveQuickAdd([{ productId: 1 }, { productId: 5 }], 5)).toEqual({ action: 'focus', index: 1 })
  })

  it('có dòng chưa chọn SP - điền vào dòng trống đầu tiên', () => {
    expect(resolveQuickAdd([{ productId: 1 }, {}, undefined], 5)).toEqual({ action: 'fill', index: 1 })
  })

  it('SP đã có được ưu tiên hơn dòng trống', () => {
    expect(resolveQuickAdd([{}, { productId: 5 }], 5)).toEqual({ action: 'focus', index: 1 })
  })
})
