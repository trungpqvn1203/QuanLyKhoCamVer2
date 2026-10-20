import { CheckCircleOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons'
import { Alert, App, Button, Collapse, Flex, Popconfirm, Space, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { MonthNavigator } from '../../components/ui/MonthNavigator'
import { ReasonModal } from '../../components/ui/ReasonModal'
import { EMPTY_TEXT, formatDateTime } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { currentMonth, formatMonth } from '../../utils/month'
import { payrollApi } from './payroll-api'
import type { PayrollLine, PayrollSheet } from './payroll-api'

const tonsFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })
const unitsFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 })

/** Không hưởng theo cách đó (BR-EMP-02) → "—" thay vì "0 đ" để khỏi tưởng là được trả 0. */
const rateOrEmpty = (rate?: number) => (rate == null ? EMPTY_TEXT : formatVnd(rate))

/**
 * Bảng lương tháng (BR-PAY-01..03).
 * Chưa chốt: tạm tính trực tiếp từ bảng công, đơn giá hiện tại. Chốt: lưu bản chụp + khóa bảng công tháng đó.
 * Hủy chốt: bắt buộc lý do, bản cũ nằm ở "Lịch sử", tháng mở lại để sửa công rồi chốt lại.
 */
export function PayrollPage() {
  const { message, notification } = App.useApp()
  const [month, setMonth] = useState(currentMonth)
  const [reloadCount, setReloadCount] = useState(0)
  const [sheet, setSheet] = useState<PayrollSheet | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [finalizing, setFinalizing] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)

  useEffect(() => {
    let ignore = false
    payrollApi
      .sheet(month)
      .then((result) => {
        if (ignore) return
        setSheet(result)
        setError(null)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (ignore) return
        setError(err instanceof ApiError ? err.message : 'Không tải được bảng lương')
        setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [month, reloadCount])

  const goTo = (next: string) => {
    setLoading(true)
    setMonth(next)
  }

  const reload = () => {
    setLoading(true)
    setReloadCount((count) => count + 1)
  }

  const finalize = async () => {
    setFinalizing(true)
    try {
      // Dùng luôn kết quả API trả về (bản chụp vừa lưu) — khỏi tải lại
      setSheet(await payrollApi.finalize(month))
      notification.success({ title: `Đã chốt lương tháng ${formatMonth(month)}`, description: 'Bảng công tháng này đã bị khóa.' })
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không chốt được lương')
    } finally {
      setFinalizing(false)
    }
  }

  const cancel = async (reason: string | undefined) => {
    // ReasonModal required → reason luôn có; ném lỗi để FormModal giữ modal mở và hiện lỗi từ backend
    const result = await payrollApi.cancel(month, reason ?? '')
    setSheet(result)
    notification.success({ title: `Đã hủy chốt lương tháng ${formatMonth(month)}`, description: 'Có thể sửa chấm công rồi chốt lại.' })
  }

  const columns: TableProps<PayrollLine>['columns'] = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      render: (_, line) => (
        <Flex vertical>
          <Typography.Text strong>{line.employeeName}</Typography.Text>
          <Space size={4}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {line.employeeCode}
            </Typography.Text>
            {!line.employeeActive && (
              <Tag variant="filled" style={{ marginInlineEnd: 0, fontSize: 11 }}>
                Đã nghỉ
              </Tag>
            )}
          </Space>
        </Flex>
      ),
    },
    {
      title: 'Theo ngày công',
      children: [
        { title: 'Công', dataIndex: 'workUnits', width: 70, align: 'right', render: (v: number) => unitsFormat.format(v) },
        { title: 'Lương ngày', dataIndex: 'dailyWage', width: 130, align: 'right', render: rateOrEmpty },
        { title: 'Thành tiền', dataIndex: 'dayAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
      ],
    },
    {
      title: 'Theo tấn bốc vác',
      children: [
        { title: 'Tấn', dataIndex: 'tons', width: 90, align: 'right', render: (v: number) => tonsFormat.format(v) },
        { title: 'Đơn giá tấn', dataIndex: 'tonRate', width: 130, align: 'right', render: rateOrEmpty },
        { title: 'Thành tiền', dataIndex: 'tonAmount', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
      ],
    },
    {
      title: 'Tổng lương',
      dataIndex: 'totalAmount',
      width: 150,
      align: 'right',
      fixed: 'right',
      render: (v: number) => <Typography.Text strong>{formatVnd(v)}</Typography.Text>,
    },
  ]

  const actions = sheet && (
    sheet.finalized ? (
      <Button icon={<UnlockOutlined />} onClick={() => setCancelOpen(true)}>
        Hủy chốt
      </Button>
    ) : (
      <Popconfirm
        title={`Chốt lương tháng ${formatMonth(sheet.month)}?`}
        description="Lưu bảng lương với đơn giá hiện tại và khóa chấm công của tháng này."
        okText="Chốt lương"
        cancelText="Không"
        onConfirm={finalize}
        disabled={sheet.items.length === 0}
      >
        <Button type="primary" icon={<LockOutlined />} loading={finalizing} disabled={sheet.items.length === 0}>
          Chốt lương
        </Button>
      </Popconfirm>
    )
  )

  return (
    <>
      <PageHeader title="Bảng lương" description="Lương tháng theo ngày công và tấn bốc vác" actions={actions} />
      <Flex justify="space-between" align="center" wrap gap="small" style={{ marginBottom: 12 }}>
        <MonthNavigator value={month} onChange={goTo} onReload={reload} />
        {sheet &&
          (sheet.finalized ? (
            <Tag icon={<CheckCircleOutlined />} color="success" variant="filled">
              Đã chốt lúc {sheet.finalizedAt ? formatDateTime(sheet.finalizedAt) : EMPTY_TEXT}
            </Tag>
          ) : (
            <Tag color="warning" variant="filled">
              Tạm tính — chưa chốt
            </Tag>
          ))}
      </Flex>
      {sheet && !sheet.finalized && sheet.items.length > 0 && (
        <Alert
          type="warning"
          showIcon
          title="Số liệu tạm tính theo bảng công và đơn giá hiện tại — còn thay đổi cho đến khi chốt."
          style={{ marginBottom: 12 }}
        />
      )}
      {error && (
        <Alert
          type="error"
          showIcon
          title={error}
          action={<Button onClick={reload}>Thử lại</Button>}
          style={{ marginBottom: 12 }}
        />
      )}
      <Table<PayrollLine>
        rowKey="employeeId"
        size="small"
        bordered
        loading={loading}
        columns={columns}
        dataSource={sheet?.items ?? []}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: `Tháng ${formatMonth(month)} chưa có chấm công nào` }}
        summary={() =>
          sheet && sheet.items.length > 0 ? (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={7}>
                  <Typography.Text strong>Tổng cộng {sheet.items.length} nhân viên</Typography.Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <Typography.Text strong>{formatVnd(sheet.totalAmount)}</Typography.Text>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          ) : null
        }
      />
      {sheet && sheet.history.length > 0 && (
        <Collapse
          style={{ marginTop: 16 }}
          items={[
            {
              key: 'history',
              label: `Lịch sử: ${sheet.history.length} lần chốt đã hủy`,
              children: (
                <Flex vertical gap="small">
                  {sheet.history.map((entry) => (
                    <Typography.Text key={entry.payrollId}>
                      Chốt {formatDateTime(entry.finalizedAt)} · tổng {formatVnd(entry.totalAmount)} · hủy{' '}
                      {formatDateTime(entry.cancelledAt)} — <Typography.Text italic>“{entry.cancelReason}”</Typography.Text>
                    </Typography.Text>
                  ))}
                </Flex>
              ),
            },
          ]}
        />
      )}
      {cancelOpen && (
        <ReasonModal
          open
          title={`Hủy chốt lương tháng ${formatMonth(month)}`}
          description="Bảng lương đã chốt được giữ lại trong lịch sử. Bảng công tháng này mở lại để sửa; sửa xong cần chốt lại."
          submitText="Hủy chốt"
          required
          onSubmit={cancel}
          onClose={() => setCancelOpen(false)}
        />
      )}
    </>
  )
}
