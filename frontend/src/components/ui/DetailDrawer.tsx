import { EditOutlined } from '@ant-design/icons'
import { Button, Descriptions, Drawer } from 'antd'
import type { DescriptionsProps } from 'antd'
import type { ReactNode } from 'react'

interface DetailDrawerProps {
  open: boolean
  title: ReactNode
  /** Các dòng "nhãn – giá trị" hiển thị trong khung */
  items: DescriptionsProps['items']
  onClose: () => void
  /** Có thì hiện nút Sửa ở góc trên — màn hình tự đóng khung và mở form sửa */
  onEdit?: () => void
  /** Độ rộng khung (px); mặc định 480 — rộng hơn khi có bảng con bên trong */
  size?: number
  /** Nội dung thêm bên dưới phần thông tin, ví dụ danh sách SP của nhà cung cấp */
  children?: ReactNode
}

/**
 * Khung xem chi tiết trượt từ phải (ADR-0002, tầng 2).
 * Chọn Drawer thay Modal: bảng vẫn nhìn thấy phía sau, xem xong không mất vị trí đang đọc.
 */
export function DetailDrawer({ open, title, items, onClose, onEdit, size = 480, children }: DetailDrawerProps) {
  return (
    <Drawer
      open={open}
      title={title}
      size={size}
      onClose={onClose}
      destroyOnHidden
      extra={
        onEdit && (
          <Button icon={<EditOutlined />} onClick={onEdit}>
            Sửa
          </Button>
        )
      }
    >
      <Descriptions column={1} bordered size="small" items={items} styles={{ label: { width: 160 } }} />
      {children}
    </Drawer>
  )
}
