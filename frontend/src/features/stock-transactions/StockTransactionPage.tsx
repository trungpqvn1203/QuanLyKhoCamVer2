import { Flex, Tag, Typography, theme } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { EMPTY_TEXT, formatDate, formatDateTime, formatQuantity } from '../../utils/format'
import { useCan } from '../auth/auth-context'
import { InboundReceiptDrawer } from '../inbound-receipts/InboundReceiptDrawer'
import { useReceiptReferences } from '../inbound-receipts/use-receipt-references'
import { PRODUCT_UNIT_LABELS, productApi } from '../products/product-api'
import type { Product } from '../products/product-api'
import { SalesOrderDrawer } from '../sales-orders/SalesOrderDrawer'
import { useSalesReferences } from '../sales-orders/use-sales-references'
import {
  DOCUMENT_TYPE_LABELS,
  MOVEMENT_LABELS,
  TRANSACTION_TYPE_LABELS,
  movementKind,
  signedQuantity,
  stockTransactionApi,
} from './stock-transaction-api'
import type {
  MovementKind,
  StockDocumentType,
  StockTransaction,
  StockTransactionFilters,
  StockTransactionType,
} from './stock-transaction-api'

const MOVEMENT_COLORS: Record<MovementKind, string> = {
  INBOUND: 'success',
  SALE: 'processing',
  INBOUND_REVERSAL: 'warning',
  SALE_REVERSAL: 'warning',
}

const DOCUMENT_TYPE_OPTIONS = (Object.keys(DOCUMENT_TYPE_LABELS) as StockDocumentType[]).map((type) => ({
  value: type,
  label: DOCUMENT_TYPE_LABELS[type],
}))

const TRANSACTION_TYPE_OPTIONS = (Object.keys(TRANSACTION_TYPE_LABELS) as StockTransactionType[]).map((type) => ({
  value: type,
  label: TRANSACTION_TYPE_LABELS[type],
}))

/** Tối đa 100 SP cho ô lọc (giới hạn size của backend) — đủ cho MVP, như các danh sách tham chiếu khác. */
const PRODUCT_REFERENCE_SIZE = 100

/** ?productId=5&warehouseId=2 (trang khác dẫn sang) → bộ lọc ban đầu. Giá trị không phải số bị bỏ qua. */
function filtersFromUrl(params: URLSearchParams): StockTransactionFilters {
  const toId = (value: string | null) => (value && /^\d+$/.test(value) ? Number(value) : undefined)
  return { productId: toId(params.get('productId')), warehouseId: toId(params.get('warehouseId')) }
}

type OpenedDocument = { type: StockDocumentType; id: number }

/**
 * Lịch sử biến động tồn kho (BR-INV-04..06) — chỉ đọc. Lọc một SP + một kho để dò tồn từng bước:
 * dòng trên cùng "Tồn sau" = tồn hiện tại; "Tồn trước" mỗi dòng = "Tồn sau" của dòng ngay dưới.
 * Bấm mã chứng từ mở đúng khung phiếu nhập / phiếu bán.
 */
export function StockTransactionPage() {
  const { token } = theme.useToken()
  const canSeeInbound = useCan('inbound')
  const [searchParams] = useSearchParams()
  const receiptReferences = useReceiptReferences()
  const salesReferences = useSalesReferences()
  const [products, setProducts] = useState<Product[]>([])
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    stockTransactionApi.list,
    20,
    filtersFromUrl(searchParams),
  )
  const [opened, setOpened] = useState<OpenedDocument | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  // Tăng mỗi lần mở → khung tạo mới hoàn toàn, không lẫn phiếu xem lần trước
  const [drawerKey, setDrawerKey] = useState(0)

  useEffect(() => {
    let ignore = false
    productApi
      .list({ page: 0, size: PRODUCT_REFERENCE_SIZE })
      .then((result) => {
        if (!ignore) setProducts(result.items)
      })
      .catch(() => {
        // giữ danh sách rỗng; lỗi thật hiện khi gọi API chính
      })
    return () => {
      ignore = true
    }
  }, [])

  const openDocument = (transaction: StockTransaction) => {
    setOpened({ type: transaction.documentType, id: transaction.documentId })
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  // Gồm cả SP/kho đã ngừng — lịch sử của chúng vẫn cần tra được
  const filterFields: FilterField<StockTransactionFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã chứng từ' },
    {
      type: 'select',
      name: 'productId',
      placeholder: 'Sản phẩm',
      options: products.map((product) => ({ value: product.id, label: `${product.code} · ${product.name}` })),
      width: 260,
    },
    {
      type: 'select',
      name: 'warehouseId',
      placeholder: 'Kho',
      options: receiptReferences.warehouses.map((warehouse) => ({
        value: warehouse.id,
        label: `${warehouse.code} · ${warehouse.name}`,
      })),
      width: 200,
    },
    { type: 'select', name: 'documentType', placeholder: 'Loại chứng từ', options: DOCUMENT_TYPE_OPTIONS, width: 150 },
    { type: 'select', name: 'type', placeholder: 'Tăng / giảm', options: TRANSACTION_TYPE_OPTIONS, width: 130 },
    { type: 'dateRange', from: 'createdFrom', to: 'createdTo', placeholder: ['Ghi sổ từ ngày', 'đến ngày'] },
  ]

  const columns: TableProps<StockTransaction>['columns'] = [
    {
      title: 'Thời điểm ghi sổ',
      dataIndex: 'createdAt',
      width: 150,
      render: (createdAt: string) => formatDateTime(createdAt),
    },
    {
      title: 'Sản phẩm',
      key: 'product',
      render: (_, transaction) => (
        <Flex vertical>
          <Typography.Text strong>{transaction.productName}</Typography.Text>
          <Typography.Text type="secondary">
            <CodeText code={transaction.productCode} />
          </Typography.Text>
        </Flex>
      ),
    },
    { title: 'Kho', dataIndex: 'warehouseName', width: 160 },
    {
      title: 'Nghiệp vụ',
      key: 'movement',
      width: 140,
      render: (_, transaction) => {
        const kind = movementKind(transaction)
        return (
          <Tag color={MOVEMENT_COLORS[kind]} variant="filled" style={{ marginInlineEnd: 0 }}>
            {MOVEMENT_LABELS[kind]}
          </Tag>
        )
      },
    },
    {
      title: 'Chứng từ',
      key: 'document',
      width: 140,
      render: (_, transaction) => (
        <Flex vertical align="flex-start">
          {/* Nhân viên bán hàng không xem được phiếu nhập (ADR-0003) → hiện mã, không mở khung */}
          {transaction.documentType === 'INBOUND' && !canSeeInbound ? (
            <CodeText code={transaction.documentCode} />
          ) : (
            <CodeLink code={transaction.documentCode} onClick={() => openDocument(transaction)} />
          )}
          <Typography.Text type="secondary">{formatDate(transaction.documentDate)}</Typography.Text>
        </Flex>
      ),
    },
    {
      title: 'Đối tác',
      key: 'partner',
      render: (_, transaction) => transaction.supplierName ?? transaction.customerName ?? EMPTY_TEXT,
    },
    {
      title: 'Thay đổi',
      key: 'change',
      width: 110,
      align: 'right',
      render: (_, transaction) => {
        const change = signedQuantity(transaction)
        return (
          <Typography.Text strong style={{ color: change > 0 ? token.colorSuccess : token.colorError }}>
            {change > 0 ? '+' : '−'}
            {formatQuantity(Math.abs(change))} {PRODUCT_UNIT_LABELS[transaction.unit].toLowerCase()}
          </Typography.Text>
        )
      },
    },
    {
      title: 'Tồn trước',
      dataIndex: 'quantityBefore',
      width: 100,
      align: 'right',
      render: (quantity: number) => <Typography.Text type="secondary">{formatQuantity(quantity)}</Typography.Text>,
    },
    {
      title: 'Tồn sau',
      dataIndex: 'quantityAfter',
      width: 100,
      align: 'right',
      render: (quantity: number) => <Typography.Text strong>{formatQuantity(quantity)}</Typography.Text>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Lịch sử tồn kho"
        description="Mọi lần tăng/giảm tồn do nhập, bán hoặc đảo phiếu. Tồn trước/sau tính riêng tại từng kho — chọn một sản phẩm và một kho để dò tồn hiện tại từng bước"
      />
      <FilterBar<StockTransactionFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<StockTransaction>
        columns={columns}
        page={page}
        loading={loading}
        rowKey="id"
        onPageChange={changePage}
        error={error}
        onRetry={reload}
        extraReservedHeight={FILTER_BAR_HEIGHT}
        emptyText={<ListEmpty noun="biến động tồn" filtering={filtering} onClearFilters={() => applyFilters({})} />}
      />
      {opened?.type === 'INBOUND' && (
        <InboundReceiptDrawer
          key={drawerKey}
          open={drawerOpen}
          receiptId={opened.id}
          references={receiptReferences}
          onClose={() => setDrawerOpen(false)}
          onChanged={reload}
        />
      )}
      {opened?.type === 'OUTBOUND' && (
        <SalesOrderDrawer
          key={drawerKey}
          open={drawerOpen}
          orderId={opened.id}
          references={salesReferences}
          onClose={() => setDrawerOpen(false)}
          onChanged={reload}
        />
      )}
    </>
  )
}
