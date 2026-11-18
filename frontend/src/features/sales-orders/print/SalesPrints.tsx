import { formatQuantity } from '../../../utils/format'
import { vndToWords } from '../../../utils/number-to-words'
import type { StoreInfo } from '../../store/store-info-api'
import type { SalesOrder } from '../sales-order-api'

/**
 * Mẫu in khổ K80 của phiếu bán (BR-PRT-02) — dùng chung cho khách và lái xe. Chỉ là HTML tĩnh — printK80 render rồi đưa vào iframe,
 * nên dùng thẻ HTML + class trong CSS in, không dùng component antd.
 */

const moneyFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const money = (amount: number) => moneyFormat.format(amount)

/** "2026-11-17" → "Ngày 17 tháng 11 năm 2026" */
function formatLongDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `Ngày ${day} tháng ${month} năm ${year}`
}

interface PrintProps {
  order: SalesOrder
  store: StoreInfo
  /** Thời điểm bấm in — in lên phiếu để phân biệt bản in lại */
  printedAt: string
}

function StoreHeader({ store }: { store: StoreInfo }) {
  if (!store.configured) return null
  return (
    <div className="center" style={{ marginBottom: 6 }}>
      <div className="store">{store.name}</div>
      {store.address && <div className="small">Đ/C: {store.address}</div>}
      {store.phone && <div className="small">Điện thoại: {store.phone}</div>}
      {store.bankAccountHolder && <div className="small">Chủ tài khoản: {store.bankAccountHolder}</div>}
      {store.bankAccount && <div className="small">STK: {store.bankAccount}</div>}
    </div>
  )
}

/** Phiếu bán khổ K80 — bảng kẻ khung (TT | Sản phẩm | Đơn giá | SL | Thành tiền) theo mẫu phiếu xuất kho KiotViet. */
export function SalesReceipt({ order, store, printedAt }: PrintProps) {
  const draft = order.status === 'DRAFT'
  return (
    <>
      <StoreHeader store={store} />
      <div className="center title">PHIẾU XUẤT KHO</div>
      {/* BR-PRT-02: nháp chưa trừ kho, số có thể còn đổi → ghi rõ để khách không coi là chứng từ chính thức */}
      {draft && <div className="stamp">PHIẾU TẠM TÍNH – CHƯA HOÀN TẤT</div>}
      <div className="center small">Số HĐ: {order.code}</div>
      <div className="center small">{formatLongDate(order.documentDate)}</div>
      <div className="row small" style={{ marginTop: 4 }}>
        <span>Khách hàng: {order.customerName}</span>
        <span>SĐT: {order.customerPhone ?? ''}</span>
      </div>
      <table className="receipt-table">
        <thead>
          <tr>
            <th>TT</th>
            <th>Sản phẩm</th>
            <th>
              Đơn
              <br />
              giá
            </th>
            <th>SL</th>
            <th>
              Thành
              <br />
              tiền
            </th>
          </tr>
        </thead>
        <tbody>
          {order.lines.map((line, index) => (
            <tr key={line.id}>
              <td className="center">{index + 1}</td>
              <td>
                {line.productName} ({line.unitName})
              </td>
              <td className="num">{money(line.unitPrice)}</td>
              <td className="center">{formatQuantity(line.quantity)}</td>
              <td className="num">{money(line.lineAmount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="summary">
        <div className="summary-row bold">
          <span>Tổng tiền hàng:</span>
          <span>{money(order.totalAmount)}</span>
        </div>
        {!draft && (
          <>
            <div className="summary-row">
              <span>Đã thanh toán:</span>
              <span>{money(order.paidAmount)}</span>
            </div>
            <div className="summary-row bold">
              <span>Còn nợ:</span>
              <span>{money(order.outstandingAmount)}</span>
            </div>
          </>
        )}
      </div>
      <div className="small" style={{ marginTop: 4 }}>
        Bằng chữ: {vndToWords(order.totalAmount)}
      </div>
      {store.receiptFooter && <div className="footer-greeting">{store.receiptFooter}</div>}
      <div className="center small" style={{ marginTop: 4 }}>
        In lúc {printedAt}
      </div>
    </>
  )
}
