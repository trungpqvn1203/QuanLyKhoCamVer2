import { FileExcelOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Flex, Typography } from 'antd'
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
import { EMPTY_TEXT, formatDateTime } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { formatPhone } from '../../utils/phone'
import { ImportExcelModal } from '../imports/ImportExcelModal'
import { employeeApi, employeeImportApi } from './employee-api'
import type { Employee, EmployeeFilters } from './employee-api'
import { EmployeeFormModal } from './EmployeeFormModal'

const FILTER_FIELDS: FilterField<EmployeeFilters>[] = [
  { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã, tên hoặc số điện thoại' },
  { type: 'status', name: 'active', activeLabel: 'Đang làm việc', inactiveLabel: 'Đã nghỉ' },
]

/** Đơn giá không có = không hưởng lương theo cách đó (BR-EMP-02), hiện "—" thay vì 0 đ để khỏi hiểu nhầm. */
function rateText(rate: number | undefined, unit: string): string {
  return rate == null ? EMPTY_TEXT : `${formatVnd(rate)}/${unit}`
}

export function EmployeeListPage() {
  const { message, notification } = App.useApp()
  const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(
    employeeApi.list,
  )
  const [modalOpen, setModalOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [editing, setEditing] = useState<Employee | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [viewing, setViewing] = useState<Employee | null>(null)

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (employee: Employee) => {
    setEditing(employee)
    setModalOpen(true)
  }

  const openDetail = (employee: Employee) => {
    setViewing(employee)
    setDetailOpen(true)
  }

  const notifySuccess = (title: string, employee: Employee) =>
    notification.success({ title, description: `${employee.code} · ${employee.name}` })

  const runRowAction = async (employee: Employee, action: () => Promise<unknown>, done: string, failed: string) => {
    try {
      await action()
      notifySuccess(done, employee)
      reload()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : failed)
    }
  }

  const deactivate = (employee: Employee) =>
    runRowAction(employee, () => employeeApi.deactivate(employee.id), 'Đã chuyển sang Đã nghỉ', 'Không cập nhật được nhân viên')

  // Mở lại = PUT đủ trường hiện có + active=true — thiếu đơn giá nào là xóa mất đơn giá đó
  const reactivate = (employee: Employee) =>
    runRowAction(
      employee,
      () =>
        employeeApi.update(employee.id, {
          name: employee.name,
          phone: employee.phone,
          dailyWage: employee.dailyWage ?? null,
          tonRate: employee.tonRate ?? null,
          active: true,
        }),
      'Đã cho nhân viên làm lại',
      'Không cập nhật được nhân viên',
    )

  const columns: TableProps<Employee>['columns'] = [
    {
      title: 'Mã NV',
      dataIndex: 'code',
      width: 110,
      render: (code: string, employee) => <CodeLink code={code} onClick={() => openDetail(employee)} />,
    },
    { title: 'Họ tên', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    {
      title: 'Số điện thoại',
      dataIndex: 'phone',
      width: 140,
      render: (phone?: string) => (phone ? formatPhone(phone) : EMPTY_TEXT),
    },
    {
      title: 'Lương ngày công',
      dataIndex: 'dailyWage',
      width: 170,
      align: 'right',
      render: (rate?: number) => rateText(rate, 'ngày'),
    },
    {
      title: 'Đơn giá bốc vác',
      dataIndex: 'tonRate',
      width: 170,
      align: 'right',
      render: (rate?: number) => rateText(rate, 'tấn'),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: STATUS_COLUMN_WIDTH,
      render: (active: boolean) => <StatusTag active={active} activeText="Đang làm" inactiveText="Đã nghỉ" />,
    },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      fixed: 'right',
      render: (_, employee) => (
        <RowActions
          recordLabel={employee.code}
          onView={() => openDetail(employee)}
          onEdit={() => openEdit(employee)}
          deactivate={
            employee.active
              ? {
                  label: 'Cho nghỉ việc',
                  confirmTitle: `Chuyển ${employee.name} sang Đã nghỉ? Lịch sử chấm công vẫn được giữ.`,
                  onConfirm: () => deactivate(employee),
                }
              : undefined
          }
          reactivate={employee.active ? undefined : { label: 'Cho làm lại', onConfirm: () => reactivate(employee) }}
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
            { key: 'code', label: 'Mã NV', children: <CodeText code={viewing.code} /> },
            { key: 'name', label: 'Họ tên', children: viewing.name },
            { key: 'phone', label: 'Số điện thoại', children: viewing.phone ? formatPhone(viewing.phone) : EMPTY_TEXT },
          ],
        },
        {
          key: 'pay',
          title: 'Cách tính lương',
          items: [
            { key: 'dailyWage', label: 'Lương ngày công', children: rateText(viewing.dailyWage, 'ngày') },
            { key: 'tonRate', label: 'Đơn giá bốc vác', children: rateText(viewing.tonRate, 'tấn') },
          ],
        },
        {
          key: 'status',
          title: 'Trạng thái',
          items: [
            {
              key: 'active',
              label: 'Trạng thái',
              children: <StatusTag active={viewing.active} activeText="Đang làm" inactiveText="Đã nghỉ" />,
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
        title="Nhân viên"
        description="Nhân viên kho, công nhân bốc vác và cách tính lương của từng người"
        actions={
          <Flex gap={8}>
            <Button icon={<FileExcelOutlined />} onClick={() => setImportOpen(true)}>
              Nhập từ Excel
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Thêm nhân viên
            </Button>
          </Flex>
        }
      />
      <FilterBar<EmployeeFilters> fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
      <DataTable<Employee>
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
            noun="nhân viên"
            filtering={filtering}
            onClearFilters={() => applyFilters({})}
            onCreate={openCreate}
            createLabel="Thêm nhân viên"
          />
        }
      />
      <EmployeeFormModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(saved) => {
          notifySuccess(editing ? 'Đã cập nhật nhân viên' : 'Đã thêm nhân viên', saved)
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
      <ImportExcelModal
        open={importOpen}
        noun="nhân viên"
        api={employeeImportApi}
        onClose={() => setImportOpen(false)}
        onImported={reload}
      />
    </>
  )
}
