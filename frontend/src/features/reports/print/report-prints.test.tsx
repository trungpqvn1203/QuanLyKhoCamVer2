import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { StockByWarehouse, StockMovement } from '../report-api'
import { StockByWarehousePrint, StockMovementPrint } from './ReportPrints'

const store = { configured: true, name: 'Cửa hàng cám Minh Phát', address: '12 Lý Bôn' }

describe('StockMovementPrint (BR-RPT-05, 06)', () => {
  it('có kỳ, kho, 4 nhóm cột và dòng tổng tấn', () => {
    const report: StockMovement = {
      from: '2026-10-01',
      to: '2026-10-29',
      rows: [
        { productId: 1, productCode: 'SP000001', productName: 'Cám heo 25kg', categoryName: 'Cám heo', unitName: 'Bao',
          openingQuantity: 10, inboundQuantity: 27, outboundQuantity: 5, closingQuantity: 32,
          openingTons: 0.25, inboundTons: 0.675, outboundTons: 0.125, closingTons: 0.8 },
      ],
      totals: { openingTons: 0.25, inboundTons: 0.675, outboundTons: 0.125, closingTons: 0.8 },
    }
    const html = renderToStaticMarkup(
      <StockMovementPrint report={report} store={store} warehouseText="Tất cả kho" printedAt="29/10/2026 09:00" />,
    )

    expect(html).toContain('BÁO CÁO NHẬP – XUẤT – TỒN')
    expect(html).toContain('Từ ngày 01/10/2026 đến ngày 29/10/2026 · Tất cả kho')
    expect(html).toContain('Tồn đầu kỳ')
    expect(html).toContain('Tồn cuối kỳ')
    expect(html).toContain('0,800')
    expect(html).toContain('Cửa hàng cám Minh Phát')
  })
})

describe('StockByWarehousePrint (BR-RPT-04, 06)', () => {
  it('mỗi kho một cột, ô không có hàng để trống, dòng tổng tấn theo kho', () => {
    const report: StockByWarehouse = {
      warehouses: [
        { id: 1, code: 'KHO001', name: 'Kho HN', active: true },
        { id: 2, code: 'KHO002', name: 'Kho HP', active: true },
      ],
      rows: [
        { productId: 1, productCode: 'SP000001', productName: 'Cám heo 25kg', categoryName: 'Cám heo', unitName: 'Bao',
          productActive: true, quantities: { 1: 15 }, totalQuantity: 15, totalTons: 0.375 },
      ],
      warehouseTons: { 1: 0.375 },
      totalTons: 0.375,
    }
    const html = renderToStaticMarkup(
      <StockByWarehousePrint report={report} store={store} filterText="Mọi danh mục" printedAt="x" />,
    )

    expect(html).toContain('<th>KHO001</th><th>KHO002</th>')
    expect(html).toContain('<td class="num">15</td><td class="num"></td>')
    expect(html).toContain('0,375')
  })
})
