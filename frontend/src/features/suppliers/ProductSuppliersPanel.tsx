import { DisconnectOutlined, PlusOutlined } from '@ant-design/icons'
import { Alert, App, Button, Flex, Popconfirm, Select, Table, Typography, theme } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import type { PageQuery } from '../../api/types'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import type { Product } from '../products/product-api'
import { supplierApi } from './supplier-api'
import type { ProductSupplierLink, Supplier } from './supplier-api'

interface ProductSuppliersPanelProps {
  product: Pick<Product, 'id' | 'code' | 'name' | 'active'>
  /** Có thì hiện tiêu đề và cách phần phía trên (khi nằm dưới thông tin SP trong khung chi tiết) */
  title?: string
}

/**
 * Chiều ngược của SupplierProductsPanel: SP này do những NCC nào cung cấp (BR-SUP-05).
 * Gán/gỡ gọi cùng API với phía NCC — một quy tắc, một chỗ kiểm tra ở backend.
 * SP ngừng kinh doanh thì ẩn ô gán, vì backend sẽ từ chối (BR-SUP-06).
 */
export function ProductSuppliersPanel({ product, title }: ProductSuppliersPanelProps) {
  const { message, notification } = App.useApp()
  const { token } = theme.useToken()

  const fetchLinks = useCallback(
    (query: PageQuery) => supplierApi.listSuppliersOfProduct(product.id, query),
    [product.id],
  )
  const { page, loading, error, changePage, reload } = usePagedList(fetchLinks, 10)

  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [linking, setLinking] = useState(false)

  useEffect(() => {
    if (!product.active) return
    let ignore = false
    supplierApi
      .listForSelect()
      // BR-SUP-06: chỉ gợi ý NCC đang hoạt động
      .then((result) => {
        if (!ignore) setSuppliers(result.items.filter((supplier) => supplier.active))
      })
      .catch(() => {
        if (!ignore) message.error('Không tải được danh sách nhà cung cấp')
      })
    return () => {
      ignore = true
    }
  }, [product.active, message])

  // Làm mờ NCC đã gán (trong trang đang xem); backend vẫn chặn trùng bằng 409
  const linkedIds = new Set(page?.items.map((item) => item.supplierId))
  const options = suppliers.map((supplier) => ({
    value: supplier.id,
    label: `${supplier.code} · ${supplier.name}`,
    disabled: linkedIds.has(supplier.id),
  }))

  const link = async () => {
    if (selectedId === null) return
    setLinking(true)
    try {
      await supplierApi.linkProduct(selectedId, product.id)
      const supplier = suppliers.find((item) => item.id === selectedId)
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
      render: (code: string) => <span style={{ fontFamily: token.fontFamilyCode }}>{code}</span>,
    },
    { title: 'Tên nhà cung cấp', dataIndex: 'supplierName' },
    { title: 'Người liên hệ', dataIndex: 'contactName' },
    {
      title: 'Trạng thái',
      dataIndex: 'supplierActive',
      width: 140,
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
          <Button type="text" danger icon={<DisconnectOutlined />} aria-label="Gỡ nhà cung cấp" />
        </Popconfirm>
      ),
    },
  ]

  return (
    <Flex vertical gap={12} style={title ? { marginTop: token.marginLG } : undefined}>
      {title && (
        <Typography.Title level={5} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
      )}

      {product.active ? (
        <Flex gap={8}>
          <Select<number>
            style={{ flex: 1, minWidth: 0 }}
            placeholder="Chọn nhà cung cấp"
            value={selectedId ?? undefined}
            options={options}
            // Danh sách đã tải sẵn (tối đa 100) → lọc ngay trên trình duyệt theo nhãn "mã · tên"
            showSearch={{ optionFilterProp: 'label' }}
            onChange={setSelectedId}
            notFoundContent="Không có nhà cung cấp phù hợp"
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
