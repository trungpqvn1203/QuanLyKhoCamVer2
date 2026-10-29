import { Alert, Button, Card, Col, Flex, Row, Skeleton, Statistic, Typography } from 'antd'
import type { ReactNode } from 'react'
import { Suspense, lazy, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { formatDate, formatQuantity, formatTons } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { useCan } from '../auth/auth-context'
import { cashBookApi } from '../cash-book/cash-book-api'
import type { CashBookSummary } from '../cash-book/cash-book-api'
import { supplierDebtApi } from '../inbound-receipts/inbound-receipt-api'
import { reportApi } from '../reports/report-api'
import type { Dashboard } from '../reports/report-api'
import { customerDebtApi } from '../sales-orders/sales-order-api'

// Tải lười: thư viện biểu đồ (~110 KB gzip) chỉ tải khi mở Tổng quan, không làm nặng các trang khác
const SalesChart = lazy(() => import('./SalesChart').then((module) => ({ default: module.SalesChart })))

/** Tổng nợ + số đối tác còn nợ — cộng từ danh sách công nợ sẵn có (tiền VND nguyên, cộng số nguyên không lệch). */
interface DebtTotal {
  amount: number
  count: number
}

const sumDebts = (debts: { outstandingAmount: number }[]): DebtTotal => ({
  amount: debts.reduce((total, debt) => total + debt.outstandingAmount, 0),
  count: debts.length,
})

const money = (value: number | string | undefined) => formatVnd(Number(value ?? 0))

/** Một thẻ số liệu; `to` = bấm vào mở trang chi tiết (giống ô tổng quan của KiotViet). */
function StatCard({ title, value, footer, to }: { title: string; value: ReactNode; footer?: ReactNode; to?: string }) {
  const body = (
    <Card size="small" hoverable={to !== undefined} style={{ height: '100%' }}>
      <Statistic title={title} valueRender={() => value} />
      {footer && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {footer}
        </Typography.Text>
      )}
    </Card>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

/**
 * Trang tổng quan (BR-RPT-01..03) — trang đầu sau đăng nhập. Bán hàng và tồn kho mọi vai trò đều thấy;
 * công nợ khách / NCC và sổ quỹ chỉ hiện với vai trò có quyền xem số đó (gọi đúng API đã phân quyền của module).
 */
export function DashboardPage() {
  const canSeeCustomerDebts = useCan('debts')
  const canSeeSupplierDebts = useCan('inbound')
  const canSeeCash = useCan('cashbook')
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [customerDebt, setCustomerDebt] = useState<DebtTotal | null>(null)
  const [supplierDebt, setSupplierDebt] = useState<DebtTotal | null>(null)
  const [cash, setCash] = useState<CashBookSummary | null>(null)

  useEffect(() => {
    let ignore = false
    reportApi
      .dashboard()
      .then((data) => {
        if (!ignore) {
          setDashboard(data)
          setError(null)
        }
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được số liệu tổng quan')
      })
    // Thẻ phụ: lỗi riêng từng thẻ không làm hỏng cả trang → bỏ qua lỗi, thẻ không hiện
    if (canSeeCustomerDebts) customerDebtApi.list().then((debts) => !ignore && setCustomerDebt(sumDebts(debts))).catch(() => {})
    if (canSeeSupplierDebts) supplierDebtApi.list().then((debts) => !ignore && setSupplierDebt(sumDebts(debts))).catch(() => {})
    if (canSeeCash) cashBookApi.summary().then((summary) => !ignore && setCash(summary)).catch(() => {})
    return () => {
      ignore = true
    }
  }, [reloadCount, canSeeCustomerDebts, canSeeSupplierDebts, canSeeCash])

  if (error && !dashboard) {
    return (
      <>
        <PageHeader title="Tổng quan" />
        <Alert
          type="error"
          showIcon
          title={error}
          action={
            <Button size="small" onClick={() => setReloadCount((count) => count + 1)}>
              Thử lại
            </Button>
          }
        />
      </>
    )
  }

  if (!dashboard) {
    return (
      <>
        <PageHeader title="Tổng quan" />
        <Skeleton active paragraph={{ rows: 8 }} />
      </>
    )
  }

  const { sales, stock } = dashboard
  const showMoneyRow = customerDebt || supplierDebt || cash?.configured

  return (
    <div style={{ height: '100%', overflowY: 'auto', overflowX: 'hidden' }}>
      <PageHeader title="Tổng quan" description={`Số liệu đến hôm nay, ${formatDate(dashboard.today)}`} />
      <Flex vertical gap={16}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={6}>
            <StatCard
              title="Doanh số hôm nay"
              value={money(sales.today.revenue)}
              footer={`${sales.today.orderCount} phiếu · ${formatTons(sales.today.tons)}`}
              to="/sales-orders"
            />
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <StatCard
              title="Doanh số tháng này"
              value={money(sales.thisMonth.revenue)}
              footer={`${sales.thisMonth.orderCount} phiếu · ${formatTons(sales.thisMonth.tons)}`}
              to="/sales-orders"
            />
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <StatCard
              title="Tổng tồn kho"
              value={formatTons(stock.totalTons)}
              footer={`${formatQuantity(stock.productCount)} sản phẩm đang kinh doanh`}
              to="/reports"
            />
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <StatCard
              title="Phiếu chờ xử lý"
              value={`${sales.draftCount} phiếu bán nháp`}
              footer={
                canSeeSupplierDebts
                  ? `${stock.draftInboundCount} phiếu nhập nháp · hàng đang về ${formatTons(stock.incomingTons)}`
                  : `Hàng đang về ${formatTons(stock.incomingTons)}`
              }
              to="/sales-orders"
            />
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={16}>
            <Card size="small" title="Doanh số 30 ngày qua">
              <Suspense fallback={<Skeleton.Node active style={{ width: '100%', height: 280 }} />}>
                <SalesChart days={sales.last30Days} />
              </Suspense>
            </Card>
          </Col>
          <Col xs={24} xl={8}>
            <Card size="small" title="Cảnh báo tồn kho" style={{ height: '100%' }}>
              <Flex vertical gap={12}>
                <Link to="/products?stockStatus=OUT&active=true">
                  <Flex justify="space-between" align="center">
                    <Typography.Text>Hết hàng</Typography.Text>
                    <Typography.Text strong type={stock.outOfStockCount > 0 ? 'danger' : 'secondary'}>
                      {stock.outOfStockCount} SP
                    </Typography.Text>
                  </Flex>
                </Link>
                <Link to="/products?stockStatus=LOW&active=true">
                  <Flex justify="space-between" align="center">
                    <Typography.Text>Sắp hết (dưới mức tối thiểu)</Typography.Text>
                    <Typography.Text strong type={stock.lowStockCount > 0 ? 'warning' : 'secondary'}>
                      {stock.lowStockCount} SP
                    </Typography.Text>
                  </Flex>
                </Link>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Bấm để mở danh sách sản phẩm đã lọc. Mức tối thiểu đặt trong form sửa sản phẩm.
                </Typography.Text>
              </Flex>
            </Card>
          </Col>
        </Row>

        {showMoneyRow && (
          <Row gutter={[16, 16]}>
            {customerDebt && (
              <Col xs={24} sm={12} xl={6}>
                <StatCard
                  title="Khách còn nợ"
                  value={money(customerDebt.amount)}
                  footer={`${customerDebt.count} khách`}
                  to="/customer-debts"
                />
              </Col>
            )}
            {supplierDebt && (
              <Col xs={24} sm={12} xl={6}>
                <StatCard
                  title="Còn nợ nhà cung cấp"
                  value={money(supplierDebt.amount)}
                  footer={`${supplierDebt.count} nhà cung cấp`}
                  to="/supplier-debts"
                />
              </Col>
            )}
            {cash?.configured && cash.cash && cash.bank && (
              <>
                <Col xs={24} sm={12} xl={6}>
                  <StatCard
                    title="Tiền mặt"
                    value={money(cash.cash.balance)}
                    footer={`Hôm nay thu ${money(cash.cash.todayIn)} · chi ${money(cash.cash.todayOut)}`}
                    to="/cash-book"
                  />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                  <StatCard
                    title="Tài khoản ngân hàng"
                    value={money(cash.bank.balance)}
                    footer={`Hôm nay thu ${money(cash.bank.todayIn)} · chi ${money(cash.bank.todayOut)}`}
                    to="/cash-book"
                  />
                </Col>
              </>
            )}
          </Row>
        )}
      </Flex>
    </div>
  )
}
