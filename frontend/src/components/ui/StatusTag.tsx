import { Tag } from 'antd'

interface StatusTagProps {
  active: boolean
  activeText?: string
  inactiveText?: string
}

/** Độ rộng cột "Trạng thái" thống nhất trên mọi bảng — đủ cho nhãn dài nhất "Ngừng kinh doanh". */
export const STATUS_COLUMN_WIDTH = 150

/**
 * Badge trạng thái thống nhất trên mọi bảng:
 * hoạt động = nền xanh nhạt chữ xanh; ngừng = nền xám chữ xám. Không viền cho gọn.
 */
export function StatusTag({ active, activeText = 'Đang hoạt động', inactiveText = 'Ngừng hoạt động' }: StatusTagProps) {
  return (
    <Tag color={active ? 'success' : 'default'} variant="filled" style={{ marginInlineEnd: 0 }}>
      {active ? activeText : inactiveText}
    </Tag>
  )
}
