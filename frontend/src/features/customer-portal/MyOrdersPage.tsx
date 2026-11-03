import { RightOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Empty, Flex, Skeleton, Tag, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { PORTAL_STATUS_COLORS, PORTAL_STATUS_LABELS, portalApi } from './portal-api'
import type { PortalOrderSummary } from './portal-api'

const PAGE_SIZE = 20

/** "Đơn của tôi" (BR-POR-08): chỉ đơn của khách đang đăng nhập, mới nhất trước; tổng tiền chỉ có sau khi xác nhận. */
export function MyOrdersPage() {
  const navigate = useNavigate()
  const [orders, setOrders] = useState<PortalOrderSummary[] | null>(null)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback((pageToLoad: number) => {
    return portalApi
      .orders(pageToLoad, PAGE_SIZE)
      .then((result) => {
        setOrders((current) => (pageToLoad === 0 ? result.items : [...(current ?? []), ...result.items]))
        setPage(pageToLoad)
        setHasMore(!result.pagination.isLast)
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : 'Không tải được danh sách đơn'))
  }, [])

  useEffect(() => {
    void load(0)
  }, [load])

  const loadMore = async () => {
    setLoadingMore(true)
    await load(page + 1)
    setLoadingMore(false)
  }

  if (error) return <Alert type="error" showIcon title={error} />
  if (!orders) return <Skeleton active paragraph={{ rows: 6 }} />

  return (
    <Flex vertical gap={12}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        Đơn của tôi
      </Typography.Title>
      {orders.length === 0 ? (
        <Empty description="Bạn chưa có đơn nào">
          <Button type="primary" onClick={() => navigate('/khach')}>
            Đặt hàng
          </Button>
        </Empty>
      ) : (
        orders.map((order) => (
          <Card key={order.id} size="small" hoverable onClick={() => navigate(`/khach/don-hang/${order.id}`)}>
            <Flex justify="space-between" align="center" gap={8}>
              <Flex vertical gap={4}>
                <Flex gap={8} align="center" wrap>
                  <Typography.Text strong>{order.code}</Typography.Text>
                  <Tag color={PORTAL_STATUS_COLORS[order.status]} variant="filled" style={{ marginInlineEnd: 0 }}>
                    {PORTAL_STATUS_LABELS[order.status]}
                  </Tag>
                </Flex>
                <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                  Đặt ngày {formatDate(order.orderDate)}
                  {order.requestedDeliveryDate && ` · muốn nhận ${formatDate(order.requestedDeliveryDate)}`}
                </Typography.Text>
                {order.totalAmount !== undefined && <Typography.Text strong>{formatVnd(order.totalAmount)}</Typography.Text>}
              </Flex>
              <RightOutlined />
            </Flex>
          </Card>
        ))
      )}
      {hasMore && (
        <Button block loading={loadingMore} onClick={() => void loadMore()}>
          Xem thêm
        </Button>
      )}
    </Flex>
  )
}
