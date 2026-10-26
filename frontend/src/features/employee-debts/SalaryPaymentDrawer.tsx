import { DollarOutlined } from '@ant-design/icons'
import { Alert, App, Button, Descriptions, Drawer, Flex, Skeleton } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { formatMonth } from '../../utils/month'
import { PAYROLL_PAYMENT_METHOD_LABELS, payrollPaymentApi } from '../payroll/payroll-api'
import type { PayrollItemPayments, PayrollPayment } from '../payroll/payroll-api'
import { PaymentModal } from '../sales-orders/PaymentModal'
import { PaymentSection } from '../sales-orders/PaymentSection'
import type { PaymentInput } from '../sales-orders/sales-order-api'

interface SalaryPaymentDrawerProps {
  /** "YYYY-MM" — tháng lương đã chốt */
  month: string
  employeeId: number
  onClose: () => void
  /** Lương hoặc tạm ứng vừa đổi ở backend → trang gọi tải lại */
  onChanged: () => void
}

const DRAWER_WIDTH = 760

/**
 * Trả lương cho một nhân viên trong một tháng đã chốt (BR-EPAY-01, 02, 04): tổng lương, đã trả, còn nợ, dư tạm ứng,
 * lịch sử trả (bất biến, hoàn khoản ghi nhầm bằng dòng hoàn). Dùng chung cho trang Bảng lương và trang Công nợ nhân viên.
 * Nút chỉ là gợi ý — quyết định cuối cùng ở backend (khóa tháng, nhân viên, dòng lương rồi mới kiểm tra), nên số liệu
 * đổi bởi người khác trong lúc đang mở vẫn được chặn đúng, lỗi hiện ngay trong hộp thoại.
 */
export function SalaryPaymentDrawer({ month, employeeId, onClose, onChanged }: SalaryPaymentDrawerProps) {
  const { notification } = App.useApp()
  const [data, setData] = useState<PayrollItemPayments | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  const [paying, setPaying] = useState(false)
  const [reversing, setReversing] = useState<PayrollPayment | null>(null)

  useEffect(() => {
    let ignore = false
    payrollPaymentApi
      .get(month, employeeId)
      .then((loaded) => {
        if (ignore) return
        setData(loaded)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được tình trạng trả lương')
      })
    return () => {
      ignore = true
    }
  }, [month, employeeId, reloadCount])

  // Ném lỗi ra để PaymentModal giữ hộp mở và hiện lỗi (ví dụ trừ tạm ứng vượt dư do người khác vừa trừ)
  const submitPayment = async (input: PaymentInput) => {
    const updated = await payrollPaymentApi.pay(month, employeeId, input)
    setData(updated)
    notification.success({
      title: 'Đã ghi nhận trả lương',
      description: `${updated.employeeName} · tháng ${formatMonth(month)} · còn nợ ${formatVnd(updated.outstandingAmount)}`,
    })
    onChanged()
  }

  const submitReverse = async (reason: string | undefined) => {
    if (!reversing) return
    const updated = await payrollPaymentApi.reverse(month, employeeId, reversing.id, reason ?? '')
    setData(updated)
    notification.success({
      title: 'Đã hoàn khoản trả lương',
      description: `${updated.employeeName} · tháng ${formatMonth(month)} · còn nợ ${formatVnd(updated.outstandingAmount)}`,
    })
    onChanged()
  }

  const canPay = data != null && data.tracksDebt && data.outstandingAmount > 0

  return (
    <>
      <Drawer
        open
        size={DRAWER_WIDTH}
        title={data ? `Trả lương · ${data.employeeCode} ${data.employeeName} · ${formatMonth(month)}` : 'Trả lương'}
        onClose={onClose}
        destroyOnHidden
        footer={
          <Flex justify="space-between" gap={8}>
            <span />
            <Flex gap={8}>
              <Button onClick={onClose}>Đóng</Button>
              {canPay && (
                <Button type="primary" icon={<DollarOutlined />} onClick={() => setPaying(true)}>
                  Trả lương
                </Button>
              )}
            </Flex>
          </Flex>
        }
      >
        {error ? (
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
        ) : data === null ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : (
          <Flex vertical gap={16}>
            {!data.tracksDebt && (
              <Alert
                type="info"
                showIcon
                title="Bảng lương chốt trước khi có công nợ nhân viên — coi như đã trả đủ, không ghi trả lương"
              />
            )}
            <Descriptions
              bordered
              size="small"
              column={{ xs: 1, md: 2 }}
              items={[
                { key: 'employee', label: 'Nhân viên', children: `${data.employeeCode} · ${data.employeeName}` },
                { key: 'month', label: 'Tháng lương', children: formatMonth(data.month) },
                { key: 'advance', label: 'Dư tạm ứng hiện có', children: formatVnd(data.advanceBalance) },
              ]}
            />
            <PaymentSection<PayrollPayment>
              direction="pay"
              totalLabel="Tổng lương"
              methodLabels={PAYROLL_PAYMENT_METHOD_LABELS}
              totalAmount={data.totalAmount}
              paidAmount={data.paidAmount}
              outstandingAmount={data.outstandingAmount}
              payments={data.payments}
              onReversePayment={data.tracksDebt ? setReversing : undefined}
            />
          </Flex>
        )}
      </Drawer>

      {data && paying && (
        <PaymentModal
          open
          direction="pay"
          document={{
            code: `lương ${formatMonth(month)} · ${data.employeeCode}`,
            counterpartyName: data.employeeName,
            outstandingAmount: data.outstandingAmount,
          }}
          advanceBalance={data.advanceBalance}
          onSubmit={submitPayment}
          onClose={() => setPaying(false)}
        />
      )}
      {reversing && (
        <ReasonModal
          open
          key={reversing.id}
          required
          title={`Hoàn khoản trả ${formatVnd(reversing.amount)}?`}
          description={
            `${PAYROLL_PAYMENT_METHOD_LABELS[reversing.method]} ngày ${formatDate(reversing.paymentDate)}. ` +
            'Khoản trả gốc được giữ lại trong lịch sử, kèm một dòng hoàn trỏ về nó; số lương còn nợ tăng lại tương ứng' +
            (reversing.method === 'ADVANCE_DEDUCTION' ? ', và dư tạm ứng của nhân viên được cộng lại.' : '.')
          }
          submitText="Hoàn khoản trả"
          onSubmit={submitReverse}
          onClose={() => setReversing(null)}
        />
      )}
    </>
  )
}
