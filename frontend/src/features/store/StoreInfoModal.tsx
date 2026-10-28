import { App, Form, Input } from 'antd'
import { useEffect } from 'react'
import { ApiError } from '../../api/client'
import { FormModal } from '../../components/ui/FormModal'
import { storeInfoApi } from './store-info-api'
import type { StoreInfoInput } from './store-info-api'

interface StoreInfoModalProps {
  open: boolean
  onClose: () => void
  onSaved: () => void
}

/** Sửa thông tin cửa hàng in ở đầu / cuối phiếu (BR-PRT-01). Chỉ Quản lý hệ thống mở được. */
export function StoreInfoModal({ open, onClose, onSaved }: StoreInfoModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<StoreInfoInput>()

  // Điền thông tin hiện có mỗi lần mở; chưa cấu hình thì để trống
  useEffect(() => {
    if (!open) return
    storeInfoApi
      .get()
      .then((info) => {
        if (info.configured) form.setFieldsValue(info)
      })
      .catch((err: unknown) =>
        message.error(err instanceof ApiError ? err.message : 'Không tải được thông tin cửa hàng'),
      )
  }, [open, form, message])

  const submit = async (values: StoreInfoInput) => {
    await storeInfoApi.save(values)
    onSaved()
  }

  return (
    <FormModal<StoreInfoInput>
      open={open}
      title="Thông tin cửa hàng"
      form={form}
      submitText="Lưu"
      onSubmit={submit}
      onClose={onClose}
    >
      <Form.Item name="name" label="Tên cửa hàng" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Cửa hàng cám Minh Phát" />
      </Form.Item>
      <Form.Item name="address" label="Địa chỉ" rules={[{ max: 500 }]}>
        <Input placeholder="Ví dụ: 12 Lý Bôn, TP Thái Bình" />
      </Form.Item>
      <Form.Item name="phone" label="Số điện thoại" rules={[{ max: 50 }]}>
        <Input placeholder="Ví dụ: 0912 345 678" />
      </Form.Item>
      <Form.Item
        name="receiptFooter"
        label="Lời chào cuối phiếu"
        rules={[{ max: 300 }]}
        extra="In ở cuối phiếu bán cho khách. Để trống thì không in."
      >
        <Input placeholder="Ví dụ: Cảm ơn quý khách, hẹn gặp lại!" />
      </Form.Item>
    </FormModal>
  )
}
