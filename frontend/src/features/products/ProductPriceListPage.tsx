import { DollarOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataTable } from '../../components/ui/DataTable'
import { FILTER_BAR_HEIGHT, FilterBar } from '../../components/ui/FilterBar'
import type { FilterField } from '../../components/ui/FilterBar'
import { ListEmpty } from '../../components/ui/ListEmpty'
import { RowActions } from '../../components/ui/RowActions'
import { STATUS_COLUMN_WIDTH, StatusTag } from '../../components/ui/StatusTag'
import { useFilteredList } from '../../hooks/use-filtered-list'
import { formatDateTime, orEmpty } from '../../utils/format'
import { useCan } from '../auth/auth-context'
import { PriceListFormModal } from './PriceListFormModal'
import { PriceListItemsDrawer } from './PriceListItemsDrawer'
import { priceListApi } from './price-list-api'
import type { PriceList, PriceListFilters } from './price-list-api'

const FILTER_FIELDS: FilterField<PriceListFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo tên hoặc mô tả bảng giá' },
  { type: 'status', name: 'active', activeLabel: 'Đang áp dụng', inactiveLabel: 'Ngừng áp dụng' },
]

/**
 * Trang Danh sách các Bảng giá bán (`/product-prices`, BR-PRC-01..07):
 * - Có phân trang + tìm kiếm theo tên/mô tả + lọc trạng thái → quản lý mượt hàng trăm / hàng nghìn bảng giá
 *   mà không bao giờ bị tràn cột ngang.
 * - Bấm vào tên một bảng giá (hoặc nút "Đơn giá sản phẩm") → mở khung danh sách sản phẩm và đơn giá của riêng bảng giá đó.
 */
export function ProductPriceListPage() {
  const { message, notification } = App.useApp()
  // Cùng quyền với bảng giá riêng & phiếu bán: nhân viên bán hàng và quản lý hệ thống tạo / sửa bảng giá
  const canWrite = useCan('sales.write')
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } =
    useFilteredList<PriceList, PriceListFilters>(priceListApi.list)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<PriceList | null>(null)
  const [itemsDrawerOpen, setItemsDrawerOpen] = useState(false)
  const [selectedList, setSelectedList] = useState<PriceList | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (priceList: PriceList) => {
    setEditing(priceList)
    setModalOpen(true)
  }

  const openItems = (priceList: PriceList) => {
    setSelectedList(priceList)
    setItemsDrawerOpen(true)
  }

  const notifySuccess = (title: string, priceList: PriceList) =>
    notification.success({ title, description: priceList.name })

  const runRowAction = async (priceList: PriceList, action: () => Promise<unknown>, done: string, failed: string) => {
    try {
      await action()
      notifySuccess(done, priceList)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
    }
  }

  const deactivate = (priceList: PriceList) =>
    runRowAction(
      priceList,
      () => priceListApi.deactivate(priceList.id),
      'Đã ngừng áp dụng bảng giá',
      'Không ngừng được bảng giá',
    )

  const reactivate = (priceList: PriceList) =>
    runRowAction(
      priceList,
      () =>
        priceListApi.update(priceList.id, {
          name: priceList.name,
          description: priceList.description,
          active: true,
        }),
      'Đã mở lại bảng giá',
      'Không mở lại được bảng giá',
    )

  const columns: TableProps<PriceList>['columns'] = [
    {
      title: 'Tên bảng giá',
      dataIndex: 'name',
      width: 240,
      render: (name: string, priceList) => (
        <Button
          type="link"
          onClick={() => openItems(priceList)}
          style={{ padding: 0, height: 'auto', fontWeight: 600 }}
        >
          {name}
        </Button>
      ),
    },
    {
      title: 'Mô tả',
      dataIndex: 'description',
      ellipsis: { showTitle: true },
      render: (description?: string) => orEmpty(description),
    },
    {
      title: 'Sản phẩm có giá',
      dataIndex: 'itemCount',
      width: 150,
      align: 'center',
      render: (count: number, priceList) => (
        <Button type="link" size="small" onClick={() => openItems(priceList)}>
          <Tag color={count > 0 ? 'blue' : undefined} style={{ marginInlineEnd: 0 }}>
            {count} sản phẩm
          </Tag>
        </Button>
      ),
    },
    {
      title: 'Khách áp dụng',
      dataIndex: 'customerCount',
      width: 140,
      align: 'center',
      render: (count: number) =>
        count > 0 ? <Tag color="green">{count} khách</Tag> : <Typography.Text type="secondary">0 khách</Typography.Text>,
    },
    {
      title: 'Cập nhật',
      dataIndex: 'updatedAt',
      width: 155,
      render: (at: string) => formatDateTime(at),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} activeText="Đang áp dụng" inactiveText="Ngừng áp dụng" />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, priceList) => (
        <RowActions
          recordLabel={priceList.name}
          onView={() => openItems(priceList)}
          onEdit={canWrite ? () => openEdit(priceList) : undefined}
          extraActions={[
            {
              key: 'items',
              label: 'Đơn giá sản phẩm',
              icon: <DollarOutlined />,
              onClick: () => openItems(priceList),
            },
          ]}
          deactivate={
            canWrite && priceList.active
              ? {
                  label: 'Ngừng áp dụng',
                  confirmTitle: `Ngừng áp dụng bảng giá "${priceList.name}"?`,
                  onConfirm: () => deactivate(priceList),
                }
              : undefined
          }
          reactivate={
            !canWrite || priceList.active ? undefined : { label: 'Mở lại bảng giá', onConfirm: () => reactivate(priceList) }
          }
        />
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Bảng giá bán"
        description="Danh sách các bảng giá bán (Giá lẻ, Giá đại lý, Giá trang trại…). Bấm vào một bảng giá để xem và đặt đơn giá cho từng sản phẩm trong bảng giá đó."
        actions={
          canWrite && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Thêm bảng giá
            </Button>
          )
        }
      />
      <FilterBar<PriceListFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<PriceList>
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
            noun="bảng giá"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={canWrite ? openCreate : undefined}
            createLabel="Thêm bảng giá"
          />
        }
      />
      <PriceListFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          notifySuccess(editing ? 'Đã cập nhật bảng giá' : 'Đã tạo bảng giá', saved)
          reload()
          // Mở mới bảng giá → mở luôn khung đặt đơn giá sản phẩm cho bảng giá vừa tạo
          if (!editing) {
            openItems(saved)
          }
        }}
      />
      <PriceListItemsDrawer
        open={itemsDrawerOpen}
        priceList={selectedList}
        onClose={() => setItemsDrawerOpen(false)}
        onChanged={reload}
      />
    </>
  )
}
