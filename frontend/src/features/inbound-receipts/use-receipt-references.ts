import { useEffect, useState } from 'react'
import type { Supplier } from '../suppliers/supplier-api'
import { supplierApi } from '../suppliers/supplier-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { warehouseApi } from '../warehouses/warehouse-api'

/** Tối đa 100 bản ghi mỗi danh sách (giới hạn size của backend) — đủ cho MVP; nhiều hơn thì cần ô tìm gọi API. */
const REFERENCE_SIZE = 100

export interface ReceiptReferences {
  suppliers: Supplier[]
  warehouses: Warehouse[]
}

/**
 * NCC và kho cho ô lọc của danh sách và ô chọn trong form phiếu — tải MỘT lần ở trang danh sách.
 * Lấy cả bản ghi đã ngừng: bộ lọc cần tìm được phiếu cũ của NCC/kho đã ngừng; form tự lọc lấy bản đang hoạt động.
 * Lỗi tải → danh sách rỗng (ô chọn trống); lỗi thật sẽ hiện khi gọi API chính.
 */
export function useReceiptReferences(): ReceiptReferences {
  const [references, setReferences] = useState<ReceiptReferences>({ suppliers: [], warehouses: [] })

  useEffect(() => {
    let ignore = false
    const query = { page: 0, size: REFERENCE_SIZE }
    Promise.all([supplierApi.list(query), warehouseApi.list(query)])
      .then(([suppliers, warehouses]) => {
        if (!ignore) setReferences({ suppliers: suppliers.items, warehouses: warehouses.items })
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
