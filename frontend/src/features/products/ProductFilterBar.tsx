import { CloseCircleOutlined, SearchOutlined } from '@ant-design/icons'
import { App, Button, Flex, Input, Select, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { categoryApi, hasActiveFilters } from './product-api'
import type { Category, ProductFilters } from './product-api'

interface ProductFilterBarProps {
  value: ProductFilters
  onChange: (filters: ProductFilters) => void
  /** totalElements của kết quả hiện tại; undefined khi chưa tải xong */
  total?: number
}

/** Chờ người dùng ngừng gõ rồi mới gọi API, tránh gửi 1 request cho mỗi phím. */
const SEARCH_DEBOUNCE_MS = 400

/** Chiều cao thanh lọc (ô nhập 32 + khoảng cách 16) — DataTable trừ đi để tính chiều cao bảng. */
export const FILTER_BAR_HEIGHT = 48

const STATUS_OPTIONS = [
  { value: 'true', label: 'Đang kinh doanh' },
  { value: 'false', label: 'Ngừng kinh doanh' },
]

/**
 * Thanh tìm kiếm + lọc. Mọi điều kiện gửi lên backend (lọc trong SQL),
 * nên số kết quả và phân trang luôn đúng trên toàn bộ dữ liệu, không chỉ trang đang xem.
 */
export function ProductFilterBar({ value, onChange, total }: ProductFilterBarProps) {
  const { message } = App.useApp()
  const [keywordInput, setKeywordInput] = useState(value.keyword ?? '')
  const [appliedKeyword, setAppliedKeyword] = useState(value.keyword)
  const [categories, setCategories] = useState<Category[]>([])
  const filtering = hasActiveFilters(value)

  // Từ khóa bị xóa từ bên ngoài (nút "Xóa bộ lọc" trong bảng trống) → xóa luôn ô nhập,
  // nếu không debounce sẽ áp lại từ khóa cũ. Chỉ xử lý khi bị XÓA: lúc đang gõ "heo c",
  // từ khóa "heo" vừa được áp dụng không được ghi đè chữ người dùng đang gõ.
  if (value.keyword !== appliedKeyword) {
    setAppliedKeyword(value.keyword)
    if (value.keyword === undefined) setKeywordInput('')
  }

  useEffect(() => {
    categoryApi
      .listForSelect()
      .then((page) => setCategories(page.items))
      .catch((error: unknown) =>
        message.error(error instanceof ApiError ? error.message : 'Không tải được danh mục'),
      )
  }, [message])

  useEffect(() => {
    const keyword = keywordInput.trim()
    if (keyword === (value.keyword ?? '')) return
    const timer = setTimeout(() => onChange({ ...value, keyword: keyword || undefined }), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [keywordInput, value, onChange])

  const clearAll = () => {
    setKeywordInput('')
    onChange({})
  }

  // Lọc được cả danh mục đã ngừng dùng (để tra sản phẩm cũ), đánh dấu rõ
  const categoryOptions = categories.map((category) => ({
    value: category.id,
    label: category.active ? category.name : `${category.name} (ngừng dùng)`,
  }))

  return (
    <Flex gap={8} wrap align="center" style={{ marginBottom: 16 }}>
      <Input
        allowClear
        prefix={<SearchOutlined />}
        placeholder="Tìm theo mã hoặc tên sản phẩm"
        aria-label="Tìm theo mã hoặc tên sản phẩm"
        value={keywordInput}
        onChange={(event) => setKeywordInput(event.target.value)}
        style={{ flex: '1 1 240px', maxWidth: 320 }}
      />
      <Select
        allowClear
        showSearch={{ optionFilterProp: 'label' }}
        placeholder="Tất cả danh mục"
        aria-label="Lọc theo danh mục"
        options={categoryOptions}
        value={value.categoryId}
        onChange={(categoryId?: number) => onChange({ ...value, categoryId })}
        style={{ width: 200 }}
      />
      <Select
        allowClear
        placeholder="Tất cả trạng thái"
        aria-label="Lọc theo trạng thái kinh doanh"
        options={STATUS_OPTIONS}
        value={value.active === undefined ? undefined : String(value.active)}
        onChange={(status?: string) => onChange({ ...value, active: status === undefined ? undefined : status === 'true' })}
        style={{ width: 180 }}
      />
      {filtering && (
        <Button type="link" icon={<CloseCircleOutlined />} onClick={clearAll}>
          Xóa bộ lọc
        </Button>
      )}
      {total !== undefined && (
        <Typography.Text type="secondary" style={{ marginInlineStart: 'auto' }}>
          {filtering ? `Tìm thấy ${total} sản phẩm` : `${total} sản phẩm`}
        </Typography.Text>
      )}
    </Flex>
  )
}
