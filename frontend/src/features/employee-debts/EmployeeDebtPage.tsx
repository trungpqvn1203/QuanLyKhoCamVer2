import { PlusOutlined, ReloadOutlined, RollbackOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Flex, Statistic, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeText } from '../../components/ui/CodeLink'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { EMPTY_TEXT, formatDate, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { formatMonth } from '../../utils/month'
import { employeeApi } from '../employees/employee-api'
import { PAYMENT_METHOD_LABELS } from '../sales-orders/sales-order-api'
import { AdvanceModal } from './AdvanceModal'
import type { AdvanceTarget } from './AdvanceModal'
import { advanceApi, employeeDebtApi } from './employee-debt-api'
import type { AdvanceLedger, EmployeeAdvance, EmployeeDebt, UnpaidMonth } from './employee-debt-api'
import { SalaryPaymentDrawer } from './SalaryPaymentDrawer'

/** Lịch sử tạm ứng của một nhân viên — tải khi mở rộng dòng; `version` đổi thì tải lại. Hoàn khoản ứng ghi nhầm tại đây. */
function AdvanceHistory({ employeeId, version, onChanged }: { employeeId: number; version: number; onChanged: () => void }) {
  const { notification } = App.useApp()
  const [ledger, setLedger] = useState<AdvanceLedger | null>(null)
  const [reversing, setReversing] = useState<EmployeeAdvance | null>(null)

  useEffect(() => {
    let ignore = false
    advanceApi
      .get(employeeId)
      .then((loaded) => {
        if (!ignore) setLedger(loaded)
      })
      .catch(() => {
        if (!ignore) setLedger(null)
      })
    return () => {
      ignore = true
    }
  }, [employeeId, version])

  // Ném lỗi ra để ReasonModal giữ hộp mở và hiện lỗi, ví dụ "số dư tạm ứng không đủ" khi một phần đã trừ vào lương
  const submitReverse = async (reason: string | undefined) => {
    if (!reversing) return
    const updated = await advanceApi.reverse(employeeId, reversing.id, reason ?? '')
    setLedger(updated)
    notification.success({
      title: 'Đã hoàn khoản ứng',
      description: `${updated.employeeName} · dư tạm ứng còn ${formatVnd(updated.advanceBalance)}`,
    })
    onChanged()
  }

  const columns: TableProps<EmployeeAdvance>['columns'] = [
    { title: 'Ngày', dataIndex: 'advanceDate', width: 110, render: (date: string) => formatDate(date) },
    {
      title: 'Loại',
      dataIndex: 'type',
      width: 110,
      render: (type: EmployeeAdvance['type'], advance) =>
        type === 'REVERSAL' ? (
          <Tag color="warning" variant="filled">Hoàn ứng</Tag>
        ) : advance.reversed ? (
          <Tag variant="filled">Đã hoàn</Tag>
        ) : (
          <Tag color="processing" variant="filled">Ứng tiền</Tag>
        ),
    },
    { title: 'Hình thức', dataIndex: 'method', width: 130, render: (method: EmployeeAdvance['method']) => PAYMENT_METHOD_LABELS[method] },
    {
      title: 'Số tiền',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (amount: number, advance) =>
        advance.type === 'REVERSAL' ? (
          <Typography.Text type="warning">−{formatVnd(amount)}</Typography.Text>
        ) : (
          <Typography.Text delete={advance.reversed}>{formatVnd(amount)}</Typography.Text>
        ),
    },
    { title: 'Ghi chú / lý do', dataIndex: 'note', render: (note?: string) => orEmpty(note) },
    {
      title: '',
      key: 'actions',
      width: 90,
      align: 'center',
      render: (_, advance) =>
        advance.type === 'ADVANCE' &&
        !advance.reversed && (
          <Button size="small" type="link" danger icon={<RollbackOutlined />} onClick={() => setReversing(advance)}>
            Hoàn
          </Button>
        ),
    },
  ]

  return (
    <>
      <Table<EmployeeAdvance>
        size="small"
        rowKey="id"
        loading={ledger === null}
        columns={columns}
        dataSource={ledger?.advances ?? []}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Chưa ứng khoản nào' }}
      />
      {reversing && (
        <ReasonModal
          open
          key={reversing.id}
          required
          title={`Hoàn khoản ứng ${formatVnd(reversing.amount)}?`}
          description={
            `${PAYMENT_METHOD_LABELS[reversing.method]} ngày ${formatDate(reversing.advanceDate)}. ` +
            'Khoản ứng gốc được giữ lại trong lịch sử, kèm một dòng hoàn; dư tạm ứng giảm tương ứng. ' +
            'Nếu một phần đã trừ vào lương, hãy hoàn khoản trừ đó trong bảng lương trước.'
          }
          submitText="Hoàn khoản ứng"
          onSubmit={submitReverse}
          onClose={() => setReversing(null)}
        />
      )}
    </>
  )
}

/**
 * Công nợ nhân viên (BR-EPAY-01, 03): lương công ty còn nợ + tạm ứng nhân viên còn nợ. Mở rộng một dòng để trả lương
 * theo từng tháng và xem / hoàn lịch sử tạm ứng. "Ứng tiền" ở đầu trang để ứng cho bất kỳ nhân viên đang làm nào.
 */
export function EmployeeDebtPage() {
  const { message } = App.useApp()
  const [debts, setDebts] = useState<EmployeeDebt[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [activeEmployees, setActiveEmployees] = useState<AdvanceTarget[]>([])
  const [advancing, setAdvancing] = useState<{ employee?: AdvanceTarget } | null>(null)
  const [salaryTarget, setSalaryTarget] = useState<{ month: string; employeeId: number } | null>(null)

  // version tăng (Tải lại, Thử lại, sau khi trả lương / ứng tiền) → tải lại; lỗi cũ xóa khi có kết quả mới
  useEffect(() => {
    let ignore = false
    employeeDebtApi
      .list()
      .then((loaded) => {
        if (ignore) return
        setDebts(loaded)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được công nợ nhân viên')
      })
    return () => {
      ignore = true
    }
  }, [version])

  // Danh sách chọn người nhận tạm ứng: chỉ nhân viên đang làm việc (BR-EPAY-A01). Tối đa 100 (giới hạn backend) — đủ cho MVP
  useEffect(() => {
    employeeApi
      .list({ page: 0, size: 100 }, { active: true })
      .then((result) => setActiveEmployees(result.items.map(({ id, code, name }) => ({ id, code, name }))))
      .catch(() => setActiveEmployees([]))
  }, [])

  const reload = () => setVersion((v) => v + 1)

  const totalUnpaid = (debts ?? []).reduce((sum, debt) => sum + debt.unpaidSalaryAmount, 0)
  const totalAdvance = (debts ?? []).reduce((sum, debt) => sum + debt.advanceBalance, 0)

  const monthColumns: TableProps<UnpaidMonth>['columns'] = [
    { title: 'Tháng lương', dataIndex: 'month', width: 120, render: (month: string) => formatMonth(month) },
    { title: 'Tổng lương', dataIndex: 'totalAmount', width: 150, align: 'right', render: (v: number) => formatVnd(v) },
    { title: 'Đã trả', dataIndex: 'paidAmount', width: 150, align: 'right', render: (v: number) => formatVnd(v) },
    {
      title: 'Còn nợ',
      dataIndex: 'outstandingAmount',
      width: 150,
      align: 'right',
      render: (v: number) => <Typography.Text type="danger">{formatVnd(v)}</Typography.Text>,
    },
  ]

  const columns: TableProps<EmployeeDebt>['columns'] = [
    { title: 'Mã NV', dataIndex: 'employeeCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    {
      title: 'Nhân viên',
      dataIndex: 'employeeName',
      render: (name: string, debt) => (
        <>
          <Typography.Text strong>{name}</Typography.Text>
          {!debt.employeeActive && <Typography.Text type="secondary"> (đã nghỉ)</Typography.Text>}
        </>
      ),
    },
    {
      title: 'Lương chưa trả',
      dataIndex: 'unpaidSalaryAmount',
      width: 170,
      align: 'right',
      render: (amount: number) =>
        amount > 0 ? <Typography.Text strong type="danger">{formatVnd(amount)}</Typography.Text> : EMPTY_TEXT,
    },
    {
      title: 'Dư tạm ứng',
      dataIndex: 'advanceBalance',
      width: 150,
      align: 'right',
      render: (amount: number) => (amount > 0 ? formatVnd(amount) : EMPTY_TEXT),
    },
    {
      title: 'Chênh lệch',
      dataIndex: 'netAmount',
      width: 210,
      render: (net: number) =>
        net > 0 ? (
          <Tag color="error" variant="filled">Công ty nợ NV {formatVnd(net)}</Tag>
        ) : net < 0 ? (
          <Tag color="processing" variant="filled">NV đang giữ {formatVnd(-net)}</Tag>
        ) : (
          EMPTY_TEXT
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 110,
      align: 'center',
      render: (_, debt) =>
        debt.employeeActive && (
          <Button
            size="small"
            icon={<PlusOutlined />}
            onClick={() => setAdvancing({ employee: { id: debt.employeeId, code: debt.employeeCode, name: debt.employeeName } })}
          >
            Ứng tiền
          </Button>
        ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Công nợ nhân viên"
        description="Lương công ty còn nợ và tiền đã ứng chưa trừ. Mở rộng một nhân viên để trả lương theo từng tháng và xem tạm ứng"
        actions={
          <Flex gap={8}>
            <Button icon={<ReloadOutlined />} onClick={reload}>
              Tải lại
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setAdvancing({})}>
              Ứng tiền
            </Button>
          </Flex>
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
          <Flex gap={16} wrap style={{ marginBottom: 16 }}>
            <Card size="small" style={{ minWidth: 260 }}>
              <Statistic title="Tổng lương chưa trả" value={formatVnd(totalUnpaid)} loading={debts === null} />
            </Card>
            <Card size="small" style={{ minWidth: 260 }}>
              <Statistic title="Tổng dư tạm ứng" value={formatVnd(totalAdvance)} loading={debts === null} />
            </Card>
          </Flex>
          <Table<EmployeeDebt>
            rowKey="employeeId"
            loading={debts === null}
            columns={columns}
            dataSource={debts ?? []}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: 'Không còn lương chưa trả hay tạm ứng nào' }}
            expandable={{
              expandedRowRender: (debt) => (
                <Flex vertical gap={16}>
                  {debt.unpaidMonths.length > 0 && (
                    <div>
                      <Typography.Text strong>Lương chưa trả</Typography.Text>
                      <Table<UnpaidMonth>
                        size="small"
                        rowKey="month"
                        style={{ marginTop: 8 }}
                        columns={[
                          ...monthColumns,
                          {
                            title: '',
                            key: 'pay',
                            width: 120,
                            align: 'center',
                            render: (_, unpaid) => (
                              <Button
                                size="small"
                                type="primary"
                                ghost
                                onClick={() => setSalaryTarget({ month: unpaid.month, employeeId: debt.employeeId })}
                              >
                                Trả lương
                              </Button>
                            ),
                          },
                        ]}
                        dataSource={debt.unpaidMonths}
                        pagination={false}
                      />
                    </div>
                  )}
                  <div>
                    <Typography.Text strong>Tạm ứng</Typography.Text>
                    <div style={{ marginTop: 8 }}>
                      <AdvanceHistory employeeId={debt.employeeId} version={version} onChanged={reload} />
                    </div>
                  </div>
                </Flex>
              ),
            }}
          />
        </>
      )}
      {advancing && (
        <AdvanceModal
          open
          employee={advancing.employee}
          employees={activeEmployees}
          onSaved={(ledger) => {
            message.success(`Đã ghi tạm ứng · ${ledger.employeeName} · dư tạm ứng ${formatVnd(ledger.advanceBalance)}`)
            reload()
          }}
          onClose={() => setAdvancing(null)}
        />
      )}
      {salaryTarget && (
        <SalaryPaymentDrawer
          key={`${salaryTarget.month}-${salaryTarget.employeeId}`}
          month={salaryTarget.month}
          employeeId={salaryTarget.employeeId}
          onClose={() => setSalaryTarget(null)}
          onChanged={reload}
        />
      )}
    </>
  )
}
