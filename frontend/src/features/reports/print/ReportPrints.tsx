import { formatDate, formatQuantity } from '../../../utils/format'
import type { StoreInfo } from '../../store/store-info-api'
import type { StockByWarehouse, StockMovement } from '../report-api'

/**
 * Mẫu in A4 ngang của báo cáo tồn kho (BR-RPT-06): đầu trang tên cửa hàng + thời điểm in, tiêu đề, điều kiện lọc,
 * bảng có dòng tổng. HTML tĩnh — printA4Landscape render rồi in qua iframe.
 */

const tonsFormat = new Intl.NumberFormat('vi-VN', { minimumFractionDigits: 3, maximumFractionDigits: 3 })
const tons = (value: number) => tonsFormat.format(value)
const qty = (value: number) => (value === 0 ? '' : formatQuantity(value))

interface HeaderProps {
  store: StoreInfo
  title: string
  subtitle: string
  printedAt: string
}

function ReportHeader({ store, title, subtitle, printedAt }: HeaderProps) {
  return (
    <>
      <div className="header">
        <div>
          {store.configured && <div className="store">{store.name}</div>}
          {store.address && <div>{store.address}</div>}
        </div>
        <div className="muted">In lúc {printedAt}</div>
      </div>
      <div className="title">{title}</div>
      <div className="subtitle">{subtitle}</div>
    </>
  )
}

interface StockByWarehousePrintProps {
  report: StockByWarehouse
  store: StoreInfo
  filterText: string
  printedAt: string
}

export function StockByWarehousePrint({ report, store, filterText, printedAt }: StockByWarehousePrintProps) {
  return (
    <>
      <ReportHeader store={store} title="BÁO CÁO TỒN KHO THEO KHO" subtitle={filterText} printedAt={printedAt} />
      <table>
        <thead>
          <tr>
            <th>STT</th>
            <th>Mã SP</th>
            <th>Tên sản phẩm</th>
            <th>ĐVT</th>
            {report.warehouses.map((warehouse) => (
              <th key={warehouse.id}>{warehouse.code}</th>
            ))}
            <th>Tổng SL</th>
            <th>Tổng tấn</th>
          </tr>
        </thead>
        <tbody>
          {report.rows.map((row, index) => (
            <tr key={row.productId}>
              <td className="num">{index + 1}</td>
              <td>{row.productCode}</td>
              <td>{row.productName}</td>
              <td>{row.unitName}</td>
              {report.warehouses.map((warehouse) => (
                <td key={warehouse.id} className="num">
                  {qty(row.quantities[warehouse.id] ?? 0)}
                </td>
              ))}
              <td className="num">{formatQuantity(row.totalQuantity)}</td>
              <td className="num">{tons(row.totalTons)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={4}>Tổng (tấn) — {report.rows.length} sản phẩm</td>
            {report.warehouses.map((warehouse) => (
              <td key={warehouse.id} className="num">
                {tons(report.warehouseTons[warehouse.id] ?? 0)}
              </td>
            ))}
            <td />
            <td className="num">{tons(report.totalTons)}</td>
          </tr>
        </tfoot>
      </table>
    </>
  )
}

interface StockMovementPrintProps {
  report: StockMovement
  store: StoreInfo
  warehouseText: string
  printedAt: string
}

export function StockMovementPrint({ report, store, warehouseText, printedAt }: StockMovementPrintProps) {
  return (
    <>
      <ReportHeader
        store={store}
        title="BÁO CÁO NHẬP – XUẤT – TỒN"
        subtitle={`Từ ngày ${formatDate(report.from)} đến ngày ${formatDate(report.to)} · ${warehouseText}`}
        printedAt={printedAt}
      />
      <table>
        <thead>
          <tr>
            <th rowSpan={2}>STT</th>
            <th rowSpan={2}>Mã SP</th>
            <th rowSpan={2}>Tên sản phẩm</th>
            <th rowSpan={2}>ĐVT</th>
            <th colSpan={2}>Tồn đầu kỳ</th>
            <th colSpan={2}>Nhập trong kỳ</th>
            <th colSpan={2}>Xuất trong kỳ</th>
            <th colSpan={2}>Tồn cuối kỳ</th>
          </tr>
          <tr>
            <th>SL</th>
            <th>Tấn</th>
            <th>SL</th>
            <th>Tấn</th>
            <th>SL</th>
            <th>Tấn</th>
            <th>SL</th>
            <th>Tấn</th>
          </tr>
        </thead>
        <tbody>
          {report.rows.map((row, index) => (
            <tr key={row.productId}>
              <td className="num">{index + 1}</td>
              <td>{row.productCode}</td>
              <td>{row.productName}</td>
              <td>{row.unitName}</td>
              <td className="num">{formatQuantity(row.openingQuantity)}</td>
              <td className="num">{tons(row.openingTons)}</td>
              <td className="num">{formatQuantity(row.inboundQuantity)}</td>
              <td className="num">{tons(row.inboundTons)}</td>
              <td className="num">{formatQuantity(row.outboundQuantity)}</td>
              <td className="num">{tons(row.outboundTons)}</td>
              <td className="num">{formatQuantity(row.closingQuantity)}</td>
              <td className="num">{tons(row.closingTons)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={4}>Tổng (tấn) — {report.rows.length} sản phẩm</td>
            <td />
            <td className="num">{tons(report.totals.openingTons)}</td>
            <td />
            <td className="num">{tons(report.totals.inboundTons)}</td>
            <td />
            <td className="num">{tons(report.totals.outboundTons)}</td>
            <td />
            <td className="num">{tons(report.totals.closingTons)}</td>
          </tr>
        </tfoot>
      </table>
    </>
  )
}
