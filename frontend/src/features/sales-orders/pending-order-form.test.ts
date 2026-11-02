import { describe, expect, it } from 'vitest'
import { confirmationFormValues, toPendingFormValues, toPendingOrderInput } from './pending-order-form'
import type { SalesOrder } from './sales-order-api'

const pendingOrder: SalesOrder = {
  id: 7,
  code: 'PX000007',
  status: 'PENDING',
  orderChannel: 'ZALO',
  documentDate: '2026-11-01',
  requestedDeliveryDate: '2026-11-05',
  customerId: 3,
  customerCode: 'KH003',
  customerName: 'Trại heo Hưng Thịnh',
  customerPhone: '0901000002',
  note: 'Giao buổi sáng',
  totalAmount: 0,
  paidAmount: 0,
  outstandingAmount: 0,
  totalTons: 0,
  createdAt: '2026-11-01T01:00:00Z',
  updatedAt: '2026-11-01T01:00:00Z',
  lines: [],
  requestLines: [
    { id: 1, productId: 10, productCode: 'SP010', productName: 'Cám heo con', unitName: 'Bao', quantity: 50 },
    { id: 2, productId: 11, productCode: 'SP011', productName: 'Cám gà thịt', unitName: 'Bao', quantity: 20 },
  ],
  payments: [],
}

describe('toPendingOrderInput (BR-SAL-17)', () => {
  it('ngày giao và ghi chú trống → không gửi', () => {
    expect(
      toPendingOrderInput({
        customerId: 1,
        orderChannel: 'PHONE',
        requestedDeliveryDate: '',
        note: '  ',
        lines: [{ productId: 2, quantity: 30 }],
      }),
    ).toEqual({
      customerId: 1,
      orderChannel: 'PHONE',
      requestedDeliveryDate: undefined,
      note: undefined,
      lines: [{ productId: 2, quantity: 30 }],
    })
  })

  it('đọc đơn đã lưu rồi gửi lại → giữ nguyên phần khách đặt', () => {
    expect(toPendingOrderInput(toPendingFormValues(pendingOrder))).toEqual({
      customerId: 3,
      orderChannel: 'ZALO',
      requestedDeliveryDate: '2026-11-05',
      note: 'Giao buổi sáng',
      lines: [
        { productId: 10, quantity: 50 },
        { productId: 11, quantity: 20 },
      ],
    })
  })
})

describe('confirmationFormValues (BR-SAL-19)', () => {
  it('điền sẵn SP + SL khách đặt, giá riêng nếu có; ngày bán = ngày ghi đơn', () => {
    const values = confirmationFormValues(pendingOrder, new Map([[10, 310000]]), 4)

    expect(values).toEqual({
      customerId: 3,
      warehouseId: 4,
      documentDate: '2026-11-01',
      note: 'Giao buổi sáng',
      lines: [
        { productId: 10, quantity: 50, unitPrice: 310000 },
        { productId: 11, quantity: 20, unitPrice: undefined },
      ],
    })
  })

  it('nhiều kho → chưa chọn sẵn kho', () => {
    expect(confirmationFormValues(pendingOrder, new Map()).warehouseId).toBeUndefined()
  })
})
