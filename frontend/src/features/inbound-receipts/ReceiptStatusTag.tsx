import { Tag } from 'antd'
import type { TagProps } from 'antd'
import { RECEIPT_STATUS_LABELS } from './inbound-receipt-api'
import type { ReceiptStatus } from './inbound-receipt-api'

/**
 * Màu theo tên preset của Ant Design (không ghi mã màu — ADR-0002):
 * Nháp = xanh dương "đang xử lý", Hoàn tất = xanh lá, Đã hủy = xám, Đã đảo = cam (đã bị vô hiệu bằng giao dịch đảo).
 */
const STATUS_COLORS: Record<ReceiptStatus, TagProps['color']> = {
  DRAFT: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'default',
  REVERSED: 'warning',
}

export function ReceiptStatusTag({ status }: { status: ReceiptStatus }) {
  return (
    <Tag color={STATUS_COLORS[status]} variant="filled" style={{ marginInlineEnd: 0 }}>
      {RECEIPT_STATUS_LABELS[status]}
    </Tag>
  )
}
