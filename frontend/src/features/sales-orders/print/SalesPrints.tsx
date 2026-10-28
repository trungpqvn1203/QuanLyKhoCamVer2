import { formatDate, formatQuantity, formatTons } from '../../../utils/format'
import { vndToWords } from '../../../utils/number-to-words'
import type { StoreInfo } from '../../store/store-info-api'
import type { SalesOrder, SalesOrderLine } from '../sales-order-api'

/**
 * Hai mẫu in khổ K80 của phiếu bán (BR-PRT-02, 03). Chỉ là HTML tĩnh — printK80 render rồi đưa vào iframe,
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

/** Phiếu bán cho khách — kiểu bill siêu thị: tên hàng một dòng, "SL x đơn giá ... thành tiền" dòng dưới. */
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

/** "50 bao, 20 túi" — cộng số lượng theo từng đơn vị (cộng chung bao với kg là vô nghĩa). */
function quantityByUnit(lines: SalesOrderLine[]): string {
  const totals = new Map<string, number>()
  for (const line of lines) {
    const unit = line.unitName.toLowerCase()
    totals.set(unit, (totals.get(unit) ?? 0) + line.quantity)
  }
  return [...totals].map(([unit, quantity]) => `${formatQuantity(quantity)} ${unit}`).join(', ')
}

/**
 * Phiếu xuất kho cho lái xe / bốc vác (BR-PRT-03): KHÔNG có giá; có kho, số lượng, tấn để xếp hàng và biết tải trọng.
 * In được cả lúc nháp — thiếu hàng thì sửa phiếu nháp rồi in lại.
 */
export function DeliverySlip({ order, store, printedAt }: PrintProps) {
  return (
    <>
      <StoreHeader store={store} />
      <div className="center title">PHIẾU XUẤT KHO – GIAO HÀNG</div>
      <div className="row">
        <span>Số: {order.code}</span>
        <span>{order.status === 'DRAFT' ? 'Nháp' : 'Đã hoàn tất'}</span>
      </div>
      <div>Ngày: {formatDate(order.documentDate)}</div>
      <div>
        Kho: {order.warehouseCode} · {order.warehouseName}
      </div>
      <div>Khách: {order.customerName}</div>
      {order.customerPhone && <div>SĐT: {order.customerPhone}</div>}
      {order.note && <div>Ghi chú: {order.note}</div>}
      <hr />
      {order.lines.map((line) => (
        <div className="item" key={line.id}>
          <div className="item-name">{line.productName}</div>
          <div className="row">
            <span className="bold">
              {formatQuantity(line.quantity)} {line.unitName.toLowerCase()}
            </span>
            <span>{formatTons(line.tons)}</span>
          </div>
        </div>
      ))}
      <hr />
      <div className="row total">
        <span>TỔNG</span>
        <span>{formatTons(order.totalTons)}</span>
      </div>
      <div className="bold">{quantityByUnit(order.lines)}</div>
      <div className="signs">
        <div>Thủ kho</div>
        <div>Lái xe</div>
      </div>
      <div className="center small">In lúc {printedAt}</div>
    </>
  )
}
