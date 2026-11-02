import { Form, Input, InputNumber, Table, Typography } from 'antd'
import type { FormRule as Rule, TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { FormModal } from '../../components/ui/FormModal'
import { formatDate } from '../../utils/format'
import { formatPriceInput, formatVnd, hasAtMostTwoDecimals, lineAmount, parsePriceInput } from '../../utils/money'
import { adjustedTotal, effectivePrice, initialPriceValues, toPriceAdjustmentInput } from './price-adjustment-form'
import type { PriceAdjustmentFormValues } from './price-adjustment-form'
import type { PriceAdjustmentInput, SalesOrder, SalesOrderLine } from './sales-order-api'

interface PriceAdjustmentModalProps {
  open: boolean
  order: SalesOrder
  /** Gọi API; ném ApiError nếu thất bại — FormModal giữ hộp mở và hiện lỗi */
  onSubmit: (input: PriceAdjustmentInput) => Promise<void>
  onClose: () => void
}

const priceRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !hasAtMostTwoDecimals(value)) throw new Error('Tối đa 2 chữ số lẻ')
  },
}

/**
 * Điều chỉnh giá phiếu đã giao mà khách chưa trả đồng nào (BR-SAL-23..26). Ô giá điền sẵn giá hiện tại —
 * chỉ sửa dòng cần đổi; số lượng không đổi được ở đây. Lý do bắt buộc, được ghi vào lịch sử điều chỉnh giá.
 * "Không vượt số đã thu" không cần kiểm: chỉ mở được khi chưa thu đồng nào; backend kiểm lại sau khi khóa phiếu.
 */
export function PriceAdjustmentModal({ open, order, onSubmit, onClose }: PriceAdjustmentModalProps) {
  const [form] = Form.useForm<PriceAdjustmentFormValues>()
  const values = Form.useWatch([], form) as PriceAdjustmentFormValues | undefined
  const newTotal = adjustedTotal(order.lines, values)

  const columns: TableProps<SalesOrderLine>['columns'] = [
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    {
      title: 'SL',
      dataIndex: 'quantity',
      width: 80,
      align: 'right',
      render: (quantity: number, line) => `${quantity} ${line.unitName.toLowerCase()}`,
    },
    { title: 'Giá hiện tại', dataIndex: 'unitPrice', width: 120, align: 'right', render: (price: number) => formatVnd(price) },
    {
      title: 'Giá mới (đ)',
      key: 'newPrice',
      width: 160,
      render: (_, line) => (
        <Form.Item
          name={['prices', String(line.productId)]}
          rules={[{ type: 'number', min: 0, message: 'Không được âm' }, priceRule]}
          style={{ marginBottom: 0 }}
        >
          <InputNumber<number>
            min={0}
            style={{ width: '100%' }}
            formatter={formatPriceInput}
            parser={(text) => Number(parsePriceInput(text))}
            aria-label={`Giá mới ${line.productName}`}
          />
        </Form.Item>
      ),
    },
    {
      title: 'Thành tiền mới',
      key: 'newAmount',
      width: 140,
      align: 'right',
      render: (_, line) => {
        const price = effectivePrice(line, values)
        const amount = formatVnd(lineAmount(line.quantity, price))
        return price === line.unitPrice ? amount : <Typography.Text strong>{amount}</Typography.Text>
      },
    },
  ]

  return (
    <FormModal<PriceAdjustmentFormValues>
      open={open}
      title={`Điều chỉnh giá phiếu ${order.code}`}
      form={form}
      width={860}
      initialValues={initialPriceValues(order.lines)}
      submitText="Điều chỉnh giá"
      onSubmit={(submitted) => onSubmit(toPriceAdjustmentInput(order.lines, submitted))}
      onClose={onClose}
    >
      <Typography.Paragraph type="secondary">
        Phiếu đã giao, {order.customerName} chưa trả đồng nào nên vẫn sửa được giá. Số lượng và tồn kho không đổi;
        công nợ và doanh số ngày {formatDate(order.documentDate)} tính theo giá mới. Khách trả tiền
        rồi thì không điều chỉnh được nữa.
      </Typography.Paragraph>
      <Table<SalesOrderLine>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={order.lines}
        pagination={false}
        scroll={{ x: 'max-content' }}
        style={{ marginBottom: 16 }}
      />
      <Typography.Paragraph>
        Tổng phiếu: {formatVnd(order.totalAmount)} →{' '}
        <Typography.Text strong type={newTotal === order.totalAmount ? undefined : 'warning'}>
          {formatVnd(newTotal)}
        </Typography.Text>
      </Typography.Paragraph>
      <Form.Item
        name="reason"
        label="Lý do điều chỉnh"
        rules={[
          { required: true, whitespace: true, message: 'Ghi lý do điều chỉnh giá' },
          // BR-SAL-24: phải có ít nhất một dòng đổi giá — báo ngay, không gửi để backend trả 400
          ({ getFieldsValue }) => ({
            validator: async () => {
              const input = toPriceAdjustmentInput(order.lines, getFieldsValue(true) as PriceAdjustmentFormValues)
              if (input.lines.length === 0) throw new Error('Chưa đổi giá dòng nào — sửa ít nhất một ô "Giá mới"')
            },
          }),
        ]}
      >
        <Input.TextArea maxLength={500} showCount autoSize={{ minRows: 2, maxRows: 4 }} placeholder="Ví dụ: bớt giá cho khách quen" />
      </Form.Item>
    </FormModal>
  )
}
