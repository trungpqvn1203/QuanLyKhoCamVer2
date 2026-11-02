import { PhoneOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Flex, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
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
import { useCan } from '../auth/auth-context'
import { ORDER_CHANNEL_LABELS, SALES_STATUS_LABELS, salesOrderApi } from './sales-order-api'
import type { OrderChannel, SalesOrderFilters, SalesOrderStatus, SalesOrderSummary } from './sales-order-api'
import { SalesOrderDrawer } from './SalesOrderDrawer'
import { SalesStatusTag } from './SalesStatusTag'
import { useSalesReferences } from './use-sales-references'

const STATUS_OPTIONS = (Object.keys(SALES_STATUS_LABELS) as SalesOrderStatus[]).map((status) => ({
  value: status,
  label: SALES_STATUS_LABELS[status],
}))

const CHANNEL_OPTIONS = (Object.keys(ORDER_CHANNEL_LABELS) as OrderChannel[]).map((channel) => ({
  value: channel,
  label: ORDER_CHANNEL_LABELS[channel],
}))

/** Tổng quan dẫn sang bằng ?status=PENDING ("Đơn chờ xác nhận", BR-SAL-22). */
function filtersFromUrl(params: URLSearchParams): SalesOrderFilters {
  const status = params.get('status')
  return { status: status && status in SALES_STATUS_LABELS ? (status as SalesOrderStatus) : undefined }
}

/**
 * Danh sách phiếu bán (BR-SAL-01..22), gồm cả đơn khách đặt Chờ xác nhận. Mọi thao tác — ghi / xác nhận đơn,
 * sửa nháp, hoàn tất, thu tiền, đảo — nằm trong SalesOrderDrawer.
 * Cột "Còn nợ" chỉ có nghĩa với phiếu Hoàn tất; phiếu khác hiện "—". Đơn chờ xác nhận chưa có kho, chưa có tiền.
 */
export function SalesOrderListPage() {
  const references = useSalesReferences()
  const canWrite = useCan('sales.write')
  const [searchParams] = useSearchParams()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    salesOrderApi.list,
    20,
    filtersFromUrl(searchParams),
  )
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  const [createMode, setCreateMode] = useState<'sale' | 'pending'>('sale')
  const [drawerKey, setDrawerKey] = useState(0)

  const openOrder = (id: number | null, mode: 'sale' | 'pending' = 'sale') => {
    setOpenedId(id)
    setCreateMode(mode)
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  const filterFields: FilterField<SalesOrderFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã phiếu' },
    { type: 'select', name: 'status', placeholder: 'Tất cả trạng thái', options: STATUS_OPTIONS, width: 160 },
    { type: 'select', name: 'orderChannel', placeholder: 'Kênh đặt', options: CHANNEL_OPTIONS, width: 130 },
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
    { type: 'dateRange', from: 'deliveryDateFrom', to: 'deliveryDateTo', placeholder: ['Giao từ ngày', 'đến ngày'] },
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
      title: 'Ngày giao',
      dataIndex: 'requestedDeliveryDate',
      width: 110,
      render: (date?: string) => (date ? formatDate(date) : EMPTY_TEXT),
    },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      render: (name: string, order) => (
        <Flex gap={6} align="center" wrap>
          <Typography.Text strong>{name}</Typography.Text>
          {order.orderChannel !== 'DIRECT' && <Tag variant="outlined">{ORDER_CHANNEL_LABELS[order.orderChannel]}</Tag>}
        </Flex>
      ),
    },
    { title: 'Kho xuất', dataIndex: 'warehouseName', render: (name?: string) => name ?? EMPTY_TEXT },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right',
      // Đơn chờ xác nhận chưa có giá — hiện 0 đ dễ hiểu nhầm là hàng tặng
      render: (amount: number, order) => (order.status === 'PENDING' ? EMPTY_TEXT : formatVnd(amount)),
    },
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
      render: (status: SalesOrderStatus) => <SalesStatusTag status={status} />,
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
          onEdit={canWrite && (order.status === 'DRAFT' || order.status === 'PENDING') ? () => openOrder(order.id) : undefined}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Phiếu bán hàng"
        description="Ghi đơn khách đặt ngay khi nghe máy, xác nhận rồi hoàn tất khi giao hàng để trừ tồn kho, thu tiền sau khi khách nhận hàng"
        actions={
          canWrite && (
            <Flex gap={8}>
              <Button icon={<PhoneOutlined />} onClick={() => openOrder(null, 'pending')}>
                Ghi đơn khách đặt
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => openOrder(null)}>
                Tạo phiếu bán
              </Button>
            </Flex>
          )
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
            onCreate={canWrite ? () => openOrder(null) : undefined}
            createLabel="Tạo phiếu bán"
          />
        }
      />
      <SalesOrderDrawer
        key={drawerKey}
        open={drawerOpen}
        orderId={openedId}
        createMode={createMode}
        references={references}
        onClose={() => setDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
