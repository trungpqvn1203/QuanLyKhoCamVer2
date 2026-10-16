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
}

/**
 * Khung xem chi tiết trượt từ phải (ADR-0002, tầng 2).
 * Chọn Drawer thay Modal: bảng vẫn nhìn thấy phía sau, xem xong không mất vị trí đang đọc.
 */
export function DetailDrawer({ open, title, items, onClose, onEdit }: DetailDrawerProps) {
  return (
    <Drawer
      open={open}
      title={title}
      size={480}
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
    </Drawer>
  )
}
