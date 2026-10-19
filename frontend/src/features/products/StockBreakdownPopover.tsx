import { Popover, Spin, Typography } from 'antd'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../../api/client'
import { productStockApi } from './product-stock-api'
import type { ProductStockDetail } from './product-stock-api'
import { WarehouseStockTable } from './WarehouseStockTable'

interface StockBreakdownPopoverProps {
  productId: number
  children: ReactNode
}

/**
 * Rê chuột (hoặc Tab tới) số tồn → hiện tồn từng kho.
 * Chỉ gọi API lần đầu mở, không tải trước cho cả trang (20 SP = 20 request thừa nếu không ai rê chuột).
 * Trang danh sách đặt `key` theo số tồn → tồn đổi thì popover tạo lại và tải số mới.
 */
export function StockBreakdownPopover({ productId, children }: StockBreakdownPopoverProps) {
  const [detail, setDetail] = useState<ProductStockDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleOpenChange = (open: boolean) => {
    if (!open || detail) return
    setError(null)
    productStockApi
      .get(productId)
      .then(setDetail)
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : 'Không tải được tồn theo kho'))
  }

  const content = error ? (
    <Typography.Text type="danger">{error}</Typography.Text>
  ) : detail ? (
    <div style={{ width: 420 }}>
      <WarehouseStockTable detail={detail} compact />
    </div>
  ) : (
    <Spin size="small" />
  )

  return (
    <Popover
      title="Tồn theo kho"
      content={content}
      trigger={['hover', 'focus']}
      mouseEnterDelay={0.3}
      placement="left"
      onOpenChange={handleOpenChange}
    >
      {children}
    </Popover>
  )
}
