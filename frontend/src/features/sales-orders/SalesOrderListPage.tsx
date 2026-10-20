import { PlusOutlined } from '@ant-design/icons'
import { Button, Typography } from 'antd'
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
import { EMPTY_TEXT, formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { RECEIPT_STATUS_LABELS } from '../inbound-receipts/inbound-receipt-api'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { salesOrderApi } from './sales-order-api'
import type { SalesOrderFilters, SalesOrderStatus, SalesOrderSummary } from './sales-order-api'
import { SalesOrderDrawer } from './SalesOrderDrawer'
import { useSalesReferences } from './use-sales-references'

const STATUS_OPTIONS = (Object.keys(RECEIPT_STATUS_LABELS) as SalesOrderStatus[]).map((status) => ({
  value: status,
  label: RECEIPT_STATUS_LABELS[status],
}))

/**
 * Danh sách phiếu bán (BR-SAL-01..15). Mọi thao tác — sửa nháp, hoàn tất, thu tiền, đảo — nằm trong SalesOrderDrawer.
 * Cột "Còn nợ" chỉ có nghĩa với phiếu Hoàn tất; phiếu nháp / đã hủy / đã đảo hiện "—".
 */
export function SalesOrderListPage() {
  const references = useSalesReferences()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(salesOrderApi.list)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  const [drawerKey, setDrawerKey] = useState(0)

  const openOrder = (id: number | null) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  const filterFields: FilterField<SalesOrderFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã phiếu' },
    { type: 'select', name: 'status', placeholder: 'Tất cả trạng thái', options: STATUS_OPTIONS, width: 160 },
    {
      type: 'select',
      name: 'unpaidOnly',
      placeholder: 'Thanh toán',
      options: [{ value: 'true', label: 'Còn nợ' }],
      width: 130,
    },
    {
      type: 'select',
      name: 'customerId',
      placeholder: 'Khách hàng',
      options: references.customers.map((customer) => ({ value: customer.id, label: `${customer.code} · ${customer.name}` })),
      width: 220,
    },
    {
      type: 'select',
      name: 'warehouseId',
      placeholder: 'Kho xuất',
      options: references.warehouses.map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} · ${warehouse.name}` })),
      width: 180,
    },
    { type: 'dateRange', from: 'documentDateFrom', to: 'documentDateTo', placeholder: ['Từ ngày bán', 'đến ngày'] },
  ]

  const columns: TableProps<SalesOrderSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, order) => <CodeLink code={code} onClick={() => openOrder(order.id)} />,
    },
    { title: 'Ngày bán', dataIndex: 'documentDate', width: 110, render: (date: string) => formatDate(date) },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      render: (name: string) => <Typography.Text strong>{name}</Typography.Text>,
    },
    { title: 'Kho xuất', dataIndex: 'warehouseName' },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 140, align: 'right', render: (amount: number) => formatVnd(amount) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 140,
      align: 'right',
      render: (amount: number, order) =>
        order.status !== 'COMPLETED' ? (
          EMPTY_TEXT
        ) : amount > 0 ? (
          <Typography.Text type="danger">{formatVnd(amount)}</Typography.Text>
        ) : (
          <Typography.Text type="success">Đã thu đủ</Typography.Text>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: STATUS_COLUMN_WIDTH,
      render: (status: SalesOrderStatus) => <ReceiptStatusTag status={status} />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, order) => (
        <RowActions
          recordLabel={order.code}
          onView={() => openOrder(order.id)}
          onEdit={order.status === 'DRAFT' ? () => openOrder(order.id) : undefined}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Phiếu bán hàng"
        description="Lập phiếu nháp, hoàn tất khi giao hàng để trừ tồn kho, thu tiền sau khi khách nhận hàng"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openOrder(null)}>
            Tạo phiếu bán
          </Button>
        }
      />
      <FilterBar<SalesOrderFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<SalesOrderSummary>
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
            noun="phiếu bán"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={() => openOrder(null)}
            createLabel="Tạo phiếu bán"
          />
        }
      />
      <SalesOrderDrawer
        key={drawerKey}
        open={drawerOpen}
        orderId={openedId}
        references={references}
        onClose={() => setDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
