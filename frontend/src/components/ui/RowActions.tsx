import { EditOutlined, StopOutlined } from '@ant-design/icons'
import { Button, Popconfirm, Space, Tooltip } from 'antd'

interface RowActionsProps {
  onEdit: () => void
  /** Bỏ trống khi bản ghi đã ngừng → ẩn nút ngừng (mở lại qua form Sửa) */
  deactivate?: {
    label: string // ví dụ "Ngừng hoạt động", "Ngừng kinh doanh"
    confirmTitle: string
    onConfirm: () => Promise<void>
  }
}

/**
 * Nút thao tác trên mỗi dòng bảng (ADR-0002, tầng 2): chỉ icon, rê chuột hiện tên.
 * aria-label giúp trình đọc màn hình đọc được nút chỉ có icon.
 */
export function RowActions({ onEdit, deactivate }: RowActionsProps) {
  return (
    <Space size="small">
      <Tooltip title="Sửa">
        <Button type="text" icon={<EditOutlined />} aria-label="Sửa" onClick={onEdit} />
      </Tooltip>
      {deactivate && (
        <Popconfirm
          title={deactivate.confirmTitle}
          description="Có thể mở lại bằng nút Sửa."
          okText={deactivate.label}
          okButtonProps={{ danger: true }}
          cancelText="Hủy"
          onConfirm={deactivate.onConfirm}
        >
          {/* Tooltip bọc trong Popconfirm: rê chuột hiện tên, bấm mới hỏi xác nhận */}
          <Tooltip title={deactivate.label}>
            <Button type="text" danger icon={<StopOutlined />} aria-label={deactivate.label} />
          </Tooltip>
        </Popconfirm>
      )}
    </Space>
  )
}
