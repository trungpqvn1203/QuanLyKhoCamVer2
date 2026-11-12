import { PlusOutlined } from '@ant-design/icons'
import { App, Button } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { DetailDrawer } from '../../components/ui/DetailDrawer'
import type { DetailSection } from '../../components/ui/DetailDrawer'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { EMPTY_TEXT, formatDateTime, formatTons, orEmpty } from '../../utils/format'
import { useCan } from '../auth/auth-context'
import { vehicleApi } from './vehicle-api'
import type { Vehicle, VehicleFilters } from './vehicle-api'
import { VehicleFormModal } from './VehicleFormModal'

const FILTER_FIELDS: FilterField<VehicleFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo biển số hoặc tên xe' },
  { type: 'status', name: 'active', activeLabel: 'Đang dùng', inactiveLabel: 'Ngừng dùng' },
]

/** Danh mục xe giao hàng (BR-VEH-01..04). Quản lý kho + quản lý hệ thống thêm / sửa; vai trò khác chỉ xem. */
export function VehicleListPage() {
  const { message, notification } = App.useApp()
  const canWrite = useCan('delivery')
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(vehicleApi.list)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Vehicle | null>(null)
  // Tách cờ mở khỏi bản ghi: đóng khung thì nội dung vẫn còn trong lúc chạy hiệu ứng trượt ra
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Vehicle | null>(null)

  const openForm = (vehicle: Vehicle | null) => {
    setEditing(vehicle)
    setModalOpen(true)
  }

  const openDetail = (vehicle: Vehicle) => {
    setViewing(vehicle)
    setDetailOpen(true)
  }

  const runRowAction = async (vehicle: Vehicle, action: () => Promise<unknown>, done: string) => {
    try {
      await action()
      notification.success({ title: done, description: vehicle.plateNumber })
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Đã có lỗi xảy ra')
    }
  }

  const columns: TableProps<Vehicle>['columns'] = [
    {
      title: 'Biển số',
      dataIndex: 'plateNumber',
      width: 160,
      render: (plate: string, vehicle) => <CodeLink code={plate} onClick={() => openDetail(vehicle)} />,
    },
    { title: 'Tên xe', dataIndex: 'name', render: (name?: string) => orEmpty(name) },
    {
      title: 'Tải trọng',
      dataIndex: 'capacityTons',
      width: 140,
      align: 'right',
      render: (tons?: number) => (tons === undefined ? EMPTY_TEXT : formatTons(tons)),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} activeText="Đang dùng" inactiveText="Ngừng dùng" />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, vehicle) => (
        <RowActions
          recordLabel={vehicle.plateNumber}
          onView={() => openDetail(vehicle)}
          onEdit={canWrite ? () => openForm(vehicle) : undefined}
          deactivate={
            canWrite && vehicle.active
              ? {
                  label: 'Ngừng dùng',
                  confirmTitle: `Ngừng dùng xe ${vehicle.plateNumber}?`,
                  onConfirm: () => runRowAction(vehicle, () => vehicleApi.deactivate(vehicle.id), 'Đã ngừng dùng xe'),
                }
              : undefined
          }
          reactivate={
            !canWrite || vehicle.active
              ? undefined
              : {
                  label: 'Dùng lại',
                  onConfirm: () =>
                    runRowAction(
                      vehicle,
                      () => vehicleApi.update(vehicle.id, { ...vehicle, active: true }),
                      'Đã dùng lại xe',
                    ),
                }
          }
        />
      ),
    },
  ]

  const detailSections: DetailSection[] = viewing
    ? [
        {
          key: 'general',
          title: 'Thông tin xe',
          items: [
            { key: 'plate', label: 'Biển số', children: viewing.plateNumber },
            { key: 'name', label: 'Tên xe', children: orEmpty(viewing.name) },
            {
              key: 'capacity',
              label: 'Tải trọng',
              children: viewing.capacityTons === undefined ? 'Không ghi (không cảnh báo quá tải)' : formatTons(viewing.capacityTons),
            },
            {
              key: 'active',
              label: 'Trạng thái',
              children: <StatusTag active={viewing.active} activeText="Đang dùng" inactiveText="Ngừng dùng" />,
            },
          ],
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
        title="Xe giao hàng"
        description="Biển số và tải trọng — chuyến giao vượt tải trọng xe sẽ được cảnh báo"
        actions={
          canWrite && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openForm(null)}>
              Thêm xe
            </Button>
          )
        }
      />
      <FilterBar<VehicleFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<Vehicle>
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
            noun="xe"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={canWrite ? () => openForm(null) : undefined}
            createLabel="Thêm xe"
          />
        }
      />
      <VehicleFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          notification.success({ title: editing ? 'Đã cập nhật xe' : 'Đã thêm xe', description: saved.plateNumber })
          reload()
        }}
      />
      <DetailDrawer
        open={detailOpen}
        title={viewing?.plateNumber ?? ''}
        sections={detailSections}
        onClose={() => setDetailOpen(false)}
        onEdit={
          canWrite
            ? () => {
                setDetailOpen(false)
                if (viewing) openForm(viewing)
              }
            : undefined
        }
      />
    </>
  )
}
