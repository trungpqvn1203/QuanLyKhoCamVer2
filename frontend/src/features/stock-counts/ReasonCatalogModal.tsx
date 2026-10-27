import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Flex, Input, Modal, Switch, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { stockCountReasonApi } from './stock-count-api'
import type { StockCountReason } from './stock-count-api'

interface ReasonCatalogModalProps {
  reasons: StockCountReason[]
  /** Danh mục vừa đổi → trang tải lại */
  onChanged: () => void
  onClose: () => void
}

/**
 * Danh mục lý do lệch kiểm kê (BR-CNT-05): thêm, đổi tên (bấm vào tên), ngừng dùng / mở lại — cùng cách với
 * danh mục thu/chi của sổ quỹ. Không xóa: dòng kiểm kê cũ vẫn trỏ tới lý do.
 */
export function ReasonCatalogModal({ reasons, onChanged, onClose }: ReasonCatalogModalProps) {
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
    if (await run(() => stockCountReasonApi.create(name.trim()), 'Không tạo được lý do')) setName('')
    setSaving(false)
  }

  const columns: TableProps<StockCountReason>['columns'] = [
    {
      title: 'Lý do',
      dataIndex: 'name',
      render: (value: string, reason) => (
        <Typography.Text
          editable={{
            onChange: (next) => {
              if (next.trim() && next.trim() !== value) {
                void run(() => stockCountReasonApi.update(reason.id, next.trim(), reason.active), 'Không đổi được tên')
              }
            },
          }}
          type={reason.active ? undefined : 'secondary'}
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
      render: (active: boolean, reason) => (
        <Switch
          size="small"
          checked={active}
          aria-label={`Đang dùng ${reason.name}`}
          onChange={(checked) => void run(() => stockCountReasonApi.update(reason.id, reason.name, checked), 'Không cập nhật được')}
        />
      ),
    },
  ]

  return (
    <Modal open title="Lý do lệch kiểm kê" width={560} onCancel={onClose} footer={null} destroyOnHidden>
      <Flex gap={8} style={{ marginBottom: 12 }}>
        <Input
          value={name}
          maxLength={100}
          placeholder="Lý do mới, ví dụ: Chuột cắn bao"
          onChange={(event) => setName(event.target.value)}
          onPressEnter={add}
        />
        <Button type="primary" icon={<PlusOutlined />} loading={saving} disabled={!name.trim()} onClick={add}>
          Thêm
        </Button>
      </Flex>
      <Table<StockCountReason>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={reasons}
        pagination={false}
        locale={{ emptyText: 'Chưa có lý do nào' }}
      />
    </Modal>
  )
}
