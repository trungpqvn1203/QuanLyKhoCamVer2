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
import { formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { RECEIPT_STATUS_LABELS, inboundReceiptApi } from './inbound-receipt-api'
import type { InboundReceiptFilters, InboundReceiptSummary, ReceiptStatus } from './inbound-receipt-api'
import { InboundReceiptDrawer } from './InboundReceiptDrawer'
import { ReceiptStatusTag } from './ReceiptStatusTag'
import { useReceiptReferences } from './use-receipt-references'

const STATUS_OPTIONS = (Object.keys(RECEIPT_STATUS_LABELS) as ReceiptStatus[]).map((status) => ({
  value: status,
  label: RECEIPT_STATUS_LABELS[status],
}))

/**
 * Danh sách phiếu nhập (BR-IMP-01..14). Mọi thao tác trên phiếu nằm trong một khung (InboundReceiptDrawer):
 * bấm mã phiếu hoặc menu ⋯ để mở; "Tạo phiếu nhập" mở khung trống.
 */
export function InboundReceiptListPage() {
  const references = useReceiptReferences()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    inboundReceiptApi.list,
  )
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  // Tăng mỗi lần mở → Drawer tạo mới hoàn toàn (state sạch), không lẫn phiếu xem lần trước
  const [drawerKey, setDrawerKey] = useState(0)

  const openReceipt = (id: number | null) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  // Bộ lọc NCC/kho gồm cả bản đã ngừng — vẫn cần tìm được phiếu cũ của họ
  const filterFields: FilterField<InboundReceiptFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã phiếu' },
    { type: 'select', name: 'status', placeholder: 'Tất cả trạng thái', options: STATUS_OPTIONS, width: 160 },
    {
      type: 'select',
      name: 'supplierId',
      placeholder: 'Nhà cung cấp',
      options: references.suppliers.map((supplier) => ({ value: supplier.id, label: `${supplier.code} · ${supplier.name}` })),
      width: 220,
    },
    {
      type: 'select',
      name: 'warehouseId',
      placeholder: 'Kho nhận',
      options: references.warehouses.map((warehouse) => ({
        value: warehouse.id,
        label: `${warehouse.code} · ${warehouse.name}`,
      })),
      width: 200,
    },
    {
      type: 'dateRange',
      from: 'documentDateFrom',
      to: 'documentDateTo',
      placeholder: ['Từ ngày chứng từ', 'đến ngày'],
    },
  ]

  const columns: TableProps<InboundReceiptSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, receipt) => <CodeLink code={code} onClick={() => openReceipt(receipt.id)} />,
    },
    { title: 'Ngày chứng từ', dataIndex: 'documentDate', width: 130, render: (date: string) => formatDate(date) },
    {
      title: 'Nhà cung cấp',
      dataIndex: 'supplierName',
      render: (name: string) => <Typography.Text strong>{name}</Typography.Text>,
    },
    { title: 'Kho nhận', dataIndex: 'warehouseName' },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      width: 160,
      align: 'right',
      render: (amount: number) => formatVnd(amount),
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
      render: (_, receipt) => (
        <RowActions
          recordLabel={receipt.code}
          onView={() => openReceipt(receipt.id)}
          // Chỉ phiếu Nháp sửa được (BR-IMP-11) — mở cùng khung, khung tự hiện form
          onEdit={receipt.status === 'DRAFT' ? () => openReceipt(receipt.id) : undefined}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Phiếu nhập kho"
        description="Nhập hàng từ nhà cung cấp: lưu nháp khi lên đơn, hoàn tất khi hàng về để cộng tồn kho"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openReceipt(null)}>
            Tạo phiếu nhập
          </Button>
        }
      />
      <FilterBar<InboundReceiptFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<InboundReceiptSummary>
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
            noun="phiếu nhập"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={() => openReceipt(null)}
            createLabel="Tạo phiếu nhập"
          />
        }
      />
      <InboundReceiptDrawer
        key={drawerKey}
        open={drawerOpen}
        receiptId={openedId}
        references={references}
        onClose={() => setDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
