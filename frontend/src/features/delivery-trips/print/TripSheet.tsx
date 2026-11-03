import { formatDate, formatQuantity, formatTons } from '../../../utils/format'
import { formatPhone } from '../../../utils/phone'
import type { StoreInfo } from '../../store/store-info-api'
import type { DeliveryTrip } from '../delivery-trip-api'

/**
 * Phiếu chuyến khổ K80 cho lái xe (BR-DLV-08): điểm giao theo thứ tự, khách, SĐT, địa chỉ, hàng, tấn.
 * KHÔNG có giá — lái xe cầm phiếu bán của từng khách (BR-PRT-02). HTML tĩnh: printK80 render rồi đưa vào iframe,
 * nên dùng thẻ HTML + class của CSS in, không dùng component antd.
 */
interface TripSheetProps {
  trip: DeliveryTrip
  store: StoreInfo
  /** Thời điểm bấm in — phân biệt bản in lại sau khi sửa chuyến */
  printedAt: string
}

export function TripSheet({ trip, store, printedAt }: TripSheetProps) {
  return (
    <>
      {store.configured && (
        <div className="center">
          <div className="store">{store.name}</div>
          {store.phone && <div className="small">ĐT: {store.phone}</div>}
          <hr />
        </div>
      )}
      <div className="center title">PHIẾU CHUYẾN GIAO HÀNG</div>
      {trip.status === 'PLANNED' && <div className="stamp">CHUYẾN ĐANG XẾP – CHƯA XUẤT KHO</div>}
      <div className="row">
        <span>Số: {trip.code}</span>
        <span>Ngày: {formatDate(trip.tripDate)}</span>
      </div>
      <div>Xe: {trip.vehiclePlate}</div>
      <div>
        Lái xe: {trip.driverName}
        {trip.driverPhone && ` – ${formatPhone(trip.driverPhone)}`}
      </div>
      <div className="row bold">
        <span>{trip.stopCount} điểm giao</span>
        <span>{formatTons(trip.totalTons)}</span>
      </div>
      {trip.overloadTons !== undefined && (
        <div className="bold">Vượt tải trọng xe {formatTons(trip.overloadTons)}</div>
      )}
      {trip.note && <div className="small">Ghi chú: {trip.note}</div>}
      <hr />
      {trip.stops.map((stop, index) => (
        <div className="item" key={stop.documentId}>
          <div className="row item-name">
            <span>
              {index + 1}. {stop.customerName}
            </span>
            <span>{stop.documentCode}</span>
          </div>
          {stop.customerPhone && <div>ĐT: {formatPhone(stop.customerPhone)}</div>}
          <div className="small">Đ/c: {stop.customerAddress ?? 'chưa có địa chỉ'}</div>
          {stop.note && <div className="small">Ghi chú: {stop.note}</div>}
          {stop.lines.map((line) => (
            <div className="row" key={line.productId}>
              <span>
                {line.productName}: {formatQuantity(line.quantity)} {line.unitName.toLowerCase()}
              </span>
              <span>{formatTons(line.tons)}</span>
            </div>
          ))}
          <div className="small">Kho bốc: {stop.warehouseName}</div>
          <hr />
        </div>
      ))}
      <div className="center small">In lúc {printedAt}</div>
    </>
  )
}
