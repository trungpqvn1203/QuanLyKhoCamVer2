import { DisconnectOutlined, PlusOutlined } from '@ant-design/icons'
import { Alert, App, Button, Flex, Popconfirm, Select, Table } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import type { PageQuery } from '../../api/types'
import { CodeText } from '../../components/ui/CodeLink'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import type { Product } from '../products/product-api'
import { supplierApi } from './supplier-api'
import type { ProductSupplierLink, Supplier } from './supplier-api'

interface ProductSuppliersPanelProps {
  product: Pick<Product, 'id' | 'code' | 'name' | 'active'>
}

/** Chờ người dùng ngừng gõ rồi mới gọi API (giống ô tìm kiếm trên thanh lọc). */
const SEARCH_DEBOUNCE_MS = 400

/** Số NCC gợi ý mỗi lần tìm — gõ thêm chữ để thu hẹp. */
const SUGGESTION_SIZE = 20

/**
 * Chiều ngược của SupplierProductsPanel: SP này do những NCC nào cung cấp (BR-SUP-05).
 * Nằm ở tab "Nhà cung cấp" trong khung chi tiết SP (mở từ mã SP hoặc menu ⋯).
 * Gán/gỡ gọi cùng API với phía NCC — một quy tắc, một chỗ kiểm tra ở backend.
 * SP ngừng kinh doanh thì ẩn ô gán, vì backend sẽ từ chối (BR-SUP-06).
 */
export function ProductSuppliersPanel({ product }: ProductSuppliersPanelProps) {
  const { message, notification } = App.useApp()

  const fetchLinks = useCallback(
    (query: PageQuery) => supplierApi.listSuppliersOfProduct(product.id, query),
    [product.id],
  )
  const { page, loading, error, changePage, reload } = usePagedList(fetchLinks, 10)

  const [keyword, setKeyword] = useState('')
  const [suggestions, setSuggestions] = useState<Supplier[]>([])
  const [searching, setSearching] = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [linking, setLinking] = useState(false)

  useEffect(() => {
    if (!product.active) return
    let ignore = false
    const timer = setTimeout(() => {
      setSearching(true)
      // BR-SUP-06: chỉ gợi ý NCC đang hoạt động; tìm theo mã/tên phía backend
      supplierApi
        .list({ page: 0, size: SUGGESTION_SIZE }, { keyword, active: true })
        .then((result) => {
          if (!ignore) setSuggestions(result.items)
        })
        .catch(() => {
          if (!ignore) setSuggestions([])
        })
        .finally(() => {
          if (!ignore) setSearching(false)
        })
    }, SEARCH_DEBOUNCE_MS)
    return () => {
      ignore = true
      clearTimeout(timer)
    }
  }, [keyword, product.active])

  // Làm mờ NCC đã gán (trong trang đang xem); backend vẫn chặn trùng bằng 409
  const linkedIds = new Set(page?.items.map((item) => item.supplierId))
  const options = suggestions.map((supplier) => ({
    value: supplier.id,
    label: `${supplier.code} · ${supplier.name}`,
    disabled: linkedIds.has(supplier.id),
  }))

  const link = async () => {
    if (selectedId === null) return
    setLinking(true)
    try {
      await supplierApi.linkProduct(selectedId, product.id)
      const supplier = suggestions.find((item) => item.id === selectedId)
      notification.success({
        title: 'Đã gán nhà cung cấp',
        description: `${supplier?.code ?? ''} → ${product.code} · ${product.name}`,
      })
      setSelectedId(null)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không gán được nhà cung cấp')
    } finally {
      setLinking(false)
    }
  }

  const unlink = async (item: ProductSupplierLink) => {
    try {
      await supplierApi.unlinkProduct(item.supplierId, product.id)
      notification.success({ title: 'Đã gỡ nhà cung cấp', description: `${item.supplierCode} · ${item.supplierName}` })
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không gỡ được nhà cung cấp')
    }
  }

  const columns: TableProps<ProductSupplierLink>['columns'] = [
    {
      title: 'Mã NCC',
      dataIndex: 'supplierCode',
      width: 100,
      render: (code: string) => <CodeText code={code} />,
    },
    { title: 'Tên nhà cung cấp', dataIndex: 'supplierName' },
    { title: 'Người liên hệ', dataIndex: 'contactName' },
    {
      title: 'Trạng thái',
      dataIndex: 'supplierActive',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} />,
    },
    {
      title: '',
      key: 'actions',
      width: 48,
      align: 'center',
      render: (_, item) => (
        <Popconfirm
          title={`Gỡ ${item.supplierName} khỏi ${product.name}?`}
          description="Phiếu nhập từ NCC này sẽ không chọn được SP này nữa."
          okText="Gỡ"
          okButtonProps={{ danger: true }}
          cancelText="Hủy"
          onConfirm={() => unlink(item)}
        >
          <Button type="text" danger icon={<DisconnectOutlined />} aria-label={`Gỡ ${item.supplierCode}`} />
        </Popconfirm>
      ),
    },
  ]

  return (
    <Flex vertical gap={12}>
      {product.active ? (
        <Flex gap={8}>
          <Select<number>
            style={{ flex: 1, minWidth: 0 }}
            placeholder="Tìm nhà cung cấp theo mã hoặc tên"
            aria-label="Chọn nhà cung cấp để gán"
            value={selectedId ?? undefined}
            options={options}
            loading={searching}
            // filterOption=false: không lọc lại phía trình duyệt, kết quả đã lọc ở backend
            showSearch={{ filterOption: false, onSearch: setKeyword }}
            onChange={setSelectedId}
            notFoundContent={searching ? 'Đang tìm…' : 'Không có nhà cung cấp phù hợp'}
          />
          <Button type="primary" icon={<PlusOutlined />} disabled={selectedId === null} loading={linking} onClick={link}>
            Gán
          </Button>
        </Flex>
      ) : (
        <Alert type="info" showIcon title="Sản phẩm đã ngừng kinh doanh. Mở bán lại để gán thêm nhà cung cấp." />
      )}

      {error && <Alert type="error" showIcon title={error} action={<Button size="small" onClick={reload}>Thử lại</Button>} />}

      <Table<ProductSupplierLink>
        size="small"
        rowKey="supplierId"
        columns={columns}
        dataSource={page?.items ?? []}
        loading={loading}
        locale={{ emptyText: 'Chưa có nhà cung cấp nào' }}
        pagination={{
          current: (page?.pagination.page ?? 0) + 1,
          pageSize: page?.pagination.size ?? 10,
          total: page?.pagination.totalElements ?? 0,
          hideOnSinglePage: true,
          size: 'small',
          onChange: (current, size) => changePage(current - 1, size),
        }}
      />
    </Flex>
  )
}
