import { ArrowRightOutlined, PlusOutlined } from '@ant-design/icons'
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
import { transferApi } from './transfer-api'
import type { TransferFilters, TransferSummary } from './transfer-api'
import { TransferDrawer } from './TransferDrawer'
import { useTransferWarehouses } from './use-transfer-warehouses'

const STATUS_OPTIONS = (Object.keys(RECEIPT_STATUS_LABELS) as ReceiptStatus[]).map((status) => ({
  value: status,
  label: RECEIPT_STATUS_LABELS[status],
}))

/**
 * Danh sách phiếu điều chuyển kho (BR-TRF-01..12): dời hàng giữa các kho trong hệ thống.
 * Mọi thao tác trên phiếu nằm trong một khung (TransferDrawer).
 */
export function TransferListPage() {
  const warehouses = useTransferWarehouses()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(transferApi.list)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  // Tăng mỗi lần mở → Drawer tạo mới hoàn toàn (state sạch)
  const [drawerKey, setDrawerKey] = useState(0)

  const openTransfer = (id: number | null) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  const warehouseOptions = warehouses.map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} · ${warehouse.name}` }))
  const filterFields: FilterField<TransferFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã phiếu' },
    { type: 'select', name: 'status', placeholder: 'Tất cả trạng thái', options: STATUS_OPTIONS, width: 160 },
    { type: 'select', name: 'sourceWarehouseId', placeholder: 'Kho nguồn', options: warehouseOptions, width: 200 },
    { type: 'select', name: 'targetWarehouseId', placeholder: 'Kho đích', options: warehouseOptions, width: 200 },
    { type: 'dateRange', from: 'documentDateFrom', to: 'documentDateTo', placeholder: ['Từ ngày', 'đến ngày'] },
  ]

  const columns: TableProps<TransferSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, transfer) => <CodeLink code={code} onClick={() => openTransfer(transfer.id)} />,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: (date: string) => formatDate(date) },
    {
      title: 'Kho nguồn → Kho đích',
      key: 'route',
      render: (_, transfer) => (
        <Flex align="center" gap={8} wrap>
          <Typography.Text strong>{transfer.sourceWarehouseName}</Typography.Text>
          <ArrowRightOutlined aria-label="chuyển đến" />
          <Typography.Text strong>{transfer.targetWarehouseName}</Typography.Text>
        </Flex>
      ),
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
      title: 'Hoàn tất',
      key: 'completed',
      width: 160,
      render: (_, transfer) =>
        transfer.completedAt ? (
          <Flex vertical>
            <span>{orEmpty(transfer.completedBy)}</span>
            <Typography.Text type="secondary">{formatDateTime(transfer.completedAt)}</Typography.Text>
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
      render: (_, transfer) => (
        <RowActions
          recordLabel={transfer.code}
          onView={() => openTransfer(transfer.id)}
          onEdit={transfer.status === 'DRAFT' ? () => openTransfer(transfer.id) : undefined}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Điều chuyển kho"
        description="Dời hàng từ kho này sang kho khác. Khi hoàn tất, kho nguồn giảm và kho đích tăng cùng lúc — thiếu hàng ở một dòng thì cả phiếu không được thực hiện"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openTransfer(null)}>
            Lập phiếu điều chuyển
          </Button>
        }
      />
      <FilterBar<TransferFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<TransferSummary>
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
            noun="phiếu điều chuyển"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={() => openTransfer(null)}
            createLabel="Lập phiếu điều chuyển"
          />
        }
      />
      <TransferDrawer
        key={drawerKey}
        open={drawerOpen}
        transferId={openedId}
        warehouses={warehouses}
        onClose={() => setDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
