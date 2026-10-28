import { formatDate, formatQuantity } from '../../../utils/format'
import { vndToWords } from '../../../utils/number-to-words'
import type { StoreInfo } from '../../store/store-info-api'
import type { SalesOrder } from '../sales-order-api'

/**
 * Mẫu in khổ K80 của phiếu bán (BR-PRT-02) — dùng chung cho khách và lái xe. Chỉ là HTML tĩnh — printK80 render rồi đưa vào iframe,
 * nên dùng thẻ HTML + class trong CSS in, không dùng component antd.
 */

const moneyFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const money = (amount: number) => moneyFormat.format(amount)

interface PrintProps {
  order: SalesOrder
  store: StoreInfo
  /** Thời điểm bấm in — in lên phiếu để phân biệt bản in lại */
  printedAt: string
}

function StoreHeader({ store }: { store: StoreInfo }) {
  if (!store.configured) return null
  return (
    <div className="center">
      <div className="store">{store.name}</div>
      {store.address && <div className="small">{store.address}</div>}
      {store.phone && <div className="small">ĐT: {store.phone}</div>}
      <hr />
    </div>
  )
}

/** Phiếu bán — kiểu bill siêu thị: tên hàng một dòng, "SL x đơn giá ... thành tiền" dòng dưới. */
export function SalesReceipt({ order, store, printedAt }: PrintProps) {
  const draft = order.status === 'DRAFT'
  return (
    <>
      <StoreHeader store={store} />
      <div className="center title">PHIẾU BÁN HÀNG</div>
      {/* BR-PRT-02: nháp chưa trừ kho, số có thể còn đổi → ghi rõ để khách không coi là chứng từ chính thức */}
      {draft && <div className="stamp">PHIẾU TẠM TÍNH – CHƯA HOÀN TẤT</div>}
      <div className="row">
        <span>Số: {order.code}</span>
        <span>Ngày: {formatDate(order.documentDate)}</span>
      </div>
      <div>
        Khách: {order.customerName}
        {order.customerPhone && ` – ${order.customerPhone}`}
      </div>
      <hr />
      {order.lines.map((line) => (
        <div className="item" key={line.id}>
          <div className="item-name">{line.productName}</div>
          <div className="row">
            <span>
              {formatQuantity(line.quantity)} {line.unitName.toLowerCase()} x {money(line.unitPrice)}
            </span>
            <span>{money(line.lineAmount)}</span>
          </div>
        </div>
      ))}
      <hr />
      <div className="row total">
        <span>TỔNG CỘNG</span>
        <span>{money(order.totalAmount)}</span>
      </div>
      {!draft && (
        <>
          <div className="row">
            <span>Đã thanh toán</span>
            <span>{money(order.paidAmount)}</span>
          </div>
          <div className="row bold">
            <span>Còn nợ</span>
            <span>{money(order.outstandingAmount)}</span>
          </div>
        </>
      )}
      <div className="small" style={{ marginTop: 4 }}>
        Bằng chữ: {vndToWords(order.totalAmount)}
      </div>
      <hr />
      {store.receiptFooter && <div className="center bold">{store.receiptFooter}</div>}
      <div className="center small">In lúc {printedAt}</div>
    </>
  )
}
