import { EditOutlined, EyeOutlined, MoreOutlined, StopOutlined } from '@ant-design/icons'
import { App, Button, Dropdown } from 'antd'
import type { MenuProps } from 'antd'

interface RowActionsProps {
  onView: () => void
  onEdit: () => void
  /** Bỏ trống khi bản ghi đã ngừng → ẩn mục ngừng (mở lại qua form Sửa) */
  deactivate?: {
    label: string // ví dụ "Ngừng hoạt động", "Ngừng kinh doanh"
    confirmTitle: string
    onConfirm: () => Promise<void>
  }
}

/**
 * Thao tác trên mỗi dòng bảng (ADR-0002, tầng 2): một nút ⋯ mở menu thả xuống.
 * Gom vào menu vì từ 3 thao tác trở lên, hàng nút icon khó nhận ra và chiếm chỗ.
 * Thao tác nguy hiểm tách riêng bằng đường kẻ, chữ đỏ, và luôn hỏi xác nhận.
 */
export function RowActions({ onView, onEdit, deactivate }: RowActionsProps) {
  const { modal } = App.useApp()

  const items: MenuProps['items'] = [
    { key: 'view', icon: <EyeOutlined />, label: 'Xem chi tiết' },
    { key: 'edit', icon: <EditOutlined />, label: 'Sửa' },
    ...(deactivate
      ? [
          { type: 'divider' as const },
          { key: 'deactivate', icon: <StopOutlined />, label: deactivate.label, danger: true },
        ]
      : []),
  ]

  const handleClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'view') onView()
    if (key === 'edit') onEdit()
    if (key === 'deactivate' && deactivate) {
      // Popconfirm không dùng được trong menu (menu đóng thì Popconfirm mất theo) → dùng hộp xác nhận.
      // onOk trả Promise: nút xác nhận hiện vòng xoay đến khi API xong, chống bấm 2 lần.
      modal.confirm({
        title: deactivate.confirmTitle,
        content: 'Có thể mở lại bằng nút Sửa.',
        okText: deactivate.label,
        okButtonProps: { danger: true },
        cancelText: 'Hủy',
        onOk: deactivate.onConfirm,
      })
    }
  }

  return (
    <Dropdown menu={{ items, onClick: handleClick }} trigger={['click']} placement="bottomRight">
      {/* aria-label: trình đọc màn hình đọc được nút chỉ có icon */}
      <Button type="text" icon={<MoreOutlined />} aria-label="Thao tác" />
    </Dropdown>
  )
}
