import { EditOutlined, RedoOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Descriptions, Flex, Input, List, Modal, Skeleton, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { formatDate, formatDateTime } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import type { ReorderState } from './OrderFormPage'
import { PORTAL_STATUS_COLORS, PORTAL_STATUS_LABELS, portalApi } from './portal-api'
import type { PortalOrder } from './portal-api'

/**
 * Chi tiết đơn của khách (BR-POR-08, 09): phần khách đặt; sau khi cửa hàng xác nhận thêm dòng chốt có giá và tổng.
 * Còn Chờ xác nhận → Sửa / Hủy đơn; mọi trạng thái → Đặt lại (giỏ điền sẵn từ phần khách đặt).
 */
export function OrderDetailPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const id = Number(useParams().id)
  const [order, setOrder] = useState<PortalOrder | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState(false)
  const [cancelNote, setCancelNote] = useState('')
  const [cancelBusy, setCancelBusy] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    portalApi
      .order(id)
      .then((loaded) => {
        if (!ignore) setOrder(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được đơn hàng')
      })
    return () => {
      ignore = true
    }
  }, [id])

  const cancel = async () => {
    if (!order) return
    setCancelBusy(true)
    setCancelError(null)
    try {
      setOrder(await portalApi.cancelOrder(order.id, cancelNote))
      setCancelling(false)
      message.success(`Đã hủy đơn ${order.code}`)
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : 'Không hủy được đơn')
    } finally {
      setCancelBusy(false)
    }
  }

  if (error) return <Alert type="error" showIcon title={error} />
  if (!order) return <Skeleton active paragraph={{ rows: 6 }} />

  const confirmed = order.lines.length > 0

  return (
    <Flex vertical gap={12}>
      <Flex justify="space-between" align="center" gap={8} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Đơn {order.code}
        </Typography.Title>
        <Tag color={PORTAL_STATUS_COLORS[order.status]} variant="filled" style={{ marginInlineEnd: 0 }}>
          {PORTAL_STATUS_LABELS[order.status]}
        </Tag>
      </Flex>

      {order.status === 'PENDING' && (
        <Alert type="info" showIcon title="Cửa hàng sẽ gọi lại để xác nhận số lượng và báo giá" />
      )}
      {order.cancelReason && <Alert type="warning" showIcon title={`Lý do hủy: ${order.cancelReason}`} />}

      <Descriptions
        bordered
        size="small"
        column={1}
        items={[
          { key: 'date', label: 'Ngày đặt', children: formatDateTime(order.createdAt) },
          ...(order.requestedDeliveryDate
            ? [{ key: 'delivery', label: 'Muốn nhận', children: formatDate(order.requestedDeliveryDate) }]
            : []),
          ...(order.note ? [{ key: 'note', label: 'Ghi chú', children: order.note }] : []),
        ]}
      />

      <Card size="small" title="Bạn đã đặt">
        <List
          size="small"
          dataSource={order.requestLines}
          renderItem={(line) => (
            <List.Item>
              <Typography.Text>{line.productName}</Typography.Text>
              <Typography.Text strong>
                {line.quantity} {line.unitName.toLowerCase()}
              </Typography.Text>
            </List.Item>
          )}
        />
      </Card>

      {confirmed && (
        <Card size="small" title="Cửa hàng xác nhận">
          <List
            size="small"
            dataSource={order.lines}
            renderItem={(line) => (
              <List.Item>
                <Flex vertical>
                  <Typography.Text>{line.productName}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {line.quantity} {line.unitName.toLowerCase()} × {formatVnd(line.unitPrice)}
                  </Typography.Text>
                </Flex>
                <Typography.Text strong>{formatVnd(line.lineAmount)}</Typography.Text>
              </List.Item>
            )}
            footer={
              <Flex justify="space-between">
                <Typography.Text strong>Tổng cộng</Typography.Text>
                <Typography.Text strong style={{ fontSize: 16 }}>
                  {formatVnd(order.totalAmount ?? 0)}
                </Typography.Text>
              </Flex>
            }
          />
        </Card>
      )}

      <Flex gap={8} wrap>
        {order.editable && (
          <>
            <Button icon={<EditOutlined />} onClick={() => navigate(`/khach/don-hang/${order.id}/sua`)}>
              Sửa đơn
            </Button>
            <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>
              Hủy đơn
            </Button>
          </>
        )}
        <Button
          type={order.editable ? 'default' : 'primary'}
          icon={<RedoOutlined />}
          onClick={() => navigate('/khach', { state: { reorderFrom: order } satisfies ReorderState })}
        >
          Đặt lại đơn này
        </Button>
      </Flex>

      <Modal
        open={cancelling}
        title={`Hủy đơn ${order.code}?`}
        okText="Hủy đơn"
        okButtonProps={{ danger: true }}
        cancelText="Không hủy"
        confirmLoading={cancelBusy}
        onOk={() => void cancel()}
        onCancel={() => setCancelling(false)}
      >
        {cancelError && <Alert type="error" showIcon title={cancelError} style={{ marginBottom: 12 }} />}
        <Typography.Paragraph>Cửa hàng chưa xác nhận đơn này nên bạn tự hủy được.</Typography.Paragraph>
        <Input.TextArea
          maxLength={300}
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Lý do (không bắt buộc), ví dụ: đặt nhầm"
          value={cancelNote}
          onChange={(event) => setCancelNote(event.target.value)}
        />
      </Modal>
    </Flex>
  )
}
