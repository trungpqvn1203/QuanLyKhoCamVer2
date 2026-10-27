import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Statistic, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { formatDate, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { inboundReceiptApi, supplierDebtApi } from '../inbound-receipts/inbound-receipt-api'
import { UnpaidOpeningDebts } from '../opening-debts/UnpaidOpeningDebts'
import type { InboundReceiptSummary, SupplierDebt } from '../inbound-receipts/inbound-receipt-api'
import { InboundReceiptDrawer } from '../inbound-receipts/InboundReceiptDrawer'
import { useReceiptReferences } from '../inbound-receipts/use-receipt-references'

/** Một NCC tối đa 100 phiếu còn nợ (giới hạn size backend) — đủ cho MVP. */
const UNPAID_RECEIPTS_SIZE = 100

/** Các phiếu nhập còn nợ của một NCC — tải khi mở rộng dòng; `version` đổi thì tải lại (sau khi trả tiền). */
function UnpaidReceipts({
  supplierId,
  version,
  onOpen,
}: {
  supplierId: number
  version: number
  onOpen: (id: number) => void
}) {
  const [receipts, setReceipts] = useState<InboundReceiptSummary[] | null>(null)

  useEffect(() => {
    let ignore = false
    inboundReceiptApi
      .list({ page: 0, size: UNPAID_RECEIPTS_SIZE }, { supplierId, unpaidOnly: 'true' })
      .then((result) => result.items)
      .catch(() => [] as InboundReceiptSummary[])
      .then((items) => {
        if (!ignore) setReceipts(items)
      })
    return () => {
      ignore = true
    }
  }, [supplierId, version])

  const columns: TableProps<InboundReceiptSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, receipt) => <CodeLink code={code} onClick={() => onOpen(receipt.id)} />,
    },
    { title: 'Ngày nhập', dataIndex: 'documentDate', width: 110, render: (date: string) => formatDate(date) },
    { title: 'Kho nhận', dataIndex: 'warehouseName' },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    { title: 'Đã trả', dataIndex: 'paidAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 140,
      align: 'right',
      render: (v: number) => <Typography.Text type="danger">{formatVnd(v)}</Typography.Text>,
    },
  ]

  return (
    <Table<InboundReceiptSummary>
      size="small"
      rowKey="id"
      loading={receipts === null}
      columns={columns}
      dataSource={receipts ?? []}
      pagination={false}
    />
  )
}

/**
 * Công nợ nhà cung cấp (BR-SPAY-01): mọi NCC mình đang còn nợ, nợ nhiều nhất lên đầu. Mở rộng một dòng để xem các
 * phiếu nhập còn nợ; bấm mã phiếu để trả tiền ngay trong khung phiếu nhập. Cùng bố cục với công nợ khách hàng.
 */
export function SupplierDebtPage() {
  const references = useReceiptReferences()
  const [debts, setDebts] = useState<SupplierDebt[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [openedId, setOpenedId] = useState<number | null>(null)
  const [drawerKey, setDrawerKey] = useState(0)

  // version tăng (Tải lại, Thử lại, sau khi trả tiền) → tải lại; lỗi cũ xóa khi có kết quả mới
  useEffect(() => {
    let ignore = false
    supplierDebtApi
      .list()
      .then((loaded) => {
        if (ignore) return
        setDebts(loaded)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được công nợ nhà cung cấp')
      })
    return () => {
      ignore = true
    }
  }, [version])

  const reload = () => setVersion((v) => v + 1)

  const openReceipt = (id: number) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
  }

  const total = (debts ?? []).reduce((sum, debt) => sum + debt.outstandingAmount, 0)

  const columns: TableProps<SupplierDebt>['columns'] = [
    { title: 'Mã NCC', dataIndex: 'supplierCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    {
      title: 'Nhà cung cấp',
      dataIndex: 'supplierName',
      render: (name: string, debt) => (
        <>
          <Typography.Text strong>{name}</Typography.Text>
          {!debt.supplierActive && <Typography.Text type="secondary"> (ngừng hoạt động)</Typography.Text>}
        </>
      ),
    },
    { title: 'Mã số thuế', dataIndex: 'supplierTaxCode', width: 150, render: (taxCode?: string) => orEmpty(taxCode) },
    { title: 'Số phiếu nợ', dataIndex: 'openReceiptCount', width: 110, align: 'right' },
    { title: 'Nợ từ ngày', dataIndex: 'oldestDocumentDate', width: 120, render: (date: string) => formatDate(date) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 160,
      align: 'right',
      render: (amount: number) => (
        <Typography.Text strong type="danger">
          {formatVnd(amount)}
        </Typography.Text>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Công nợ nhà cung cấp"
        description="Số tiền mình còn nợ trên các phiếu nhập đã hoàn tất và nợ đầu kỳ. Mở rộng một NCC để xem từng khoản và trả tiền"
        actions={
          <Button icon={<ReloadOutlined />} onClick={reload}>
            Tải lại
          </Button>
        }
      />
      {error ? (
        <Alert
          type="error"
          showIcon
          title={error}
          action={
            <Button size="small" onClick={reload}>
              Thử lại
            </Button>
          }
        />
      ) : (
        <>
          <Card size="small" style={{ marginBottom: 16, maxWidth: 360 }}>
            <Statistic
              title={`Tổng phải trả · ${debts?.length ?? 0} nhà cung cấp`}
              value={formatVnd(total)}
              loading={debts === null}
            />
          </Card>
          <Table<SupplierDebt>
            rowKey="supplierId"
            loading={debts === null}
            columns={columns}
            dataSource={debts ?? []}
            pagination={false}
            locale={{ emptyText: 'Không còn nợ nhà cung cấp nào' }}
            expandable={{
              expandedRowRender: (debt) => (
                <>
                  {/* Nợ đầu kỳ (BR-ODB-05) — không có thì không hiện */}
                  <UnpaidOpeningDebts party="SUPPLIER" partyId={debt.supplierId} version={version} onChanged={reload} />
                  <UnpaidReceipts supplierId={debt.supplierId} version={version} onOpen={openReceipt} />
                </>
              ),
            }}
          />
        </>
      )}
      {openedId !== null && (
        <InboundReceiptDrawer
          key={drawerKey}
          open
          receiptId={openedId}
          references={references}
          onClose={() => setOpenedId(null)}
          onChanged={reload}
        />
      )}
    </>
  )
}
