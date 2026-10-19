import { PlusOutlined } from '@ant-design/icons'
import { App, Button, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { DetailDrawer } from '../../components/ui/DetailDrawer'
import type { DetailSection } from '../../components/ui/DetailDrawer'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDateTime, orEmpty } from '../../utils/format'
import { CUSTOMER_TYPE_OPTIONS, customerApi, formatPhone } from './customer-api'
import type { Customer, CustomerFilters, CustomerType } from './customer-api'
import { CustomerFormModal } from './CustomerFormModal'
import { CustomerTypeTag } from './CustomerTypeTag'

const FILTER_FIELDS: FilterField<CustomerFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã, tên hoặc số điện thoại' },
  { type: 'select', name: 'type', placeholder: 'Loại khách', options: CUSTOMER_TYPE_OPTIONS, width: 150 },
  { type: 'status', name: 'active', activeLabel: 'Đang hoạt động', inactiveLabel: 'Ngừng hoạt động' },
]

export function CustomerListPage() {
  const { message, notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    customerApi.list,
  )
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Customer | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (customer: Customer) => {
    setEditing(customer)
    setModalOpen(true)
  }

  const openDetail = (customer: Customer) => {
    setViewing(customer)
    setDetailOpen(true)
  }

  const notifySuccess = (title: string, customer: Customer) =>
    notification.success({ title, description: `${customer.code} · ${customer.name}` })

  /** Gọi API cho thao tác trên dòng → báo kết quả → tải lại để khớp backend (không cập nhật tại chỗ). */
  const runRowAction = async (customer: Customer, action: () => Promise<unknown>, done: string, failed: string) => {
    try {
      await action()
      notifySuccess(done, customer)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
    }
  }

  const deactivate = (customer: Customer) =>
    runRowAction(
      customer,
      () => customerApi.deactivate(customer.id),
      'Đã ngừng hoạt động khách hàng',
      'Không ngừng hoạt động được khách hàng',
    )

  // Mở lại = PUT đủ trường hiện có + active=true — thiếu trường nào là xóa mất trường đó
  const reactivate = (customer: Customer) =>
    runRowAction(
      customer,
      () =>
        customerApi.update(customer.id, {
          name: customer.name,
          type: customer.type,
          phone: customer.phone,
          contactName: customer.contactName,
          address: customer.address,
          note: customer.note,
          active: true,
        }),
      'Đã mở lại khách hàng',
      'Không mở lại được khách hàng',
    )

  const columns: TableProps<Customer>['columns'] = [
    {
      title: 'Mã KH',
      dataIndex: 'code',
      width: 110,
      render: (code: string, customer) => <CodeLink code={code} onClick={() => openDetail(customer)} />,
    },
    { title: 'Tên khách hàng', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    {
      title: 'Loại khách',
      dataIndex: 'type',
      width: 120,
      render: (type: CustomerType) => <CustomerTypeTag type={type} />,
    },
    { title: 'Số điện thoại', dataIndex: 'phone', width: 140, render: (phone: string) => formatPhone(phone) },
    { title: 'Người liên hệ', dataIndex: 'contactName', render: (contactName?: string) => orEmpty(contactName) },
    { title: 'Địa chỉ', dataIndex: 'address', ellipsis: true, render: (address?: string) => orEmpty(address) },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, customer) => (
        <RowActions
          recordLabel={customer.code}
          onView={() => openDetail(customer)}
          onEdit={() => openEdit(customer)}
          deactivate={
            customer.active
              ? {
                  label: 'Ngừng hoạt động',
                  confirmTitle: `Ngừng hoạt động khách hàng ${customer.name}?`,
                  onConfirm: () => deactivate(customer),
                }
              : undefined
          }
          reactivate={customer.active ? undefined : { label: 'Mở lại hoạt động', onConfirm: () => reactivate(customer) }}
        />
      ),
    },
  ]

  const detailSections: DetailSection[] = viewing
    ? [
        {
          key: 'general',
          title: 'Thông tin chung',
          items: [
            { key: 'code', label: 'Mã KH', children: <CodeText code={viewing.code} /> },
            { key: 'name', label: 'Tên khách hàng', children: viewing.name },
            { key: 'type', label: 'Loại khách', children: <CustomerTypeTag type={viewing.type} /> },
            { key: 'phone', label: 'Số điện thoại', children: formatPhone(viewing.phone) },
            { key: 'contactName', label: 'Người liên hệ', children: orEmpty(viewing.contactName) },
            { key: 'address', label: 'Địa chỉ', children: orEmpty(viewing.address) },
            { key: 'note', label: 'Ghi chú', children: orEmpty(viewing.note) },
          ],
        },
        {
          key: 'status',
          title: 'Trạng thái',
          items: [{ key: 'active', label: 'Trạng thái', children: <StatusTag active={viewing.active} /> }],
        },
        {
          key: 'system',
          title: 'Thông tin hệ thống',
          items: [
            { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(viewing.createdAt) },
            { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(viewing.updatedAt) },
          ],
        },
      ]
    : []

  return (
    <>
      <PageHeader
        title="Khách hàng"
        description="Đại lý, trang trại và khách lẻ mua cám"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm khách hàng
          </Button>
        }
      />
      <FilterBar<CustomerFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<Customer>
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
            noun="khách hàng"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={openCreate}
            createLabel="Thêm khách hàng"
          />
        }
      />
      <CustomerFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          notifySuccess(editing ? 'Đã cập nhật khách hàng' : 'Đã tạo khách hàng', saved)
          reload()
        }}
      />
      <DetailDrawer
        open={detailOpen}
        title={viewing ? `${viewing.code} · ${viewing.name}` : ''}
        size={640}
        sections={detailSections}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
      />
    </>
  )
}
