import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { StoreInfo } from '../../store/store-info-api'
import type { DeliveryTrip } from '../delivery-trip-api'
import { TripSheet } from './TripSheet'

const store: StoreInfo = { configured: true, name: 'Cửa hàng cám Minh Phát', phone: '0912 345 678' }

function trip(overrides: Partial<DeliveryTrip> = {}): DeliveryTrip {
  return {
    id: 3,
    code: 'CG000003',
    tripDate: '2026-11-04',
    status: 'PLANNED',
    vehicleId: 1,
    vehiclePlate: '29H-123.45',
    capacityTons: 5,
    driverId: 2,
    driverCode: 'NV002',
    driverName: 'Lái xe Hùng',
    driverPhone: '0987654321',
    stopCount: 2,
    totalTons: 5.5,
    overloadTons: 0.5,
    createdBy: 'kho',
    createdAt: '2026-11-03T22:00:00Z',
    updatedAt: '2026-11-03T22:00:00Z',
    stops: [
      {
        stopOrder: 1, documentId: 11, documentCode: 'PX000011', documentStatus: 'DRAFT', documentDate: '2026-11-03',
        customerId: 4, customerCode: 'KH0004', customerName: 'Trại <b>Đồng Tâm</b>', customerPhone: '0901000101',
        customerAddress: 'Xóm 3, Vũ Thư', warehouseId: 1, warehouseName: 'Kho tổng', note: 'Giao trước 8h',
        totalTons: 3,
        lines: [{ productId: 22, productCode: 'SP022', productName: 'Cám heo 25kg', unitName: 'Bao', quantity: 120, tons: 3 }],
      },
      {
        stopOrder: 2, documentId: 12, documentCode: 'PX000012', documentStatus: 'DRAFT', documentDate: '2026-11-03',
        customerId: 5, customerCode: 'KH0005', customerName: 'Đại lý Minh Phát', warehouseId: 2,
        warehouseName: 'Kho B', totalTons: 2.5,
        lines: [{ productId: 23, productCode: 'SP023', productName: 'Cám gà 25kg', unitName: 'Bao', quantity: 100, tons: 2.5 }],
      },
    ],
    ...overrides,
  }
}

describe('TripSheet (phiếu chuyến, BR-DLV-08)', () => {
  it('in xe, lái xe, điểm giao theo thứ tự với SĐT, địa chỉ, hàng, tấn; cảnh báo vượt tải', () => {
    const html = renderToStaticMarkup(<TripSheet trip={trip()} store={store} printedAt="03/11/2026 22:10" />)

    expect(html).toContain('PHIẾU CHUYẾN GIAO HÀNG')
    expect(html).toContain('29H-123.45')
    expect(html).toContain('Lái xe Hùng – 0987 654 321')
    expect(html).toContain('Xóm 3, Vũ Thư')
    expect(html).toContain('Giao trước 8h')
    expect(html).toContain('Cám heo 25kg: 120 bao')
    expect(html).toContain('Vượt tải trọng xe 0,5 tấn')
    expect(html.indexOf('PX000011')).toBeLessThan(html.indexOf('PX000012'))
    // Khách không có địa chỉ vẫn in dòng địa chỉ để lái xe biết phải hỏi
    expect(html).toContain('chưa có địa chỉ')
  })

  it('không in giá; tên khách được escape; chuyến đang xếp có dấu "chưa xuất kho"', () => {
    const html = renderToStaticMarkup(<TripSheet trip={trip()} store={store} printedAt="x" />)

    expect(html).not.toMatch(/Đơn giá|Thành tiền|TỔNG CỘNG|\d{1,3}(\.\d{3})+/)
    expect(html).toContain('Trại &lt;b&gt;Đồng Tâm&lt;/b&gt;')
    expect(html).toContain('CHƯA XUẤT KHO')
    const dispatched = renderToStaticMarkup(
      <TripSheet trip={trip({ status: 'DISPATCHED', overloadTons: undefined })} store={store} printedAt="x" />,
    )
    expect(dispatched).not.toContain('CHƯA XUẤT KHO')
    expect(dispatched).not.toContain('Vượt tải')
  })
})
