import { useEffect, useState } from 'react'
import type { Customer } from '../customers/customer-api'
import { customerApi } from '../customers/customer-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { warehouseApi } from '../warehouses/warehouse-api'

/** Tối đa 100 bản ghi mỗi danh sách (giới hạn size của backend) — đủ cho MVP. */
const REFERENCE_SIZE = 100

export interface SalesReferences {
  customers: Customer[]
  warehouses: Warehouse[]
}

/**
 * Khách và kho cho ô lọc danh sách và ô chọn trong form phiếu bán — tải một lần ở trang.
 * Lấy cả bản đã ngừng (bộ lọc cần tìm phiếu cũ); form tự lọc bản đang hoạt động. Lỗi tải → danh sách rỗng.
 */
export function useSalesReferences(): SalesReferences {
  const [references, setReferences] = useState<SalesReferences>({ customers: [], warehouses: [] })

  useEffect(() => {
    let ignore = false
    const query = { page: 0, size: REFERENCE_SIZE }
    Promise.all([customerApi.list(query), warehouseApi.list(query)])
      .then(([customers, warehouses]) => {
        if (!ignore) setReferences({ customers: customers.items, warehouses: warehouses.items })
      })
      .catch(() => {
        // giữ danh sách rỗng
      })
    return () => {
      ignore = true
    }
  }, [])

  return references
}
