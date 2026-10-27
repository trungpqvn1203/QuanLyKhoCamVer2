import { PlusOutlined } from '@ant-design/icons'
import { Button, Flex, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { EMPTY_TEXT, formatDate, formatDateTime, formatQuantity, orEmpty } from '../../utils/format'
import { RECEIPT_STATUS_LABELS } from '../inbound-receipts/inbound-receipt-api'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { openingStockApi } from './opening-stock-api'
import type { OpeningStockFilters, OpeningStockSummary } from './opening-stock-api'
import { OpeningStockDrawer } from './OpeningStockDrawer'
import { useOpeningReferences } from './use-opening-references'

const STATUS_OPTIONS = (Object.keys(RECEIPT_STATUS_LABELS) as ReceiptStatus[]).map((status) => ({
  value: status,
  label: RECEIPT_STATUS_LABELS[status],
}))

/**
 * Danh sách phiếu tồn đầu kỳ (BR-OPN-01..10): khai hàng có sẵn trong kho khi bắt đầu quản lý trên phần mềm.
 * Mọi thao tác trên phiếu nằm trong một khung (OpeningStockDrawer).
 */
export function OpeningStockListPage() {
  const references = useOpeningReferences()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(openingStockApi.list)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  // Tăng mỗi lần mở → Drawer tạo mới hoàn toàn (state sạch)
  const [drawerKey, setDrawerKey] = useState(0)

  const openDocument = (id: number | null) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  const filterFields: FilterField<OpeningStockFilters>[] = [
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

  const columns: TableProps<OpeningStockSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, document) => <CodeLink code={code} onClick={() => openDocument(document.id)} />,
    },
    { title: 'Ngày đầu kỳ', dataIndex: 'documentDate', width: 120, render: (date: string) => formatDate(date) },
    {
      title: 'Kho',
      dataIndex: 'warehouseName',
      render: (name: string) => <Typography.Text strong>{name}</Typography.Text>,
    },
    { title: 'Số dòng', dataIndex: 'lineCount', width: 90, align: 'right' },
    {
      title: 'Tổng SL',
      dataIndex: 'totalQuantity',
      width: 110,
      align: 'right',
      render: (quantity: number) => formatQuantity(quantity),
    },
    { title: 'Người lập', dataIndex: 'createdBy', width: 120, render: (by?: string) => orEmpty(by) },
    {
      title: 'Xác nhận',
      key: 'completed',
      width: 160,
      render: (_, document) =>
        document.completedAt ? (
          <Flex vertical>
            <span>{orEmpty(document.completedBy)}</span>
            <Typography.Text type="secondary">{formatDateTime(document.completedAt)}</Typography.Text>
          </Flex>
        ) : (
          EMPTY_TEXT
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: STATUS_COLUMN_WIDTH,
      render: (status: ReceiptStatus) => <ReceiptStatusTag status={status} />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, document) => (
        <RowActions
          recordLabel={document.code}
          onView={() => openDocument(document.id)}
          onEdit={document.status === 'DRAFT' ? () => openDocument(document.id) : undefined}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Tồn kho đầu kỳ"
        description="Khai số hàng đang có sẵn trong kho khi bắt đầu quản lý trên phần mềm. Mỗi sản phẩm tại mỗi kho chỉ khai một lần, khi chưa có nhập/xuất"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openDocument(null)}>
            Lập phiếu tồn đầu kỳ
          </Button>
        }
      />
      <FilterBar<OpeningStockFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<OpeningStockSummary>
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
            noun="phiếu tồn đầu kỳ"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={() => openDocument(null)}
            createLabel="Lập phiếu tồn đầu kỳ"
          />
        }
      />
      <OpeningStockDrawer
        key={drawerKey}
        open={drawerOpen}
        documentId={openedId}
        references={references}
        onClose={() => setDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
