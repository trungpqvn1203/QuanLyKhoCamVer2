import { LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { Button, Input, Space } from 'antd'
import { currentMonth, shiftMonth } from '../../utils/month'

interface MonthNavigatorProps {
  /** "YYYY-MM" */
  value: string
  onChange: (month: string) => void
  onReload: () => void
}

/**
 * Chọn tháng: lùi/tiến, ô chọn tháng, về tháng này, tải lại. Dùng ô native type="month"
 * (không cần thư viện ngày tháng, cùng cách với ô ngày chứng từ của phiếu nhập).
 */
export function MonthNavigator({ value, onChange, onReload }: MonthNavigatorProps) {
  const go = (next: string) => {
    if (next && next !== value) onChange(next)
  }

  return (
    <Space>
      <Button icon={<LeftOutlined />} onClick={() => go(shiftMonth(value, -1))} aria-label="Tháng trước" />
      <Input
        type="month"
        value={value}
        onChange={(event) => go(event.target.value)}
        style={{ width: 170 }}
        aria-label="Chọn tháng"
      />
      <Button icon={<RightOutlined />} onClick={() => go(shiftMonth(value, 1))} aria-label="Tháng sau" />
      <Button onClick={() => go(currentMonth())}>Tháng này</Button>
      <Button icon={<ReloadOutlined />} onClick={onReload} aria-label="Tải lại" />
    </Space>
  )
}
