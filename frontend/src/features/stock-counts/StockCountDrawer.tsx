import { CheckOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Alert, App, Button, Descriptions, Drawer, Flex, Form, Input, InputNumber, Select, Skeleton, Table, Typography } from 'antd'
import type { FormListFieldData, FormRule as Rule, TableProps } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { CodeText } from '../../components/ui/CodeLink'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { EMPTY_TEXT, formatDate, formatDateTime, formatQuantity, orEmpty, todayIso } from '../../utils/format'
import { PRODUCT_UNIT_LABELS } from '../products/product-api'
import type { Product } from '../products/product-api'
import { DifferenceText } from './DifferenceText'
import { draftDifference, stockCountApi } from './stock-count-api'
import type { StockCount, StockCountLine, StockCountReason } from './stock-count-api'
import { StockCountStatusTag } from './StockCountStatusTag'

interface StockCountDrawerProps {
  countId: number
  /** SP đang kinh doanh — để thêm dòng cho SP chưa có tồn */
  products: Product[]
  reasons: StockCountReason[]
  onClose: () => void
  onChanged: () => void
}

interface LineValues {
  productId?: number
  countedQuantity?: number | null
  reasonId?: number
  reasonNote?: string
}

interface CountFormValues {
  documentDate?: string
  note?: string
  lines?: LineValues[]
}

const NO_MARGIN = { marginBottom: 0 }

const integerRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value !== undefined && value !== null && !Number.isInteger(value)) throw new Error('Số nguyên')
  },
}

function toFormValues(count: StockCount): CountFormValues {
  return {
    documentDate: count.documentDate,
    note: count.note,
    lines: count.lines.map((line) => ({
      productId: line.productId,
      countedQuantity: line.countedQuantity,
      reasonId: line.reasonId,
      reasonNote: line.reasonNote,
    })),
  }
}

/**
 * Khung phiếu kiểm kê (BR-CNT-01..10). Đang kiểm: nhập SL đếm từng dòng, chênh lệch hiện ngay; dòng lệch chọn lý do;
 * thêm SP chưa có tồn, bỏ SP không kiểm; Lấy lại số sổ sách khi có cảnh báo phát sinh. Xác nhận ghi phần chênh vào tồn.
 */
export function StockCountDrawer({ countId, products, reasons, onClose, onChanged }: StockCountDrawerProps) {
  const { modal, notification } = App.useApp()
  const [form] = Form.useForm<CountFormValues>()
  const lines = Form.useWatch('lines', form) as LineValues[] | undefined
  const [count, setCount] = useState<StockCount | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busy, setBusy] = useState<'save' | 'complete' | 'refresh' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reasonAction, setReasonAction] = useState<'cancel' | 'reverse' | null>(null)

  useEffect(() => {
    let ignore = false
    stockCountApi
      .get(countId)
      .then((loaded) => {
        if (!ignore) setCount(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được phiếu kiểm kê')
      })
    return () => {
      ignore = true
    }
  }, [countId])

  const editable = count?.status === 'DRAFT'
  const initialValues = useMemo(() => (count ? toFormValues(count) : {}), [count])
  const formVersion = count ? `${count.id}:${count.updatedAt}` : 'loading'
  useEffect(() => {
    if (editable) form.resetFields()
  }, [formVersion, editable, form])

  const savedLines = useMemo(
    () => new Map((count?.lines ?? []).map((line) => [line.productId, line])),
    [count],
  )
  const moved = new Set(count?.movedSinceSnapshot ?? [])
  const productLabel = new Map<number, string>()
  for (const product of products) productLabel.set(product.id, `${product.code} · ${product.name}`)
  for (const line of count?.lines ?? []) productLabel.set(line.productId, `${line.productCode} · ${line.productName}`)
  const chosen = (lines ?? []).map((line) => line?.productId)
  const reasonOptions = reasons
    .filter((reason) => reason.active)
    .map((reason) => ({ value: reason.id, label: reason.name }))

  const handleError = (err: unknown) => {
    const fieldErrors = toFormFieldErrors(err)
    if (fieldErrors.length > 0 && editable) {
      form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      setErrorMessage('Có ô chưa hợp lệ — xem lỗi ngay dưới từng ô')
    } else {
      setErrorMessage(err instanceof ApiError ? err.message : 'Đã có lỗi xảy ra')
    }
  }

  const persist = async (): Promise<StockCount | null> => {
    try {
      await form.validateFields()
    } catch {
      return null
    }
    const values = form.getFieldsValue(true) as CountFormValues
    const saved = await stockCountApi.update(countId, {
      documentDate: values.documentDate,
      note: values.note?.trim() || undefined,
      lines: (values.lines ?? []).map((line) => ({
        productId: line.productId!,
        countedQuantity: line.countedQuantity ?? undefined,
        reasonId: line.reasonId,
        reasonNote: line.reasonNote?.trim() || undefined,
      })),
    })
    setCount(saved)
    onChanged()
    return saved
  }

  const save = async () => {
    setBusy('save')
    setErrorMessage(null)
    try {
      const saved = await persist()
      if (saved) notification.success({ title: 'Đã lưu phiếu kiểm kê', description: `${saved.code} · chưa đổi tồn kho` })
    } catch (err) {
      handleError(err)
    } finally {
      setBusy(null)
    }
  }

  const refreshBook = async () => {
    setBusy('refresh')
    setErrorMessage(null)
    try {
      // Lưu số đếm đang nhập trước, rồi mới chụp lại sổ sách — không mất số vừa gõ
      if (form.isFieldsTouched() && !(await persist())) return
      setCount(await stockCountApi.refreshBook(countId))
      notification.success({ title: 'Đã lấy lại số sổ sách theo tồn hiện tại' })
      onChanged()
    } catch (err) {
      handleError(err)
    } finally {
      setBusy(null)
    }
  }

  const complete = async () => {
    setErrorMessage(null)
    let current = count
    try {
      if (form.isFieldsTouched()) {
        setBusy('save')
        current = await persist()
      }
    } catch (err) {
      handleError(err)
      return
    } finally {
      setBusy(null)
    }
    if (!current) return
    modal.confirm({
      title: `Xác nhận kiểm kê ${current.code}?`,
      width: 520,
      content: (
        <Flex vertical gap={8}>
          <span>
            Tồn kho {current.warehouseName}: <Typography.Text type="success">thừa +{formatQuantity(current.surplusQuantity)}</Typography.Text>
            {', '}
            <Typography.Text type="danger">thiếu −{formatQuantity(current.shortageQuantity)}</Typography.Text> trên{' '}
            {current.differenceLineCount} dòng lệch. Sau khi xác nhận, phiếu bị khóa — sai thì chỉ có thể đảo cả phiếu.
          </span>
          {current.movedSinceSnapshot.length > 0 && (
            <Typography.Text type="warning">
              {current.movedSinceSnapshot.length} sản phẩm có nhập/xuất sau lúc chụp sổ sách. Nếu số đếm đã tính cả các giao
              dịch đó, hãy Xem lại và bấm "Lấy lại số sổ sách" trước.
            </Typography.Text>
          )}
        </Flex>
      ),
      okText: 'Xác nhận',
      cancelText: 'Xem lại',
      onOk: async () => {
        setBusy('complete')
        try {
          const completed = await stockCountApi.complete(current.id)
          setCount(completed)
          notification.success({ title: 'Đã xác nhận kiểm kê', description: `${completed.code} · tồn kho đã được điều chỉnh` })
          onChanged()
        } catch (err) {
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  const submitReason = async (reason: string | undefined) => {
    if (!count || !reasonAction) return
    const updated =
      reasonAction === 'cancel' ? await stockCountApi.cancel(count.id, reason) : await stockCountApi.reverse(count.id, reason ?? '')
    setCount(updated)
    setErrorMessage(null)
    notification.success({ title: reasonAction === 'cancel' ? 'Đã hủy phiếu kiểm kê' : 'Đã đảo phiếu kiểm kê' })
    onChanged()
  }

  const requestClose = () => {
    if (busy) return
    if (!editable || !form.isFieldsTouched()) {
      onClose()
      return
    }
    modal.confirm({
      title: 'Bỏ các số vừa nhập chưa lưu?',
      okText: 'Bỏ thay đổi',
      okButtonProps: { danger: true },
      cancelText: 'Tiếp tục nhập',
      onOk: onClose,
    })
  }

  const editColumns = (remove: (index: number) => void): TableProps<FormListFieldData>['columns'] => [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    {
      title: 'Sản phẩm',
      key: 'product',
      width: 280,
      render: (_, field) => {
        const productId = lines?.[field.name]?.productId
        const saved = productId === undefined ? undefined : savedLines.get(productId)
        if (saved) {
          return (
            <Flex vertical>
              <Typography.Text strong>{saved.productName}</Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                <CodeText code={saved.productCode} /> · {PRODUCT_UNIT_LABELS[saved.unit]}
                {moved.has(saved.productId) && <Typography.Text type="warning"> · có phát sinh sau lúc chụp</Typography.Text>}
              </Typography.Text>
            </Flex>
          )
        }
        return (
          <Form.Item name={[field.name, 'productId']} rules={[{ required: true, message: 'Chọn sản phẩm' }]} style={NO_MARGIN}>
            <Select<number>
              placeholder="Chọn sản phẩm"
              showSearch={{ optionFilterProp: 'label' }}
              aria-label={`Sản phẩm dòng ${field.name + 1}`}
              options={[...productLabel].map(([id, label]) => ({
                value: id,
                label,
                disabled: chosen.some((other, index) => other === id && index !== field.name),
              }))}
            />
          </Form.Item>
        )
      },
    },
    {
      title: 'Sổ sách',
      key: 'book',
      width: 90,
      align: 'right',
      render: (_, field) => {
        const productId = lines?.[field.name]?.productId
        const saved = productId === undefined ? undefined : savedLines.get(productId)
        return saved ? formatQuantity(saved.bookQuantity) : <Typography.Text type="secondary">khi lưu</Typography.Text>
      },
    },
    {
      title: 'Thực tế',
      key: 'counted',
      width: 120,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'countedQuantity']}
          rules={[{ type: 'number', min: 0, message: 'Không âm' }, integerRule]}
          style={NO_MARGIN}
        >
          <InputNumber min={0} placeholder="Chưa đếm" style={{ width: '100%' }} aria-label={`SL đếm dòng ${field.name + 1}`} />
        </Form.Item>
      ),
    },
    {
      title: 'Chênh',
      key: 'difference',
      width: 80,
      align: 'right',
      render: (_, field) => {
        const line = lines?.[field.name]
        const saved = line?.productId === undefined ? undefined : savedLines.get(line.productId)
        return saved ? <DifferenceText value={draftDifference(saved.bookQuantity, line?.countedQuantity)} /> : EMPTY_TEXT
      },
    },
    {
      title: 'Lý do lệch',
      key: 'reason',
      width: 190,
      render: (_, field) => (
        <Form.Item name={[field.name, 'reasonId']} style={NO_MARGIN}>
          <Select<number> allowClear placeholder="Chọn khi lệch" options={reasonOptions} aria-label={`Lý do dòng ${field.name + 1}`} />
        </Form.Item>
      ),
    },
    {
      title: 'Ghi chú',
      key: 'note',
      width: 170,
      render: (_, field) => (
        <Form.Item name={[field.name, 'reasonNote']} style={NO_MARGIN}>
          <Input maxLength={200} aria-label={`Ghi chú dòng ${field.name + 1}`} />
        </Form.Item>
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 44,
      render: (_, field) => (
        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} aria-label={`Bỏ dòng ${field.name + 1}`} />
      ),
    },
  ]

  const viewColumns: TableProps<StockCountLine>['columns'] = [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'Sổ sách', dataIndex: 'bookQuantity', width: 90, align: 'right', render: (v: number) => formatQuantity(v) },
    { title: 'Thực tế', dataIndex: 'countedQuantity', width: 90, align: 'right', render: (v?: number) => (v === undefined ? EMPTY_TEXT : formatQuantity(v)) },
    { title: 'Chênh', dataIndex: 'difference', width: 80, align: 'right', render: (v?: number) => <DifferenceText value={v} /> },
    {
      title: 'Lý do',
      key: 'reason',
      render: (_, line) => [line.reasonName, line.reasonNote].filter(Boolean).join(' · ') || EMPTY_TEXT,
    },
  ]

  const statusNotice = (current: StockCount) => {
    switch (current.status) {
      case 'COMPLETED':
        return (
          <Alert
            type="success"
            showIcon
            title={`Đã xác nhận lúc ${formatDateTime(current.completedAt!)} bởi ${orEmpty(current.completedBy)} — tồn kho đã điều chỉnh theo chênh lệch`}
          />
        )
      case 'REVERSED':
        return <Alert type="warning" showIcon title={`Đã đảo lúc ${formatDateTime(current.reversedAt!)}`} description={`Lý do: ${orEmpty(current.reason)}`} />
      case 'CANCELLED':
        return <Alert type="info" showIcon title={`Đã hủy lúc ${formatDateTime(current.cancelledAt!)} — phiếu chưa từng tác động tồn kho`} />
      default:
        return current.movedSinceSnapshot.length > 0 ? (
          <Alert
            type="warning"
            showIcon
            title={`${current.movedSinceSnapshot.length} sản phẩm có nhập/xuất sau lúc chụp sổ sách (${formatDateTime(current.bookSnapshotAt)})`}
            description="Nếu bạn đếm SAU các giao dịch đó, bấm Lấy lại số sổ sách để so đúng thời điểm. Nếu đếm trước, giữ nguyên — hệ thống chỉ ghi phần chênh."
            action={
              <Button size="small" icon={<ReloadOutlined />} loading={busy === 'refresh'} onClick={refreshBook}>
                Lấy lại số sổ sách
              </Button>
            }
          />
        ) : null
    }
  }

  const footer = count && (
    <Flex justify="space-between" gap={8} wrap>
      <Flex gap={8}>
        {editable && (
          <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setReasonAction('cancel')}>
            Hủy phiếu
          </Button>
        )}
        {count.status === 'COMPLETED' && (
          <Button danger icon={<RollbackOutlined />} onClick={() => setReasonAction('reverse')}>
            Đảo phiếu
          </Button>
        )}
      </Flex>
      <Flex gap={8}>
        <Button onClick={requestClose} disabled={busy !== null}>
          Đóng
        </Button>
        {editable && (
          <>
            <Button icon={<SaveOutlined />} loading={busy === 'save'} disabled={busy !== null && busy !== 'save'} onClick={save}>
              Lưu
            </Button>
            <Button type="primary" icon={<CheckOutlined />} loading={busy === 'complete'} disabled={busy !== null && busy !== 'complete'} onClick={complete}>
              Xác nhận
            </Button>
          </>
        )}
      </Flex>
    </Flex>
  )

  return (
    <Drawer
      open
      size={1120}
      title={
        count ? (
          <Flex align="center" gap={8}>
            <span>Kiểm kê {count.code}</span>
            <StockCountStatusTag status={count.status} />
          </Flex>
        ) : (
          'Kiểm kê'
        )
      }
      onClose={requestClose}
      footer={footer}
      destroyOnHidden
    >
      {loadError ? (
        <Alert type="error" showIcon title={loadError} />
      ) : !count ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <Flex vertical gap={16}>
          {errorMessage && <Alert type="error" showIcon closable title={errorMessage} onClose={() => setErrorMessage(null)} />}
          {statusNotice(count)}
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, md: 3 }}
            items={[
              { key: 'code', label: 'Mã phiếu', children: <CodeText code={count.code} /> },
              { key: 'warehouse', label: 'Kho', children: `${count.warehouseCode} · ${count.warehouseName}` },
              { key: 'snapshot', label: 'Chụp sổ sách lúc', children: formatDateTime(count.bookSnapshotAt) },
              { key: 'lines', label: 'Đã đếm', children: `${count.countedLineCount}/${count.lineCount} dòng` },
              {
                key: 'diff',
                label: 'Chênh lệch',
                children: (
                  <span>
                    <DifferenceText value={count.surplusQuantity} /> / <DifferenceText value={-count.shortageQuantity} /> (
                    {count.differenceLineCount} dòng lệch)
                  </span>
                ),
              },
              { key: 'createdBy', label: 'Người lập', children: `${orEmpty(count.createdBy)} · ${formatDate(count.documentDate)}` },
            ]}
          />
          {editable ? (
            <Form form={form} layout="vertical" initialValues={initialValues} disabled={busy !== null}>
              <Flex gap={16} wrap>
                <Form.Item
                  name="documentDate"
                  label="Ngày kiểm"
                  style={{ width: 200 }}
                  rules={[
                    { required: true, message: 'Chọn ngày' },
                    {
                      validator: async (_, value?: string) => {
                        if (value && value > todayIso()) throw new Error('Không được ở tương lai')
                      },
                    },
                  ]}
                >
                  <Input type="date" max={todayIso()} />
                </Form.Item>
                <Form.Item name="note" label="Ghi chú" style={{ flex: 1, minWidth: 260 }}>
                  <Input maxLength={500} placeholder="Ví dụ: người đếm, ca kiểm" />
                </Form.Item>
              </Flex>
              <Form.List name="lines">
                {(fields, { add, remove }) => (
                  <>
                    <Table<FormListFieldData>
                      size="small"
                      rowKey="key"
                      columns={editColumns(remove)}
                      dataSource={fields}
                      pagination={false}
                      scroll={{ x: 'max-content', y: 'calc(100vh - 460px)' }}
                    />
                    <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})} style={{ marginTop: 12 }}>
                      Thêm sản phẩm (chưa có trong danh sách)
                    </Button>
                  </>
                )}
              </Form.List>
            </Form>
          ) : (
            <Table<StockCountLine>
              size="small"
              rowKey="id"
              columns={viewColumns}
              dataSource={count.lines}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
          )}
        </Flex>
      )}

      {count && reasonAction && (
        <ReasonModal
          key={reasonAction}
          open
          required={reasonAction === 'reverse'}
          title={reasonAction === 'cancel' ? `Hủy phiếu ${count.code}?` : `Đảo phiếu ${count.code}?`}
          description={
            reasonAction === 'cancel'
              ? 'Phiếu đang kiểm chưa tác động tồn kho. Hủy xong có thể lập phiếu kiểm mới cho kho này.'
              : 'Mọi điều chỉnh của phiếu được ghi ngược lại: phần thừa bị trừ, phần thiếu được cộng lại. Nếu tồn không đủ để trừ phần thừa, hệ thống sẽ từ chối.'
          }
          submitText={reasonAction === 'cancel' ? 'Hủy phiếu' : 'Đảo phiếu'}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
    </Drawer>
  )
}
