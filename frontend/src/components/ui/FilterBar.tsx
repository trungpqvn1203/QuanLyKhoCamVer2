import { CloseCircleOutlined, SearchOutlined } from '@ant-design/icons'
import { Button, Flex, Input, Select } from 'antd'
import { useEffect, useState } from 'react'
import type { FilterValues } from '../../utils/filters'
import { hasActiveFilters } from '../../utils/filters'

/** Ô tìm theo từ khóa (mã hoặc tên). Mỗi thanh lọc chỉ có tối đa một ô này. */
interface KeywordField<F> {
  type: 'keyword'
  name: keyof F & string
  placeholder: string
}

/** Ô chọn một giá trị trong danh sách, ví dụ danh mục, loại kho. */
interface SelectField<F> {
  type: 'select'
  name: keyof F & string
  placeholder: string
  options: { value: string | number; label: string }[]
  width?: number
}

/** Ô trạng thái hoạt động (boolean `active`). Nhãn theo nghiệp vụ: "Đang kinh doanh" / "Đang hoạt động"... */
interface StatusField<F> {
  type: 'status'
  name: keyof F & string
  activeLabel: string
  inactiveLabel: string
}

/**
 * Mô tả một ô lọc. Màn hình khai báo danh sách ô; FilterBar tự vẽ và gom giá trị.
 * Chỉ khai báo những thuộc tính mà API của module đó thực sự hỗ trợ lọc (contract-spec).
 */
export type FilterField<F> = KeywordField<F> | SelectField<F> | StatusField<F>

interface FilterBarProps<F extends FilterValues> {
  fields: FilterField<F>[]
  value: F
  onChange: (filters: F) => void
}

/** Chờ người dùng ngừng gõ rồi mới gọi API, tránh gửi 1 request cho mỗi phím. */
const SEARCH_DEBOUNCE_MS = 400

/** Chiều cao thanh lọc (ô nhập 32 + khoảng cách 16) — DataTable trừ đi để tính chiều cao bảng. */
export const FILTER_BAR_HEIGHT = 48

/**
 * Thanh tìm kiếm + lọc dùng chung (ADR-0002, tầng 2). Mọi điều kiện gửi lên backend (lọc trong SQL),
 * nên tổng số và phân trang luôn đúng trên toàn bộ dữ liệu, không chỉ trang đang xem.
 * Tổng số kết quả hiển thị ở thanh phân trang của DataTable (không lặp lại ở đây).
 */
export function FilterBar<F extends FilterValues>({ fields, value, onChange }: FilterBarProps<F>) {
  // Chỉ giữ tên (chuỗi): mảng fields thường khai báo ngay trong render, object mới mỗi lần
  const keywordName = fields.find((field): field is KeywordField<F> => field.type === 'keyword')?.name
  const appliedKeyword = keywordName ? (value[keywordName] as string | undefined) : undefined
  const [keywordInput, setKeywordInput] = useState(appliedKeyword ?? '')
  const [lastApplied, setLastApplied] = useState(appliedKeyword)
  const filtering = hasActiveFilters(value)

  // Từ khóa bị xóa từ bên ngoài (nút "Xóa bộ lọc" trong bảng trống) → xóa luôn ô nhập,
  // nếu không debounce sẽ áp lại từ khóa cũ. Chỉ xử lý khi bị XÓA: lúc đang gõ "heo c",
  // từ khóa "heo" vừa được áp dụng không được ghi đè chữ người dùng đang gõ.
  if (appliedKeyword !== lastApplied) {
    setLastApplied(appliedKeyword)
    if (appliedKeyword === undefined) setKeywordInput('')
  }

  useEffect(() => {
    if (!keywordName) return
    const keyword = keywordInput.trim()
    if (keyword === (appliedKeyword ?? '')) return
    const timer = setTimeout(() => onChange({ ...value, [keywordName]: keyword || undefined }), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [keywordInput, appliedKeyword, keywordName, value, onChange])

  const setField = (name: keyof F & string, fieldValue: F[keyof F] | undefined) => onChange({ ...value, [name]: fieldValue })

  const clearAll = () => {
    setKeywordInput('')
    onChange({} as F)
  }

  return (
    <Flex gap={8} wrap align="center" style={{ marginBottom: 16 }}>
      {fields.map((field) => {
        switch (field.type) {
          case 'keyword':
            return (
              <Input
                key={field.name}
                allowClear
                prefix={<SearchOutlined />}
                placeholder={field.placeholder}
                aria-label={field.placeholder}
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                style={{ flex: '1 1 240px', maxWidth: 320 }}
              />
            )
          case 'select':
            return (
              <Select<string | number>
                key={field.name}
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                placeholder={field.placeholder}
                aria-label={field.placeholder}
                options={field.options}
                value={value[field.name] as string | number | undefined}
                onChange={(selected?: string | number) => setField(field.name, selected as F[keyof F] | undefined)}
                style={{ width: field.width ?? 200 }}
              />
            )
          case 'status': {
            const current = value[field.name]
            return (
              <Select<string>
                key={field.name}
                allowClear
                placeholder="Tất cả trạng thái"
                aria-label="Lọc theo trạng thái"
                // Select của Ant Design không nhận giá trị boolean → đổi qua chuỗi
                options={[
                  { value: 'true', label: field.activeLabel },
                  { value: 'false', label: field.inactiveLabel },
                ]}
                value={current === undefined ? undefined : String(current)}
                onChange={(status?: string) =>
                  setField(field.name, (status === undefined ? undefined : status === 'true') as F[keyof F] | undefined)
                }
                style={{ width: 180 }}
              />
            )
          }
        }
      })}
      {filtering && (
        <Button type="link" icon={<CloseCircleOutlined />} onClick={clearAll}>
          Xóa bộ lọc
        </Button>
      )}
    </Flex>
  )
}
