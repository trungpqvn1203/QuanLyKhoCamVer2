import { DatabaseOutlined, PlusOutlined, ShopOutlined } from '@ant-design/icons'
import { App, Button, Flex, Typography } from 'antd'
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
import { EMPTY_TEXT, formatDateTime, formatQuantity, formatTons, orEmpty } from '../../utils/format'
import { ProductSuppliersPanel } from '../suppliers/ProductSuppliersPanel'
import { warehouseApi } from '../warehouses/warehouse-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { PRODUCT_UNIT_LABELS, categoryApi, productApi } from './product-api'
import type { Category, Product } from './product-api'
import { STOCK_STATUS_LABELS, isByWarehouse, productStockApi } from './product-stock-api'
import type { ProductStock, ProductStockFilters, StockStatus } from './product-stock-api'
import { ProductFormModal } from './ProductFormModal'
import { ProductStockPanel } from './ProductStockPanel'
import { StockBreakdownPopover } from './StockBreakdownPopover'
import { StockStatusTag } from './StockStatusTag'

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/** Tab trong khung chi tiết; menu ⋯ mở thẳng tab tương ứng. */
const STOCK_TAB = 'stock'
const SUPPLIERS_TAB = 'suppliers'

const STOCK_STATUS_OPTIONS = (Object.keys(STOCK_STATUS_LABELS) as StockStatus[]).map((status) => ({
  value: status,
  label: STOCK_STATUS_LABELS[status],
}))

/** "Bao" → "bao" để ghép sau số: "120 bao". */
const unitText = (product: Pick<Product, 'unit'>) => PRODUCT_UNIT_LABELS[product.unit].toLowerCase()
/**
 * Sản phẩm kèm tồn kho (BR-STK-01..06). Bảng lấy từ GET /api/inventory/products (đủ trường SP + số tồn);
 * thêm/sửa/ngừng vẫn qua /api/products. Tồn chỉ để xem — chỉ đổi được qua chứng từ kho.
 */
export function ProductListPage() {
  const { message, notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    productStockApi.list,
  )
  const [categories, setCategories] = useState<Category[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<ProductStock | null>(null)
  const [detailTab, setDetailTab] = useState(DETAIL_INFO_TAB)

  useEffect(() => {
    categoryApi
      .listForSelect()
      .then((result) => setCategories(result.items))
      .catch((err: unknown) => message.error(err instanceof ApiError ? err.message : 'Không tải được danh mục'))
    // Gồm cả kho đã ngừng: hàng ở đó vẫn tính vào tồn (BR-STK-01)
    warehouseApi
      .list({ page: 0, size: 100 })
      .then((result) => setWarehouses(result.items))
      .catch(() => setWarehouses([]))
  }, [message])

  const selectedWarehouse =
    filters.warehouseId == null ? undefined : warehouses.find((warehouse) => warehouse.id === filters.warehouseId)

  const filterFields: FilterField<ProductStockFilters>[] = [
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
    { type: 'select', name: 'stockStatus', placeholder: 'Tình trạng tồn', options: STOCK_STATUS_OPTIONS, width: 150 },
    {
      type: 'select',
      name: 'warehouseId',
      // BR-STK-05: chọn kho = cột Tồn kho hiện số tại kho đó; không bớt SP khỏi danh sách
      placeholder: 'Xem tồn tại kho…',
      options: warehouses.map((warehouse) => ({
        value: warehouse.id,
        label: `${warehouse.code} · ${warehouse.name}${warehouse.active ? '' : ' (ngừng)'}`,
      })),
      width: 200,
    },
  ]

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (product: Product) => {
    setEditing(product)
    setModalOpen(true)
  }

  const openDetail = (product: ProductStock, tab = DETAIL_INFO_TAB) => {
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
          // PUT = cập nhật toàn bộ: thiếu trường này thì mức tồn tối thiểu bị xóa
          minStockQuantity: product.minStockQuantity,
          active: true,
        }),
      'Đã mở bán lại sản phẩm',
      'Không mở bán lại được sản phẩm',
    )

  const columns: TableProps<ProductStock>['columns'] = [
    {
      title: 'Mã SP',
      dataIndex: 'code',
      width: 110,
      render: (code: string, product) => <CodeLink code={code} onClick={() => openDetail(product)} />,
    },
    { title: 'Tên sản phẩm', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    { title: 'Danh mục', dataIndex: 'categoryName' },
    {
      title: 'KL tịnh (kg)',
      dataIndex: 'netWeightKg',
      align: 'right',
      width: 110,
      render: (kg: number) => <span style={{ fontVariantNumeric: 'tabular-nums' }}>{weightFormat.format(kg)}</span>,
    },
    {
      // Tiêu đề ghi rõ kho đang xem để số "0" không bị hiểu là hết hàng toàn hệ thống
      title: selectedWarehouse ? `Tồn tại ${selectedWarehouse.code}` : 'Tồn kho',
      key: 'stock',
      align: 'right',
      width: 150,
      // Ô tự đọc dữ liệu của dòng (không đọc bộ lọc — bộ lọc có thể vừa đổi mà dữ liệu chưa tải xong)
      render: (_, product) => {
        const byWarehouse = isByWarehouse(product)
        const quantity = byWarehouse ? (product.warehouseStockQuantity ?? 0) : product.stockQuantity
        const tons = byWarehouse ? (product.warehouseStockTons ?? 0) : product.stockTons
        return (
          <Flex vertical align="flex-end">
            {/* key theo số tồn: tồn đổi sau khi tải lại → popover tạo lại, không hiện số cũ đã lưu */}
            <StockBreakdownPopover key={`${product.id}-${product.stockQuantity}-${product.incomingQuantity}`} productId={product.id}>
              <Button
                type="link"
                size="small"
                aria-label={`Tồn theo kho của ${product.code}`}
                style={{ padding: 0, height: 'auto', fontVariantNumeric: 'tabular-nums' }}
              >
                {formatQuantity(quantity)} {unitText(product)}
              </Button>
            </StockBreakdownPopover>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {byWarehouse ? `Tổng mọi kho: ${formatQuantity(product.stockQuantity)}` : formatTons(tons)}
            </Typography.Text>
          </Flex>
        )
      },
    },
    {
      title: 'Đang về',
      key: 'incoming',
      align: 'right',
      width: 100,
      render: (_, product) => {
        const incoming = isByWarehouse(product) ? (product.warehouseIncomingQuantity ?? 0) : product.incomingQuantity
        return incoming > 0 ? (
          <Typography.Text type="secondary">+{formatQuantity(incoming)}</Typography.Text>
        ) : (
          EMPTY_TEXT
        )
      },
    },
    {
      title: 'Tình trạng tồn',
      dataIndex: 'stockStatus',
      width: 130,
      render: (status: StockStatus) => <StockStatusTag status={status} />,
    },
    {
      title: 'Kinh doanh',
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
              key: 'stock',
              label: 'Tồn theo kho',
              icon: <DatabaseOutlined />,
              onClick: () => openDetail(product, STOCK_TAB),
            },
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
            {
              key: 'minStock',
              label: 'Tồn tối thiểu',
              children:
                viewing.minStockQuantity == null
                  ? 'Không đặt'
                  : `${formatQuantity(viewing.minStockQuantity)} ${unitText(viewing)}`,
            },
            { key: 'description', label: 'Mô tả', children: orEmpty(viewing.description) },
          ],
        },
        {
          key: 'status',
          title: 'Trạng thái',
          items: [
            {
              key: 'active',
              label: 'Kinh doanh',
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
        description="Thông tin sản phẩm và số lượng đang có ở các kho"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm sản phẩm
          </Button>
        }
      />
      <FilterBar<ProductStockFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<ProductStock>
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
        size={720}
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
                  key: STOCK_TAB,
                  label: 'Tồn kho',
                  // key: sang SP khác thì tạo lại panel và tải tồn của SP đó
                  children: <ProductStockPanel key={viewing.id} product={viewing} />,
                },
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
