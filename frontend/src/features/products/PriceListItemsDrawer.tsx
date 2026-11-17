import { CheckOutlined, DeleteOutlined } from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, InputNumber, Popconfirm, Tag, Tooltip, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import type { PageQuery } from '../../api/types'
import { CodeText } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { EMPTY_TEXT, formatDateTime } from '../../utils/format'
import { formatPriceInput, formatVnd, hasAtMostTwoDecimals, parsePriceInput } from '../../utils/money'
import { useCan } from '../auth/auth-context'
import { priceListApi } from './price-list-api'
import type { PriceList, PriceListItem } from './price-list-api'
import { categoryApi, productApi } from './product-api'
import type { Category, Product, ProductFilters } from './product-api'

interface PriceListItemsDrawerProps {
  open: boolean
  priceList: PriceList | null
  onClose: () => void
  onChanged: () => void
}

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/**
 * Khung chi tiết của MỘT Bảng giá bán:
 * - Chiều rộng mở rộng (88vw, tối đa 1360px) và thu gọn cột để hiển thị trọn vẹn trên một màn hình mà KHÔNG phải kéo ngang.
 * - Tự động ưu tiên sắp xếp những sản phẩm ĐÃ CÓ GIÁ trong bảng giá này lên đầu danh sách (`prioritizePriceListId`).
 */
export function PriceListItemsDrawer({ open, priceList, onClose, onChanged }: PriceListItemsDrawerProps) {
  const { message, notification } = App.useApp()
  const canWrite = useCan('sales.write')
  const priceListId = priceList?.id

  const listProductsForPriceList = useCallback(
    (query: PageQuery, currentFilters: ProductFilters) =>
      productApi.list(query, { ...currentFilters, prioritizePriceListId: priceListId }),
    [priceListId],
  )

  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } =
    useFilteredList<Product, ProductFilters>(listProductsForPriceList, 20, { active: true })
  const [categories, setCategories] = useState<Category[]>([])
  // Map productId → PriceListItem của bảng giá đang mở
  const [itemsMap, setItemsMap] = useState<Record<number, PriceListItem>>({})
  const [loadingItems, setLoadingItems] = useState(false)
  const [itemsError, setItemsError] = useState<string | null>(null)
  // Giá đang gõ trên từng dòng (productId → giá trị trong ô nhập)
  const [drafts, setDrafts] = useState<Record<number, number | null | undefined>>({})
  const [savingId, setSavingId] = useState<number | null>(null)

  const loadItems = useCallback(() => {
    if (!open || priceListId === undefined) return
    setLoadingItems(true)
    setItemsError(null)
    priceListApi
      .listItems(priceListId)
      .then((items) => {
        const next: Record<number, PriceListItem> = {}
        for (const item of items) {
          next[item.productId] = item
        }
        setItemsMap(next)
      })
      .catch((err: unknown) => {
        setItemsError(err instanceof ApiError ? err.message : 'Không tải được đơn giá của bảng giá này')
      })
      .finally(() => setLoadingItems(false))
  }, [open, priceListId])

  useEffect(() => {
    if (!open) return
    setDrafts({})
    loadItems()
    reload()
    categoryApi
      .listForSelect()
      .then((result) => setCategories(result.items))
      .catch(() => setCategories([]))
  }, [open, priceListId, loadItems, reload])

  const filterFields: FilterField<ProductFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm sản phẩm theo mã hoặc tên' },
    {
      type: 'select',
      name: 'categoryId',
      placeholder: 'Tất cả danh mục',
      options: categories.map((category) => ({
        value: category.id,
        label: category.active ? category.name : `${category.name} (ngừng dùng)`,
      })),
    },
    { type: 'status', name: 'active', activeLabel: 'Đang kinh doanh', inactiveLabel: 'Ngừng kinh doanh' },
  ]

  const savedValue = (product: Product): number | undefined => itemsMap[product.id]?.unitPrice

  const currentValue = (product: Product): number | null | undefined =>
    product.id in drafts ? drafts[product.id] : savedValue(product)

  const isDirty = (product: Product): boolean => {
    if (!(product.id in drafts)) return false
    const draft = drafts[product.id] ?? undefined
    return draft !== savedValue(product)
  }

  const clearDraft = (productId: number) => {
    setDrafts((prev) => {
      const copy = { ...prev }
      delete copy[productId]
      return copy
    })
  }

  const savePrice = async (product: Product, nextPrice: number | null) => {
    if (priceListId === undefined || !priceList) return
    if (nextPrice !== null) {
      if (nextPrice < 0) {
        message.error('Đơn giá không được âm')
        return
      }
      if (!hasAtMostTwoDecimals(nextPrice)) {
        message.error('Đơn giá tối đa 2 chữ số lẻ')
        return
      }
    }
    setSavingId(product.id)
    try {
      if (nextPrice === null) {
        await priceListApi.removeItemPrice(priceListId, product.id)
        clearDraft(product.id)
        setItemsMap((prev) => {
          const copy = { ...prev }
          delete copy[product.id]
          return copy
        })
        notification.success({
          title: 'Đã bỏ đơn giá khỏi bảng giá',
          description: `${product.code} · ${product.name}`,
        })
      } else {
        const saved = await priceListApi.setItemPrice(priceListId, product.id, nextPrice)
        clearDraft(product.id)
        setItemsMap((prev) => ({ ...prev, [product.id]: saved }))
        notification.success({
          title: `Đã lưu giá (${priceList.name})`,
          description: `${product.code} · ${product.name}: ${formatVnd(saved.unitPrice)} / ${product.unitName.toLowerCase()}`,
        })
      }
      reload()
      onChanged()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không lưu được đơn giá')
    } finally {
      setSavingId(null)
    }
  }

  const columns: TableProps<Product>['columns'] = [
    {
      title: 'Mã SP',
      dataIndex: 'code',
      width: 96,
      render: (code: string) => <CodeText code={code} />,
    },
    {
      title: 'Tên sản phẩm',
      dataIndex: 'name',
      ellipsis: { showTitle: true },
      render: (name: string) => <Typography.Text strong>{name}</Typography.Text>,
    },
    { title: 'Danh mục', dataIndex: 'categoryName', width: 120, ellipsis: true },
    {
      title: 'ĐVT · KL',
      key: 'unit',
      width: 105,
      render: (_, product) => `${product.unitName} (${weightFormat.format(product.netWeightKg)} kg)`,
    },
    {
      title: (
        <Tooltip title="Đơn giá bán mặc định đặt trên sản phẩm — dùng để đối chiếu">
          Giá chung SP
        </Tooltip>
      ),
      dataIndex: 'salePrice',
      width: 120,
      align: 'right',
      render: (price?: number) =>
        price == null ? (
          <Typography.Text type="secondary">{EMPTY_TEXT}</Typography.Text>
        ) : (
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatVnd(price)}</span>
        ),
    },
    {
      title: priceList ? `Đơn giá "${priceList.name}"` : 'Đơn giá bảng giá',
      key: 'unitPrice',
      width: 145,
      align: 'right',
      render: (_, product) => {
        const price = savedValue(product)
        return price == null ? (
          <Tag style={{ marginInlineEnd: 0 }}>Chưa đặt giá</Tag>
        ) : (
          <Typography.Text strong style={{ fontVariantNumeric: 'tabular-nums', color: '#1677ff' }}>
            {formatVnd(price)}
          </Typography.Text>
        )
      },
    },
    ...(canWrite
      ? ([
          {
            title: 'Đặt / sửa đơn giá (đ)',
            key: 'editPrice',
            width: 230,
            render: (_: unknown, product: Product) => {
              const saved = savedValue(product)
              const value = currentValue(product)
              const dirty = isDirty(product)
              const busy = savingId === product.id
              return (
                <Flex gap={6} align="center">
                  <InputNumber<number>
                    min={0}
                    value={value ?? null}
                    disabled={busy}
                    style={{ width: 135 }}
                    placeholder="Nhập giá (đ)"
                    formatter={formatPriceInput}
                    parser={(text) => Number(parsePriceInput(text))}
                    aria-label={`Đơn giá ${product.code}`}
                    onChange={(next) => setDrafts((prev) => ({ ...prev, [product.id]: next }))}
                    onPressEnter={() => {
                      if (dirty && value != null) void savePrice(product, value)
                    }}
                  />
                  <Button
                    type="primary"
                    size="small"
                    icon={<CheckOutlined />}
                    disabled={!dirty || value == null}
                    loading={busy}
                    onClick={() => {
                      if (value != null) void savePrice(product, value)
                    }}
                  >
                    Lưu
                  </Button>
                  {saved != null && (
                    <Popconfirm
                      title={`Bỏ giá của ${product.name} khỏi bảng giá "${priceList?.name}"?`}
                      okText="Bỏ giá"
                      okButtonProps={{ danger: true }}
                      cancelText="Không"
                      onConfirm={() => void savePrice(product, null)}
                    >
                      <Button
                        type="text"
                        size="small"
                        danger
                        disabled={busy}
                        icon={<DeleteOutlined />}
                        aria-label={`Bỏ giá ${product.code}`}
                      />
                    </Popconfirm>
                  )}
                </Flex>
              )
            },
          },
        ] satisfies NonNullable<TableProps<Product>['columns']>)
      : []),
    {
      title: 'Cập nhật',
      key: 'updatedAt',
      width: 135,
      render: (_, product) => {
        const at = itemsMap[product.id]?.updatedAt
        return at ? formatDateTime(at) : EMPTY_TEXT
      },
    },
    {
      title: 'Kinh doanh',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => (
        <StatusTag active={active} activeText="Đang KD" inactiveText="Ngừng KD" />
      ),
    },
  ]

  return (
    <Drawer
      open={open}
      title={priceList ? `Bảng giá · ${priceList.name}` : 'Chi tiết bảng giá'}
      size="min(90vw, 1380px)"
      onClose={onClose}
      destroyOnHidden
    >
      <Flex vertical gap={12}>
        {priceList?.description && (
          <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
            {priceList.description}
          </Typography.Paragraph>
        )}
        {itemsError && <Alert type="error" showIcon closable title={itemsError} onClose={() => setItemsError(null)} />}
        <FilterBar fields={filterFields} value={filters} onChange={applyFilters} />
        <DataTable<Product>
          columns={columns}
          page={page}
          loading={loading || loadingItems}
          rowKey="id"
          onPageChange={changePage}
          error={error}
          onRetry={reload}
          extraReservedHeight={FILTER_BAR_HEIGHT + 40}
          emptyText={<ListEmpty filtering={filtering} noun="sản phẩm" onClearFilters={() => applyFilters({})} />}
        />
      </Flex>
    </Drawer>
  )
}
