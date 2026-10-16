import { AppstoreAddOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Drawer, Typography, theme } from 'antd'
import type { DescriptionsProps, TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { DetailDrawer } from '../../components/ui/DetailDrawer'
import { RowActions } from '../../components/ui/RowActions'
import { StatusTag } from '../../components/ui/StatusTag'
import { usePagedList } from '../../hooks/use-paged-list'
import { formatDateTime } from '../../utils/format'
import { supplierApi } from './supplier-api'
import type { Supplier } from './supplier-api'
import { SupplierFormModal } from './SupplierFormModal'
import { SupplierProductsPanel } from './SupplierProductsPanel'

/** Trường tùy chọn bị bỏ khỏi JSON khi trống → hiện gạch ngang thay vì ô rỗng. */
const EMPTY = '—'

export function SupplierListPage() {
  const { message, notification } = App.useApp()
  const { token } = theme.useToken()
  const { page, loading, error, changePage, reload } = usePagedList(supplierApi.list)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Supplier | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Supplier | null>(null)
  // Khung riêng chỉ để gán/gỡ SP, mở từ menu ⋯ (không phải cuộn qua phần thông tin)
  const [linksOpen, setLinksOpen] = useState(false)
  const [linking, setLinking] = useState<Supplier | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (supplier: Supplier) => {
    setEditing(supplier)
    setModalOpen(true)
  }

  const openDetail = (supplier: Supplier) => {
    setViewing(supplier)
    setDetailOpen(true)
  }

  const openLinks = (supplier: Supplier) => {
    setLinking(supplier)
    setLinksOpen(true)
  }

  const notifySuccess = (title: string, supplier: Supplier) =>
    notification.success({ title, description: `${supplier.code} · ${supplier.name}` })

  const deactivate = async (supplier: Supplier) => {
    try {
      await supplierApi.deactivate(supplier.id)
      notifySuccess('Đã ngừng hoạt động nhà cung cấp', supplier)
      reload()
    } catch (error) {
      message.error(error instanceof ApiError ? error.message : 'Không ngừng hoạt động được nhà cung cấp')
    }
  }

  const columns: TableProps<Supplier>['columns'] = [
    {
      title: 'Mã NCC',
      dataIndex: 'code',
      width: 110,
      render: (code: string, supplier) => (
        <Typography.Link onClick={() => openDetail(supplier)} style={{ fontFamily: token.fontFamilyCode }}>
          {code}
        </Typography.Link>
      ),
    },
    { title: 'Tên nhà cung cấp', dataIndex: 'name' },
    { title: 'Người liên hệ', dataIndex: 'contactName' },
    { title: 'Số điện thoại', dataIndex: 'phone', render: (phone?: string) => phone ?? EMPTY },
    { title: 'Trạng thái', dataIndex: 'active', render: (active: boolean) => <StatusTag active={active} /> },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, supplier) => (
        <RowActions
          onView={() => openDetail(supplier)}
          onEdit={() => openEdit(supplier)}
          extraActions={[
            { key: 'products', label: 'Sản phẩm cung cấp', icon: <AppstoreAddOutlined />, onClick: () => openLinks(supplier) },
          ]}
          deactivate={
            supplier.active
              ? {
                  label: 'Ngừng hoạt động',
                  confirmTitle: `Ngừng hoạt động nhà cung cấp ${supplier.name}?`,
                  onConfirm: () => deactivate(supplier),
                }
              : undefined
          }
        />
      ),
    },
  ]

  const detailItems: DescriptionsProps['items'] = viewing
    ? [
        { key: 'code', label: 'Mã NCC', children: <span style={{ fontFamily: token.fontFamilyCode }}>{viewing.code}</span> },
        { key: 'name', label: 'Tên nhà cung cấp', children: viewing.name },
        { key: 'contactName', label: 'Người liên hệ', children: viewing.contactName },
        { key: 'phone', label: 'Số điện thoại', children: viewing.phone ?? EMPTY },
        { key: 'email', label: 'Email', children: viewing.email ?? EMPTY },
        { key: 'address', label: 'Địa chỉ', children: viewing.address ?? EMPTY },
        { key: 'status', label: 'Trạng thái', children: <StatusTag active={viewing.active} /> },
        { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(viewing.createdAt) },
        { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(viewing.updatedAt) },
      ]
    : []

  return (
    <>
      <PageHeader
        title="Nhà cung cấp"
        description="Nhà máy, đơn vị cung cấp cám và các sản phẩm họ được phép cung cấp"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Thêm nhà cung cấp
          </Button>
        }
      />
      <DataTable<Supplier>
        columns={columns}
        page={page}
        loading={loading}
        rowKey="id"
        onPageChange={changePage}
        error={error}
        onRetry={reload}
      />
      <SupplierFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          notifySuccess(editing ? 'Đã cập nhật nhà cung cấp' : 'Đã tạo nhà cung cấp', saved)
          reload()
        }}
      />
      <DetailDrawer
        open={detailOpen}
        title="Chi tiết nhà cung cấp"
        size={640}
        items={detailItems}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
      >
        {/* key: sang NCC khác thì tạo lại khung, không giữ ô tìm kiếm/trang của NCC trước */}
        {viewing && <SupplierProductsPanel key={viewing.id} supplier={viewing} title="Sản phẩm được cung cấp" />}
      </DetailDrawer>
      <Drawer
        open={linksOpen}
        title={linking ? `Sản phẩm cung cấp · ${linking.code} ${linking.name}` : ''}
        size={640}
        onClose={() => setLinksOpen(false)}
        destroyOnHidden
      >
        {linking && <SupplierProductsPanel key={linking.id} supplier={linking} />}
      </Drawer>
    </>
  )
}
