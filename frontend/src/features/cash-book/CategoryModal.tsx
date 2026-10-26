import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Flex, Input, Modal, Select, Switch, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { CASH_DIRECTION_LABELS, cashCategoryApi } from './cash-book-api'
import type { CashCategory, CashDirection } from './cash-book-api'

interface CategoryModalProps {
  open: boolean
  categories: CashCategory[]
  /** Danh mục vừa đổi → trang tải lại danh sách */
  onChanged: () => void
  onClose: () => void
}

const DIRECTION_OPTIONS = (Object.keys(CASH_DIRECTION_LABELS) as CashDirection[]).map((direction) => ({
  value: direction,
  label: CASH_DIRECTION_LABELS[direction],
}))

/**
 * Danh mục thu/chi khác (BR-CASH-05): thêm mới, đổi tên (bấm vào tên), ngừng dùng / mở lại. Không đổi được chiều
 * thu/chi của danh mục đã tạo — phiếu cũ sẽ mang nghĩa ngược lại.
 */
export function CategoryModal({ open, categories, onChanged, onClose }: CategoryModalProps) {
  const { message } = App.useApp()
  const [name, setName] = useState('')
  const [direction, setDirection] = useState<CashDirection>('OUT')
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
    if (await run(() => cashCategoryApi.create(name.trim(), direction), 'Không tạo được danh mục')) setName('')
    setSaving(false)
  }

  const columns: TableProps<CashCategory>['columns'] = [
    {
      title: 'Chiều',
      dataIndex: 'direction',
      width: 80,
      render: (value: CashDirection) => (
        <Tag color={value === 'IN' ? 'success' : 'error'} variant="filled">
          {CASH_DIRECTION_LABELS[value]}
        </Tag>
      ),
    },
    {
      title: 'Tên danh mục',
      dataIndex: 'name',
      render: (value: string, category) => (
        <Typography.Text
          editable={{
            onChange: (next) => {
              if (next.trim() && next.trim() !== value) {
                void run(() => cashCategoryApi.update(category.id, next.trim(), category.active), 'Không đổi được tên')
              }
            },
          }}
          type={category.active ? undefined : 'secondary'}
        >
          {value}
        </Typography.Text>
      ),
    },
    {
      title: 'Đang dùng',
      dataIndex: 'active',
      width: 100,
      align: 'center',
      render: (active: boolean, category) => (
        <Switch
          size="small"
          checked={active}
          aria-label={`Đang dùng ${category.name}`}
          onChange={(checked) => void run(() => cashCategoryApi.update(category.id, category.name, checked), 'Không cập nhật được')}
        />
      ),
    },
  ]

  return (
    <Modal open={open} title="Danh mục thu / chi" width={620} onCancel={onClose} footer={null} destroyOnHidden>
      <Flex gap={8} style={{ marginBottom: 12 }}>
        <Select value={direction} onChange={setDirection} options={DIRECTION_OPTIONS} style={{ width: 90 }} aria-label="Chiều" />
        <Input
          value={name}
          maxLength={100}
          placeholder="Tên danh mục mới, ví dụ: Tiền điện"
          onChange={(event) => setName(event.target.value)}
          onPressEnter={add}
        />
        <Button type="primary" icon={<PlusOutlined />} loading={saving} disabled={!name.trim()} onClick={add}>
          Thêm
        </Button>
      </Flex>
      <Table<CashCategory>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={categories}
        pagination={false}
        locale={{ emptyText: 'Chưa có danh mục nào' }}
      />
    </Modal>
  )
}
