import { SearchOutlined } from '@ant-design/icons'
import { Select, Typography } from 'antd'
import type { RefSelectProps } from 'antd'
import { useEffect } from 'react'
import type { ReactNode, RefObject } from 'react'
import { matchesSearch } from './product-quick-add'

export interface QuickAddOption {
  value: number
  label: string
  disabled?: boolean
}

interface ProductQuickAddProps {
  options: QuickAddOption[]
  onPick: (productId: number) => void
  /** Form giữ ref để đưa con trỏ về đây sau khi nhập xong một dòng */
  selectRef: RefObject<RefSelectProps | null>
  disabled?: boolean
  loading?: boolean
  placeholder?: string
  notFoundContent?: ReactNode
}

/**
 * Ô thêm nhanh SP phía trên bảng dòng hàng: gõ mã/tên (không dấu cũng được) → Enter hoặc click → SP vào bảng.
 * Ô luôn trống sau khi chọn để gõ tiếp SP sau; F2 đưa con trỏ về ô từ bất kỳ đâu trong form.
 */
export function ProductQuickAdd({
  options,
  onPick,
  selectRef,
  disabled,
  loading,
  placeholder = 'Gõ mã hoặc tên sản phẩm để thêm vào phiếu (F2)',
  notFoundContent,
}: ProductQuickAddProps) {
  useEffect(() => {
    if (disabled) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'F2') return
      event.preventDefault()
      selectRef.current?.focus()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [disabled, selectRef])

  return (
    <Select<number>
      ref={selectRef}
      // value luôn null: ô chỉ là nơi tìm, SP đã chọn nằm ở bảng bên dưới
      value={null}
      placeholder={placeholder}
      aria-label="Thêm sản phẩm vào phiếu"
      options={options}
      disabled={disabled}
      loading={loading}
      showSearch={{ filterOption: (input, option) => matchesSearch(String(option?.label ?? ''), input) }}
      suffixIcon={<SearchOutlined />}
      notFoundContent={loading ? 'Đang tải…' : notFoundContent}
      onSelect={(productId) => onPick(productId)}
      style={{ width: '100%', marginBottom: 12 }}
    />
  )
}

/**
 * SP của dòng dạng chữ, đặt trong Form.Item name=productId (Form.Item truyền `value` vào) — vẫn giữ rule "Chọn sản phẩm".
 * Dòng chưa có SP thì nhắc tìm ở ô thêm nhanh.
 */
export function ProductLabel({ value, labels }: { value?: number; labels: Map<number, string> }) {
  if (value === undefined) return <Typography.Text type="secondary">Chưa chọn — tìm ở ô phía trên</Typography.Text>
  return <span>{labels.get(value) ?? `#${value}`}</span>
}
