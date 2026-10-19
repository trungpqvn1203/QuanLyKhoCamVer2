import { PlusOutlined, ShopOutlined } from '@ant-design/icons'
import { App, Button, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { DETAIL_INFO_TAB, DetailDrawer } from '../../components/ui/DetailDrawer'
import type { DetailSection } from '../../components/ui/DetailDrawer'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDateTime, orEmpty } from '../../utils/format'
import { ProductSuppliersPanel } from '../suppliers/ProductSuppliersPanel'
import { PRODUCT_UNIT_LABELS, categoryApi, productApi } from './product-api'
import type { Category, Product, ProductFilters } from './product-api'
import { ProductFormModal } from './ProductFormModal'

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/** Tab NCC trong khung chi tiết; menu ⋯ "Nhà cung cấp" mở thẳng tab này. */
const SUPPLIERS_TAB = 'suppliers'

export function ProductListPage() {
  const { message, notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    productApi.list,
  )
  const [categories, setCategories] = useState<Category[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Product | null>(null)
  const [detailTab, setDetailTab] = useState(DETAIL_INFO_TAB)

  useEffect(() => {
    categoryApi
      .listForSelect()
      .then((result) => setCategories(result.items))
      .catch((err: unknown) => message.error(err instanceof ApiError ? err.message : 'Không tải được danh mục'))
  }, [message])

  const filterFields: FilterField<ProductFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã hoặc tên sản phẩm' },
    {
      type: 'select',
      name: 'categoryId',
      placeholder: 'Tất cả danh mục',
      // Lọc được cả danh mục đã ngừng dùng (để tra sản phẩm cũ), đánh dấu rõ
      options: categories.map((category) => ({
        value: category.id,
        label: category.active ? category.name : `${category.name} (ngừng dùng)`,
      })),
    },
    { type: 'status', name: 'active', activeLabel: 'Đang kinh doanh', inactiveLabel: 'Ngừng kinh doanh' },
  ]

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (product: Product) => {
    setEditing(product)
    setModalOpen(true)
  }

  const openDetail = (product: Product, tab = DETAIL_INFO_TAB) => {
    setViewing(product)
    setDetailTab(tab)
    setDetailOpen(true)
  }

  // Thành công dùng notification (góc phải, có tiêu đề + mã + tên) để người dùng biết chắc bản ghi nào;
  // lỗi vặt vẫn dùng message cho gọn
  const notifySuccess = (title: string, product: Product) =>
    notification.success({ title, description: `${product.code} · ${product.name}` })

  /** Gọi API cho thao tác trên dòng → báo kết quả → tải lại để khớp backend (không cập nhật tại chỗ). */
  const runRowAction = async (product: Product, action: () => Promise<unknown>, done: string, failed: string) => {
    try {
      await action()
      notifySuccess(done, product)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
    }
  }

  const deactivate = (product: Product) =>
    runRowAction(
      product,
      () => productApi.deactivate(product.id),
      'Đã ngừng kinh doanh sản phẩm',
      'Không ngừng kinh doanh được sản phẩm',
    )

  // Mở bán lại = PUT đủ trường hiện có + active=true (BR-PRD-09: hướng dẫn mở bán lại bằng PUT)
  const reactivate = (product: Product) =>
    runRowAction(
      product,
      () =>
        productApi.update(product.id, {
          name: product.name,
          categoryId: product.categoryId,
          unit: product.unit,
          netWeightKg: product.netWeightKg,
          description: product.description,
          active: true,
        }),
      'Đã mở bán lại sản phẩm',
      'Không mở bán lại được sản phẩm',
    )

  const columns: TableProps<Product>['columns'] = [
    {
      title: 'Mã SP',
      dataIndex: 'code',
      width: 110,
      render: (code: string, product) => <CodeLink code={code} onClick={() => openDetail(product)} />,
    },
    { title: 'Tên sản phẩm', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
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
      width: STATUS_COLUMN_WIDTH,
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
          recordLabel={product.code}
          onView={() => openDetail(product)}
          onEdit={() => openEdit(product)}
          extraActions={[
            {
              key: 'suppliers',
              label: 'Nhà cung cấp',
              icon: <ShopOutlined />,
              onClick: () => openDetail(product, SUPPLIERS_TAB),
            },
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
          reactivate={product.active ? undefined : { label: 'Mở bán lại', onConfirm: () => reactivate(product) }}
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
            { key: 'code', label: 'Mã SP', children: <CodeText code={viewing.code} /> },
            { key: 'name', label: 'Tên sản phẩm', children: viewing.name },
            { key: 'category', label: 'Danh mục', children: viewing.categoryName },
            { key: 'unit', label: 'Đơn vị', children: PRODUCT_UNIT_LABELS[viewing.unit] },
            { key: 'weight', label: 'KL tịnh (kg)', children: weightFormat.format(viewing.netWeightKg) },
            { key: 'description', label: 'Mô tả', children: orEmpty(viewing.description) },
          ],
        },
        {
          key: 'status',
          title: 'Trạng thái',
          items: [
            {
              key: 'active',
              label: 'Trạng thái',
              children: (
                <StatusTag active={viewing.active} activeText="Đang kinh doanh" inactiveText="Ngừng kinh doanh" />
              ),
            },
          ],
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
        title="Sản phẩm"
        description="Quản lý danh mục và thông tin sản phẩm"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm sản phẩm
          </Button>
        }
      />
      <FilterBar<ProductFilters> fields={filterFields} value={filters} onChange={applyFilters} />
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
          <ListEmpty
            noun="sản phẩm"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={openCreate}
            createLabel="Thêm sản phẩm"
          />
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
        title={viewing ? `${viewing.code} · ${viewing.name}` : ''}
        size={640}
        sections={detailSections}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
        activeTab={detailTab}
        onTabChange={setDetailTab}
        tabs={
          viewing
            ? [
                {
                  key: SUPPLIERS_TAB,
                  label: 'Nhà cung cấp',
                  // key: sang SP khác thì tạo lại panel, không giữ ô tìm kiếm/trang của SP trước
                  children: <ProductSuppliersPanel key={viewing.id} product={viewing} />,
                },
              ]
            : []
        }
      />
    </>
  )
}
