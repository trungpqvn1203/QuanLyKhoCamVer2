import { PlusOutlined, ShopOutlined } from '@ant-design/icons'
import { App, Button, Drawer, Empty, Typography, theme } from 'antd'
import type { DescriptionsProps, TableProps } from 'antd'
import { useCallback, useState } from 'react'
import { ApiError } from '../../api/client'
import type { PageQuery } from '../../api/types'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { DetailDrawer } from '../../components/ui/DetailDrawer'
import { RowActions } from '../../components/ui/RowActions'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { formatDateTime } from '../../utils/format'
import { ProductSuppliersPanel } from '../suppliers/ProductSuppliersPanel'
import { PRODUCT_UNIT_LABELS, hasActiveFilters, productApi } from './product-api'
import type { Product, ProductFilters } from './product-api'
import { FILTER_BAR_HEIGHT, ProductFilterBar } from './ProductFilterBar'
import { ProductFormModal } from './ProductFormModal'

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

export function ProductListPage() {
  const { message, notification } = App.useApp()
  const { token } = theme.useToken()
  const [filters, setFilters] = useState<ProductFilters>({})
  // useCallback: chỉ tạo hàm mới khi bộ lọc đổi → usePagedList tự tải lại đúng lúc
  const fetchPage = useCallback((query: PageQuery) => productApi.list(query, filters), [filters])
  const { page, query, loading, error, changePage, reload } = usePagedList(fetchPage)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Product | null>(null)
  // Khung riêng chỉ để gán/gỡ NCC, mở từ menu ⋯
  const [linksOpen, setLinksOpen] = useState(false)
  const [linking, setLinking] = useState<Product | null>(null)

  const openLinks = (product: Product) => {
    setLinking(product)
    setLinksOpen(true)
  }
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

  const openDetail = (product: Product) => {
    setViewing(product)
    setDetailOpen(true)
  }

  // Thành công dùng notification (góc phải, có tiêu đề + mã + tên) để người dùng biết chắc bản ghi nào;
  // lỗi vặt vẫn dùng message cho gọn
  const notifySuccess = (title: string, product: Product) =>
    notification.success({ title, description: `${product.code} · ${product.name}` })

  const deactivate = async (product: Product) => {
    try {
      await productApi.deactivate(product.id)
      notifySuccess('Đã ngừng kinh doanh sản phẩm', product)
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
      // Font đơn cách: các mã SP000001, SP000012 thẳng cột, dễ dò. Bấm mã → xem chi tiết
      render: (code: string, product) => (
        <Typography.Link onClick={() => openDetail(product)} style={{ fontFamily: token.fontFamilyCode }}>
          {code}
        </Typography.Link>
      ),
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
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, product) => (
        <RowActions
          onView={() => openDetail(product)}
          onEdit={() => openEdit(product)}
          extraActions={[
            { key: 'suppliers', label: 'Nhà cung cấp', icon: <ShopOutlined />, onClick: () => openLinks(product) },
          ]}
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

  const detailItems: DescriptionsProps['items'] = viewing
    ? [
        { key: 'code', label: 'Mã SP', children: <span style={{ fontFamily: token.fontFamilyCode }}>{viewing.code}</span> },
        { key: 'name', label: 'Tên sản phẩm', children: viewing.name },
        { key: 'category', label: 'Danh mục', children: viewing.categoryName },
        { key: 'unit', label: 'Đơn vị', children: PRODUCT_UNIT_LABELS[viewing.unit] },
        { key: 'weight', label: 'KL tịnh (kg)', children: weightFormat.format(viewing.netWeightKg) },
        { key: 'description', label: 'Mô tả', children: viewing.description ?? '—' },
        {
          key: 'status',
          label: 'Trạng thái',
          children: <StatusTag active={viewing.active} activeText="Đang kinh doanh" inactiveText="Ngừng kinh doanh" />,
        },
        { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(viewing.createdAt) },
        { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(viewing.updatedAt) },
      ]
    : []

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
        onSaved={(saved) => {
          notifySuccess(editing ? 'Đã cập nhật sản phẩm' : 'Đã tạo sản phẩm', saved)
          reload()
        }}
      />
      <DetailDrawer
        open={detailOpen}
        title="Chi tiết sản phẩm"
        size={640}
        items={detailItems}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
      >
        {viewing && <ProductSuppliersPanel key={viewing.id} product={viewing} title="Nhà cung cấp" />}
      </DetailDrawer>
      <Drawer
        open={linksOpen}
        title={linking ? `Nhà cung cấp · ${linking.code} ${linking.name}` : ''}
        size={640}
        onClose={() => setLinksOpen(false)}
        destroyOnHidden
      >
        {linking && <ProductSuppliersPanel key={linking.id} product={linking} />}
      </Drawer>
    </>
  )
}
