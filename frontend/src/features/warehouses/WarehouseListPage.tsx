import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Typography, theme } from 'antd'
import type { DescriptionsProps, TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { DetailDrawer } from '../../components/ui/DetailDrawer'
import { RowActions } from '../../components/ui/RowActions'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { formatDateTime } from '../../utils/format'
import { WAREHOUSE_TYPE_LABELS, warehouseApi } from './warehouse-api'
import type { Warehouse } from './warehouse-api'
import { WarehouseFormModal } from './WarehouseFormModal'

export function WarehouseListPage() {
  const { message, notification } = App.useApp()
  const { token } = theme.useToken()
  const { page, loading, error, changePage, reload } = usePagedList(warehouseApi.list)
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

  const deactivate = async (warehouse: Warehouse) => {
    try {
      await warehouseApi.deactivate(warehouse.id)
      notifySuccess('Đã ngừng hoạt động kho', warehouse)
      reload()
    } catch (error) {
      message.error(error instanceof ApiError ? error.message : 'Không ngừng hoạt động được kho')
    }
  }

  const columns: TableProps<Warehouse>['columns'] = [
    {
      title: 'Mã kho',
      dataIndex: 'code',
      width: 110,
      render: (code: string, warehouse) => (
        <Typography.Link onClick={() => openDetail(warehouse)} style={{ fontFamily: token.fontFamilyCode }}>
          {code}
        </Typography.Link>
      ),
    },
    { title: 'Tên kho', dataIndex: 'name' },
    { title: 'Loại', dataIndex: 'type', render: (type: Warehouse['type']) => WAREHOUSE_TYPE_LABELS[type] },
    { title: 'Địa chỉ', dataIndex: 'address' },
    { title: 'Trạng thái', dataIndex: 'active', render: (active: boolean) => <StatusTag active={active} /> },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, warehouse) => (
        <RowActions
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
        />
      ),
    },
  ]

  const detailItems: DescriptionsProps['items'] = viewing
    ? [
        { key: 'code', label: 'Mã kho', children: <span style={{ fontFamily: token.fontFamilyCode }}>{viewing.code}</span> },
        { key: 'name', label: 'Tên kho', children: viewing.name },
        { key: 'type', label: 'Loại', children: WAREHOUSE_TYPE_LABELS[viewing.type] },
        { key: 'address', label: 'Địa chỉ', children: viewing.address },
        { key: 'status', label: 'Trạng thái', children: <StatusTag active={viewing.active} /> },
        { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(viewing.createdAt) },
        { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(viewing.updatedAt) },
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
      <DataTable<Warehouse>
        columns={columns}
        page={page}
        loading={loading}
        rowKey="id"
        onPageChange={changePage}
        error={error}
        onRetry={reload}
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
        title="Chi tiết kho"
        items={detailItems}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
      />
    </>
  )
}
