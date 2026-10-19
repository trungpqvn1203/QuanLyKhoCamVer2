import { CheckOutlined, RollbackOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, Form, Skeleton } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { inboundReceiptApi } from './inbound-receipt-api'
import type { InboundReceipt } from './inbound-receipt-api'
import { InboundReceiptForm } from './InboundReceiptForm'
import { InboundReceiptView } from './InboundReceiptView'
import { ReasonModal } from './ReasonModal'
import { ReceiptStatusTag } from './ReceiptStatusTag'
import { emptyReceiptForm, toFormValues, toReceiptInput } from './receipt-form'
import type { ReceiptFormValues } from './receipt-form'
import type { ReceiptReferences } from './use-receipt-references'

interface InboundReceiptDrawerProps {
  open: boolean
  /** null = tạo phiếu mới */
  receiptId: number | null
  references: ReceiptReferences
  onClose: () => void
  /** Phiếu vừa thay đổi ở backend → trang danh sách tải lại */
  onChanged: () => void
}

/** Rộng đủ cho bảng dòng hàng 8 cột; màn hình hẹp hơn thì Drawer tự co, bảng cuộn ngang. */
const DRAWER_WIDTH = 1080

type ReasonAction = 'cancel' | 'reverse'

/**
 * Khung phiếu nhập — một phiếu một khung, từ lúc tạo đến lúc hoàn tất/đảo (ADR-0002: Drawer thay trang riêng).
 * Nội dung theo trạng thái (BR-IMP-11..13):
 * - Tạo mới / Nháp: form sửa được; nút Lưu nháp, Hoàn tất; phiếu nháp đã lưu có thêm Hủy phiếu.
 * - Hoàn tất: chỉ xem; nút Đảo phiếu.
 * - Đã hủy / Đã đảo: chỉ xem.
 * Nút chỉ là gợi ý cho người dùng — quyết định cuối cùng ở backend (khóa + kiểm tra lại trạng thái),
 * nên phiếu bị người khác đổi trạng thái trong lúc đang mở vẫn được chặn đúng, lỗi hiện trong khung.
 *
 * Trang danh sách đổi `key` mỗi lần mở → state luôn bắt đầu sạch, không lẫn phiếu đã xem trước đó.
 */
export function InboundReceiptDrawer({ open, receiptId, references, onClose, onChanged }: InboundReceiptDrawerProps) {
  const { modal, notification } = App.useApp()
  const [form] = Form.useForm<ReceiptFormValues>()
  const [receipt, setReceipt] = useState<InboundReceipt | null>(null)
  const [loading, setLoading] = useState(receiptId !== null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [busy, setBusy] = useState<'save' | 'complete' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null)

  useEffect(() => {
    if (receiptId === null) return
    let ignore = false
    inboundReceiptApi
      .get(receiptId)
      .then((loaded) => {
        if (!ignore) setReceipt(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được phiếu nhập')
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [receiptId, reloadCount])

  const editable = receipt ? receipt.status === 'DRAFT' : receiptId === null
  const showForm = !loading && !loadError && editable
  const initialValues = useMemo(() => (receipt ? toFormValues(receipt) : emptyReceiptForm()), [receipt])

  // Sau mỗi lần lưu, backend trả phiếu mới → nạp lại form theo số đã lưu và xóa cờ "đã sửa"
  const formVersion = receipt ? `${receipt.id}:${receipt.updatedAt}` : 'new'
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
      // Đường dẫn từ backend là mảng chung chung; Form đòi kiểu chính xác từng ô → ép kiểu như FormModal
      form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      setErrorMessage('Có ô chưa hợp lệ — xem lỗi ngay dưới từng ô')
    } else {
      setErrorMessage(err instanceof ApiError ? err.message : 'Đã có lỗi xảy ra')
    }
  }

  // Lỗi kiểm tra ở trình duyệt đã hiện dưới từng ô → không cần thêm thông báo chung
  const validValues = async (): Promise<ReceiptFormValues | null> => {
    try {
      await form.validateFields()
      return form.getFieldsValue(true) as ReceiptFormValues
    } catch {
      return null
    }
  }

  /** Tạo mới hoặc ghi đè phiếu nháp (PUT thay toàn bộ dòng) → giữ phiếu backend trả về. */
  const persist = async (values: ReceiptFormValues): Promise<InboundReceipt> => {
    const input = toReceiptInput(values)
    const saved = receipt ? await inboundReceiptApi.update(receipt.id, input) : await inboundReceiptApi.create(input)
    setReceipt(saved)
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
        title: receipt ? 'Đã lưu phiếu nhập nháp' : 'Đã tạo phiếu nhập nháp',
        description: `${saved.code} · ${saved.supplierName} — chưa cộng tồn kho`,
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
      title: receipt ? `Hoàn tất phiếu nhập ${receipt.code}?` : 'Lưu và hoàn tất phiếu nhập?',
      content: 'Tồn kho tại kho nhận sẽ tăng theo SL thực nhận. Sau khi hoàn tất, phiếu bị khóa, không sửa được.',
      okText: 'Hoàn tất',
      cancelText: 'Xem lại',
      // Trả Promise: nút xác nhận quay vòng đến khi xong. Lỗi được bắt bên trong để hộp xác nhận đóng
      // và lỗi hiện trong khung phiếu (người dùng sửa ngay tại đó)
      onOk: async () => {
        setBusy('complete')
        setErrorMessage(null)
        try {
          // Phiếu mới hoặc vừa sửa → lưu trước, để hoàn tất đúng nội dung đang nhìn thấy
          let current = receipt
          if (!current || form.isFieldsTouched()) {
            current = await persist(values)
            onChanged()
          }
          const completed = await inboundReceiptApi.complete(current.id)
          setReceipt(completed)
          notification.success({
            title: 'Đã hoàn tất phiếu nhập',
            description: `${completed.code} · tồn kho ${completed.warehouseName} đã được cộng`,
          })
          onChanged()
        } catch (err) {
          // Ví dụ: SP vừa bị gỡ khỏi NCC (BR-IMP-10) — phiếu vẫn là Nháp, đã lưu nội dung mới nhất
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  // Ném lỗi ra để ReasonModal (FormModal) giữ hộp mở và hiện lỗi, ví dụ "Không đủ tồn" khi đảo
  const submitReason = async (reason: string | undefined) => {
    if (!receipt || !reasonAction) return
    const updated =
      reasonAction === 'cancel'
        ? await inboundReceiptApi.cancel(receipt.id, reason)
        : await inboundReceiptApi.reverse(receipt.id, reason ?? '')
    setReceipt(updated)
    setErrorMessage(null)
    notification.success({
      title: reasonAction === 'cancel' ? 'Đã hủy phiếu nhập' : 'Đã đảo phiếu nhập',
      description:
        reasonAction === 'cancel'
          ? `${updated.code} · phiếu được giữ lại để tra cứu`
          : `${updated.code} · tồn kho ${updated.warehouseName} đã trừ lại`,
    })
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

  const title = receipt ? (
    <Flex align="center" gap={8}>
      <span>Phiếu nhập {receipt.code}</span>
      <ReceiptStatusTag status={receipt.status} />
    </Flex>
  ) : receiptId === null ? (
    'Tạo phiếu nhập'
  ) : (
    'Phiếu nhập'
  )

  const footer =
    loading || loadError ? null : (
      <Flex justify="space-between" gap={8} wrap>
        <Flex gap={8}>
          {receipt?.status === 'DRAFT' && (
            <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setReasonAction('cancel')}>
              Hủy phiếu
            </Button>
          )}
          {receipt?.status === 'COMPLETED' && (
            <Button danger icon={<RollbackOutlined />} onClick={() => setReasonAction('reverse')}>
              Đảo phiếu
            </Button>
          )}
        </Flex>
        <Flex gap={8}>
          <Button onClick={requestClose} disabled={busy !== null}>
            Đóng
          </Button>
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
                {receipt ? 'Hoàn tất' : 'Lưu & hoàn tất'}
              </Button>
            </>
          )}
        </Flex>
      </Flex>
    )

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
            <InboundReceiptForm
              form={form}
              initialValues={initialValues}
              references={references}
              receipt={receipt}
              disabled={busy !== null}
            />
          ) : (
            receipt && <InboundReceiptView receipt={receipt} />
          )}
        </>
      )}

      {receipt && (
        <ReasonModal
          // Đổi key theo thao tác → form lý do luôn trống khi mở lại
          key={reasonAction ?? 'closed'}
          open={reasonAction !== null}
          required={reasonAction === 'reverse'}
          title={reasonAction === 'reverse' ? `Đảo phiếu nhập ${receipt.code}?` : `Hủy phiếu nhập ${receipt.code}?`}
          description={
            reasonAction === 'reverse'
              ? `Tồn kho ${receipt.warehouseName} sẽ bị trừ lại đúng số đã nhập; lịch sử giữ cả giao dịch nhập và giao dịch đảo. ` +
                'Mỗi phiếu chỉ đảo được một lần. Nếu hàng đã xuất bớt và tồn không đủ, hệ thống sẽ từ chối.'
              : 'Phiếu nháp chưa tác động tồn kho. Sau khi hủy, phiếu được giữ lại để tra cứu nhưng không sửa hay hoàn tất được nữa.'
          }
          submitText={reasonAction === 'reverse' ? 'Đảo phiếu' : 'Hủy phiếu'}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
    </Drawer>
  )
}
