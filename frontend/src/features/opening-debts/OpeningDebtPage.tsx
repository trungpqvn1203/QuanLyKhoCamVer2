import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { STATUS_COLUMN_WIDTH } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDate, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { customerApi } from '../customers/customer-api'
import { RECEIPT_STATUS_LABELS } from '../inbound-receipts/inbound-receipt-api'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { supplierApi } from '../suppliers/supplier-api'
import { OPENING_DEBT_APIS, PARTY_TEXT } from './opening-debt-api'
import type { OpeningDebt, OpeningDebtFilters, OpeningDebtParty } from './opening-debt-api'
import { OpeningDebtDrawer } from './OpeningDebtDrawer'
import { OpeningDebtEntryModal } from './OpeningDebtEntryModal'
import type { PartyOption } from './OpeningDebtEntryModal'

/** Tối đa 100 khách / NCC và 100 khoản nợ còn hiệu lực cho ô chọn (giới hạn size backend) — đủ cho MVP. */
const REFERENCE_SIZE = 100

// Phiếu nợ đầu kỳ không có Nháp / Đã hủy: tạo thẳng Hoàn tất, chỉ đảo được
const STATUS_OPTIONS = (['COMPLETED', 'REVERSED'] as ReceiptStatus[]).map((status) => ({
  value: status,
  label: RECEIPT_STATUS_LABELS[status],
}))

/** Khách / NCC đang hoạt động, đánh dấu ai đã có nợ đầu kỳ còn hiệu lực (BR-ODB-03). Tải lại khi `version` đổi. */
function usePartyOptions(party: OpeningDebtParty, version: number): PartyOption[] {
  const [options, setOptions] = useState<PartyOption[]>([])

  useEffect(() => {
    let ignore = false
    const query = { page: 0, size: REFERENCE_SIZE }
    const parties =
      party === 'CUSTOMER'
        ? customerApi.list(query, { active: true }).then((page) => page.items)
        : supplierApi.list(query, { active: true }).then((page) => page.items)
    Promise.all([parties, OPENING_DEBT_APIS[party].list(query, { status: 'COMPLETED' })])
      .then(([items, debts]) => {
        if (ignore) return
        const withDebt = new Set(debts.items.map((debt) => debt.partyId))
        setOptions(items.map((item) => ({ id: item.id, code: item.code, name: item.name, hasDebt: withDebt.has(item.id) })))
      })
      .catch(() => {
        // giữ danh sách rỗng; lỗi thật hiện khi ghi nhận
      })
    return () => {
      ignore = true
    }
  }, [party, version])

  return options
}

/**
 * Công nợ đầu kỳ (BR-ODB-01..09) — một trang cho cả khách (thu) và NCC (trả), khác nhau ở `party`.
 * Ghi nhận theo lô; bấm mã để xem, thu/trả, hoàn khoản thu nhầm hoặc đảo khoản nợ.
 */
export function OpeningDebtPage({ party }: { party: OpeningDebtParty }) {
  const { notification } = App.useApp()
  const text = PARTY_TEXT[party]
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    OPENING_DEBT_APIS[party].list,
  )
  const [version, setVersion] = useState(0)
  const [entering, setEntering] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  const partyOptions = usePartyOptions(party, version)

  const refresh = () => {
    reload()
    setVersion((v) => v + 1)
  }

  const filterFields: FilterField<OpeningDebtFilters>[] = [
    { type: 'keyword', name: 'keyword', placeholder: `Tìm theo mã hoặc tên ${text.party.toLowerCase()}` },
    { type: 'select', name: 'status', placeholder: 'Tất cả trạng thái', options: STATUS_OPTIONS, width: 160 },
    {
      type: 'select',
      name: 'unpaidOnly',
      placeholder: 'Còn nợ / đã hết',
      options: [{ value: 'true', label: 'Chỉ khoản còn nợ' }],
      width: 170,
    },
  ]

  const columns: TableProps<OpeningDebt>['columns'] = [
    {
      title: 'Mã',
      dataIndex: 'code',
      width: 120,
      render: (code: string, debt) => <CodeLink code={code} onClick={() => setOpenedId(debt.id)} />,
    },
    { title: 'Ngày chốt nợ', dataIndex: 'documentDate', width: 120, render: (date: string) => formatDate(date) },
    {
      title: text.party,
      dataIndex: 'partyName',
      render: (name: string, debt) => (
        <>
          <Typography.Text strong>{name}</Typography.Text>{' '}
          <Typography.Text type="secondary">{debt.partyCode}</Typography.Text>
        </>
      ),
    },
    { title: 'Nợ đầu kỳ', dataIndex: 'totalAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    { title: text.paidLabel, dataIndex: 'paidAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 140,
      align: 'right',
      render: (v: number) => (v > 0 ? <Typography.Text type="danger">{formatVnd(v)}</Typography.Text> : formatVnd(v)),
    },
    { title: 'Người ghi nhận', dataIndex: 'createdBy', width: 130, render: (by?: string) => orEmpty(by) },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: STATUS_COLUMN_WIDTH,
      render: (status: ReceiptStatus) => <ReceiptStatusTag status={status} />,
    },
  ]

  return (
    <>
      <PageHeader
        title={text.title}
        description={`Nợ cũ từ trước khi dùng phần mềm, mỗi ${text.party.toLowerCase()} một tổng số. Tính vào trang công nợ và ${party === 'CUSTOMER' ? 'thu' : 'trả'} tiền như phiếu thường`}
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEntering(true)}>
            Ghi nhận nợ đầu kỳ
          </Button>
        }
      />
      <FilterBar<OpeningDebtFilters> fields={filterFields} value={filters} onChange={applyFilters} />
      <DataTable<OpeningDebt>
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
            noun="khoản nợ đầu kỳ"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={() => setEntering(true)}
            createLabel="Ghi nhận nợ đầu kỳ"
          />
        }
      />
      {entering && (
        <OpeningDebtEntryModal
          party={party}
          parties={partyOptions}
          onSaved={(created) => {
            notification.success({
              title: `Đã ghi nhận ${created.length} khoản nợ đầu kỳ`,
              description: `Tổng ${formatVnd(created.reduce((sum, debt) => sum + debt.totalAmount, 0))}`,
            })
            refresh()
          }}
          onClose={() => setEntering(false)}
        />
      )}
      {openedId !== null && (
        <OpeningDebtDrawer party={party} debtId={openedId} onClose={() => setOpenedId(null)} onChanged={refresh} />
      )}
    </>
  )
}
