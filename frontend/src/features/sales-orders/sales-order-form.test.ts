import { describe, expect, it } from 'vitest'
import { applyPriceList, draftSalesLineAmount, draftSalesTotal, paymentTotal, toPaymentInput, toSalesOrderInput } from './sales-order-form'

describe('applyPriceList (BR-SAL-07)', () => {
  it('dòng có giá riêng lấy giá riêng; dòng không có giữ giá đang nhập; dòng chưa chọn SP giữ nguyên', () => {
    const lines = [{ productId: 1, quantity: 2, unitPrice: 300000 }, { productId: 2, unitPrice: 150000 }, {}]
    const prices = new Map([[1, 310000]])

    const result = applyPriceList(lines, prices)

    expect(result[0].unitPrice).toBe(310000)
    expect(result[1]).toBe(lines[1]) // không đổi → giữ đúng object cũ (để biết có cần báo "đã điền lại" không)
    expect(result[2]).toBe(lines[2])
  })
})

describe('thành tiền tạm', () => {
  it('làm tròn HALF_UP về đồng như backend; thiếu SL hoặc giá → undefined', () => {
    expect(draftSalesLineAmount({ productId: 1, quantity: 3, unitPrice: 12345.5 })).toBe(37037)
    expect(draftSalesLineAmount({ productId: 1, quantity: 3 })).toBeUndefined()
    expect(draftSalesTotal([{ quantity: 3, unitPrice: 12345.5 }, { quantity: 10, unitPrice: 250000 }, undefined])).toBe(2537037)
  })
})

describe('toSalesOrderInput', () => {
  it('ghi chú trống → không gửi', () => {
    const input = toSalesOrderInput({
      customerId: 1,
      warehouseId: 2,
      documentDate: '2026-10-20',
      note: '   ',
      lines: [{ productId: 3, quantity: 4, unitPrice: 5 }],
    })
    expect(input).toEqual({
      customerId: 1,
      warehouseId: 2,
      documentDate: '2026-10-20',
      note: undefined,
      lines: [{ productId: 3, quantity: 4, unitPrice: 5 }],
    })
  })
})

describe('thanh toán kết hợp (BR-SAL-12)', () => {
  it('tổng = tiền mặt + chuyển khoản, ô trống tính 0', () => {
    expect(paymentTotal({ cashAmount: 300000, transferAmount: 500000 })).toBe(800000)
    expect(paymentTotal({ cashAmount: null, transferAmount: 500000 })).toBe(500000)
    expect(paymentTotal(undefined)).toBe(0)
  })

  it('ô trống hoặc 0 → không gửi phần đó', () => {
    expect(toPaymentInput({ paymentDate: '2026-10-20', cashAmount: 0, transferAmount: 500000, note: ' ' })).toEqual({
      paymentDate: '2026-10-20',
      cashAmount: undefined,
      transferAmount: 500000,
      note: undefined,
    })
  })
})

describe('trả lương có trừ tạm ứng (BR-EPAY-02)', () => {
  it('tổng = tiền mặt + chuyển khoản + trừ tạm ứng', () => {
    expect(paymentTotal({ cashAmount: 300000, transferAmount: 100000, advanceDeduction: 150000 })).toBe(550000)
    expect(paymentTotal({ advanceDeduction: 150000 })).toBe(150000)
  })

  it('phần trừ tạm ứng bỏ trống hoặc 0 → không gửi; có thì gửi kèm', () => {
    expect(toPaymentInput({ cashAmount: 100000, advanceDeduction: 0 }).advanceDeduction).toBeUndefined()
    expect(toPaymentInput({ cashAmount: 100000, advanceDeduction: 50000 })).toMatchObject({
      cashAmount: 100000,
      advanceDeduction: 50000,
    })
  })
})
