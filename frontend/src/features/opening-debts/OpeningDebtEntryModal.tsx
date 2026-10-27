import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Form, Input, InputNumber, Select, Table, Typography } from 'antd'
import type { FormListFieldData, FormRule as Rule, TableProps } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { todayIso } from '../../utils/format'
import { formatPriceInput, formatVnd, parsePriceInput } from '../../utils/money'
import { OPENING_DEBT_APIS, PARTY_TEXT } from './opening-debt-api'
import type { OpeningDebt, OpeningDebtParty } from './opening-debt-api'

/** Khách / NCC có thể chọn: đang hoạt động; hasDebt = đã có nợ đầu kỳ còn hiệu lực (BR-ODB-03) → làm mờ. */
export interface PartyOption {
  id: number
  code: string
  name: string
  hasDebt: boolean
}

interface EntryLine {
  partyId?: number
  amount?: number | null
  note?: string
}

interface EntryValues {
  documentDate?: string
  lines?: EntryLine[]
}

interface OpeningDebtEntryModalProps {
  party: OpeningDebtParty
  parties: PartyOption[]
  onSaved: (created: OpeningDebt[]) => void
  onClose: () => void
}

const NO_MARGIN = { marginBottom: 0 }

const wholeDongRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !Number.isInteger(value)) throw new Error('Nhập số đồng nguyên')
  },
}

/**
 * Ghi nhận công nợ đầu kỳ theo lô (BR-ODB-04): bảng nhiều dòng, mỗi dòng một khách / NCC và một số nợ tổng.
 * Ghi tất cả hoặc không ghi gì — một dòng lỗi thì cả lô không vào, lỗi hiện ngay dưới ô.
 */
export function OpeningDebtEntryModal({ party, parties, onSaved, onClose }: OpeningDebtEntryModalProps) {
  const [form] = Form.useForm<EntryValues>()
  const lines = Form.useWatch('lines', form) as EntryLine[] | undefined
  const text = PARTY_TEXT[party]
  const chosen = (lines ?? []).map((line) => line?.partyId)
  const total = (lines ?? []).reduce((sum, line) => sum + (line?.amount ?? 0), 0)

  const options = (rowIndex: number) =>
    parties.map((item) => ({
      value: item.id,
      label: item.hasDebt ? `${item.code} · ${item.name} — đã có nợ đầu kỳ` : `${item.code} · ${item.name}`,
      disabled: item.hasDebt || chosen.some((id, index) => id === item.id && index !== rowIndex),
    }))

  const columns = (remove: (index: number) => void, count: number): TableProps<FormListFieldData>['columns'] => [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    {
      title: text.party,
      key: 'party',
      width: 320,
      render: (_, field) => (
        <Form.Item name={[field.name, 'partyId']} rules={[{ required: true, message: 'Chọn' }]} style={NO_MARGIN}>
          <Select<number>
            placeholder={`Chọn ${text.party.toLowerCase()}`}
            options={options(field.name)}
            showSearch={{ optionFilterProp: 'label' }}
            aria-label={`${text.party} dòng ${field.name + 1}`}
          />
        </Form.Item>
      ),
    },
    {
      title: 'Số nợ (đ)',
      key: 'amount',
      width: 180,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'amount']}
          rules={[{ required: true, message: 'Nhập số nợ' }, { type: 'number', min: 1, message: 'Phải lớn hơn 0' }, wholeDongRule]}
          style={NO_MARGIN}
        >
          <InputNumber<number>
            min={1}
            style={{ width: '100%' }}
            formatter={formatPriceInput}
            parser={(value) => Number(parsePriceInput(value))}
            aria-label={`Số nợ dòng ${field.name + 1}`}
          />
        </Form.Item>
      ),
    },
    {
      title: 'Ghi chú',
      key: 'note',
      render: (_, field) => (
        <Form.Item name={[field.name, 'note']} style={NO_MARGIN}>
          <Input maxLength={500} placeholder="Ví dụ: theo sổ tay đến 31/10" aria-label={`Ghi chú dòng ${field.name + 1}`} />
        </Form.Item>
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 48,
      render: (_, field) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          disabled={count === 1}
          onClick={() => remove(field.name)}
          aria-label={`Xóa dòng ${field.name + 1}`}
        />
      ),
    },
  ]

  const handleSubmit = async (values: EntryValues) => {
    const created = await OPENING_DEBT_APIS[party].create({
      documentDate: values.documentDate,
      lines: (values.lines ?? []).map((line) => ({
        partyId: line.partyId!,
        amount: line.amount!,
        note: line.note?.trim() || undefined,
      })),
    })
    onSaved(created)
  }

  return (
    <FormModal<EntryValues>
      open
      width={960}
      title={`Ghi nhận ${text.title.toLowerCase()}`}
      form={form}
      initialValues={{ documentDate: todayIso(), lines: [{}] }}
      submitText="Ghi nhận"
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Typography.Paragraph type="secondary">
        Số nợ cũ từ trước khi dùng phần mềm, mỗi {text.party.toLowerCase()} một tổng số. Ghi nhận xong không sửa được —
        nhập sai thì đảo khoản đó (khi chưa {party === 'CUSTOMER' ? 'thu' : 'trả'} đồng nào) rồi ghi nhận lại.
      </Typography.Paragraph>
      <Form.Item
        name="documentDate"
        label="Ngày chốt nợ"
        style={{ maxWidth: 220 }}
        rules={[
          { required: true, message: 'Chọn ngày' },
          {
            validator: async (_, value?: string) => {
              if (value && value > todayIso()) throw new Error('Không được ở tương lai')
            },
          },
        ]}
      >
        <Input type="date" max={todayIso()} />
      </Form.Item>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            <Table<FormListFieldData>
              size="small"
              rowKey="key"
              columns={columns(remove, fields.length)}
              dataSource={fields}
              pagination={false}
              scroll={{ x: 'max-content' }}
              summary={() => (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={2} align="right">
                    <Typography.Text type="secondary">Tổng ({fields.length} dòng)</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={2}>
                    <Typography.Text strong>{formatVnd(total)}</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3} colSpan={2} />
                </Table.Summary.Row>
              )}
            />
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})} style={{ marginTop: 12 }}>
              Thêm dòng
            </Button>
          </>
        )}
      </Form.List>
    </FormModal>
  )
}
