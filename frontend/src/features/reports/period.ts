import { todayIso } from '../../utils/format'

/**
 * Kỳ báo cáo chọn nhanh kiểu KiotViet / MISA (BR-RPT-05). Tính trên chuỗi "YYYY-MM-DD" bằng Date.UTC để không
 * lệch múi giờ: "hôm nay" lấy theo giờ Việt Nam (todayIso), phần cộng trừ ngày làm trên lịch thuần.
 */
export type PeriodPreset = 'today' | 'yesterday' | 'last7Days' | 'thisMonth' | 'lastMonth' | 'custom'

export const PERIOD_LABELS: Record<PeriodPreset, string> = {
  today: 'Hôm nay',
  yesterday: 'Hôm qua',
  last7Days: '7 ngày qua',
  thisMonth: 'Tháng này',
  lastMonth: 'Tháng trước',
  custom: 'Tùy chọn…',
}

export interface DateRange {
  from: string
  to: string
}

const pad = (value: number) => String(value).padStart(2, '0')

function toIso(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

function parse(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

function addDays(iso: string, days: number): string {
  const date = parse(iso)
  date.setUTCDate(date.getUTCDate() + days)
  return toIso(date)
}

/** Khoảng ngày của một kỳ chọn nhanh ('custom' không có khoảng cố định → undefined). */
export function presetRange(preset: PeriodPreset, today: string = todayIso()): DateRange | undefined {
  const date = parse(today)
  switch (preset) {
    case 'today':
      return { from: today, to: today }
    case 'yesterday': {
      const yesterday = addDays(today, -1)
      return { from: yesterday, to: yesterday }
    }
    case 'last7Days':
      return { from: addDays(today, -6), to: today }
    case 'thisMonth':
      return { from: toIso(new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 1))), to: today }
    case 'lastMonth':
      return {
        from: toIso(new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() - 1, 1))),
        // Ngày 0 của tháng này = ngày cuối tháng trước (tự đúng cả tháng 2, năm nhuận)
        to: toIso(new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 0))),
      }
    case 'custom':
      return undefined
  }
}
