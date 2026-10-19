import { Tag } from 'antd'
import type { TagProps } from 'antd'
import { CUSTOMER_TYPE_LABELS } from './customer-api'
import type { CustomerType } from './customer-api'

const TYPE_COLORS: Record<CustomerType, TagProps['color']> = {
  DEALER: 'blue',
  FARM: 'green',
  RETAIL: 'default',
}

export function CustomerTypeTag({ type }: { type: CustomerType }) {
  return (
    <Tag color={TYPE_COLORS[type]} variant="filled" style={{ marginInlineEnd: 0 }}>
      {CUSTOMER_TYPE_LABELS[type]}
    </Tag>
  )
}
