import { LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { Button, Select, Space } from 'antd'
import { currentMonth, shiftMonth } from '../../utils/month'

interface MonthNavigatorProps {
  /** "YYYY-MM" */
  value: string
  onChange: (month: string) => void
  onReload: () => void
}

const MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => ({
  value: index + 1,
  label: `Tháng ${index + 1}`,
}))

/** Năm hiện tại lùi 5 năm, tiến 1 năm; luôn có năm đang xem (lùi bằng nút "<" có thể ra ngoài khoảng). */
function yearOptions(viewingYear: number) {
  const thisYear = Number(currentMonth().slice(0, 4))
  const years = new Set<number>([viewingYear])
  for (let year = thisYear - 5; year <= thisYear + 1; year++) years.add(year)
  return [...years].sort((a, b) => b - a).map((year) => ({ value: year, label: `Năm ${year}` }))
}

/**
 * Chọn tháng: lùi/tiến, chọn Tháng + Năm, về tháng này, tải lại.
 * Không dùng ô native type="month": trình duyệt tự hiển thị theo ngôn ngữ của nó ("October 2026"),
 * trang web không đổi được sang tiếng Việt.
 */
export function MonthNavigator({ value, onChange, onReload }: MonthNavigatorProps) {
  const [year, month] = value.split('-').map(Number)

  const go = (next: string) => {
    if (next !== value) onChange(next)
  }
  const select = (nextYear: number, nextMonth: number) => go(`${nextYear}-${String(nextMonth).padStart(2, '0')}`)

  return (
    <Space>
      <Button icon={<LeftOutlined />} onClick={() => go(shiftMonth(value, -1))} aria-label="Tháng trước" />
      <Select
        value={month}
        options={MONTH_OPTIONS}
        onChange={(next) => select(year, next)}
        style={{ width: 120 }}
        aria-label="Chọn tháng"
      />
      <Select
        value={year}
        options={yearOptions(year)}
        onChange={(next) => select(next, month)}
        style={{ width: 120 }}
        aria-label="Chọn năm"
      />
      <Button icon={<RightOutlined />} onClick={() => go(shiftMonth(value, 1))} aria-label="Tháng sau" />
      <Button onClick={() => go(currentMonth())}>Tháng này</Button>
      <Button icon={<ReloadOutlined />} onClick={onReload} aria-label="Tải lại" />
    </Space>
  )
}
