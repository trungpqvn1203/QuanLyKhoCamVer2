import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Empty, theme } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useState } from 'react'
import { ApiError } from '../../api/client'
import type { PageQuery } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { RowActions } from '../../components/ui/RowActions'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { PRODUCT_UNIT_LABELS, hasActiveFilters, productApi } from './product-api'
import type { Product, ProductFilters } from './product-api'
import { FILTER_BAR_HEIGHT, ProductFilterBar } from './ProductFilterBar'
import { ProductFormModal } from './ProductFormModal'

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

export function ProductListPage() {
  const { message } = App.useApp()
  const { token } = theme.useToken()
  const [filters, setFilters] = useState<ProductFilters>({})
  // useCallback: chỉ tạo hàm mới khi bộ lọc đổi → usePagedList tự tải lại đúng lúc
  const fetchPage = useCallback((query: PageQuery) => productApi.list(query, filters), [filters])
  const { page, query, loading, error, changePage, reload } = usePagedList(fetchPage)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const filtering = hasActiveFilters(filters)

  const applyFilters = useCallback(
    (next: ProductFilters) => {
      setFilters(next)
      changePage(0, query.size) // đổi điều kiện lọc → về trang đầu
    },
    [changePage, query.size],
  )

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
    {
      title: 'Mã SP',
      dataIndex: 'code',
      width: 110,
      // Font đơn cách: các mã SP000001, SP000012 thẳng cột, dễ dò
      render: (code: string) => <span style={{ fontFamily: token.fontFamilyCode }}>{code}</span>,
    },
    {
      title: 'Tên sản phẩm',
      dataIndex: 'name',
      render: (name: string) => <span style={{ fontWeight: token.fontWeightStrong }}>{name}</span>,
    },
    { title: 'Danh mục', dataIndex: 'categoryName' },
    {
      title: 'Đơn vị',
      dataIndex: 'unit',
      width: 90,
      render: (unit: Product['unit']) => PRODUCT_UNIT_LABELS[unit],
    },
    {
      title: 'KL tịnh (kg)',
      dataIndex: 'netWeightKg',
      align: 'right',
      width: 120,
      render: (kg: number) => <span style={{ fontVariantNumeric: 'tabular-nums' }}>{weightFormat.format(kg)}</span>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 150,
      render: (active: boolean) => (
        <StatusTag active={active} activeText="Đang kinh doanh" inactiveText="Ngừng kinh doanh" />
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, product) => (
        <RowActions
          onEdit={() => openEdit(product)}
          deactivate={
            product.active
              ? {
                  label: 'Ngừng kinh doanh',
                  confirmTitle: `Ngừng kinh doanh ${product.name}?`,
                  onConfirm: () => deactivate(product),
                }
              : undefined
          }
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Sản phẩm"
        description="Quản lý danh mục và thông tin sản phẩm"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm sản phẩm
          </Button>
        }
      />
      <ProductFilterBar value={filters} onChange={applyFilters} total={page?.pagination.totalElements} />
      <DataTable<Product>
        columns={columns}
        page={page}
        loading={loading}
        rowKey="id"
        onPageChange={changePage}
        error={error}
        onRetry={reload}
        extraReservedHeight={FILTER_BAR_HEIGHT}
        emptyText={
          filtering ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có sản phẩm phù hợp bộ lọc">
              <Button onClick={() => applyFilters({})}>Xóa bộ lọc</Button>
            </Empty>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có sản phẩm nào" />
          )
        }
      />
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
