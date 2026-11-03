import { Tag } from 'antd'
import { TRIP_STATUS_LABELS } from './delivery-trip-api'
import type { DeliveryTripStatus } from './delivery-trip-api'

/** Đang xếp = xanh dương (còn sửa, như phiếu Nháp); Đã xuất = xanh lá (hàng đã rời kho, như Hoàn tất). */
export function TripStatusTag({ status }: { status: DeliveryTripStatus }) {
  return (
    <Tag color={status === 'PLANNED' ? 'processing' : 'success'} variant="filled" style={{ marginInlineEnd: 0 }}>
      {TRIP_STATUS_LABELS[status]}
    </Tag>
  )
}
