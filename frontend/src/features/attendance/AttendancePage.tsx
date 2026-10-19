import { LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { Alert, Button, Flex, Input, Space, Table, Tag, Tooltip, Typography, theme } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { formatDate, todayIso } from '../../utils/format'
import { DAY_STATUS_LABELS, DAY_STATUS_SYMBOLS, attendanceApi, dayIso, shiftMonth, weekdayLabel } from './attendance-api'
import type { AttendanceDay, AttendanceRow, DayStatus, MonthlyAttendance } from './attendance-api'
import { AttendanceCellModal } from './AttendanceCellModal'

const tonsFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })
const unitsFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 })

interface EditingCell {
  row: AttendanceRow
  date: string
  current?: AttendanceDay
}

/**
 * Bảng công tháng (BR-ATT-01): mỗi nhân viên một dòng, mỗi ngày một ô. Bấm ô để chấm/sửa.
 * Ô trống = chưa chấm (khác "N" = đã chấm là nghỉ). Ô sau hôm nay bị khóa (BR-ATT-02).
 */
export function AttendancePage() {
  const { token } = theme.useToken()
  const [month, setMonth] = useState(() => todayIso().slice(0, 7))
  const [reloadCount, setReloadCount] = useState(0)
  const [sheet, setSheet] = useState<MonthlyAttendance | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<EditingCell | null>(null)

  useEffect(() => {
    // Bỏ kết quả của request cũ: bấm "tháng trước" hai lần thật nhanh, tháng giữa về sau không ghi đè bảng
    let ignore = false
    attendanceApi
      .month(month)
      .then((result) => {
        if (ignore) return
        setSheet(result)
        setError(null)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (ignore) return
        setError(err instanceof ApiError ? err.message : 'Không tải được bảng công')
        setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [month, reloadCount])

  const goTo = (next: string) => {
    if (!next || next === month) return
    setLoading(true)
    setMonth(next)
  }

  const reload = () => {
    setLoading(true)
    setReloadCount((count) => count + 1)
  }

  const STATUS_COLORS: Record<DayStatus, string> = {
    FULL: token.colorSuccess,
    HALF: token.colorWarning,
    ABSENT: token.colorTextTertiary,
  }

  // Tra ô theo "employeeId|date" — dựng một lần mỗi lần tải, không tìm tuyến tính trong từng ô
  const cells = useMemo(() => {
    const map = new Map<string, AttendanceDay>()
    sheet?.employees.forEach((row) => row.days.forEach((day) => map.set(`${row.employeeId}|${day.date}`, day)))
    return map
  }, [sheet])

  const renderCell = (row: AttendanceRow, date: string) => {
    const day = cells.get(`${row.employeeId}|${date}`)
    const future = sheet != null && date > sheet.today
    const locked = future || !row.active
    const tooltip = future
      ? 'Chưa đến ngày'
      : !row.active
        ? 'Nhân viên đã nghỉ việc — cho làm lại nếu cần sửa công'
        : day
          ? `${DAY_STATUS_LABELS[day.status]}${day.tons > 0 ? ` · ${tonsFormat.format(day.tons)} tấn` : ''}${day.note ? ` · ${day.note}` : ''}`
          : 'Chưa chấm — bấm để chấm công'
    return (
      <Tooltip title={tooltip} mouseEnterDelay={0.4}>
        <button
          type="button"
          disabled={locked}
          onClick={() => setEditing({ row, date, current: day })}
          aria-label={`${row.employeeName} ngày ${formatDate(date)}: ${tooltip}`}
          style={{
            width: '100%',
            minHeight: 38,
            border: 'none',
            background: future ? token.colorFillQuaternary : 'transparent',
            cursor: locked ? 'not-allowed' : 'pointer',
            padding: 0,
            lineHeight: 1.15,
            color: day ? STATUS_COLORS[day.status] : token.colorTextQuaternary,
          }}
        >
          <div style={{ fontWeight: 600 }}>{day ? DAY_STATUS_SYMBOLS[day.status] : '·'}</div>
          {day && day.tons > 0 && (
            <div style={{ fontSize: 11, color: token.colorText }}>{tonsFormat.format(day.tons)}</div>
          )}
        </button>
      </Tooltip>
    )
  }

  const dayColumns: TableProps<AttendanceRow>['columns'] = sheet
    ? Array.from({ length: sheet.daysInMonth }, (_, index) => {
        const date = dayIso(sheet.month, index + 1)
        const weekday = weekdayLabel(date)
        const sunday = weekday === 'CN'
        return {
          key: date,
          width: 46,
          align: 'center' as const,
          title: (
            <div style={{ lineHeight: 1.2, color: sunday ? token.colorError : undefined }}>
              <div>{index + 1}</div>
              <div style={{ fontSize: 11, fontWeight: 400 }}>{weekday}</div>
            </div>
          ),
          // Chủ nhật tô nền nhạt để dò cột dễ hơn
          onCell: () => ({ style: { padding: 0, background: sunday ? token.colorFillQuaternary : undefined } }),
          render: (_: unknown, row: AttendanceRow) => renderCell(row, date),
        }
      })
    : []

  const columns: TableProps<AttendanceRow>['columns'] = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 200,
      render: (_, row) => (
        <Flex vertical>
          <Typography.Text strong ellipsis>
            {row.employeeName}
          </Typography.Text>
          <Space size={4}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {row.employeeCode}
            </Typography.Text>
            {!row.active && (
              <Tag variant="filled" style={{ marginInlineEnd: 0, fontSize: 11 }}>
                Đã nghỉ
              </Tag>
            )}
          </Space>
        </Flex>
      ),
    },
    ...dayColumns,
    {
      title: 'Công',
      key: 'units',
      fixed: 'right',
      width: 70,
      align: 'right',
      render: (_, row) =>
        row.paysByDay ? (
          <Typography.Text strong>{unitsFormat.format(row.totalWorkUnits)}</Typography.Text>
        ) : (
          // Không hưởng theo ngày: vẫn hiện số công nhưng mờ — để biết là không tính tiền
          <Typography.Text type="secondary">{unitsFormat.format(row.totalWorkUnits)}</Typography.Text>
        ),
    },
    {
      title: 'Tấn',
      key: 'tons',
      fixed: 'right',
      width: 90,
      align: 'right',
      render: (_, row) => (
        <Typography.Text strong={row.paysByTon} type={row.paysByTon ? undefined : 'secondary'}>
          {tonsFormat.format(row.totalTons)}
        </Typography.Text>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="Chấm công" description="Bảng công tháng: ngày công và tấn bốc vác của từng nhân viên" />
      <Flex justify="space-between" align="center" wrap gap="small" style={{ marginBottom: 12 }}>
        <Space>
          <Button icon={<LeftOutlined />} onClick={() => goTo(shiftMonth(month, -1))} aria-label="Tháng trước" />
          <Input
            type="month"
            value={month}
            onChange={(event) => goTo(event.target.value)}
            style={{ width: 170 }}
            aria-label="Chọn tháng"
          />
          <Button icon={<RightOutlined />} onClick={() => goTo(shiftMonth(month, 1))} aria-label="Tháng sau" />
          <Button onClick={() => goTo(todayIso().slice(0, 7))}>Tháng này</Button>
          <Button icon={<ReloadOutlined />} onClick={reload} aria-label="Tải lại" />
        </Space>
        <Space size="middle" wrap>
          {(Object.keys(DAY_STATUS_SYMBOLS) as DayStatus[]).map((status) => (
            <Typography.Text key={status} type="secondary">
              <span style={{ color: STATUS_COLORS[status], fontWeight: 600 }}>{DAY_STATUS_SYMBOLS[status]}</span>{' '}
              {DAY_STATUS_LABELS[status]}
            </Typography.Text>
          ))}
          <Typography.Text type="secondary">· Chưa chấm · số nhỏ dưới ký hiệu = tấn bốc vác</Typography.Text>
        </Space>
      </Flex>
      {error && (
        <Alert
          type="error"
          showIcon
          title={error}
          action={<Button onClick={reload}>Thử lại</Button>}
          style={{ marginBottom: 12 }}
        />
      )}
      <Table<AttendanceRow>
        rowKey="employeeId"
        size="small"
        bordered
        loading={loading}
        columns={columns}
        dataSource={sheet?.employees ?? []}
        pagination={false}
        scroll={{ x: 'max-content', y: 'calc(100vh - 330px)' }}
        locale={{ emptyText: 'Chưa có nhân viên đang làm việc. Thêm nhân viên ở mục Nhân viên trước.' }}
      />
      {editing && (
        <AttendanceCellModal
          // key: mỗi ô một lần mở mới — không giữ lỗi/giá trị của ô trước
          key={`${editing.row.employeeId}|${editing.date}`}
          open
          row={editing.row}
          date={editing.date}
          current={editing.current}
          onClose={() => setEditing(null)}
          onChanged={reload}
        />
      )}
    </>
  )
}
