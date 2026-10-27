import { CheckOutlined, RollbackOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, Form, Skeleton } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { formatQuantity } from '../../utils/format'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { openingStockApi } from './opening-stock-api'
import type { OpeningStock } from './opening-stock-api'
import { draftTotalQuantity, emptyOpeningStockForm, toOpeningFormValues, toOpeningInput } from './opening-stock-form'
import type { OpeningStockFormValues } from './opening-stock-form'
import { OpeningStockForm } from './OpeningStockForm'
import { OpeningStockView } from './OpeningStockView'
import type { OpeningReferences } from './use-opening-references'

interface OpeningStockDrawerProps {
  open: boolean
  /** null = tạo phiếu mới */
  documentId: number | null
  references: OpeningReferences
  onClose: () => void
  /** Phiếu vừa thay đổi ở backend → trang danh sách tải lại */
  onChanged: () => void
}

const DRAWER_WIDTH = 1000

/**
 * Khung phiếu tồn đầu kỳ — cùng cách làm với khung phiếu nhập (InboundReceiptDrawer):
 * - Tạo mới / Nháp: form; nút Lưu nháp, Xác nhận; nháp đã lưu có thêm Hủy phiếu.
 * - Hoàn tất: chỉ xem; nút Đảo phiếu. Đã hủy / Đã đảo: chỉ xem.
 * Nút chỉ là gợi ý — backend khóa phiếu và kiểm tra lại trạng thái, lỗi hiện trong khung.
 */
export function OpeningStockDrawer({ open, documentId, references, onClose, onChanged }: OpeningStockDrawerProps) {
  const { modal, notification } = App.useApp()
  const [form] = Form.useForm<OpeningStockFormValues>()
  const [document, setDocument] = useState<OpeningStock | null>(null)
  const [loading, setLoading] = useState(documentId !== null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [busy, setBusy] = useState<'save' | 'complete' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reasonAction, setReasonAction] = useState<'cancel' | 'reverse' | null>(null)

  useEffect(() => {
    if (documentId === null) return
    let ignore = false
    openingStockApi
      .get(documentId)
      .then((loaded) => {
        if (!ignore) setDocument(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được phiếu tồn đầu kỳ')
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [documentId, reloadCount])

  const editable = document ? document.status === 'DRAFT' : documentId === null
  const showForm = !loading && !loadError && editable
  const initialValues = useMemo(() => (document ? toOpeningFormValues(document) : emptyOpeningStockForm()), [document])

  // Sau mỗi lần lưu, nạp lại form theo số backend trả về và xóa cờ "đã sửa"
  const formVersion = document ? `${document.id}:${document.updatedAt}` : 'new'
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

  const validValues = async (): Promise<OpeningStockFormValues | null> => {
    try {
      await form.validateFields()
      return form.getFieldsValue(true) as OpeningStockFormValues
    } catch {
      return null
    }
  }

  const persist = async (values: OpeningStockFormValues): Promise<OpeningStock> => {
    const input = toOpeningInput(values)
    const saved = document ? await openingStockApi.update(document.id, input) : await openingStockApi.create(input)
    setDocument(saved)
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
        title: document ? 'Đã lưu phiếu nháp' : 'Đã tạo phiếu nháp',
        description: `${saved.code} · ${saved.warehouseName} — chưa cộng tồn kho`,
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
    const lineCount = values.lines?.length ?? 0
    const warehouse = references.warehouses.find((item) => item.id === values.warehouseId)
    modal.confirm({
      title: document ? `Xác nhận tồn đầu kỳ ${document.code}?` : 'Lưu và xác nhận tồn đầu kỳ?',
      content:
        `Tồn kho ${warehouse?.name ?? ''} sẽ tăng ${lineCount} dòng, tổng ${formatQuantity(draftTotalQuantity(values.lines))}. ` +
        'Sau khi xác nhận, phiếu bị khóa, không sửa được — sai thì chỉ có thể đảo cả phiếu.',
      okText: 'Xác nhận',
      cancelText: 'Xem lại',
      onOk: async () => {
        setBusy('complete')
        setErrorMessage(null)
        try {
          let current = document
          if (!current || form.isFieldsTouched()) {
            current = await persist(values)
            onChanged()
          }
          const completed = await openingStockApi.complete(current.id)
          setDocument(completed)
          notification.success({
            title: 'Đã xác nhận tồn đầu kỳ',
            description: `${completed.code} · tồn kho ${completed.warehouseName} đã được cộng`,
          })
          onChanged()
        } catch (err) {
          // Ví dụ: SP vừa phát sinh nhập/xuất (BR-OPN-05) — phiếu vẫn là Nháp, đã lưu nội dung mới nhất
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  // Ném lỗi ra để ReasonModal giữ hộp mở và hiện lỗi, ví dụ "Không đủ tồn" khi đảo
  const submitReason = async (reason: string | undefined) => {
    if (!document || !reasonAction) return
    const updated =
      reasonAction === 'cancel'
        ? await openingStockApi.cancel(document.id, reason)
        : await openingStockApi.reverse(document.id, reason ?? '')
    setDocument(updated)
    setErrorMessage(null)
    notification.success(
      reasonAction === 'cancel'
        ? { title: 'Đã hủy phiếu', description: `${updated.code} · phiếu được giữ lại để tra cứu` }
        : { title: 'Đã đảo phiếu', description: `${updated.code} · tồn kho ${updated.warehouseName} đã trừ lại` },
    )
    onChanged()
  }

  const reasonTexts = (current: OpeningStock) =>
    reasonAction === 'cancel'
      ? {
          title: `Hủy phiếu ${current.code}?`,
          description: 'Phiếu nháp chưa tác động tồn kho. Sau khi hủy, phiếu được giữ lại để tra cứu nhưng không sửa hay xác nhận được nữa.',
          submitText: 'Hủy phiếu',
        }
      : {
          title: `Đảo phiếu ${current.code}?`,
          description:
            `Tồn kho ${current.warehouseName} sẽ bị trừ lại đúng số đã khai; lịch sử giữ cả giao dịch đầu kỳ và giao dịch đảo. ` +
            'Nếu hàng đã bán bớt và tồn không đủ, hệ thống sẽ từ chối. Đảo xong có thể lập phiếu đầu kỳ đúng cho các sản phẩm này.',
          submitText: 'Đảo phiếu',
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

  const title = document ? (
    <Flex align="center" gap={8}>
      <span>Tồn đầu kỳ {document.code}</span>
      <ReceiptStatusTag status={document.status} />
    </Flex>
  ) : documentId === null ? (
    'Lập phiếu tồn đầu kỳ'
  ) : (
    'Tồn đầu kỳ'
  )

  const footer =
    loading || loadError ? null : (
      <Flex justify="space-between" gap={8} wrap>
        <Flex gap={8}>
          {document?.status === 'DRAFT' && (
            <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setReasonAction('cancel')}>
              Hủy phiếu
            </Button>
          )}
          {document?.status === 'COMPLETED' && (
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
              <Button type="primary" icon={<CheckOutlined />} loading={busy === 'complete'} disabled={busy === 'save'} onClick={complete}>
                {document ? 'Xác nhận' : 'Lưu & xác nhận'}
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
            <OpeningStockForm
              form={form}
              initialValues={initialValues}
              warehouses={references.warehouses}
              products={references.products}
              document={document}
              disabled={busy !== null}
            />
          ) : (
            document && <OpeningStockView document={document} />
          )}
        </>
      )}

      {document && reasonAction && (
        <ReasonModal
          key={reasonAction}
          open
          required={reasonAction === 'reverse'}
          {...reasonTexts(document)}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
    </Drawer>
  )
}
