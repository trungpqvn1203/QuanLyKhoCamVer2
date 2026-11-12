import { CheckOutlined, RollbackOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, Form, Skeleton } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { formatQuantity } from '../../utils/format'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import type { Warehouse } from '../warehouses/warehouse-api'
import { transferApi } from './transfer-api'
import type { Transfer } from './transfer-api'
import { draftTotalQuantity, emptyTransferForm, toTransferFormValues, toTransferInput } from './transfer-form'
import type { TransferFormValues } from './transfer-form'
import { TransferForm } from './TransferForm'
import { TransferView } from './TransferView'

interface TransferDrawerProps {
  open: boolean
  /** null = tạo phiếu mới */
  transferId: number | null
  warehouses: Warehouse[]
  onClose: () => void
  /** Phiếu vừa thay đổi ở backend → trang danh sách tải lại */
  onChanged: () => void
}

const DRAWER_WIDTH = 1000

/**
 * Khung phiếu điều chuyển — cùng cách làm với khung phiếu nhập (InboundReceiptDrawer):
 * - Tạo mới / Nháp: form; nút Lưu nháp, Hoàn tất; nháp đã lưu có thêm Hủy phiếu.
 * - Hoàn tất: chỉ xem; nút Đảo phiếu. Đã hủy / Đã đảo: chỉ xem.
 * Nút chỉ là gợi ý — backend khóa phiếu và kiểm tra lại trạng thái, lỗi (ví dụ thiếu tồn) hiện trong khung.
 */
export function TransferDrawer({ open, transferId, warehouses, onClose, onChanged }: TransferDrawerProps) {
  const { modal, notification } = App.useApp()
  const [form] = Form.useForm<TransferFormValues>()
  const [transfer, setTransfer] = useState<Transfer | null>(null)
  const [loading, setLoading] = useState(transferId !== null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [busy, setBusy] = useState<'save' | 'complete' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reasonAction, setReasonAction] = useState<'cancel' | 'reverse' | null>(null)

  useEffect(() => {
    if (transferId === null) return
    let ignore = false
    transferApi
      .get(transferId)
      .then((loaded) => {
        if (!ignore) setTransfer(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được phiếu điều chuyển')
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [transferId, reloadCount])

  const editable = transfer ? transfer.status === 'DRAFT' : transferId === null
  const showForm = !loading && !loadError && editable
  const initialValues = useMemo(() => (transfer ? toTransferFormValues(transfer) : emptyTransferForm()), [transfer])

  // Sau mỗi lần lưu, nạp lại form theo số backend trả về và xóa cờ "đã sửa"
  const formVersion = transfer ? `${transfer.id}:${transfer.updatedAt}` : 'new'
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

  const validValues = async (): Promise<TransferFormValues | null> => {
    try {
      await form.validateFields()
      return form.getFieldsValue(true) as TransferFormValues
    } catch {
      return null
    }
  }

  const persist = async (values: TransferFormValues): Promise<Transfer> => {
    const input = toTransferInput(values)
    const saved = transfer ? await transferApi.update(transfer.id, input) : await transferApi.create(input)
    setTransfer(saved)
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
        title: transfer ? 'Đã lưu phiếu nháp' : 'Đã tạo phiếu nháp',
        description: `${saved.code} · ${saved.sourceWarehouseName} → ${saved.targetWarehouseName} — chưa đổi tồn kho`,
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
    const nameOf = (id?: number) => warehouses.find((item) => item.id === id)?.name ?? ''
    modal.confirm({
      title: transfer ? `Hoàn tất điều chuyển ${transfer.code}?` : 'Lưu và hoàn tất điều chuyển?',
      content:
        `Chuyển ${lineCount} dòng, tổng ${formatQuantity(draftTotalQuantity(values.lines))} từ ${nameOf(values.sourceWarehouseId)} ` +
        `sang ${nameOf(values.targetWarehouseId)}. Tồn hai kho đổi cùng lúc; thiếu hàng ở một dòng thì cả phiếu không được thực hiện. ` +
        'Sau khi hoàn tất, phiếu bị khóa — sai thì chỉ có thể đảo cả phiếu.',
      okText: 'Hoàn tất',
      cancelText: 'Xem lại',
      onOk: async () => {
        setBusy('complete')
        setErrorMessage(null)
        try {
          let current = transfer
          if (!current || form.isFieldsTouched()) {
            current = await persist(values)
            onChanged()
          }
          const completed = await transferApi.complete(current.id)
          setTransfer(completed)
          notification.success({
            title: 'Đã hoàn tất điều chuyển',
            description: `${completed.code} · hàng đã vào ${completed.targetWarehouseName}`,
          })
          onChanged()
        } catch (err) {
          // Ví dụ: kho nguồn không đủ tồn — phiếu vẫn là Nháp, đã lưu nội dung mới nhất, tồn hai kho không đổi
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  // Ném lỗi ra để ReasonModal giữ hộp mở và hiện lỗi, ví dụ "Không đủ tồn" khi đảo
  const submitReason = async (reason: string | undefined) => {
    if (!transfer || !reasonAction) return
    const updated =
      reasonAction === 'cancel'
        ? await transferApi.cancel(transfer.id, reason)
        : await transferApi.reverse(transfer.id, reason ?? '')
    setTransfer(updated)
    setErrorMessage(null)
    notification.success(
      reasonAction === 'cancel'
        ? { title: 'Đã hủy phiếu', description: `${updated.code} · phiếu được giữ lại để tra cứu` }
        : { title: 'Đã đảo phiếu', description: `${updated.code} · hàng đã về lại ${updated.sourceWarehouseName}` },
    )
    onChanged()
  }

  const reasonTexts = (current: Transfer) =>
    reasonAction === 'cancel'
      ? {
          title: `Hủy phiếu ${current.code}?`,
          description: 'Phiếu nháp chưa tác động tồn kho. Sau khi hủy, phiếu được giữ lại để tra cứu nhưng không sửa hay hoàn tất được nữa.',
          submitText: 'Hủy phiếu',
        }
      : {
          title: `Đảo phiếu ${current.code}?`,
          description:
            `Hàng sẽ được đưa từ ${current.targetWarehouseName} về lại ${current.sourceWarehouseName}; lịch sử giữ cả giao dịch gốc và giao dịch đảo. ` +
            `Nếu ${current.targetWarehouseName} đã bán bớt và không còn đủ hàng, hệ thống sẽ từ chối.`,
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

  const title = transfer ? (
    <Flex align="center" gap={8}>
      <span>Điều chuyển {transfer.code}</span>
      <ReceiptStatusTag status={transfer.status} />
    </Flex>
  ) : transferId === null ? (
    'Lập phiếu điều chuyển'
  ) : (
    'Điều chuyển'
  )

  const footer =
    loading || loadError ? null : (
      <Flex justify="space-between" gap={8} wrap>
        <Flex gap={8}>
          {transfer?.status === 'DRAFT' && (
            <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setReasonAction('cancel')}>
              Hủy phiếu
            </Button>
          )}
          {transfer?.status === 'COMPLETED' && (
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
                {transfer ? 'Hoàn tất' : 'Lưu & hoàn tất'}
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
            <TransferForm form={form} initialValues={initialValues} warehouses={warehouses} transfer={transfer} disabled={busy !== null} />
          ) : (
            transfer && <TransferView transfer={transfer} />
          )}
        </>
      )}

      {transfer && reasonAction && (
        <ReasonModal
          key={reasonAction}
          open
          required={reasonAction === 'reverse'}
          {...reasonTexts(transfer)}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
    </Drawer>
  )
}
