import { theme } from 'antd'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatDate } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import type { DailySales } from '../reports/report-api'

/** "2026-10-29" → "29/10" cho trục ngày (ngắn, 30 cột vẫn đọc được). */
const dayLabel = (iso: string) => formatDate(iso).slice(0, 5)

/** 12_500_000 → "12,5 tr"; 1_200_000_000 → "1,2 tỷ" — trục tiền gọn như KiotViet. */
const compactVnd = (value: number) => {
  const format = (n: number) => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(n)
  if (value >= 1_000_000_000) return `${format(value / 1_000_000_000)} tỷ`
  if (value >= 1_000_000) return `${format(value / 1_000_000)} tr`
  if (value >= 1_000) return `${format(value / 1_000)} k`
  return format(value)
}

interface SalesChartProps {
  days: DailySales[]
}

/** Doanh số 30 ngày gần nhất (BR-RPT-03), mỗi cột một ngày; rê chuột xem số tiền và số phiếu. */
export function SalesChart({ days }: SalesChartProps) {
  const { token } = theme.useToken()
  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={days} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke={token.colorBorderSecondary} />
        <XAxis dataKey="date" tickFormatter={dayLabel} tick={{ fontSize: 11 }} interval="preserveStartEnd" />
        <YAxis tickFormatter={compactVnd} tick={{ fontSize: 11 }} width={56} />
        <Tooltip
          cursor={{ fill: token.colorFillTertiary }}
          labelFormatter={(label) => formatDate(String(label))}
          formatter={(value, _name, item) => [
            `${formatVnd(Number(value))} · ${(item.payload as DailySales).orderCount} phiếu`,
            'Doanh số',
          ]}
        />
        <Bar dataKey="revenue" fill={token.colorPrimary} radius={[3, 3, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  )
}
