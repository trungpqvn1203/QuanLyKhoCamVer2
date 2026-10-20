import { KeyOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Flex, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { EMPTY_TEXT, formatDateTime } from '../../utils/format'
import { ROLE_COLORS, ROLE_LABELS } from '../auth/auth-api'
import type { Role } from '../auth/auth-api'
import { ResetPasswordModal } from './ResetPasswordModal'
import { userApi } from './user-api'
import type { User, UserFilters } from './user-api'
import { UserFormModal } from './UserFormModal'

const FILTER_FIELDS: FilterField<UserFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo tên đăng nhập hoặc họ tên' },
  {
    type: 'select',
    name: 'role',
    placeholder: 'Vai trò',
    options: (Object.keys(ROLE_LABELS) as Role[]).map((role) => ({ value: role, label: ROLE_LABELS[role] })),
    width: 200,
  },
  { type: 'status', name: 'active', activeLabel: 'Đang hoạt động', inactiveLabel: 'Đã khóa' },
]

/** Quản lý tài khoản đăng nhập — chỉ Quản lý hệ thống (route + backend cùng kiểm). */
export function UserListPage() {
  const { notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(userApi.list)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [resetting, setResetting] = useState<User | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (user: User) => {
    setEditing(user)
    setModalOpen(true)
  }

  const columns: TableProps<User>['columns'] = [
    {
      title: 'Tên đăng nhập',
      dataIndex: 'username',
      width: 180,
      render: (username: string) => <Typography.Text strong>{username}</Typography.Text>,
    },
    { title: 'Họ tên', dataIndex: 'fullName' },
    {
      title: 'Vai trò',
      dataIndex: 'roles',
      render: (roles: Role[]) => (
        <Flex gap={4} wrap>
          {roles.map((role) => (
            <Tag key={role} color={ROLE_COLORS[role]} variant="filled" style={{ marginInlineEnd: 0 }}>
              {ROLE_LABELS[role]}
            </Tag>
          ))}
        </Flex>
      ),
    },
    {
      title: 'Đăng nhập gần nhất',
      dataIndex: 'lastLoginAt',
      width: 170,
      render: (at?: string) => (at ? formatDateTime(at) : EMPTY_TEXT),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} activeText="Đang hoạt động" inactiveText="Đã khóa" />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, user) => (
        <RowActions
          recordLabel={user.username}
          onView={() => openEdit(user)}
          onEdit={() => openEdit(user)}
          extraActions={[
            { key: 'reset', label: 'Đặt lại mật khẩu', icon: <KeyOutlined />, onClick: () => setResetting(user) },
          ]}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Người dùng"
        description="Tài khoản đăng nhập và vai trò. Một người có thể kiêm nhiều vai trò"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm tài khoản
          </Button>
        }
      />
      <FilterBar<UserFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<User>
        columns={columns}
        page={page}
        loading={loading}
        rowKey="id"
        onPageChange={changePage}
        error={error}
        onRetry={reload}
        extraReservedHeight={FILTER_BAR_HEIGHT}
        emptyText={
          <ListEmpty
            noun="tài khoản"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={openCreate}
            createLabel="Thêm tài khoản"
          />
        }
      />
      <UserFormModal
        key={editing?.id ?? 'new'}
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          setModalOpen(false)
          notification.success({ title: editing ? 'Đã cập nhật tài khoản' : 'Đã tạo tài khoản', description: saved.username })
          reload()
        }}
      />
      <ResetPasswordModal
        user={resetting}
        onClose={() => setResetting(null)}
        onDone={(user) => {
          setResetting(null)
          notification.success({ title: 'Đã đặt lại mật khẩu', description: `${user.username} phải đăng nhập lại` })
        }}
      />
    </>
  )
}
