import { MinusOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Empty, Flex, Form, Input, InputNumber, List, Modal, Skeleton, Typography, theme } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { matchesSearch } from '../../components/ui/product-quick-add'
import { formatDate, todayIso } from '../../utils/format'
import { portalApi } from './portal-api'
import type { PortalOrder, PortalProduct } from './portal-api'
import { cartFromRequestLines, cartLineCount, setQuantity, toOrderInput } from './portal-cart'
import type { Cart } from './portal-cart'
import { BOTTOM_NAV_HEIGHT } from './PortalLayout'

/** Đặt lại đơn cũ: trang chi tiết đơn chuyển sang đây kèm giỏ điền sẵn. */
export interface ReorderState {
  reorderFrom?: PortalOrder
}

interface SubmitValues {
  requestedDeliveryDate?: string
  note?: string
}

/**
 * Đặt hàng (BR-POR-07) — cũng là màn sửa đơn Chờ xác nhận (BR-POR-09, /don-hang/:id/sua).
 * Danh sách SP không có giá, không có tồn (BR-POR-06): khách chọn SL theo ĐVT, cửa hàng gọi lại báo giá khi xác nhận.
 */
export function OrderFormPage() {
  const { message, notification } = App.useApp()
  const { token } = theme.useToken()
  const navigate = useNavigate()
  const { id } = useParams()
  const editingId = id ? Number(id) : null
  const reorderFrom = (useLocation().state as ReorderState | null)?.reorderFrom

  const [products, setProducts] = useState<PortalProduct[] | null>(null)
  const [editing, setEditing] = useState<PortalOrder | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [cart, setCart] = useState<Cart>(() => (reorderFrom ? cartFromRequestLines(reorderFrom.requestLines) : {}))
  const [keyword, setKeyword] = useState('')
  const [reviewing, setReviewing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [form] = Form.useForm<SubmitValues>()

  useEffect(() => {
    let ignore = false
    Promise.all([portalApi.products(), editingId === null ? Promise.resolve(null) : portalApi.order(editingId)])
      .then(([loadedProducts, order]) => {
        if (ignore) return
        setProducts(loadedProducts)
        if (order) {
          if (!order.editable) {
            message.info('Cửa hàng đã tiếp nhận đơn này, không sửa được nữa')
            navigate(`/khach/don-hang/${order.id}`, { replace: true })
            return
          }
          setEditing(order)
          setCart(cartFromRequestLines(order.requestLines))
          form.setFieldsValue({ requestedDeliveryDate: order.requestedDeliveryDate, note: order.note })
        }
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được danh sách sản phẩm')
      })
    return () => {
      ignore = true
    }
  }, [editingId, form, message, navigate])

  const byId = useMemo(() => new Map((products ?? []).map((product) => [product.id, product])), [products])
  const visible = (products ?? []).filter((product) =>
    matchesSearch(`${product.code} ${product.name} ${product.categoryName}`, keyword),
  )
  // SP đã chọn lên đầu — khách đặt lại đơn cũ thấy ngay những gì đã có trong giỏ
  const sorted = [...visible].sort((a, b) => Number(Boolean(cart[b.id])) - Number(Boolean(cart[a.id])))
  const count = cartLineCount(cart)

  const submit = async (values: SubmitValues) => {
    const { input, unavailable } = toOrderInput(cart, new Set(byId.keys()), values.requestedDeliveryDate, values.note)
    if (input.lines.length === 0) {
      setSubmitError('Chưa chọn sản phẩm nào còn bán')
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      const saved = editing ? await portalApi.updateOrder(editing.id, input) : await portalApi.createOrder(input)
      notification.success({
        title: editing ? 'Đã cập nhật đơn' : 'Đã gửi đơn',
        description: `${saved.code} — cửa hàng sẽ gọi lại để xác nhận số lượng và giá`,
      })
      if (unavailable.length > 0) message.warning(`${unavailable.length} sản phẩm của đơn cũ không còn bán nên đã được bỏ ra`)
      navigate(`/khach/don-hang/${saved.id}`, { replace: true })
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Không gửi được đơn, vui lòng thử lại')
    } finally {
      setSubmitting(false)
    }
  }

  if (loadError) return <Alert type="error" showIcon title={loadError} />
  if (!products) return <Skeleton active paragraph={{ rows: 8 }} />

  return (
    <Flex vertical gap={12}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {editing ? `Sửa đơn ${editing.code}` : 'Đặt hàng'}
      </Typography.Title>
      {reorderFrom && !editing && (
        <Alert type="info" showIcon title={`Đặt lại theo đơn ${reorderFrom.code} — sửa số lượng nếu cần rồi gửi`} />
      )}
      <Input
        allowClear
        size="large"
        prefix={<SearchOutlined />}
        placeholder="Tìm cám theo tên hoặc mã (gõ không dấu cũng được)"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />
      {sorted.length === 0 ? (
        <Empty description="Không có sản phẩm phù hợp" />
      ) : (
        <List
          dataSource={sorted}
          style={{ background: token.colorBgContainer, borderRadius: token.borderRadiusLG }}
          renderItem={(product) => {
            const quantity = cart[product.id] ?? 0
            return (
              <List.Item style={{ padding: '12px 16px' }}>
                <Flex justify="space-between" align="center" gap={12} style={{ width: '100%' }}>
                  <Flex vertical style={{ minWidth: 0 }}>
                    <Typography.Text strong={quantity > 0} ellipsis>
                      {product.name}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {product.code} · {product.categoryName} · tính theo {product.unitName.toLowerCase()}
                    </Typography.Text>
                  </Flex>
                  <Flex align="center" gap={4} style={{ flexShrink: 0 }}>
                    <Button
                      icon={<MinusOutlined />}
                      disabled={quantity === 0}
                      aria-label={`Bớt ${product.name}`}
                      onClick={() => setCart((current) => setQuantity(current, product.id, quantity - 1))}
                    />
                    <InputNumber<number>
                      min={0}
                      value={quantity || null}
                      placeholder="0"
                      inputMode="numeric"
                      controls={false}
                      style={{ width: 64 }}
                      aria-label={`Số lượng ${product.name}`}
                      onChange={(value) => setCart((current) => setQuantity(current, product.id, value))}
                    />
                    <Button
                      icon={<PlusOutlined />}
                      aria-label={`Thêm ${product.name}`}
                      onClick={() => setCart((current) => setQuantity(current, product.id, quantity + 1))}
                    />
                  </Flex>
                </Flex>
              </List.Item>
            )
          }}
        />
      )}

      {/* Thanh giỏ hàng: ngay trên thanh điều hướng dưới */}
      <Flex
        align="center"
        justify="space-between"
        gap={12}
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: BOTTOM_NAV_HEIGHT,
          padding: '10px 16px',
          background: token.colorBgContainer,
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          zIndex: 9,
        }}
      >
        <Typography.Text>{count === 0 ? 'Chưa chọn sản phẩm' : `Đã chọn ${count} sản phẩm`}</Typography.Text>
        <Button type="primary" size="large" disabled={count === 0} onClick={() => setReviewing(true)}>
          {editing ? 'Xem & lưu' : 'Xem & gửi đơn'}
        </Button>
      </Flex>
      <div style={{ height: 56 }} />

      <Modal
        open={reviewing}
        title={editing ? `Lưu đơn ${editing.code}` : 'Gửi đơn cho cửa hàng'}
        okText={editing ? 'Lưu đơn' : 'Gửi đơn'}
        cancelText="Chọn tiếp"
        confirmLoading={submitting}
        onOk={() => form.submit()}
        onCancel={() => setReviewing(false)}
        forceRender
      >
        {submitError && <Alert type="error" showIcon title={submitError} style={{ marginBottom: 12 }} />}
        <List
          size="small"
          dataSource={Object.entries(cart).map(([productId, quantity]) => ({ productId: Number(productId), quantity }))}
          renderItem={(line) => {
            const product = byId.get(line.productId)
            return (
              <List.Item>
                <Typography.Text type={product ? undefined : 'secondary'} delete={!product}>
                  {product?.name ?? 'Sản phẩm không còn bán'}
                </Typography.Text>
                <Typography.Text strong>
                  {line.quantity} {product?.unitName.toLowerCase()}
                </Typography.Text>
              </List.Item>
            )
          }}
        />
        <Form<SubmitValues> form={form} layout="vertical" onFinish={submit} style={{ marginTop: 12 }}>
          <Form.Item
            name="requestedDeliveryDate"
            label="Ngày muốn nhận hàng"
            extra="Để trống nếu chưa cần hẹn ngày"
            rules={[
              {
                validator: async (_, value?: string) => {
                  if (value && value < todayIso()) throw new Error(`Chọn từ hôm nay (${formatDate(todayIso())}) trở đi`)
                },
              },
            ]}
          >
            <Input type="date" min={todayIso()} />
          </Form.Item>
          <Form.Item name="note" label="Ghi chú cho cửa hàng">
            <Input.TextArea maxLength={500} autoSize={{ minRows: 2, maxRows: 4 }} placeholder="Ví dụ: giao buổi sáng, gọi trước khi đến" />
          </Form.Item>
        </Form>
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          Giá sẽ được cửa hàng báo khi xác nhận đơn.
        </Typography.Text>
      </Modal>
    </Flex>
  )
}
