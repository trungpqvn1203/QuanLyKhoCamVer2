import { EditOutlined } from '@ant-design/icons'
import { Button, Descriptions, Drawer, Flex, Tabs } from 'antd'
import type { DescriptionsProps } from 'antd'
import type { ReactNode } from 'react'

/** Một nhóm "nhãn – giá trị", ví dụ "Thông tin chung", "Trạng thái", "Thông tin hệ thống". */
export interface DetailSection {
  key: string
  title: string
  items: DescriptionsProps['items']
}

/** Tab dữ liệu liên quan đặt sau tab "Thông tin", ví dụ danh sách SP của NCC. */
export interface DetailTab {
  key: string
  label: string
  children: ReactNode
}

/** Khóa tab thông tin mặc định — màn hình dùng để mở thẳng tab này hoặc tab liên quan. */
export const DETAIL_INFO_TAB = 'info'

interface DetailDrawerProps {
  open: boolean
  title: ReactNode
  sections: DetailSection[]
  onClose: () => void
  /** Có thì hiện nút Sửa ở góc trên — màn hình tự đóng khung và mở form sửa */
  onEdit?: () => void
  /** Độ rộng khung (px); mặc định 480 — rộng hơn khi có bảng con bên trong */
  size?: number
  /** Có thì khung chia tab: "Thông tin" + các tab này */
  tabs?: DetailTab[]
  activeTab?: string
  onTabChange?: (key: string) => void
}

/**
 * Khung xem chi tiết trượt từ phải (ADR-0002, tầng 2).
 * Chọn Drawer thay Modal: bảng vẫn nhìn thấy phía sau, xem xong không mất vị trí đang đọc.
 * Một bản ghi chỉ có một khung: dữ liệu liên quan nằm ở tab, không mở thêm khung chồng lên.
 */
export function DetailDrawer({
  open,
  title,
  sections,
  onClose,
  onEdit,
  size = 480,
  tabs,
  activeTab,
  onTabChange,
}: DetailDrawerProps) {
  const info = (
    <Flex vertical gap={20}>
      {sections.map((section) => (
        <Descriptions
          key={section.key}
          title={section.title}
          column={1}
          bordered
          size="small"
          items={section.items}
          styles={{ label: { width: 160 } }}
        />
      ))}
    </Flex>
  )

  return (
    <Drawer
      open={open}
      title={title}
      size={size}
      onClose={onClose}
      destroyOnHidden
      extra={
        onEdit && (
          <Button type="primary" ghost icon={<EditOutlined />} onClick={onEdit}>
            Sửa
          </Button>
        )
      }
    >
      {tabs ? (
        <Tabs
          activeKey={activeTab}
          onChange={onTabChange}
          items={[{ key: DETAIL_INFO_TAB, label: 'Thông tin', children: info }, ...tabs]}
        />
      ) : (
        info
      )}
    </Drawer>
  )
}
