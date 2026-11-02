import { describe, expect, it } from 'vitest'
import { cartFromRequestLines, cartLineCount, setQuantity, toOrderInput } from './portal-cart'

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
