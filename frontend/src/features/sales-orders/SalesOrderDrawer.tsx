import {
  ArrowLeftOutlined,
  CheckOutlined,
  DollarOutlined,
  EditOutlined,
  PrinterOutlined,
  RollbackOutlined,
  SaveOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, Form, Skeleton } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { formatDate, formatDateTime } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { useCan } from '../auth/auth-context'
import { storeInfoApi } from '../store/store-info-api'
import {
  confirmationFormValues,
  emptyPendingOrderForm,
  toPendingFormValues,
  toPendingOrderInput,
} from './pending-order-form'
import type { PendingOrderFormValues } from './pending-order-form'
import { PAYMENT_METHOD_LABELS, customerPriceApi, salesOrderApi } from './sales-order-api'
import type { PaymentInput, PriceAdjustmentInput, SalePayment, SalesOrder } from './sales-order-api'
import { emptySalesOrderForm, toSalesFormValues, toSalesOrderInput } from './sales-order-form'
import type { SalesOrderFormValues } from './sales-order-form'
import { PaymentModal } from './PaymentModal'
import { PendingOrderForm } from './PendingOrderForm'
import { PriceAdjustmentModal } from './PriceAdjustmentModal'
import { RequestLinesTable } from './RequestLinesTable'
import { SalesOrderForm } from './SalesOrderForm'
import { SalesOrderView } from './SalesOrderView'
import { SalesStatusTag } from './SalesStatusTag'
import type { SalesReferences } from './use-sales-references'

interface SalesOrderDrawerProps {
  open: boolean
  /** null = tạo mới */
  orderId: number | null
  /** Chỉ dùng khi tạo mới: 'pending' = ghi đơn khách đặt (BR-SAL-17), 'sale' = kho lập phiếu nháp trực tiếp */
  createMode?: 'sale' | 'pending'
  references: SalesReferences
  onClose: () => void
  /** Phiếu vừa thay đổi ở backend → trang gọi tải lại */
  onChanged: () => void
}

const DRAWER_WIDTH = 1080

/** Hộp lý do đang mở: hủy / đảo phiếu, hoặc hoàn một khoản thu. */
type ReasonAction = { kind: 'cancel' } | { kind: 'reverse' } | { kind: 'reversePayment'; payment: SalePayment }

/**
 * Khung phiếu bán — một phiếu một khung, từ lúc khách đặt đến lúc thu đủ tiền (cùng cách làm với phiếu nhập).
 * - Đơn khách đặt (ghi mới / Chờ xác nhận): form khách đặt (không kho, không giá); nút Lưu đơn, Xác nhận đơn,
 *   Hủy đơn (lý do bắt buộc). Xác nhận đơn → form phiếu bán điền sẵn từ phần khách đặt + giá riêng; kho sửa
 *   SL / đổi SP rồi bấm Xác nhận → phiếu thành Nháp (BR-SAL-19), vẫn trong khung này.
 * - Tạo mới / Nháp: form; nút Lưu nháp, Hoàn tất (trừ tồn); phiếu nháp đã lưu có thêm Hủy phiếu.
 * - Hoàn tất: xem + thanh toán; nút Thu tiền (khi còn nợ), Đảo phiếu, Hoàn từng khoản thu.
 * - Đã hủy / Đã đảo: chỉ xem.
 * Nút chỉ là gợi ý — backend khóa phiếu và kiểm tra lại, lỗi (thiếu tồn, thu vượt nợ…) hiện trong khung.
 */
export function SalesOrderDrawer({
  open,
  orderId,
  createMode = 'sale',
  references,
  onClose,
  onChanged,
}: SalesOrderDrawerProps) {
  const { message, modal, notification } = App.useApp()
  const [form] = Form.useForm<SalesOrderFormValues>()
  const [pendingForm] = Form.useForm<PendingOrderFormValues>()
  // Đang ở bước xác nhận đơn khách đặt: giá trị điền sẵn cho form phiếu bán (null = không xác nhận)
  const [confirmValues, setConfirmValues] = useState<SalesOrderFormValues | null>(null)
  const [order, setOrder] = useState<SalesOrder | null>(null)
  const [loading, setLoading] = useState(orderId !== null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [busy, setBusy] = useState<'save' | 'complete' | 'confirm' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null)
  const [paying, setPaying] = useState(false)
  const [adjustingPrice, setAdjustingPrice] = useState(false)

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
  const ready = !loading && !loadError && canWrite
  const confirming = confirmValues !== null
  const isPending = order ? order.status === 'PENDING' : orderId === null && createMode === 'pending'
  const showPendingForm = ready && isPending && !confirming
  const showForm = ready && (confirming || (order ? order.status === 'DRAFT' : orderId === null && createMode === 'sale'))
  // Phiếu từ đơn khách đặt (đang hoặc đã xác nhận) giữ nguyên khách — BR-SAL-18, 19
  const lockCustomer = confirming || order?.confirmedAt !== undefined
  const initialValues = useMemo(
    () => confirmValues ?? (order ? toSalesFormValues(order) : emptySalesOrderForm()),
    [order, confirmValues],
  )
  const pendingInitialValues = useMemo(() => (order ? toPendingFormValues(order) : emptyPendingOrderForm()), [order])
  const activeForm = showPendingForm ? pendingForm : form

  const formVersion = `${order ? `${order.id}:${order.updatedAt}` : 'new'}:${confirming}`
  useEffect(() => {
    if (showForm) form.resetFields()
    if (showPendingForm) pendingForm.resetFields()
  }, [formVersion, showForm, showPendingForm, form, pendingForm])

  /**
   * BR-PRT-02: một mẫu in dùng chung cho khách và lái xe. In bản ĐÃ LƯU của phiếu. Đang sửa nháp mà chưa lưu thì chặn — in ra sẽ khác phiếu thật
   * (ví dụ vừa giảm số lượng vì thiếu hàng nhưng chưa bấm Lưu nháp).
   */
  const print = async () => {
    if (!order) return
    if (showForm && form.isFieldsTouched()) {
      message.warning('Phiếu có thay đổi chưa lưu — bấm Lưu nháp trước khi in')
      return
    }
    try {
      // Tải lười: react-dom/server + mẫu in chỉ tải khi bấm In, không làm nặng lần mở trang đầu
      const [{ printK80 }, { SalesReceipt }, store] = await Promise.all([
        import('../../utils/print'),
        import('./print/SalesPrints'),
        storeInfoApi.get(),
      ])
      const printedAt = formatDateTime(new Date().toISOString())
      printK80(`Phiếu bán ${order.code}`, <SalesReceipt order={order} store={store} printedAt={printedAt} />)
      if (!store.configured) message.info('Chưa có thông tin cửa hàng — Quản lý hệ thống nhập ở menu tài khoản')
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không in được phiếu')
    }
  }
  // Phiếu hủy / đảo không còn giá trị giao hàng → không in
  const printable = order?.status === 'DRAFT' || order?.status === 'COMPLETED'

  const retryLoad = () => {
    setLoading(true)
    setLoadError(null)
    setReloadCount((count) => count + 1)
  }

  const handleError = (err: unknown) => {
    const fieldErrors = toFormFieldErrors(err)
    if (fieldErrors.length > 0 && (showForm || showPendingForm)) {
      activeForm.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
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

  /** BR-SAL-17, 18: lưu phần khách đặt (ghi mới hoặc sửa đơn Chờ xác nhận). Trả về đơn đã lưu, lỗi → null. */
  const savePending = async (): Promise<SalesOrder | null> => {
    let values: PendingOrderFormValues
    try {
      await pendingForm.validateFields()
      values = pendingForm.getFieldsValue(true) as PendingOrderFormValues
    } catch {
      return null
    }
    setBusy('save')
    setErrorMessage(null)
    try {
      const input = toPendingOrderInput(values)
      const saved = order ? await salesOrderApi.updatePending(order.id, input) : await salesOrderApi.createPending(input)
      setOrder(saved)
      onChanged()
      return saved
    } catch (err) {
      handleError(err)
      return null
    } finally {
      setBusy(null)
    }
  }

  const saveAndNotify = async () => {
    const saved = await savePending()
    if (!saved) return
    notification.success({
      title: order ? 'Đã lưu đơn khách đặt' : 'Đã ghi đơn khách đặt',
      description: `${saved.code} · ${saved.customerName} — chờ xác nhận, chưa ảnh hưởng tồn kho`,
    })
  }

  /**
   * Sang bước xác nhận: lưu thay đổi chưa lưu của phần khách đặt (nếu có), rồi điền form phiếu bán từ phần
   * khách đặt + bảng giá riêng. Chưa gọi API xác nhận — kho còn sửa SL / đổi SP / chọn kho.
   */
  const startConfirm = async () => {
    let current = order
    if (!current || pendingForm.isFieldsTouched()) {
      current = await savePending()
      if (!current) return
    }
    const prices = await customerPriceApi
      .list(current.customerId)
      .then((list) => new Map(list.map((price) => [price.productId, price.unitPrice])))
      .catch(() => new Map<number, number>())
    const activeWarehouses = references.warehouses.filter((warehouse) => warehouse.active)
    setErrorMessage(null)
    setConfirmValues(
      confirmationFormValues(current, prices, activeWarehouses.length === 1 ? activeWarehouses[0].id : undefined),
    )
  }

  /** BR-SAL-19: Chờ xác nhận → Nháp. Lỗi (thiếu giá, SP ngừng…) → vẫn ở bước xác nhận, sửa rồi bấm lại. */
  const submitConfirm = async () => {
    if (!order) return
    const values = await validValues()
    if (!values) return
    setBusy('confirm')
    setErrorMessage(null)
    try {
      const confirmed = await salesOrderApi.confirm(order.id, toSalesOrderInput(values))
      setConfirmValues(null)
      setOrder(confirmed)
      notification.success({
        title: 'Đã xác nhận đơn khách đặt',
        description: `${confirmed.code} · phiếu bán nháp — bấm Hoàn tất khi xuất hàng để trừ tồn ${confirmed.warehouseName}`,
      })
      onChanged()
    } catch (err) {
      handleError(err)
    } finally {
      setBusy(null)
    }
  }

  // BR-SAL-23..26: ném lỗi ra để FormModal giữ hộp mở và hiện lỗi (ví dụ người khác vừa thu tiền)
  const submitPriceAdjustment = async (input: PriceAdjustmentInput) => {
    if (!order) return
    const updated = await salesOrderApi.adjustPrices(order.id, input)
    setOrder(updated)
    setErrorMessage(null)
    notification.success({
      title: 'Đã điều chỉnh giá',
      description: `${updated.code} · tổng phiếu ${formatVnd(order.totalAmount)} → ${formatVnd(updated.totalAmount)}`,
    })
    onChanged()
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
      title = order.status === 'PENDING' ? 'Đã hủy đơn khách đặt' : 'Đã hủy phiếu bán'
      description = `${updated.code} · được giữ lại để tra cứu`
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
    if (!(showForm || showPendingForm) || !activeForm.isFieldsTouched()) {
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
      <span>
        {order.status === 'PENDING' || (order.status === 'CANCELLED' && !order.confirmedAt) ? 'Đơn khách đặt' : 'Phiếu bán'}{' '}
        {order.code}
      </span>
      <SalesStatusTag status={order.status} />
      {confirming && <span>· Xác nhận đơn</span>}
    </Flex>
  ) : orderId === null ? (
    createMode === 'pending' ? 'Ghi đơn khách đặt' : 'Tạo phiếu bán'
  ) : (
    'Phiếu bán'
  )

  const pendingFooter = (
    <Flex justify="space-between" gap={8} wrap>
      <Flex gap={8}>
        {order && (
          <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setReasonAction({ kind: 'cancel' })}>
            Hủy đơn
          </Button>
        )}
      </Flex>
      <Flex gap={8}>
        <Button onClick={requestClose} disabled={busy !== null}>
          Đóng
        </Button>
        <Button icon={<SaveOutlined />} loading={busy === 'save'} onClick={() => void saveAndNotify()}>
          {order ? 'Lưu đơn' : 'Ghi đơn'}
        </Button>
        <Button type="primary" icon={<CheckOutlined />} disabled={busy !== null} onClick={() => void startConfirm()}>
          {order ? 'Xác nhận đơn…' : 'Ghi & xác nhận…'}
        </Button>
      </Flex>
    </Flex>
  )

  const confirmFooter = (
    <Flex justify="flex-end" gap={8} wrap>
      <Button icon={<ArrowLeftOutlined />} disabled={busy !== null} onClick={() => setConfirmValues(null)}>
        Quay lại đơn
      </Button>
      <Button type="primary" icon={<CheckOutlined />} loading={busy === 'confirm'} onClick={() => void submitConfirm()}>
        Xác nhận đơn
      </Button>
    </Flex>
  )

  const draftFooter = (
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
        {printable && (
          <Button icon={<PrinterOutlined />} disabled={busy !== null} onClick={() => void print()}>
            In phiếu
          </Button>
        )}
        {/* BR-SAL-25: chỉ khi khách chưa trả đồng nào — backend kiểm lại sau khi khóa phiếu */}
        {canWrite && order?.status === 'COMPLETED' && order.paidAmount === 0 && (
          <Button icon={<EditOutlined />} onClick={() => setAdjustingPrice(true)}>
            Điều chỉnh giá
          </Button>
        )}
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

  const footer = loading || loadError ? null : confirming ? confirmFooter : showPendingForm ? pendingFooter : draftFooter

  const reasonTexts = (action: ReasonAction, current: SalesOrder) => {
    switch (action.kind) {
      case 'cancel':
        if (current.status === 'PENDING') {
          return {
            title: `Hủy đơn khách đặt ${current.code}?`,
            description:
              'Ghi rõ lý do (ví dụ: khách đổi ý, hết hàng và khách không lấy loại khác). Đơn được giữ lại để tra cứu, ' +
              'kể cả phần khách đặt, nhưng không sửa hay xác nhận được nữa.',
            submitText: 'Hủy đơn',
          }
        }
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
          {showPendingForm ? (
            <PendingOrderForm
              form={pendingForm}
              initialValues={pendingInitialValues}
              references={references}
              order={order}
              disabled={busy !== null}
            />
          ) : showForm ? (
            <Flex vertical gap={16}>
              {confirming && (
                <Alert
                  type="info"
                  showIcon
                  title="Chốt đơn với khách: chọn kho xuất, kiểm tra giá; được đổi số lượng hoặc đổi sang sản phẩm khác"
                  description="Xác nhận xong phiếu thành Nháp, chưa trừ tồn. Phần khách đặt bên dưới được giữ nguyên để đối chiếu."
                />
              )}
              {order && order.requestLines.length > 0 && <RequestLinesTable lines={order.requestLines} />}
              <SalesOrderForm
                form={form}
                initialValues={initialValues}
                references={references}
                order={order}
                disabled={busy !== null}
                lockCustomer={lockCustomer}
              />
            </Flex>
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
          // Hủy nháp: lý do tùy chọn; hủy đơn khách đặt: bắt buộc (BR-SAL-21)
          required={reasonAction.kind !== 'cancel' || order.status === 'PENDING'}
          {...reasonTexts(reasonAction, order)}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
      {order && adjustingPrice && (
        <PriceAdjustmentModal
          open
          order={order}
          onSubmit={submitPriceAdjustment}
          onClose={() => setAdjustingPrice(false)}
        />
      )}
      {order && paying && (
        <PaymentModal
          open
          direction="receive"
          document={{ code: order.code, counterpartyName: order.customerName, outstandingAmount: order.outstandingAmount }}
          onSubmit={submitPayment}
          onClose={() => setPaying(false)}
        />
      )}
    </Drawer>
  )
}
