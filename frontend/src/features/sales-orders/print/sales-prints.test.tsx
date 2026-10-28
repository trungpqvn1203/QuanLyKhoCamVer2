import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { StoreInfo } from '../../store/store-info-api'
import type { SalesOrder } from '../sales-order-api'
import { SalesReceipt } from './SalesPrints'

const store: StoreInfo = {
  configured: true,
  name: 'Cửa hàng cám Minh Phát',
  address: '12 Lý Bôn, Thái Bình',
  phone: '0912 345 678',
  receiptFooter: 'Cảm ơn quý khách!',
}

function order(overrides: Partial<SalesOrder> = {}): SalesOrder {
  return {
    id: 8,
    code: 'PX000008',
    status: 'COMPLETED',
    documentDate: '2026-10-22',
    warehouseId: 1,
    warehouseCode: 'KHO001',
    warehouseName: 'Kho tổng Hà Nội',
    customerId: 4,
    customerCode: 'KH0004',
    customerName: 'Trang trại <b>Đồng Tâm</b>',
    customerPhone: '0987654321',
    note: 'Giao trước 8h',
    totalAmount: 16_410_000,
    paidAmount: 16_000_000,
    outstandingAmount: 410_000,
    totalTons: 1.29,
    createdAt: '2026-10-26T10:25:00Z',
    updatedAt: '2026-10-28T10:35:00Z',
    lines: [
      { id: 1, productId: 22, productCode: 'SP000022', productName: 'Cám gà thịt 25kg', unitName: 'Bao',
        quantity: 50, unitPrice: 320_000, lineAmount: 16_000_000, tons: 1.25 },
      { id: 2, productId: 7, productCode: 'SP000007', productName: 'Cám cá xá', unitName: 'Kg',
        quantity: 40, unitPrice: 10_250, lineAmount: 410_000, tons: 0.04 },
    ],
    payments: [],
    ...overrides,
  }
}

const render = (element: React.ReactElement) => renderToStaticMarkup(element)

describe('SalesReceipt (phiếu bán, BR-PRT-02)', () => {
  it('có thông tin cửa hàng, dòng hàng, tổng, đã trả, còn nợ, số tiền bằng chữ', () => {
    const html = render(<SalesReceipt order={order()} store={store} printedAt="28/10/2026 17:40" />)

    expect(html).toContain('Cửa hàng cám Minh Phát')
    expect(html).toContain('50 bao x 320.000')
    expect(html).toContain('16.410.000')
    expect(html).toContain('Còn nợ')
    expect(html).toContain('Mười sáu triệu bốn trăm mười nghìn đồng')
    expect(html).toContain('Cảm ơn quý khách!')
    expect(html).not.toContain('TẠM TÍNH')
  })

  it('phiếu nháp ghi rõ TẠM TÍNH và không in đã trả / còn nợ', () => {
    const html = render(<SalesReceipt order={order({ status: 'DRAFT' })} store={store} printedAt="x" />)

    expect(html).toContain('PHIẾU TẠM TÍNH')
    expect(html).not.toContain('Còn nợ')
  })

  it('tên khách có ký tự HTML được escape, không thành thẻ', () => {
    const html = render(<SalesReceipt order={order()} store={store} printedAt="x" />)

    expect(html).toContain('&lt;b&gt;Đồng Tâm&lt;/b&gt;')
  })

  it('chưa cấu hình cửa hàng - vẫn in, không có phần đầu', () => {
    const html = render(<SalesReceipt order={order()} store={{ configured: false }} printedAt="x" />)

    expect(html).toContain('PHIẾU BÁN HÀNG')
    expect(html).not.toContain('class="store"')
  })
})
