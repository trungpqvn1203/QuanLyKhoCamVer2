import { describe, expect, it } from 'vitest'
import type { InboundReceipt } from './inbound-receipt-api'
import { draftLineAmount, draftTotal, toFormValues, toReceiptInput } from './receipt-form'

describe('toReceiptInput', () => {
  it('SL thực nhận để trống (null/undefined) → không gửi, backend lấy bằng SL đặt', () => {
    const input = toReceiptInput({
      supplierId: 1,
      warehouseId: 2,
      documentDate: '2026-10-19',
      note: '   ',
      lines: [
        { productId: 10, orderedQuantity: 5, receivedQuantity: null, unitPrice: 1000 },
        { productId: 11, orderedQuantity: 5, receivedQuantity: 4, unitPrice: 2000 },
      ],
    })

    expect(input.note).toBeUndefined()
    expect(input.lines[0]).not.toHaveProperty('receivedQuantity', null)
    expect(input.lines[0].receivedQuantity).toBeUndefined()
    expect(input.lines[1].receivedQuantity).toBe(4)
    // JSON.stringify bỏ trường undefined → body thật không có receivedQuantity
    expect(JSON.parse(JSON.stringify(input)).lines[0]).toEqual({ productId: 10, orderedQuantity: 5, unitPrice: 1000 })
  })
})

describe('toFormValues', () => {
  it('phiếu nháp đã lưu → form giữ SL đặt và SL thực nhận riêng', () => {
    const receipt = {
      supplierId: 1,
      warehouseId: 2,
      documentDate: '2026-10-18',
      lines: [{ productId: 10, orderedQuantity: 10, receivedQuantity: 9, unitPrice: 1000 }],
    } as InboundReceipt

    expect(toFormValues(receipt)).toEqual({
      supplierId: 1,
      warehouseId: 2,
      documentDate: '2026-10-18',
      note: undefined,
      lines: [{ productId: 10, orderedQuantity: 10, receivedQuantity: 9, unitPrice: 1000 }],
    })
  })
})

describe('thành tiền tạm khi đang nhập', () => {
  it('tính theo SL thực nhận; trống thì theo SL đặt; HALF_UP về đồng', () => {
    expect(draftLineAmount({ orderedQuantity: 3, unitPrice: 12345.5 })).toBe(37037)
    expect(draftLineAmount({ orderedQuantity: 10, receivedQuantity: 8, unitPrice: 250000 })).toBe(2000000)
    expect(draftLineAmount({ orderedQuantity: 10, receivedQuantity: 0, unitPrice: 250000 })).toBe(0)
  })

  it('dòng chưa nhập đủ → chưa có thành tiền, tổng bỏ qua dòng đó', () => {
    expect(draftLineAmount({ orderedQuantity: 3 })).toBeUndefined()
    expect(draftLineAmount(undefined)).toBeUndefined()
    expect(draftTotal([{ orderedQuantity: 3, unitPrice: 12345.5 }, { productId: 1 }, undefined])).toBe(37037)
  })
})
