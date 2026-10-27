import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { Alert, App, Button, Descriptions, Flex, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { EMPTY_TEXT, formatDate, formatDateTime } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { cashBookApi } from './cash-book-api'
import type { CashBookOpening, OpeningChange } from './cash-book-api'
import { OpeningModal } from './OpeningModal'

/** "01/09/2026 · TM 50.000.000 đ · TK 300.000.000 đ" — một lần đặt đầu kỳ (cũ hoặc mới). */
function describe(startDate?: string, cash?: number, bank?: number) {
  if (!startDate) return EMPTY_TEXT
  return `${formatDate(startDate)} · TM ${formatVnd(cash ?? 0)} · TK ${formatVnd(bank ?? 0)}`
}

/**
 * Quỹ đầu kỳ (BR-CASH-03), nằm trong nhóm "Số dư đầu kỳ" cùng tồn kho đầu kỳ: việc làm một lần lúc bắt đầu, tách khỏi
 * trang Sổ quỹ dùng hằng ngày. Đầu kỳ sửa được nhưng làm đổi mọi số dư → mỗi lần đặt/sửa ghi lịch sử, hiện ngay dưới.
 */
export function CashOpeningPage() {
  const { notification } = App.useApp()
  const [opening, setOpening] = useState<CashBookOpening | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [editing, setEditing] = useState(false)

  useEffect(() => {
    let ignore = false
    cashBookApi
      .opening()
      .then((loaded) => {
        if (ignore) return
        setOpening(loaded)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được đầu kỳ sổ quỹ')
      })
    return () => {
      ignore = true
    }
  }, [version])

  const columns: TableProps<OpeningChange>['columns'] = [
    { title: 'Thời điểm', dataIndex: 'changedAt', width: 160, render: (at: string) => formatDateTime(at) },
    { title: 'Người sửa', dataIndex: 'changedBy', width: 140 },
    {
      title: 'Trước',
      key: 'old',
      render: (_, change) =>
        change.oldStartDate ? (
          describe(change.oldStartDate, change.oldOpeningCash, change.oldOpeningBank)
        ) : (
          <Typography.Text type="secondary">Đặt lần đầu</Typography.Text>
        ),
    },
    {
      title: 'Sau',
      key: 'new',
      render: (_, change) => (
        <Typography.Text strong>{describe(change.newStartDate, change.newOpeningCash, change.newOpeningBank)}</Typography.Text>
      ),
    },
  ]

  const configured = opening?.configured ?? false

  return (
    <>
      <PageHeader
        title="Quỹ đầu kỳ"
        description="Số tiền mặt đang có trong quỹ và số dư tài khoản thực tế tại ngày bắt đầu dùng sổ quỹ"
        actions={
          opening && (
            <Button
              type="primary"
              icon={configured ? <EditOutlined /> : <PlusOutlined />}
              onClick={() => setEditing(true)}
            >
              {configured ? 'Sửa đầu kỳ' : 'Đặt đầu kỳ'}
            </Button>
          )
        }
      />
      {error && (
        <Alert
          type="error"
          showIcon
          title={error}
          action={<Button size="small" onClick={() => setVersion((v) => v + 1)}>Thử lại</Button>}
          style={{ marginBottom: 12 }}
        />
      )}
      {opening && !configured && (
        <Alert
          type="info"
          showIcon
          title="Sổ quỹ chưa có đầu kỳ"
          description="Đặt ngày bắt đầu cùng số tiền mặt đếm được và số dư tài khoản theo sao kê ngân hàng tại ngày đó. Khoản thu/chi có ngày trước ngày này sẽ không tính vào sổ quỹ."
        />
      )}
      {configured && opening && (
        <Flex vertical gap={16}>
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, md: 3 }}
            items={[
              { key: 'start', label: 'Ngày bắt đầu', children: formatDate(opening.startDate!) },
              { key: 'cash', label: 'Tiền mặt đầu kỳ', children: <Typography.Text strong>{formatVnd(opening.openingCash ?? 0)}</Typography.Text> },
              { key: 'bank', label: 'Tài khoản đầu kỳ', children: <Typography.Text strong>{formatVnd(opening.openingBank ?? 0)}</Typography.Text> },
            ]}
          />
          <Alert
            type="warning"
            showIcon
            title="Sửa đầu kỳ làm thay đổi mọi số dư trong sổ quỹ"
            description={
              <>
                Chỉ sửa khi nhập sai số thực tế lúc bắt đầu. Đếm quỹ thấy lệch sau một thời gian thì lập phiếu thu/chi ở{' '}
                <Link to="/cash-book">Sổ quỹ</Link> thay vì sửa đầu kỳ.
              </>
            }
          />
          <div>
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              Lịch sử đặt / sửa đầu kỳ
            </Typography.Title>
            <Table<OpeningChange>
              size="small"
              rowKey={(change) => change.changedAt}
              columns={columns}
              dataSource={opening.changes}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
          </div>
        </Flex>
      )}

      {editing && opening && (
        <OpeningModal
          open
          opening={opening}
          onSaved={(saved) => {
            setOpening(saved)
            notification.success({ title: 'Đã lưu đầu kỳ sổ quỹ' })
          }}
          onClose={() => setEditing(false)}
        />
      )}
    </>
  )
}
