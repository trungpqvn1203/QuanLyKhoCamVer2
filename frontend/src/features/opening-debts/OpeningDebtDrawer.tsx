import { DollarOutlined, RollbackOutlined } from '@ant-design/icons'
import { Alert, App, Button, Descriptions, Drawer, Flex, Skeleton } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { CodeText } from '../../components/ui/CodeLink'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { formatDate, formatDateTime, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { useCan } from '../auth/auth-context'
import { ReceiptStatusTag } from '../inbound-receipts/ReceiptStatusTag'
import { PaymentModal } from '../sales-orders/PaymentModal'
import { PaymentSection } from '../sales-orders/PaymentSection'
import { PAYMENT_METHOD_LABELS } from '../sales-orders/sales-order-api'
import type { PaymentInput, SalePayment } from '../sales-orders/sales-order-api'
import { OPENING_DEBT_APIS, PARTY_TEXT } from './opening-debt-api'
import type { OpeningDebt, OpeningDebtParty } from './opening-debt-api'

interface OpeningDebtDrawerProps {
  party: OpeningDebtParty
  debtId: number
  onClose: () => void
  /** Khoản vừa thay đổi → trang gọi tải lại */
  onChanged: () => void
}

type ReasonAction = { kind: 'reverse' } | { kind: 'reversePayment'; payment: SalePayment }

/**
 * Chi tiết một khoản nợ đầu kỳ: thu (khách) / trả (NCC) như phiếu bán / phiếu nhập — kể cả hoàn khoản ghi nhầm.
 * Đảo khoản nợ chỉ Quản lý hệ thống (BR-ODB-08), và chỉ khi chưa thu/trả đồng nào (BR-ODB-06).
 */
export function OpeningDebtDrawer({ party, debtId, onClose, onChanged }: OpeningDebtDrawerProps) {
  const { notification } = App.useApp()
  const canManage = useCan('opening')
  const api = OPENING_DEBT_APIS[party]
  const text = PARTY_TEXT[party]
  const [debt, setDebt] = useState<OpeningDebt | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [paying, setPaying] = useState(false)
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null)

  useEffect(() => {
    let ignore = false
    OPENING_DEBT_APIS[party]
      .get(debtId)
      .then((loaded) => {
        if (!ignore) setDebt(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được khoản nợ đầu kỳ')
      })
    return () => {
      ignore = true
    }
  }, [party, debtId])

  const applyUpdate = (updated: OpeningDebt, title: string) => {
    setDebt(updated)
    notification.success({ title, description: `${updated.code} · còn nợ ${formatVnd(updated.outstandingAmount)}` })
    onChanged()
  }

  // Ném lỗi ra để hộp thoại giữ mở và hiện lỗi (ví dụ vượt số còn nợ do người khác vừa thu)
  const submitPayment = async (input: PaymentInput) => {
    if (!debt) return
    applyUpdate(await api.recordPayment(debt.id, input), party === 'CUSTOMER' ? 'Đã thu nợ cũ' : 'Đã trả nợ cũ')
  }

  const submitReason = async (reason: string | undefined) => {
    if (!debt || !reasonAction) return
    if (reasonAction.kind === 'reverse') {
      applyUpdate(await api.reverse(debt.id, reason ?? ''), 'Đã đảo khoản nợ đầu kỳ')
    } else {
      applyUpdate(await api.reversePayment(debt.id, reasonAction.payment.id, reason ?? ''), 'Đã hoàn khoản thanh toán')
    }
  }

  const reasonTexts = (action: ReasonAction, current: OpeningDebt) =>
    action.kind === 'reverse'
      ? {
          title: `Đảo khoản nợ đầu kỳ ${current.code}?`,
          description: `Khoản nợ ${formatVnd(current.totalAmount)} của ${current.partyName} bị vô hiệu, công nợ giảm tương ứng. Đảo xong có thể ghi nhận lại số đúng.`,
          submitText: 'Đảo khoản nợ',
        }
      : {
          title: `Hoàn khoản ${formatVnd(action.payment.amount)}?`,
          description: `${PAYMENT_METHOD_LABELS[action.payment.method]} ngày ${formatDate(action.payment.paymentDate)}. Khoản gốc được giữ lại trong lịch sử, kèm một dòng hoàn; số còn nợ tăng lại tương ứng.`,
          submitText: 'Hoàn khoản',
        }

  const completed = debt?.status === 'COMPLETED'

  const footer = debt && (
    <Flex justify="space-between" gap={8} wrap>
      <Flex gap={8}>
        {completed && canManage && (
          <Button
            danger
            icon={<RollbackOutlined />}
            disabled={debt.paidAmount > 0}
            title={debt.paidAmount > 0 ? 'Đã có khoản thanh toán — hoàn các khoản đó trước' : undefined}
            onClick={() => setReasonAction({ kind: 'reverse' })}
          >
            Đảo khoản nợ
          </Button>
        )}
      </Flex>
      <Flex gap={8}>
        <Button onClick={onClose}>Đóng</Button>
        {completed && debt.outstandingAmount > 0 && (
          <Button type="primary" icon={<DollarOutlined />} onClick={() => setPaying(true)}>
            {party === 'CUSTOMER' ? 'Thu tiền' : 'Trả tiền'}
          </Button>
        )}
      </Flex>
    </Flex>
  )

  return (
    <Drawer
      open
      size={760}
      title={
        debt ? (
          <Flex align="center" gap={8}>
            <span>Nợ đầu kỳ {debt.code}</span>
            <ReceiptStatusTag status={debt.status} />
          </Flex>
        ) : (
          'Nợ đầu kỳ'
        )
      }
      onClose={onClose}
      footer={footer}
      destroyOnHidden
    >
      {loadError ? (
        <Alert type="error" showIcon title={loadError} />
      ) : !debt ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <Flex vertical gap={16}>
          {debt.status === 'REVERSED' && (
            <Alert
              type="warning"
              showIcon
              title={`Đã đảo lúc ${formatDateTime(debt.reversedAt!)} — khoản nợ này không còn tính vào công nợ`}
              description={`Lý do: ${orEmpty(debt.reason)}`}
            />
          )}
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, md: 2 }}
            items={[
              { key: 'code', label: 'Mã', children: <CodeText code={debt.code} /> },
              { key: 'date', label: 'Ngày chốt nợ', children: formatDate(debt.documentDate) },
              {
                key: 'party',
                label: text.party,
                span: 'filled',
                children: `${debt.partyCode} · ${debt.partyName}${debt.partyActive ? '' : ' (ngừng hoạt động)'}`,
              },
              { key: 'note', label: 'Ghi chú', span: 'filled', children: orEmpty(debt.note) },
              { key: 'createdBy', label: 'Người ghi nhận', children: `${orEmpty(debt.createdBy)} · ${formatDateTime(debt.createdAt)}` },
            ]}
          />
          <PaymentSection
            direction={text.direction}
            totalAmount={debt.totalAmount}
            paidAmount={debt.paidAmount}
            outstandingAmount={debt.outstandingAmount}
            payments={debt.payments ?? []}
            totalLabel="Nợ đầu kỳ"
            onReversePayment={completed ? (payment) => setReasonAction({ kind: 'reversePayment', payment }) : undefined}
          />
        </Flex>
      )}

      {debt && paying && (
        <PaymentModal
          open
          direction={text.direction}
          document={{ code: debt.code, counterpartyName: debt.partyName, outstandingAmount: debt.outstandingAmount }}
          onSubmit={submitPayment}
          onClose={() => setPaying(false)}
        />
      )}
      {debt && reasonAction && (
        <ReasonModal
          key={reasonAction.kind === 'reversePayment' ? `payment-${reasonAction.payment.id}` : 'reverse'}
          open
          required
          {...reasonTexts(reasonAction, debt)}
          onSubmit={submitReason}
          onClose={() => setReasonAction(null)}
        />
      )}
    </Drawer>
  )
}
