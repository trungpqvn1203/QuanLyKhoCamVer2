import { Tag } from 'antd'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { SALES_STATUS_LABELS } from './sales-order-api'
import type { SalesOrderStatus } from './sales-order-api'

/**
 * Trạng thái phiếu bán: như phiếu nhập, thêm "Chờ xác nhận" (BR-SAL-16) màu vàng cam — việc đang chờ người
 * xử lý, khác Nháp (xanh dương, kho đã nhận đơn và đang lên phiếu).
 */
export function SalesStatusTag({ status }: { status: SalesOrderStatus }) {
  if (status !== 'PENDING') return <ReceiptStatusTag status={status} />
  return (
    <Tag color="gold" variant="filled" style={{ marginInlineEnd: 0 }}>
      {SALES_STATUS_LABELS.PENDING}
    </Tag>
  )
}
