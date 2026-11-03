import { PlusOutlined } from '@ant-design/icons'
import { Button, Flex, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { PageHeader } from '../../components/layout/PageHeader'
import { CodeLink } from '../../components/ui/CodeLink'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDate, formatTons } from '../../utils/format'
import { useCan } from '../auth/auth-context'
import { TRIP_STATUS_LABELS, deliveryTripApi } from './delivery-trip-api'
import type { DeliveryTripFilters, DeliveryTripStatus, DeliveryTripSummary } from './delivery-trip-api'
import { DeliveryTripDrawer } from './DeliveryTripDrawer'
import { TripStatusTag } from './TripStatusTag'

const FILTER_FIELDS: FilterField<DeliveryTripFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm mã chuyến, biển số, lái xe' },
  {
    type: 'select',
    name: 'status',
    placeholder: 'Tất cả trạng thái',
    options: (Object.keys(TRIP_STATUS_LABELS) as DeliveryTripStatus[]).map((status) => ({
      value: status,
      label: TRIP_STATUS_LABELS[status],
    })),
    width: 160,
  },
  { type: 'dateRange', from: 'tripDateFrom', to: 'tripDateTo', placeholder: ['Đi giao từ ngày', 'đến ngày'] },
]

/**
 * Chuyến giao hàng (BR-DLV-01..10): chia phiếu bán Nháp theo xe, sắp thứ tự giao, in phiếu chuyến cho lái xe, xuất
 * chuyến để hoàn tất phiếu và trừ tồn. Mọi thao tác nằm trong DeliveryTripDrawer.
 */
export function DeliveryTripListPage() {
  const canWrite = useCan('delivery')
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    deliveryTripApi.list,
  )
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [openedId, setOpenedId] = useState<number | null>(null)
  // Mỗi lần mở là một khung mới (destroyOnHidden + key) — không giữ trạng thái của chuyến trước
  const [drawerKey, setDrawerKey] = useState(0)

  const openTrip = (id: number | null) => {
    setOpenedId(id)
    setDrawerKey((key) => key + 1)
    setDrawerOpen(true)
  }

  const columns: TableProps<DeliveryTripSummary>['columns'] = [
    {
      title: 'Mã chuyến',
      dataIndex: 'code',
      width: 120,
      render: (code: string, trip) => <CodeLink code={code} onClick={() => openTrip(trip.id)} />,
    },
    { title: 'Ngày đi giao', dataIndex: 'tripDate', width: 120, render: (date: string) => formatDate(date) },
    {
      title: 'Xe',
      dataIndex: 'vehiclePlate',
      width: 140,
      render: (plate: string) => <Typography.Text strong>{plate}</Typography.Text>,
    },
    { title: 'Lái xe', dataIndex: 'driverName' },
    { title: 'Điểm giao', dataIndex: 'stopCount', width: 100, align: 'right' },
    {
      title: 'Khối lượng',
      dataIndex: 'totalTons',
      width: 170,
      align: 'right',
      render: (tons: number, trip) => (
        <Flex vertical align="flex-end">
          <span>
            {formatTons(tons)}
            {trip.capacityTons !== undefined && (
              <Typography.Text type="secondary"> / {formatTons(trip.capacityTons)}</Typography.Text>
            )}
          </span>
          {trip.overloadTons !== undefined && (
            <Typography.Text type="warning">Vượt {formatTons(trip.overloadTons)}</Typography.Text>
          )}
        </Flex>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: STATUS_COLUMN_WIDTH,
      render: (status: DeliveryTripStatus) => <TripStatusTag status={status} />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, trip) => (
        <RowActions
          recordLabel={trip.code}
          onView={() => openTrip(trip.id)}
          onEdit={canWrite && trip.status === 'PLANNED' ? () => openTrip(trip.id) : undefined}
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Chuyến giao hàng"
        description="Chia phiếu bán theo xe, sắp thứ tự giao, in phiếu cho lái xe — xuất chuyến thì các phiếu hoàn tất và trừ tồn kho"
        actions={
          canWrite && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openTrip(null)}>
              Lập chuyến
            </Button>
          )
        }
      />
      <FilterBar<DeliveryTripFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<DeliveryTripSummary>
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
            noun="chuyến giao"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={canWrite ? () => openTrip(null) : undefined}
            createLabel="Lập chuyến"
          />
        }
      />
      <DeliveryTripDrawer
        key={drawerKey}
        open={drawerOpen}
        tripId={openedId}
        onClose={() => setDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
