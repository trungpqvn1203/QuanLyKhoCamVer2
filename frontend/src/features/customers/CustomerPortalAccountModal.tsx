import { CopyOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons'
import { Alert, App, Button, Descriptions, Flex, Form, Input, Modal, Skeleton, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { formatDateTime } from '../../utils/format'
import type { Customer } from './customer-api'
import { portalAccountApi } from './portal-account-api'
import type { PortalAccount } from './portal-account-api'

interface CustomerPortalAccountModalProps {
  customer: Customer
  onClose: () => void
}

interface PasswordValues {
  password: string
}

/** Đường dẫn app khách gửi qua Zalo / SMS (ADR-0005). */
const portalUrl = () => `${window.location.origin}/khach`

/**
 * Tài khoản app của một khách (BR-POR-02, 10): tạo với mật khẩu ban đầu (khách phải đổi khi đăng nhập lần đầu),
 * đặt lại mật khẩu, khóa / mở khóa. Mọi thao tác backend kiểm lại quyền (bán hàng + quản lý hệ thống).
 */
export function CustomerPortalAccountModal({ customer, onClose }: CustomerPortalAccountModalProps) {
  const { message, modal } = App.useApp()
  const [form] = Form.useForm<PasswordValues>()
  const [account, setAccount] = useState<PortalAccount | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let ignore = false
    portalAccountApi
      .get(customer.id)
      .then((loaded) => {
        if (!ignore) setAccount(loaded)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được tài khoản app')
      })
    return () => {
      ignore = true
    }
  }, [customer.id])

  const run = async (action: () => Promise<PortalAccount>, success: string) => {
    setBusy(true)
    setError(null)
    try {
      setAccount(await action())
      form.resetFields()
      message.success(success)
    } catch (err) {
      const fieldErrors = toFormFieldErrors(err)
      if (fieldErrors.length > 0) form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      else setError(err instanceof ApiError ? err.message : 'Đã có lỗi xảy ra')
    } finally {
      setBusy(false)
    }
  }

  const submitPassword = (values: PasswordValues) =>
    account?.exists
      ? run(() => portalAccountApi.resetPassword(customer.id, values.password), 'Đã đặt lại mật khẩu')
      : run(() => portalAccountApi.create(customer.id, values.password), 'Đã tạo tài khoản app')

  const confirmLock = () =>
    modal.confirm({
      title: `Khóa tài khoản app của ${customer.name}?`,
      content: 'Khách bị đăng xuất trên mọi thiết bị và không đặt được đơn mới. Các đơn đã có giữ nguyên.',
      okText: 'Khóa',
      okButtonProps: { danger: true },
      cancelText: 'Không',
      onOk: () => run(() => portalAccountApi.lock(customer.id), 'Đã khóa tài khoản app'),
    })

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(portalUrl())
      message.success('Đã chép đường dẫn app — dán vào Zalo gửi khách')
    } catch {
      message.info(portalUrl())
    }
  }

  const status = (current: PortalAccount) => {
    if (!current.active) return <Tag color="error">Đã khóa</Tag>
    if (current.lockedUntil) return <Tag color="warning">Khóa tạm đến {formatDateTime(current.lockedUntil)}</Tag>
    return <Tag color="success">Đang hoạt động</Tag>
  }

  return (
    <Modal open title={`Tài khoản app · ${customer.name}`} footer={null} onCancel={onClose} width={520}>
      {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 12 }} />}
      {!account ? (
        !error && <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <Flex vertical gap={16}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Khách đăng nhập app bằng số điện thoại <Typography.Text strong>{customer.phone}</Typography.Text> và mật
            khẩu cửa hàng đặt; lần đầu đăng nhập khách phải tự đổi mật khẩu.
          </Typography.Paragraph>
          {account.exists && (
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'status', label: 'Trạng thái', children: status(account) },
                {
                  key: 'password',
                  label: 'Mật khẩu',
                  children: account.mustChangePassword ? 'Khách chưa đổi mật khẩu cửa hàng cấp' : 'Khách đã đặt mật khẩu riêng',
                },
                {
                  key: 'lastLogin',
                  label: 'Đăng nhập gần nhất',
                  children: account.lastLoginAt ? formatDateTime(account.lastLoginAt) : 'Chưa đăng nhập',
                },
              ]}
            />
          )}
          <Form<PasswordValues> form={form} layout="vertical" onFinish={submitPassword} disabled={busy} requiredMark={false}>
            <Form.Item
              name="password"
              label={account.exists ? 'Đặt lại mật khẩu' : 'Mật khẩu ban đầu'}
              extra={account.exists ? 'Khách bị đăng xuất trên mọi thiết bị và phải đổi lại mật khẩu' : undefined}
              rules={[
                { required: true, message: 'Nhập mật khẩu' },
                { min: 8, message: 'Tối thiểu 8 ký tự' },
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={busy} disabled={!customer.active && !account.exists}>
              {account.exists ? 'Đặt lại mật khẩu' : 'Tạo tài khoản app'}
            </Button>
          </Form>
          <Flex gap={8} wrap>
            <Button icon={<CopyOutlined />} onClick={() => void copyLink()}>
              Chép đường dẫn app
            </Button>
            {account.exists &&
              (account.active ? (
                <Button danger icon={<LockOutlined />} disabled={busy} onClick={confirmLock}>
                  Khóa tài khoản
                </Button>
              ) : (
                <Button
                  icon={<UnlockOutlined />}
                  disabled={busy}
                  onClick={() => void run(() => portalAccountApi.unlock(customer.id), 'Đã mở khóa tài khoản app')}
                >
                  Mở khóa
                </Button>
              ))}
          </Flex>
        </Flex>
      )}
    </Modal>
  )
}
