import { Typography } from 'antd'
import { EMPTY_TEXT, formatQuantity } from '../../utils/format'

/** Chênh lệch kiểm kê: +thừa xanh, −thiếu đỏ, khớp "0", chưa đếm "—". */
export function DifferenceText({ value }: { value?: number }) {
  if (value === undefined) return <>{EMPTY_TEXT}</>
  if (value === 0) return <Typography.Text type="secondary">0</Typography.Text>
  return (
    <Typography.Text strong type={value > 0 ? 'success' : 'danger'}>
      {value > 0 ? '+' : '−'}
      {formatQuantity(Math.abs(value))}
    </Typography.Text>
  )
}
