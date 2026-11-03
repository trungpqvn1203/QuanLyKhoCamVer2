import { describe, expect, it } from 'vitest'
import type { DeliveryOrder } from './delivery-trip-api'
import {
  addStop,
  availableOrders,
  matchesKeyword,
  moveStop,
  overloadTons,
  removeStop,
  stopsChanged,
  totalTons,
} from './trip-stops'

function order(documentId: number, overrides: Partial<DeliveryOrder> = {}): DeliveryOrder {
  return {
    documentId,
    documentCode: `PX${String(documentId).padStart(6, '0')}`,
    documentStatus: 'DRAFT',
    documentDate: '2026-11-03',
    customerId: documentId,
    customerCode: `KH${documentId}`,
    customerName: `Khách ${documentId}`,
    warehouseId: 1,
    warehouseName: 'Kho tổng',
    totalTons: 1,
    lines: [],
    ...overrides,
  }
}

describe('moveStop (kéo thả, nút lên / xuống)', () => {
  it('kéo điểm 1 xuống cuối, kéo điểm cuối lên đầu', () => {
    expect(moveStop(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a'])
    expect(moveStop(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b'])
  })

  it('vị trí ngoài khoảng hoặc trùng → giữ nguyên, không đổi mảng gốc', () => {
    const stops = ['a', 'b']
    expect(moveStop(stops, 0, 5)).toEqual(['a', 'b'])
    expect(moveStop(stops, -1, 0)).toEqual(['a', 'b'])
    expect(moveStop(stops, 1, 1)).toEqual(['a', 'b'])
    moveStop(stops, 0, 1)
    expect(stops).toEqual(['a', 'b'])
  })
})

describe('thêm / bỏ điểm giao', () => {
  it('thêm vào cuối, thêm trùng thì bỏ qua; bỏ theo mã phiếu', () => {
    const stops = addStop(addStop([order(1)], order(2)), order(1))
    expect(stops.map((stop) => stop.documentId)).toEqual([1, 2])
    expect(removeStop(stops, 1).map((stop) => stop.documentId)).toEqual([2])
  })

  it('phiếu vừa bỏ khỏi chuyến hiện lại trong danh sách chờ; phiếu đã hủy thì không', () => {
    const assignable = [order(3)]
    const removed = [order(1), order(2, { documentStatus: 'CANCELLED' })]
    const result = availableOrders(assignable, removed, [order(4)])
    expect(result.map((item) => item.documentId)).toEqual([3, 1])
  })
})

describe('tổng tấn và vượt tải (BR-DLV-04)', () => {
  it('cộng tấn không lệch số thực', () => {
    expect(totalTons([{ totalTons: 0.1 }, { totalTons: 0.2 }])).toBe(0.3)
  })

  it('chỉ báo vượt tải khi xe có tải trọng và tổng lớn hơn', () => {
    expect(overloadTons(5, 5.5)).toBe(0.5)
    expect(overloadTons(5, 5)).toBeUndefined()
    expect(overloadTons(undefined, 100)).toBeUndefined()
  })
})

describe('stopsChanged', () => {
  it('đổi thứ tự, thêm hoặc bỏ đều là thay đổi', () => {
    const saved = [order(1), order(2)]
    expect(stopsChanged(saved, [order(1), order(2)])).toBe(false)
    expect(stopsChanged(saved, [order(2), order(1)])).toBe(true)
    expect(stopsChanged(saved, [order(1)])).toBe(true)
  })
})

describe('matchesKeyword', () => {
  it('tìm không dấu theo tên khách, mã phiếu, địa chỉ', () => {
    const item = order(7, { customerName: 'Trại heo Đồng Tâm', customerAddress: 'Xóm 3, Vũ Thư' })
    expect(matchesKeyword(item, 'dong tam')).toBe(true)
    expect(matchesKeyword(item, 'px000007')).toBe(true)
    expect(matchesKeyword(item, 'vu thu')).toBe(true)
    expect(matchesKeyword(item, 'minh phat')).toBe(false)
    expect(matchesKeyword(item, '  ')).toBe(true)
  })
})
