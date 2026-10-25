import { AppstoreAddOutlined, FileExcelOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Flex, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink, CodeText } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { DETAIL_INFO_TAB, DetailDrawer } from '../../components/ui/DetailDrawer'
import type { DetailSection } from '../../components/ui/DetailDrawer'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDateTime, orEmpty } from '../../utils/format'
import { ImportExcelModal } from '../imports/ImportExcelModal'
import { supplierApi, supplierImportApi } from './supplier-api'
import type { Supplier, SupplierFilters } from './supplier-api'
import { SupplierFormModal } from './SupplierFormModal'
import { SupplierProductsPanel } from './SupplierProductsPanel'

/** Tab danh sách SP trong khung chi tiết; menu ⋯ "Sản phẩm cung cấp" mở thẳng tab này. */
const PRODUCTS_TAB = 'products'

const FILTER_FIELDS: FilterField<SupplierFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã, tên hoặc mã số thuế' },
  { type: 'status', name: 'active', activeLabel: 'Đang hoạt động', inactiveLabel: 'Ngừng hoạt động' },
]

export function SupplierListPage() {
  const { message, notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    supplierApi.list,
  )
  const [modalOpen, setModalOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [editing, setEditing] = useState<Supplier | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Supplier | null>(null)
  const [detailTab, setDetailTab] = useState(DETAIL_INFO_TAB)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (supplier: Supplier) => {
    setEditing(supplier)
    setModalOpen(true)
  }

  const openDetail = (supplier: Supplier, tab = DETAIL_INFO_TAB) => {
    setViewing(supplier)
    setDetailTab(tab)
    setDetailOpen(true)
  }

  const notifySuccess = (title: string, supplier: Supplier) =>
    notification.success({ title, description: `${supplier.code} · ${supplier.name}` })

  /** Gọi API cho thao tác trên dòng → báo kết quả → tải lại để khớp backend (không cập nhật tại chỗ). */
  const runRowAction = async (supplier: Supplier, action: () => Promise<unknown>, done: string, failed: string) => {
    try {
      await action()
      notifySuccess(done, supplier)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
    }
  }

  const deactivate = (supplier: Supplier) =>
    runRowAction(
      supplier,
      () => supplierApi.deactivate(supplier.id),
      'Đã ngừng hoạt động nhà cung cấp',
      'Không ngừng hoạt động được nhà cung cấp',
    )

  // Mở lại = PUT đủ trường hiện có + active=true (contract-spec: "PUT có active để mở lại")
  const reactivate = (supplier: Supplier) =>
    runRowAction(
      supplier,
      () =>
        supplierApi.update(supplier.id, {
          name: supplier.name,
          taxCode: supplier.taxCode,
          phone: supplier.phone,
          email: supplier.email,
          address: supplier.address,
          active: true,
        }),
      'Đã mở lại nhà cung cấp',
      'Không mở lại được nhà cung cấp',
    )

  const columns: TableProps<Supplier>['columns'] = [
    {
      title: 'Mã NCC',
      dataIndex: 'code',
      width: 110,
      render: (code: string, supplier) => <CodeLink code={code} onClick={() => openDetail(supplier)} />,
    },
    { title: 'Tên nhà cung cấp', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    { title: 'Mã số thuế', dataIndex: 'taxCode', width: 150, render: (taxCode?: string) => orEmpty(taxCode) },
    { title: 'Số điện thoại', dataIndex: 'phone', width: 140, render: (phone?: string) => orEmpty(phone) },
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
      render: (_, supplier) => (
        <RowActions
          recordLabel={supplier.code}
          onView={() => openDetail(supplier)}
          onEdit={() => openEdit(supplier)}
          extraActions={[
            {
              key: 'products',
              label: 'Sản phẩm cung cấp',
              icon: <AppstoreAddOutlined />,
              onClick: () => openDetail(supplier, PRODUCTS_TAB),
            },
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
          reactivate={supplier.active ? undefined : { label: 'Mở lại hoạt động', onConfirm: () => reactivate(supplier) }}
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
            { key: 'code', label: 'Mã NCC', children: <CodeText code={viewing.code} /> },
            { key: 'name', label: 'Tên nhà cung cấp', children: viewing.name },
            { key: 'taxCode', label: 'Mã số thuế', children: orEmpty(viewing.taxCode) },
            { key: 'phone', label: 'Số điện thoại', children: orEmpty(viewing.phone) },
            { key: 'email', label: 'Email', children: orEmpty(viewing.email) },
            { key: 'address', label: 'Địa chỉ', children: orEmpty(viewing.address) },
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
        title="Nhà cung cấp"
        description="Nhà máy, đơn vị cung cấp cám và các sản phẩm họ được phép cung cấp"
        actions={
          <Flex gap={8}>
            <Button icon={<FileExcelOutlined />} onClick={() => setImportOpen(true)}>
              Nhập từ Excel
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Thêm nhà cung cấp
            </Button>
          </Flex>
        }
      />
      <FilterBar<SupplierFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<Supplier>
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
            noun="nhà cung cấp"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={openCreate}
            createLabel="Thêm nhà cung cấp"
          />
        }
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
        title={viewing ? `${viewing.code} · ${viewing.name}` : ''}
        size={640}
        sections={detailSections}
        onClose={() => setDetailOpen(false)}
        onEdit={() => {
          setDetailOpen(false)
          if (viewing) openEdit(viewing)
        }}
        activeTab={detailTab}
        onTabChange={setDetailTab}
        tabs={
          viewing
            ? [
                {
                  key: PRODUCTS_TAB,
                  label: 'Sản phẩm cung cấp',
                  // key: sang NCC khác thì tạo lại panel, không giữ ô tìm kiếm/trang của NCC trước
                  children: <SupplierProductsPanel key={viewing.id} supplier={viewing} />,
                },
              ]
            : []
        }
      />
      <ImportExcelModal
        open={importOpen}
        noun="nhà cung cấp"
        api={supplierImportApi}
        onClose={() => setImportOpen(false)}
        onImported={reload}
      />
    </>
  )
}
