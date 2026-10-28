import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Flex, Input, Switch, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'

export interface CatalogItem {
  id: number
  name: string
  active: boolean
}

interface CatalogPanelProps<T extends CatalogItem> {
  items: T[]
  loading: boolean
  /** "danh mục", "đơn vị" — ghép vào câu báo lỗi */
  noun: string
  placeholder: string
  maxLength: number
  onCreate: (name: string) => Promise<unknown>
  onUpdate: (item: T, name: string, active: boolean) => Promise<unknown>
  /** Gọi sau mỗi thay đổi thành công → tải lại danh sách */
  onChanged: () => void
  /** Cột phụ chèn giữa Tên và Đang dùng */
  extraColumns?: TableProps<T>['columns']
}

/**
 * Bảng danh mục đơn giản: thêm, đổi tên (bấm vào tên), ngừng dùng / mở lại — cùng cách với danh mục lý do
 * kiểm kê. Không xóa: sản phẩm cũ vẫn trỏ tới bản ghi. Trùng tên (kể cả bản đã ngừng) do backend báo lỗi.
 */
export function CatalogPanel<T extends CatalogItem>({
  items,
  loading,
  noun,
  placeholder,
  maxLength,
  onCreate,
  onUpdate,
  onChanged,
  extraColumns = [],
}: CatalogPanelProps<T>) {
  const { message } = App.useApp()
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)

  const run = async (action: () => Promise<unknown>, failed: string) => {
    try {
      await action()
      onChanged()
      return true
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
      return false
    }
  }

  const add = async () => {
    if (!name.trim()) return
    setSaving(true)
    if (await run(() => onCreate(name.trim()), `Không tạo được ${noun}`)) setName('')
    setSaving(false)
  }

  const columns: TableProps<T>['columns'] = [
    {
      title: 'Tên',
      dataIndex: 'name',
      render: (value: string, item) => (
        <Typography.Text
          editable={{
            maxLength,
            onChange: (next) => {
              if (next.trim() && next.trim() !== value) {
                void run(() => onUpdate(item, next.trim(), item.active), 'Không đổi được tên')
              }
            },
          }}
          type={item.active ? undefined : 'secondary'}
        >
          {value}
        </Typography.Text>
      ),
    },
    ...extraColumns,
    {
      title: 'Đang dùng',
      dataIndex: 'active',
      width: 100,
      align: 'center',
      render: (active: boolean, item) => (
        <Switch
          size="small"
          checked={active}
          aria-label={`Đang dùng ${item.name}`}
          onChange={(checked) => void run(() => onUpdate(item, item.name, checked), 'Không cập nhật được')}
        />
      ),
    },
  ]

  return (
    <Flex vertical gap={12}>
      <Flex gap={8}>
        <Input
          value={name}
          maxLength={maxLength}
          placeholder={placeholder}
          onChange={(event) => setName(event.target.value)}
          onPressEnter={add}
        />
        <Button type="primary" icon={<PlusOutlined />} loading={saving} disabled={!name.trim()} onClick={add}>
          Thêm
        </Button>
      </Flex>
      <Table<T>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={items}
        loading={loading}
        pagination={false}
        locale={{ emptyText: `Chưa có ${noun} nào` }}
      />
    </Flex>
  )
}
