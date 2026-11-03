import type { DeliveryOrder } from './delivery-trip-api'

/**
 * Các phép trên danh sách điểm giao đang sửa (BR-DLV-05) — hàm thuần, không đổi mảng đầu vào.
 * Thứ tự trong mảng chính là thứ tự giao; stopOrder của backend chỉ để hiển thị lại sau khi lưu.
 */

/** Kéo điểm ở vị trí `from` thả vào vị trí `to` (chỉ số 0-based). Ngoài khoảng → giữ nguyên. */
export function moveStop<T>(stops: readonly T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= stops.length || to >= stops.length) return [...stops]
  const next = [...stops]
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved)
  return next
}

/** Thêm phiếu vào CUỐI chuyến; đã có thì bỏ qua. */
export function addStop(stops: readonly DeliveryOrder[], order: DeliveryOrder): DeliveryOrder[] {
  return stops.some((stop) => stop.documentId === order.documentId) ? [...stops] : [...stops, order]
}

export function removeStop(stops: readonly DeliveryOrder[], documentId: number): DeliveryOrder[] {
  return stops.filter((stop) => stop.documentId !== documentId)
}

/**
 * Tổng tấn = cộng tấn từng phiếu (backend đã làm tròn từng dòng 3 chữ số lẻ). Cộng số thực trong JS có thể lệch
 * (0.1 + 0.2 = 0.30000000000000004) → làm tròn lại về 3 chữ số lẻ.
 */
export function totalTons(stops: readonly Pick<DeliveryOrder, 'totalTons'>[]): number {
  return roundTons(stops.reduce((sum, stop) => sum + stop.totalTons, 0))
}

/** BR-DLV-04: số tấn vượt tải trọng; undefined khi xe không ghi tải trọng hoặc chưa vượt. */
export function overloadTons(capacityTons: number | undefined, tons: number): number | undefined {
  if (capacityTons === undefined || tons <= capacityTons) return undefined
  return roundTons(tons - capacityTons)
}

/** Phiếu chờ xếp chưa có trong danh sách đang sửa — phiếu vừa bỏ khỏi chuyến hiện lại ngay, chưa cần lưu. */
export function availableOrders(
  assignable: readonly DeliveryOrder[],
  removedFromTrip: readonly DeliveryOrder[],
  stops: readonly DeliveryOrder[],
): DeliveryOrder[] {
  const onTrip = new Set(stops.map((stop) => stop.documentId))
  const seen = new Set<number>()
  return [...assignable, ...removedFromTrip].filter((order) => {
    // Phiếu đã hủy / hoàn tất riêng (BR-DLV-07) bỏ khỏi chuyến thì không xếp lại được nữa
    if (onTrip.has(order.documentId) || seen.has(order.documentId) || order.documentStatus !== 'DRAFT') return false
    seen.add(order.documentId)
    return true
  })
}

/** Danh sách đang sửa khác bản đã lưu (thêm / bỏ / đổi thứ tự) → cần Lưu trước khi in hoặc xuất. */
export function stopsChanged(saved: readonly DeliveryOrder[], current: readonly DeliveryOrder[]): boolean {
  return saved.length !== current.length || saved.some((stop, index) => stop.documentId !== current[index].documentId)
}

/** Lọc phiếu chờ xếp theo mã phiếu, tên / mã khách, địa chỉ — không phân biệt hoa thường, không dấu. */
export function matchesKeyword(order: DeliveryOrder, keyword: string): boolean {
  const needle = normalize(keyword.trim())
  if (!needle) return true
  return [order.documentCode, order.customerCode, order.customerName, order.customerAddress ?? '']
    .some((text) => normalize(text).includes(needle))
}

function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()
}

function roundTons(tons: number): number {
  return Math.round(tons * 1000) / 1000
}
