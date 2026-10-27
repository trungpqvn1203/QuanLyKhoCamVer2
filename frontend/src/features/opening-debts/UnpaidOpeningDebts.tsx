import { Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { CodeLink } from '../../components/ui/CodeLink'
import { formatDate, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { OPENING_DEBT_APIS, PARTY_TEXT } from './opening-debt-api'
import type { OpeningDebt, OpeningDebtParty } from './opening-debt-api'
import { OpeningDebtDrawer } from './OpeningDebtDrawer'

interface UnpaidOpeningDebtsProps {
  party: OpeningDebtParty
  partyId: number
  /** Đổi → tải lại (trang công nợ tải lại sau mỗi lần thu/trả) */
  version: number
  onChanged: () => void
}

/**
 * Nợ đầu kỳ còn nợ của một khách / NCC — hiện trong dòng mở rộng của trang Công nợ, phía trên các phiếu còn nợ
 * (BR-ODB-05). Không có thì không hiện gì. Bấm mã để thu / trả ngay.
 */
export function UnpaidOpeningDebts({ party, partyId, version, onChanged }: UnpaidOpeningDebtsProps) {
  const [debts, setDebts] = useState<OpeningDebt[]>([])
  const [openedId, setOpenedId] = useState<number | null>(null)
  const text = PARTY_TEXT[party]

  useEffect(() => {
    let ignore = false
    OPENING_DEBT_APIS[party]
      .list({ page: 0, size: 10 }, { partyId, unpaidOnly: 'true' })
      .then((page) => page.items)
      .catch(() => [] as OpeningDebt[])
      .then((items) => {
        if (!ignore) setDebts(items)
      })
    return () => {
      ignore = true
    }
  }, [party, partyId, version])

  if (debts.length === 0) return null

  const columns: TableProps<OpeningDebt>['columns'] = [
    {
      title: 'Nợ đầu kỳ',
      dataIndex: 'code',
      width: 120,
      render: (code: string, debt) => <CodeLink code={code} onClick={() => setOpenedId(debt.id)} />,
    },
    { title: 'Ngày chốt nợ', dataIndex: 'documentDate', width: 110, render: (date: string) => formatDate(date) },
    { title: 'Ghi chú', dataIndex: 'note', render: (note?: string) => orEmpty(note) },
    { title: 'Nợ đầu kỳ', dataIndex: 'totalAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    { title: text.paidLabel, dataIndex: 'paidAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 140,
      align: 'right',
      render: (v: number) => <Typography.Text type="danger">{formatVnd(v)}</Typography.Text>,
    },
  ]

  return (
    <>
      <Table<OpeningDebt>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={debts}
        pagination={false}
        style={{ marginBottom: 8 }}
      />
      {openedId !== null && (
        <OpeningDebtDrawer party={party} debtId={openedId} onClose={() => setOpenedId(null)} onChanged={onChanged} />
      )}
    </>
  )
}
