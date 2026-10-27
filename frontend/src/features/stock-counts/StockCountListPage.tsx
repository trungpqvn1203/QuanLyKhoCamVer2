import { PlusOutlined, TagsOutlined } from '@ant-design/icons'
import { App, Button, Flex, Form, Input, Select, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { FormModal } from '../../components/ui/FormModal'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { STATUS_COLUMN_WIDTH } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDate, formatQuantity, orEmpty, todayIso } from '../../utils/format'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import { useOpeningReferences } from '../opening-stocks/use-opening-references'
import { DifferenceText } from './DifferenceText'
import { ReasonCatalogModal } from './ReasonCatalogModal'
import { STOCK_COUNT_STATUS_LABELS, stockCountApi, stockCountReasonApi } from './stock-count-api'
import type { StockCountFilters, StockCountReason, StockCountSummary } from './stock-count-api'
import { StockCountDrawer } from './StockCountDrawer'
import { StockCountStatusTag } from './StockCountStatusTag'

const STATUS_OPTIONS = (Object.keys(STOCK_COUNT_STATUS_LABELS) as ReceiptStatus[]).map((status) => ({
  value: status,
  label: STOCK_COUNT_STATUS_LABELS[status],
}))

interface CreateValues {
  warehouseId?: number
  documentDate?: string
  note?: string
}

/**
 * Kiểm kê kho (BR-CNT-01..10): so số đếm thực tế với sổ sách, ghi phần chênh kèm lý do.
 * Lập phiếu chỉ cần chọn kho — hệ thống nạp sẵn mọi SP đang có tồn; đếm và xác nhận trong khung phiếu.
 */
export function StockCountListPage() {
  const { notification } = App.useApp()
  const references = useOpeningReferences()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(stockCountApi.list)
  const [reasons, setReasons] = useState<StockCountReason[]>([])
  const [creating, setCreating] = useState(false)
  const [catalogOpen, setCatalogOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  const [createForm] = Form.useForm<CreateValues>()

  const loadReasons = useCallback(() => {
    stockCountReasonApi
      .list()
      .then(setReasons)
      .catch(() => {
        // giữ danh sách cũ; lỗi thật hiện khi lưu phiếu
      })
  }, [])

  useEffect(() => {
    loadReasons()
  }, [loadReasons])

  const filterFields: FilterField<StockCountFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã phiếu' },
    { type: 'select', name: 'status', placeholder: 'Tất cả trạng thái', options: STATUS_OPTIONS, width: 160 },
    {
      type: 'select',
      name: 'warehouseId',
      placeholder: 'Kho',
      options: references.warehouses.map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} · ${warehouse.name}` })),
      width: 220,
    },
    { type: 'dateRange', from: 'documentDateFrom', to: 'documentDateTo', placeholder: ['Từ ngày', 'đến ngày'] },
  ]

  const columns: TableProps<StockCountSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, count) => <CodeLink code={code} onClick={() => setOpenedId(count.id)} />,
    },
    { title: 'Ngày kiểm', dataIndex: 'documentDate', width: 110, render: (date: string) => formatDate(date) },
    {
      title: 'Kho',
      dataIndex: 'warehouseName',
      render: (name: string) => <Typography.Text strong>{name}</Typography.Text>,
    },
    {
      title: 'Đã đếm',
      key: 'counted',
      width: 100,
      align: 'right',
      render: (_, count) => `${formatQuantity(count.countedLineCount)}/${formatQuantity(count.lineCount)}`,
    },
    { title: 'Dòng lệch', dataIndex: 'differenceLineCount', width: 90, align: 'right' },
    {
      title: 'Thừa / thiếu',
      key: 'difference',
      width: 140,
      align: 'right',
      render: (_, count) => (
        <Flex gap={6} justify="flex-end">
          <DifferenceText value={count.surplusQuantity} />
          <span>/</span>
          <DifferenceText value={-count.shortageQuantity} />
        </Flex>
      ),
    },
    { title: 'Người lập', dataIndex: 'createdBy', width: 110, render: (by?: string) => orEmpty(by) },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: STATUS_COLUMN_WIDTH,
      render: (status: ReceiptStatus) => <StockCountStatusTag status={status} />,
    },
  ]

  const handleCreate = async (values: CreateValues) => {
    const created = await stockCountApi.create({
      warehouseId: values.warehouseId!,
      documentDate: values.documentDate,
      note: values.note?.trim() || undefined,
    })
    notification.success({
      title: `Đã lập phiếu ${created.code}`,
      description: `${created.lineCount} sản phẩm đang có tồn tại ${created.warehouseName} — nhập số đếm thực tế`,
    })
    reload()
    setOpenedId(created.id)
  }

  return (
    <>
      <PageHeader
        title="Kiểm kê kho"
        description="Đếm hàng thực tế, so với sổ sách và ghi phần chênh kèm lý do. Kho vẫn nhập / bán bình thường trong lúc kiểm"
        actions={
          <Flex gap={8}>
            <Button icon={<TagsOutlined />} onClick={() => setCatalogOpen(true)}>
              Lý do lệch
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
              Lập phiếu kiểm kê
            </Button>
          </Flex>
        }
      />
      <FilterBar<StockCountFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<StockCountSummary>
        columns={columns}
        page={page}
        loading={loading}
        rowKey="id"
        onPageChange={changePage}
        error={error}
        onRetry={reload}
        extraReservedHeight={FILTER_BAR_HEIGHT}
        emptyText={
          <ListEmpty
            noun="phiếu kiểm kê"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={() => setCreating(true)}
            createLabel="Lập phiếu kiểm kê"
          />
        }
      />

      {creating && (
        <FormModal<CreateValues>
          open
          title="Lập phiếu kiểm kê"
          form={createForm}
          initialValues={{ documentDate: todayIso() }}
          submitText="Lập phiếu"
          onSubmit={handleCreate}
          onClose={() => setCreating(false)}
        >
          <Typography.Paragraph type="secondary">
            Hệ thống nạp sẵn mọi sản phẩm đang có tồn tại kho và chụp số sổ sách ngay lúc này. Nên đếm ngay sau khi lập phiếu.
            Mỗi kho chỉ có một phiếu đang kiểm.
          </Typography.Paragraph>
          <Form.Item name="warehouseId" label="Kho" rules={[{ required: true, message: 'Chọn kho' }]}>
            <Select<number>
              placeholder="Chọn kho"
              showSearch={{ optionFilterProp: 'label' }}
              options={references.warehouses
                .filter((warehouse) => warehouse.active)
                .map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} · ${warehouse.name}` }))}
            />
          </Form.Item>
          <Form.Item name="documentDate" label="Ngày kiểm" rules={[{ required: true, message: 'Chọn ngày' }]}>
            <Input type="date" max={todayIso()} />
          </Form.Item>
          <Form.Item name="note" label="Ghi chú">
            <Input maxLength={500} placeholder="Ví dụ: kiểm định kỳ cuối tháng" />
          </Form.Item>
        </FormModal>
      )}
      {catalogOpen && <ReasonCatalogModal reasons={reasons} onChanged={loadReasons} onClose={() => setCatalogOpen(false)} />}
      {openedId !== null && (
        <StockCountDrawer
          key={openedId}
          countId={openedId}
          products={references.products}
          reasons={reasons}
          onClose={() => setOpenedId(null)}
          onChanged={reload}
        />
      )}
    </>
  )
}
