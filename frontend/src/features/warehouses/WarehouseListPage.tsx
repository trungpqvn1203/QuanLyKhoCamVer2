import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Popconfirm, Space } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { WAREHOUSE_TYPE_LABELS, warehouseApi } from './warehouse-api'
import type { Warehouse } from './warehouse-api'
import { WarehouseFormModal } from './WarehouseFormModal'

export function WarehouseListPage() {
  const { message } = App.useApp()
  const { page, loading, changePage, reload } = usePagedList(warehouseApi.list)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Warehouse | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (warehouse: Warehouse) => {
    setEditing(warehouse)
    setModalOpen(true)
  }

  const deactivate = async (warehouse: Warehouse) => {
    try {
      await warehouseApi.deactivate(warehouse.id)
      message.success(`Đã ngừng hoạt động kho ${warehouse.code}`)
      reload()
    } catch (error) {
      message.error(error instanceof ApiError ? error.message : 'Không ngừng hoạt động được kho')
    }
  }

  const columns: TableProps<Warehouse>['columns'] = [
    { title: 'Mã kho', dataIndex: 'code', width: 110 },
    { title: 'Tên kho', dataIndex: 'name' },
    { title: 'Loại', dataIndex: 'type', render: (type: Warehouse['type']) => WAREHOUSE_TYPE_LABELS[type] },
    { title: 'Địa chỉ', dataIndex: 'address' },
    { title: 'Trạng thái', dataIndex: 'active', render: (active: boolean) => <StatusTag active={active} /> },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_, warehouse) => (
        <Space>
          <Button type="link" onClick={() => openEdit(warehouse)}>
            Sửa
          </Button>
          {warehouse.active && (
            <Popconfirm
              title={`Ngừng hoạt động kho ${warehouse.name}?`}
              description="Có thể mở lại bằng nút Sửa."
              okText="Ngừng hoạt động"
              cancelText="Hủy"
              onConfirm={() => deactivate(warehouse)}
            >
              <Button type="link" danger>
                Ngừng hoạt động
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

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
      <DataTable<Warehouse> columns={columns} page={page} loading={loading} rowKey="id" onPageChange={changePage} />
      <WarehouseFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={() => {
          message.success(editing ? 'Cập nhật kho thành công' : 'Tạo kho thành công')
          reload()
        }}
      />
    </>
  )
}
