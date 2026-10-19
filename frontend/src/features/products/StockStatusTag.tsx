import { Tag } from 'antd'
import type { TagProps } from 'antd'
import { STOCK_STATUS_LABELS } from './product-stock-api'
import type { StockStatus } from './product-stock-api'

// Màu theo preset của Ant Design (ADR-0002: không ghi mã màu): Sắp hết = cam cảnh báo, Hết hàng = đỏ
const STATUS_COLORS: Record<StockStatus, TagProps['color']> = {
  IN_STOCK: 'success',
  LOW: 'warning',
  OUT: 'error',
}

/** Tình trạng tồn trên tổng mọi kho (BR-STK-04). */
export function StockStatusTag({ status }: { status: StockStatus }) {
  return (
    <Tag color={STATUS_COLORS[status]} variant="filled" style={{ marginInlineEnd: 0 }}>
      {STOCK_STATUS_LABELS[status]}
    </Tag>
  )
}
