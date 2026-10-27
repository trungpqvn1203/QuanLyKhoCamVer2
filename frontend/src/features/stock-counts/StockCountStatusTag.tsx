import { Tag } from 'antd'
import type { TagProps } from 'antd'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import { STOCK_COUNT_STATUS_LABELS } from './stock-count-api'

// Cùng màu với trạng thái phiếu nhập (ReceiptStatusTag), chỉ khác nhãn: Nháp → "Đang kiểm"
const STATUS_COLORS: Record<ReceiptStatus, TagProps['color']> = {
  DRAFT: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'default',
  REVERSED: 'warning',
}

export function StockCountStatusTag({ status }: { status: ReceiptStatus }) {
  return (
    <Tag color={STATUS_COLORS[status]} variant="filled" style={{ marginInlineEnd: 0 }}>
      {STOCK_COUNT_STATUS_LABELS[status]}
    </Tag>
  )
}
