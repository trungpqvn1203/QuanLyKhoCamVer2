import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Popconfirm, Space } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { PRODUCT_UNIT_LABELS, productApi } from './product-api'
import type { Product } from './product-api'
import { ProductFormModal } from './ProductFormModal'

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

export function ProductListPage() {
  const { message } = App.useApp()
  const { page, loading, changePage, reload } = usePagedList(productApi.list)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (product: Product) => {
    setEditing(product)
    setModalOpen(true)
  }

  const deactivate = async (product: Product) => {
    try {
      await productApi.deactivate(product.id)
      message.success(`Đã ngừng kinh doanh ${product.code}`)
      reload()
    } catch (error) {
      message.error(error instanceof ApiError ? error.message : 'Không ngừng kinh doanh được sản phẩm')
    }
  }

  const columns: TableProps<Product>['columns'] = [
    { title: 'Mã SP', dataIndex: 'code', width: 110 },
    { title: 'Tên sản phẩm', dataIndex: 'name' },
    { title: 'Danh mục', dataIndex: 'categoryName' },
    { title: 'Đơn vị', dataIndex: 'unit', render: (unit: Product['unit']) => PRODUCT_UNIT_LABELS[unit] },
    {
      title: 'KL tịnh (kg)',
      dataIndex: 'netWeightKg',
      align: 'right',
      render: (kg: number) => weightFormat.format(kg),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      render: (active: boolean) => (
        <StatusTag active={active} activeText="Đang kinh doanh" inactiveText="Ngừng kinh doanh" />
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_, product) => (
        <Space>
          <Button type="link" onClick={() => openEdit(product)}>
            Sửa
          </Button>
          {product.active && (
            <Popconfirm
              title={`Ngừng kinh doanh ${product.name}?`}
              description="Có thể mở bán lại bằng nút Sửa."
              okText="Ngừng kinh doanh"
              cancelText="Hủy"
              onConfirm={() => deactivate(product)}
            >
              <Button type="link" danger>
                Ngừng kinh doanh
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
        title="Sản phẩm"
        description="Danh sách sản phẩm cám đang quản lý"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm sản phẩm
          </Button>
        }
      />
      <DataTable<Product> columns={columns} page={page} loading={loading} rowKey="id" onPageChange={changePage} />
      <ProductFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={() => {
          message.success(editing ? 'Cập nhật sản phẩm thành công' : 'Tạo sản phẩm thành công')
          reload()
        }}
      />
    </>
  )
}
