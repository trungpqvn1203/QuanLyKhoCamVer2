import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { DetailDrawer } from '../../components/ui/DetailDrawer'
import type { DetailSection } from '../../components/ui/DetailDrawer'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDateTime } from '../../utils/format'
import { WAREHOUSE_TYPE_LABELS, warehouseApi } from './warehouse-api'
import type { Warehouse, WarehouseFilters } from './warehouse-api'
import { WarehouseFormModal } from './WarehouseFormModal'

const FILTER_FIELDS: FilterField<WarehouseFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã hoặc tên kho' },
  { type: 'status', name: 'active', activeLabel: 'Đang hoạt động', inactiveLabel: 'Ngừng hoạt động' },
]

export function WarehouseListPage() {
  const { message, notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    warehouseApi.list,
  )
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Warehouse | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Warehouse | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (warehouse: Warehouse) => {
    setEditing(warehouse)
    setModalOpen(true)
  }

  const openDetail = (warehouse: Warehouse) => {
    setViewing(warehouse)
    setDetailOpen(true)
  }

  const notifySuccess = (title: string, warehouse: Warehouse) =>
    notification.success({ title, description: `${warehouse.code} · ${warehouse.name}` })

  /** Gọi API cho thao tác trên dòng → báo kết quả → tải lại để khớp backend (không cập nhật tại chỗ). */
  const runRowAction = async (warehouse: Warehouse, action: () => Promise<unknown>, done: string, failed: string) => {
    try {
      await action()
      notifySuccess(done, warehouse)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
    }
  }

  const deactivate = (warehouse: Warehouse) =>
    runRowAction(warehouse, () => warehouseApi.deactivate(warehouse.id), 'Đã ngừng hoạt động kho', 'Không ngừng hoạt động được kho')

  // Mở lại = PUT đủ trường hiện có + active=true (contract-spec: "PUT có active để mở lại")
  const reactivate = (warehouse: Warehouse) =>
    runRowAction(
      warehouse,
      () =>
        warehouseApi.update(warehouse.id, {
          name: warehouse.name,
          type: warehouse.type,
          address: warehouse.address,
          active: true,
        }),
      'Đã mở lại kho',
      'Không mở lại được kho',
    )

  const columns: TableProps<Warehouse>['columns'] = [
    {
      title: 'Mã kho',
      dataIndex: 'code',
      width: 110,
      render: (code: string, warehouse) => <CodeLink code={code} onClick={() => openDetail(warehouse)} />,
    },
    { title: 'Tên kho', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    {
      title: 'Loại',
      dataIndex: 'type',
      width: 150,
      render: (type: Warehouse['type']) => WAREHOUSE_TYPE_LABELS[type],
    },
    // Địa chỉ dài: cắt bằng "…", rê chuột xem đủ; xem nguyên văn trong khung chi tiết
    { title: 'Địa chỉ', dataIndex: 'address', ellipsis: { showTitle: true } },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, warehouse) => (
        <RowActions
          recordLabel={warehouse.code}
          onView={() => openDetail(warehouse)}
          onEdit={() => openEdit(warehouse)}
          deactivate={
            warehouse.active
              ? {
                  label: 'Ngừng hoạt động',
                  confirmTitle: `Ngừng hoạt động kho ${warehouse.name}?`,
                  onConfirm: () => deactivate(warehouse),
                }
              : undefined
          }
          reactivate={warehouse.active ? undefined : { label: 'Mở lại hoạt động', onConfirm: () => reactivate(warehouse) }}
        />
      ),
    },
  ]

  const detailSections: DetailSection[] = viewing
    ? [
        {
          key: 'general',
          title: 'Thông tin chung',
          items: [
            { key: 'code', label: 'Mã kho', children: <CodeText code={viewing.code} /> },
            { key: 'name', label: 'Tên kho', children: viewing.name },
            { key: 'type', label: 'Loại', children: WAREHOUSE_TYPE_LABELS[viewing.type] },
            { key: 'address', label: 'Địa chỉ', children: viewing.address },
          ],
        },
        {
          key: 'status',
          title: 'Trạng thái',
          items: [{ key: 'active', label: 'Trạng thái', children: <StatusTag active={viewing.active} /> }],
        },
        {
          key: 'system',
          title: 'Thông tin hệ thống',
          items: [
            { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(viewing.createdAt) },
            { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(viewing.updatedAt) },
          ],
        },
      ]
    : []

  return (
    <>
      <PageHeader
        title="Kho hàng"
        description="Kho tập kết và cửa hàng bán lẻ trong chuỗi phân phối"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm kho
          </Button>
        }
      />
      <FilterBar<WarehouseFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<Warehouse>
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
            noun="kho"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={openCreate}
            createLabel="Thêm kho"
          />
        }
      />
      <WarehouseFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          notifySuccess(editing ? 'Đã cập nhật kho' : 'Đã tạo kho', saved)
          reload()
        }}
      />
      <DetailDrawer
        open={detailOpen}
        title={viewing ? `${viewing.code} · ${viewing.name}` : ''}
        sections={detailSections}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
      />
    </>
  )
}
