import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CloseOutlined,
  DeleteOutlined,
  HolderOutlined,
  PlusOutlined,
  PrinterOutlined,
  SaveOutlined,
  SearchOutlined,
  SendOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Col,
  Descriptions,
  Drawer,
  Flex,
  Form,
  Input,
  Row,
  Select,
  Skeleton,
  Table,
  Tooltip,
  Typography,
  theme,
} from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { DragEvent } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { CodeText } from '../../components/ui/CodeLink'
import { EMPTY_TEXT, formatDate, formatDateTime, formatQuantity, formatTons, orEmpty, todayIso } from '../../utils/format'
import { formatPhone } from '../../utils/phone'
import { useCan } from '../auth/auth-context'
import { SalesStatusTag } from '../sales-orders/SalesStatusTag'
import { storeInfoApi } from '../store/store-info-api'
import { vehicleApi, vehicleLabel } from '../vehicles/vehicle-api'
import type { Vehicle } from '../vehicles/vehicle-api'
import { deliveryTripApi } from './delivery-trip-api'
import type { DeliveryOrder, DeliveryTrip, DeliveryTripInput, Driver } from './delivery-trip-api'
import { TripStatusTag } from './TripStatusTag'
import {
  addStop,
  availableOrders,
  matchesKeyword,
  moveStop,
  overloadTons,
  removeStop,
  stopsChanged,
  totalTons,
} from './trip-stops'

const DRAWER_WIDTH = 1180
const REFERENCE_SIZE = 100

interface HeaderValues {
  tripDate: string
  vehicleId?: number
  driverId?: number
  note?: string
}

interface DeliveryTripDrawerProps {
  open: boolean
  /** null = lập chuyến mới */
  tripId: number | null
  onClose: () => void
  /** Chuyến được lưu / xuất / xóa → trang danh sách tải lại */
  onChanged: () => void
}

function headerValues(trip: DeliveryTrip | null, vehicles: Vehicle[]): HeaderValues {
  if (trip) {
    return { tripDate: trip.tripDate, vehicleId: trip.vehicleId, driverId: trip.driverId, note: trip.note }
  }
  // Chỉ có một xe thì chọn sẵn — cửa hàng nhỏ thường chỉ một xe
  return { tripDate: todayIso(), vehicleId: vehicles.length === 1 ? vehicles[0].id : undefined }
}

/** Dòng hàng gọn: "120 bao Cám heo 25kg" */
function LinesCell({ order }: { order: DeliveryOrder }) {
  return (
    <Flex vertical>
      {order.lines.map((line) => (
        <span key={line.productId}>
          {formatQuantity(line.quantity)} {line.unitName.toLowerCase()} {line.productName}
        </span>
      ))}
      {order.note && (
        <Typography.Text type="secondary" italic>
          {order.note}
        </Typography.Text>
      )}
    </Flex>
  )
}

function CustomerCell({ order }: { order: DeliveryOrder }) {
  return (
    <Flex vertical>
      <Typography.Text strong>{order.customerName}</Typography.Text>
      {order.customerPhone && <Typography.Text type="secondary">{formatPhone(order.customerPhone)}</Typography.Text>}
      <Typography.Text type={order.customerAddress ? 'secondary' : 'warning'}>
        {order.customerAddress ?? 'Chưa có địa chỉ'}
      </Typography.Text>
    </Flex>
  )
}

/**
 * Khung chuyến giao (BR-DLV-01..10): lập / sửa chuyến Đang xếp — ngày, xe, lái xe, chọn phiếu bán Nháp và sắp thứ
 * tự giao (kéo thả hoặc nút lên / xuống) — rồi in phiếu chuyến và Xuất chuyến (hoàn tất mọi phiếu, trừ tồn).
 * Chuyến Đã xuất, hoặc người không có quyền, chỉ xem và in.
 */
export function DeliveryTripDrawer({ open, tripId, onClose, onChanged }: DeliveryTripDrawerProps) {
  const { message, modal, notification } = App.useApp()
  const { token } = theme.useToken()
  const canWrite = useCan('delivery')
  const [form] = Form.useForm<HeaderValues>()
  const [trip, setTrip] = useState<DeliveryTrip | null>(null)
  const [stops, setStops] = useState<DeliveryOrder[]>([])
  const [assignable, setAssignable] = useState<DeliveryOrder[]>([])
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [drivers, setDrivers] = useState<Driver[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [busy, setBusy] = useState<'save' | 'dispatch' | 'delete' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [keyword, setKeyword] = useState('')
  const dragIndex = useRef<number | null>(null)
  const [dropIndex, setDropIndex] = useState<number | null>(null)
  const watchedVehicleId = Form.useWatch('vehicleId', form)

  const editable = canWrite && (trip === null || trip.status === 'PLANNED')

  useEffect(() => {
    let ignore = false
    const query = { page: 0, size: REFERENCE_SIZE }
    Promise.all([
      tripId === null ? Promise.resolve(null) : deliveryTripApi.get(tripId),
      // Phiếu chờ xếp, xe, lái xe chỉ cần khi được lập / sửa chuyến; lái xe là dữ liệu nhân viên (BR-DLV-10)
      canWrite ? deliveryTripApi.assignableOrders() : Promise.resolve([]),
      canWrite ? vehicleApi.list(query, { active: true }).then((result) => result.items) : Promise.resolve([]),
      canWrite ? deliveryTripApi.drivers() : Promise.resolve([]),
    ])
      .then(([loadedTrip, loadedAssignable, loadedVehicles, loadedDrivers]) => {
        if (ignore) return
        setTrip(loadedTrip)
        setStops(loadedTrip?.stops ?? [])
        setAssignable(loadedAssignable)
        setVehicles(loadedVehicles)
        setDrivers(loadedDrivers)
        setLoadError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setLoadError(err instanceof ApiError ? err.message : 'Không tải được chuyến giao')
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [tripId, canWrite, reloadKey])

  // Xe / lái xe của chuyến cũ có thể đã ngừng → vẫn hiện trong ô chọn để không mất giá trị đang có
  const vehicleOptions = useMemo(() => {
    const options = vehicles.map((vehicle) => ({ value: vehicle.id, label: vehicleLabel(vehicle) }))
    if (trip && !vehicles.some((vehicle) => vehicle.id === trip.vehicleId)) {
      options.push({ value: trip.vehicleId, label: `${trip.vehiclePlate} (đang ngừng dùng)` })
    }
    return options
  }, [vehicles, trip])

  const driverOptions = useMemo(() => {
    const options = drivers.map((driver) => ({ value: driver.id, label: `${driver.code} · ${driver.name}` }))
    if (trip && !drivers.some((driver) => driver.id === trip.driverId)) {
      options.push({ value: trip.driverId, label: `${trip.driverCode} · ${trip.driverName} (đã nghỉ)` })
    }
    return options
  }, [drivers, trip])

  const capacityTons = editable
    ? (vehicles.find((vehicle) => vehicle.id === watchedVehicleId)?.capacityTons ??
      (trip && trip.vehicleId === watchedVehicleId ? trip.capacityTons : undefined))
    : trip?.capacityTons
  const tons = totalTons(stops)
  const overload = overloadTons(capacityTons, tons)
  const brokenStops = stops.filter((stop) => stop.documentStatus !== 'DRAFT')
  const waiting = availableOrders(assignable, trip?.stops ?? [], stops).filter((order) => matchesKeyword(order, keyword))

  const isDirty = () => stopsChanged(trip?.stops ?? [], stops) || form.isFieldsTouched()

  const handleError = (err: unknown) => {
    const fieldErrors = toFormFieldErrors(err)
    if (fieldErrors.length > 0) form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
    setErrorMessage(err instanceof ApiError ? err.message : 'Đã có lỗi xảy ra')
  }

  /** Sau khi lưu / xuất: chuyến mới nhất từ backend là bản gốc; phiếu chờ xếp tải lại (phiếu vừa xếp biến mất). */
  const applySaved = async (saved: DeliveryTrip) => {
    // Form có key theo updatedAt → dựng lại với giá trị vừa lưu, hết trạng thái "đã sửa"
    setTrip(saved)
    setStops(saved.stops)
    onChanged()
    try {
      setAssignable(await deliveryTripApi.assignableOrders())
    } catch {
      // giữ danh sách cũ — lần mở sau sẽ đúng
    }
  }

  const save = async () => {
    let values: HeaderValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    const input: DeliveryTripInput = {
      tripDate: values.tripDate,
      vehicleId: values.vehicleId as number,
      driverId: values.driverId as number,
      note: values.note?.trim() || undefined,
      documentIds: stops.map((stop) => stop.documentId),
    }
    setBusy('save')
    setErrorMessage(null)
    try {
      const saved = trip ? await deliveryTripApi.update(trip.id, input) : await deliveryTripApi.create(input)
      await applySaved(saved)
      notification.success({
        title: trip ? 'Đã lưu chuyến giao' : 'Đã lập chuyến giao',
        description: `${saved.code} · ${saved.stopCount} điểm giao · ${formatTons(saved.totalTons)}`,
      })
    } catch (err) {
      handleError(err)
    } finally {
      setBusy(null)
    }
  }

  const confirmDispatch = () => {
    if (!trip) return
    if (isDirty()) {
      message.warning('Chuyến có thay đổi chưa lưu — bấm Lưu chuyến trước khi xuất')
      return
    }
    modal.confirm({
      title: `Xuất chuyến ${trip.code}?`,
      content: (
        <Flex vertical gap={8}>
          <span>
            {trip.stopCount} phiếu bán sẽ <b>Hoàn tất và trừ tồn kho ngay</b>. Không hoàn tác được — khách không nhận
            hàng thì đảo phiếu bán đó.
          </span>
          {trip.overloadTons !== undefined && (
            <Typography.Text type="warning">Chuyến vượt tải trọng xe {formatTons(trip.overloadTons)}.</Typography.Text>
          )}
        </Flex>
      ),
      okText: 'Xuất chuyến',
      cancelText: 'Chưa xuất',
      onOk: async () => {
        setBusy('dispatch')
        setErrorMessage(null)
        try {
          const dispatched = await deliveryTripApi.dispatch(trip.id)
          await applySaved(dispatched)
          notification.success({
            title: 'Đã xuất chuyến',
            description: `${dispatched.code} · ${dispatched.stopCount} phiếu bán đã hoàn tất, đã trừ tồn kho`,
          })
        } catch (err) {
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  const confirmDelete = () => {
    if (!trip) return
    modal.confirm({
      title: `Xóa chuyến ${trip.code}?`,
      content: 'Các phiếu bán trong chuyến trở về danh sách chờ xếp, không ảnh hưởng tồn kho.',
      okText: 'Xóa chuyến',
      okButtonProps: { danger: true },
      cancelText: 'Không',
      onOk: async () => {
        setBusy('delete')
        try {
          await deliveryTripApi.remove(trip.id)
          notification.success({ title: 'Đã xóa chuyến giao', description: trip.code })
          onChanged()
          onClose()
        } catch (err) {
          handleError(err)
        } finally {
          setBusy(null)
        }
      },
    })
  }

  const print = async () => {
    if (!trip) return
    if (editable && isDirty()) {
      message.warning('Chuyến có thay đổi chưa lưu — bấm Lưu chuyến trước khi in')
      return
    }
    try {
      // Tải lười: react-dom/server + mẫu in chỉ tải khi bấm In
      const [{ printK80 }, { TripSheet }, store] = await Promise.all([
        import('../../utils/print'),
        import('./print/TripSheet'),
        storeInfoApi.get(),
      ])
      const printedAt = formatDateTime(new Date().toISOString())
      printK80(`Phiếu chuyến ${trip.code}`, <TripSheet trip={trip} store={store} printedAt={printedAt} />)
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không in được phiếu chuyến')
    }
  }

  const requestClose = () => {
    if (busy) return
    if (!editable || !isDirty()) {
      onClose()
      return
    }
    modal.confirm({
      title: 'Bỏ các thay đổi chưa lưu?',
      content: 'Thứ tự và danh sách phiếu vừa sắp sẽ mất.',
      okText: 'Bỏ thay đổi',
      okButtonProps: { danger: true },
      cancelText: 'Tiếp tục sửa',
      onOk: onClose,
    })
  }

  // ===== Bảng điểm giao (kéo thả bằng HTML5 drag & drop — không thêm thư viện) =====

  const stopColumns: TableProps<DeliveryOrder>['columns'] = [
    ...(editable
      ? [
          {
            key: 'drag',
            width: 32,
            render: () => (
              <HolderOutlined style={{ cursor: 'grab', color: token.colorTextTertiary }} aria-label="Kéo để đổi thứ tự" />
            ),
          },
        ]
      : []),
    {
      title: '#',
      key: 'order',
      width: 44,
      align: 'center' as const,
      render: (_: unknown, __: DeliveryOrder, index: number) => <Typography.Text strong>{index + 1}</Typography.Text>,
    },
    {
      title: 'Phiếu',
      dataIndex: 'documentCode',
      width: 130,
      render: (code: string, order: DeliveryOrder) => (
        <Flex vertical gap={4} align="flex-start">
          <CodeText code={code} />
          {order.documentStatus !== 'DRAFT' && <SalesStatusTag status={order.documentStatus} />}
        </Flex>
      ),
    },
    { title: 'Khách hàng', key: 'customer', render: (_: unknown, order: DeliveryOrder) => <CustomerCell order={order} /> },
    { title: 'Hàng giao', key: 'lines', render: (_: unknown, order: DeliveryOrder) => <LinesCell order={order} /> },
    { title: 'Kho bốc', dataIndex: 'warehouseName', width: 130 },
    {
      title: 'Tấn',
      dataIndex: 'totalTons',
      width: 90,
      align: 'right' as const,
      render: (value: number) => formatTons(value),
    },
    ...(editable
      ? [
          {
            key: 'actions',
            width: 112,
            align: 'right' as const,
            render: (_: unknown, order: DeliveryOrder, index: number) => (
              <Flex gap={2} justify="flex-end">
                <Tooltip title="Lên trên">
                  <Button
                    size="small"
                    type="text"
                    icon={<ArrowUpOutlined />}
                    disabled={index === 0}
                    onClick={() => setStops((current) => moveStop(current, index, index - 1))}
                  />
                </Tooltip>
                <Tooltip title="Xuống dưới">
                  <Button
                    size="small"
                    type="text"
                    icon={<ArrowDownOutlined />}
                    disabled={index === stops.length - 1}
                    onClick={() => setStops((current) => moveStop(current, index, index + 1))}
                  />
                </Tooltip>
                <Tooltip title="Bỏ khỏi chuyến">
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<CloseOutlined />}
                    onClick={() => setStops((current) => removeStop(current, order.documentId))}
                  />
                </Tooltip>
              </Flex>
            ),
          },
        ]
      : []),
  ]

  const dragRowProps = (index: number) =>
    editable
      ? {
          draggable: true,
          onDragStart: (event: DragEvent) => {
            dragIndex.current = index
            event.dataTransfer.effectAllowed = 'move'
          },
          onDragOver: (event: DragEvent) => {
            event.preventDefault()
            if (dropIndex !== index) setDropIndex(index)
          },
          onDragEnd: () => {
            dragIndex.current = null
            setDropIndex(null)
          },
          onDrop: (event: DragEvent) => {
            event.preventDefault()
            const from = dragIndex.current
            if (from !== null) setStops((current) => moveStop(current, from, index))
            dragIndex.current = null
            setDropIndex(null)
          },
          style: {
            cursor: 'grab',
            boxShadow: dropIndex === index ? `inset 0 2px 0 ${token.colorPrimary}` : undefined,
          },
        }
      : {}

  const waitingColumns: TableProps<DeliveryOrder>['columns'] = [
    {
      title: 'Ngày giao',
      dataIndex: 'requestedDeliveryDate',
      width: 105,
      render: (date?: string) => (date ? formatDate(date) : EMPTY_TEXT),
    },
    { title: 'Phiếu', dataIndex: 'documentCode', width: 110, render: (code: string) => <CodeText code={code} /> },
    { title: 'Khách hàng', key: 'customer', render: (_, order) => <CustomerCell order={order} /> },
    { title: 'Hàng giao', key: 'lines', render: (_, order) => <LinesCell order={order} /> },
    { title: 'Kho bốc', dataIndex: 'warehouseName', width: 120 },
    { title: 'Tấn', dataIndex: 'totalTons', width: 80, align: 'right', render: (value: number) => formatTons(value) },
    {
      key: 'add',
      width: 90,
      align: 'right',
      render: (_, order) => (
        <Button size="small" icon={<PlusOutlined />} onClick={() => setStops((current) => addStop(current, order))}>
          Xếp
        </Button>
      ),
    },
  ]

  // ===== Bố cục =====

  const title = trip ? (
    <Flex gap={8} align="center" wrap>
      <span>Chuyến {trip.code}</span>
      <TripStatusTag status={trip.status} />
    </Flex>
  ) : (
    'Lập chuyến giao'
  )

  const footer =
    loading || loadError ? null : (
      <Flex justify="space-between" gap={8} wrap>
        <Flex gap={8}>
          {editable && trip && (
            <Button danger icon={<DeleteOutlined />} disabled={busy !== null} loading={busy === 'delete'} onClick={confirmDelete}>
              Xóa chuyến
            </Button>
          )}
        </Flex>
        <Flex gap={8} wrap>
          <Button onClick={requestClose} disabled={busy !== null}>
            Đóng
          </Button>
          {trip && (
            <Button icon={<PrinterOutlined />} disabled={busy !== null} onClick={() => void print()}>
              In phiếu chuyến
            </Button>
          )}
          {editable && (
            <Button icon={<SaveOutlined />} loading={busy === 'save'} disabled={busy !== null} onClick={() => void save()}>
              {trip ? 'Lưu chuyến' : 'Lập chuyến'}
            </Button>
          )}
          {editable && trip && (
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={busy === 'dispatch'}
              disabled={busy !== null || stops.length === 0}
              onClick={confirmDispatch}
            >
              Xuất chuyến
            </Button>
          )}
        </Flex>
      </Flex>
    )

  const summary = (
    <Flex gap={16} align="center" wrap>
      <Typography.Text>
        <b>{stops.length}</b> điểm giao · <b>{formatTons(tons)}</b>
        {capacityTons !== undefined && <Typography.Text type="secondary"> / tải trọng {formatTons(capacityTons)}</Typography.Text>}
      </Typography.Text>
    </Flex>
  )

  return (
    <Drawer open={open} title={title} size={DRAWER_WIDTH} onClose={requestClose} footer={footer} destroyOnHidden>
      {loading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : loadError ? (
        <Alert
          type="error"
          showIcon
          title={loadError}
          action={
            <Button
              size="small"
              onClick={() => {
                setLoading(true)
                setReloadKey((key) => key + 1)
              }}
            >
              Thử lại
            </Button>
          }
        />
      ) : (
        <Flex vertical gap={16}>
          {errorMessage && <Alert type="error" showIcon closable title={errorMessage} onClose={() => setErrorMessage(null)} />}

          {editable ? (
            <Form<HeaderValues>
              key={trip ? `${trip.id}:${trip.updatedAt}` : 'new'}
              form={form}
              layout="vertical"
              requiredMark={false}
              disabled={busy !== null}
              initialValues={headerValues(trip, vehicles)}
            >
              <Row gutter={16}>
                <Col xs={24} md={5}>
                  <Form.Item name="tripDate" label="Ngày đi giao" rules={[{ required: true, message: 'Chọn ngày đi giao' }]}>
                    <Input type="date" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={7}>
                  <Form.Item name="vehicleId" label="Xe" rules={[{ required: true, message: 'Chọn xe' }]}>
                    <Select
                      options={vehicleOptions}
                      placeholder={vehicleOptions.length === 0 ? 'Chưa có xe — thêm ở menu Xe giao hàng' : 'Chọn xe'}
                      showSearch={{ optionFilterProp: 'label' }}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item name="driverId" label="Lái xe" rules={[{ required: true, message: 'Chọn lái xe' }]}>
                    <Select options={driverOptions} placeholder="Chọn nhân viên" showSearch={{ optionFilterProp: 'label' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item name="note" label="Ghi chú" rules={[{ max: 500 }]}>
                    <Input placeholder="Ví dụ: đi tuyến Vũ Thư" />
                  </Form.Item>
                </Col>
              </Row>
            </Form>
          ) : (
            trip && (
              <Descriptions
                bordered
                size="small"
                column={{ xs: 1, md: 3 }}
                items={[
                  { key: 'date', label: 'Ngày đi giao', children: formatDate(trip.tripDate) },
                  { key: 'vehicle', label: 'Xe', children: vehicleLabel({ ...trip, plateNumber: trip.vehiclePlate, name: trip.vehicleName }) },
                  {
                    key: 'driver',
                    label: 'Lái xe',
                    children: `${trip.driverName}${trip.driverPhone ? ` · ${formatPhone(trip.driverPhone)}` : ''}`,
                  },
                  { key: 'note', label: 'Ghi chú', children: orEmpty(trip.note) },
                  { key: 'created', label: 'Người lập', children: `${trip.createdBy} · ${formatDateTime(trip.createdAt)}` },
                  {
                    key: 'dispatched',
                    label: 'Xuất chuyến',
                    children: trip.dispatchedAt ? `${trip.dispatchedBy} · ${formatDateTime(trip.dispatchedAt)}` : 'Chưa xuất',
                  },
                ]}
              />
            )
          )}

          {overload !== undefined && (
            <Alert
              type="warning"
              showIcon
              title={`Vượt tải trọng xe ${formatTons(overload)}`}
              description="Vẫn lưu và xuất được — cân nhắc chuyển bớt phiếu sang chuyến khác."
            />
          )}
          {trip?.status === 'PLANNED' && brokenStops.length > 0 && (
            <Alert
              type="error"
              showIcon
              title={`${brokenStops.map((stop) => stop.documentCode).join(', ')} không còn là phiếu Nháp`}
              description="Phiếu đã bị hủy hoặc hoàn tất riêng sau khi xếp. Bỏ khỏi chuyến rồi lưu trước khi xuất."
            />
          )}

          <div>
            <Flex justify="space-between" align="center" style={{ marginBottom: 8 }} wrap gap={8}>
              <Typography.Title level={5} style={{ margin: 0 }}>
                Thứ tự giao
              </Typography.Title>
              {summary}
            </Flex>
            {editable && stops.length > 1 && (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
                Kéo dòng hoặc bấm mũi tên để đổi thứ tự — điểm số 1 giao đầu tiên.
              </Typography.Paragraph>
            )}
            <Table<DeliveryOrder>
              columns={stopColumns}
              dataSource={stops}
              rowKey="documentId"
              size="small"
              pagination={false}
              onRow={(_, index) => dragRowProps(index ?? 0)}
              locale={{ emptyText: editable ? 'Chưa có phiếu nào — bấm "Xếp" ở danh sách bên dưới' : 'Chuyến chưa có phiếu nào' }}
            />
          </div>

          {editable && (
            <div>
              <Flex justify="space-between" align="center" style={{ marginBottom: 8 }} wrap gap={8}>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  Phiếu bán chờ xếp chuyến ({waiting.length})
                </Typography.Title>
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  placeholder="Tìm mã phiếu, khách, địa chỉ"
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  style={{ width: 280 }}
                />
              </Flex>
              <Table<DeliveryOrder>
                columns={waitingColumns}
                dataSource={waiting}
                rowKey="documentId"
                size="small"
                pagination={false}
                scroll={{ y: 320 }}
                locale={{
                  emptyText: 'Không có phiếu bán Nháp nào chờ xếp — xác nhận đơn khách đặt hoặc lập phiếu bán trước',
                }}
              />
            </div>
          )}
        </Flex>
      )}
    </Drawer>
  )
}
