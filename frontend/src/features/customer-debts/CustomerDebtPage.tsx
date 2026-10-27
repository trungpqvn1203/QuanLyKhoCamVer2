import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Statistic, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { CustomerTypeTag } from '../customers/CustomerTypeTag'
import { UnpaidOpeningDebts } from '../opening-debts/UnpaidOpeningDebts'
import type { CustomerType } from '../customers/customer-api'
import { customerDebtApi, salesOrderApi } from '../sales-orders/sales-order-api'
import type { CustomerDebt, SalesOrderSummary } from '../sales-orders/sales-order-api'
import { SalesOrderDrawer } from '../sales-orders/SalesOrderDrawer'
import { useSalesReferences } from '../sales-orders/use-sales-references'

/** Một khách tối đa 100 phiếu còn nợ (giới hạn size backend) — đủ cho MVP. */
const UNPAID_ORDERS_SIZE = 100

/** Các phiếu còn nợ của một khách — tải khi mở rộng dòng; `version` đổi thì tải lại (sau khi thu tiền). */
function UnpaidOrders({ customerId, version, onOpen }: { customerId: number; version: number; onOpen: (id: number) => void }) {
  const [orders, setOrders] = useState<SalesOrderSummary[] | null>(null)

  useEffect(() => {
    let ignore = false
    salesOrderApi
      .list({ page: 0, size: UNPAID_ORDERS_SIZE }, { customerId, unpaidOnly: 'true' })
      .then((result) => result.items)
      .catch(() => [] as SalesOrderSummary[])
      .then((items) => {
        if (!ignore) setOrders(items)
      })
    return () => {
      ignore = true
    }
  }, [customerId, version])

  const columns: TableProps<SalesOrderSummary>['columns'] = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 120,
      render: (code: string, order) => <CodeLink code={code} onClick={() => onOpen(order.id)} />,
    },
    { title: 'Ngày bán', dataIndex: 'documentDate', width: 110, render: (date: string) => formatDate(date) },
    { title: 'Kho xuất', dataIndex: 'warehouseName' },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    { title: 'Đã thu', dataIndex: 'paidAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 140,
      align: 'right',
      render: (v: number) => <Typography.Text type="danger">{formatVnd(v)}</Typography.Text>,
    },
  ]

  return (
    <Table<SalesOrderSummary>
      size="small"
      rowKey="id"
      loading={orders === null}
      columns={columns}
      dataSource={orders ?? []}
      pagination={false}
    />
  )
}

/**
 * Công nợ khách hàng (BR-SAL-10): mọi khách đang còn nợ, nợ nhiều nhất lên đầu. Mở rộng một dòng để xem các phiếu
 * còn nợ; bấm mã phiếu để thu tiền ngay trong khung phiếu bán.
 */
export function CustomerDebtPage() {
  const references = useSalesReferences()
  const [debts, setDebts] = useState<CustomerDebt[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [openedId, setOpenedId] = useState<number | null>(null)
  const [drawerKey, setDrawerKey] = useState(0)

  // version tăng (Tải lại, Thử lại, sau khi thu tiền) → tải lại; lỗi cũ xóa khi có kết quả mới
  useEffect(() => {
    let ignore = false
    customerDebtApi
      .list()
      .then((loaded) => {
        if (ignore) return
        setDebts(loaded)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được công nợ')
      })
    return () => {
      ignore = true
    }
  }, [version])

  const reload = () => setVersion((v) => v + 1)

  const openOrder = (id: number) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
  }

  const total = (debts ?? []).reduce((sum, debt) => sum + debt.outstandingAmount, 0)

  const columns: TableProps<CustomerDebt>['columns'] = [
    { title: 'Mã KH', dataIndex: 'customerCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      render: (name: string, debt) => (
        <>
          <Typography.Text strong>{name}</Typography.Text>
          {!debt.customerActive && <Typography.Text type="secondary"> (ngừng hoạt động)</Typography.Text>}
        </>
      ),
    },
    { title: 'Loại', dataIndex: 'customerType', width: 120, render: (type: CustomerType) => <CustomerTypeTag type={type} /> },
    { title: 'SĐT', dataIndex: 'customerPhone', width: 130 },
    { title: 'Số phiếu nợ', dataIndex: 'openOrderCount', width: 110, align: 'right' },
    {
      title: 'Nợ từ ngày',
      dataIndex: 'oldestDocumentDate',
      width: 120,
      render: (date: string) => formatDate(date),
    },
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
        title="Công nợ khách hàng"
        description="Tổng còn nợ trên các phiếu bán đã hoàn tất và nợ đầu kỳ. Mở rộng một khách để xem từng khoản và thu tiền"
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
              title={`Tổng công nợ · ${debts?.length ?? 0} khách`}
              value={formatVnd(total)}
              loading={debts === null}
            />
          </Card>
          <Table<CustomerDebt>
            rowKey="customerId"
            loading={debts === null}
            columns={columns}
            dataSource={debts ?? []}
            pagination={false}
            locale={{ emptyText: 'Không có khách nào còn nợ' }}
            expandable={{
              expandedRowRender: (debt) => (
                <>
                  {/* Nợ đầu kỳ (BR-ODB-05) — không có thì không hiện */}
                  <UnpaidOpeningDebts party="CUSTOMER" partyId={debt.customerId} version={version} onChanged={reload} />
                  <UnpaidOrders customerId={debt.customerId} version={version} onOpen={openOrder} />
                </>
              ),
            }}
          />
        </>
      )}
      {openedId !== null && (
        <SalesOrderDrawer
          key={drawerKey}
          open
          orderId={openedId}
          references={references}
          onClose={() => setOpenedId(null)}
          onChanged={reload}
        />
      )}
    </>
  )
}
