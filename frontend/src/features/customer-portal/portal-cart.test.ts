import { describe, expect, it } from 'vitest'
import {
  MAX_LINES,
  cartFromRequestLines,
  cartLineCount,
  latestDeliveryIso,
  setQuantity,
  toOrderInput,
} from './portal-cart'

describe('giỏ đặt hàng', () => {
  it('SL 0 / null / âm bỏ SP khỏi giỏ; SL lẻ làm tròn xuống', () => {
    let cart = setQuantity({}, 1, 20)
    cart = setQuantity(cart, 2, 2.7)
    expect(cart).toEqual({ 1: 20, 2: 2 })

    cart = setQuantity(cart, 1, 0)
    cart = setQuantity(cart, 2, null)
    expect(cartLineCount(cart)).toBe(0)
    expect(setQuantity({ 3: 5 }, 3, -1)).toEqual({})
  })

  it('đặt lại đơn cũ: giỏ lấy từ phần khách đặt', () => {
    expect(
      cartFromRequestLines([
        { id: 1, productId: 10, productCode: 'SP010', productName: 'Cám heo', unitName: 'Bao', quantity: 50 },
        { id: 2, productId: 11, productCode: 'SP011', productName: 'Cám gà', unitName: 'Bao', quantity: 20 },
      ]),
    ).toEqual({ 10: 50, 11: 20 })
  })
})

describe('toOrderInput', () => {
  it('SP không còn bán bị tách ra để báo khách; ngày giao / ghi chú trống không gửi', () => {
    const { input, unavailable } = toOrderInput({ 10: 50, 99: 5 }, new Set([10, 11]), '', '  ')

    expect(input).toEqual({ requestedDeliveryDate: undefined, note: undefined, lines: [{ productId: 10, quantity: 50 }] })
    expect(unavailable).toEqual([99])
  })
})

describe('giới hạn đơn app (BR-POR-12)', () => {
  it('SL tối đa 9.999 mỗi SP', () => {
    expect(setQuantity({}, 1, 20_000)).toEqual({ 1: 9999 })
  })

  it('đủ 50 SP thì không thêm SP mới, vẫn sửa / bỏ SP đã có', () => {
    let cart = {}
    for (let id = 1; id <= MAX_LINES; id++) cart = setQuantity(cart, id, 1)
    expect(setQuantity(cart, 999, 1)).toBe(cart)
    expect(setQuantity(cart, 1, 5)[1]).toBe(5)
    expect(cartLineCount(setQuantity(cart, 1, 0))).toBe(MAX_LINES - 1)
  })

  it('ngày giao xa nhất = hôm nay + 60 ngày theo giờ VN', () => {
    // 23h ngày 31/10 giờ VN (16h UTC) → hôm nay ở VN là 31/10, xa nhất 30/12
    expect(latestDeliveryIso(new Date('2026-10-31T16:00:00Z'))).toBe('2026-12-30')
  })
})
