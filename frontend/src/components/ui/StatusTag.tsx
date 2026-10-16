import { Tag } from 'antd'

interface StatusTagProps {
  active: boolean
  activeText?: string
  inactiveText?: string
}

/** Hiển thị trạng thái active/ngừng thống nhất trên mọi bảng. */
export function StatusTag({ active, activeText = 'Đang hoạt động', inactiveText = 'Ngừng hoạt động' }: StatusTagProps) {
  return <Tag color={active ? 'green' : 'default'}>{active ? activeText : inactiveText}</Tag>
}
