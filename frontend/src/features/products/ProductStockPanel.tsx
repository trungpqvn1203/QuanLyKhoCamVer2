import { Alert, Button, Col, Flex, Row, Skeleton, Statistic, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { EMPTY_TEXT, formatQuantity, formatTons } from '../../utils/format'
import { PRODUCT_UNIT_LABELS } from './product-api'
import type { Product } from './product-api'
import { productStockApi } from './product-stock-api'
import type { ProductStockDetail } from './product-stock-api'
import { StockStatusTag } from './StockStatusTag'
import { WarehouseStockTable } from './WarehouseStockTable'

/**
 * Tab "Tồn kho" trong khung chi tiết SP: số tổng + bảng từng kho.
 * Tự tải khi mở tab (dữ liệu tồn đổi thường xuyên, không dùng lại số của dòng trong bảng).
 */
export function ProductStockPanel({ product }: { product: Product }) {
  const [detail, setDetail] = useState<ProductStockDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  useEffect(() => {
    let ignore = false
    productStockApi
      .get(product.id)
      .then((loaded) => {
        if (!ignore) setDetail(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được tồn kho')
      })
    return () => {
      ignore = true
    }
  }, [product.id, reloadCount])

  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        title={error}
        action={
          <Button
            size="small"
            onClick={() => {
              setError(null)
              setReloadCount((count) => count + 1)
            }}
          >
            Thử lại
          </Button>
        }
      />
    )
  }
  if (!detail) return <Skeleton active paragraph={{ rows: 4 }} />

  const unit = PRODUCT_UNIT_LABELS[detail.unit].toLowerCase()

  return (
    <Flex vertical gap={16}>
      <Row gutter={16}>
        <Col span={8}>
          <Statistic title={`Tổng tồn (${unit})`} value={formatQuantity(detail.stockQuantity)} />
          <Typography.Text type="secondary">{formatTons(detail.stockTons)}</Typography.Text>
        </Col>
        <Col span={8}>
          <Statistic
            title={`Đang về (${unit})`}
            value={detail.incomingQuantity > 0 ? `+${formatQuantity(detail.incomingQuantity)}` : EMPTY_TEXT}
          />
          <Typography.Text type="secondary">Phiếu nhập còn Nháp</Typography.Text>
        </Col>
        <Col span={8}>
          <Statistic
            title="Tồn tối thiểu"
            value={detail.minStockQuantity === undefined ? 'Không đặt' : formatQuantity(detail.minStockQuantity)}
          />
          <StockStatusTag status={detail.stockStatus} />
        </Col>
      </Row>
      <WarehouseStockTable detail={detail} />
      <Typography.Text type="secondary">
        Tồn chỉ thay đổi qua phiếu nhập/xuất/điều chuyển. Hàng đang về chưa được cộng vào tồn cho đến khi phiếu nhập hoàn tất.
      </Typography.Text>
    </Flex>
  )
}
