import { DisconnectOutlined, PlusOutlined } from '@ant-design/icons'
import { Alert, App, Button, Flex, Popconfirm, Select, Table } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import type { PageQuery } from '../../api/types'
import { CodeText } from '../../components/ui/CodeLink'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { productApi } from '../products/product-api'
import type { Product } from '../products/product-api'
import { supplierApi } from './supplier-api'
import type { Supplier, SupplierProduct } from './supplier-api'

interface SupplierProductsPanelProps {
  supplier: Supplier
}

/** Chờ người dùng ngừng gõ rồi mới gọi API (giống ô tìm kiếm trang Sản phẩm). */
const SEARCH_DEBOUNCE_MS = 400

/** Số SP gợi ý mỗi lần tìm — gõ thêm chữ để thu hẹp. */
const SUGGESTION_SIZE = 20

/**
 * Danh sách SP mà NCC được phép cung cấp (BR-SUP-05).
 * Nằm ở tab "Sản phẩm cung cấp" trong khung chi tiết NCC (mở từ mã NCC hoặc menu ⋯).
 * - Gán: chọn SP đang kinh doanh (tìm theo mã/tên phía backend) → POST.
 * - Gỡ: hỏi xác nhận → DELETE (backend chỉ đặt active=false).
 * NCC ngừng hoạt động thì ẩn ô gán, vì backend sẽ từ chối (BR-SUP-06).
 */
export function SupplierProductsPanel({ supplier }: SupplierProductsPanelProps) {
  const { message, notification } = App.useApp()

  // useCallback: usePagedList tải lại mỗi khi hàm này đổi → chỉ đổi khi sang NCC khác
  const fetchLinks = useCallback((query: PageQuery) => supplierApi.listProducts(supplier.id, query), [supplier.id])
  const { page, loading, error, changePage, reload } = usePagedList(fetchLinks, 10)

  const [keyword, setKeyword] = useState('')
  const [suggestions, setSuggestions] = useState<Product[]>([])
  const [searching, setSearching] = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [linking, setLinking] = useState(false)

  useEffect(() => {
    if (!supplier.active) return
    let ignore = false
    const timer = setTimeout(() => {
      setSearching(true)
      // BR-SUP-06: chỉ gợi ý SP đang kinh doanh
      productApi
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
  }, [keyword, supplier.active])

  // Làm mờ SP đã gán (trong trang đang xem) để khỏi bấm nhầm; backend vẫn chặn trùng bằng 409
  const linkedIds = new Set(page?.items.map((item) => item.productId))
  const options = suggestions.map((product) => ({
    value: product.id,
    label: `${product.code} · ${product.name}`,
    disabled: linkedIds.has(product.id),
  }))

  const link = async () => {
    if (selectedId === null) return
    setLinking(true)
    try {
      const linked = await supplierApi.linkProduct(supplier.id, selectedId)
      notification.success({
        title: 'Đã gán sản phẩm',
        description: `${linked.productCode} · ${linked.productName} → ${supplier.code}`,
      })
      setSelectedId(null)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không gán được sản phẩm')
    } finally {
      setLinking(false)
    }
  }

  const unlink = async (item: SupplierProduct) => {
    try {
      await supplierApi.unlinkProduct(supplier.id, item.productId)
      notification.success({ title: 'Đã gỡ sản phẩm', description: `${item.productCode} · ${item.productName}` })
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không gỡ được sản phẩm')
    }
  }

  const columns: TableProps<SupplierProduct>['columns'] = [
    {
      title: 'Mã SP',
      dataIndex: 'productCode',
      width: 100,
      render: (code: string) => <CodeText code={code} />,
    },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
    {
      title: 'Trạng thái',
      dataIndex: 'productActive',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => (
        <StatusTag active={active} activeText="Đang kinh doanh" inactiveText="Ngừng kinh doanh" />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 48,
      align: 'center',
      render: (_, item) => (
        <Popconfirm
          title={`Gỡ ${item.productName} khỏi ${supplier.name}?`}
          description="Phiếu nhập từ NCC này sẽ không chọn được SP này nữa."
          okText="Gỡ"
          okButtonProps={{ danger: true }}
          cancelText="Hủy"
          onConfirm={() => unlink(item)}
        >
          <Button type="text" danger icon={<DisconnectOutlined />} aria-label={`Gỡ ${item.productCode}`} />
        </Popconfirm>
      ),
    },
  ]

  return (
    <Flex vertical gap={12}>
      {supplier.active ? (
        <Flex gap={8}>
          <Select<number>
            style={{ flex: 1, minWidth: 0 }}
            placeholder="Tìm sản phẩm theo mã hoặc tên"
            aria-label="Chọn sản phẩm để gán"
            value={selectedId ?? undefined}
            options={options}
            loading={searching}
            // filterOption=false: không lọc lại phía trình duyệt, kết quả đã lọc ở backend
            showSearch={{ filterOption: false, onSearch: setKeyword }}
            onChange={setSelectedId}
            notFoundContent={searching ? 'Đang tìm…' : 'Không có sản phẩm phù hợp'}
          />
          <Button type="primary" icon={<PlusOutlined />} disabled={selectedId === null} loading={linking} onClick={link}>
            Gán
          </Button>
        </Flex>
      ) : (
        <Alert type="info" showIcon title="Nhà cung cấp đang ngừng hoạt động. Mở lại để gán thêm sản phẩm." />
      )}

      {error && <Alert type="error" showIcon title={error} action={<Button size="small" onClick={reload}>Thử lại</Button>} />}

      <Table<SupplierProduct>
        size="small"
        rowKey="productId"
        columns={columns}
        dataSource={page?.items ?? []}
        loading={loading}
        locale={{ emptyText: 'Chưa gán sản phẩm nào' }}
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
