import { describe, expect, it } from 'vitest'
import { adjustedTotal, initialPriceValues, toPriceAdjustmentInput } from './price-adjustment-form'
import type { SalesOrderLine } from './sales-order-api'

const lines: SalesOrderLine[] = [
  { id: 1, productId: 10, productCode: 'SP010', productName: 'Cám heo con', unitName: 'Bao', quantity: 10,
    unitPrice: 300000, lineAmount: 3000000, tons: 0.25 },
  { id: 2, productId: 11, productCode: 'SP011', productName: 'Cám gà thịt', unitName: 'Bao', quantity: 3,
    unitPrice: 12345.5, lineAmount: 37037, tons: 0.075 },
]

describe('toPriceAdjustmentInput (BR-SAL-24)', () => {
  it('chỉ gửi dòng đổi giá; ô trống giữ giá cũ; lý do được cắt khoảng trắng', () => {
    const values = { prices: { '10': 295000, '11': null }, reason: '  Bớt giá khách quen ' }

    expect(toPriceAdjustmentInput(lines, values)).toEqual({
      reason: 'Bớt giá khách quen',
      lines: [{ productId: 10, unitPrice: 295000 }],
    })
  })

  it('không sửa ô nào → không có dòng để gửi', () => {
    expect(toPriceAdjustmentInput(lines, { ...initialPriceValues(lines), reason: 'x' }).lines).toEqual([])
  })
})

describe('adjustedTotal', () => {
  it('tính lại theo HALF_UP như backend', () => {
    // 10 × 295.000 + 3 × 12.000 = 2.986.000
    expect(adjustedTotal(lines, { prices: { '10': 295000, '11': 12000 } })).toBe(2986000)
    // giữ nguyên → đúng tổng cũ (37.036,5 → 37.037)
    expect(adjustedTotal(lines, initialPriceValues(lines))).toBe(3037037)
  })
})
