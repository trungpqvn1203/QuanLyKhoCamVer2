import { MinusOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, TagsOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Descriptions, Flex, Input, Segmented, Select, Statistic, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeText } from '../../components/ui/CodeLink'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { EMPTY_TEXT, formatDate, orEmpty, todayIso } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import {
  CASH_ACCOUNT_LABELS,
  CASH_SOURCE_LABELS,
  cashBookApi,
  cashCategoryApi,
  cashVoucherApi,
  signedAmountParts,
} from './cash-book-api'
import type {
  AccountSummary,
  CashAccount,
  CashBookOpening,
  CashBookSummary,
  CashCategory,
  CashDirection,
  CashEntry,
  CashEntrySource,
  CashLedger,
} from './cash-book-api'
import { CategoryModal } from './CategoryModal'
import { VoucherModal } from './VoucherModal'

/** Trang đặt / sửa đầu kỳ (nhóm menu Số dư đầu kỳ) */
const OPENING_CASH_PATH = '/opening/cash'

const SOURCE_OPTIONS = (Object.keys(CASH_SOURCE_LABELS) as CashEntrySource[]).map((source) => ({
  value: source,
  label: CASH_SOURCE_LABELS[source],
}))

const SOURCE_COLORS: Record<CashEntrySource, string> = {
  SALE_PAYMENT: 'green',
  SUPPLIER_PAYMENT: 'orange',
  PAYROLL_PAYMENT: 'purple',
  EMPLOYEE_ADVANCE: 'magenta',
  CASH_VOUCHER: 'blue',
}

/** Đầu tháng hiện tại (giờ VN) — mặc định của khoảng xem, giống backend. */
const firstOfMonth = () => `${todayIso().slice(0, 7)}-01`

/** Một thẻ số dư: đỏ khi âm (BR-CASH-06 — chỉ cảnh báo, không chặn khoản chi). */
function BalanceCard({ summary }: { summary?: AccountSummary }) {
  if (!summary) return null
  return (
    <Card size="small" style={{ minWidth: 260 }}>
      <Statistic
        title={`${CASH_ACCOUNT_LABELS[summary.account]} hiện có`}
        value={formatVnd(summary.balance)}
        styles={{ content: summary.negative ? { color: 'var(--ant-color-error)' } : undefined }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Hôm nay: thu {formatVnd(summary.todayIn)} · chi {formatVnd(summary.todayOut)}
      </Typography.Text>
      {summary.negative && (
        <Typography.Paragraph type="danger" style={{ fontSize: 12, margin: 0 }}>
          Số dư âm — kiểm tra lại đầu kỳ hoặc khoản thu chưa ghi
        </Typography.Paragraph>
      )}
    </Card>
  )
}

/**
 * Sổ quỹ (BR-CASH-01..07): số dư tiền mặt và tài khoản, sổ theo khoảng ngày (gộp thu tiền khách, trả NCC, trả lương,
 * tạm ứng, phiếu thu/chi khác) với số dư lũy kế từng dòng; lập phiếu thu/chi khác, hoàn phiếu ghi nhầm.
 * Khoản của các module khác chỉ xem ở đây — sửa/hoàn ở đúng màn của nó (phiếu bán, phiếu nhập, bảng lương...).
 * Đặt / sửa đầu kỳ nằm ở trang Quỹ đầu kỳ (CashOpeningPage) — việc một lần, tách khỏi thao tác hằng ngày.
 */
export function CashBookPage() {
  const { message, notification } = App.useApp()
  const navigate = useNavigate()
  const [summary, setSummary] = useState<CashBookSummary | null>(null)
  const [opening, setOpening] = useState<CashBookOpening | null>(null)
  const [categories, setCategories] = useState<CashCategory[]>([])
  const [ledger, setLedger] = useState<CashLedger | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [account, setAccount] = useState<CashAccount>('CASH')
  const [from, setFrom] = useState(firstOfMonth)
  const [to, setTo] = useState(todayIso)
  const [source, setSource] = useState<CashEntrySource | undefined>(undefined)
  const [version, setVersion] = useState(0)
  const [voucherDirection, setVoucherDirection] = useState<CashDirection | null>(null)
  const [categoryOpen, setCategoryOpen] = useState(false)
  const [reversing, setReversing] = useState<CashEntry | null>(null)

  const reload = () => setVersion((v) => v + 1)

  // Tổng quan + đầu kỳ + danh mục: tải lại sau mỗi thay đổi (version)
  useEffect(() => {
    let ignore = false
    Promise.all([cashBookApi.summary(), cashBookApi.opening(), cashCategoryApi.list()])
      .then(([loadedSummary, loadedOpening, loadedCategories]) => {
        if (ignore) return
        setSummary(loadedSummary)
        setOpening(loadedOpening)
        setCategories(loadedCategories)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được sổ quỹ')
      })
    return () => {
      ignore = true
    }
  }, [version])

  // Sổ quỹ theo bộ lọc — chỉ khi đã đặt đầu kỳ (chưa đặt thì backend trả 409)
  const configured = summary?.configured ?? false
  useEffect(() => {
    if (!configured) return
    let ignore = false
    cashBookApi
      .ledger({ account, from: from || undefined, to: to || undefined, source })
      .then((loaded) => {
        if (!ignore) setLedger(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setLedger(null)
          message.error(err instanceof ApiError ? err.message : 'Không tải được sổ quỹ')
        }
      })
    return () => {
      ignore = true
    }
  }, [configured, account, from, to, source, version, message])

  // Ném lỗi ra để ReasonModal giữ hộp mở và hiện lỗi (ví dụ phiếu đã được hoàn ở tab khác)
  const submitReverse = async (reason: string | undefined) => {
    if (!reversing) return
    const reversal = await cashVoucherApi.reverse(reversing.sourceId, reason ?? '')
    notification.success({ title: 'Đã lập phiếu hoàn', description: `${reversal.code} · ${formatVnd(reversal.amount)}` })
    reload()
  }

  const columns: TableProps<CashEntry>['columns'] = [
    { title: 'Ngày', dataIndex: 'entryDate', width: 105, render: (date: string) => formatDate(date) },
    {
      title: 'Nguồn',
      dataIndex: 'source',
      width: 130,
      render: (value: CashEntrySource, entry) => (
        <Flex vertical gap={2}>
          <Tag color={SOURCE_COLORS[value]} variant="filled" style={{ marginInlineEnd: 0, width: 'fit-content' }}>
            {CASH_SOURCE_LABELS[value]}
          </Tag>
          {entry.reversal && <Typography.Text type="warning" style={{ fontSize: 11 }}>Dòng hoàn</Typography.Text>}
          {entry.reversed && <Typography.Text type="secondary" style={{ fontSize: 11 }}>Đã hoàn</Typography.Text>}
        </Flex>
      ),
    },
    {
      title: 'Chứng từ',
      dataIndex: 'reference',
      width: 140,
      render: (reference?: string) => (reference ? <CodeText code={reference} /> : EMPTY_TEXT),
    },
    { title: 'Đối tượng', dataIndex: 'counterparty', width: 180, render: (value?: string) => orEmpty(value) },
    {
      title: 'Nội dung',
      key: 'content',
      render: (_, entry) => [entry.category, entry.note].filter(Boolean).join(' · ') || EMPTY_TEXT,
    },
    {
      title: 'Thu',
      key: 'income',
      width: 130,
      align: 'right',
      render: (_, entry) => {
        const { income } = signedAmountParts(entry.amount)
        return income == null ? '' : <Typography.Text delete={entry.reversed} type="success">{formatVnd(income)}</Typography.Text>
      },
    },
    {
      title: 'Chi',
      key: 'expense',
      width: 130,
      align: 'right',
      render: (_, entry) => {
        const { expense } = signedAmountParts(entry.amount)
        return expense == null ? '' : <Typography.Text delete={entry.reversed} type="danger">{formatVnd(expense)}</Typography.Text>
      },
    },
    {
      title: 'Số dư',
      dataIndex: 'balance',
      width: 140,
      align: 'right',
      render: (balance?: number) =>
        balance == null ? EMPTY_TEXT : <Typography.Text strong type={balance < 0 ? 'danger' : undefined}>{formatVnd(balance)}</Typography.Text>,
    },
    {
      title: '',
      key: 'actions',
      width: 80,
      align: 'center',
      render: (_, entry) =>
        entry.source === 'CASH_VOUCHER' &&
        !entry.reversal &&
        !entry.reversed && (
          <Button size="small" type="link" danger icon={<RollbackOutlined />} onClick={() => setReversing(entry)}>
            Hoàn
          </Button>
        ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Sổ quỹ"
        description="Tiền mặt và tài khoản: mọi khoản thu tiền khách, trả NCC, trả lương, tạm ứng và phiếu thu/chi khác"
        actions={
          <Flex gap={8} wrap>
            <Button icon={<ReloadOutlined />} onClick={reload}>
              Tải lại
            </Button>
            <Button icon={<TagsOutlined />} onClick={() => setCategoryOpen(true)}>
              Danh mục thu/chi
            </Button>
            <Button icon={<MinusOutlined />} disabled={!configured} onClick={() => setVoucherDirection('OUT')}>
              Lập phiếu chi
            </Button>
            <Button type="primary" icon={<PlusOutlined />} disabled={!configured} onClick={() => setVoucherDirection('IN')}>
              Lập phiếu thu
            </Button>
          </Flex>
        }
      />
      {error && (
        <Alert type="error" showIcon title={error} action={<Button size="small" onClick={reload}>Thử lại</Button>} style={{ marginBottom: 12 }} />
      )}
      {summary && !summary.configured && (
        <Alert
          type="info"
          showIcon
          title="Sổ quỹ chưa có đầu kỳ"
          description="Vào Số dư đầu kỳ → Quỹ đầu kỳ, nhập ngày bắt đầu cùng số tiền mặt và số dư tài khoản thực tế tại ngày đó. Sau đó sổ quỹ tự gộp mọi khoản thu/chi từ ngày đó trở đi."
          action={
            <Button type="primary" size="small" onClick={() => navigate(OPENING_CASH_PATH)}>
              Đặt đầu kỳ
            </Button>
          }
          style={{ marginBottom: 12 }}
        />
      )}
      {configured && (
        <>
          {opening?.startDate && (
            // Chỉ đọc: đặt / sửa đầu kỳ ở nhóm Số dư đầu kỳ, tách khỏi thao tác hằng ngày
            <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
              Đầu kỳ {formatDate(opening.startDate)} · Tiền mặt {formatVnd(opening.openingCash ?? 0)} · Tài khoản{' '}
              {formatVnd(opening.openingBank ?? 0)} · <Link to={OPENING_CASH_PATH}>Xem / sửa ở Quỹ đầu kỳ</Link>
            </Typography.Paragraph>
          )}
          <Flex gap={16} wrap style={{ marginBottom: 16 }}>
            <BalanceCard summary={summary?.cash} />
            <BalanceCard summary={summary?.bank} />
          </Flex>
          <Flex gap={12} wrap align="center" style={{ marginBottom: 12 }}>
            <Segmented<CashAccount>
              value={account}
              onChange={setAccount}
              options={[
                { value: 'CASH', label: CASH_ACCOUNT_LABELS.CASH },
                { value: 'BANK', label: CASH_ACCOUNT_LABELS.BANK },
              ]}
            />
            <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} style={{ width: 150 }} aria-label="Từ ngày" />
            <Typography.Text type="secondary">đến</Typography.Text>
            <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} style={{ width: 150 }} aria-label="Đến ngày" />
            <Select
              allowClear
              placeholder="Tất cả nguồn"
              value={source}
              onChange={setSource}
              options={SOURCE_OPTIONS}
              style={{ width: 170 }}
            />
          </Flex>
          {ledger && (
            <Descriptions
              size="small"
              bordered
              column={{ xs: 1, md: 4 }}
              style={{ marginBottom: 12 }}
              items={[
                { key: 'opening', label: `Đầu ngày ${formatDate(ledger.from)}`, children: formatVnd(ledger.openingBalance) },
                { key: 'in', label: ledger.source ? 'Thu (nguồn đang lọc)' : 'Tổng thu', children: <Typography.Text type="success">{formatVnd(ledger.totalIn)}</Typography.Text> },
                { key: 'out', label: ledger.source ? 'Chi (nguồn đang lọc)' : 'Tổng chi', children: <Typography.Text type="danger">{formatVnd(ledger.totalOut)}</Typography.Text> },
                {
                  key: 'closing',
                  label: `Cuối ngày ${formatDate(ledger.to)}`,
                  children: <Typography.Text strong type={ledger.negative ? 'danger' : undefined}>{formatVnd(ledger.closingBalance)}</Typography.Text>,
                },
              ]}
            />
          )}
          <Table<CashEntry>
            size="small"
            rowKey={(entry) => `${entry.source}-${entry.sourceId}`}
            loading={ledger === null}
            columns={columns}
            dataSource={ledger?.entries ?? []}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: 'Không có khoản thu/chi nào trong khoảng này' }}
          />
        </>
      )}

      {voucherDirection && opening?.startDate && (
        <VoucherModal
          open
          direction={voucherDirection}
          categories={categories}
          startDate={opening.startDate}
          onSaved={(voucher) => {
            notification.success({ title: voucher.direction === 'IN' ? 'Đã lập phiếu thu' : 'Đã lập phiếu chi', description: `${voucher.code} · ${formatVnd(voucher.amount)}` })
            reload()
          }}
          onClose={() => setVoucherDirection(null)}
        />
      )}
      {categoryOpen && (
        <CategoryModal open categories={categories} onChanged={reload} onClose={() => setCategoryOpen(false)} />
      )}
      {reversing && (
        <ReasonModal
          open
          key={reversing.sourceId}
          required
          title={`Hoàn phiếu ${reversing.reference ?? ''}?`}
          description={`Lập phiếu hoàn ${formatVnd(Math.abs(reversing.amount))} ngày hôm nay, ngược chiều phiếu gốc. Phiếu gốc được giữ lại trong sổ, gạch ngang.`}
          submitText="Lập phiếu hoàn"
          onSubmit={submitReverse}
          onClose={() => setReversing(null)}
        />
      )}
    </>
  )
}
