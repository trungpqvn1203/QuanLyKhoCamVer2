import { useEffect, useState } from 'react'
import type { Warehouse } from '../warehouses/warehouse-api'
import { warehouseApi } from '../warehouses/warehouse-api'

/** Tối đa 100 kho (giới hạn size của backend) — như các danh sách tham chiếu khác của MVP. */
const REFERENCE_SIZE = 100

/**
 * Mọi kho, gồm cả kho đã ngừng: bộ lọc cần tìm được phiếu cũ và kho nguồn được phép đã ngừng (BR-TRF-09).
 * Tải MỘT lần ở trang danh sách. Lỗi → danh sách rỗng.
 */
export function useTransferWarehouses(): Warehouse[] {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  useEffect(() => {
    let ignore = false
    warehouseApi
      .list({ page: 0, size: REFERENCE_SIZE })
      .then((result) => {
        if (!ignore) setWarehouses(result.items)
      })
      .catch(() => {
        // giữ danh sách rỗng
      })
    return () => {
      ignore = true
    }
  }, [])

  return warehouses
}
