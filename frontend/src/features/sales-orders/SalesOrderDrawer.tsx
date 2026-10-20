import { CheckOutlined, DollarOutlined, RollbackOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, Form, Skeleton } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { useCan } from '../auth/auth-context'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { PAYMENT_METHOD_LABELS, salesOrderApi } from './sales-order-api'
import type { PaymentInput, SalePayment, SalesOrder } from './sales-order-api'
import { emptySalesOrderForm, toSalesFormValues, toSalesOrderInput } from './sales-order-form'
import type { SalesOrderFormValues } from './sales-order-form'
import { PaymentModal } from './PaymentModal'
import { SalesOrderForm } from './SalesOrderForm'
import { SalesOrderView } from './SalesOrderView'
import type { SalesReferences } from './use-sales-references'

interface SalesOrderDrawerProps {
  open: boolean
  /** null = tạo phiếu mới */
  orderId: number | null
  references: SalesReferences
  onClose: () => void
  /** Phiếu vừa thay đổi ở backend → trang gọi tải lại */
  onChanged: () => void
}

const DRAWER_WIDTH = 1080

/** Hộp lý do đang mở: hủy / đảo phiếu, hoặc hoàn một khoản thu. */
type ReasonAction = { kind: 'cancel' } | { kind: 'reverse' } | { kind: 'reversePayment'; payment: SalePayment }

/**
 * Khung phiếu bán — một phiếu một khung, từ lúc lập đến lúc thu đủ tiền (cùng cách làm với phiếu nhập).
 * - Tạo mới / Nháp: form; nút Lưu nháp, Hoàn tất (trừ tồn); phiếu nháp đã lưu có thêm Hủy phiếu.
 * - Hoàn tất: xem + thanh toán; nút Thu tiền (khi còn nợ), Đảo phiếu, Hoàn từng khoản thu.
 * - Đã hủy / Đã đảo: chỉ xem.
 * Nút chỉ là gợi ý — backend khóa phiếu và kiểm tra lại, lỗi (thiếu tồn, thu vượt nợ…) hiện trong khung.
 */
export function SalesOrderDrawer({ open, orderId, references, onClose, onChanged }: SalesOrderDrawerProps) {
  const { modal, notification } = App.useApp()
  const [form] = Form.useForm<SalesOrderFormValues>()
  const [order, setOrder] = useState<SalesOrder | null>(null)
  const [loading, setLoading] = useState(orderId !== null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [busy, setBusy] = useState<'save' | 'complete' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null)
  const [paying, setPaying] = useState(false)

  useEffect(() => {
    if (orderId === null) return
    let ignore = false
    salesOrderApi
      .get(orderId)
      .then((loaded) => {
        if (!ignore) setOrder(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được phiếu bán')
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [orderId, reloadCount])

  // Quản lý kho chỉ xem phiếu bán (ADR-0003): không hiện form và nút thao tác — backend vẫn chặn nếu gọi thẳng API
  const canWrite = useCan('sales.write')
  const editable = canWrite && (order ? order.status === 'DRAFT' : orderId === null)
  const showForm = !loading && !loadError && editable
  const initialValues = useMemo(() => (order ? toSalesFormValues(order) : emptySalesOrderForm()), [order])

  const formVersion = order ? `${order.id}:${order.updatedAt}` : 'new'
  useEffect(() => {
    if (showForm) form.resetFields()
  }, [formVersion, showForm, form])

  const retryLoad = () => {
    setLoading(true)
    setLoadError(null)
    setReloadCount((count) => count + 1)
  }

  const handleError = (err: unknown) => {
    const fieldErrors = toFormFieldErrors(err)
    if (fieldErrors.length > 0 && showForm) {
      form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      setErrorMessage('Có ô chưa hợp lệ — xem lỗi ngay dưới từng ô')
    } else {
      setErrorMessage(err instanceof ApiError ? err.message : 'Đã có lỗi xảy ra')
    }
  }

  const validValues = async (): Promise<SalesOrderFormValues | null> => {
    try {
      await form.validateFields()
      return form.getFieldsValue(true) as SalesOrderFormValues
    } catch {
      return null
    }
  }

  const persist = async (values: SalesOrderFormValues): Promise<SalesOrder> => {
    const input = toSalesOrderInput(values)
    const saved = order ? await salesOrderApi.update(order.id, input) : await salesOrderApi.create(input)
    setOrder(saved)
    return saved
  }

  const saveDraft = async () => {
    const values = await validValues()
    if (!values) return
    setBusy('save')
    setErrorMessage(null)
    try {
      const saved = await persist(values)
      notification.success({
        title: order ? 'Đã lưu phiếu bán nháp' : 'Đã tạo phiếu bán nháp',
        description: `${saved.code} · ${saved.customerName} — chưa trừ tồn kho`,
      })
      onChanged()
    } catch (err) {
      handleError(err)
    } finally {
      setBusy(null)
    }
  }

  const complete = async () => {
    const values = await validValues()
    if (!values) return
    modal.confirm({
      title: order ? `Hoàn tất phiếu bán ${order.code}?` : 'Lưu và hoàn tất phiếu bán?',
      content:
        'Tồn kho tại kho xuất sẽ bị trừ theo số lượng trên phiếu; thiếu tồn ở bất kỳ dòng nào thì không dòng nào bị trừ. ' +
        'Sau khi hoàn tất, phiếu bị khóa và bắt đầu tính công nợ.',
      okText: 'Hoàn tất',
      cancelText: 'Xem lại',
      onOk: async () => {
        setBusy('complete')
        setErrorMessage(null)
        try {
          let current = order
          if (!current || form.isFieldsTouched()) {
            current = await persist(values)
            onChanged()
          }
          const completed = await salesOrderApi.complete(current.id)
          setOrder(completed)
          notification.success({
            title: 'Đã hoàn tất phiếu bán',
            description: `${completed.code} · đã trừ tồn ${completed.warehouseName}, khách nợ ${formatVnd(completed.outstandingAmount)}`,
          })
          onChanged()
        } catch (err) {
          // Ví dụ thiếu tồn — phiếu vẫn là Nháp, nội dung mới nhất đã được lưu
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  const submitPayment = async (input: PaymentInput) => {
    if (!order) return
    const updated = await salesOrderApi.recordPayment(order.id, input)
    setOrder(updated)
    setErrorMessage(null)
    notification.success({
      title: 'Đã ghi nhận thanh toán',
      description: `${updated.code} · còn nợ ${formatVnd(updated.outstandingAmount)}`,
    })
    onChanged()
  }

  // Ném lỗi ra để ReasonModal giữ hộp mở và hiện lỗi (ví dụ "đã thu tiền" khi đảo)
  const submitReason = async (reason: string | undefined) => {
    if (!order || !reasonAction) return
    let updated: SalesOrder
    let title: string
    let description: string
    if (reasonAction.kind === 'cancel') {
      updated = await salesOrderApi.cancel(order.id, reason)
      title = 'Đã hủy phiếu bán'
      description = `${updated.code} · phiếu được giữ lại để tra cứu`
    } else if (reasonAction.kind === 'reverse') {
      updated = await salesOrderApi.reverse(order.id, reason ?? '')
      title = 'Đã đảo phiếu bán'
      description = `${updated.code} · hàng đã về lại kho ${updated.warehouseName}`
    } else {
      updated = await salesOrderApi.reversePayment(order.id, reasonAction.payment.id, reason ?? '')
      title = 'Đã hoàn khoản thu'
      description = `${updated.code} · còn nợ ${formatVnd(updated.outstandingAmount)}`
    }
    setOrder(updated)
    setErrorMessage(null)
    notification.success({ title, description })
    onChanged()
  }

  const requestClose = () => {
    if (busy) return
    if (!showForm || !form.isFieldsTouched()) {
      onClose()
      return
    }
    modal.confirm({
      title: 'Bỏ các thay đổi chưa lưu?',
      content: 'Dữ liệu vừa nhập trên phiếu sẽ mất.',
      okText: 'Bỏ thay đổi',
      okButtonProps: { danger: true },
      cancelText: 'Tiếp tục sửa',
      onOk: onClose,
    })
  }

  const title = order ? (
    <Flex align="center" gap={8}>
      <span>Phiếu bán {order.code}</span>
      <ReceiptStatusTag status={order.status} />
    </Flex>
  ) : orderId === null ? (
    'Tạo phiếu bán'
  ) : (
    'Phiếu bán'
  )

  const footer =
    loading || loadError ? null : (
      <Flex justify="space-between" gap={8} wrap>
        <Flex gap={8}>
          {canWrite && order?.status === 'DRAFT' && (
            <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setReasonAction({ kind: 'cancel' })}>
              Hủy phiếu
            </Button>
          )}
          {canWrite && order?.status === 'COMPLETED' && (
            <Button danger icon={<RollbackOutlined />} onClick={() => setReasonAction({ kind: 'reverse' })}>
              Đảo phiếu
            </Button>
          )}
        </Flex>
        <Flex gap={8}>
          <Button onClick={requestClose} disabled={busy !== null}>
            Đóng
          </Button>
          {canWrite && order?.status === 'COMPLETED' && order.outstandingAmount > 0 && (
            <Button type="primary" icon={<DollarOutlined />} onClick={() => setPaying(true)}>
              Thu tiền
            </Button>
          )}
          {showForm && (
            <>
              <Button icon={<SaveOutlined />} loading={busy === 'save'} disabled={busy === 'complete'} onClick={saveDraft}>
                Lưu nháp
              </Button>
              <Button
                type="primary"
                icon={<CheckOutlined />}
                loading={busy === 'complete'}
                disabled={busy === 'save'}
                onClick={complete}
              >
                {order ? 'Hoàn tất' : 'Lưu & hoàn tất'}
              </Button>
            </>
          )}
        </Flex>
      </Flex>
    )

  const reasonTexts = (action: ReasonAction, current: SalesOrder) => {
    switch (action.kind) {
      case 'cancel':
        return {
          title: `Hủy phiếu bán ${current.code}?`,
          description: 'Phiếu nháp chưa tác động tồn kho. Sau khi hủy, phiếu được giữ lại để tra cứu nhưng không sửa hay hoàn tất được nữa.',
          submitText: 'Hủy phiếu',
        }
      case 'reverse':
        return {
          title: `Đảo phiếu bán ${current.code}?`,
          description:
            `Dùng khi giao sai hoặc hàng không đạt chất lượng. Hàng trên phiếu sẽ về lại kho ${current.warehouseName}; ` +
            'lịch sử giữ cả giao dịch xuất và giao dịch đảo. Mỗi phiếu chỉ đảo một lần. ' +
            (current.paidAmount > 0
              ? `Phiếu đã thu ${formatVnd(current.paidAmount)} — phải hoàn các khoản thu trước, nếu không hệ thống sẽ từ chối.`
              : ''),
          submitText: 'Đảo phiếu',
        }
      case 'reversePayment':
        return {
          title: `Hoàn khoản thu ${formatVnd(action.payment.amount)}?`,
          description:
            `${PAYMENT_METHOD_LABELS[action.payment.method]} ngày ${formatDate(action.payment.paymentDate)}. ` +
            'Dùng khi ghi nhầm hoặc đã trả lại tiền cho khách. Khoản thu gốc vẫn được giữ trong lịch sử, kèm một dòng hoàn trỏ về nó; ' +
            'số còn nợ của phiếu tăng lại tương ứng.',
          submitText: 'Hoàn khoản thu',
        }
    }
  }

  return (
    <Drawer open={open} title={title} size={DRAWER_WIDTH} onClose={requestClose} footer={footer} destroyOnHidden>
      {loading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : loadError ? (
        <Alert
          type="error"
          showIcon
          title={loadError}
          action={
            <Button size="small" onClick={retryLoad}>
              Thử lại
            </Button>
          }
        />
      ) : (
        <>
          {errorMessage && (
            <Alert
              type="error"
              showIcon
              closable
              title={errorMessage}
              onClose={() => setErrorMessage(null)}
              style={{ marginBottom: 16 }}
            />
          )}
          {showForm ? (
            <SalesOrderForm form={form} initialValues={initialValues} references={references} order={order} disabled={busy !== null} />
          ) : (
            order && (
              <SalesOrderView
                order={order}
                onReversePayment={
                  canWrite && order.status === 'COMPLETED' ? (payment) => setReasonAction({ kind: 'reversePayment', payment }) : undefined
                }
              />
            )
          )}
        </>
      )}

      {order && reasonAction && (
        <ReasonModal
          key={reasonAction.kind === 'reversePayment' ? `payment-${reasonAction.payment.id}` : reasonAction.kind}
          open
          required={reasonAction.kind !== 'cancel'}
          {...reasonTexts(reasonAction, order)}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
      {order && paying && <PaymentModal open order={order} onSubmit={submitPayment} onClose={() => setPaying(false)} />}
    </Drawer>
  )
}
