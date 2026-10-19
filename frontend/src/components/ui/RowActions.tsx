import { CheckCircleOutlined, EditOutlined, EyeOutlined, MoreOutlined, StopOutlined } from '@ant-design/icons'
import { App, Button, Dropdown } from 'antd'
import type { MenuProps } from 'antd'
import type { ReactNode } from 'react'

/** Thao tác riêng của từng màn hình, chèn sau "Sửa" (ví dụ "Sản phẩm cung cấp"). */
export interface RowExtraAction {
  key: string
  label: string
  icon: ReactNode
  onClick: () => void
}

interface RowActionsProps {
  /** Mã bản ghi của dòng, ví dụ "NCC001" — để trình đọc màn hình phân biệt nút ⋯ giữa các dòng */
  recordLabel: string
  onView: () => void
  /** Không truyền = bản ghi không sửa được (ví dụ phiếu nhập đã hoàn tất) → ẩn mục "Sửa" */
  onEdit?: () => void
  extraActions?: RowExtraAction[]
  /** Chỉ truyền khi bản ghi đang hoạt động → hiện mục ngừng (chữ đỏ, hỏi xác nhận) */
  deactivate?: {
    label: string // ví dụ "Ngừng hoạt động", "Ngừng kinh doanh"
    confirmTitle: string
    onConfirm: () => Promise<void>
  }
  /** Chỉ truyền khi bản ghi đã ngừng → hiện mục mở lại (không cần xác nhận vì không mất gì) */
  reactivate?: {
    label: string // ví dụ "Mở lại hoạt động", "Mở bán lại"
    onConfirm: () => Promise<void>
  }
}

/**
 * Thao tác trên mỗi dòng bảng (ADR-0002, tầng 2): một nút ⋯ mở menu thả xuống.
 * Gom vào menu vì từ 3 thao tác trở lên, hàng nút icon khó nhận ra và chiếm chỗ.
 * Thao tác nguy hiểm tách riêng bằng đường kẻ, chữ đỏ, và luôn hỏi xác nhận.
 */
export function RowActions({ recordLabel, onView, onEdit, extraActions = [], deactivate, reactivate }: RowActionsProps) {
  const { modal } = App.useApp()

  const items: MenuProps['items'] = [
    { key: 'view', icon: <EyeOutlined />, label: 'Xem chi tiết' },
    ...(onEdit ? [{ key: 'edit', icon: <EditOutlined />, label: 'Sửa' }] : []),
    ...extraActions.map(({ key, label, icon }) => ({ key, label, icon })),
    ...(reactivate ? [{ key: 'reactivate', icon: <CheckCircleOutlined />, label: reactivate.label }] : []),
    ...(deactivate
      ? [
          { type: 'divider' as const },
          { key: 'deactivate', icon: <StopOutlined />, label: deactivate.label, danger: true },
        ]
      : []),
  ]

  const handleClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'view') onView()
    if (key === 'edit') onEdit?.()
    extraActions.find((action) => action.key === key)?.onClick()
    if (key === 'reactivate' && reactivate) void reactivate.onConfirm()
    if (key === 'deactivate' && deactivate) {
      // Popconfirm không dùng được trong menu (menu đóng thì Popconfirm mất theo) → dùng hộp xác nhận.
      // onOk trả Promise: nút xác nhận hiện vòng xoay đến khi API xong, chống bấm 2 lần.
      modal.confirm({
        title: deactivate.confirmTitle,
        content: 'Dữ liệu và lịch sử được giữ nguyên; có thể mở lại sau.',
        okText: deactivate.label,
        okButtonProps: { danger: true },
        cancelText: 'Hủy',
        onOk: deactivate.onConfirm,
      })
    }
  }

  return (
    <Dropdown menu={{ items, onClick: handleClick }} trigger={['click']} placement="bottomRight">
      {/* aria-label: trình đọc màn hình đọc được nút chỉ có icon, kèm mã để biết dòng nào */}
      <Button type="text" icon={<MoreOutlined />} aria-label={`Thao tác cho ${recordLabel}`} />
    </Dropdown>
  )
}
